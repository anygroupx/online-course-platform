package com.course.platform.service;

import com.course.platform.domain.entity.PaymentConfig;

import java.util.List;

/**
 * 支付配置服务接口
 * 
 * @author AI Assistant
 * @date 2025-11-26
 */
public interface PaymentConfigService {

    /**
     * 获取当前激活的配置
     * 
     * @return 支付配置
     */
    PaymentConfig getActiveConfig();

    /**
     * 获取所有配置列表
     * 
     * @return 配置列表
     */
    List<PaymentConfig> getAllConfigs();

    /**
     * 根据ID获取配置
     * 
     * @param id 配置ID
     * @return 支付配置
     */
    PaymentConfig getById(Long id);

    /**
     * 创建配置
     * 
     * @param config 配置信息
     * @return 是否成功
     */
    boolean create(PaymentConfig config);

    /**
     * 更新配置
     * 
     * @param config 配置信息
     * @return 是否成功
     */
    boolean update(PaymentConfig config);

    /**
     * 激活指定配置
     * 
     * @param id 配置ID
     * @return 是否成功
     */
    boolean activate(Long id);

    /**
     * 删除配置
     * 
     * @param id 配置ID
     * @return 是否成功
     */
    boolean delete(Long id);
}
