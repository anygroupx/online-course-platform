package com.course.platform.infra.external;

import com.course.platform.domain.exception.ProviderRequestException;
import com.course.platform.domain.entity.ApiProvider;
import com.course.platform.infra.http.OutboundRequestPolicy;
import com.course.platform.infra.http.ProviderOutboundPolicyFactory;
import com.course.platform.infra.http.SafeHttpClient;
import com.course.platform.infra.http.SafeHttpException;
import com.course.platform.infra.http.SafeHttpResponse;
import lombok.extern.slf4j.Slf4j;
import okhttp3.HttpUrl;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

/** Provider API adapter backed exclusively by the SSRF-safe outbound transport. */
@Slf4j
@Component
public class ApiHttpClient {

    private final SafeHttpClient safeHttpClient;
    private final ProviderOutboundPolicyFactory policyFactory;

    public ApiHttpClient(SafeHttpClient safeHttpClient, ProviderOutboundPolicyFactory policyFactory) {
        this.safeHttpClient = safeHttpClient;
        this.policyFactory = policyFactory;
    }

    public String postForString(ApiProvider provider, String url, Map<String, Object> params) {
        return postForString(provider, url, params, null);
    }

    public String postForString(ApiProvider provider, String url, Map<String, Object> params,
                                HttpHeaders headers) {
        long started = System.nanoTime();
        try {
            URI endpoint = strictUri(url);
            OutboundRequestPolicy policy = policyFactory.forProvider(provider, endpoint);
            SafeHttpResponse response = safeHttpClient.postForm(
                    endpoint, params, singleHeaders(headers), policy);
            return requireSuccess(response);
        } catch (SafeHttpException | IllegalArgumentException ex) {
            throw failure(provider, "POST", ex, started);
        }
    }

    public String getForString(ApiProvider provider, String url, Map<String, Object> params) {
        long started = System.nanoTime();
        try {
            URI original = strictUri(url);
            OutboundRequestPolicy policy = policyFactory.forProvider(provider, original);
            safeHttpClient.validate(original, policy); // Before HttpUrl can canonicalize the authority.
            HttpUrl parsed = HttpUrl.get(original.toURL());
            HttpUrl.Builder builder = parsed.newBuilder();
            if (params != null) {
                params.forEach((key, value) -> {
                    if (key != null && value != null) builder.addQueryParameter(key, String.valueOf(value));
                });
            }
            SafeHttpResponse response = safeHttpClient.get(
                    builder.build().uri(), Map.of(), policy);
            return requireSuccess(response);
        } catch (SafeHttpException | IllegalArgumentException | java.net.MalformedURLException ex) {
            throw failure(provider, "GET", ex, started);
        }
    }

    private String requireSuccess(SafeHttpResponse response) {
        if (response == null) {
            throw new SafeHttpException(SafeHttpException.Reason.INVALID_RESPONSE);
        }
        if (!response.isSuccessful()) {
            throw new SafeHttpException(SafeHttpException.Reason.HTTP_ERROR);
        }
        return response.body();
    }

    private URI strictUri(String value) {
        if (value == null || value.isBlank() || value.length() > 2048) {
            throw new SafeHttpException(SafeHttpException.Reason.BLOCKED_DESTINATION);
        }
        try {
            return URI.create(value);
        } catch (IllegalArgumentException ex) {
            throw new SafeHttpException(SafeHttpException.Reason.BLOCKED_DESTINATION, ex);
        }
    }

    private Map<String, String> singleHeaders(HttpHeaders headers) {
        if (headers == null || headers.isEmpty()) return Map.of();
        Map<String, String> values = new LinkedHashMap<>();
        headers.forEach((key, list) -> {
            if (key != null && list != null && !list.isEmpty()) values.put(key, list.get(0));
        });
        return values;
    }

    private Object providerId(ApiProvider provider) {
        return provider == null || provider.getId() == null ? "unknown" : provider.getId();
    }

    private ProviderRequestException failure(ApiProvider provider, String operation, Exception cause, long started) {
        var reason = cause instanceof SafeHttpException safe
                ? ProviderRequestException.Reason.valueOf(safe.getReason().name())
                : ProviderRequestException.Reason.BLOCKED_DESTINATION;
        ProviderRequestException failure = new ProviderRequestException(reason);
        String host = "unknown";
        try {
            String candidate = URI.create(provider.getApiUrl()).getHost();
            if (candidate != null && candidate.matches("[A-Za-z0-9.-]{1,253}")) {
                host = candidate.toLowerCase(Locale.ROOT);
            }
        } catch (RuntimeException ignored) {
            // Never log the original URL or exception; either can contain credentials.
        }
        String type = provider == null ? null : provider.getProviderType();
        type = type != null && type.matches("[A-Za-z0-9_-]{1,50}") ? type : "unknown";
        log.warn("Provider request failed: providerId={}, providerType={}, operation={}, normalizedHost={}, reason={}, errorId={}, durationMs={}",
                providerId(provider), type, operation, host, reason, failure.getErrorId(),
                TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started));
        return failure;
    }
}
