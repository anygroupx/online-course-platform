package com.course.platform.infra.http;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.course.platform.domain.entity.ApiProvider;
import com.course.platform.domain.exception.ProviderRequestException;
import com.course.platform.infra.external.ApiHttpClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.slf4j.LoggerFactory;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ProviderFailureClassificationTest {
    private ApiProvider provider() {
        ApiProvider provider = new ApiProvider();
        provider.setId(42L);
        provider.setProviderType("Daytime");
        provider.setApiUrl("https://provider.example");
        provider.setStatus(ApiProvider.STATUS_ACTIVE);
        return provider;
    }

    private ApiHttpClient adapter(SafeHttpClient transport) {
        return new ApiHttpClient(transport,
                new ProviderOutboundPolicyFactory(new OutboundSecurityProperties(), new ProviderUrlNormalizer()));
    }

    @ParameterizedTest
    @EnumSource(SafeHttpException.Reason.class)
    void everyTransportFailureRetainsOnlyTheSafeCategory(SafeHttpException.Reason reason) {
        SafeHttpClient transport = mock(SafeHttpClient.class);
        when(transport.postForm(any(), any(), any(), any())).thenThrow(new SafeHttpException(reason,
                new IllegalArgumentException("secret-api-key=abc; private-ip=10.1.1.1")));
        ProviderRequestException error = assertThrows(ProviderRequestException.class, () -> adapter(transport)
                .postForString(provider(), "https://provider.example/api.php?act=getmoney", Map.of("key", "secret-api-key")));
        assertEquals(reason.name(), error.getReason().name());
        assertEquals(ProviderRequestException.PUBLIC_MESSAGE, error.getMessage());
        assertNull(error.getCause());
        assertNotNull(error.getErrorId());
        assertFalse(error.toString().contains("secret-api-key"));
    }

    @Test
    void classifiesHttpErrorsWithoutForwardingTheUpstreamBody() {
        SafeHttpClient transport = mock(SafeHttpClient.class);
        when(transport.postForm(any(), any(), any(), any())).thenReturn(
                new SafeHttpResponse(500, "password=upstream-secret", Map.of()));
        var error = assertThrows(ProviderRequestException.class, () -> adapter(transport)
                .postForString(provider(), "https://provider.example/api", Map.of()));
        assertEquals(ProviderRequestException.Reason.HTTP_ERROR, error.getReason());
        assertFalse(error.getMessage().contains("upstream-secret"));
    }

    @Test
    void logsCorrelationAndProviderContextButNeverQueryCredentialsOrUnsafeCauses() {
        SafeHttpClient transport = mock(SafeHttpClient.class);
        when(transport.postForm(any(), any(), any(), any())).thenThrow(new SafeHttpException(
                SafeHttpException.Reason.DNS_FAILURE, new IllegalStateException("10.10.0.1 secret-from-dns")));
        Logger logger = (Logger) LoggerFactory.getLogger(ApiHttpClient.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            var error = assertThrows(ProviderRequestException.class, () -> adapter(transport)
                    .postForString(provider(), "https://provider.example/api?password=query-secret",
                            Map.of("api_key", "body-secret")));
            String logged = appender.list.stream().map(ILoggingEvent::getFormattedMessage)
                    .collect(java.util.stream.Collectors.joining("\n"));
            for (String secret : new String[]{"query-secret", "body-secret", "secret-from-dns", "10.10.0.1"}) {
                assertFalse(logged.contains(secret));
            }
            for (String field : new String[]{"providerId=42", "providerType=Daytime", "operation=POST",
                    "normalizedHost=provider.example", "reason=DNS_FAILURE", "durationMs=", error.getErrorId()}) {
                assertTrue(logged.contains(field), field);
            }
        } finally {
            logger.detachAppender(appender);
            appender.stop();
        }
    }
}
