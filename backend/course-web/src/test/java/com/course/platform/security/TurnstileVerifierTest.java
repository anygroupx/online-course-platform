package com.course.platform.security;

import com.course.platform.application.service.security.SecurityAuditService;
import com.course.platform.common.exception.BusinessException;
import com.course.platform.common.result.ResultCode;
import com.course.platform.infra.http.OutboundPolicyRegistry;
import com.course.platform.infra.http.OutboundRequestPolicy;
import com.course.platform.infra.http.SafeHttpClient;
import com.course.platform.infra.http.SafeHttpException;
import com.course.platform.infra.http.SafeHttpResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class TurnstileVerifierTest {

    @Test
    void disabledVerifierFailsClosedWhenRiskEngineRequiresChallenge() {
        SecurityAuditService audit = mock(SecurityAuditService.class);
        TurnstileVerifier verifier = verifier(mock(SafeHttpClient.class), audit, false, false, "dev", "");

        BusinessException error = assertThrows(BusinessException.class,
                () -> verifier.verify("token", "login", true));

        assertEquals(ResultCode.HUMAN_VERIFICATION_UNAVAILABLE.getCode(), error.getCode());
        verify(audit).record(eq("TURNSTILE_FAILED"), eq("WARN"), isNull(), isNull(),
                eq("/auth/login"), eq("POST"), anyString(), contains("verification-disabled"));
    }

    @Test
    void productionRequiresExpectedHostnameAtStartup() {
        TurnstileVerifier verifier = verifier(mock(SafeHttpClient.class), mock(SecurityAuditService.class),
                true, true, "prod", "");
        assertThrows(IllegalStateException.class, verifier::validateConfiguration);
    }

    @Test
    void validatesSuccessHostnameAndAction() {
        SafeHttpClient client = mock(SafeHttpClient.class);
        when(client.postForm(any(), anyMap(), anyMap(), any())).thenReturn(new SafeHttpResponse(200, """
                {
                  "success": true,
                  "hostname": "course.example.com",
                  "action": "login",
                  "error-codes": [],
                  "messages": [],
                  "challenge_ts": "2026-09-05T16:02:06.000Z",
                  "cdata": "login-attempt",
                  "metadata": {"ephemeral_id":"opaque"}
                }
                """, Map.of()));
        TurnstileVerifier verifier = verifier(client, mock(SecurityAuditService.class),
                true, true, "prod", "course.example.com");

        assertDoesNotThrow(() -> verifier.verify("opaque-one-time-token", "login", false));
        verify(client).postForm(any(), argThat(form -> "opaque-one-time-token".equals(form.get("response"))
                        && form.get("idempotency_key") instanceof String key && !key.isBlank()),
                anyMap(), any());
    }

    @Test
    void retriesTransientFailureWithStableIdempotencyKey() {
        SafeHttpClient client = mock(SafeHttpClient.class);
        AtomicInteger attempts = new AtomicInteger();
        List<String> idempotencyKeys = new ArrayList<>();
        when(client.postForm(any(), anyMap(), anyMap(), any())).thenAnswer(call -> {
            Map<String, ?> form = call.getArgument(1);
            idempotencyKeys.add(String.valueOf(form.get("idempotency_key")));
            if (attempts.getAndIncrement() == 0) {
                throw new SafeHttpException(SafeHttpException.Reason.DNS_FAILURE);
            }
            return new SafeHttpResponse(200, """
                    {"success":true,"hostname":"course.example.com","action":"register","error-codes":[]}
                    """, Map.of());
        });
        TurnstileVerifier verifier = verifier(client, mock(SecurityAuditService.class),
                true, true, "prod", "course.example.com");

        assertDoesNotThrow(() -> verifier.verify("token", "register", false));
        assertEquals(2, idempotencyKeys.size());
        assertFalse(idempotencyKeys.get(0).isBlank());
        assertEquals(idempotencyKeys.get(0), idempotencyKeys.get(1));
        verify(client, times(2)).postForm(any(), anyMap(), anyMap(), any());
    }

    @Test
    void doesNotRetryPermanentOrClientSideFailures() {
        for (SafeHttpException.Reason reason : List.of(
                SafeHttpException.Reason.BLOCKED_DESTINATION,
                SafeHttpException.Reason.PRIVATE_ADDRESS,
                SafeHttpException.Reason.REDIRECT_BLOCKED,
                SafeHttpException.Reason.RESPONSE_TOO_LARGE)) {
            SafeHttpClient client = mock(SafeHttpClient.class);
            when(client.postForm(any(), anyMap(), anyMap(), any()))
                    .thenThrow(new SafeHttpException(reason));
            TurnstileVerifier verifier = verifier(client, mock(SecurityAuditService.class),
                    true, true, "prod", "course.example.com");

            BusinessException error = assertThrows(BusinessException.class,
                    () -> verifier.verify("token", "register", false));

            assertEquals(ResultCode.HUMAN_VERIFICATION_UNAVAILABLE.getCode(), error.getCode());
            verify(client, times(1)).postForm(any(), anyMap(), anyMap(), any());
        }

        SafeHttpClient clientError = mock(SafeHttpClient.class);
        when(clientError.postForm(any(), anyMap(), anyMap(), any()))
                .thenReturn(new SafeHttpResponse(400, "{}", Map.of()));
        TurnstileVerifier verifier = verifier(clientError, mock(SecurityAuditService.class),
                true, true, "prod", "course.example.com");

        assertThrows(BusinessException.class, () -> verifier.verify("token", "register", false));
        verify(clientError, times(1)).postForm(any(), anyMap(), anyMap(), any());
    }

    @Test
    void retriesServerErrors() {
        SafeHttpClient client = mock(SafeHttpClient.class);
        when(client.postForm(any(), anyMap(), anyMap(), any()))
                .thenReturn(new SafeHttpResponse(503, "{}", Map.of()))
                .thenReturn(new SafeHttpResponse(200,
                        "{\"success\":true,\"hostname\":\"course.example.com\",\"action\":\"register\"}",
                        Map.of()));
        TurnstileVerifier verifier = verifier(client, mock(SecurityAuditService.class),
                true, true, "prod", "course.example.com");

        assertDoesNotThrow(() -> verifier.verify("token", "register", false));
        verify(client, times(2)).postForm(any(), anyMap(), anyMap(), any());
    }

    @Test
    void actionMismatchAndProviderFailureAreRejected() {
        SecurityAuditService audit = mock(SecurityAuditService.class);
        SafeHttpClient mismatchClient = mock(SafeHttpClient.class);
        when(mismatchClient.postForm(any(), anyMap(), anyMap(), any())).thenReturn(new SafeHttpResponse(200, """
                {"success":true,"hostname":"course.example.com","action":"register","error-codes":[]}
                """, Map.of()));
        TurnstileVerifier mismatch = verifier(mismatchClient, audit, true, true,
                "prod", "course.example.com");
        BusinessException mismatchError = assertThrows(BusinessException.class,
                () -> mismatch.verify("token", "login", false));
        assertEquals(ResultCode.HUMAN_VERIFICATION_FAILED.getCode(), mismatchError.getCode());

        SafeHttpClient failedClient = mock(SafeHttpClient.class);
        when(failedClient.postForm(any(), anyMap(), anyMap(), any()))
                .thenThrow(new SafeHttpException(SafeHttpException.Reason.NETWORK_FAILURE));
        TurnstileVerifier failed = verifier(failedClient, audit, true, true,
                "prod", "course.example.com");
        BusinessException unavailable = assertThrows(BusinessException.class,
                () -> failed.verify("token", "login", false));
        assertEquals(ResultCode.HUMAN_VERIFICATION_UNAVAILABLE.getCode(), unavailable.getCode());
        verify(failedClient, times(3)).postForm(any(), anyMap(), anyMap(), any());
        verify(audit).record(eq("TURNSTILE_FAILED"), eq("CRITICAL"), isNull(), isNull(),
                eq("/auth/login"), eq("POST"), anyString(), contains("provider-unavailable"));
    }

    private TurnstileVerifier verifier(SafeHttpClient safeHttpClient, SecurityAuditService audit,
                                       boolean enabled, boolean alwaysRequired,
                                       String profile, String hostname) {
        OutboundPolicyRegistry policies = mock(OutboundPolicyRegistry.class);
        when(policies.turnstile()).thenReturn(new OutboundRequestPolicy("turnstile",
                Set.of("challenges.cloudflare.com"), Set.of(), Set.of(), 4096,
                Duration.ofSeconds(1), Duration.ofSeconds(1), Duration.ofSeconds(2)));
        TurnstileVerifier verifier = new TurnstileVerifier(safeHttpClient, policies, new ObjectMapper(), audit);
        ReflectionTestUtils.setField(verifier, "enabled", enabled);
        ReflectionTestUtils.setField(verifier, "alwaysRequired", alwaysRequired);
        ReflectionTestUtils.setField(verifier, "activeProfile", profile);
        ReflectionTestUtils.setField(verifier, "secretKey", "test-secret");
        ReflectionTestUtils.setField(verifier, "expectedHostname", hostname);
        ReflectionTestUtils.setField(verifier, "verifyUrl", "https://challenges.cloudflare.com/turnstile/v0/siteverify");
        return verifier;
    }
}
