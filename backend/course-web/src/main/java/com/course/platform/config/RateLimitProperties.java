package com.course.platform.config;

import jakarta.annotation.PostConstruct;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/** Tunable P0 application rate limits. Values are intentionally conservative defaults. */
@Data
@Component
@ConfigurationProperties(prefix = "app.security.rate-limit")
public class RateLimitProperties {
    private boolean enabled = true;
    private boolean failClosed = true;
    private Rule loginIp = new Rule(30, 60);
    private Rule registerIp = new Rule(5, 600);
    private Rule inviteIp = new Rule(30, 60);
    private Rule refreshIp = new Rule(60, 60);
    private Rule refreshCredential = new Rule(20, 60);
    private Rule mfaIp = new Rule(10, 300);
    private Rule paymentNotifyIp = new Rule(300, 60);
    private Rule passwordIp = new Rule(20, 900);
    private Rule passwordUser = new Rule(5, 900);
    private Rule apiKeyUser = new Rule(5, 900);
    private Rule externalIp = new Rule(180, 60);
    private Rule externalKey = new Rule(120, 60);
    private Rule orderUser = new Rule(30, 60);
    private Rule paymentUser = new Rule(15, 60);
    private Rule refundUser = new Rule(5, 900);
    private Rule exportUser = new Rule(5, 600);

    @PostConstruct
    void validate() {
        for (Rule rule : new Rule[]{loginIp, registerIp, inviteIp, refreshIp, refreshCredential,
                mfaIp, paymentNotifyIp, passwordIp, passwordUser, apiKeyUser, externalIp, externalKey,
                orderUser, paymentUser, refundUser, exportUser}) {
            if (rule == null || rule.limit < 1 || rule.windowSeconds < 1) {
                throw new IllegalStateException("Invalid rate-limit rule");
            }
        }
    }

    @Data
    public static class Rule {
        private int limit;
        private long windowSeconds;

        public Rule() {}
        public Rule(int limit, long windowSeconds) {
            this.limit = limit;
            this.windowSeconds = windowSeconds;
        }
    }
}
