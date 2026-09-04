package com.course.platform.service.impl;

import cn.hutool.core.util.RandomUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.course.platform.infra.cache.SystemVariableCache;
import com.course.platform.common.exception.BusinessException;
import com.course.platform.common.result.ResultCode;
import com.course.platform.common.util.PublicUidUtil;
import com.course.platform.domain.dto.RechargeRequest;
import com.course.platform.domain.dto.UserCreateRequest;
import com.course.platform.domain.dto.UserUpdateRequest;
import com.course.platform.domain.entity.User;
import com.course.platform.infra.persistence.mapper.UserMapper;
import com.course.platform.infra.persistence.mapper.UserAuthorityMapper;
import com.course.platform.application.service.support.OperationLogService;
import com.course.platform.application.service.user.UserService;
import com.course.platform.service.impl.AccountLedgerServiceImpl;
import com.course.platform.security.SecurityUtils;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

/**
 * 用户服务实现类
 *
 * @author AI Assistant
 * @since 2025-01-17
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserMapper userMapper;
    private final UserAuthorityMapper userAuthorityMapper;
    private final PasswordEncoder passwordEncoder;
    private final OperationLogService operationLogService;
    private final AccountLedgerServiceImpl accountLedgerService;

    @Value("${course.business.user-register-fee:5}")
    private BigDecimal userRegisterFee;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String createUser(UserCreateRequest request, Long operatorId) {
        // 1. 检查操作人权限和余额
        User operator = userMapper.selectById(operatorId);
        if (operator == null) {
            throw new BusinessException(ResultCode.USER_NOT_FOUND);
        }

        if (operator.getBalance().compareTo(userRegisterFee) < 0) {
            throw new BusinessException("余额不足，开户需扣除" + userRegisterFee + "元开户费");
        }

        // 2. 检查用户名是否已存在
        User existingUser = userMapper.selectOne(new LambdaQueryWrapper<User>()
                .eq(User::getUsername, request.getUsername()));
        if (existingUser != null) {
            throw new BusinessException(ResultCode.ALREADY_EXISTS.getCode(), "用户名已存在");
        }

        // 3. 检查费率设置是否合理
        if (request.getRate().compareTo(operator.getRate()) < 0) {
            throw new BusinessException(ResultCode.PARENT_RATE_ERROR);
        }

        // 4. 创建用户
        User user = new User();
        user.setUid(PublicUidUtil.generate());
        user.setParentId(operatorId);
        user.setUsername(request.getUsername());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setNickname(request.getNickname());
        user.setRate(request.getRate());
        user.setBalance(BigDecimal.ZERO);
        user.setTotalRecharge(BigDecimal.ZERO);
        user.setStatus(SystemVariableCache.getStatusValue("user_status", "normal"));
        user.setRole(com.course.platform.common.security.SecurityRoles.USER);
        user.setMustChangePassword(0);

        userMapper.insert(user);
        if (userAuthorityMapper.assignRole(user.getId(), "USER") != 1) {
            throw new IllegalStateException("默认 USER 角色不存在，拒绝创建无权限边界的账号");
        }

        // 5. 扣除开户费
        String openBizNo = "OPEN-" + request.getUsername() + "-" + System.currentTimeMillis();
        accountLedgerService.debit(
                operatorId,
                userRegisterFee,
                AccountLedgerServiceImpl.BIZ_ADJUST,
                openBizNo,
                String.format("开户扣费：%s", request.getUsername())
        );
        operator = userMapper.selectById(operatorId);

        // 6. 记录日志
        operationLogService.log(operatorId, "开户",
                String.format("开户成功：%s（%s），扣费：%s元", request.getUsername(), request.getNickname(), userRegisterFee),
                userRegisterFee.negate(), operator.getBalance());

        log.info("用户创建成功：userId={}, username={}, operatorId={}", user.getId(), user.getUsername(), operatorId);

        return user.getUid();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateUser(UserUpdateRequest request, Long operatorId) {
        User user = requireUserByUid(request.getUid());
        if (user == null) {
            throw new BusinessException(ResultCode.USER_NOT_FOUND);
        }

        // 检查权限：只能修改自己的下级
        if (!(SecurityUtils.isAdmin()) && !user.getParentId().equals(operatorId)) {
            throw new BusinessException(ResultCode.FORBIDDEN);
        }

        // 更新用户信息
        if (StrUtil.isNotBlank(request.getNickname())) {
            user.setNickname(request.getNickname());
        }
        if (request.getRate() != null) {
            User operator = userMapper.selectById(operatorId);
            if (request.getRate().compareTo(operator.getRate()) < 0) {
                throw new BusinessException(ResultCode.PARENT_RATE_ERROR);
            }
            user.setRate(request.getRate());
        }
        if (request.getStatus() != null) {
            user.setStatus(request.getStatus());
        }
        if (request.getInviteRate() != null) {
            user.setInviteRate(request.getInviteRate());
        }

        String nickname = StrUtil.isNotBlank(request.getNickname()) ? request.getNickname() : null;
        if (userMapper.updateProfileFields(user.getId(), nickname, request.getRate(),
                request.getStatus(), request.getInviteRate()) != 1) {
            throw new BusinessException(ResultCode.USER_NOT_FOUND);
        }

        operationLogService.log(operatorId, "修改用户",
                String.format("修改用户：%s", user.getUsername()),
                BigDecimal.ZERO, null);

        log.info("用户更新成功：userId={}, operatorId={}", user.getId(), operatorId);
    }

    @Override
    public IPage<User> queryUsers(String keyword, Integer status, Integer page, Integer pageSize, Long operatorId) {
        Page<User> pageObj = new Page<>(page, pageSize);

        LambdaQueryWrapper<User> queryWrapper = new LambdaQueryWrapper<>();

        // 非管理员只能查看自己的下级
        if (!(SecurityUtils.isAdmin())) {
            queryWrapper.eq(User::getParentId, operatorId);
        }

        if (StrUtil.isNotBlank(keyword)) {
            queryWrapper.and(wrapper -> wrapper
                    .like(User::getUsername, keyword)
                    .or()
                    .like(User::getNickname, keyword));
        }

        if (status != null) {
            queryWrapper.eq(User::getStatus, status);
        }

        queryWrapper.orderByDesc(User::getCreateTime);

        return userMapper.selectPage(pageObj, queryWrapper);
    }

    @Override
    public User getUserByUid(String userUid, Long operatorId) {
        User user = requireUserByUid(userUid);

        // 检查权限
        if (!(SecurityUtils.isAdmin()) &&
            !user.getId().equals(operatorId) &&
            !user.getParentId().equals(operatorId)) {
            throw new BusinessException(ResultCode.FORBIDDEN);
        }

        // 隐藏密码
        user.setPassword(null);

        return user;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void recharge(RechargeRequest request, Long operatorId) {
        User operator = userMapper.selectById(operatorId);
        if (operator == null) {
            throw new BusinessException(ResultCode.USER_NOT_FOUND);
        }

        User targetUser = requireUserByUid(request.getTargetUserUid());

        boolean isAdmin = SecurityUtils.isAdmin();
        if (!isAdmin && (targetUser.getParentId() == null || !targetUser.getParentId().equals(operatorId))) {
            throw new BusinessException("只能给自己的下级充值");
        }

        BigDecimal actualCost = request.getAmount()
                .multiply(operator.getRate())
                .divide(targetUser.getRate(), 2, java.math.RoundingMode.HALF_UP);

        String bizNo = "RCH-" + targetUser.getUid() + "-" + System.currentTimeMillis();

        // 超级管理员可直接给下级加款；其他角色必须先原子扣减自身余额
        if (!(SecurityUtils.isAdmin())) {
            accountLedgerService.debit(
                    operatorId,
                    actualCost,
                    AccountLedgerServiceImpl.BIZ_RECHARGE,
                    bizNo + "-OUT",
                    String.format("给用户[%s]充值扣费", targetUser.getUsername())
            );
        }

        accountLedgerService.credit(
                targetUser.getId(),
                request.getAmount(),
                AccountLedgerServiceImpl.BIZ_RECHARGE,
                bizNo + "-IN",
                String.format("上级[%s]充值", operator.getUsername()),
                true
        );

        operator = userMapper.selectById(operatorId);
        targetUser = userMapper.selectById(targetUser.getId());

        operationLogService.log(operatorId, "充值",
                String.format("给用户[%s]充值%s元，实际扣费%s元", targetUser.getUsername(), request.getAmount(), actualCost),
                SecurityUtils.isAdmin() ? BigDecimal.ZERO : actualCost.negate(),
                operator.getBalance());

        operationLogService.log(targetUser.getId(), "充值",
                String.format("上级[%s]充值%s元", operator.getUsername(), request.getAmount()),
                request.getAmount(), targetUser.getBalance());

        log.info("充值成功：operatorId={}, targetUserUid={}, amount={}, actualCost={}",
                operatorId, targetUser.getUid(), request.getAmount(), actualCost);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void changePassword(Long userId, String oldPassword, String newPassword) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ResultCode.USER_NOT_FOUND);
        }

        if (!passwordEncoder.matches(oldPassword, user.getPassword())) {
            throw new BusinessException("原密码错误");
        }

        validatePasswordStrength(newPassword);
        if (passwordEncoder.matches(newPassword, user.getPassword())) {
            throw new BusinessException("新密码不能与旧密码相同");
        }
        if ("123456".equals(newPassword) || "admin".equalsIgnoreCase(newPassword)) {
            throw new BusinessException("不能使用默认弱密码");
        }

        if (userMapper.updatePassword(userId, passwordEncoder.encode(newPassword), 0, LocalDateTime.now()) != 1) {
            throw new BusinessException(ResultCode.USER_NOT_FOUND);
        }

        operationLogService.log(userId, "修改密码", "修改密码成功", BigDecimal.ZERO, null);
        log.info("修改密码成功：userId={}", userId);
    }


    @Override
    @Transactional(rollbackFor = Exception.class)
    public String resetPassword(String targetUserUid, Long operatorId) {
        User targetUser = requireUserByUid(targetUserUid);

        boolean isAdmin = SecurityUtils.isAdmin();
        if (!isAdmin && (targetUser.getParentId() == null || !targetUser.getParentId().equals(operatorId))) {
            throw new BusinessException(ResultCode.FORBIDDEN);
        }

        String newPassword = RandomUtil.randomString(12);
        if (userMapper.updatePassword(targetUser.getId(), passwordEncoder.encode(newPassword), 1,
                LocalDateTime.now()) != 1) {
            throw new BusinessException(ResultCode.USER_NOT_FOUND);
        }

        operationLogService.log(operatorId, "重置密码",
                String.format("重置用户[%s]密码", targetUser.getUsername()),
                BigDecimal.ZERO, null);
        log.info("重置密码成功：targetUserUid={}, operatorId={}", targetUser.getUid(), operatorId);
        return newPassword;
    }


    @Override
    @Transactional(rollbackFor = Exception.class)
    public void changeUserStatus(String userUid, Integer status, Long operatorId) {
        // 只有管理员可以禁用用户
        if (!(SecurityUtils.isAdmin())) {
            throw new BusinessException(ResultCode.FORBIDDEN);
        }

        User user = requireUserByUid(userUid);

        if (userMapper.updateStatus(user.getId(), status) != 1) {
            throw new BusinessException(ResultCode.USER_NOT_FOUND);
        }

        String action = status == SystemVariableCache.getStatusValue("user_status", "normal") ? "启用" : "禁用";
        operationLogService.log(operatorId, action + "用户",
                String.format("%s用户：%s", action, user.getUsername()),
                BigDecimal.ZERO, null);

        log.info("{}用户成功：userUid={}, operatorId={}", action, user.getUid(), operatorId);
    }

    private User requireUserByUid(String uid) {
        if (!PublicUidUtil.isValid(uid)) {
            throw new BusinessException(ResultCode.USER_NOT_FOUND);
        }
        User user = userMapper.selectOne(new LambdaQueryWrapper<User>()
                .eq(User::getUid, PublicUidUtil.normalize(uid))
                .last("LIMIT 1"));
        if (user == null) {
            throw new BusinessException(ResultCode.USER_NOT_FOUND);
        }
        return user;
    }

    private void validatePasswordStrength(String password) {
        if (password == null || password.length() < 8) {
            throw new BusinessException("密码长度至少8位");
        }
        if (password.length() > 64) {
            throw new BusinessException("密码长度不能超过64位");
        }
        boolean hasLetter = password.matches(".*[A-Za-z].*");
        boolean hasDigit = password.matches(".*\\d.*");
        if (!hasLetter || !hasDigit) {
            throw new BusinessException("密码需同时包含字母和数字");
        }
    }
}
