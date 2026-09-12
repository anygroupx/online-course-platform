package com.course.platform.controller;

import com.course.platform.application.service.system.SystemConfigService;
import com.course.platform.common.result.Result;
import com.course.platform.domain.vo.ClientBootstrapVO;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ClientBootstrapControllerTest {

    @Mock
    private SystemConfigService systemConfigService;

    @InjectMocks
    private ClientBootstrapController controller;

    @Test
    void bootstrapUsesDefaultsForMissingValues() {
        when(systemConfigService.getConfigValueAsBoolean("auto_refresh_token_enabled", true))
                .thenReturn(true);

        Result<ClientBootstrapVO> result = controller.bootstrap();

        assertEquals("在线网课平台", result.getData().branding().siteName());
        assertEquals("网课,在线教育,代刷", result.getData().branding().siteKeywords());
        assertEquals("专业的在线网课服务平台", result.getData().branding().siteDescription());
        assertTrue(result.getData().session().autoRefreshEnabled());
    }

    @Test
    void bootstrapOnlySerializesTheFixedClientWhitelist() throws Exception {
        when(systemConfigService.getConfigValue("site_name")).thenReturn("课程中心");
        when(systemConfigService.getConfigValue("site_keywords")).thenReturn("课程,学习");
        when(systemConfigService.getConfigValue("site_description")).thenReturn("课程服务");
        when(systemConfigService.getConfigValueAsBoolean("auto_refresh_token_enabled", true))
                .thenReturn(false);

        String json = new ObjectMapper().writeValueAsString(controller.bootstrap().getData());

        assertTrue(json.contains("课程中心"));
        assertTrue(json.contains("autoRefreshEnabled"));
        assertFalse(json.contains("configKey"));
        assertFalse(json.contains("tokenExpireMinutes"));
        assertFalse(json.contains("userRegisterFee"));
        verify(systemConfigService, never()).getAllConfigs();
        verify(systemConfigService).getConfigValue("site_name");
        verify(systemConfigService).getConfigValue("site_keywords");
        verify(systemConfigService).getConfigValue("site_description");
    }
}
