package com.course.platform.infra.integration;

import com.course.platform.application.service.integration.PluginReadOnlyConnector;
import com.course.platform.application.service.platform.docking.PlatformDockingStrategy;
import com.course.platform.application.service.platform.docking.ProviderConnectionProbe;
import com.course.platform.infra.docking.PlatformDockingStrategyFactory;
import com.course.platform.infra.external.ApiHttpClient;
import com.course.platform.infra.http.ProviderUrlNormalizer;
import com.course.platform.infra.servicecommerce.*;
import com.course.platform.domain.entity.ApiProvider;
import com.course.platform.domain.servicecommerce.ServiceProduct;
import com.course.platform.domain.servicecommerce.ServiceCommerceTypes.Lookup;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PluginRegistriesTest {
    @Test void springDiscoversTenServiceProbesAndOnePrimaryNativeRouterButNoCourseStrategies() {
        try (var context = new AnnotationConfigApplicationContext()) {
            context.registerBean(ApiHttpClient.class, () -> mock(ApiHttpClient.class));
            context.registerBean(ProviderUrlNormalizer.class);
            context.register(PluginConnectorConfiguration.class, PluginConnectorRegistry.class,
                    ProviderConnectionProbeRegistry.class, PluginResearchCatalog.class, PlatformDockingStrategyFactory.class,
                    com.course.platform.infra.servicecommerce.WuxinNativeServiceGateway.class,
                    com.course.platform.infra.servicecommerce.SsbenzDistanceGateway.class,
                    com.course.platform.infra.servicecommerce.PhpNativeServiceGateway.class,
                    com.course.platform.infra.servicecommerce.NativeServiceGatewayRouter.class,
                    com.course.platform.infra.servicecommerce.InternshipNativeServiceGateway.class,
                    AppuiNativeServiceGateway.class,
                    LeidianNativeServiceGateway.class,
                    JingyuNativeServiceGateway.class,
                    com.course.platform.infra.projectcenter.Syyv5ProjectGateway.class,
                    com.course.platform.infra.projectcenter.ProjectTicketImagePolicy.class,
                    com.course.platform.infra.projectclient.ProjectTicketImageCodec.class);
            context.refresh();
            assertEquals(8, context.getBeansOfType(PluginReadOnlyConnector.class).size());
            assertEquals(10, context.getBeansOfType(ProviderConnectionProbe.class).size());
            assertEquals(1, context.getBeansOfType(com.course.platform.application.service.servicecommerce.ServiceAccountGateway.class).size());
            assertTrue(context.getBeansOfType(PlatformDockingStrategy.class).isEmpty());
            assertInstanceOf(com.course.platform.infra.servicecommerce.NativeServiceGatewayRouter.class,
                    context.getBean(com.course.platform.application.service.servicecommerce.NativeServiceGateway.class));
            for (String type : List.of("flash", "heisha", "jiguang", "wuxin", "sxdk_tw", "syyv5", "ssbenz_xbd", "appui", "leidian", "jingyu")) {
                assertNotNull(context.getBean(ProviderConnectionProbeRegistry.class).getProbe(type));
                assertNull(context.getBean(PlatformDockingStrategyFactory.class).getStrategy(type));
            }
            var catalog = context.getBean(PluginResearchCatalog.class).list();
            assertEquals(12, catalog.size());
            assertEquals(12, catalog.stream().map(c -> c.id()).distinct().count());
            assertEquals(0, catalog.stream().filter(c -> "READ_ONLY".equals(c.integrationStatus())).count());
            assertEquals(10, catalog.stream().filter(c -> "NATIVE_PARTIAL".equals(c.integrationStatus())).count());
            var appui = catalog.stream().filter(c -> "P09".equals(c.id())).findFirst().orElseThrow();
            assertEquals("appui", appui.providerType());
            assertEquals("NATIVE_PARTIAL", appui.integrationStatus());
            assertEquals(List.of("CATALOG"), appui.availableCapabilities());
            assertSame(context.getBean(AppuiNativeServiceGateway.class),
                    context.getBean(PluginConnectorRegistry.class).getConnector("appui"));
            assertEquals(List.of("1", "2", "3", "4", "5", "6", "7", "8", "9"),
                    context.getBean(AppuiNativeServiceGateway.class).projects().stream().map(p -> p.id()).toList());
            assertEquals(List.of("LOOKUP", "CREATE", "SYNC", "REFUND", "ADD_TIMES", "EDIT_PLAN"),
                    PhpNativeServiceGateway.capabilities("appui"));
            var distance = catalog.stream().filter(c -> "P05".equals(c.id())).findFirst().orElseThrow();
            assertEquals("ssbenz_xbd", distance.providerType());
            assertEquals(List.of("CATALOG"), distance.availableCapabilities());
            assertEquals(List.of("CREATE", "SYNC"), com.course.platform.infra.servicecommerce.PhpNativeServiceGateway.capabilities("ssbenz_xbd"));
            var flash = catalog.stream().filter(c -> "P01".equals(c.id())).findFirst().orElseThrow();
            assertEquals("NATIVE_PARTIAL", flash.integrationStatus());
            assertTrue(flash.availableCapabilities().contains("CATALOG"));
            assertEquals("P08", catalog.stream().filter(c -> "P11".equals(c.id())).findFirst().orElseThrow().duplicateOf());
            var jingyu = catalog.stream().filter(c -> "P08".equals(c.id())).findFirst().orElseThrow();
            assertEquals("jingyu", jingyu.providerType()); assertEquals("NATIVE_PARTIAL", jingyu.integrationStatus());
            assertEquals("MIXED", jingyu.evidenceLevel()); assertEquals(List.of("CATALOG"), jingyu.availableCapabilities());
            assertSame(context.getBean(JingyuNativeServiceGateway.class), context.getBean(PluginConnectorRegistry.class).getConnector("jingyu"));
            assertEquals(List.of("keep", "bdlp"), context.getBean(JingyuNativeServiceGateway.class).projects().stream().map(p -> p.id()).toList());
            var leidian = catalog.stream().filter(c -> "P12".equals(c.id())).findFirst().orElseThrow();
            assertEquals("leidian", leidian.providerType());
            assertEquals("NATIVE_PARTIAL", leidian.integrationStatus());
            assertEquals("MIXED", leidian.evidenceLevel());
            assertEquals(List.of("CATALOG"), leidian.availableCapabilities());
            assertSame(context.getBean(LeidianNativeServiceGateway.class),
                    context.getBean(PluginConnectorRegistry.class).getConnector("leidian"));
            assertEquals(List.of("1", "2", "3", "4"),
                    context.getBean(LeidianNativeServiceGateway.class).projects().stream().map(p -> p.id()).toList());
            verifyNoInteractions(context.getBean(ApiHttpClient.class));
        }
    }

    @Test void appuiRequestsUseTheDedicatedNativeGateway() {
        var templates = mock(PhpNativeServiceGateway.class);
        var wuxin = mock(WuxinNativeServiceGateway.class);
        var internship = mock(InternshipNativeServiceGateway.class);
        var distance = mock(SsbenzDistanceGateway.class);
        var appui = mock(AppuiNativeServiceGateway.class);
        var router = new NativeServiceGatewayRouter(templates, wuxin, internship, distance, appui, mock(LeidianNativeServiceGateway.class), mock(JingyuNativeServiceGateway.class));
        var provider = new ApiProvider();
        provider.setProviderType("appui");
        var product = new ServiceProduct();
        var fields = Map.of("account", "fixture-student");
        var expected = new Lookup(Map.of("studentName", "示例姓名"), List.of(), "查询成功");
        when(appui.lookup(provider, product, fields)).thenReturn(expected);

        assertSame(expected, router.lookup(provider, product, fields));
        verify(appui).lookup(provider, product, fields);
        verifyNoMoreInteractions(appui);
        verifyNoInteractions(templates, wuxin, internship, distance);
    }

    @Test void courseProbeStillWorksAndDuplicateRegistrationsFailClosed() {
        PlatformDockingStrategy course = mock(PlatformDockingStrategy.class);
        when(course.getProviderType()).thenReturn("27");
        PluginReadOnlyConnector plugin = mock(PluginReadOnlyConnector.class);
        when(plugin.getProviderType()).thenReturn("jiguang");
        var probes = new ProviderConnectionProbeRegistry(List.of(course, plugin));
        assertSame(course, probes.getProbe("27"));
        assertSame(plugin, probes.getProbe("jiguang"));
        assertNull(probes.getProbe("unknown"));
        assertThrows(IllegalStateException.class, () -> new ProviderConnectionProbeRegistry(List.of(plugin, plugin)));
        assertThrows(IllegalStateException.class, () -> new PluginConnectorRegistry(List.of(plugin, plugin)));
        assertNull(new PluginConnectorRegistry(List.of(plugin)).getConnector(null));
    }

    @Test void missingConnectorsCannotBeAdvertisedAsImplemented() {
        var catalog = new PluginResearchCatalog(new PluginConnectorRegistry(List.of()));
        assertTrue(catalog.list().stream().noneMatch(c -> "READ_ONLY".equals(c.integrationStatus())));
        assertTrue(catalog.find("P04").availableCapabilities().isEmpty());
        assertNull(catalog.find("../../source"));
    }
    @Test void leidianOrderAndScoreRequestsUseOnlyTheirDedicatedGateway() {
        var templates = mock(PhpNativeServiceGateway.class);
        var wuxin = mock(WuxinNativeServiceGateway.class);
        var internship = mock(InternshipNativeServiceGateway.class);
        var distance = mock(SsbenzDistanceGateway.class);
        var appui = mock(AppuiNativeServiceGateway.class);
        var leidian = mock(LeidianNativeServiceGateway.class);
        var router = new NativeServiceGatewayRouter(templates, wuxin, internship, distance, appui, leidian, mock(JingyuNativeServiceGateway.class));
        var provider = new ApiProvider(); provider.setProviderType("leidian");
        var order = new com.course.platform.domain.servicecommerce.ServiceOrder();
        var product = new ServiceProduct();
        var score = new com.course.platform.domain.servicecommerce.ServiceCommerceTypes.OrderText("成绩信息");
        when(leidian.scoreInfo(provider, order)).thenReturn(score);
        assertSame(score, router.scoreInfo(provider, order));
        router.lookup(provider, product, Map.of("account", "fixture-student"));
        router.orderOptions(provider, order); router.logs(provider, order, 1); router.sync(provider, order);
        router.execute(provider, product, order, "CANCEL", Map.of());
        verify(leidian).scoreInfo(provider, order);
        verify(leidian).lookup(provider, product, Map.of("account", "fixture-student"));
        verify(leidian).orderOptions(provider, order); verify(leidian).logs(provider, order, 1); verify(leidian).sync(provider, order);
        verify(leidian).execute(provider, product, order, "CANCEL", Map.of());
        verifyNoMoreInteractions(leidian); verifyNoInteractions(templates, wuxin, internship, distance, appui);
    }


    @Test void jingyuCommerceUsesOnlyItsDedicatedGateway() {
        var templates = mock(PhpNativeServiceGateway.class); var wuxin = mock(WuxinNativeServiceGateway.class);
        var internship = mock(InternshipNativeServiceGateway.class); var distance = mock(SsbenzDistanceGateway.class);
        var appui = mock(AppuiNativeServiceGateway.class); var leidian = mock(LeidianNativeServiceGateway.class);
        var jingyu = mock(JingyuNativeServiceGateway.class);
        var router = new NativeServiceGatewayRouter(templates, wuxin, internship, distance, appui, leidian, jingyu);
        var provider = new ApiProvider(); provider.setProviderType("jingyu");
        var product = new com.course.platform.domain.servicecommerce.ServiceProduct();
        var order = new com.course.platform.domain.servicecommerce.ServiceOrder();
        var fields = Map.of("account", "150123");
        router.lookup(provider, product, fields); router.prepare(provider, product, null);
        router.prepareAction(provider, order, "PAUSE", Map.of()); router.refundRemaining(provider, order);
        router.execute(provider, product, order, "CREATE", Map.of()); router.logs(provider, order, 1); router.sync(provider, order);
        verify(jingyu).lookup(provider, product, fields); verify(jingyu).prepare(provider, product, null);
        verify(jingyu).prepareAction(provider, order, "PAUSE", Map.of()); verify(jingyu).refundRemaining(provider, order);
        verify(jingyu).execute(provider, product, order, "CREATE", Map.of()); verify(jingyu).logs(provider, order, 1); verify(jingyu).sync(provider, order);
        verifyNoMoreInteractions(jingyu); verifyNoInteractions(templates, wuxin, internship, distance, appui, leidian);
    }
}
