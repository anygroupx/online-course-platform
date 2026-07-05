package com.course.platform.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.course.platform.domain.entity.ApiProvider;

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
}

