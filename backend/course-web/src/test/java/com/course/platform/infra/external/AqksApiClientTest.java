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
            "https://https-aqks-csuft-edu-cn-443.webvpn.csuft.edu.cn";

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
        var client = client(transport("https://aqks.csuft.edu.cn", ""), mock(SafeHttpClient.class));

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
        when(http.postJson(any(URI.class), anyMap(), anyString(), any()))
                .thenReturn(new SafeHttpResponse(200, "", Map.of()));

        var client = client(transport(PROXY_BASE, "transport-token"), http);

        assertTrue(client.addStudyTime("777", 10, "session-value"));
    }

    @Test
    void addStudyTimeRejectsAnUnrecognizedSuccessfulResponse() {
        SafeHttpClient http = mock(SafeHttpClient.class);
        when(http.postJson(any(URI.class), anyMap(), anyString(), any()))
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
