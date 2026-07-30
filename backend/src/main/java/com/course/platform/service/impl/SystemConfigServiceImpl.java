package com.course.platform.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.course.platform.domain.entity.SystemConfig;
import com.course.platform.mapper.SystemConfigMapper;
import com.course.platform.service.SystemConfigService;
import com.course.platform.shared.exception.BusinessException;
import com.course.platform.shared.result.ResultCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 系统配置服务实现类（legacy 单体路径）
 *
 * @author AI Assistant
 * @since 2025-01-17
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SystemConfigServiceImpl implements SystemConfigService {

    private final SystemConfigMapper systemConfigMapper;

    /**
     * 默认配置项：key -> [defaultValue, description]
     */
    private static final Map<String, DefaultConfig> DEFAULTS;

    static {
        Map<String, DefaultConfig> defaults = new LinkedHashMap<>();
        defaults.put("site_name", new DefaultConfig("在线网课平台", "网站名称"));
        defaults.put("site_keywords", new DefaultConfig("网课,在线教育,代刷", "网站关键词"));
        defaults.put("site_description", new DefaultConfig("专业的在线网课服务平台", "网站描述"));
        defaults.put("system_notice", new DefaultConfig("欢迎使用本平台！", "系统公告"));
        defaults.put("user_register_enabled", new DefaultConfig("1", "用户注册开关：0-关闭 1-开启"));
        defaults.put("user_register_fee", new DefaultConfig("5", "用户开户费用"));
        defaults.put("min_recharge_amount", new DefaultConfig("10", "最低充值金额"));
        defaults.put("api_enable_threshold", new DefaultConfig("300", "API开通免费门槛（余额）"));
        defaults.put("token_expire_minutes", new DefaultConfig("15", "Access Token过期时间（分钟）"));
        defaults.put("refresh_token_expire_days", new DefaultConfig("7", "Refresh Token过期时间（天）"));
        defaults.put("auto_refresh_token_enabled", new DefaultConfig("1", "启用自动刷新Token：0-关闭 1-开启"));
        DEFAULTS = Collections.unmodifiableMap(defaults);
    }

    @Override
    public List<SystemConfig> getAllConfigs() {
        return systemConfigMapper.selectList(new LambdaQueryWrapper<>());
    }

    @Override
    public String getConfigValue(String configKey) {
        SystemConfig config = findByKey(configKey);
        return config != null ? config.getConfigValue() : null;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateConfigs(Map<String, String> configs) {
        if (configs == null || configs.isEmpty()) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "配置内容不能为空");
        }

        for (Map.Entry<String, String> entry : configs.entrySet()) {
            String configKey = entry.getKey();
            if (!StringUtils.hasText(configKey)) {
                continue;
            }

            String configValue = entry.getValue() == null ? "" : String.valueOf(entry.getValue());
            SystemConfig config = findByKey(configKey);

            if (config != null) {
                config.setConfigValue(configValue);
                systemConfigMapper.updateById(config);
            } else {
                SystemConfig newConfig = new SystemConfig();
                newConfig.setConfigKey(configKey);
                newConfig.setConfigValue(configValue);
                Optional.ofNullable(DEFAULTS.get(configKey)).ifPresent(def ->
                        newConfig.setConfigDesc(def.description()));
                systemConfigMapper.insert(newConfig);
            }
        }

        log.info("系统配置更新成功，数量={}", configs.size());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void resetConfig(String configKey) {
        if (!StringUtils.hasText(configKey)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "配置键不能为空");
        }

        DefaultConfig defaultConfig = DEFAULTS.get(configKey);
        if (defaultConfig == null) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "不支持重置的配置项: " + configKey);
        }

        SystemConfig config = findByKey(configKey);
        if (config == null) {
            SystemConfig newConfig = new SystemConfig();
            newConfig.setConfigKey(configKey);
            newConfig.setConfigValue(defaultConfig.value());
            newConfig.setConfigDesc(defaultConfig.description());
            systemConfigMapper.insert(newConfig);
            log.info("配置项不存在，已按默认值创建：{} = {}", configKey, defaultConfig.value());
            return;
        }

        config.setConfigValue(defaultConfig.value());
        if (!StringUtils.hasText(config.getConfigDesc())) {
            config.setConfigDesc(defaultConfig.description());
        }
        systemConfigMapper.updateById(config);
        log.info("配置重置成功：{} = {}", configKey, defaultConfig.value());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int resetAllConfigs() {
        int count = 0;
        for (String key : DEFAULTS.keySet()) {
            resetConfig(key);
            count++;
        }
        log.info("全部系统配置重置完成，数量={}", count);
        return count;
    }

    @Override
    public Integer getConfigValueAsInteger(String configKey, Integer defaultValue) {
        String value = getConfigValue(configKey);
        if (value == null) {
            DefaultConfig builtin = DEFAULTS.get(configKey);
            if (builtin != null) {
                try {
                    return Integer.parseInt(builtin.value());
                } catch (NumberFormatException ignored) {
                    return defaultValue;
                }
            }
            return defaultValue;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            log.warn("配置值转换失败：{} = {}, 使用默认值：{}", configKey, value, defaultValue);
            return defaultValue;
        }
    }

    @Override
    public Boolean getConfigValueAsBoolean(String configKey, Boolean defaultValue) {
        String value = getConfigValue(configKey);
        if (value == null) {
            DefaultConfig builtin = DEFAULTS.get(configKey);
            if (builtin != null) {
                return "1".equals(builtin.value()) || "true".equalsIgnoreCase(builtin.value());
            }
            return defaultValue;
        }
        return "1".equals(value) || "true".equalsIgnoreCase(value);
    }

    private SystemConfig findByKey(String configKey) {
        if (!StringUtils.hasText(configKey)) {
            return null;
        }
        return systemConfigMapper.selectOne(new LambdaQueryWrapper<SystemConfig>()
                .eq(SystemConfig::getConfigKey, configKey)
                .last("LIMIT 1"));
    }

    private record DefaultConfig(String value, String description) {
    }
}
