package com.course.platform.application.service.auth;

/**
 * API密钥服务接口
 * 
 * @author AI Assistant
 * @since 2025-01-17
 */
public interface ApiKeyService {

    /**
     * 开通API密钥。
     * @param userId 当前用户内部ID
     * @param type 1-自己开通，2-给下级开通
     * @param targetUserUid 目标用户UUID（给下级开通时）
     * @return 仅签发时返回一次的完整密钥
     */
    String enableApiKey(Long userId, Integer type, String targetUserUid);

    /** 免费轮换自己的已开通密钥；校验当前密码，立即失效旧密钥，保留作用域。 */
    String rotateApiKey(Long userId, String currentPassword);
}
