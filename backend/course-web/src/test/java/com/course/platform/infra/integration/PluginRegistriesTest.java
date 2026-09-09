package com.course.platform.infra.integration;

import com.course.platform.application.service.integration.PluginReadOnlyConnector;
import com.course.platform.application.service.platform.docking.PlatformDockingStrategy;
import com.course.platform.application.service.platform.docking.ProviderConnectionProbe;
import com.course.platform.infra.docking.PlatformDockingStrategyFactory;
import com.course.platform.infra.external.ApiHttpClient;
import com.course.platform.infra.http.ProviderUrlNormalizer;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PluginRegistriesTest {
    @Test void springDiscoversFiveServiceProbesAndOnePrimaryNativeRouterButNoCourseStrategies() {
        try (var context = new AnnotationConfigApplicationContext()) {
            context.registerBean(ApiHttpClient.class, () -> mock(ApiHttpClient.class));
            context.registerBean(ProviderUrlNormalizer.class);
            context.register(PluginConnectorConfiguration.class, PluginConnectorRegistry.class,
                    ProviderConnectionProbeRegistry.class, PluginResearchCatalog.class, PlatformDockingStrategyFactory.class,
                    com.course.platform.infra.servicecommerce.WuxinNativeServiceGateway.class,
                    com.course.platform.infra.servicecommerce.PhpNativeServiceGateway.class,
                    com.course.platform.infra.servicecommerce.NativeServiceGatewayRouter.class,
                    com.course.platform.infra.servicecommerce.InternshipNativeServiceGateway.class,
                    com.course.platform.infra.projectcenter.Syyv5ProjectGateway.class);
            context.refresh();
            assertEquals(4, context.getBeansOfType(PluginReadOnlyConnector.class).size());
            assertEquals(6, context.getBeansOfType(ProviderConnectionProbe.class).size());
            assertEquals(1, context.getBeansOfType(com.course.platform.application.service.servicecommerce.ServiceAccountGateway.class).size());
            assertTrue(context.getBeansOfType(PlatformDockingStrategy.class).isEmpty());
            assertInstanceOf(com.course.platform.infra.servicecommerce.NativeServiceGatewayRouter.class,
                    context.getBean(com.course.platform.application.service.servicecommerce.NativeServiceGateway.class));
            for (String type : List.of("flash", "heisha", "jiguang", "wuxin", "sxdk_tw", "syyv5")) {
                assertNotNull(context.getBean(ProviderConnectionProbeRegistry.class).getProbe(type));
                assertNull(context.getBean(PlatformDockingStrategyFactory.class).getStrategy(type));
            }
            var catalog = context.getBean(PluginResearchCatalog.class).list();
            assertEquals(12, catalog.size());
            assertEquals(12, catalog.stream().map(c -> c.id()).distinct().count());
            assertEquals(0, catalog.stream().filter(c -> "READ_ONLY".equals(c.integrationStatus())).count());
            assertEquals(6, catalog.stream().filter(c -> "NATIVE_PARTIAL".equals(c.integrationStatus())).count());
            var flash = catalog.stream().filter(c -> "P01".equals(c.id())).findFirst().orElseThrow();
            assertEquals("NATIVE_PARTIAL", flash.integrationStatus());
            assertTrue(flash.availableCapabilities().contains("CATALOG"));
            assertEquals("P08", catalog.stream().filter(c -> "P11".equals(c.id())).findFirst().orElseThrow().duplicateOf());
            assertTrue(catalog.stream().filter(c -> c.id().equals("P12")).findFirst().orElseThrow().availableCapabilities().isEmpty());
            verifyNoInteractions(context.getBean(ApiHttpClient.class));
        }
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
}
