package com.course.platform.common.constant;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * 系统配置默认值定义
 * <p>
 * 统一维护 schema 初始值与业务默认值，供配置重置、缺省回退使用。
 */
public final class SystemConfigDefaults {

    private SystemConfigDefaults() {
    }

    /**
     * 默认配置项：key -> [defaultValue, description]
     */
    private static final Map<String, DefaultConfig> DEFAULTS;

    static {
        Map<String, DefaultConfig> defaults = new LinkedHashMap<>();
        // 基础站点配置（database/schema.sql 初始数据）
        defaults.put("site_name", new DefaultConfig("在线网课平台", "网站名称"));
        defaults.put("site_keywords", new DefaultConfig("网课,在线教育,代刷", "网站关键词"));
        defaults.put("site_description", new DefaultConfig("专业的在线网课服务平台", "网站描述"));
        defaults.put("system_notice", new DefaultConfig("欢迎使用本平台！", "系统公告"));
        defaults.put("user_register_enabled", new DefaultConfig("1", "用户注册开关：0-关闭 1-开启"));
        defaults.put("user_register_fee", new DefaultConfig("5", "用户开户费用"));
        defaults.put("min_recharge_amount", new DefaultConfig("10", "最低充值金额"));
        defaults.put("api_enable_threshold", new DefaultConfig("300", "API开通免费门槛（余额）"));

        // Token 安全配置（前端 Settings 高级设置）
        defaults.put("token_expire_minutes", new DefaultConfig("15", "Access Token过期时间（分钟）"));
        defaults.put("refresh_token_expire_days", new DefaultConfig("7", "Refresh Token过期时间（天）"));
        defaults.put("auto_refresh_token_enabled", new DefaultConfig("1", "启用自动刷新Token：0-关闭 1-开启"));

        DEFAULTS = Collections.unmodifiableMap(defaults);
    }

    /**
     * 获取全部默认配置（不可变）
     */
    public static Map<String, DefaultConfig> all() {
        return DEFAULTS;
    }

    /**
     * 是否存在默认值定义
     */
    public static boolean hasDefault(String configKey) {
        return configKey != null && DEFAULTS.containsKey(configKey);
    }

    /**
     * 获取默认配置项
     */
    public static Optional<DefaultConfig> get(String configKey) {
        if (configKey == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(DEFAULTS.get(configKey));
    }

    /**
     * 获取默认值，不存在时返回 null
     */
    public static String getDefaultValue(String configKey) {
        return get(configKey).map(DefaultConfig::value).orElse(null);
    }

    /**
     * 默认配置值对象
     *
     * @param value       默认值
     * @param description 配置描述
     */
    public record DefaultConfig(String value, String description) {
    }
}
