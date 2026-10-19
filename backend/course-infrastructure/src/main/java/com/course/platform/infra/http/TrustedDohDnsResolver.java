package com.course.platform.infra.http;

import okhttp3.HttpUrl;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * Trusted DNS-over-HTTPS resolver for deployments whose OS resolver returns proxy/Fake-IP
 * addresses. Queries use the RFC 8484 wire format because it is the only encoding every
 * public DoH provider implements; the JSON API is provider-specific and has silently broken
 * on at least one of the built-in endpoints. A and AAAA are queried concurrently, successful
 * answers are cached, and the configured endpoints act as mutual fallbacks. Bootstrap IPs are
 * fixed, TLS host verification remains enabled, and SsrfGuard still validates every answer.
 */
final class TrustedDohDnsResolver implements SsrfGuard.HostResolver {
    private static final Duration CACHE_TTL = Duration.ofMinutes(5);
    private static final String DNS_QUERY_PATH = "dns-query";
    private static final String WIRE_MEDIA_TYPE = "application/dns-message";
    private static final int MAX_RESPONSE_BYTES = 65_536;

    private static final ExecutorService QUERY_EXECUTOR = new ThreadPoolExecutor(
            4, 4, 30, TimeUnit.SECONDS, new ArrayBlockingQueue<>(32), runnable -> {
                Thread thread = new Thread(runnable, "trusted-dns-query");
                thread.setDaemon(true);
                return thread;
            }, new ThreadPoolExecutor.CallerRunsPolicy());

    private final List<Endpoint> endpoints;
    private final OutboundRequestPolicy policy;
    private final Map<String, CacheEntry> cache = new ConcurrentHashMap<>();

    TrustedDohDnsResolver(List<DohEndpointProperties> configured) {
        List<DohEndpointProperties> effective = configured == null ? List.of() : configured;
        if (effective.isEmpty()) {
            throw new IllegalStateException("Trusted DNS mode requires at least one DoH endpoint");
        }
        List<Endpoint> built = new ArrayList<>(effective.size());
        Set<String> hosts = new LinkedHashSet<>();
        for (DohEndpointProperties entry : effective) {
            if (entry == null || entry.getHost() == null || entry.getHost().isBlank()) {
                throw new IllegalStateException("DoH endpoint host must not be blank");
            }
            String host = entry.getHost().trim().toLowerCase(java.util.Locale.ROOT);
            List<InetAddress> bootstrap = entry.bootstrapAddresses();
            if (bootstrap.isEmpty()) {
                throw new IllegalStateException("DoH endpoint requires at least one bootstrap address");
            }
            hosts.add(host);
            built.add(new Endpoint(host, entry.getPath(), bootstrapTransport(host, bootstrap)));
        }
        this.endpoints = List.copyOf(built);
        // The allowlist is derived from configuration so a new provider never widens egress
        // beyond the hosts an operator explicitly declared.
        this.policy = new OutboundRequestPolicy("dns-over-https", Set.copyOf(hosts), Set.of(), Set.of(),
                MAX_RESPONSE_BYTES, Duration.ofSeconds(5), Duration.ofSeconds(5), Duration.ofSeconds(8));
    }

    /** Test seam: a single endpoint backed by a caller-supplied transport. */
    TrustedDohDnsResolver(String host, String path, SafeHttpClient transport) {
        this.endpoints = List.of(new Endpoint(host, path, transport));
        this.policy = new OutboundRequestPolicy("dns-over-https", Set.of(host), Set.of(), Set.of(),
                MAX_RESPONSE_BYTES, Duration.ofSeconds(5), Duration.ofSeconds(5), Duration.ofSeconds(8));
    }

    /** Test seam: two named endpoints backed by caller-supplied transports. */
    TrustedDohDnsResolver(SafeHttpClient primary, SafeHttpClient fallback) {
        this.endpoints = List.of(
                new Endpoint("cloudflare-dns.com", "dns-query", primary),
                new Endpoint("dns.google", "dns-query", fallback));
        this.policy = new OutboundRequestPolicy("dns-over-https",
                Set.of("cloudflare-dns.com", "dns.google"), Set.of(), Set.of(),
                MAX_RESPONSE_BYTES, Duration.ofSeconds(5), Duration.ofSeconds(5), Duration.ofSeconds(8));
    }

    @Override
    public List<InetAddress> resolve(String host) throws UnknownHostException {
        long now = System.nanoTime();
        CacheEntry cached = cache.get(host);
        if (cached != null && cached.expiresAtNanos() > now) {
            return cached.addresses();
        }

        // Query every endpoint immediately instead of waiting for a failed primary before
        // starting the fallback. IPv4 is preferred because production containers do not have
        // a routable IPv6 interface; AAAA remains a fallback for IPv6-only hosts.
        List<CompletableFuture<List<InetAddress>>> ipv4Queries = new ArrayList<>();
        List<CompletableFuture<List<InetAddress>>> ipv6Queries = new ArrayList<>();
        for (Endpoint endpoint : endpoints) {
            ipv4Queries.add(queryAsync(endpoint, host, DohMessageCodec.TYPE_A));
            ipv6Queries.add(queryAsync(endpoint, host, DohMessageCodec.TYPE_AAAA));
        }

        Optional<List<InetAddress>> resolved = firstSuccessful(ipv4Queries);
        if (resolved.isEmpty()) {
            resolved = firstSuccessful(ipv6Queries);
        }
        if (resolved.isPresent()) {
            List<InetAddress> addresses = resolved.get();
            cache.put(host, new CacheEntry(addresses, now + CACHE_TTL.toNanos()));
            return addresses;
        }
        throw failed();
    }

    private Optional<List<InetAddress>> firstSuccessful(List<CompletableFuture<List<InetAddress>>> queries) {
        BlockingQueue<QueryResult> completions = new LinkedBlockingQueue<>();
        queries.forEach(query -> query.whenComplete((addresses, error) ->
                completions.offer(new QueryResult(addresses, error))));

        for (int i = 0; i < queries.size(); i++) {
            try {
                QueryResult result = completions.take();
                if (result.error() == null && result.addresses() != null && !result.addresses().isEmpty()) {
                    return Optional.of(List.copyOf(new LinkedHashSet<>(result.addresses())));
                }
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                return Optional.empty();
            }
        }
        return Optional.empty();
    }

    private CompletableFuture<List<InetAddress>> queryAsync(Endpoint endpoint, String host, int type) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                HttpUrl.Builder url = new HttpUrl.Builder().scheme("https").host(endpoint.host());
                for (String segment : endpoint.path().split("/", -1)) {
                    if (!segment.isEmpty()) url.addPathSegment(segment);
                }
                byte[] query = DohMessageCodec.encodeQuery(host, type);
                SafeHttpBinaryResponse response = endpoint.transport().postBinary(url.build().uri(),
                        Map.of("Accept", WIRE_MEDIA_TYPE), query, WIRE_MEDIA_TYPE, policy);
                if (!response.isSuccessful()) {
                    throw failed();
                }
                return DohMessageCodec.decodeAnswers(response.body(), host, type);
            } catch (RuntimeException | UnknownHostException ex) {
                throw new CompletionException(ex);
            }
        }, QUERY_EXECUTOR);
    }

    private static SafeHttpClient bootstrapTransport(String host, List<InetAddress> addresses) {
        return new SafeHttpClient(new SsrfGuard(Map.of(host, addresses)));
    }

    private static UnknownHostException failed() {
        return new UnknownHostException("Trusted DNS resolution failed");
    }

    private record Endpoint(String host, String path, SafeHttpClient transport) { }

    private record QueryResult(List<InetAddress> addresses, Throwable error) { }

    private record CacheEntry(List<InetAddress> addresses, long expiresAtNanos) {
        private CacheEntry {
            addresses = List.copyOf(addresses);
        }
    }
}
