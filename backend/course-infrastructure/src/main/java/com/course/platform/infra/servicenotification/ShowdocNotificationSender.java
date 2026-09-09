package com.course.platform.infra.servicenotification;

import com.course.platform.application.service.servicenotification.ServiceNotificationSender;
import com.course.platform.domain.servicenotification.ServiceNotificationTypes.*;
import com.course.platform.infra.http.*;
import com.fasterxml.jackson.core.*;
import com.fasterxml.jackson.databind.*;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Component;

import java.net.URI;
import java.time.Duration;
import java.util.*;

/** Official ShowDoc form protocol; token is a path credential, never a caller-supplied URL. */
@Component
@RequiredArgsConstructor
public class ShowdocNotificationSender implements ServiceNotificationSender {
    private final SafeHttpClient http;
    private static final OutboundRequestPolicy POLICY =
            new OutboundRequestPolicy(
                    "service-notification-showdoc",
                    Set.of("push.showdoc.com.cn"),
                    Set.of(),
                    Set.of(443),
                    4096,
                    Duration.ofSeconds(3),
                    Duration.ofSeconds(5),
                    Duration.ofSeconds(8));
    private static final ObjectMapper JSON =
            new ObjectMapper(
                            JsonFactory.builder()
                                    .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
                                    .streamReadConstraints(
                                            StreamReadConstraints.builder()
                                                    .maxStringLength(1024)
                                                    .maxNestingDepth(8)
                                                    .maxNumberLength(16)
                                                    .build())
                                    .build())
                    .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);

    @Override
    public Receipt send(String token, Message message) {
        if (token == null
                || !token.matches("[A-Za-z0-9_-]{16,128}")
                || message == null
                || message.title() == null
                || message.title().length() > 100
                || message.content() == null
                || message.content().length() > 2000) return Receipt.REJECTED;
        try {
            var result =
                    http.postForm(
                            URI.create("https://push.showdoc.com.cn/server/api/push/" + token),
                            Map.of("title", message.title(), "content", message.content()),
                            Map.of(),
                            POLICY);
            if (result == null
                    || !result.isSuccessful()
                    || result.body() == null
                    || result.body().length() > 4096) return Receipt.UNKNOWN;
            var root = JSON.readTree(result.body());
            if (root == null || !root.isObject() || !root.path("error_code").isIntegralNumber())
                return Receipt.UNKNOWN;
            return root.path("error_code").canConvertToInt()
                            && root.path("error_code").intValue() == 0
                    ? Receipt.ACCEPTED
                    : Receipt.REJECTED;
        } catch (Exception e) {
            return Receipt.UNKNOWN;
        }
    }
}
