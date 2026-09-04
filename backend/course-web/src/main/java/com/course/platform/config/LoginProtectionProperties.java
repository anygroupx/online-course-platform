package com.course.platform.config;

import jakarta.annotation.PostConstruct;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "app.security.login-protection")
public class LoginProtectionProperties {
    private boolean enabled = true;
    private int challengeThreshold = 5;
    private int throttleThreshold = 10;
    private int blockThreshold = 20;
    private long failureWindowSeconds = 900;
    private long throttleWindowSeconds = 30;

    @PostConstruct
    void validate() {
        if (challengeThreshold < 1 || throttleThreshold <= challengeThreshold
                || blockThreshold <= throttleThreshold || failureWindowSeconds < 1
                || throttleWindowSeconds < 1) {
            throw new IllegalStateException("Invalid login-protection thresholds");
        }
    }
}
