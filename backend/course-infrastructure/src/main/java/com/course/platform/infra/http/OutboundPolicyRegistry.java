package com.course.platform.infra.http;

import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Named, least-privilege policies for every supported external integration.
 *
 * <p>The AQKS integration reaches the platform through the campus WebVPN reverse proxy
 * ({@code https-<host>-<port>.webvpn.csuft.edu.cn}) because the origin
 * {@code aqks.csuft.edu.cn} only resolves to a private campus address. The proxy host is
 * deployment-specific, so it is supplied through
 * {@link OutboundSecurityProperties#getAqksAllowedHosts()} instead of being hard-coded;
 * the allowlist stays a closed set and never widens to a wildcard.</p>
 */
@Component
public class OutboundPolicyRegistry {
    /** Origin host, kept allowlisted so a direct campus deployment keeps working unchanged. */
    private static final Set<String> AQKS_ORIGIN_HOSTS = Set.of("aqks.csuft.edu.cn");
    private static final Set<String> TURNSTILE_HOSTS = Set.of("challenges.cloudflare.com");

    private final OutboundSecurityProperties properties;

    public OutboundPolicyRegistry(OutboundSecurityProperties properties) {
        this.properties = properties;
    }

    public OutboundRequestPolicy aqks() {
        return policy("aqks", aqksAllowedHosts(), Set.of(), Set.of());
    }

    /**
     * Allowlist for the AQKS integration: the fixed origin host plus any configured WebVPN
     * proxy hosts.
     */
    Set<String> aqksAllowedHosts() {
        Set<String> hosts = new LinkedHashSet<>(AQKS_ORIGIN_HOSTS);
        for (String host : set(properties.getAqksAllowedHosts())) {
            if (host != null && !host.isBlank()) {
                hosts.add(host.trim().toLowerCase(java.util.Locale.ROOT));
            }
        }
        return hosts;
    }

    public OutboundRequestPolicy turnstile() {
        return policy("turnstile", TURNSTILE_HOSTS, Set.of(), Set.of());
    }

    public OutboundRequestPolicy alertWebhook() {
        return policy("security-alert", set(properties.getAlertWebhookAllowedHosts()),
                set(properties.getAlertWebhookHttpAllowedHosts()), Set.of());
    }

    public OutboundRequestPolicy fixedHttps(String name, Set<String> hosts) {
        return policy(name, hosts, Set.of(), Set.of());
    }

    private OutboundRequestPolicy policy(String name, Set<String> hosts, Set<String> httpHosts, Set<Integer> ports) {
        return new OutboundRequestPolicy(name, hosts, httpHosts, ports, properties.getMaxResponseBytes(),
                Duration.ofMillis(properties.getConnectTimeoutMillis()),
                Duration.ofMillis(properties.getReadTimeoutMillis()),
                Duration.ofMillis(properties.getCallTimeoutMillis()));
    }

    private Set<String> set(List<String> values) {
        return values == null ? Set.of() : new LinkedHashSet<>(values);
    }
}
