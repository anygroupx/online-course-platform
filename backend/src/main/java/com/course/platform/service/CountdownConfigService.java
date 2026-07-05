package com.course.platform.service;

import com.course.platform.domain.entity.CountdownConfig;

import java.util.List;
import java.util.Map;

/**
 * 倒计时配置服务接口
 * 
 * @author AI Assistant
 * @since 2025-01-17
 */
public interface CountdownConfigService {

    /**
     * 获取所有配置
     */
    Map<String, String> getAllConfigs();

    /**
     * 获取指定配置值
     */
    String getConfigValue(String configKey);

    /**
     * 获取指定配置值（带默认值）
     */
    String getConfigValue(String configKey, String defaultValue);

    /**
     * 获取整数配置值
     */
    Integer getIntConfigValue(String configKey, Integer defaultValue);

    /**
     * 获取布尔配置值
     */
    Boolean getBooleanConfigValue(String configKey, Boolean defaultValue);

    /**
     * 设置配置值
     */
    void setConfigValue(String configKey, String configValue);

    /**
     * 批量设置配置值
     */
    void setConfigValues(Map<String, String> configs);

    /**
     * 获取所有配置列表
     */
    List<CountdownConfig> getAllConfigList();

    /**
     * 更新配置
     */
    void updateConfig(CountdownConfig config);

    /**
     * 重置为默认配置
     */
    void resetToDefault();

    /**
     * 获取考试倒计时配置
     */
    Map<String, String> getExamCountdownConfigs();

    /**
     * 更新考试倒计时配置
     */
    void updateExamCountdownConfigs(Map<String, String> configs);

    /**
     * 获取考试倒计时默认时长
     */
    Integer getDefaultExamCountdownDuration();

    /**
     * 获取考试自动完成状态
     */
    Integer getExamAutoCompleteStatus();

    /**
     * 获取考试自动完成是否启用
     */
    Boolean getExamAutoCompleteEnabled();

    /**
     * 获取考试倒计时警告时间
     */
    Integer getExamCountdownWarningTime();
}
