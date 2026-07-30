package com.course.platform.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.course.platform.application.service.security.MfaService;
import com.course.platform.application.service.security.SecurityAuditService;
import com.course.platform.application.service.system.SystemConfigService;
import com.course.platform.common.exception.BusinessException;
import com.course.platform.common.result.ResultCode;
import com.course.platform.common.security.SecretCrypto;
import com.course.platform.common.security.SecurityRoles;
import com.course.platform.common.security.TokenHashUtil;
import com.course.platform.common.security.TotpUtil;
import com.course.platform.domain.dto.MfaCodeRequest;
import com.course.platform.domain.dto.MfaConfirmSetupRequest;
import com.course.platform.domain.dto.MfaVerifyLoginRequest;
import com.course.platform.domain.entity.MfaChallenge;
import com.course.platform.domain.entity.RefreshToken;
import com.course.platform.domain.entity.User;
import com.course.platform.domain.vo.LoginResponse;
import com.course.platform.domain.vo.MfaSetupVO;
import com.course.platform.domain.vo.MfaStatusVO;
import com.course.platform.infra.persistence.mapper.MfaChallengeMapper;
import com.course.platform.infra.persistence.mapper.RefreshTokenMapper;
import com.course.platform.infra.persistence.mapper.UserMapper;
import com.course.platform.shared.util.JwtUtil;
import com.course.platform.shared.util.ServletUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class MfaServiceImpl implements MfaService {

    private final UserMapper userMapper;
    private final MfaChallengeMapper mfaChallengeMapper;
    private final RefreshTokenMapper refreshTokenMapper;
    private final JwtUtil jwtUtil;
    private final SystemConfigService systemConfigService;
    private final SecurityAuditService securityAuditService;

    @Value("${app.crypto.secret:}")
    private String cryptoSecret;

    @Value("${app.security.mfa-issuer:OnlineCoursePlatform}")
    private String mfaIssuer;

    /** setupToken -> secret plain (short lived, process memory) */
    private final Map<String, PendingSetup> pendingSetups = new ConcurrentHashMap<>();

    private record PendingSetup(Long userId, String secret, List<String> backupCodes, LocalDateTime expireAt) {}

    @Override
    public MfaStatusVO status(Long userId) {
        User user = requireUser(userId);
        return MfaStatusVO.builder()
                .enabled(isEnabled(user))
                .enabledAt(user.getMfaEnabledAt() == null ? null :
                        user.getMfaEnabledAt().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME))
                .build();
    }

    @Override
    public MfaSetupVO beginSetup(Long userId) {
        User user = requireUser(userId);
        requireAdmin(user);
        String secret = TotpUtil.generateSecret();
        String setupToken = TokenHashUtil.randomHex(16);
        List<String> backupCodes = generateBackupCodes();
        pendingSetups.put(setupToken, new PendingSetup(userId, secret, backupCodes, LocalDateTime.now().plusMinutes(10)));
        cleanupPending();
        String otpauth = TotpUtil.otpAuthUrl(mfaIssuer, user.getUsername(), secret);
        // 备用码仅在 beginSetup 返回一次，confirm 时按同一批哈希入库
        return MfaSetupVO.builder()
                .setupToken(setupToken)
                .otpauthUrl(otpauth)
                .secretMasked(maskSecret(secret))
                .backupCodes(backupCodes)
                .build();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void confirmSetup(Long userId, MfaConfirmSetupRequest request) {
        PendingSetup pending = pendingSetups.get(request.getSetupToken());
        if (pending == null || !pending.userId().equals(userId) || pending.expireAt().isBefore(LocalDateTime.now())) {
            throw new BusinessException(ResultCode.PARAM_ERROR.getCode(), "MFA setupToken 无效或已过期");
        }
        if (!TotpUtil.verify(pending.secret(), request.getCode())) {
            securityAuditService.record("MFA_SETUP_FAIL", "WARN", userId, null, "/auth/mfa/setup/confirm",
                    "POST", "MFA 绑定验证码错误", null);
            throw new BusinessException(ResultCode.MFA_CODE_INVALID);
        }
        User user = requireUser(userId);
        requireAdmin(user);
        user.setMfaEnabled(1);
        user.setMfaSecret(SecretCrypto.encrypt(pending.secret(), cryptoSecret));
        user.setMfaEnabledAt(LocalDateTime.now());
        List<String> backupCodes = pending.backupCodes() == null ? List.of() : pending.backupCodes();
        user.setMfaBackupCodesHash(backupCodes.stream().map(TokenHashUtil::sha256).collect(Collectors.joining(",")));
        userMapper.updateById(user);
        pendingSetups.remove(request.getSetupToken());
        securityAuditService.record("MFA_ENABLED", "INFO", userId, user.getUsername(),
                "/auth/mfa/setup/confirm", "POST", "管理员启用 MFA", null);
        log.info("MFA enabled for user {}", userId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void disable(Long userId, MfaCodeRequest request) {
        User user = requireUser(userId);
        requireAdmin(user);
        if (!isEnabled(user)) {
            return;
        }
        if (!verifyUserCode(user, request.getCode())) {
            securityAuditService.record("MFA_DISABLE_FAIL", "WARN", userId, user.getUsername(),
                    "/auth/mfa/disable", "POST", "MFA 关闭验证码错误", null);
            throw new BusinessException(ResultCode.MFA_CODE_INVALID);
        }
        user.setMfaEnabled(0);
        user.setMfaSecret(null);
        user.setMfaBackupCodesHash(null);
        user.setMfaEnabledAt(null);
        userMapper.updateById(user);
        securityAuditService.record("MFA_DISABLED", "WARN", userId, user.getUsername(),
                "/auth/mfa/disable", "POST", "管理员关闭 MFA", null);
    }

    @Override
    public boolean isEnabled(User user) {
        return user != null && user.getMfaEnabled() != null && user.getMfaEnabled() == 1
                && StringUtils.hasText(user.getMfaSecret());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String createChallenge(User user) {
        String challengeId = TokenHashUtil.randomHex(16);
        MfaChallenge challenge = new MfaChallenge();
        challenge.setChallengeId(challengeId);
        challenge.setUserId(user.getId());
        challenge.setExpireTime(LocalDateTime.now().plusMinutes(5));
        challenge.setConsumed(0);
        challenge.setCreateTime(LocalDateTime.now());
        mfaChallengeMapper.insert(challenge);
        return challengeId;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public LoginResponse verifyLogin(MfaVerifyLoginRequest request) {
        MfaChallenge challenge = mfaChallengeMapper.selectOne(new LambdaQueryWrapper<MfaChallenge>()
                .eq(MfaChallenge::getChallengeId, request.getChallengeId())
                .last("LIMIT 1"));
        if (challenge == null || challenge.getConsumed() != null && challenge.getConsumed() == 1
                || challenge.getExpireTime() == null || challenge.getExpireTime().isBefore(LocalDateTime.now())) {
            throw new BusinessException(ResultCode.MFA_CHALLENGE_INVALID);
        }
        User user = requireUser(challenge.getUserId());
        if (!isEnabled(user)) {
            throw new BusinessException(ResultCode.MFA_REQUIRED);
        }
        if (!verifyUserCode(user, request.getCode())) {
            securityAuditService.record("MFA_LOGIN_FAIL", "WARN", user.getId(), user.getUsername(),
                    "/auth/mfa/verify", "POST", "MFA 登录验证失败", null);
            throw new BusinessException(ResultCode.MFA_CODE_INVALID);
        }
        challenge.setConsumed(1);
        mfaChallengeMapper.updateById(challenge);

        String token = jwtUtil.generateToken(user.getId(), user.getUsername());
        String refreshToken = jwtUtil.generateRefreshToken(user.getId(), user.getUsername());
        Integer expireDays = systemConfigService.getConfigValueAsInteger("refresh_token_expire_days", 7);
        RefreshToken refreshTokenEntity = RefreshToken.builder()
                .userId(user.getId())
                .token(null)
                .tokenHash(TokenHashUtil.sha256(refreshToken))
                .tokenFamilyId(TokenHashUtil.randomHex(16))
                .expireTime(LocalDateTime.now().plusDays(expireDays))
                .lastUsedIp(safeIp())
                .build();
        refreshTokenMapper.insert(refreshTokenEntity);

        String role = resolveRole(user);
        securityAuditService.record("MFA_LOGIN_SUCCESS", "INFO", user.getId(), user.getUsername(),
                "/auth/mfa/verify", "POST", "MFA 登录成功", null);
        return LoginResponse.builder()
                .token(token)
                .refreshToken(refreshToken)
                .userId(user.getId())
                .username(user.getUsername())
                .nickname(StringUtils.hasText(user.getNickname()) ? user.getNickname() : user.getUsername())
                .balance(user.getBalance())
                .rate(user.getRate())
                .isAdmin(SecurityRoles.ADMIN.equals(role))
                .role(role)
                .mustChangePassword(user.getMustChangePassword() != null && user.getMustChangePassword() == 1)
                .mfaRequired(false)
                .mfaEnabled(true)
                .build();
    }

    private boolean verifyUserCode(User user, String code) {
        if (!StringUtils.hasText(code)) {
            return false;
        }
        String secret = SecretCrypto.decrypt(user.getMfaSecret(), cryptoSecret);
        if (TotpUtil.verify(secret, code)) {
            return true;
        }
        // backup codes
        if (!StringUtils.hasText(user.getMfaBackupCodesHash())) {
            return false;
        }
        String hash = TokenHashUtil.sha256(code.trim());
        List<String> hashes = new ArrayList<>(Arrays.asList(user.getMfaBackupCodesHash().split(",")));
        boolean matched = hashes.remove(hash);
        if (matched) {
            user.setMfaBackupCodesHash(String.join(",", hashes));
            userMapper.updateById(user);
            securityAuditService.record("MFA_BACKUP_USED", "WARN", user.getId(), user.getUsername(),
                    "/auth/mfa", "POST", "使用了 MFA 备用恢复码", null);
            return true;
        }
        return false;
    }

    private List<String> generateBackupCodes() {
        List<String> codes = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            codes.add(TokenHashUtil.randomHex(4));
        }
        return codes;
    }

    private void cleanupPending() {
        LocalDateTime now = LocalDateTime.now();
        pendingSetups.entrySet().removeIf(e -> e.getValue().expireAt().isBefore(now));
    }

    private String maskSecret(String secret) {
        if (secret == null || secret.length() < 8) {
            return "****";
        }
        return secret.substring(0, 4) + "****" + secret.substring(secret.length() - 4);
    }

    private User requireUser(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ResultCode.USER_NOT_FOUND);
        }
        return user;
    }

    private void requireAdmin(User user) {
        String role = resolveRole(user);
        if (!SecurityRoles.ADMIN.equals(role)) {
            throw new BusinessException(ResultCode.FORBIDDEN.getCode(), "仅管理员可配置 MFA");
        }
    }

    private String resolveRole(User user) {
        if (StringUtils.hasText(user.getRole())) {
            return user.getRole().trim().toUpperCase();
        }
        return SecurityRoles.USER;
    }

    private String safeIp() {
        try {
            return ServletUtil.getClientIp();
        } catch (Exception e) {
            return "127.0.0.1";
        }
    }
}
