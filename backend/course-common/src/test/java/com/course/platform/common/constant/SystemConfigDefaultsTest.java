package com.course.platform.common.constant;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 系统配置默认值定义测试
 */
class SystemConfigDefaultsTest {

    @Test
    @DisplayName("应包含 schema 初始配置与 Token 安全配置")
    void shouldContainCoreDefaultKeys() {
        assertTrue(SystemConfigDefaults.hasDefault("site_name"));
        assertTrue(SystemConfigDefaults.hasDefault("user_register_enabled"));
        assertTrue(SystemConfigDefaults.hasDefault("token_expire_minutes"));
        assertTrue(SystemConfigDefaults.hasDefault("refresh_token_expire_days"));
        assertTrue(SystemConfigDefaults.hasDefault("auto_refresh_token_enabled"));
        assertEquals(11, SystemConfigDefaults.all().size());
    }

    @Test
    @DisplayName("默认值应与业务约定一致")
    void shouldExposeExpectedDefaultValues() {
        assertEquals("在线网课平台", SystemConfigDefaults.getDefaultValue("site_name"));
        assertEquals("15", SystemConfigDefaults.getDefaultValue("token_expire_minutes"));
        assertEquals("1", SystemConfigDefaults.getDefaultValue("user_register_enabled"));
        assertNull(SystemConfigDefaults.getDefaultValue("not_exists_key"));
    }

    @Test
    @DisplayName("默认配置映射应不可变")
    void defaultsMapShouldBeImmutable() {
        assertThrows(UnsupportedOperationException.class, () ->
                SystemConfigDefaults.all().put("x", new SystemConfigDefaults.DefaultConfig("1", "x")));
    }
}
