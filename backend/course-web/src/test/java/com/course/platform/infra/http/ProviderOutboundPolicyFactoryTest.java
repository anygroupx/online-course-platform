package com.course.platform.infra.http;

import com.course.platform.domain.entity.ApiProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.net.URI;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ProviderOutboundPolicyFactoryTest {

    private ApiProvider provider(String url) {
        ApiProvider provider = new ApiProvider();
        provider.setId(42L);
        provider.setApiUrl(url);
        provider.setStatus(ApiProvider.STATUS_ACTIVE);
        return provider;
    }

    private ProviderOutboundPolicyFactory factory(OutboundSecurityProperties properties) {
        return new ProviderOutboundPolicyFactory(properties, new ProviderUrlNormalizer());
    }

    @Test
    void httpsProviderAuthorizesOnlyItsOwnExactHostWithoutGlobalHostList() {
        OutboundSecurityProperties properties = new OutboundSecurityProperties();
        OutboundRequestPolicy policy = factory(properties).forProvider(
                provider("https://New-Provider.example/api"),
                URI.create("https://new-provider.example/api/orders"));

        assertEquals(Set.of("new-provider.example"), policy.allowedHosts());
        assertEquals(Set.of(), policy.httpAllowedHosts());
        assertEquals("api-provider-42", policy.name());
    }

    @Test
    void rejectsEndpointThatDoesNotMatchProviderOrigin() {
        OutboundSecurityProperties properties = new OutboundSecurityProperties();
        ApiProvider provider = provider("https://provider.example");

        assertThrows(SafeHttpException.class, () -> factory(properties).forProvider(
                provider, URI.create("https://other.example/api.php")));
        assertThrows(SafeHttpException.class, () -> factory(properties).forProvider(
                provider, URI.create("http://provider.example/api.php")));
        assertThrows(SafeHttpException.class, () -> factory(properties).forProvider(
                provider, URI.create("https://provider.example:8443/api.php")));
        assertThrows(SafeHttpException.class, () -> factory(properties).forProvider(
                provider, URI.create("https://user:secret@provider.example/api.php")));
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 2, 99})
    void disabledPendingAndUnknownStatesCannotSendBusinessRequests(int status) {
        ApiProvider provider = provider("https://provider.example");
        provider.setStatus(status);
        SafeHttpException error = assertThrows(SafeHttpException.class, () -> factory(new OutboundSecurityProperties())
                .forProvider(provider, URI.create("https://provider.example/api.php")));
        assertEquals(SafeHttpException.Reason.PROVIDER_NOT_ACTIVE, error.getReason());
    }

    @Test
    void runtimeRequiresPersistedProviderButCandidatePolicyRemainsAvailableForValidation() {
        ApiProvider provider = provider("https://provider.example");
        provider.setId(null);
        var factory = factory(new OutboundSecurityProperties());
        assertThrows(SafeHttpException.class,
                () -> factory.forProvider(provider, URI.create("https://provider.example/api.php")));
        assertEquals(Set.of("provider.example"), factory.forCandidate(URI.create(provider.getApiUrl())).allowedHosts());
    }

    @Test
    void cannotUseAnotherProvidersHttpOrPortExceptionToChangeTheConfiguredOrigin() {
        var properties = new OutboundSecurityProperties();
        properties.setProviderAllowedPorts(List.of(8443, 9443));
        properties.setProviderHttpAllowedHosts(List.of("provider.example", "other.example"));
        var factory = factory(properties);
        ApiProvider provider = provider("https://provider.example:8443");
        assertThrows(SafeHttpException.class, () -> factory.forProvider(provider, URI.create("http://provider.example:8443/api")));
        assertThrows(SafeHttpException.class, () -> factory.forProvider(provider, URI.create("https://provider.example:9443/api")));
        assertThrows(SafeHttpException.class, () -> factory.forProvider(provider, URI.create("https://other.example:8443/api")));
    }

    @Test
    void httpStillRequiresExplicitDeploymentException() {
        OutboundSecurityProperties properties = new OutboundSecurityProperties();
        URI endpoint = URI.create("http://legacy.example/api.php");

        assertThrows(SafeHttpException.class,
                () -> factory(properties).forProvider(provider("http://legacy.example"), endpoint));

        properties.setProviderHttpAllowedHosts(List.of("legacy.example"));
        OutboundRequestPolicy policy = factory(properties).forProvider(
                provider("http://legacy.example"), endpoint);
        assertEquals(Set.of("legacy.example"), policy.httpAllowedHosts());
    }

    @Test
    void customPortStillRequiresExplicitDeploymentException() {
        OutboundSecurityProperties properties = new OutboundSecurityProperties();
        URI endpoint = URI.create("https://provider.example:8443/api.php");

        assertThrows(SafeHttpException.class, () -> factory(properties).forProvider(
                provider("https://provider.example:8443"), endpoint));

        properties.setProviderAllowedPorts(List.of(8443));
        OutboundRequestPolicy policy = factory(properties).forProvider(
                provider("https://provider.example:8443"), endpoint);
        assertEquals(Set.of(8443), policy.allowedPorts());
    }

    @Test
    void dynamicPolicyStillChecksEveryDnsAnswerAndRechecksOnLaterRequests() throws Exception {
        var calls = new java.util.concurrent.atomic.AtomicInteger();
        var guard = new SsrfGuard(host -> calls.getAndIncrement() == 0
                ? List.of(java.net.InetAddress.getByName("8.8.8.8"))
                : List.of(java.net.InetAddress.getByName("8.8.8.8"), java.net.InetAddress.getByName("10.0.0.1")));
        URI endpoint = URI.create("https://provider.example/api.php");
        var policy = factory(new OutboundSecurityProperties()).forProvider(provider("https://provider.example"), endpoint);
        assertEquals("8.8.8.8", guard.validate(endpoint, policy).pinnedAddresses().get(0).getHostAddress());
        assertEquals(SafeHttpException.Reason.PRIVATE_ADDRESS,
                assertThrows(SafeHttpException.class, () -> guard.validate(endpoint, policy)).getReason());
        assertEquals(2, calls.get());
    }

    @Test
    void fixedIntegrationsKeepTheirOwnStaticAllowlists() {
        var policies = new OutboundPolicyRegistry(new OutboundSecurityProperties());
        assertEquals(Set.of("aqks.csuft.edu.cn"), policies.aqks().allowedHosts());
        assertEquals(Set.of("challenges.cloudflare.com"), policies.turnstile().allowedHosts());
        for (var method : com.course.platform.infra.external.ApiHttpClient.class.getDeclaredMethods()) {
            if (java.lang.reflect.Modifier.isPublic(method.getModifiers())
                    && (method.getName().equals("postForString") || method.getName().equals("getForString"))) {
                assertEquals(ApiProvider.class, method.getParameterTypes()[0]);
            }
        }
    }
}
