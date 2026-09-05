package com.course.platform.infra.http;

import com.sun.net.httpserver.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import javax.net.ssl.*;
import java.net.*;
import java.nio.file.*;
import java.security.KeyStore;
import java.util.*;
import java.util.concurrent.TimeUnit;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class SafeHttpTlsTest {
    @TempDir Path temporary;

    @Test void defaultTlsRejectsUntrustedCertificate() throws Exception {
        Path store = temporary.resolve("fixture.p12");
        Path keytool = Path.of(System.getProperty("java.home"),"bin","keytool");
        // Ephemeral, test-only certificate. Never modifies the JVM trust store.
        Process generate = new ProcessBuilder(keytool.toString(),"-genkeypair","-alias","fixture",
                "-keyalg","RSA","-keysize","2048","-storetype","PKCS12","-keystore",store.toString(),
                "-storepass","test-fixture","-keypass","test-fixture","-dname","CN=pinned.invalid",
                "-ext","SAN=dns:pinned.invalid","-validity","1","-noprompt")
                .redirectErrorStream(true).redirectOutput(temporary.resolve("keytool.log").toFile()).start();
        if (!generate.waitFor(20,TimeUnit.SECONDS)) { generate.destroyForcibly(); fail("Fixture certificate generation timed out"); }
        assertEquals(0,generate.exitValue());
        KeyStore keys = KeyStore.getInstance("PKCS12");
        try(var input=Files.newInputStream(store)) { keys.load(input,"test-fixture".toCharArray()); }
        KeyManagerFactory manager=KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        manager.init(keys,"test-fixture".toCharArray());
        SSLContext tls=SSLContext.getInstance("TLS"); tls.init(manager.getKeyManagers(),null,null);
        HttpsServer server=HttpsServer.create(new InetSocketAddress("127.0.0.1",0),0);
        server.setHttpsConfigurator(new HttpsConfigurator(tls));
        server.start();
        try {
            URI uri=URI.create("https://pinned.invalid:"+server.getAddress().getPort()+"/secret-token");
            SsrfGuard guard=mock(SsrfGuard.class);
            when(guard.validate(any(),any())).thenReturn(new ValidatedDestination(uri,"pinned.invalid",List.of(InetAddress.getByName("127.0.0.1"))));
            when(guard.normalizeHost(anyString())).thenAnswer(call->call.getArgument(0));
            var policy=new OutboundRequestPolicy("tls-test",Set.of("pinned.invalid"),Set.of(),Set.of(server.getAddress().getPort()),1024,
                    java.time.Duration.ofSeconds(3),java.time.Duration.ofSeconds(3),java.time.Duration.ofSeconds(5));
            var ex=assertThrows(SafeHttpException.class,()->new SafeHttpClient(guard).get(uri,Map.of(),policy));
            assertEquals(SafeHttpException.Reason.NETWORK_FAILURE,ex.getReason());
            assertNull(ex.getCause()); assertFalse(ex.toString().contains("secret-token"));
        } finally {server.stop(0);}
    }
}
