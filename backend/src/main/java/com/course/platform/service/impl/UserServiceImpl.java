package com.course.platform.service.impl;

import cn.hutool.core.util.RandomUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.course.platform.infra.cache.SystemVariableCache;
import com.course.platform.shared.constant.Constants;
import com.course.platform.shared.exception.BusinessException;
import com.course.platform.shared.result.ResultCode;
import com.course.platform.domain.dto.RechargeRequest;
import com.course.platform.domain.dto.UserCreateRequest;
import com.course.platform.domain.dto.UserUpdateRequest;
import com.course.platform.domain.entity.User;
import com.course.platform.mapper.UserMapper;
import com.course.platform.service.OperationLogService;
import com.course.platform.service.UserService;
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
    private final PasswordEncoder passwordEncoder;
    private final OperationLogService operationLogService;

    @Value("${course.business.user-register-fee:5}")
    private BigDecimal userRegisterFee;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createUser(UserCreateRequest request, Long operatorId) {
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
        user.setParentId(operatorId);
        user.setUsername(request.getUsername());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setNickname(request.getNickname());
        user.setRate(request.getRate());
        user.setBalance(BigDecimal.ZERO);
        user.setTotalRecharge(BigDecimal.ZERO);
        user.setStatus(SystemVariableCache.getStatusValue("user_status", "normal"));

        userMapper.insert(user);

        // 5. 扣除开户费
        operator.setBalance(operator.getBalance().subtract(userRegisterFee));
        userMapper.updateById(operator);

        // 6. 记录日志
        operationLogService.log(operatorId, "开户",
                String.format("开户成功：%s（%s），扣费：%s元", request.getUsername(), request.getNickname(), userRegisterFee),
                userRegisterFee.negate(), operator.getBalance());

        log.info("用户创建成功：userId={}, username={}, operatorId={}", user.getId(), user.getUsername(), operatorId);

        return user.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateUser(UserUpdateRequest request, Long operatorId) {
        User user = userMapper.selectById(request.getId());
        if (user == null) {
            throw new BusinessException(ResultCode.USER_NOT_FOUND);
        }

        // 检查权限：只能修改自己的下级
        if (!Constants.DEFAULT_ADMIN_ID.equals(operatorId) && !user.getParentId().equals(operatorId)) {
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

        userMapper.updateById(user);

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
        if (!Constants.DEFAULT_ADMIN_ID.equals(operatorId)) {
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
    public User getUserById(Long userId, Long operatorId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ResultCode.USER_NOT_FOUND);
        }

        // 检查权限
        if (!Constants.DEFAULT_ADMIN_ID.equals(operatorId) && 
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
        // 1. 查询操作人信息
        User operator = userMapper.selectById(operatorId);
        if (operator == null) {
            throw new BusinessException(ResultCode.USER_NOT_FOUND);
        }

        // 2. 查询目标用户信息
        User targetUser = userMapper.selectById(request.getTargetUserId());
        if (targetUser == null) {
            throw new BusinessException(ResultCode.USER_NOT_FOUND);
        }

        // 3. 检查权限：只能给下级充值
        if (!Constants.DEFAULT_ADMIN_ID.equals(operatorId) && !targetUser.getParentId().equals(operatorId)) {
            throw new BusinessException("只能给自己的下级充值");
        }

        // 4. 计算实际扣费（根据费率差异）
        BigDecimal actualCost = request.getAmount()
                .multiply(operator.getRate())
                .divide(targetUser.getRate(), 2, BigDecimal.ROUND_HALF_UP);

        // 5. 检查余额
        if (operator.getBalance().compareTo(actualCost) < 0) {
            throw new BusinessException(ResultCode.BALANCE_INSUFFICIENT);
        }

        // 6. 扣除操作人余额
        operator.setBalance(operator.getBalance().subtract(actualCost));
        userMapper.updateById(operator);

        // 7. 增加目标用户余额
        targetUser.setBalance(targetUser.getBalance().add(request.getAmount()));
        targetUser.setTotalRecharge(targetUser.getTotalRecharge().add(request.getAmount()));
        userMapper.updateById(targetUser);

        // 8. 记录日志
        operationLogService.log(operatorId, "充值",
                String.format("给用户[%s]充值%s元，实际扣费%s元", targetUser.getUsername(), request.getAmount(), actualCost),
                actualCost.negate(), operator.getBalance());

        operationLogService.log(request.getTargetUserId(), "充值",
                String.format("上级[%s]充值%s元", operator.getUsername(), request.getAmount()),
                request.getAmount(), targetUser.getBalance());

        log.info("充值成功：operatorId={}, targetUserId={}, amount={}, actualCost={}", 
                operatorId, request.getTargetUserId(), request.getAmount(), actualCost);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void changePassword(Long userId, String oldPassword, String newPassword) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ResultCode.USER_NOT_FOUND);
        }

        // 验证旧密码
        if (!passwordEncoder.matches(oldPassword, user.getPassword())) {
            throw new BusinessException("原密码错误");
        }

        // 更新密码
        user.setPassword(passwordEncoder.encode(newPassword));
        userMapper.updateById(user);

        operationLogService.log(userId, "修改密码", "修改密码成功", BigDecimal.ZERO, null);

        log.info("修改密码成功：userId={}", userId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String resetPassword(Long targetUserId, Long operatorId) {
        User targetUser = userMapper.selectById(targetUserId);
        if (targetUser == null) {
            throw new BusinessException(ResultCode.USER_NOT_FOUND);
        }

        // 检查权限
        if (!Constants.DEFAULT_ADMIN_ID.equals(operatorId) && !targetUser.getParentId().equals(operatorId)) {
            throw new BusinessException(ResultCode.FORBIDDEN);
        }

        // 生成新密码
        String newPassword = RandomUtil.randomString(8);

        // 更新密码
        targetUser.setPassword(passwordEncoder.encode(newPassword));
        userMapper.updateById(targetUser);

        operationLogService.log(operatorId, "重置密码",
                String.format("重置用户[%s]密码", targetUser.getUsername()),
                BigDecimal.ZERO, null);

        log.info("重置密码成功：targetUserId={}, operatorId={}", targetUserId, operatorId);

        return newPassword;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void changeUserStatus(Long userId, Integer status, Long operatorId) {
        // 只有管理员可以禁用用户
        if (!Constants.DEFAULT_ADMIN_ID.equals(operatorId)) {
            throw new BusinessException(ResultCode.FORBIDDEN);
        }

        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ResultCode.USER_NOT_FOUND);
        }

        user.setStatus(status);
        userMapper.updateById(user);

        String action = status == SystemVariableCache.getStatusValue("user_status", "normal") ? "启用" : "禁用";
        operationLogService.log(operatorId, action + "用户",
                String.format("%s用户：%s", action, user.getUsername()),
                BigDecimal.ZERO, null);

        log.info("{}用户成功：userId={}, operatorId={}", action, userId, operatorId);
    }
}

