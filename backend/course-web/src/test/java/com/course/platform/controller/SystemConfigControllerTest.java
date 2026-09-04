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
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * 系统配置控制器单元测试
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

    private Authentication authWithAuthorities(Long userId, String... names) {
        List<SimpleGrantedAuthority> authorities = java.util.Arrays.stream(names)
                .map(SimpleGrantedAuthority::new)
                .toList();
        Authentication authentication = new UsernamePasswordAuthenticationToken(userId, null, authorities);
        SecurityContextHolder.getContext().setAuthentication(authentication);
        return authentication;
    }

    @Test
    @DisplayName("管理员可重置单个配置")
    void resetConfig_adminAllowed() {
        Authentication authentication = authWithAuthorities(1L, SecurityAuthorities.SYSTEM_CONFIG_UPDATE);

        Result<Void> result = systemConfigController.resetConfig("site_name", authentication);

        assertEquals(ResultCode.SUCCESS.getCode(), result.getCode());
        verify(systemConfigService).resetConfig("site_name");
    }

    @Test
    @DisplayName("非管理员重置配置应拒绝")
    void resetConfig_nonAdminForbidden() {
        Authentication authentication = authWithAuthorities(2L, SecurityAuthorities.ROLE_USER);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> systemConfigController.resetConfig("site_name", authentication));
        assertEquals(ResultCode.FORBIDDEN.getCode(), ex.getCode());
        verify(systemConfigService, never()).resetConfig(anyString());
    }

    @Test
    @DisplayName("管理员可重置全部配置")
    void resetAllConfigs_adminAllowed() {
        Authentication authentication = authWithAuthorities(1L, SecurityAuthorities.SYSTEM_CONFIG_UPDATE);
        when(systemConfigService.resetAllConfigs()).thenReturn(11);

        Result<Map<String, Object>> result = systemConfigController.resetAllConfigs(authentication);

        assertEquals(ResultCode.SUCCESS.getCode(), result.getCode());
        assertEquals(11, result.getData().get("count"));
        verify(systemConfigService).resetAllConfigs();
    }

    @Test
    @DisplayName("获取配置列表无需额外权限校验")
    void getAllConfigs_shouldReturnList() {
        SystemConfig config = new SystemConfig();
        config.setConfigKey("site_name");
        when(systemConfigService.getAllConfigs()).thenReturn(List.of(config));

        Result<List<SystemConfig>> result = systemConfigController.getAllConfigs();

        assertEquals(1, result.getData().size());
        assertEquals("site_name", result.getData().get(0).getConfigKey());
    }
}
