package com.course.platform.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.course.platform.application.service.system.SystemConfigService;
import com.course.platform.common.constant.SystemConfigDefaults;
import com.course.platform.common.exception.BusinessException;
import com.course.platform.common.result.ResultCode;
import com.course.platform.domain.entity.SystemConfig;
import com.course.platform.infra.persistence.mapper.SystemConfigMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;

/**
 * 系统配置服务实现类
 *
 * @author AI Assistant
 * @since 2025-01-17
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SystemConfigServiceImpl implements SystemConfigService {

    private final SystemConfigMapper systemConfigMapper;

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
                // 允许写入已知默认配置或新增配置项，避免前端新增字段静默失败
                SystemConfig newConfig = new SystemConfig();
                newConfig.setConfigKey(configKey);
                newConfig.setConfigValue(configValue);
                SystemConfigDefaults.get(configKey).ifPresent(def ->
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

        SystemConfigDefaults.DefaultConfig defaultConfig = SystemConfigDefaults.get(configKey)
                .orElseThrow(() -> new BusinessException(
                        ResultCode.PARAM_ERROR,
                        "不支持重置的配置项: " + configKey
                ));

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

    /**
     * 将全部已知默认配置重置为默认值。
     * 对库中不存在的默认项执行插入，对已存在项执行覆盖。
     *
     * @return 重置条数
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int resetAllConfigs() {
        int count = 0;
        for (Map.Entry<String, SystemConfigDefaults.DefaultConfig> entry
                : SystemConfigDefaults.all().entrySet()) {
            resetConfig(entry.getKey());
            count++;
        }
        log.info("全部系统配置重置完成，数量={}", count);
        return count;
    }

    @Override
    public Integer getConfigValueAsInteger(String configKey, Integer defaultValue) {
        String value = getConfigValue(configKey);
        if (value == null) {
            // 优先回退内置默认值
            String builtin = SystemConfigDefaults.getDefaultValue(configKey);
            if (builtin != null) {
                try {
                    return Integer.parseInt(builtin);
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
            String builtin = SystemConfigDefaults.getDefaultValue(configKey);
            if (builtin != null) {
                return "1".equals(builtin) || "true".equalsIgnoreCase(builtin);
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
}
