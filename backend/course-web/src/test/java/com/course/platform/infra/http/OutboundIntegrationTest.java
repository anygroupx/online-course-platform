package com.course.platform.infra.http;

import com.course.platform.common.exception.BusinessException;
import com.course.platform.domain.entity.ApiProvider;
import com.course.platform.infra.external.ApiHttpClient;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OutboundIntegrationTest {

    private ApiProvider provider(String url) {
        ApiProvider provider = new ApiProvider();
        provider.setId(7L);
        provider.setApiUrl(url);
        provider.setStatus(ApiProvider.STATUS_ACTIVE);
        return provider;
    }

    private ApiHttpClient adapter(SafeHttpClient transport, OutboundSecurityProperties properties) {
        ProviderUrlNormalizer normalizer = new ProviderUrlNormalizer();
        return new ApiHttpClient(transport, new ProviderOutboundPolicyFactory(properties, normalizer));
    }

    @Test
    void rejectsOriginalUrlBeforeCanonicalizationOrSending() {
        SafeHttpClient transport = mock(SafeHttpClient.class);
        OutboundSecurityProperties properties = new OutboundSecurityProperties();
        ApiHttpClient adapter = adapter(transport, properties);
        ApiProvider provider = provider("https://api.example");

        assertThrows(BusinessException.class,
                () -> adapter.getForString(provider, "https://api.example/#secret", Map.of()));

        verify(transport, never()).validate(any(), any());
        verify(transport, never()).get(any(), any(), any());
    }

    @Test
    void appendsEncodedQueryWithoutLosingExistingAction() {
        SafeHttpClient transport = mock(SafeHttpClient.class);
        OutboundSecurityProperties properties = new OutboundSecurityProperties();
        when(transport.get(any(), any(), any())).thenAnswer(call -> {
            URI uri = call.getArgument(0);
            assertEquals("act=get&pass=a%2Bb%26c", uri.getRawQuery());
            return new SafeHttpResponse(200, "ok", Map.of());
        });

        assertEquals("ok", adapter(transport, properties).getForString(
                provider("https://api.example"),
                "https://api.example/api.php?act=get", Map.of("pass", "a+b&c")));
    }

    @Test
    void emptyEnvironmentExceptionsStillAllowDynamicHttpsProvider() {
        var source = new MapConfigurationPropertySource(Map.of(
                "app.security.outbound.provider-http-allowed-hosts", "",
                "app.security.outbound.provider-allowed-ports", ""));
        var properties = new Binder(source).bind(
                "app.security.outbound", Bindable.of(OutboundSecurityProperties.class)).get();
        properties.validate();

        OutboundRequestPolicy policy = new ProviderOutboundPolicyFactory(
                properties, new ProviderUrlNormalizer()).forCandidate(URI.create("https://api.example"));
        assertEquals(Set.of("api.example"), policy.allowedHosts());
        assertEquals(Set.of(), policy.httpAllowedHosts());
    }

    @Test
    void explicitHttpAndPortExceptionsStillBind() {
        var source = new MapConfigurationPropertySource(Map.of(
                "app.security.outbound.provider-http-allowed-hosts", "legacy.example",
                "app.security.outbound.provider-allowed-ports", "8443"));
        var properties = new Binder(source).bind(
                "app.security.outbound", Bindable.of(OutboundSecurityProperties.class)).get();
        assertEquals(List.of("legacy.example"), properties.getProviderHttpAllowedHosts());
        assertEquals(List.of(8443), properties.getProviderAllowedPorts());
    }

}
