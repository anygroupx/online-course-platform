package com.course.platform.application.service.auth;

import com.course.platform.domain.dto.InviteCodeRequest;
import com.course.platform.domain.dto.RegisterRequest;

/**
 * 用户注册服务接口
 * 
 * @author AI Assistant
 * @since 2025-01-17
 */
public interface RegisterService {

    /**
     * 用户注册（通过邀请码）
     * 
     * @param request 注册请求
     * @return 对外用户 UUID
     */
    String register(RegisterRequest request);

    /**
     * 生成/设置邀请码
     * 
     * @param userId 用户ID
     * @param request 邀请码设置请求
     * @return 邀请码
     */
    String setupInviteCode(Long userId, InviteCodeRequest request);

    /**
     * 验证邀请码
     * 
     * @param inviteCode 邀请码
     * @return 是否有效
     */
    boolean validateInviteCode(String inviteCode);
}

