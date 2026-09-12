package com.course.platform.domain.vo;

/**
 * 客户端启动配置白名单。
 *
 * <p>仅包含所有访客均可读取的品牌信息和会话行为开关。新增系统配置不会自动进入该响应。</p>
 */
public record ClientBootstrapVO(Branding branding, Session session) {

    public record Branding(String siteName, String siteKeywords, String siteDescription) {
    }

    public record Session(boolean autoRefreshEnabled) {
    }
}
