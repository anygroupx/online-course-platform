package com.course.platform.infra.http;

import org.springframework.stereotype.Component;

import java.net.IDN;
import java.net.URI;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Locale;

/** Canonical provider base URLs, never credentials, IP literals, queries or actions. */
@Component
public class ProviderUrlNormalizer {

    private static final int MAX_URL_LENGTH = 2048;

    public URI normalize(String value) {
        if (value == null || value.isBlank() || value.length() > MAX_URL_LENGTH) {
            throw blocked();
        }
        String input = value.trim();
        if (input.chars().anyMatch(c -> c <= 0x20 || c == 0x7f || c == '\\')) {
            throw blocked();
        }
        try {
            URI raw = URI.create(input);
            if (!raw.isAbsolute() || raw.isOpaque() || raw.getRawAuthority() == null
                    || raw.getRawUserInfo() != null || raw.getRawQuery() != null
                    || raw.getRawFragment() != null) {
                throw blocked();
            }
            String scheme = raw.getScheme().toLowerCase(Locale.ROOT);
            if (!"https".equals(scheme) && !"http".equals(scheme)) {
                throw blocked();
            }

            // URI#getHost is null for Unicode domain names. Parse only an unambiguous DNS authority
            // before IDN conversion; never let an HTTP client's lenient parser reinterpret it.
            String authority = raw.getRawAuthority();
            if (authority.contains("%") || authority.contains("@") || authority.contains("[")
                    || authority.contains("]") || authority.indexOf(':') != authority.lastIndexOf(':')) {
                throw blocked();
            }
            int separator = authority.indexOf(':');
            String host = separator < 0 ? authority : authority.substring(0, separator);
            host = IDN.toASCII(host, IDN.USE_STD3_ASCII_RULES).toLowerCase(Locale.ROOT);
            if (host.isBlank() || host.length() > 253 || host.endsWith(".") || !host.contains(".")
                    || host.endsWith(".localhost")) {
                throw blocked();
            }
            // Numeric/hex/octal IPv4 variants have a numeric final label. Reject these as well as
            // ordinary IP literals; a provider must name a DNS host, not bypass DNS validation.
            String lastLabel = host.substring(host.lastIndexOf('.') + 1);
            if (lastLabel.matches("[0-9]+|0x[0-9a-f]+")) {
                throw blocked();
            }
            for (String label : host.split("\\.", -1)) {
                if (label.isEmpty() || label.length() > 63) {
                    throw blocked();
                }
            }

            int port = -1;
            if (separator >= 0) {
                String portString = authority.substring(separator + 1);
                if (!portString.matches("[0-9]{1,5}")) {
                    throw blocked();
                }
                port = Integer.parseInt(portString);
                if (port < 1 || port > 65535) {
                    throw blocked();
                }
                if ((port == 443 && "https".equals(scheme)) || (port == 80 && "http".equals(scheme))) {
                    port = -1;
                }
            }

            String path = normalizePath(raw.getRawPath());
            URI normalized = URI.create(scheme + "://" + host + (port < 0 ? "" : ":" + port) + path);
            if (normalized.toASCIIString().length() > MAX_URL_LENGTH) {
                throw blocked();
            }
            return URI.create(normalized.toASCIIString());
        } catch (IllegalArgumentException ex) {
            throw blocked();
        }
    }

    /** Daytime and its 29 alias accept both the base directory and the historical api.php URL. */
    public URI normalize(String value, String providerType) {
        URI normalized = normalize(value);
        if (("Daytime".equalsIgnoreCase(providerType) || "29".equals(providerType))
                && normalized.getRawPath().toLowerCase(Locale.ROOT).endsWith("/api.php")) {
            String url = normalized.toASCIIString();
            return URI.create(url.substring(0, url.length() - "/api.php".length()));
        }
        return normalized;
    }

    public String normalizeToString(String value) {
        return normalize(value).toASCIIString();
    }

    private String normalizePath(String path) {
        if (path == null || path.isEmpty()) {
            return "";
        }
        // Encoded separators/dot-segments/control bytes are ambiguous across URI/HTTP parsers.
        if (path.toLowerCase(Locale.ROOT).matches(".*%(2e|2f|5c|0[0-9a-f]|1[0-9a-f]|7f).*")) {
            throw blocked();
        }
        Deque<String> segments = new ArrayDeque<>();
        for (String segment : path.split("/")) {
            if (segment.isEmpty() || ".".equals(segment)) {
                continue;
            }
            if ("..".equals(segment)) {
                if (!segments.isEmpty()) segments.removeLast();
            } else {
                segments.addLast(segment);
            }
        }
        return segments.isEmpty() ? "" : "/" + String.join("/", segments);
    }

    private SafeHttpException blocked() {
        return new SafeHttpException(SafeHttpException.Reason.BLOCKED_DESTINATION);
    }
}
