package com.course.platform.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.course.platform.domain.entity.CountdownConfig;
import com.course.platform.mapper.CountdownConfigMapper;
import com.course.platform.service.CountdownConfigService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 倒计时配置服务实现类
 * 
 * @author AI Assistant
 * @since 2025-01-17
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CountdownConfigServiceImpl implements CountdownConfigService {

    private final CountdownConfigMapper countdownConfigMapper;

    @Override
    public Map<String, String> getAllConfigs() {
        List<CountdownConfig> configs = countdownConfigMapper.selectList(
            new LambdaQueryWrapper<CountdownConfig>()
                .eq(CountdownConfig::getIsEnabled, 1)
        );
        
        Map<String, String> configMap = new HashMap<>();
        for (CountdownConfig config : configs) {
            configMap.put(config.getConfigKey(), config.getConfigValue());
        }
        
        return configMap;
    }

    @Override
    public String getConfigValue(String configKey) {
        return getConfigValue(configKey, null);
    }

    @Override
    public String getConfigValue(String configKey, String defaultValue) {
        CountdownConfig config = countdownConfigMapper.selectOne(
            new LambdaQueryWrapper<CountdownConfig>()
                .eq(CountdownConfig::getConfigKey, configKey)
                .eq(CountdownConfig::getIsEnabled, 1)
        );
        
        return config != null ? config.getConfigValue() : defaultValue;
    }

    @Override
    public Integer getIntConfigValue(String configKey, Integer defaultValue) {
        String value = getConfigValue(configKey);
        if (value == null || value.trim().isEmpty()) {
            return defaultValue;
        }
        
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            log.warn("配置值转换失败: {} = {}, 使用默认值: {}", configKey, value, defaultValue);
            return defaultValue;
        }
    }

    @Override
    public Boolean getBooleanConfigValue(String configKey, Boolean defaultValue) {
        String value = getConfigValue(configKey);
        if (value == null || value.trim().isEmpty()) {
            return defaultValue;
        }
        
        return "1".equals(value.trim()) || "true".equalsIgnoreCase(value.trim());
    }

    @Override
    @Transactional
    public void setConfigValue(String configKey, String configValue) {
        // 验证配置键和值的有效性
        if (configKey == null || configKey.trim().isEmpty()) {
            log.warn("配置键不能为空，跳过处理");
            return;
        }
        
        // 过滤掉无效的配置键（如id等非配置字段）
        if ("id".equals(configKey.trim())) {
            log.warn("跳过无效的配置键: {}", configKey);
            return;
        }
        
        // 确保配置值不为空，如果为空则使用空字符串
        if (configValue == null) {
            configValue = "";
            log.warn("配置值为空，使用空字符串作为默认值: {}", configKey);
        }
        
        CountdownConfig existingConfig = countdownConfigMapper.selectOne(
            new LambdaQueryWrapper<CountdownConfig>()
                .eq(CountdownConfig::getConfigKey, configKey)
        );
        
        if (existingConfig != null) {
            existingConfig.setConfigValue(configValue);
            countdownConfigMapper.updateById(existingConfig);
        } else {
            CountdownConfig newConfig = new CountdownConfig();
            newConfig.setConfigKey(configKey);
            newConfig.setConfigValue(configValue);
            newConfig.setConfigDesc("系统配置");
            newConfig.setIsEnabled(1);
            countdownConfigMapper.insert(newConfig);
        }
        
        log.info("更新配置: {} = {}", configKey, configValue);
    }

    @Override
    @Transactional
    public void setConfigValues(Map<String, String> configs) {
        for (Map.Entry<String, String> entry : configs.entrySet()) {
            setConfigValue(entry.getKey(), entry.getValue());
        }
    }

    @Override
    public List<CountdownConfig> getAllConfigList() {
        return countdownConfigMapper.selectList(
            new LambdaQueryWrapper<CountdownConfig>()
                .orderByAsc(CountdownConfig::getConfigKey)
        );
    }

    @Override
    @Transactional
    public void updateConfig(CountdownConfig config) {
        countdownConfigMapper.updateById(config);
        log.info("更新配置: {}", config.getConfigKey());
    }

    @Override
    @Transactional
    public void resetToDefault() {
        // 删除所有现有配置
        countdownConfigMapper.delete(null);
        
        // 插入默认配置
        Map<String, String> defaultConfigs = new HashMap<>();
        defaultConfigs.put("default_countdown_duration", "60");
        defaultConfigs.put("auto_complete_status", "2");
        defaultConfigs.put("auto_complete_enabled", "1");
        defaultConfigs.put("countdown_warning_time", "10");
        
        setConfigValues(defaultConfigs);
        log.info("重置为默认配置");
    }

    @Override
    public Map<String, String> getExamCountdownConfigs() {
        Map<String, String> allConfigs = getAllConfigs();
        Map<String, String> examConfigs = new HashMap<>();
        
        // 筛选考试相关的配置
        String[] examConfigKeys = {
            "default_exam_countdown_duration",
            "exam_auto_complete_status", 
            "exam_auto_complete_enabled",
            "exam_countdown_warning_time"
        };
        
        for (String key : examConfigKeys) {
            examConfigs.put(key, allConfigs.getOrDefault(key, ""));
        }
        
        return examConfigs;
    }

    @Override
    @Transactional
    public void updateExamCountdownConfigs(Map<String, String> configs) {
        for (Map.Entry<String, String> entry : configs.entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue();
            
            // 验证配置键是否为考试相关配置
            if (key.startsWith("exam_") || key.equals("default_exam_countdown_duration")) {
                setConfigValue(key, value);
            }
        }
        log.info("更新考试倒计时配置：{}", configs);
    }

    @Override
    public Integer getDefaultExamCountdownDuration() {
        return getIntConfigValue("default_exam_countdown_duration", 120);
    }

    @Override
    public Integer getExamAutoCompleteStatus() {
        return getIntConfigValue("exam_auto_complete_status", 7);
    }

    @Override
    public Boolean getExamAutoCompleteEnabled() {
        return getBooleanConfigValue("exam_auto_complete_enabled", true);
    }

    @Override
    public Integer getExamCountdownWarningTime() {
        return getIntConfigValue("exam_countdown_warning_time", 15);
    }
}
