package com.course.platform.infra.http;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertFalse;

class ProviderUrlNormalizerTest {

    private final ProviderUrlNormalizer normalizer = new ProviderUrlNormalizer();

    @Test
    void normalizesOriginAndBasePathWithoutRemovingApiScript() {
        assertEquals("https://provider.example",
                normalizer.normalizeToString("  HTTPS://Provider.Example:443/  "));
        assertEquals("https://provider.example/openapi",
                normalizer.normalizeToString("https://Provider.Example/a/../openapi///"));
        assertEquals("https://provider.example/api.php",
                normalizer.normalizeToString("https://Provider.Example/api.php"));
    }

    @Test
    void canonicalizesIdnAndDaytimeBasePaths() {
        assertEquals("https://xn--bcher-kva.example/openapi",
                normalizer.normalize("HTTPS://Bücher.Example:443/a/../openapi/api.php/", "Daytime").toString());
        assertEquals("https://provider.example",
                normalizer.normalize("https://provider.example/../../api.php", "29").toString());
        assertEquals("https://provider.example/openapi",
                normalizer.normalize("https://provider.example/a/../../openapi/./", "Daytime").toString());
        assertEquals("http://legacy.example/api", normalizer.normalizeToString("HTTP://LEGACY.EXAMPLE:80/api/"));
        assertEquals("https://provider.example/%E8%AF%BE%E7%A8%8B",
                normalizer.normalizeToString("https://provider.example/课程/"));
    }

    @Test
    void boundsInputLengthAndNeverIncludesRejectedSecretsInTheException() {
        for (String value : new String[]{null, "https://provider.example/" + "a".repeat(2048),
                "https://user:very-secret-password@provider.example"}) {
            var error = assertThrows(SafeHttpException.class, () -> normalizer.normalize(value));
            assertNull(error.getCause());
            assertFalse(error.toString().contains("very-secret-password"));
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "", "relative/path", "file:///etc/passwd",
            "https://user:secret@provider.example",
            "https://provider.example?token=secret",
            "https://provider.example/#fragment",
            "https://provider.example./", "https://provider.example:", "https://provider.example:0",
            "https://provider.example:65536", "https://provider.example:443:443", "https://provider.example:abc",
            "https://127.0.0.1", "https://8.8.8.8", "https://2130706433", "https://127.1",
            "https://0177.0.0.1", "https://0x7f000001", "https://0x7f.0.0.1", "https://[::1]",
            "https://[2001:4860:4860::8888]", "https://[::ffff:127.0.0.1]",
            "https://localhost", "https://service.localhost", "https://single-label",
            "https://provider%2eexample", "https://provider.example/path/%2e%2e", "https://provider.example/%2f",
            "https://provider.example/%5c", "https://provider.example/%00", "https://provider.example/path\\escape",
            "https://provider.example?", "https://provider.example#", "https://provider..example",
            "https://-provider.example", "https://provider_.example", "https://provider.example/path\nsecret"
    })
    void rejectsValuesThatAreNotProviderBaseUrls(String value) {
        assertThrows(SafeHttpException.class, () -> normalizer.normalize(value));
    }
}
