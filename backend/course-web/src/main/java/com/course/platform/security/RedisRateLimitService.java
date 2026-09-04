package com.course.platform.security;

import com.course.platform.common.exception.BusinessException;
import com.course.platform.common.result.ResultCode;
import com.course.platform.common.security.TokenHashUtil;
import com.course.platform.config.RateLimitProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

/** Atomic Redis sliding-window limiter. PII is SHA-256 hashed before becoming a Redis key. */
@Slf4j
@Service
public class RedisRateLimitService implements RateLimitService {

    private static final DefaultRedisScript<List> SLIDING_WINDOW = new DefaultRedisScript<>("""
            local nowParts = redis.call('TIME')
            local now = (tonumber(nowParts[1]) * 1000) + math.floor(tonumber(nowParts[2]) / 1000)
            local window = tonumber(ARGV[1])
            local limit = tonumber(ARGV[2])
            redis.call('ZREMRANGEBYSCORE', KEYS[1], '-inf', now - window)
            local count = redis.call('ZCARD', KEYS[1])
            if count >= limit then
              local oldest = redis.call('ZRANGE', KEYS[1], 0, 0, 'WITHSCORES')
              local retry = 1
              if oldest[2] then retry = math.max(1, math.ceil((tonumber(oldest[2]) + window - now) / 1000)) end
              redis.call('PEXPIRE', KEYS[1], window)
              return {0, 0, retry}
            end
            redis.call('ZADD', KEYS[1], now, ARGV[3])
            redis.call('PEXPIRE', KEYS[1], window)
            return {1, limit - count - 1, 0}
            """, List.class);

    private final StringRedisTemplate redisTemplate;
    private final RateLimitProperties properties;

    public RedisRateLimitService(StringRedisTemplate redisTemplate, RateLimitProperties properties) {
        this.redisTemplate = redisTemplate;
        this.properties = properties;
    }

    @Override
    public RateLimitDecision check(RateLimitRequest request) {
        if (!properties.isEnabled()) {
            return RateLimitDecision.allowed(request.limit());
        }
        String key = redisKey(request.dimension(), request.keyMaterial());
        try {
            List<?> result = redisTemplate.execute(SLIDING_WINDOW, List.of(key),
                    Long.toString(request.window().toMillis()), Integer.toString(request.limit()),
                    UUID.randomUUID().toString());
            if (result == null || result.size() < 3) {
                return unavailable("empty Redis Lua response");
            }
            boolean allowed = number(result.get(0)) == 1;
            return allowed
                    ? RateLimitDecision.allowed(number(result.get(1)))
                    : RateLimitDecision.denied(number(result.get(2)));
        } catch (DataAccessException | IllegalStateException ex) {
            log.error("Redis rate limiter unavailable: {}", ex.getClass().getSimpleName());
            return unavailable(ex.getClass().getSimpleName());
        }
    }

    @Override
    public void reset(String dimension, String keyMaterial) {
        if (!properties.isEnabled()) return;
        try {
            redisTemplate.delete(redisKey(dimension, keyMaterial));
        } catch (DataAccessException ex) {
            if (properties.isFailClosed()) {
                throw new BusinessException(ResultCode.RATE_LIMIT_UNAVAILABLE);
            }
            log.error("Redis rate-limit reset failed: {}", ex.getClass().getSimpleName());
        }
    }

    String redisKey(String dimension, String keyMaterial) {
        String safeDimension = dimension.replaceAll("[^a-z0-9:_-]", "_");
        return "rl:" + safeDimension + ":" + TokenHashUtil.sha256(keyMaterial.trim());
    }

    private RateLimitDecision unavailable(String reason) {
        if (properties.isFailClosed()) {
            throw new BusinessException(ResultCode.RATE_LIMIT_UNAVAILABLE, "安全限流服务暂不可用");
        }
        log.warn("Rate limiter failed open by explicit configuration: {}", reason);
        return RateLimitDecision.allowed(0);
    }

    private long number(Object value) {
        if (value instanceof Number number) return number.longValue();
        return Long.parseLong(String.valueOf(value));
    }

    public static Duration window(long seconds) {
        return Duration.ofSeconds(Math.max(1, seconds));
    }
}
