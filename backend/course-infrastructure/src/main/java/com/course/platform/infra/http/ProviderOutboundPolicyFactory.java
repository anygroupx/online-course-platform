package com.course.platform.infra.http;

import com.course.platform.domain.entity.ApiProvider;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.time.Duration;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Creates a least-privilege egress policy for exactly one configured API provider.
 * HTTPS provider hosts are authorized by the active provider record itself. Insecure HTTP and
 * non-default ports still require an explicit deployment-level exception.
 */
@Component
public class ProviderOutboundPolicyFactory {

    private final OutboundSecurityProperties properties;
    private final ProviderUrlNormalizer urlNormalizer;

    public ProviderOutboundPolicyFactory(OutboundSecurityProperties properties,
                                         ProviderUrlNormalizer urlNormalizer) {
        this.properties = properties;
        this.urlNormalizer = urlNormalizer;
    }

    public OutboundRequestPolicy forCandidate(URI providerBaseUri) {
        URI normalized = urlNormalizer.normalize(providerBaseUri == null ? null : providerBaseUri.toString());
        return create("api-provider-candidate", normalized);
    }

    public OutboundRequestPolicy forProvider(ApiProvider provider, URI endpoint) {
        if (provider == null || endpoint == null) {
            blocked();
        }
        if (provider.getId() == null || !Integer.valueOf(ApiProvider.STATUS_ACTIVE).equals(provider.getStatus())) {
            throw new SafeHttpException(SafeHttpException.Reason.PROVIDER_NOT_ACTIVE);
        }
        URI base = urlNormalizer.normalize(provider.getApiUrl(), provider.getProviderType());
        requireSameOrigin(base, endpoint);
        String name = provider.getId() == null ? "api-provider" : "api-provider-" + provider.getId();
        return create(name, base);
    }

    private OutboundRequestPolicy create(String name, URI base) {
        String host = base.getHost().toLowerCase(Locale.ROOT);
        String scheme = base.getScheme().toLowerCase(Locale.ROOT);
        int defaultPort = "https".equals(scheme) ? 443 : 80;
        int port = base.getPort() < 0 ? defaultPort : base.getPort();

        Set<String> hosts = Set.of(host);
        Set<String> httpHosts = Set.of();
        if ("http".equals(scheme)) {
            Set<String> explicitlyAllowedHttpHosts = normalizedHosts(properties.getProviderHttpAllowedHosts());
            if (!explicitlyAllowedHttpHosts.contains(host)) {
                blocked();
            }
            httpHosts = hosts;
        }

        Set<Integer> ports = Set.of();
        if (port != defaultPort) {
            Set<Integer> explicitlyAllowedPorts = properties.getProviderAllowedPorts() == null
                    ? Set.of() : new LinkedHashSet<>(properties.getProviderAllowedPorts());
            if (!explicitlyAllowedPorts.contains(port)) {
                blocked();
            }
            ports = Set.of(port);
        }

        return new OutboundRequestPolicy(name, hosts, httpHosts, ports,
                properties.getMaxResponseBytes(),
                Duration.ofMillis(properties.getConnectTimeoutMillis()),
                Duration.ofMillis(properties.getReadTimeoutMillis()),
                Duration.ofMillis(properties.getCallTimeoutMillis()));
    }

    private void requireSameOrigin(URI base, URI endpoint) {
        if (!endpoint.isAbsolute() || endpoint.isOpaque() || endpoint.getHost() == null
                || endpoint.getRawUserInfo() != null || endpoint.getRawFragment() != null
                || endpoint.getRawAuthority().endsWith(":")) {
            blocked();
        }
        String baseScheme = base.getScheme().toLowerCase(Locale.ROOT);
        String endpointScheme = endpoint.getScheme().toLowerCase(Locale.ROOT);
        String baseHost = base.getHost().toLowerCase(Locale.ROOT);
        String endpointHost = endpoint.getHost().toLowerCase(Locale.ROOT);
        if (!baseScheme.equals(endpointScheme) || !baseHost.equals(endpointHost)
                || effectivePort(base) != effectivePort(endpoint)) {
            blocked();
        }
    }

    private int effectivePort(URI uri) {
        if (uri.getPort() >= 0) {
            return uri.getPort();
        }
        return "https".equalsIgnoreCase(uri.getScheme()) ? 443 : 80;
    }

    private Set<String> normalizedHosts(Iterable<String> values) {
        Set<String> normalized = new LinkedHashSet<>();
        if (values != null) {
            for (String value : values) {
                if (value != null && !value.isBlank()) {
                    normalized.add(value.trim().toLowerCase(Locale.ROOT));
                }
            }
        }
        return normalized;
    }

    private void blocked() {
        throw new SafeHttpException(SafeHttpException.Reason.BLOCKED_DESTINATION);
    }
}
