package com.course.platform.security;

import com.course.platform.application.service.security.SecurityAuditService;
import com.course.platform.common.exception.BusinessException;
import com.course.platform.common.result.ResultCode;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class TurnstileVerifierTest {

    @Test
    void disabledVerifierFailsClosedWhenRiskEngineRequiresChallenge() {
        SecurityAuditService audit = mock(SecurityAuditService.class);
        TurnstileVerifier verifier = verifier(new RestTemplate(), audit, false, false, "dev", "");

        BusinessException error = assertThrows(BusinessException.class,
                () -> verifier.verify("token", "login", true));

        assertEquals(ResultCode.HUMAN_VERIFICATION_UNAVAILABLE.getCode(), error.getCode());
        verify(audit).record(eq("TURNSTILE_FAILED"), eq("WARN"), isNull(), isNull(),
                eq("/auth/login"), eq("POST"), anyString(), contains("verification-disabled"));
    }

    @Test
    void productionRequiresExpectedHostnameAtStartup() {
        TurnstileVerifier verifier = verifier(new RestTemplate(), mock(SecurityAuditService.class),
                true, true, "prod", "");
        assertThrows(IllegalStateException.class, verifier::validateConfiguration);
    }

    @Test
    void validatesSuccessHostnameAndActionWithoutLoggingToken() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        server.expect(requestTo("https://turnstile.test/siteverify"))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_FORM_URLENCODED))
                .andRespond(withSuccess("""
                        {"success":true,"hostname":"course.example.com","action":"login","error-codes":[]}
                        """, MediaType.APPLICATION_JSON));
        TurnstileVerifier verifier = verifier(restTemplate, mock(SecurityAuditService.class),
                true, true, "prod", "course.example.com");

        assertDoesNotThrow(() -> verifier.verify("opaque-one-time-token", "login", false));
        server.verify();
    }

    @Test
    void actionMismatchAndProviderFailureAreRejected() {
        SecurityAuditService audit = mock(SecurityAuditService.class);
        RestTemplate mismatchTemplate = new RestTemplate();
        MockRestServiceServer mismatchServer = MockRestServiceServer.bindTo(mismatchTemplate).build();
        mismatchServer.expect(requestTo("https://turnstile.test/siteverify"))
                .andRespond(withSuccess("""
                        {"success":true,"hostname":"course.example.com","action":"register","error-codes":[]}
                        """, MediaType.APPLICATION_JSON));
        TurnstileVerifier mismatch = verifier(mismatchTemplate, audit, true, true,
                "prod", "course.example.com");
        BusinessException mismatchError = assertThrows(BusinessException.class,
                () -> mismatch.verify("token", "login", false));
        assertEquals(ResultCode.HUMAN_VERIFICATION_FAILED.getCode(), mismatchError.getCode());

        RestTemplate failedTemplate = new RestTemplate();
        MockRestServiceServer failedServer = MockRestServiceServer.bindTo(failedTemplate).build();
        failedServer.expect(requestTo("https://turnstile.test/siteverify")).andRespond(withServerError());
        TurnstileVerifier failed = verifier(failedTemplate, audit, true, true,
                "prod", "course.example.com");
        BusinessException unavailable = assertThrows(BusinessException.class,
                () -> failed.verify("token", "login", false));
        assertEquals(ResultCode.HUMAN_VERIFICATION_UNAVAILABLE.getCode(), unavailable.getCode());
        verify(audit).record(eq("TURNSTILE_FAILED"), eq("CRITICAL"), isNull(), isNull(),
                eq("/auth/login"), eq("POST"), anyString(), contains("provider-unavailable"));
    }

    private TurnstileVerifier verifier(RestTemplate restTemplate, SecurityAuditService audit,
                                       boolean enabled, boolean alwaysRequired,
                                       String profile, String hostname) {
        TurnstileVerifier verifier = new TurnstileVerifier(restTemplate, audit);
        ReflectionTestUtils.setField(verifier, "enabled", enabled);
        ReflectionTestUtils.setField(verifier, "alwaysRequired", alwaysRequired);
        ReflectionTestUtils.setField(verifier, "activeProfile", profile);
        ReflectionTestUtils.setField(verifier, "secretKey", "test-secret");
        ReflectionTestUtils.setField(verifier, "expectedHostname", hostname);
        ReflectionTestUtils.setField(verifier, "verifyUrl", "https://turnstile.test/siteverify");
        return verifier;
    }
}
