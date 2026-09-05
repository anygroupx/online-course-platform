package com.course.platform.infra.http;

import lombok.extern.slf4j.Slf4j;
import okhttp3.Call;
import okhttp3.Dns;
import okhttp3.FormBody;
import okhttp3.ConnectionPool;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;
import okio.BufferedSource;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.Proxy;
import java.net.Socket;
import java.net.InetAddress;
import javax.net.SocketFactory;
import java.io.InterruptedIOException;
import java.net.URI;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * The only general-purpose outbound HTTP transport. DNS answers are validated once and then
 * pinned into OkHttp, redirects are disabled, and response bodies are bounded before parsing.
 */
@Slf4j
@Component
public class SafeHttpClient {
    private static final Set<String> FORBIDDEN_REQUEST_HEADERS = Set.of(
            "host", "connection", "content-length", "transfer-encoding", "proxy-authorization",
            "proxy-authenticate", "upgrade", "keep-alive", "te", "trailer");

    private final SsrfGuard ssrfGuard;

    public SafeHttpClient(SsrfGuard ssrfGuard) {
        this.ssrfGuard = ssrfGuard;
    }

    public SafeHttpResponse get(URI uri, Map<String, String> headers, OutboundRequestPolicy policy) {
        return execute("GET", uri, headers, null, null, policy);
    }

    public SafeHttpResponse postJson(URI uri, Map<String, String> headers, String json,
                                     OutboundRequestPolicy policy) {
        return execute("POST", uri, headers, json == null ? new byte[0] : json.getBytes(StandardCharsets.UTF_8),
                "application/json; charset=utf-8", policy);
    }

    public SafeHttpResponse postForm(URI uri, Map<String, ?> params, Map<String, String> headers,
                                     OutboundRequestPolicy policy) {
        FormBody.Builder form = new FormBody.Builder(StandardCharsets.UTF_8);
        if (params != null) {
            params.forEach((key, value) -> {
                if (key != null && value != null) form.add(key, String.valueOf(value));
            });
        }
        ValidatedDestination destination = ssrfGuard.validate(uri, policy);
        Request.Builder request = requestBuilder(destination, headers).post(form.build());
        return call(destination, request.build(), policy);
    }

    public ValidatedDestination validate(URI uri, OutboundRequestPolicy policy) {
        return ssrfGuard.validate(uri, policy);
    }

    private SafeHttpResponse execute(String method, URI uri, Map<String, String> headers, byte[] body,
                                     String contentType, OutboundRequestPolicy policy) {
        ValidatedDestination destination = ssrfGuard.validate(uri, policy);
        Request.Builder builder = requestBuilder(destination, headers);
        if ("GET".equals(method)) {
            builder.get();
        } else {
            builder.method(method, RequestBody.create(body == null ? new byte[0] : body,
                    MediaType.parse(contentType == null ? "application/octet-stream" : contentType)));
        }
        return call(destination, builder.build(), policy);
    }

    private Request.Builder requestBuilder(ValidatedDestination destination, Map<String, String> headers) {
        Request.Builder builder = new Request.Builder().url(destination.uri().toString());
        if (headers != null) {
            headers.forEach((name, value) -> {
                if (name == null || value == null || value.indexOf('\r') >= 0 || value.indexOf('\n') >= 0) return;
                if (!FORBIDDEN_REQUEST_HEADERS.contains(name.toLowerCase(Locale.ROOT))) builder.header(name, value);
            });
        }
        return builder;
    }

    private SafeHttpResponse call(ValidatedDestination destination, Request request,
                                  OutboundRequestPolicy policy) {
        OkHttpClient client = client(destination, policy);
        Call call = client.newCall(request);
        try (Response response = call.execute()) {
            if (response.code() >= 300 && response.code() < 400) {
                log.warn("Blocked outbound redirect: integration={}, status={}", policy.name(), response.code());
                throw new SafeHttpException(SafeHttpException.Reason.REDIRECT_BLOCKED);
            }
            String body = readBounded(response.body(), policy.maxResponseBytes());
            return new SafeHttpResponse(response.code(), body, response.headers().toMultimap());
        } catch (SafeHttpException ex) {
            throw ex;
        } catch (InterruptedIOException ex) {
            log.warn("Outbound request timed out: integration={}", policy.name());
            throw new SafeHttpException(SafeHttpException.Reason.TIMEOUT, ex);
        } catch (javax.net.ssl.SSLException ex) {
            throw new SafeHttpException(SafeHttpException.Reason.TLS_FAILURE, ex);
        } catch (IOException ex) {
            log.warn("Outbound request failed: integration={}, reason={}", policy.name(), ex.getClass().getSimpleName());
            throw new SafeHttpException(SafeHttpException.Reason.NETWORK_FAILURE, ex);
        }
    }

    private OkHttpClient client(ValidatedDestination destination, OutboundRequestPolicy policy) {
        Dns pinnedDns = hostname -> {
            String normalized;
            try {
                normalized = ssrfGuard.normalizeHost(hostname);
            } catch (SafeHttpException ex) {
                throw new UnknownHostException("blocked host");
            }
            if (!destination.asciiHost().equals(normalized)) throw new UnknownHostException("host not pinned");
            return destination.pinnedAddresses();
        };
        return new OkHttpClient.Builder()
                .proxy(Proxy.NO_PROXY)
                .socketFactory(new DirectSocketFactory())
                .connectionPool(new ConnectionPool(0, 1, TimeUnit.SECONDS))
                .dns(pinnedDns)
                .followRedirects(false)
                .followSslRedirects(false)
                .retryOnConnectionFailure(false)
                .connectTimeout(millis(policy.connectTimeout()), TimeUnit.MILLISECONDS)
                .readTimeout(millis(policy.readTimeout()), TimeUnit.MILLISECONDS)
                .writeTimeout(millis(policy.readTimeout()), TimeUnit.MILLISECONDS)
                .callTimeout(millis(policy.callTimeout()), TimeUnit.MILLISECONDS)
                .build();
    }

    /** Disable JVM SOCKS selection too; Proxy.NO_PROXY on OkHttp alone is insufficient. */
    private static final class DirectSocketFactory extends SocketFactory {
        @Override public Socket createSocket() { return new Socket(Proxy.NO_PROXY); }
        // OkHttp must connect the unconnected socket to its validated, pinned InetSocketAddress.
        @Override public Socket createSocket(String host, int port) throws IOException { throw unsupported(); }
        @Override public Socket createSocket(String host, int port, InetAddress local, int localPort) throws IOException { throw unsupported(); }
        @Override public Socket createSocket(InetAddress host, int port) throws IOException { throw unsupported(); }
        @Override public Socket createSocket(InetAddress host, int port, InetAddress local, int localPort) throws IOException { throw unsupported(); }
        private IOException unsupported() { return new IOException("Only pinned unconnected sockets are supported"); }
    }

    private String readBounded(ResponseBody body, int maximumBytes) throws IOException {
        if (body == null) return "";
        long declared = body.contentLength();
        if (declared > maximumBytes) throw new SafeHttpException(SafeHttpException.Reason.RESPONSE_TOO_LARGE);
        try (BufferedSource source = body.source(); ByteArrayOutputStream out = new ByteArrayOutputStream(Math.min(maximumBytes, 8192))) {
            byte[] buffer = new byte[8192];
            long total = 0;
            while (true) {
                int read = source.inputStream().read(buffer);
                if (read < 0) break;
                total += read;
                if (total > maximumBytes) throw new SafeHttpException(SafeHttpException.Reason.RESPONSE_TOO_LARGE);
                out.write(buffer, 0, read);
            }
            return out.toString(StandardCharsets.UTF_8);
        }
    }

    private long millis(Duration duration) {
        return Math.max(1, duration.toMillis());
    }
}
