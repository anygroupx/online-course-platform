package com.course.platform.infra.http;

import com.course.platform.common.exception.BusinessException;
import com.course.platform.infra.external.ApiHttpClient;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.*;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;
import java.net.URI;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class OutboundIntegrationTest {
    @Test void rejectsOriginalUrlBeforeCanonicalizationOrSending() {
        SafeHttpClient transport = mock(SafeHttpClient.class);
        OutboundSecurityProperties properties = new OutboundSecurityProperties();
        properties.setProviderAllowedHosts(List.of("api.example"));
        var policy = new OutboundPolicyRegistry(properties);
        doThrow(new SafeHttpException(SafeHttpException.Reason.BLOCKED_DESTINATION))
                .when(transport).validate(any(), any());
        ApiHttpClient adapter = new ApiHttpClient(transport, policy);
        assertThrows(BusinessException.class, () -> adapter.getForString("https://api.example/#secret", Map.of()));
        verify(transport).validate(eq(URI.create("https://api.example/#secret")), any());
        verify(transport, never()).get(any(), any(), any());
    }
    @Test void appendsEncodedQueryWithoutLosingExistingAction() {
        SafeHttpClient transport = mock(SafeHttpClient.class);
        OutboundSecurityProperties properties = new OutboundSecurityProperties();
        properties.setProviderAllowedHosts(List.of("api.example"));
        when(transport.get(any(), any(), any())).thenAnswer(call -> {
            URI uri = call.getArgument(0);
            assertEquals("act=get&pass=a%2Bb%26c", uri.getRawQuery());
            return new SafeHttpResponse(200,"ok",Map.of());
        });
        assertEquals("ok",new ApiHttpClient(transport,new OutboundPolicyRegistry(properties))
                .getForString("https://api.example/api.php?act=get", Map.of("pass","a+b&c")));
    }
    @Test void emptyEnvironmentListsBindAndFailClosed() {
        var source = new MapConfigurationPropertySource(Map.of(
                "app.security.outbound.provider-allowed-hosts", "",
                "app.security.outbound.provider-http-allowed-hosts", "",
                "app.security.outbound.provider-allowed-ports", ""));
        var properties = new Binder(source).bind("app.security.outbound", Bindable.of(OutboundSecurityProperties.class)).get();
        properties.validate();
        var registry = new OutboundPolicyRegistry(properties);
        assertThrows(IllegalArgumentException.class, registry::provider);
        assertNotNull(registry.turnstile()); assertNotNull(registry.aqks());
    }
    @Test void explicitEnvironmentListsBind() {
        var source = new MapConfigurationPropertySource(Map.of(
                "app.security.outbound.provider-allowed-hosts", "a.example,b.example",
                "app.security.outbound.provider-http-allowed-hosts", "b.example",
                "app.security.outbound.provider-allowed-ports", "8443"));
        var properties = new Binder(source).bind("app.security.outbound", Bindable.of(OutboundSecurityProperties.class)).get();
        var policy = new OutboundPolicyRegistry(properties).provider();
        assertEquals(Set.of("a.example","b.example"),policy.allowedHosts());
        assertEquals(Set.of("b.example"),policy.httpAllowedHosts());
        assertEquals(Set.of(8443),policy.allowedPorts());
    }
}
