package com.course.platform.security;

import com.course.platform.application.service.security.SecurityAuditService;
import com.course.platform.common.exception.BusinessException;
import com.course.platform.common.result.ResultCode;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.course.platform.shared.util.ServletUtil;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.List;

/**
 * Cloudflare Turnstile 服务端验证器。
 *
 * <p>仅服务端持有 Secret Key，并校验令牌、业务动作和生产域名。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TurnstileVerifier {

    private final RestTemplate restTemplate;
    private final SecurityAuditService securityAuditService;

    @Value("${cloudflare.turnstile.enabled:false}")
    private boolean enabled;

    @Value("${cloudflare.turnstile.secret-key:}")
    private String secretKey;

    @Value("${cloudflare.turnstile.expected-hostname:}")
    private String expectedHostname;

    @Value("${cloudflare.turnstile.verify-url:https://challenges.cloudflare.com/turnstile/v0/siteverify}")
    private String verifyUrl;

    @Value("${cloudflare.turnstile.always-required:false}")
    private boolean alwaysRequired;

    @Value("${spring.profiles.active:dev}")
    private String activeProfile;

    @PostConstruct
    void validateConfiguration() {
        if (enabled && !StringUtils.hasText(secretKey)) {
            throw new IllegalStateException("TURNSTILE_SECRET_KEY is required when Turnstile is enabled");
        }
        if (enabled && "prod".equalsIgnoreCase(activeProfile) && !StringUtils.hasText(expectedHostname)) {
            throw new IllegalStateException("TURNSTILE_EXPECTED_HOSTNAME is required in production");
        }
    }

    /**
     * 验证 Cloudflare 签发的单次令牌。
     */
    public void verify(String token, String expectedAction) {
        verify(token, expectedAction, false);
    }

    public void verify(String token, String expectedAction, boolean dynamicallyRequired) {
        boolean required = alwaysRequired || dynamicallyRequired;
        if (!required) {
            return;
        }
        if (!enabled) {
            if (required) {
                auditFailure(expectedAction, "verification-disabled", "WARN");
                throw new BusinessException(ResultCode.HUMAN_VERIFICATION_UNAVAILABLE);
            }
            return;
        }
        if (!StringUtils.hasText(token) || token.length() > 2048) {
            auditFailure(expectedAction, "missing-or-invalid-token", "WARN");
            throw new BusinessException(ResultCode.HUMAN_VERIFICATION_FAILED);
        }

        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("secret", secretKey);
        form.add("response", token);
        form.add("remoteip", ServletUtil.getClientIp());

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        TurnstileResponse response;
        try {
            response = restTemplate.postForObject(
                    verifyUrl,
                    new HttpEntity<>(form, headers),
                    TurnstileResponse.class
            );
        } catch (RestClientException ex) {
            log.warn("Cloudflare Turnstile 服务调用失败：{}", ex.getClass().getSimpleName());
            auditFailure(expectedAction, "provider-unavailable", "CRITICAL");
            throw new BusinessException(ResultCode.HUMAN_VERIFICATION_UNAVAILABLE);
        }

        if (response == null || !response.success()) {
            log.warn("Cloudflare Turnstile 验证拒绝：action={}, errors={}",
                    expectedAction, response == null ? List.of("empty-response") : response.errorCodes());
            auditFailure(expectedAction, "provider-rejected", "WARN");
            throw new BusinessException(ResultCode.HUMAN_VERIFICATION_FAILED);
        }
        if (StringUtils.hasText(expectedHostname)
                && !expectedHostname.equalsIgnoreCase(response.hostname())) {
            log.warn("Cloudflare Turnstile 域名不匹配：expected={}, actual={}",
                    expectedHostname, response.hostname());
            auditFailure(expectedAction, "hostname-mismatch", "WARN");
            throw new BusinessException(ResultCode.HUMAN_VERIFICATION_FAILED);
        }
        if (StringUtils.hasText(expectedAction) && !expectedAction.equals(response.action())) {
            log.warn("Cloudflare Turnstile 动作不匹配：expected={}, actual={}",
                    expectedAction, response.action());
            auditFailure(expectedAction, "action-mismatch", "WARN");
            throw new BusinessException(ResultCode.HUMAN_VERIFICATION_FAILED);
        }
    }

    private void auditFailure(String action, String reason, String severity) {
        securityAuditService.record("TURNSTILE_FAILED", severity, null, null,
                "/auth/" + (action == null ? "unknown" : action), "POST",
                "人机验证失败", "action=" + action + ",reason=" + reason);
    }

    private record TurnstileResponse(
            boolean success,
            String hostname,
            String action,
            @JsonProperty("error-codes") List<String> errorCodes
    ) {
    }
}
