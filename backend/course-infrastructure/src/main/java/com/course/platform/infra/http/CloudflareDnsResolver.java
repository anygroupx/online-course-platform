package com.course.platform.infra.http;

import cn.hutool.json.JSONUtil;
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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * Trusted DNS-over-HTTPS resolver for deployments whose OS resolver returns proxy/Fake-IP
 * addresses. A and AAAA are queried concurrently, successful answers are cached, and Google
 * Public DNS is used as a trusted fallback when Cloudflare DoH is unavailable. Bootstrap IPs
 * are fixed, TLS host verification remains enabled, and SsrfGuard still validates every answer.
 */
final class CloudflareDnsResolver implements SsrfGuard.HostResolver {
    private static final String CLOUDFLARE_HOST = "cloudflare-dns.com";
    private static final String GOOGLE_HOST = "dns.google";
    private static final Duration CACHE_TTL = Duration.ofMinutes(5);
    private static final OutboundRequestPolicy POLICY = new OutboundRequestPolicy("dns-over-https",
            Set.of(CLOUDFLARE_HOST, GOOGLE_HOST), Set.of(), Set.of(), 65_536,
            Duration.ofSeconds(5), Duration.ofSeconds(5), Duration.ofSeconds(8));
    private static final ExecutorService QUERY_EXECUTOR = new ThreadPoolExecutor(
            4, 4, 30, TimeUnit.SECONDS, new ArrayBlockingQueue<>(32), runnable -> {
                Thread thread = new Thread(runnable, "trusted-dns-query");
                thread.setDaemon(true);
                return thread;
            }, new ThreadPoolExecutor.CallerRunsPolicy());

    private final List<Endpoint> endpoints;
    private final Map<String, CacheEntry> cache = new ConcurrentHashMap<>();

    CloudflareDnsResolver() {
        this(List.of(
                new Endpoint(CLOUDFLARE_HOST, bootstrapTransport(CLOUDFLARE_HOST,
                        address(1, 1, 1, 1), address(1, 0, 0, 1))),
                new Endpoint(GOOGLE_HOST, bootstrapTransport(GOOGLE_HOST,
                        address(8, 8, 8, 8), address(8, 8, 4, 4)))
        ));
    }

    CloudflareDnsResolver(SafeHttpClient transport) {
        this(List.of(new Endpoint(CLOUDFLARE_HOST, transport)));
    }

    CloudflareDnsResolver(SafeHttpClient primary, SafeHttpClient fallback) {
        this(List.of(new Endpoint(CLOUDFLARE_HOST, primary), new Endpoint(GOOGLE_HOST, fallback)));
    }

    private CloudflareDnsResolver(List<Endpoint> endpoints) {
        this.endpoints = List.copyOf(endpoints);
    }

    @Override
    public List<InetAddress> resolve(String host) throws UnknownHostException {
        long now = System.nanoTime();
        CacheEntry cached = cache.get(host);
        if (cached != null && cached.expiresAtNanos() > now) {
            return cached.addresses();
        }

        for (Endpoint endpoint : endpoints) {
            Optional<List<InetAddress>> resolved = resolveWith(endpoint, host);
            if (resolved.isPresent()) {
                List<InetAddress> addresses = resolved.get();
                cache.put(host, new CacheEntry(addresses, now + CACHE_TTL.toNanos()));
                return addresses;
            }
        }
        throw failed();
    }

    private Optional<List<InetAddress>> resolveWith(Endpoint endpoint, String host) {
        CompletableFuture<List<InetAddress>> ipv4 = queryAsync(endpoint, host, 1);
        CompletableFuture<List<InetAddress>> ipv6 = queryAsync(endpoint, host, 28);
        List<InetAddress> addresses = new ArrayList<>();
        boolean receivedValidResponse = false;

        for (CompletableFuture<List<InetAddress>> query : List.of(ipv4, ipv6)) {
            try {
                addresses.addAll(query.join());
                receivedValidResponse = true;
            } catch (CompletionException ignored) {
                // Try the other address family, then the next trusted endpoint.
            }
        }
        if (!receivedValidResponse || addresses.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(List.copyOf(new LinkedHashSet<>(addresses)));
    }

    private CompletableFuture<List<InetAddress>> queryAsync(Endpoint endpoint, String host, int type) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                HttpUrl url = new HttpUrl.Builder().scheme("https").host(endpoint.host())
                        .addPathSegment("dns-query")
                        .addQueryParameter("name", host)
                        .addQueryParameter("type", String.valueOf(type)).build();
                SafeHttpResponse response = endpoint.transport().get(url.uri(),
                        Map.of("Accept", "application/dns-json"), POLICY);
                if (!response.isSuccessful()) {
                    throw failed();
                }
                return parse(response.body(), host, type);
            } catch (RuntimeException | UnknownHostException ex) {
                throw new CompletionException(ex);
            }
        }, QUERY_EXECUTOR);
    }

    static List<InetAddress> parse(String body, String host, int type) throws UnknownHostException {
        try {
            var json = JSONUtil.parseObj(body);
            if (json.getInt("Status", -1) != 0 || json.getBool("TC", false)) throw failed();
            var questions = json.getJSONArray("Question");
            if (questions == null || questions.size() != 1) throw failed();
            var question = questions.getJSONObject(0);
            String queried = question.getStr("name", "");
            if (queried.endsWith(".")) queried = queried.substring(0, queried.length() - 1);
            if (!host.equalsIgnoreCase(queried) || question.getInt("type", -1) != type) throw failed();
            List<InetAddress> result = new ArrayList<>();
            var answers = json.getJSONArray("Answer");
            if (answers == null) return result; // NODATA for one family is valid.
            for (int i = 0; i < answers.size(); i++) {
                var answer = answers.getJSONObject(i);
                if (answer.getInt("type", -1) != type) continue;
                String literal = answer.getStr("data", "");
                if (type == 1 ? !literal.matches("[0-9]+\\.[0-9]+\\.[0-9]+\\.[0-9]+")
                        : !literal.matches("[0-9a-fA-F]*:[0-9a-fA-F:]+")) throw failed();
                InetAddress address = InetAddress.getByName(literal);
                if (type == 1 && address.getAddress().length != 4) throw failed();
                result.add(address);
            }
            return result;
        } catch (RuntimeException ex) {
            throw failed();
        }
    }

    private static SafeHttpClient bootstrapTransport(String host, InetAddress... addresses) {
        return new SafeHttpClient(new SsrfGuard(Map.of(host, List.of(addresses))));
    }

    private static InetAddress address(int a, int b, int c, int d) {
        try {
            return InetAddress.getByAddress(new byte[]{(byte) a, (byte) b, (byte) c, (byte) d});
        } catch (UnknownHostException ex) {
            throw new IllegalStateException("Invalid DNS bootstrap constants", ex);
        }
    }

    private static UnknownHostException failed() {
        return new UnknownHostException("Trusted DNS resolution failed");
    }

    private record Endpoint(String host, SafeHttpClient transport) { }

    private record CacheEntry(List<InetAddress> addresses, long expiresAtNanos) {
        private CacheEntry {
            addresses = List.copyOf(addresses);
        }
    }
}
