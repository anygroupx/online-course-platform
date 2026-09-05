package com.course.platform.infra.http;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.net.*;
import java.time.Duration;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class SsrfGuardTest {
    static OutboundRequestPolicy policy(Set<String> hosts, Set<String> http, Set<Integer> ports) {
        return new OutboundRequestPolicy("test", hosts, http, ports, 1024,
                Duration.ofMillis(300), Duration.ofMillis(300), Duration.ofMillis(600));
    }
    private final OutboundRequestPolicy policy = policy(Set.of("api.example"), Set.of(), Set.of());
    private SsrfGuard publicGuard() {
        return new SsrfGuard(host -> List.of(InetAddress.getByName("8.8.8.8")));
    }

    @ParameterizedTest
    @ValueSource(strings={"http://api.example/", "https://api.example:444/", "https://api.example./",
            "https://api.example.evil.test/", "https://evil.test/", "https://user:secret@api.example/",
            "https://api.example/#secret", "https://api%2eexample/", "//api.example/",
            "file:///etc/passwd", "https://localhost/", "https://127.0.0.1/", "https://2130706433/"})
    void rejectsUnsafeUris(String url) {
        assertEquals(SafeHttpException.Reason.BLOCKED_DESTINATION,
                assertThrows(SafeHttpException.class, () -> publicGuard().validate(URI.create(url), policy)).getReason());
    }

    @ParameterizedTest
    @ValueSource(strings={"0.0.0.0","10.0.0.1","127.0.0.1","169.254.169.254","172.16.0.1",
            "192.168.1.1","100.64.0.1","192.0.0.1","192.0.2.1","192.88.99.1","198.18.0.1",
            "198.51.100.1","203.0.113.1","224.0.0.1","240.0.0.1","::1","::","fc00::1",
            "fe80::1","ff02::1","2001:db8::1","2001::1","2001:2::1","2002:7f00:1::1",
            "::ffff:127.0.0.1","64:ff9b::7f00:1","3fff::1"})
    void rejectsNonPublicDnsAnswers(String address) {
        SsrfGuard guard = new SsrfGuard(host -> List.of(InetAddress.getByName(address)));
        assertEquals(SafeHttpException.Reason.BLOCKED_DESTINATION,
                assertThrows(SafeHttpException.class, () -> guard.validate(URI.create("https://api.example/"), policy)).getReason());
    }

    @Test void rejectsMixedPublicAndPrivateAnswers() {
        SsrfGuard guard = new SsrfGuard(host -> List.of(InetAddress.getByName("8.8.8.8"), InetAddress.getByName("10.0.0.1")));
        assertThrows(SafeHttpException.class, () -> guard.validate(URI.create("https://api.example/"), policy));
    }
    @Test void pinsImmutablePublicAnswers() throws Exception {
        var answers = new ArrayList<>(List.of(InetAddress.getByName("8.8.8.8"), InetAddress.getByName("2001:4860:4860::8888")));
        var result = new SsrfGuard(host -> answers).validate(URI.create("https://API.EXAMPLE/"), policy);
        answers.clear();
        assertEquals("api.example", result.asciiHost());
        assertEquals(2, result.pinnedAddresses().size());
        assertThrows(UnsupportedOperationException.class, () -> result.pinnedAddresses().clear());
    }
    @Test void allowsOnlyExplicitHttpAndPorts() {
        var explicit = policy(Set.of("api.example"), Set.of("api.example"), Set.of(8080));
        assertNotNull(publicGuard().validate(URI.create("http://api.example:8080/"), explicit));
    }
    @Test void deniesEmptyPoliciesAndHttpOutsideMainList() {
        assertThrows(IllegalArgumentException.class, () -> policy(Set.of(), Set.of(), Set.of()));
        assertThrows(IllegalArgumentException.class, () -> policy(Set.of("a.test"), Set.of("b.test"), Set.of()));
    }
    @Test void dnsFailureDoesNotRetainUnsafeCause() {
        var guard = new SsrfGuard(host -> { throw new UnknownHostException("secret-url-token"); });
        var ex = assertThrows(SafeHttpException.class, () -> guard.validate(URI.create("https://api.example/"), policy));
        assertEquals(SafeHttpException.Reason.DNS_FAILURE, ex.getReason());
        assertNull(ex.getCause());
        assertFalse(ex.toString().contains("secret"));
    }
    @Test void boundsDnsWait() {
        var guard = new SsrfGuard(host -> {
            try { Thread.sleep(5000); } catch (InterruptedException ex) { Thread.currentThread().interrupt(); }
            return List.of();
        });
        assertTimeoutPreemptively(Duration.ofSeconds(2), () -> assertEquals(SafeHttpException.Reason.TIMEOUT,
                assertThrows(SafeHttpException.class, () -> guard.validate(URI.create("https://api.example/"), policy)).getReason()));
    }
}
