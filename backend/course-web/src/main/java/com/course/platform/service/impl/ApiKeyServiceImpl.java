package com.course.platform.service.impl;

import cn.hutool.core.util.IdUtil;
import com.course.platform.common.exception.BusinessException;
import com.course.platform.common.result.ResultCode;
import com.course.platform.domain.entity.User;
import com.course.platform.infra.persistence.mapper.UserMapper;
import com.course.platform.application.service.auth.ApiKeyService;
import com.course.platform.application.service.support.OperationLogService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

/**
 * API密钥服务实现类
 * 
 * @author AI Assistant
 * @since 2025-01-17
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ApiKeyServiceImpl implements ApiKeyService {

    private final UserMapper userMapper;
    private final OperationLogService operationLogService;

    @Value("${course.business.api-enable-free-threshold:300}")
    private BigDecimal apiEnableFreeThreshold;

    @Value("${course.business.api-enable-fee:10}")
    private BigDecimal apiEnableFee;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String enableApiKey(Long userId, Integer type, Long targetUserId) {
        if (type == 1) {
            // 自己开通
            return enableForSelf(userId);
        } else if (type == 2) {
            // 给下级开通
            return enableForSubordinate(userId, targetUserId);
        } else {
            throw new BusinessException("开通类型错误");
        }
    }

    /**
     * 自己开通API密钥
     */
    private String enableForSelf(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ResultCode.USER_NOT_FOUND);
        }

        // 检查是否已开通
        if (!"0".equals(user.getApiKey()) && user.getApiKey()!=null) {
            throw new BusinessException("API接口已开通");
        }

        // 生成密钥
        String apiKey = IdUtil.simpleUUID().substring(0, 16);

        // 判断是否免费开通
        if (user.getBalance().compareTo(apiEnableFreeThreshold) >= 0) {
            // 免费开通
            user.setApiKey(apiKey);
            userMapper.updateById(user);

            operationLogService.log(userId, "开通API", "免费开通API接口成功", BigDecimal.ZERO, user.getBalance());

            log.info("免费开通API成功：userId={}", userId);

            return apiKey;
        } else {
            // 收费开通
            if (user.getBalance().compareTo(apiEnableFee) < 0) {
                throw new BusinessException(ResultCode.BALANCE_INSUFFICIENT.getCode(), "余额不足，需要" + apiEnableFee + "元");
            }

            user.setApiKey(apiKey);
            user.setBalance(user.getBalance().subtract(apiEnableFee));
            userMapper.updateById(user);

            operationLogService.log(userId, "开通API", 
                    String.format("开通API接口成功，扣费%s元", apiEnableFee),
                    apiEnableFee.negate(), user.getBalance());

            log.info("付费开通API成功：userId={}, fee={}", userId, apiEnableFee);

            return apiKey;
        }
    }

    /**
     * 给下级开通API密钥
     */
    private String enableForSubordinate(Long operatorId, Long targetUserId) {
        if (targetUserId == null) {
            throw new BusinessException("目标用户ID不能为空");
        }

        User operator = userMapper.selectById(operatorId);
        if (operator == null) {
            throw new BusinessException(ResultCode.USER_NOT_FOUND);
        }

        User target = userMapper.selectById(targetUserId);
        if (target == null) {
            throw new BusinessException("目标用户不存在");
        }

        // 检查权限
        if (!target.getParentId().equals(operatorId)) {
            throw new BusinessException("只能给自己的下级开通");
        }

        // 检查是否已开通
        if (!"0".equals(target.getApiKey())) {
            throw new BusinessException("该用户API接口已开通");
        }

        // 检查余额
        BigDecimal cost = new BigDecimal("5");
        if (operator.getBalance().compareTo(cost) < 0) {
            throw new BusinessException("余额不足，需要" + cost + "元");
        }

        // 生成密钥
        String apiKey = IdUtil.simpleUUID().substring(0, 16);

        // 开通API
        target.setApiKey(apiKey);
        userMapper.updateById(target);

        // 扣费
        operator.setBalance(operator.getBalance().subtract(cost));
        userMapper.updateById(operator);

        // 记录日志
        operationLogService.log(operatorId, "开通API",
                String.format("给下级用户[%s]开通API接口，扣费%s元", target.getUsername(), cost),
                cost.negate(), operator.getBalance());

        operationLogService.log(targetUserId, "开通API",
                String.format("上级[%s]为你开通API接口", operator.getUsername()),
                BigDecimal.ZERO, null);

        log.info("给下级开通API成功：operatorId={}, targetUserId={}", operatorId, targetUserId);

        return apiKey;
    }
}

