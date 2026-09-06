package com.course.platform.infra.http;

import jakarta.annotation.PostConstruct;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/** Centralized outbound HTTP restrictions. */
@Data
@Component
@ConfigurationProperties(prefix = "app.security.outbound")
public class OutboundSecurityProperties {
    private String dnsMode = "system";
    private int connectTimeoutMillis = 5_000;
    private int readTimeoutMillis = 15_000;
    private int callTimeoutMillis = 20_000;
    private int maxResponseBytes = 1_048_576;
    // Provider 查询可能经过上游聚合；大目录也不应放宽 Turnstile/告警等固定集成。
    private int providerReadTimeoutMillis = 35_000;
    private int providerCallTimeoutMillis = 40_000;
    private int providerMaxResponseBytes = 8_388_608;
    private List<String> providerHttpAllowedHosts = new ArrayList<>();
    private List<Integer> providerAllowedPorts = new ArrayList<>();
    private List<String> alertWebhookAllowedHosts = new ArrayList<>();
    private List<String> alertWebhookHttpAllowedHosts = new ArrayList<>();

    @PostConstruct
    void validate() {
        dnsMode = dnsMode == null ? "system" : dnsMode.trim().toLowerCase(java.util.Locale.ROOT);
        if (!"system".equals(dnsMode) && !"cloudflare-doh".equals(dnsMode)) {
            throw new IllegalStateException("Unsupported outbound DNS mode");
        }
        if (connectTimeoutMillis < 100 || readTimeoutMillis < 100 || callTimeoutMillis < 100
                || maxResponseBytes < 1_024) {
            throw new IllegalStateException("Invalid outbound HTTP security limits");
        }
        if (providerReadTimeoutMillis < 100 || providerCallTimeoutMillis < providerReadTimeoutMillis
                || providerCallTimeoutMillis < connectTimeoutMillis || providerCallTimeoutMillis > 120_000
                || providerMaxResponseBytes < 1_024 || providerMaxResponseBytes > 16_777_216) {
            throw new IllegalStateException("Invalid provider HTTP limits (maximum 120s / 16MiB)");
        }
        if (callTimeoutMillis < connectTimeoutMillis) {
            throw new IllegalStateException("Outbound call timeout must be >= connect timeout");
        }
    }
}
