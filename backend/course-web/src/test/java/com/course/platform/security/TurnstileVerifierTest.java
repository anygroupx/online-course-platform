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
import java.util.Map;
import java.util.Set;

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
                {"success":true,"hostname":"course.example.com","action":"login","error-codes":[]}
                """, Map.of()));
        TurnstileVerifier verifier = verifier(client, mock(SecurityAuditService.class),
                true, true, "prod", "course.example.com");

        assertDoesNotThrow(() -> verifier.verify("opaque-one-time-token", "login", false));
        verify(client).postForm(any(), argThat(form -> "opaque-one-time-token".equals(form.get("response"))),
                anyMap(), any());
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
