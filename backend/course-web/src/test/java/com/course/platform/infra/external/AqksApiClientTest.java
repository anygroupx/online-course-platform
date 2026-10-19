package com.course.platform.infra.external;

import com.course.platform.infra.http.OutboundPolicyRegistry;
import com.course.platform.infra.http.OutboundSecurityProperties;
import com.course.platform.infra.http.SafeHttpClient;
import com.course.platform.infra.http.SafeHttpException;
import com.course.platform.infra.http.SafeHttpResponse;
import com.course.platform.infra.http.SsrfGuard;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.net.URI;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Verifies how the AQKS client composes its Cookie header.
 *
 * <p>The integration carries two independent credentials, and conflating them is the main
 * hazard: {@code webvpn-token} is a service-wide transport credential for the campus WebVPN
 * reverse proxy, whereas {@code LoginUserInfo_SYSAQ_Server} is a per-student application
 * session. These cases pin both the request target and the Cookie composition.</p>
 */
class AqksApiClientTest {

    private static final String PROXY_BASE =
            "https://https-aqks-example-edu-cn-443.webvpn.example.edu.cn";

    private AqksTransportProperties transport(String baseUrl, String token) {
        var properties = new AqksTransportProperties();
        properties.setBaseUrl(baseUrl);
        properties.setWebVpnToken(token);
        properties.validate();
        return properties;
    }

    private AqksApiClient client(AqksTransportProperties transport, SafeHttpClient http) {
        return new AqksApiClient(http, new OutboundPolicyRegistry(new OutboundSecurityProperties()), transport);
    }

    private String invokeCookie(AqksApiClient client, String serverCookie) throws Exception {
        Method method = AqksApiClient.class.getDeclaredMethod("buildCookieValue", String.class);
        method.setAccessible(true);
        return (String) method.invoke(client, serverCookie);
    }

    private URI invokeUri(AqksApiClient client, String path, Map<String, ?> query) throws Exception {
        Method method = AqksApiClient.class.getDeclaredMethod("aqksUri", String.class, Map.class);
        method.setAccessible(true);
        return (URI) method.invoke(client, path, query);
    }

    @Test
    void directDeploymentSendsOnlyTheApplicationSession() throws Exception {
        var client = client(transport("https://aqks.example.edu.cn", ""), mock(SafeHttpClient.class));

        String cookie = invokeCookie(client, "session-value");

        assertEquals("LoginUserInfo_SYSAQ_Server=session-value", cookie,
                "a direct deployment must not invent a transport token");
    }

    @Test
    void webVpnDeploymentCarriesBothCredentials() throws Exception {
        var client = client(transport(PROXY_BASE, "header.payload.signature"), mock(SafeHttpClient.class));

        String cookie = invokeCookie(client, "session-value");

        assertEquals("webvpn-token=header.payload.signature; LoginUserInfo_SYSAQ_Server=session-value", cookie);
    }

    @Test
    void loginCallSendsTransportTokenWithoutAnApplicationSession() throws Exception {
        var client = client(transport(PROXY_BASE, "header.payload.signature"), mock(SafeHttpClient.class));

        // Login has no application session yet; the transport token alone must still be sent.
        assertEquals("webvpn-token=header.payload.signature", invokeCookie(client, null));
        assertEquals("webvpn-token=header.payload.signature", invokeCookie(client, "   "));
    }

    @Test
    void requestsTargetTheConfiguredProxyHost() throws Exception {
        var client = client(transport(PROXY_BASE, "token"), mock(SafeHttpClient.class));

        URI uri = invokeUri(client, "/api/MyScores", Map.of("CourseID", "42"));

        assertEquals(PROXY_BASE, uri.getScheme() + "://" + uri.getAuthority(),
                "the proxy host must be the request target when WebVPN transport is configured");
        assertTrue(uri.toString().contains("/api/MyScores"));
        assertTrue(uri.toString().contains("CourseID=42"));
    }

    @Test
    void loginParsesASuccessfulResponse() throws Exception {
        SafeHttpClient http = mock(SafeHttpClient.class);
        var captured = new AtomicReference<Map<String, String>>();
        when(http.postJson(any(URI.class), anyMap(), anyString(), any()))
                .thenAnswer(invocation -> {
                    captured.set(invocation.getArgument(1));
                    return new SafeHttpResponse(200,
                            "{\"ID\":777,\"Token\":\"t-1\",\"UserName\":\"20250001\",\"Name\":\"Student\","
                                    + "\"StudyTimes\":\"35\",\"MinTimeMinute\":\"120\"}",
                            Map.of());
                });

        var client = client(transport(PROXY_BASE, "transport-token"), http);
        var result = client.login("20250001", "secret-password");

        assertTrue(result.isSuccess(), "a response carrying a Token field must be treated as a login success");
        assertEquals("777", result.getUserId());
        assertEquals("35", result.getStudyTimes());
        assertEquals("transport-token",
                extractCookieToken(captured.get().get("Cookie")),
                "the login request must carry the WebVPN transport credential");
    }

    @Test
    void addStudyTimeAcceptsAnEmptySuccessfulResponse() {
        SafeHttpClient http = mock(SafeHttpClient.class);
        when(http.get(any(URI.class), anyMap(), any()))
                .thenReturn(new SafeHttpResponse(204, "", Map.of()));

        var client = client(transport(PROXY_BASE, "transport-token"), http);

        assertTrue(client.addStudyTime("777", 10, "session-value"));
    }

    @Test
    void addStudyTimeUsesGetBecausePostIsRejectedAsForbidden() {
        // The upstream answers POST on this endpoint with 404 + -8002 "no permission",
        // which is a method mismatch masquerading as an auth failure. GET returns 204.
        SafeHttpClient http = mock(SafeHttpClient.class);
        when(http.get(any(URI.class), anyMap(), any()))
                .thenReturn(new SafeHttpResponse(204, "", Map.of()));

        var client = client(transport(PROXY_BASE, "transport-token"), http);

        assertTrue(client.addStudyTime("777", 10, "session-value"));
        org.mockito.Mockito.verify(http).get(any(URI.class), anyMap(), any());
        org.mockito.Mockito.verify(http, org.mockito.Mockito.never())
                .postJson(any(URI.class), anyMap(), anyString(), any());
    }

    @Test
    void loginKeepsTheServerCookieInsteadOfReconstructingIt() throws Exception {
        // The body-derived cookie cannot be decrypted by the server, so follow-up calls
        // fail as "not logged in". The issued Set-Cookie is the only usable value.
        SafeHttpClient http = mock(SafeHttpClient.class);
        String issued = "01227C56EB5Edeadbeef";
        String body = "{\"ID\":100175800,\"UserName\":\"20257270\",\"Token\":\"tok-1\","
                + "\"StudyTimes\":\"458\",\"MinTimeMinute\":\"0\"}";
        when(http.postJson(any(URI.class), anyMap(), anyString(), any()))
                .thenReturn(new SafeHttpResponse(200, body,
                        Map.of("set-cookie", java.util.List.of(
                                "LoginUserInfo_SYSAQ_Server=" + issued + ";expires=Wed, 21 Oct 2026 07:28:00 GMT;path=/"))));

        var client = client(transport(PROXY_BASE, "transport-token"), http);
        var result = client.login("20257270", "secret");

        assertTrue(result.isSuccess());
        assertEquals(issued, result.getServerCookie(),
                "the issued session cookie must be used, not a re-encoded response body");
    }

    @Test
    void businessRejectionIsSurfacedWithItsCodeInsteadOfABareStatus() throws Exception {
        // This is the shape that hid the wrong HTTP method for so long: a non-2xx status whose
        // body explains the rejection. The explanation must reach the caller.
        SafeHttpClient http = mock(SafeHttpClient.class);
        when(http.get(any(URI.class), anyMap(), any()))
                .thenReturn(new SafeHttpResponse(404,
                        "{\"Code\":-8002,\"Message\":\"no permission\"}", Map.of()));

        var client = client(transport(PROXY_BASE, "transport-token"), http);

        assertFalse(client.addStudyTime("777", 10, "session-value"),
                "a rejection still fails the call");

        Method method = AqksApiClient.class.getDeclaredMethod("requireSuccess", SafeHttpResponse.class);
        method.setAccessible(true);
        var thrown = assertThrows(java.lang.reflect.InvocationTargetException.class,
                () -> method.invoke(client, new SafeHttpResponse(404,
                        "{\"Code\":-8002,\"Message\":\"no permission\"}", Map.of())));
        var cause = (com.course.platform.infra.http.UpstreamBusinessException) thrown.getCause();
        assertEquals("-8002", cause.getBusinessCode());
        assertEquals("no permission", cause.getBusinessMessage());
        assertEquals(404, cause.getStatusCode());
    }

    @Test
    void businessRejectionDropsAMessageThatCarriesACredential() throws Exception {
        Method method = AqksApiClient.class.getDeclaredMethod("requireSuccess", SafeHttpResponse.class);
        method.setAccessible(true);
        var client = client(transport(PROXY_BASE, "transport-token"), mock(SafeHttpClient.class));

        var thrown = assertThrows(java.lang.reflect.InvocationTargetException.class,
                () -> method.invoke(client, new SafeHttpResponse(401,
                        "{\"Code\":-1,\"Message\":\"bad password for 20257270\"}", Map.of())));
        var cause = (com.course.platform.infra.http.UpstreamBusinessException) thrown.getCause();

        assertNull(cause.getBusinessMessage(),
                "a message naming a credential must never be recorded");
        assertEquals("-1", cause.getBusinessCode());
    }

    @Test
    void addStudyTimeRejectsAnUnrecognizedSuccessfulResponse() {
        SafeHttpClient http = mock(SafeHttpClient.class);
        when(http.get(any(URI.class), anyMap(), any()))
                .thenReturn(new SafeHttpResponse(200, "accepted", Map.of()));

        var client = client(transport(PROXY_BASE, "transport-token"), http);

        assertFalse(client.addStudyTime("777", 10, "session-value"));
    }

    @Test
    void addStudyTimeRejectsInvalidArgumentsBeforeSendingRequest() {
        SafeHttpClient http = mock(SafeHttpClient.class);
        var client = client(transport(PROXY_BASE, "transport-token"), http);

        assertFalse(client.addStudyTime(" ", 10, "session-value"));
        assertFalse(client.addStudyTime("777", 0, "session-value"));
        assertFalse(client.addStudyTime("777", 10, " "));

        org.mockito.Mockito.verifyNoInteractions(http);
    }

    @Test
    void loginSurfacesTheUpstreamBusinessReasonOnANon2xxStatus() {
        SafeHttpClient http = mock(SafeHttpClient.class);
        // The upstream answers a wrong password with HTTP 404 plus a JSON body naming the real
        // reason. Judging the status before the body is what hid the cause behind a generic
        // "service unavailable".
        when(http.postJson(any(URI.class), anyMap(), anyString(), any()))
                .thenReturn(new SafeHttpResponse(404,
                        "{\"Data\":null,\"Code\":-2,\"Message\":\"添加登录用户（用户登录）失败，用户不存在或密码错误！\"}",
                        Map.of()));

        var client = client(transport(PROXY_BASE, "transport-token"), http);
        var result = client.login("20250001", "secret-password");

        assertFalse(result.isSuccess());
        assertEquals("添加登录用户（用户登录）失败，用户不存在或密码错误！", result.getErrorMessage());
    }

    @Test
    void loginSurfacesALockoutNotice() {
        SafeHttpClient http = mock(SafeHttpClient.class);
        when(http.postJson(any(URI.class), anyMap(), anyString(), any()))
                .thenReturn(new SafeHttpResponse(404,
                        "{\"Data\":null,\"Code\":-99,\"Message\":\"当前客户端连续输入错误达到5次，登录操作已锁定，请于30分钟后再重新输入\"}",
                        Map.of()));

        var client = client(transport(PROXY_BASE, "transport-token"), http);
        var result = client.login("20250001", "secret-password");

        assertFalse(result.isSuccess());
        assertTrue(result.getErrorMessage().contains("锁定"),
                "a lockout must reach the operator instead of being flattened to a generic message");
    }

    @Test
    void loginReportsTheHttpStatusWhenTheBodyIsNotABusinessAnswer() {
        SafeHttpClient http = mock(SafeHttpClient.class);
        when(http.postJson(any(URI.class), anyMap(), anyString(), any()))
                .thenReturn(new SafeHttpResponse(502, "<html>Bad Gateway</html>", Map.of()));

        var client = client(transport(PROXY_BASE, "transport-token"), http);
        var result = client.login("20250001", "secret-password");

        assertFalse(result.isSuccess());
        assertEquals("登录服务返回 HTTP 502", result.getErrorMessage(),
                "an HTML error page must be reported as a status failure, not parsed as a business answer");
    }

    @Test
    void loginNamesTheFailingLayerForATransportFailure() {
        SafeHttpClient http = mock(SafeHttpClient.class);
        when(http.postJson(any(URI.class), anyMap(), anyString(), any()))
                .thenThrow(new SafeHttpException(SafeHttpException.Reason.TIMEOUT));

        var client = client(transport(PROXY_BASE, "transport-token"), http);
        var result = client.login("20250001", "secret-password");

        assertFalse(result.isSuccess());
        assertEquals("登录服务响应超时", result.getErrorMessage(),
                "each transport failure must name its own layer so the cause is identifiable");
    }

    @Test
    void safeHttpExceptionCarriesTheStatusWithoutLeakingContent() {
        var withStatus = new SafeHttpException(SafeHttpException.Reason.HTTP_ERROR, 404);
        var withoutStatus = new SafeHttpException(SafeHttpException.Reason.TIMEOUT);

        assertEquals(404, withStatus.getStatusCode());
        assertNull(withoutStatus.getStatusCode());
        assertTrue(withStatus.getMessage().contains("404"));
        assertFalse(withStatus.getMessage().contains("http"), "the message must not embed a URL");
    }

    @Test
    void loginSendsTheSha256HexDigestOfThePassword() throws Exception {
        SafeHttpClient http = mock(SafeHttpClient.class);
        var captured = new AtomicReference<String>();
        when(http.postJson(any(URI.class), anyMap(), anyString(), any()))
                .thenAnswer(invocation -> {
                    captured.set(invocation.getArgument(2));
                    return new SafeHttpResponse(200,
                            "{\"ID\":777,\"Token\":\"t-1\",\"UserName\":\"20250001\"}", Map.of());
                });

        var client = client(transport(PROXY_BASE, "transport-token"), http);
        client.login("20250001", "secret-password");

        // The browser client sends SHA256(password) as lowercase hex. Sending Base64 instead
        // makes every credential look wrong, which is indistinguishable from a bad password.
        assertEquals("\"" + sha256Hex("secret-password") + "\"", captured.get(),
                "the password must be sent as a quoted SHA256 hex digest");
    }

    @Test
    void loginDoesNotSendThePasswordInClearOrAsBase64() throws Exception {
        SafeHttpClient http = mock(SafeHttpClient.class);
        var captured = new AtomicReference<String>();
        when(http.postJson(any(URI.class), anyMap(), anyString(), any()))
                .thenAnswer(invocation -> {
                    captured.set(invocation.getArgument(2));
                    return new SafeHttpResponse(200,
                            "{\"ID\":777,\"Token\":\"t-1\",\"UserName\":\"20250001\"}", Map.of());
                });

        var client = client(transport(PROXY_BASE, "transport-token"), http);
        client.login("20250001", "secret-password");

        assertFalse(captured.get().contains("secret-password"), "the raw password must never be sent");
        assertFalse(captured.get().contains(Base64.getEncoder().encodeToString("secret-password".getBytes())),
                "Base64 is not accepted by the upstream");
    }

    private String sha256Hex(String value) throws Exception {
        byte[] hash = java.security.MessageDigest.getInstance("SHA-256")
                .digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        StringBuilder hex = new StringBuilder();
        for (byte b : hash) {
            hex.append(Character.forDigit((b >> 4) & 0xF, 16));
            hex.append(Character.forDigit(b & 0xF, 16));
        }
        return hex.toString();
    }

    private String extractCookieToken(String cookieHeader) {
        assertNotNull(cookieHeader, "the login request must send a Cookie header");
        for (String part : cookieHeader.split(";")) {
            String trimmed = part.trim();
            if (trimmed.startsWith("webvpn-token=")) {
                return trimmed.substring("webvpn-token=".length());
            }
        }
        return null;
    }
}
