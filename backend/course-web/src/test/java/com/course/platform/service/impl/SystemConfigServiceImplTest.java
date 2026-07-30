package com.course.platform.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.course.platform.common.exception.BusinessException;
import com.course.platform.domain.entity.SystemConfig;
import com.course.platform.infra.persistence.mapper.SystemConfigMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * 系统配置服务单元测试
 */
@ExtendWith(MockitoExtension.class)
class SystemConfigServiceImplTest {

    @Mock
    private SystemConfigMapper systemConfigMapper;

    @InjectMocks
    private SystemConfigServiceImpl systemConfigService;

    @Test
    @DisplayName("重置已存在配置应写回默认值")
    void resetConfig_shouldUpdateExistingToDefault() {
        SystemConfig existing = new SystemConfig();
        existing.setId(1L);
        existing.setConfigKey("site_name");
        existing.setConfigValue("自定义站点");
        existing.setConfigDesc("网站名称");

        when(systemConfigMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(existing);
        when(systemConfigMapper.updateById(any(SystemConfig.class))).thenReturn(1);

        systemConfigService.resetConfig("site_name");

        ArgumentCaptor<SystemConfig> captor = ArgumentCaptor.forClass(SystemConfig.class);
        verify(systemConfigMapper).updateById(captor.capture());
        assertEquals("在线网课平台", captor.getValue().getConfigValue());
        verify(systemConfigMapper, never()).insert(any(SystemConfig.class));
    }

    @Test
    @DisplayName("重置不存在配置应创建默认项")
    void resetConfig_shouldInsertWhenMissing() {
        when(systemConfigMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);
        when(systemConfigMapper.insert(any(SystemConfig.class))).thenReturn(1);

        systemConfigService.resetConfig("token_expire_minutes");

        ArgumentCaptor<SystemConfig> captor = ArgumentCaptor.forClass(SystemConfig.class);
        verify(systemConfigMapper).insert(captor.capture());
        SystemConfig inserted = captor.getValue();
        assertEquals("token_expire_minutes", inserted.getConfigKey());
        assertEquals("15", inserted.getConfigValue());
        assertEquals("Access Token过期时间（分钟）", inserted.getConfigDesc());
    }

    @Test
    @DisplayName("重置未知配置键应抛业务异常")
    void resetConfig_shouldRejectUnknownKey() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> systemConfigService.resetConfig("unknown_key"));
        assertTrue(ex.getMessage().contains("不支持重置"));
        verify(systemConfigMapper, never()).updateById(any(SystemConfig.class));
        verify(systemConfigMapper, never()).insert(any(SystemConfig.class));
    }

    @Test
    @DisplayName("重置空配置键应抛参数错误")
    void resetConfig_shouldRejectBlankKey() {
        assertThrows(BusinessException.class, () -> systemConfigService.resetConfig("  "));
        assertThrows(BusinessException.class, () -> systemConfigService.resetConfig(null));
    }

    @Test
    @DisplayName("重置全部配置应覆盖所有默认键")
    void resetAllConfigs_shouldResetEveryDefaultKey() {
        when(systemConfigMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);
        when(systemConfigMapper.insert(any(SystemConfig.class))).thenReturn(1);

        int count = systemConfigService.resetAllConfigs();

        assertEquals(11, count);
        verify(systemConfigMapper, times(11)).insert(any(SystemConfig.class));
    }

    @Test
    @DisplayName("更新配置时不存在的键应自动插入")
    void updateConfigs_shouldInsertMissingKeys() {
        when(systemConfigMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);
        when(systemConfigMapper.insert(any(SystemConfig.class))).thenReturn(1);

        Map<String, String> configs = new HashMap<>();
        configs.put("site_name", "新站点");

        systemConfigService.updateConfigs(configs);

        ArgumentCaptor<SystemConfig> captor = ArgumentCaptor.forClass(SystemConfig.class);
        verify(systemConfigMapper).insert(captor.capture());
        assertEquals("新站点", captor.getValue().getConfigValue());
        assertEquals("网站名称", captor.getValue().getConfigDesc());
    }

    @Test
    @DisplayName("更新配置为空应抛参数错误")
    void updateConfigs_shouldRejectEmptyMap() {
        assertThrows(BusinessException.class, () -> systemConfigService.updateConfigs(Map.of()));
        assertThrows(BusinessException.class, () -> systemConfigService.updateConfigs(null));
    }

    @Test
    @DisplayName("整型配置读取失败时应回退默认值")
    void getConfigValueAsInteger_shouldFallback() {
        SystemConfig invalid = new SystemConfig();
        invalid.setConfigKey("token_expire_minutes");
        invalid.setConfigValue("abc");
        when(systemConfigMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(invalid);

        Integer value = systemConfigService.getConfigValueAsInteger("token_expire_minutes", 20);
        assertEquals(20, value);
    }

    @Test
    @DisplayName("配置不存在时整型读取应使用内置默认值")
    void getConfigValueAsInteger_shouldUseBuiltinDefaultWhenMissing() {
        when(systemConfigMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);

        Integer value = systemConfigService.getConfigValueAsInteger("token_expire_minutes", 99);
        assertEquals(15, value);
    }

    @Test
    @DisplayName("布尔配置应识别 1/true")
    void getConfigValueAsBoolean_shouldParseTruthyValues() {
        SystemConfig config = new SystemConfig();
        config.setConfigKey("auto_refresh_token_enabled");
        config.setConfigValue("1");
        when(systemConfigMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(config);

        assertTrue(systemConfigService.getConfigValueAsBoolean("auto_refresh_token_enabled", false));
    }
}
