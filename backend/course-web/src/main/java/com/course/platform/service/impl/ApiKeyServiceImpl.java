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

    @Value("${course.business.api-enable-free-threshold:300}")
    private BigDecimal apiEnableFreeThreshold;

    @Value("${course.business.api-enable-fee:10}")
    private BigDecimal apiEnableFee;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String enableApiKey(Long userId, Integer type, String targetUserUid) {
        if (type == 1) {
            return enableForSelf(userId);
        } else if (type == 2) {
            return enableForSubordinate(userId, targetUserUid);
        }
        throw new BusinessException("开通类型错误");
    }

    private String enableForSelf(Long userId) {
        User user = userMapper.selectById(userId);
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
                "/api-key/enable", "POST", "用户开通/轮换 API Key", free ? "free" : "paid");
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
        User operator = userMapper.selectById(operatorId);
        if (operator == null) {
            throw new BusinessException(ResultCode.USER_NOT_FOUND);
        }
        User target = userMapper.selectOne(new LambdaQueryWrapper<User>()
                .eq(User::getUid, PublicUidUtil.normalize(targetUserUid))
                .last("LIMIT 1"));
        if (target == null) {
            throw new BusinessException("目标用户不存在");
        }
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
                "/api-key/enable", "POST", "上级为下级开通 API Key", "targetUserUid=" + target.getUid());
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
        // 32 hex chars
        return IdUtil.simpleUUID() + IdUtil.simpleUUID().substring(0, 16);
    }

    private void storeApiKey(User user, String plain, String source) {
        // ensure non-empty longer key
        if (plain == null || plain.length() < 16) {
            plain = IdUtil.simpleUUID();
        }
        String prefix = plain.length() >= 8 ? plain.substring(0, 8) : plain;
        String hash = TokenHashUtil.sha256(plain);
        String scopes = "balance:read,orders:read,orders:write,platforms:read";
        LocalDateTime expiresAt = LocalDateTime.now().plusYears(1);
        if (userMapper.updateApiKey(user.getId(), hash, prefix, scopes, expiresAt) != 1) {
            throw new BusinessException(ResultCode.USER_NOT_FOUND);
        }
        log.info("API Key 已生成并哈希存储：userId={}, prefix={}, source={}", user.getId(), prefix, source);
    }
}
