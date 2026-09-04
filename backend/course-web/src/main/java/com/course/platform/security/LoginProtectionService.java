package com.course.platform.security;

import com.course.platform.application.service.security.SecurityAuditService;
import com.course.platform.common.exception.BusinessException;
import com.course.platform.common.result.ResultCode;
import com.course.platform.common.security.TokenHashUtil;
import com.course.platform.config.LoginProtectionProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

/** Multi-dimensional brute-force, credential-stuffing and password-spraying defense. */
@Slf4j
@Service
public class LoginProtectionService {

    private static final DefaultRedisScript<Long> INCREMENT_WITH_TTL = new DefaultRedisScript<>("""
            local count = redis.call('INCR', KEYS[1])
            if count == 1 then redis.call('EXPIRE', KEYS[1], ARGV[1]) end
            return count
            """, Long.class);

    private final StringRedisTemplate redisTemplate;
    private final RateLimitService rateLimitService;
    private final LoginProtectionProperties properties;
    private final SecurityAuditService securityAuditService;

    public LoginProtectionService(StringRedisTemplate redisTemplate,
                                  RateLimitService rateLimitService,
                                  LoginProtectionProperties properties,
                                  SecurityAuditService securityAuditService) {
        this.redisTemplate = redisTemplate;
        this.rateLimitService = rateLimitService;
        this.properties = properties;
        this.securityAuditService = securityAuditService;
    }

    public LoginProtectionDecision check(String username, String ipAddress) {
        if (!properties.isEnabled()) {
            return new LoginProtectionDecision(true, false, 0, false, 0);
        }
        String accountKey = accountFailureKey(username);
        String ipKey = ipFailureKey(ipAddress);
        try {
            int failures = Math.max(readCount(accountKey), readCount(ipKey));
            boolean recentSuccess = Boolean.TRUE.equals(redisTemplate.hasKey(accountSuccessKey(username)));
            if (failures >= properties.getBlockThreshold()) {
                long retry = retryAfter(accountKey, ipKey);
                auditBlocked(username, "failure-threshold", failures, retry);
                return new LoginProtectionDecision(false, true, failures, recentSuccess, retry);
            }
            if (failures >= properties.getThrottleThreshold()) {
                RateLimitDecision throttle = rateLimitService.check(new RateLimitRequest(
                        "login:throttle", normalize(username) + "|" + safeIp(ipAddress), 1,
                        Duration.ofSeconds(properties.getThrottleWindowSeconds()), "login"));
                if (!throttle.allowed()) {
                    auditBlocked(username, "temporary-throttle", failures, throttle.retryAfterSeconds());
                    return new LoginProtectionDecision(false, true, failures, recentSuccess,
                            throttle.retryAfterSeconds());
                }
            }
            return new LoginProtectionDecision(true,
                    failures >= properties.getChallengeThreshold(), failures, recentSuccess, 0);
        } catch (DataAccessException | IllegalStateException ex) {
            log.error("Login protection unavailable: {}", ex.getClass().getSimpleName());
            throw new BusinessException(ResultCode.RATE_LIMIT_UNAVAILABLE);
        }
    }

    public void recordFailure(String username, String ipAddress) {
        if (!properties.isEnabled()) return;
        try {
            long account = increment(accountFailureKey(username));
            long ip = increment(ipFailureKey(ipAddress));
            securityAuditService.record("AUTH_LOGIN_FAILED", "WARN", null, null,
                    "/auth/login", "POST", "登录凭据验证失败",
                    "accountFailures=" + account + ",ipFailures=" + ip);
        } catch (DataAccessException | IllegalStateException ex) {
            log.error("Cannot persist login failure counter: {}", ex.getClass().getSimpleName());
            throw new BusinessException(ResultCode.RATE_LIMIT_UNAVAILABLE);
        }
    }

    public void recordSuccess(String username) {
        if (!properties.isEnabled()) return;
        try {
            redisTemplate.delete(accountFailureKey(username));
            redisTemplate.opsForValue().set(accountSuccessKey(username), "1", Duration.ofHours(24));
        } catch (DataAccessException ex) {
            // The request already passed the pre-auth fail-closed check. Do not orphan a newly
            // issued session solely because post-success bookkeeping became unavailable.
            log.error("Cannot persist login success marker: {}", ex.getClass().getSimpleName());
        }
    }

    String accountFailureKey(String username) {
        return "lp:fail:account:" + TokenHashUtil.sha256(normalize(username));
    }

    String ipFailureKey(String ipAddress) {
        return "lp:fail:ip:" + TokenHashUtil.sha256(safeIp(ipAddress));
    }

    private String accountSuccessKey(String username) {
        return "lp:success:account:" + TokenHashUtil.sha256(normalize(username));
    }

    private long increment(String key) {
        Long value = redisTemplate.execute(INCREMENT_WITH_TTL, List.of(key),
                Long.toString(properties.getFailureWindowSeconds()));
        if (value == null) throw new IllegalStateException("empty Redis counter response");
        return value;
    }

    private int readCount(String key) {
        String value = redisTemplate.opsForValue().get(key);
        if (value == null) return 0;
        try {
            return Math.max(0, Integer.parseInt(value));
        } catch (NumberFormatException ex) {
            throw new IllegalStateException("invalid Redis login counter", ex);
        }
    }

    private long retryAfter(String... keys) {
        long max = 1;
        for (String key : keys) {
            Long ttl = redisTemplate.getExpire(key, TimeUnit.SECONDS);
            if (ttl != null && ttl > max) max = ttl;
        }
        return max;
    }

    private void auditBlocked(String username, String reason, int failures, long retry) {
        securityAuditService.record("AUTH_LOGIN_BLOCKED", "WARN", null, null,
                "/auth/login", "POST", "登录攻击保护已阻止认证尝试",
                "reason=" + reason + ",failures=" + failures + ",retryAfter=" + retry
                        + ",account=" + TokenHashUtil.sha256(normalize(username)).substring(0, 12));
    }

    private String normalize(String value) {
        return value == null ? "missing" : value.trim().toLowerCase(Locale.ROOT);
    }

    private String safeIp(String value) {
        return value == null || value.isBlank() ? "unknown" : value.trim();
    }
}
