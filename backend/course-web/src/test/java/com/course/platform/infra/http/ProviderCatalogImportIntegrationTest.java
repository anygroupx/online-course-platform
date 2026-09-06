package com.course.platform.infra.http;

import com.course.platform.application.service.platform.ApiProviderService;
import com.course.platform.application.service.security.SecurityAuditService;
import com.course.platform.controller.PlatformDockingController;
import com.course.platform.domain.entity.ApiProvider;
import com.course.platform.domain.entity.CoursePlatform;
import com.course.platform.infra.docking.PlatformDockingStrategyFactory;
import com.course.platform.infra.docking.impl.DaytimeDockingStrategy;
import com.course.platform.infra.external.ApiHttpClient;
import com.course.platform.infra.persistence.mapper.*;
import com.course.platform.service.impl.PlatformDockingServiceImpl;
import com.course.platform.shared.exception.GlobalExceptionHandler;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Real HTTP -> bounded transport -> Daytime parsing -> import/controller; no production writes. */
class ProviderCatalogImportIntegrationTest {
    private static final String LARGE_DESCRIPTION = "x".repeat(5 * 1024 * 1024);
    private final CoursePlatformMapper platforms = mock(CoursePlatformMapper.class);
    private final ApiProviderService providers = mock(ApiProviderService.class);
    private final List<Map<String, String>> upstreamRequests = new CopyOnWriteArrayList<>();
    private final AtomicReference<CoursePlatform> stored = new AtomicReference<>();
    private final AtomicReference<String> price = new AtomicReference<>("5.00");
    private final OutboundSecurityProperties limits = new OutboundSecurityProperties();
    private boolean ignoreRemoteCategory;
    private HttpServer upstream;
    private MockMvc mvc;

    @BeforeEach
    void setUp() throws Exception {
        upstream = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        String baseUrl = "http://catalog.invalid:" + upstream.getAddress().getPort();
        upstream.createContext("/api.php", exchange -> {
            assertEquals("act=getclass", exchange.getRequestURI().getRawQuery());
            String form = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            Map<String, String> params = new HashMap<>();
            for (String pair : form.split("&")) {
                String[] part = pair.split("=", 2);
                params.put(URLDecoder.decode(part[0], StandardCharsets.UTF_8),
                        URLDecoder.decode(part.length > 1 ? part[1] : "", StandardCharsets.UTF_8));
            }
            upstreamRequests.add(params);
            String selected = "{\"cid\":\"selected\",\"name\":\"分类商品\",\"price\":\"" + price.get()
                    + "\",\"fenlei\":\"60\",\"content\":\"fresh detail\"}";
            String other = "{\"cid\":\"other\",\"name\":\"其他分类\",\"price\":\"8.00\","
                    + "\"fenlei\":\"61\",\"content\":\"" + LARGE_DESCRIPTION + "\"}";
            String data = params.containsKey("fenlei") && !ignoreRemoteCategory ? selected : selected + "," + other;
            byte[] response = ("{\"code\":1,\"data\":[" + data + "]}").getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.length);
            try (var out = exchange.getResponseBody()) {
                out.write(response);
            } catch (IOException ignored) {
                // Expected when the old 1 MiB policy rejects the full catalog and closes the socket.
            }
        });
        upstream.start();

        limits.setProviderHttpAllowedHosts(List.of("catalog.invalid"));
        limits.setProviderAllowedPorts(List.of(upstream.getAddress().getPort()));
        SsrfGuard guard = mock(SsrfGuard.class);
        // Loopback is permitted ONLY by this transport fixture; production SsrfGuard remains strict.
        when(guard.validate(any(), any())).thenAnswer(call -> new ValidatedDestination(
                call.getArgument(0, URI.class), "catalog.invalid", List.of(InetAddress.getByName("127.0.0.1"))));
        when(guard.normalizeHost(anyString())).thenAnswer(call -> call.getArgument(0));
        var http = new ApiHttpClient(new SafeHttpClient(guard),
                new ProviderOutboundPolicyFactory(limits, new ProviderUrlNormalizer()));
        var strategy = new DaytimeDockingStrategy(http);
        ApiProvider provider = new ApiProvider();
        provider.setId(6L);
        provider.setProviderType("Daytime");
        provider.setApiUrl(baseUrl);
        provider.setUsername("test-uid");
        provider.setApiKey("test-key");
        provider.setStatus(ApiProvider.STATUS_ACTIVE);
        when(providers.loadDecrypted(6L)).thenReturn(provider);
        when(platforms.selectList(any())).thenReturn(List.of());
        when(platforms.selectOne(any())).thenAnswer(call -> stored.get());
        when(platforms.insert(any(CoursePlatform.class))).thenAnswer(call -> {
            CoursePlatform item = call.getArgument(0);
            item.setId(100L);
            stored.set(item);
            return 1;
        });
        var service = new PlatformDockingServiceImpl(new PlatformDockingStrategyFactory(List.of(strategy)),
                mock(ApiProviderMapper.class), platforms, mock(CourseOrderMapper.class),
                mock(PlatformCategoryMapper.class), providers);
        mvc = MockMvcBuilders.standaloneSetup(new PlatformDockingController(service))
                .setControllerAdvice(new GlobalExceptionHandler(mock(SecurityAuditService.class))).build();
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(7L, null,
                List.of(new SimpleGrantedAuthority("api-provider:update"))));
    }

    @AfterEach
    void cleanUp() {
        upstream.stop(0);
        SecurityContextHolder.clearContext();
    }

    private String body(String category, String ids) {
        return "{\"apiProviderId\":6,\"productIds\":[" + ids + "],\"priceMultiplier\":2,"
                + "\"syncCategories\":false" + (category == null ? "" : ",\"categoryId\":\"" + category + "\"") + "}";
    }

    @Test
    void reproducesOld193Byte502WhenCategoryQueryIsFollowedByUnfilteredImport() throws Exception {
        limits.setProviderMaxResponseBytes(1_048_576);
        mvc.perform(get("/admin/docking/products").param("apiProviderId", "6").param("categoryId", "60"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.length()").value(1));
        var response = mvc.perform(post("/admin/docking/import-products").contentType(MediaType.APPLICATION_JSON)
                        .content(body(null, "\"selected\"")))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.data.reason").value("RESPONSE_TOO_LARGE"))
                .andReturn().getResponse();
        assertEquals(193, response.getContentAsByteArray().length);
        assertEquals("60", upstreamRequests.get(0).get("fenlei"));
        assertFalse(upstreamRequests.get(1).containsKey("fenlei"));
        verify(platforms, never()).insert(any(CoursePlatform.class));
        verify(platforms, never()).updateById(any(CoursePlatform.class));
    }

    @Test
    void categoryImportAvoidsFullCatalogAndUsesFreshAuthoritativePrice() throws Exception {
        limits.setProviderMaxResponseBytes(1_048_576);
        mvc.perform(get("/admin/docking/products").param("apiProviderId", "6").param("categoryId", "60"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data[0].price").value(5.00));
        price.set("6.00"); // An updated upstream price must replace the displayed snapshot.
        mvc.perform(post("/admin/docking/import-products").contentType(MediaType.APPLICATION_JSON)
                        .content(body(" 60 ", "\"selected\"")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.created").value(1))
                .andExpect(jsonPath("$.data.fail").value(0));
        assertEquals(List.of("60", "60"), upstreamRequests.stream().map(p -> p.get("fenlei")).toList());
        assertEquals(0, new BigDecimal("12.00").compareTo(stored.get().getBasePrice()));
        assertEquals("selected", stored.get().getDockParam());
        assertEquals("fresh detail", stored.get().getDescription());
    }

    @Test
    void unfilteredLegacyClientsCanImportFromTheLargeCatalogWithCurrentLimit() throws Exception {
        mvc.perform(post("/admin/docking/import-products").contentType(MediaType.APPLICATION_JSON)
                        .content(body(null, "\"selected\"")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.created").value(1))
                .andExpect(jsonPath("$.data.success").value(1));
        assertFalse(upstreamRequests.get(0).containsKey("fenlei"));
        assertEquals("selected", stored.get().getDockParam());
        verify(platforms, times(1)).insert(any(CoursePlatform.class));
    }

    @Test
    void ignoredUpstreamFilterCannotImportSelectedIdsFromAnotherCategory() throws Exception {
        ignoreRemoteCategory = true;
        mvc.perform(post("/admin/docking/import-products").contentType(MediaType.APPLICATION_JSON)
                        .content(body("60", "\"selected\",\"other\"")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.missing").value(1));
        ArgumentCaptor<CoursePlatform> inserted = ArgumentCaptor.forClass(CoursePlatform.class);
        verify(platforms).insert(inserted.capture());
        assertEquals("selected", inserted.getValue().getDockParam());
    }

    @Test
    void mismatchedCategoryFailsWithoutFallingBackToUnfilteredImport() throws Exception {
        ignoreRemoteCategory = true;
        mvc.perform(post("/admin/docking/import-products").contentType(MediaType.APPLICATION_JSON)
                        .content(body("unknown", "\"selected\"")))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.success").value(false));
        assertEquals(1, upstreamRequests.size());
        verify(platforms, never()).insert(any(CoursePlatform.class));
    }

    @Test
    void categoryLengthIsValidatedBeforeAnyUpstreamRequest() throws Exception {
        mvc.perform(post("/admin/docking/import-products").contentType(MediaType.APPLICATION_JSON)
                        .content(body("x".repeat(51), "\"selected\"")))
                .andExpect(status().isUnprocessableEntity());
        assertTrue(upstreamRequests.isEmpty());
        verifyNoInteractions(providers);
    }

    @Test
    void repeatingAnImportUpdatesTheExistingProductWithoutAnotherInsert() throws Exception {
        for (int attempt = 0; attempt < 2; attempt++) {
            mvc.perform(post("/admin/docking/import-products").contentType(MediaType.APPLICATION_JSON)
                            .content(body("60", "\"selected\"")))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.data.created").value(attempt == 0 ? 1 : 0))
                    .andExpect(jsonPath("$.data.updated").value(attempt == 0 ? 0 : 1));
        }
        verify(platforms, times(1)).insert(any(CoursePlatform.class));
        verify(platforms, times(1)).updateById(any(CoursePlatform.class));
    }
}
