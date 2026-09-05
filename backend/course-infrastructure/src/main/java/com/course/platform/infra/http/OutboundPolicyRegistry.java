package com.course.platform.infra.http;

import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Named, least-privilege policies for every supported external integration. */
@Component
public class OutboundPolicyRegistry {
    private static final Set<String> AQKS_HOSTS = Set.of("aqks.csuft.edu.cn");
    private static final Set<String> TURNSTILE_HOSTS = Set.of("challenges.cloudflare.com");

    private final OutboundSecurityProperties properties;

    public OutboundPolicyRegistry(OutboundSecurityProperties properties) {
        this.properties = properties;
    }

    public OutboundRequestPolicy aqks() {
        return policy("aqks", AQKS_HOSTS, Set.of(), Set.of());
    }

    public OutboundRequestPolicy turnstile() {
        return policy("turnstile", TURNSTILE_HOSTS, Set.of(), Set.of());
    }

    public OutboundRequestPolicy provider() {
        return policy("api-provider", set(properties.getProviderAllowedHosts()),
                set(properties.getProviderHttpAllowedHosts()), new LinkedHashSet<>(properties.getProviderAllowedPorts()));
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
