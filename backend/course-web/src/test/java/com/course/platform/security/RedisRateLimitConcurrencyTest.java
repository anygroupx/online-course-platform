package com.course.platform.security;

import com.course.platform.application.service.security.SecurityAuditService;
import com.course.platform.config.LoginProtectionProperties;
import com.course.platform.config.RateLimitProperties;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import redis.embedded.RedisServer;

import java.net.ServerSocket;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

class RedisRateLimitConcurrencyTest {

    private static RedisServer redisServer;
    private static LettuceConnectionFactory connectionFactory;
    private static StringRedisTemplate redis;

    @BeforeAll
    static void startRedis() throws Exception {
        int port;
        try (ServerSocket socket = new ServerSocket(0)) {
            port = socket.getLocalPort();
        }
        redisServer = RedisServer.newRedisServer().port(port).bind("127.0.0.1").build();
        redisServer.start();
        connectionFactory = new LettuceConnectionFactory("127.0.0.1", port);
        connectionFactory.afterPropertiesSet();
        redis = new StringRedisTemplate(connectionFactory);
        redis.afterPropertiesSet();
    }

    @AfterAll
    static void stopRedis() throws Exception {
        if (connectionFactory != null) connectionFactory.destroy();
        if (redisServer != null && Boolean.TRUE.equals(redisServer.isActive())) redisServer.stop();
    }

    @Test
    void luaSlidingWindowNeverExceedsLimitUnderConcurrencyAndHashesPii() throws Exception {
        RateLimitProperties properties = new RateLimitProperties();
        RedisRateLimitService service = new RedisRateLimitService(redis, properties);
        String rawAccount = "Alice.Sensitive@example.com";
        int attempts = 100;
        int limit = 25;
        ExecutorService executor = Executors.newFixedThreadPool(20);
        CountDownLatch ready = new CountDownLatch(20);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Boolean>> futures = new ArrayList<>();
        try {
            for (int i = 0; i < attempts; i++) {
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    start.await();
                    return service.check(new RateLimitRequest(
                            "login:account", rawAccount, limit, Duration.ofSeconds(30), "login")).allowed();
                }));
            }
            ready.await();
            start.countDown();
            int allowed = 0;
            for (Future<Boolean> future : futures) if (future.get()) allowed++;
            assertEquals(limit, allowed);

            Set<String> keys = redis.keys("rl:login:account:*");
            assertNotNull(keys);
            assertEquals(1, keys.size());
            assertFalse(keys.iterator().next().contains("Alice"));
            assertFalse(keys.iterator().next().contains("example.com"));
            assertFalse(service.check(new RateLimitRequest(
                    "login:account", rawAccount, limit, Duration.ofSeconds(30), "login")).allowed());
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void loginProtectionEscalatesChallengeThrottleAndTemporaryBlock() {
        redis.getConnectionFactory().getConnection().serverCommands().flushDb();
        RateLimitProperties rateProperties = new RateLimitProperties();
        RedisRateLimitService rateService = new RedisRateLimitService(redis, rateProperties);
        LoginProtectionProperties loginProperties = new LoginProtectionProperties();
        loginProperties.setChallengeThreshold(3);
        loginProperties.setThrottleThreshold(5);
        loginProperties.setBlockThreshold(7);
        loginProperties.setFailureWindowSeconds(60);
        loginProperties.setThrottleWindowSeconds(30);
        LoginProtectionService protection = new LoginProtectionService(
                redis, rateService, loginProperties, mock(SecurityAuditService.class));

        String username = "victim@example.com";
        String ip = "203.0.113.9";
        for (int i = 0; i < 3; i++) protection.recordFailure(username, ip);
        LoginProtectionDecision challenge = protection.check(username, ip);
        assertTrue(challenge.allowed());
        assertTrue(challenge.challengeRequired());

        for (int i = 3; i < 5; i++) protection.recordFailure(username, ip);
        assertTrue(protection.check(username, ip).allowed());
        LoginProtectionDecision throttled = protection.check(username, ip);
        assertFalse(throttled.allowed());
        assertTrue(throttled.retryAfterSeconds() > 0);

        for (int i = 5; i < 7; i++) protection.recordFailure(username, ip);
        LoginProtectionDecision blocked = protection.check(username, ip);
        assertFalse(blocked.allowed());
        assertEquals(7, blocked.failureCount());
        assertTrue(blocked.retryAfterSeconds() > 0);
    }
}
