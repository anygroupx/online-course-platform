package com.course.platform.service;

import com.course.platform.domain.entity.SystemConfig;

import java.util.List;
import java.util.Map;

/**
 * 系统配置服务接口
 * 
 * @author AI Assistant
 * @since 2025-01-17
 */
public interface SystemConfigService {

    /**
     * 获取所有配置
     * 
     * @return 配置列表
     */
    List<SystemConfig> getAllConfigs();

    /**
     * 获取配置值
     * 
     * @param configKey 配置键
     * @return 配置值
     */
    String getConfigValue(String configKey);

    /**
     * 更新配置
     * 
     * @param configs 配置Map
     */
    void updateConfigs(Map<String, String> configs);

    /**
     * 重置配置
     * 
     * @param configKey 配置键
     */
    void resetConfig(String configKey);

    /**
     * 获取整数配置值
     * 
     * @param configKey 配置键
     * @param defaultValue 默认值
     * @return 整数值
     */
    Integer getConfigValueAsInteger(String configKey, Integer defaultValue);

    /**
     * 获取布尔配置值
     * 
     * @param configKey 配置键
     * @param defaultValue 默认值
     * @return 布尔值
     */
    Boolean getConfigValueAsBoolean(String configKey, Boolean defaultValue);
}

