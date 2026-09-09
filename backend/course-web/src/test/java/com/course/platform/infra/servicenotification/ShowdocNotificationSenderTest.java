package com.course.platform.infra.servicenotification;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.course.platform.domain.servicenotification.ServiceNotificationTypes.*;
import com.course.platform.infra.http.*;

import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.net.URI;
import java.util.*;

class ShowdocNotificationSenderTest {
    SafeHttpClient http;
    ShowdocNotificationSender sender;
    static final String TOKEN = "privateShowdocKey0123456789abcdef";

    @BeforeEach
    void setup() {
        http = mock(SafeHttpClient.class);
        sender = new ShowdocNotificationSender(http);
    }

    @Test
    void fixedHostFormAndBoundedSafeHttpPolicyDoNotAcceptAUserUrl() {
        when(http.postForm(any(), anyMap(), anyMap(), any()))
                .thenReturn(new SafeHttpResponse(200, "{\"error_code\":0}", Map.of()));
        assertEquals(Receipt.ACCEPTED, sender.send(TOKEN, new Message("通知", "本平台状态更新")));
        verify(http)
                .postForm(
                        eq(URI.create("https://push.showdoc.com.cn/server/api/push/" + TOKEN)),
                        eq(Map.of("title", "通知", "content", "本平台状态更新")),
                        eq(Map.of()),
                        argThat(
                                p ->
                                        p.allowedHosts().equals(Set.of("push.showdoc.com.cn"))
                                                && p.allowedPorts().equals(Set.of(443))
                                                && p.httpAllowedHosts().isEmpty()
                                                && p.maxResponseBytes() == 4096
                                                && p.callTimeout().toSeconds() == 8));
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "https://push.showdoc.com.cn/server/api/push/key",
                "../private0123456789",
                "a1234567890?foo=bar",
                "a12345678901234%2f",
                "abc\n0123456789012345",
                "tiny",
                "a123456789012345@evil.example"
            })
    void invalidCredentialsNeverBecomeDestinations(String key) {
        assertEquals(Receipt.REJECTED, sender.send(key, new Message("标题", "正文")));
        verifyNoInteractions(http);
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "{}",
                "{\"error_code\":\"0\"}",
                "{\"error_code\":0,\"error_code\":1}",
                "{\"error_code\":0} {}",
                "<html>login</html>",
                "null",
                "{\"error_code\":false}"
            })
    void uncertainResponsesCannotBeCalledDeliveredOrRetried(String body) {
        when(http.postForm(any(), anyMap(), anyMap(), any()))
                .thenReturn(new SafeHttpResponse(200, body, Map.of()));
        assertEquals(Receipt.UNKNOWN, sender.send(TOKEN, new Message("标题", "正文")));
        verify(http, times(1)).postForm(any(), anyMap(), anyMap(), any());
    }

    @Test
    void explicitRejectionIsNotPaymentFailureAndTransportExceptionIsSanitized() {
        when(http.postForm(any(), anyMap(), anyMap(), any()))
                .thenReturn(
                        new SafeHttpResponse(
                                200,
                                "{\"error_code\":101,\"error_message\":\"" + TOKEN + "\"}",
                                Map.of()));
        assertEquals(Receipt.REJECTED, sender.send(TOKEN, new Message("标题", "正文")));
        when(http.postForm(any(), anyMap(), anyMap(), any()))
                .thenThrow(new RuntimeException("secret=" + TOKEN));
        assertEquals(Receipt.UNKNOWN, sender.send(TOKEN, new Message("标题", "正文")));
    }

    @Test
    void redirectsErrorsAndOversizeBodiesDoNotProveAcceptance() {
        for (var r :
                List.of(
                        new SafeHttpResponse(302, "{\"error_code\":0}", Map.of()),
                        new SafeHttpResponse(503, "{\"error_code\":0}", Map.of()),
                        new SafeHttpResponse(200, " ".repeat(4097), Map.of()))) {
            when(http.postForm(any(), anyMap(), anyMap(), any())).thenReturn(r);
            assertEquals(Receipt.UNKNOWN, sender.send(TOKEN, new Message("标题", "正文")));
        }
        verify(http, times(3)).postForm(any(), anyMap(), anyMap(), any());
    }
}
