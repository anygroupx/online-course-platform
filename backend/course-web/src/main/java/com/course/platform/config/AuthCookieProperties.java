package com.course.platform.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "app.security.auth-cookie")
public class AuthCookieProperties {
    /** Must be true in production HTTPS deployments. */
    private boolean secure = false;
    private String sameSite = "Strict";
}
