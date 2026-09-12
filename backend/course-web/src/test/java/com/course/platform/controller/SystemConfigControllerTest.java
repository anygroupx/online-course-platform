package com.course.platform.controller;

import com.course.platform.application.service.system.SystemConfigService;
import com.course.platform.common.exception.BusinessException;
import com.course.platform.common.result.Result;
import com.course.platform.common.result.ResultCode;
import com.course.platform.common.security.SecurityAuthorities;
import com.course.platform.domain.entity.SystemConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 系统配置控制器单元测试。
 */
@ExtendWith(MockitoExtension.class)
class SystemConfigControllerTest {

    @Mock
    private SystemConfigService systemConfigService;

    @InjectMocks
    private SystemConfigController systemConfigController;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    private void authenticate(String... names) {
        List<SimpleGrantedAuthority> authorities = java.util.Arrays.stream(names)
                .map(SimpleGrantedAuthority::new)
                .toList();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(1L, null, authorities)
        );
    }

    @Test
    @DisplayName("读取权限可获取配置列表")
    void getAllConfigs_readAllowed() {
        authenticate(SecurityAuthorities.SYSTEM_CONFIG_READ);
        SystemConfig config = new SystemConfig();
        config.setConfigKey("site_name");
        when(systemConfigService.getAllConfigs()).thenReturn(List.of(config));

        Result<List<SystemConfig>> result = systemConfigController.getAllConfigs();

        assertEquals(1, result.getData().size());
        assertEquals("site_name", result.getData().get(0).getConfigKey());
    }

    @Test
    @DisplayName("普通用户不能获取完整系统配置")
    void getAllConfigs_ordinaryUserForbidden() {
        authenticate(SecurityAuthorities.ROLE_USER);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> systemConfigController.getAllConfigs());

        assertEquals(ResultCode.FORBIDDEN.getCode(), ex.getCode());
        verify(systemConfigService, never()).getAllConfigs();
    }

    @Test
    @DisplayName("更新权限可批量更新配置")
    void updateConfigs_updateAllowed() {
        authenticate(SecurityAuthorities.SYSTEM_CONFIG_UPDATE);
        Map<String, String> configs = Map.of("site_name", "新名称");

        Result<Void> result = systemConfigController.updateConfigs(configs);

        assertEquals(ResultCode.SUCCESS.getCode(), result.getCode());
        verify(systemConfigService).updateConfigs(configs);
    }

    @Test
    @DisplayName("只有读取权限不能更新配置")
    void updateConfigs_readOnlyForbidden() {
        authenticate(SecurityAuthorities.SYSTEM_CONFIG_READ);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> systemConfigController.updateConfigs(Map.of("site_name", "新名称")));

        assertEquals(ResultCode.FORBIDDEN.getCode(), ex.getCode());
        verify(systemConfigService, never()).updateConfigs(anyMap());
    }

    @Test
    @DisplayName("更新权限可重置单个配置")
    void resetConfig_updateAllowed() {
        authenticate(SecurityAuthorities.SYSTEM_CONFIG_UPDATE);

        Result<Void> result = systemConfigController.resetConfig("site_name");

        assertEquals(ResultCode.SUCCESS.getCode(), result.getCode());
        verify(systemConfigService).resetConfig("site_name");
    }

    @Test
    @DisplayName("普通用户重置配置应拒绝")
    void resetConfig_ordinaryUserForbidden() {
        authenticate(SecurityAuthorities.ROLE_USER);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> systemConfigController.resetConfig("site_name"));

        assertEquals(ResultCode.FORBIDDEN.getCode(), ex.getCode());
        verify(systemConfigService, never()).resetConfig(anyString());
    }

    @Test
    @DisplayName("更新权限可重置全部配置")
    void resetAllConfigs_updateAllowed() {
        authenticate(SecurityAuthorities.SYSTEM_CONFIG_UPDATE);
        when(systemConfigService.resetAllConfigs()).thenReturn(11);

        Result<Map<String, Object>> result = systemConfigController.resetAllConfigs();

        assertEquals(ResultCode.SUCCESS.getCode(), result.getCode());
        assertEquals(11, result.getData().get("count"));
        verify(systemConfigService).resetAllConfigs();
    }
}
