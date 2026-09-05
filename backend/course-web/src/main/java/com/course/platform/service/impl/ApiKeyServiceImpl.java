package com.course.platform.service.impl;

import cn.hutool.core.util.IdUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.course.platform.application.service.auth.ApiKeyService;
import com.course.platform.application.service.security.SecurityAuditService;
import com.course.platform.application.service.support.OperationLogService;
import com.course.platform.common.exception.BusinessException;
import com.course.platform.common.result.ResultCode;
import com.course.platform.common.security.TokenHashUtil;
import com.course.platform.common.util.PublicUidUtil;
import com.course.platform.domain.entity.User;
import com.course.platform.infra.persistence.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * API密钥服务实现：明文仅返回一次，落库仅存哈希/前缀/作用域/过期时间
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ApiKeyServiceImpl implements ApiKeyService {

    private final UserMapper userMapper;
    private final OperationLogService operationLogService;
    private final AccountLedgerServiceImpl accountLedgerService;
    private final SecurityAuditService securityAuditService;
    private final PasswordEncoder passwordEncoder;

    @Value("${course.business.api-enable-free-threshold:300}")
    private BigDecimal apiEnableFreeThreshold;

    @Value("${course.business.api-enable-fee:10}")
    private BigDecimal apiEnableFee;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String enableApiKey(Long userId, Integer type, String targetUserUid) {
        if (Integer.valueOf(1).equals(type)) {
            return enableForSelf(userId);
        } else if (Integer.valueOf(2).equals(type)) {
            return enableForSubordinate(userId, targetUserUid);
        }
        throw new BusinessException("开通类型错误");
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String rotateApiKey(Long userId, String currentPassword) {
        User user = userMapper.selectByIdForUpdate(userId);
        if (user == null) throw new BusinessException(ResultCode.USER_NOT_FOUND);
        if (Integer.valueOf(0).equals(user.getStatus())) throw new BusinessException(ResultCode.ACCOUNT_DISABLED);
        if (currentPassword == null || !passwordEncoder.matches(currentPassword, user.getPassword())) {
            throw new BusinessException(ResultCode.USERNAME_OR_PASSWORD_ERROR);
        }
        if (!hasActiveApiKey(user)) throw new BusinessException(ResultCode.API_KEY_NOT_ENABLED);
        String plain = generatePlainApiKey();
        // Rotation must not restore permissions an administrator has deliberately removed.
        persistApiKey(user, plain, user.getApiKeyScopes());
        securityAuditService.record("KEY_CHANGE", "WARN", userId, user.getUsername(),
                "/api-keys/rotate", "POST", "用户轮换 API Key，旧密钥立即失效", null);
        return plain;
    }

    private String enableForSelf(Long userId) {
        // Serialize issuance with account debits so concurrent clicks cannot charge twice.
        User user = userMapper.selectByIdForUpdate(userId);
        if (user == null) {
            throw new BusinessException(ResultCode.USER_NOT_FOUND);
        }
        if (hasActiveApiKey(user)) {
            throw new BusinessException("API接口已开通");
        }

        boolean free = user.getBalance() != null
                && user.getBalance().compareTo(apiEnableFreeThreshold) >= 0;
        if (!free) {
            accountLedgerService.debit(
                    userId,
                    apiEnableFee,
                    AccountLedgerServiceImpl.BIZ_API_FEE,
                    "API-ENABLE-" + userId + "-" + System.currentTimeMillis(),
                    "开通API接口扣费"
            );
            user = userMapper.selectById(userId);
        }

        String plain = generatePlainApiKey();
        storeApiKey(user, plain, free ? "免费开通" : "付费开通");
        securityAuditService.record("KEY_CHANGE", "WARN", userId, user.getUsername(),
                "/api-keys/enable", "POST", "用户开通/轮换 API Key", free ? "free" : "paid");
        operationLogService.log(userId, "开通API",
                free ? "免费开通API接口成功" : String.format("开通API接口成功，扣费%s元", apiEnableFee),
                free ? BigDecimal.ZERO : apiEnableFee.negate(),
                user.getBalance());
        return plain;
    }

    private String enableForSubordinate(Long operatorId, String targetUserUid) {
        if (!PublicUidUtil.isValid(targetUserUid)) {
            throw new BusinessException("目标用户 UUID 格式错误");
        }
        User operator = userMapper.selectByIdForUpdate(operatorId);
        if (operator == null) {
            throw new BusinessException(ResultCode.USER_NOT_FOUND);
        }
        User target = userMapper.selectOne(new LambdaQueryWrapper<User>()
                .eq(User::getUid, PublicUidUtil.normalize(targetUserUid))
                .last("LIMIT 1"));
        if (target == null) {
            throw new BusinessException("目标用户不存在");
        }
        target = userMapper.selectByIdForUpdate(target.getId());
        if (target == null) throw new BusinessException(ResultCode.USER_NOT_FOUND);
        if (target.getParentId() == null || !target.getParentId().equals(operatorId)) {
            throw new BusinessException("只能给自己的下级开通");
        }
        if (hasActiveApiKey(target)) {
            throw new BusinessException("该用户API接口已开通");
        }

        BigDecimal cost = new BigDecimal("5");
        accountLedgerService.debit(
                operatorId,
                cost,
                AccountLedgerServiceImpl.BIZ_API_FEE,
                "API-SUB-" + target.getUid() + "-" + System.currentTimeMillis(),
                String.format("给下级[%s]开通API", target.getUsername())
        );
        operator = userMapper.selectById(operatorId);

        String plain = generatePlainApiKey();
        storeApiKey(target, plain, "上级开通");
        securityAuditService.record("KEY_CHANGE", "WARN", operatorId, operator.getUsername(),
                "/api-keys/enable", "POST", "上级为下级开通 API Key", "targetUserUid=" + target.getUid());
        operationLogService.log(operatorId, "开通API",
                String.format("给下级用户[%s]开通API接口，扣费%s元", target.getUsername(), cost),
                cost.negate(), operator.getBalance());
        operationLogService.log(target.getId(), "开通API",
                String.format("上级[%s]为你开通API接口", operator.getUsername()),
                BigDecimal.ZERO, null);
        return plain;
    }

    private boolean hasActiveApiKey(User user) {
        return StringUtils.hasText(user.getApiKeyHash());
    }

    private String generatePlainApiKey() {
        // 48 hex chars (192 bits); never returned again after issuance.
        return IdUtil.simpleUUID() + IdUtil.simpleUUID().substring(0, 16);
    }

    private void storeApiKey(User user, String plain, String source) {
        persistApiKey(user, plain, "balance:read,orders:read,orders:write,platforms:read");
        log.info("API Key 已生成并哈希存储：userId={}, source={}", user.getId(), source);
    }

    private void persistApiKey(User user, String plain, String scopes) {
        String prefix = plain.substring(0, 8);
        String hash = TokenHashUtil.sha256(plain);
        LocalDateTime expiresAt = LocalDateTime.now().plusYears(1);
        if (userMapper.updateApiKey(user.getId(), hash, prefix, scopes, expiresAt) != 1) {
            throw new BusinessException(ResultCode.USER_NOT_FOUND);
        }
    }
}
