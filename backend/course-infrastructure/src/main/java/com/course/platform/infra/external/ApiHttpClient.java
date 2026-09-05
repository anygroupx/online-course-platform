package com.course.platform.infra.external;

import com.course.platform.common.exception.BusinessException;
import com.course.platform.infra.http.OutboundPolicyRegistry;
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

/** Provider API adapter backed exclusively by the SSRF-safe outbound transport. */
@Slf4j
@Component
public class ApiHttpClient {

    private final SafeHttpClient safeHttpClient;
    private final OutboundPolicyRegistry policies;

    public ApiHttpClient(SafeHttpClient safeHttpClient, OutboundPolicyRegistry policies) {
        this.safeHttpClient = safeHttpClient;
        this.policies = policies;
    }

    public String postForString(String url, Map<String, Object> params) {
        return postForString(url, params, null);
    }

    public String postForString(String url, Map<String, Object> params, HttpHeaders headers) {
        try {
            SafeHttpResponse response = safeHttpClient.postForm(
                    strictUri(url), params, singleHeaders(headers), policies.provider());
            return requireSuccess(response);
        } catch (SafeHttpException | IllegalArgumentException ex) {
            log.warn("Provider request blocked or failed: reason={}", safeReason(ex));
            throw new BusinessException("第三方服务暂不可用");
        }
    }

    public String getForString(String url, Map<String, Object> params) {
        try {
            URI original = strictUri(url);
            var policy = policies.provider();
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
            log.warn("Provider request blocked or failed: reason={}", safeReason(ex));
            throw new BusinessException("第三方服务暂不可用");
        }
    }

    private String requireSuccess(SafeHttpResponse response) {
        if (!response.isSuccessful()) {
            log.warn("Provider returned non-success status: status={}", response.statusCode());
            throw new BusinessException("第三方服务暂不可用");
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

    private String safeReason(Exception ex) {
        return ex instanceof SafeHttpException safe ? safe.getReason().name() : ex.getClass().getSimpleName();
    }
}
