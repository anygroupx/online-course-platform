package com.course.platform.service.impl;

import cn.hutool.core.util.RandomUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.course.platform.infra.cache.SystemVariableCache;
import com.course.platform.shared.constant.Constants;
import com.course.platform.shared.exception.BusinessException;
import com.course.platform.shared.result.ResultCode;
import com.course.platform.domain.dto.InviteCodeRequest;
import com.course.platform.domain.dto.RegisterRequest;
import com.course.platform.domain.entity.SystemConfig;
import com.course.platform.domain.entity.User;
import com.course.platform.mapper.SystemConfigMapper;
import com.course.platform.mapper.UserMapper;
import com.course.platform.service.OperationLogService;
import com.course.platform.service.RegisterService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

/**
 * 用户注册服务实现类
 * 
 * @author AI Assistant
 * @since 2025-01-17
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RegisterServiceImpl implements RegisterService {

    private final UserMapper userMapper;
    private final SystemConfigMapper systemConfigMapper;
    private final PasswordEncoder passwordEncoder;
    private final OperationLogService operationLogService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long register(RegisterRequest request) {
        // 1. 检查注册开关
        SystemConfig registerSwitch = systemConfigMapper.selectOne(new LambdaQueryWrapper<SystemConfig>()
                .eq(SystemConfig::getConfigKey, "user_register_enabled"));

        if (registerSwitch == null || !"1".equals(registerSwitch.getConfigValue())) {
            throw new BusinessException("暂停注册，具体开放时间等通知");
        }

        // 2. 检查用户名是否已存在
        User existingUser = userMapper.selectOne(new LambdaQueryWrapper<User>()
                .eq(User::getUsername, request.getUsername()));

        if (existingUser != null) {
            throw new BusinessException(ResultCode.ALREADY_EXISTS.getCode(), "用户名已存在");
        }

        // 3. 验证邀请码
        User inviter = userMapper.selectOne(new LambdaQueryWrapper<User>()
                .eq(User::getInviteCode, request.getInviteCode()));

        if (inviter == null) {
            throw new BusinessException("邀请码无效");
        }

        if (inviter.getInviteRate() == null) {
            throw new BusinessException("该邀请码未设置费率");
        }

        // 4. 创建用户
        User user = new User();
        user.setParentId(inviter.getId());
        user.setUsername(request.getUsername());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setNickname(StrUtil.isNotBlank(request.getNickname()) ? request.getNickname() : request.getUsername());
        user.setRate(inviter.getInviteRate());
        user.setBalance(BigDecimal.ZERO);
        user.setTotalRecharge(BigDecimal.ZERO);
        user.setStatus(SystemVariableCache.getStatusValue("user_status", "normal"));

        userMapper.insert(user);

        // 5. 记录日志
        operationLogService.log(user.getId(), "注册",
                String.format("通过邀请码[%s]注册成功", request.getInviteCode()),
                BigDecimal.ZERO, BigDecimal.ZERO);

        operationLogService.log(inviter.getId(), "邀请注册",
                String.format("用户[%s]通过你的邀请码注册", request.getUsername()),
                BigDecimal.ZERO, null);

        log.info("用户注册成功：userId={}, username={}, inviterId={}", user.getId(), user.getUsername(), inviter.getId());

        return user.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String setupInviteCode(Long userId, InviteCodeRequest request) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ResultCode.USER_NOT_FOUND);
        }

        // 验证邀请费率
        if (request.getInviteRate().compareTo(user.getRate()) < 0) {
            throw new BusinessException("邀请费率不能低于自己的费率");
        }

        if (request.getInviteRate().compareTo(new BigDecimal("0.6")) < 0) {
            throw new BusinessException("邀请费率最低为0.6");
        }

        // 检查费率是否为0.05的倍数
        BigDecimal multiplied = request.getInviteRate().multiply(new BigDecimal("100"));
        if (multiplied.remainder(new BigDecimal("5")).compareTo(BigDecimal.ZERO) != 0) {
            throw new BusinessException("邀请费率必须为0.05的倍数");
        }

        // 生成或验证邀请码
        String inviteCode;
        if (StrUtil.isBlank(user.getInviteCode())) {
            // 首次生成邀请码
            if (StrUtil.isNotBlank(request.getCustomInviteCode())) {
                inviteCode = request.getCustomInviteCode();
            } else {
                inviteCode = generateInviteCode();
            }

            // 检查邀请码是否已被使用
            User existing = userMapper.selectOne(new LambdaQueryWrapper<User>()
                    .eq(User::getInviteCode, inviteCode));

            if (existing != null) {
                throw new BusinessException(ResultCode.INVITE_CODE_USED);
            }

            user.setInviteCode(inviteCode);
        } else {
            inviteCode = user.getInviteCode();
        }

        // 更新邀请费率
        user.setInviteRate(request.getInviteRate());
        userMapper.updateById(user);

        // 记录日志
        operationLogService.log(userId, "设置邀请码",
                String.format("设置邀请码：%s，费率：%s", inviteCode, request.getInviteRate()),
                BigDecimal.ZERO, null);

        log.info("邀请码设置成功：userId={}, inviteCode={}, inviteRate={}", userId, inviteCode, request.getInviteRate());

        return inviteCode;
    }

    @Override
    public boolean validateInviteCode(String inviteCode) {
        if (StrUtil.isBlank(inviteCode)) {
            return false;
        }

        User user = userMapper.selectOne(new LambdaQueryWrapper<User>()
                .eq(User::getInviteCode, inviteCode));

        return user != null && user.getInviteRate() != null;
    }

    /**
     * 生成随机邀请码
     */
    private String generateInviteCode() {
        String code = RandomUtil.randomNumbers(6);

        // 确保唯一
        User existing = userMapper.selectOne(new LambdaQueryWrapper<User>()
                .eq(User::getInviteCode, code));

        if (existing != null) {
            return generateInviteCode();
        }

        return code;
    }
}

