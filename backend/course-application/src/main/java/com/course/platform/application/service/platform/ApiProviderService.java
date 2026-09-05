package com.course.platform.application.service.platform;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.course.platform.domain.entity.ApiProvider;
import com.course.platform.domain.vo.ProviderConnectionTestResult;

/**
 * 第三方API接口服务接口
 * 
 * @author AI Assistant
 * @since 2025-01-17
 */
public interface ApiProviderService {

    /**
     * 创建API接口
     * 
     * @param apiProvider API接口信息
     * @return 接口ID
     */
    Long createApiProvider(ApiProvider apiProvider);

    /**
     * 更新API接口
     * 
     * @param apiProvider API接口信息
     */
    void updateApiProvider(ApiProvider apiProvider);

    /** Test saved configuration without changing its activation state or placing orders. */
    ProviderConnectionTestResult testConnection(Long id, Long operatorId);

    /** Enabling requires successful verification of the current configuration; disabling is always local. */
    void updateStatus(Long id, Integer status);

    /** Read-only health check for an enabled provider; does not grant or revoke activation. */
    void checkHealth(Long id);

    /**
     * 删除API接口
     * 
     * @param id 接口ID
     */
    void deleteApiProvider(Long id);

    /**
     * 分页查询API接口
     * 
     * @param keyword 搜索关键词
     * @param status 状态
     * @param page 当前页
     * @param pageSize 每页数量
     * @return 接口分页数据
     */
    IPage<ApiProvider> queryApiProviders(String keyword, Integer status, Integer page, Integer pageSize);

    /**
     * 按 ID 加载并解密运行时凭据。返回值只允许用于第三方对接，不得回写数据库。
     *
     * @param id 接口ID
     * @return 已解密的接口配置，不存在时返回 null
     */
    ApiProvider loadDecrypted(Long id);
}
