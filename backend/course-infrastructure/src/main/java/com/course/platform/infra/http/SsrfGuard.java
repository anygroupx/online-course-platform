package com.course.platform.infra.http;

import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Autowired;

import java.net.IDN;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.*;

/** Strict URI, DNS and address validation used before every outbound connection. */
@Component
public class SsrfGuard {

    @FunctionalInterface
    public interface HostResolver {
        List<InetAddress> resolve(String host) throws UnknownHostException;
    }

    // Bounded workers/queue: a stuck OS resolver cannot consume unbounded request threads.
    private static final ExecutorService DNS_EXECUTOR = new ThreadPoolExecutor(
            4, 4, 30, TimeUnit.SECONDS, new ArrayBlockingQueue<>(16), runnable -> {
                Thread thread = new Thread(runnable, "outbound-dns");
                thread.setDaemon(true);
                return thread;
            }, new ThreadPoolExecutor.AbortPolicy());

    private final HostResolver resolver;
    private final boolean fixedDns;

    @Autowired
    public SsrfGuard(OutboundSecurityProperties properties) {
        this("cloudflare-doh".equals(properties.getDnsMode())
                ? new CloudflareDnsResolver()
                : host -> Arrays.asList(InetAddress.getAllByName(host)));
    }

    SsrfGuard(HostResolver resolver) {
        this.resolver = resolver;
        this.fixedDns = false;
    }

    /** Bootstrap only: no DNS I/O, but normal public-address and URI checks still apply. */
    SsrfGuard(Map<String, List<InetAddress>> fixedAddresses) {
        var copy = fixedAddresses.entrySet().stream().collect(java.util.stream.Collectors.toUnmodifiableMap(
                Map.Entry::getKey, entry -> List.copyOf(entry.getValue())));
        this.resolver = copy::get;
        this.fixedDns = true;
    }

    public ValidatedDestination validate(URI uri, OutboundRequestPolicy policy) {
        if (uri == null || policy == null || !uri.isAbsolute() || uri.isOpaque()) blocked();
        if (uri.getRawUserInfo() != null || uri.getRawFragment() != null || uri.getRawAuthority() == null) blocked();
        String rawAuthority = uri.getRawAuthority();
        if (rawAuthority.contains("\\") || rawAuthority.contains("%") || rawAuthority.contains("@")) blocked();

        String scheme = lower(uri.getScheme());
        String host = normalizeHost(uri.getHost());
        if (!policy.allowedHosts().contains(host)) blocked();
        if (!"https".equals(scheme) && !("http".equals(scheme) && policy.httpAllowedHosts().contains(host))) blocked();

        int defaultPort = "https".equals(scheme) ? 443 : 80;
        int port = uri.getPort() < 0 ? defaultPort : uri.getPort();
        if (port < 1 || port > 65535) blocked();
        if (port != defaultPort && !policy.allowedPorts().contains(port)) blocked();

        List<InetAddress> addresses = resolveBounded(host, policy);
        if (addresses == null || addresses.isEmpty()) {
            throw new SafeHttpException(SafeHttpException.Reason.DNS_FAILURE);
        }
        for (InetAddress address : addresses) {
            if (!isPublicAddress(address)) blocked();
        }
        return new ValidatedDestination(uri, host, addresses);
    }

    private List<InetAddress> resolveBounded(String host, OutboundRequestPolicy policy) {
        if (fixedDns) {
            try { return resolver.resolve(host); }
            catch (UnknownHostException ex) { throw new SafeHttpException(SafeHttpException.Reason.DNS_FAILURE); }
        }
        Future<List<InetAddress>> pending = null;
        try {
            pending = DNS_EXECUTOR.submit(() -> resolver.resolve(host));
            return pending.get(Math.max(1, Math.min(policy.connectTimeout().toMillis(),
                    policy.callTimeout().toMillis())), TimeUnit.MILLISECONDS);
        } catch (TimeoutException ex) {
            throw new SafeHttpException(SafeHttpException.Reason.TIMEOUT);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new SafeHttpException(SafeHttpException.Reason.NETWORK_FAILURE);
        } catch (ExecutionException | RejectedExecutionException ex) {
            throw new SafeHttpException(SafeHttpException.Reason.DNS_FAILURE);
        } finally {
            if (pending != null && !pending.isDone()) pending.cancel(true);
        }
    }

    String normalizeHost(String host) {
        if (host == null || host.isBlank() || host.endsWith(".")) blocked();
        String value = host;
        if (value.startsWith("[") && value.endsWith("]")) value = value.substring(1, value.length() - 1);
        try {
            value = IDN.toASCII(value, IDN.USE_STD3_ASCII_RULES).toLowerCase(Locale.ROOT);
        } catch (IllegalArgumentException ex) {
            throw new SafeHttpException(SafeHttpException.Reason.BLOCKED_DESTINATION, ex);
        }
        if (value.isBlank() || value.length() > 253 || "localhost".equals(value) || value.endsWith(".localhost")) blocked();
        return value;
    }

    boolean isPublicAddress(InetAddress address) {
        if (address == null || address.isAnyLocalAddress() || address.isLoopbackAddress()
                || address.isLinkLocalAddress() || address.isSiteLocalAddress() || address.isMulticastAddress()) {
            return false;
        }
        byte[] bytes = address.getAddress();
        if (address instanceof Inet4Address || bytes.length == 4) return isPublicIpv4(bytes);
        if (!(address instanceof Inet6Address) || bytes.length != 16) return false;

        int b0 = u(bytes[0]);
        int b1 = u(bytes[1]);
        // Only global-unicast space is eligible. Exclude ULA/link-local/multicast/unspecified by construction.
        if ((b0 & 0xE0) != 0x20) return false; // 2000::/3
        if (b0 == 0x20 && b1 == 0x01) {
            int b2 = u(bytes[2]);
            int b3 = u(bytes[3]);
            if (b2 == 0x0d && b3 == 0xb8) return false; // 2001:db8::/32 documentation
            if (b2 < 0x02) return false; // 2001::/23 special-purpose/transition space
        }
        if (b0 == 0x3f && b1 == 0xff && (u(bytes[2]) & 0xf0) == 0) return false; // documentation
        if (b0 == 0x20 && b1 == 0x02) return false; // 6to4 can encode private IPv4 destinations
        return true;
    }

    private boolean isPublicIpv4(byte[] bytes) {
        int a = u(bytes[0]);
        int b = u(bytes[1]);
        int c = u(bytes[2]);
        if (a == 0 || a == 10 || a == 127 || a >= 224) return false;
        if (a == 100 && b >= 64 && b <= 127) return false; // shared carrier space
        if (a == 169 && b == 254) return false;
        if (a == 172 && b >= 16 && b <= 31) return false;
        if (a == 192 && b == 88 && c == 99) return false; // deprecated relay range
        if (a == 192 && b == 168) return false;
        if (a == 192 && b == 0 && c == 0) return false;
        if (a == 192 && b == 0 && c == 2) return false;
        if (a == 198 && (b == 18 || b == 19)) return false;
        if (a == 198 && b == 51 && c == 100) return false;
        if (a == 203 && b == 0 && c == 113) return false;
        return !(a == 255 && b == 255 && c == 255 && u(bytes[3]) == 255);
    }

    private int u(byte value) {
        return value & 0xff;
    }

    private String lower(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT);
    }

    private void blocked() {
        throw new SafeHttpException(SafeHttpException.Reason.BLOCKED_DESTINATION);
    }
}
