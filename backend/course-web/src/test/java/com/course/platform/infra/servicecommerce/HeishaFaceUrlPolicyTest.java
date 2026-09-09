package com.course.platform.infra.servicecommerce;

import static org.junit.jupiter.api.Assertions.*;

import com.course.platform.common.exception.BusinessException;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class HeishaFaceUrlPolicyTest {
    final HeishaFaceUrlPolicy policy =
            new HeishaFaceUrlPolicy("https://collect.example, https://second.example");

    @Test
    void exactApprovedHttpsOriginsPreserveSignedUrlWithoutAnyNetworkRequest() {
        String url = "https://collect.example/official/face?signature=ab%2Bcd%3D&batch=2";
        assertEquals(url, policy.validate(url).toASCIIString());
        assertEquals("https://collect.example", policy.origin(url));
        assertEquals(
                "https://collect.example", policy.origin("https://COLLECT.example:443/face?a=b"));
        assertEquals("https://second.example", policy.origin("https://second.example/face"));
    }

    @Test
    void collectionRequiresAnExplicitOperatorAllowlist() {
        assertThrows(
                BusinessException.class, () -> new HeishaFaceUrlPolicy("").requireConfigured());
        assertThrows(
                BusinessException.class,
                () -> new HeishaFaceUrlPolicy("").validate("https://collect.example/face"));
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "http://collect.example",
                "https://collect.example/path",
                "https://collect.example/?foo=bar",
                "https://*.example",
                "https://127.0.0.1",
                "https://collect.example#fragment"
            })
    void configurationCannotSilentlyBecomeAWildcardOrPathPrefix(String value) {
        assertThrows(RuntimeException.class, () -> new HeishaFaceUrlPolicy(value));
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "http://collect.example/face",
                "https://evil.example/face",
                "https://collect.example.evil.example/face",
                "//collect.example/face",
                "javascript:alert(1)",
                "data:text/html,secret",
                "https://user:password@collect.example/face",
                "https://collect.example/face#token",
                "https://collect.example:8443/face",
                "https://127.0.0.1/face",
                "https://[::1]/face",
                "https://collect.example@evil.example/face",
                "https://collect%2eexample/face",
                "https://collect.example/face%0d%0aLocation:secret",
                "https://collect.example/face?token=%00secret",
                "https://collect.example/face with spaces",
                "https://collect.example\\@evil.example/face"
            })
    void maliciousRedirectsNeverEscapeTheApprovedOriginAndNeverEchoTheUrl(String value) {
        var ex = assertThrows(BusinessException.class, () -> policy.validate(value));
        assertFalse(ex.getMessage().contains(value));
        assertNull(ex.getCause());
    }
}
