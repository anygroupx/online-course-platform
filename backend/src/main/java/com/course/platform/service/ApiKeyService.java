package com.course.platform.service;

/**
 * API密钥服务接口
 * 
 * @author AI Assistant
 * @since 2025-01-17
 */
public interface ApiKeyService {

    /**
     * 开通API密钥
     * 
     * @param userId 用户ID
     * @param type 开通类型：1-自己开通 2-给下级开通
     * @param targetUserId 目标用户ID（给下级开通时）
     * @return API密钥
     */
    String enableApiKey(Long userId, Integer type, Long targetUserId);
}

