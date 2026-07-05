package com.course.platform.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.course.platform.infra.cache.SystemVariableCache;
import com.course.platform.shared.constant.Constants;
import com.course.platform.shared.exception.BusinessException;
import com.course.platform.shared.result.ResultCode;
import com.course.platform.shared.util.JwtUtil;
import com.course.platform.domain.dto.LoginRequest;
import com.course.platform.domain.entity.User;
import com.course.platform.domain.vo.LoginResponse;
import com.course.platform.mapper.UserMapper;
import com.course.platform.service.AuthService;
import com.course.platform.service.OperationLogService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * 认证服务实现类
 * 
 * @author AI Assistant
 * @since 2025-01-17
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final com.course.platform.mapper.RefreshTokenMapper refreshTokenMapper;
    private final com.course.platform.service.SystemConfigService systemConfigService;
    private final OperationLogService operationLogService;

    @Override
    public LoginResponse login(LoginRequest request) {
        // 查询用户
        User user = userMapper.selectOne(new LambdaQueryWrapper<User>()
                .eq(User::getUsername, request.getUsername()));

        // 验证用户是否存在
        if (user == null) {
            throw new BusinessException(ResultCode.USERNAME_OR_PASSWORD_ERROR);
        }

        // 验证密码
        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new BusinessException(ResultCode.USERNAME_OR_PASSWORD_ERROR);
        }

        // 验证账号状态
        if (SystemVariableCache.getStatusValue("user_status", "disabled") == user.getStatus()) {
            throw new BusinessException(ResultCode.ACCOUNT_DISABLED);
        }

        // 更新最后登录时间和IP
        user.setLastLoginTime(LocalDateTime.now());
        try {
            user.setLastLoginIp(com.course.platform.shared.util.ServletUtil.getClientIp());
        } catch (Exception e) {
            user.setLastLoginIp("127.0.0.1");
        }
        userMapper.updateById(user);

        // 生成Token
        String token = jwtUtil.generateToken(user.getId(), user.getUsername());

        // 生成Refresh Token
        String refreshToken = jwtUtil.generateRefreshToken(user.getId(), user.getUsername());

        // 从数据库读取 Refresh Token 过期时间（天）
        Integer expireDays = systemConfigService.getConfigValueAsInteger("refresh_token_expire_days", 7);
        LocalDateTime expireTime = LocalDateTime.now().plusDays(expireDays);
        
        // 保存Refresh Token到数据库
        com.course.platform.domain.entity.RefreshToken refreshTokenEntity = com.course.platform.domain.entity.RefreshToken.builder()
                .userId(user.getId())
                .token(refreshToken)
                .expireTime(expireTime)
                .build();
        refreshTokenMapper.insert(refreshTokenEntity);

        // 构建响应
        LoginResponse response = LoginResponse.builder()
                .token(token)
                .refreshToken(refreshToken)
                .userId(user.getId())
                .username(user.getUsername())
                .nickname(StrUtil.isNotBlank(user.getNickname()) ? user.getNickname() : user.getUsername())
                .balance(user.getBalance())
                .rate(user.getRate())
                .isAdmin(Constants.DEFAULT_ADMIN_ID.equals(user.getId()))
                .build();
        
        // 记录登录日志
        operationLogService.log(user.getId(), "登录", 
                "用户登录成功，IP: " + user.getLastLoginIp(), 
                null, user.getBalance());
        
        return response;
    }

    @Override
    public void logout(Long userId) {
        // TODO: 如果使用Redis，可以将Token加入黑名单
        log.info("用户登出: userId={}", userId);
        
        // 记录登出日志
        operationLogService.log(userId, "登出", "用户主动登出", null, null);
    }

    @Override
    public LoginResponse refresh(String refreshToken) {
        // 验证Refresh Token格式和签名
        if (!jwtUtil.validateRefreshToken(refreshToken)) {
            throw new BusinessException(ResultCode.TOKEN_EXPIRED);
        }

        // 查询Refresh Token是否存在且未过期
        com.course.platform.domain.entity.RefreshToken refreshTokenEntity = refreshTokenMapper.selectOne(
                new LambdaQueryWrapper<com.course.platform.domain.entity.RefreshToken>()
                        .eq(com.course.platform.domain.entity.RefreshToken::getToken, refreshToken)
                        .ge(com.course.platform.domain.entity.RefreshToken::getExpireTime, LocalDateTime.now())
        );

        if (refreshTokenEntity == null) {
            throw new BusinessException(ResultCode.TOKEN_EXPIRED);
        }

        // 获取用户信息
        User user = userMapper.selectById(refreshTokenEntity.getUserId());
        if (user == null) {
            throw new BusinessException(ResultCode.USER_NOT_FOUND);
        }

        // 验证账号状态
        if (SystemVariableCache.getStatusValue("user_status", "disabled") == user.getStatus()) {
            throw new BusinessException(ResultCode.ACCOUNT_DISABLED);
        }

        // 生成新的Access Token
        String newToken = jwtUtil.generateToken(user.getId(), user.getUsername());

        // 生成新的Refresh Token（滚动刷新）
        String newRefreshToken = jwtUtil.generateRefreshToken(user.getId(), user.getUsername());

        // 从数据库读取 Refresh Token 过期时间（天）
        Integer expireDays = systemConfigService.getConfigValueAsInteger("refresh_token_expire_days", 7);
        LocalDateTime newExpireTime = LocalDateTime.now().plusDays(expireDays);
        
        // 更新Refresh Token
        refreshTokenEntity.setToken(newRefreshToken);
        refreshTokenEntity.setExpireTime(newExpireTime);
        refreshTokenMapper.updateById(refreshTokenEntity);

        // 构建响应
        return LoginResponse.builder()
                .token(newToken)
                .refreshToken(newRefreshToken)
                .userId(user.getId())
                .username(user.getUsername())
                .nickname(StrUtil.isNotBlank(user.getNickname()) ? user.getNickname() : user.getUsername())
                .balance(user.getBalance())
                .rate(user.getRate())
                .isAdmin(Constants.DEFAULT_ADMIN_ID.equals(user.getId()))
                .build();
    }
}

