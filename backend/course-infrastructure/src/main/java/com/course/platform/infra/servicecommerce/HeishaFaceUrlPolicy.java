package com.course.platform.infra.servicecommerce;

import com.course.platform.common.exception.BusinessException;
import com.course.platform.infra.http.ProviderUrlNormalizer;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/** Separate, explicit trust boundary for browser-only biometric collection; never an HTTP proxy. */
@Component
public class HeishaFaceUrlPolicy {
    private final Set<String> allowedOrigins;
    private final ProviderUrlNormalizer normalizer = new ProviderUrlNormalizer();

    public HeishaFaceUrlPolicy(
            @Value("${app.native-services.heisha-face-allowed-origins:}") String configured) {
        var origins = new HashSet<String>();
        for (String value : configured.split(",")) {
            if (value.isBlank()) continue;
            URI uri = normalizer.normalize(value.trim());
            if (!"https".equals(uri.getScheme()) || !uri.getRawPath().isEmpty())
                throw new IllegalArgumentException(
                        "Heisha collection allowlist requires exact HTTPS origins");
            origins.add(uri.toASCIIString());
        }
        this.allowedOrigins = Set.copyOf(origins);
    }

    public boolean configured() {
        return !allowedOrigins.isEmpty();
    }

    public void requireConfigured() {
        if (allowedOrigins.isEmpty()) throw new BusinessException("管理员尚未批准官方人脸采集域名，暂不能创建采集链接");
    }

    public URI validate(String value) {
        requireConfigured();
        try {
            if (value == null
                    || value.length() > 4096
                    || value.contains("\\")
                    || value.codePoints()
                            .anyMatch(c -> Character.isISOControl(c) || Character.isWhitespace(c))
                    || value.toLowerCase(Locale.ROOT).matches(".*%(?:0[0-9a-f]|1[0-9a-f]|7f).*"))
                throw rejected();
            URI uri = URI.create(value);
            if (!uri.isAbsolute()
                    || uri.isOpaque()
                    || !"https".equals(uri.getScheme())
                    || uri.getRawUserInfo() != null
                    || uri.getRawFragment() != null
                    || uri.getHost() == null) throw rejected();
            URI base = normalizer.normalize("https://" + uri.getRawAuthority());
            if (!allowedOrigins.contains(base.toASCIIString())) throw rejected();
            // Preserve the signed path/query byte-for-byte. Do not fetch or follow this URL on the
            // server.
            return URI.create(uri.toASCIIString());
        } catch (RuntimeException ex) {
            throw rejected();
        }
    }

    public String origin(String value) {
        URI uri = validate(value);
        return normalizer.normalize("https://" + uri.getRawAuthority()).toASCIIString();
    }

    private static BusinessException rejected() {
        return new BusinessException("上游采集地址不在已批准的 HTTPS 官方域名内，已阻止打开；请联系管理员核实");
    }
}
