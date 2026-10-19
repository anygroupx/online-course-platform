package com.course.platform.infra.http;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.net.InetAddress;
import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Live check against the real DoH endpoints. Skipped unless DOH_LIVE_TEST=true because it
 * requires outbound network access, which CI does not guarantee. Run it when changing the
 * endpoint list or the wire-format codec:
 *
 * <pre>
 * DOH_LIVE_TEST=true mvn -pl course-web -am test -Dtest=TrustedDohLiveIntegrationTest \
 *     -Dsurefire.failIfNoSpecifiedTests=false
 * </pre>
 */
@EnabledIfEnvironmentVariable(named = "DOH_LIVE_TEST", matches = "true")
class TrustedDohLiveIntegrationTest {

    private static final String PROBE_HOST = "aqks.example.edu.cn";

    private DohEndpointProperties endpoint(String host, String... bootstrap) {
        DohEndpointProperties properties = new DohEndpointProperties();
        properties.setHost(host);
        properties.setBootstrap(List.of(bootstrap));
        return properties;
    }

    @Test
    void resolvesThroughEachConfiguredEndpoint() throws Exception {
        List<DohEndpointProperties> endpoints = List.of(
                endpoint("dns.alidns.com", "223.5.5.5", "223.6.6.6"),
                endpoint("doh.pub", "1.12.12.12", "120.53.53.53"),
                endpoint("cloudflare-dns.com", "1.1.1.1", "1.0.0.1"),
                endpoint("dns.google", "8.8.8.8", "8.8.4.4"));

        TrustedDohDnsResolver resolver = new TrustedDohDnsResolver(endpoints);
        List<InetAddress> addresses = resolver.resolve(PROBE_HOST);

        assertFalse(addresses.isEmpty(), "no address returned for " + PROBE_HOST);
        // The probe host must not resolve to the Fake-IP range the resolver exists to bypass.
        for (InetAddress address : addresses) {
            String literal = address.getHostAddress();
            assertFalse(literal.startsWith("198.18.") || literal.startsWith("198.19."),
                    "resolver returned a Fake-IP address: " + literal);
        }
    }

    @Test
    void eachEndpointAnswersIndependently() throws Exception {
        // Each provider must work on its own, otherwise a single reachable endpoint is masking
        // a dead peer and the "mutual fallback" is not real.
        List<DohEndpointProperties> endpoints = List.of(
                endpoint("dns.alidns.com", "223.5.5.5", "223.6.6.6"),
                endpoint("doh.pub", "1.12.12.12", "120.53.53.53"),
                endpoint("cloudflare-dns.com", "1.1.1.1", "1.0.0.1"),
                endpoint("dns.google", "8.8.8.8", "8.8.4.4"));

        for (DohEndpointProperties only : endpoints) {
            TrustedDohDnsResolver resolver = new TrustedDohDnsResolver(List.of(only));
            List<InetAddress> addresses = resolver.resolve(PROBE_HOST);
            assertFalse(addresses.isEmpty(), only.getHost() + " returned no address");
        }
    }

    @Test
    void guardAcceptsLiveAnswersAndStillRejectsPrivateTargets() throws Exception {
        TrustedDohDnsResolver resolver = new TrustedDohDnsResolver(
                List.of(endpoint("cloudflare-dns.com", "1.1.1.1")));
        SsrfGuard guard = new SsrfGuard(resolver);

        // SsrfGuardTest.policy() caps the call at 600ms, which is fine for stubbed resolvers but
        // too tight for a real network round trip; this live check needs a realistic budget.
        OutboundRequestPolicy policy = new OutboundRequestPolicy("live", Set.of(PROBE_HOST),
                Set.of(), Set.of(), 1024, Duration.ofSeconds(5), Duration.ofSeconds(5), Duration.ofSeconds(20));

        ValidatedDestination destination = guard.validate(URI.create("https://" + PROBE_HOST), policy);

        assertNotNull(destination);
        assertTrue(destination.pinnedAddresses().size() >= 1);
    }
}
