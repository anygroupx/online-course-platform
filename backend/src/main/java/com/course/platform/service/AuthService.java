package com.course.platform.service;

import com.course.platform.domain.dto.LoginRequest;
import com.course.platform.domain.vo.LoginResponse;

/**
 * 认证服务接口
 * 
 * @author AI Assistant
 * @since 2025-01-17
 */
public interface AuthService {

    /**
     * 用户登录
     * 
     * @param request 登录请求
     * @return 登录响应（包含Token）
     */
    LoginResponse login(LoginRequest request);

    /**
     * 用户登出
     * 
     * @param userId 用户ID
     */
    void logout(Long userId);

    /**
     * 刷新Token
     * 
     * @param refreshToken Refresh Token
     * @return 登录响应（包含新的Token和Refresh Token）
     */
    LoginResponse refresh(String refreshToken);
}

