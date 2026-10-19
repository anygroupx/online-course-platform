package com.course.platform.infra.external;

import jakarta.annotation.PostConstruct;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Transport configuration for the AQKS integration.
 *
 * <p>The platform is reachable in two ways:</p>
 * <ul>
 *   <li><b>Direct</b> — {@code https://aqks.example.edu.cn}, only resolvable from inside the
 *       campus network.</li>
 *   <li><b>WebVPN</b> — the campus reverse proxy publishes the same origin under a
 *       derived host, {@code https-<host-with-dashes>-<port>.webvpn.example.edu.cn}. This is
 *       the only path from outside the campus network.</li>
 * </ul>
 *
 * <p>The WebVPN proxy requires its own {@code webvpn-token} cookie. That token is a
 * <em>transport</em> credential: it authorises tunnelling to the origin, and is
 * independent of the per-student AQKS login that follows over the tunnel. It therefore
 * belongs in service configuration, not in any per-order record.</p>
 */
@Data
@Component
@ConfigurationProperties(prefix = "app.aqks")
public class AqksTransportProperties {

    /** Base URL of the AQKS origin, without a trailing slash. */
    private String baseUrl = "https://aqks.example.edu.cn";

    /**
     * WebVPN transport token. When blank the client talks to {@link #baseUrl} directly,
     * which is the correct behaviour for an in-campus deployment.
     */
    private String webVpnToken = "";

    /** Referer path used to make automated requests look like the browser client. */
    private String refererPath = "/client_pc/exone.html";

    @PostConstruct
    void validate() {
        baseUrl = baseUrl == null ? "" : baseUrl.trim();
        while (baseUrl.endsWith("/")) {
            baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
        }
        if (baseUrl.isEmpty()) {
            throw new IllegalStateException("AQKS base URL must not be blank");
        }
        if (!baseUrl.startsWith("https://")) {
            throw new IllegalStateException("AQKS base URL must use HTTPS");
        }
        webVpnToken = webVpnToken == null ? "" : webVpnToken.trim();
        refererPath = refererPath == null || refererPath.isBlank() ? "/client_pc/exone.html" : refererPath.trim();
    }

    /** Whether requests are tunnelled through the campus WebVPN proxy. */
    public boolean usesWebVpn() {
        return !webVpnToken.isEmpty();
    }
}
