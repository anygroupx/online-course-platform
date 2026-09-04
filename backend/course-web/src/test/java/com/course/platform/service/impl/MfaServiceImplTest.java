package com.course.platform.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.course.platform.application.service.security.SecurityAuditService;
import com.course.platform.common.exception.BusinessException;
import com.course.platform.common.result.ResultCode;
import com.course.platform.common.security.SecretCrypto;
import com.course.platform.common.security.TotpUtil;
import com.course.platform.domain.dto.MfaVerifyLoginRequest;
import com.course.platform.domain.entity.MfaChallenge;
import com.course.platform.domain.entity.User;
import com.course.platform.infra.persistence.mapper.MfaChallengeMapper;
import com.course.platform.infra.persistence.mapper.UserMapper;
import com.course.platform.security.RefreshSessionService;
import com.course.platform.security.UserAuthorityService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class MfaServiceImplTest {

    private UserMapper userMapper;
    private MfaChallengeMapper challengeMapper;
    private RefreshSessionService refreshSessionService;
    private MfaServiceImpl service;
    private User user;
    private MfaChallenge challenge;
    private String code;

    @BeforeEach
    void setUp() {
        userMapper = mock(UserMapper.class);
        challengeMapper = mock(MfaChallengeMapper.class);
        refreshSessionService = mock(RefreshSessionService.class);
        SecurityAuditService auditService = mock(SecurityAuditService.class);
        UserAuthorityService authorityService = mock(UserAuthorityService.class);
        service = new MfaServiceImpl(userMapper, challengeMapper, refreshSessionService,
                auditService, authorityService);
        String cryptoKey = "test-mfa-envelope-key-with-enough-entropy";
        ReflectionTestUtils.setField(service, "cryptoSecret", cryptoKey);

        String secret = TotpUtil.generateSecret();
        code = TotpUtil.generateCode(secret);
        user = new User();
        user.setId(42L);
        user.setUid("550e8400-e29b-41d4-a716-446655440000");
        user.setUsername("admin");
        user.setStatus(1);
        user.setMfaEnabled(1);
        user.setMfaSecret(SecretCrypto.encrypt(secret, cryptoKey));

        challenge = new MfaChallenge();
        challenge.setChallengeId("0123456789abcdef0123456789abcdef");
        challenge.setUserId(42L);
        challenge.setConsumed(0);
        challenge.setExpireTime(LocalDateTime.now().plusMinutes(2));
        when(challengeMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(challenge);
        when(userMapper.selectByIdForUpdate(42L)).thenReturn(user);
        when(authorityService.getPrimaryRole(42L)).thenReturn("SUPER_ADMIN");
    }

    @Test
    void challengeMustBeAtomicallyConsumedBeforeSessionIsIssued() {
        when(challengeMapper.consumeIfActive(anyString(), any(LocalDateTime.class))).thenReturn(0);

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.verifyLogin(request()));

        assertEquals(ResultCode.MFA_CHALLENGE_INVALID.getCode(), error.getCode());
        verifyNoInteractions(refreshSessionService);
    }

    @Test
    void consumedChallengeIssuesExactlyOneServerSideSession() {
        when(challengeMapper.consumeIfActive(anyString(), any(LocalDateTime.class))).thenReturn(1);
        when(refreshSessionService.issue(user)).thenReturn(new RefreshSessionService.SessionTokens(
                "access", "rt_" + "a".repeat(64), "b".repeat(32), user));

        var response = service.verifyLogin(request());

        assertEquals("access", response.getToken());
        assertTrue(response.getRefreshToken().matches("rt_[a-f0-9]{64}"));
        verify(challengeMapper).consumeIfActive(eq(challenge.getChallengeId()), any(LocalDateTime.class));
        verify(refreshSessionService).issue(user);
    }

    private MfaVerifyLoginRequest request() {
        MfaVerifyLoginRequest request = new MfaVerifyLoginRequest();
        request.setChallengeId(challenge.getChallengeId());
        request.setCode(code);
        return request;
    }
}
