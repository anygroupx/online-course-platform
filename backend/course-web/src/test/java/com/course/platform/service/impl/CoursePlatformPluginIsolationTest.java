package com.course.platform.service.impl;

import com.course.platform.application.service.platform.docking.PlatformDockingStrategy;
import com.course.platform.common.exception.BusinessException;
import com.course.platform.domain.entity.ApiProvider;
import com.course.platform.domain.entity.CoursePlatform;
import com.course.platform.infra.docking.PlatformDockingStrategyFactory;
import com.course.platform.infra.persistence.mapper.ApiProviderMapper;
import com.course.platform.infra.persistence.mapper.CoursePlatformMapper;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class CoursePlatformPluginIsolationTest {
    @Test void neitherCourseQueryNorDockConfigurationCanBindAReadOnlyPlugin() {
        var mapper = mock(CoursePlatformMapper.class);
        var providers = mock(ApiProviderMapper.class);
        var service = new CoursePlatformServiceImpl(mapper, providers, new PlatformDockingStrategyFactory(List.of()));
        for (String type : List.of("flash", "heisha", "jiguang", "wuxin", "sxdk_tw")) {
            ApiProvider provider = new ApiProvider(); provider.setId(9L); provider.setProviderType(type);
            when(providers.selectById(9L)).thenReturn(provider);
            CoursePlatform platform = new CoursePlatform(); platform.setQueryApiId(9L);
            assertThrows(BusinessException.class, () -> service.createPlatform(platform));
            platform.setQueryApiId(null); platform.setDockApiId(9L);
            assertThrows(BusinessException.class, () -> service.createPlatform(platform));
            CoursePlatform existing = new CoursePlatform(); existing.setId(5L);
            when(mapper.selectById(5L)).thenReturn(existing); platform.setId(5L);
            assertThrows(BusinessException.class, () -> service.updatePlatform(platform));
        }
        verify(mapper, never()).insert(any(CoursePlatform.class));
        verify(mapper, never()).updateById(any(CoursePlatform.class));
    }

    @Test void ordinaryCourseProviderCanStillBeConfiguredBeforeActivation() {
        var mapper = mock(CoursePlatformMapper.class);
        var providers = mock(ApiProviderMapper.class);
        var course = mock(PlatformDockingStrategy.class); when(course.getProviderType()).thenReturn("27");
        var service = new CoursePlatformServiceImpl(mapper, providers, new PlatformDockingStrategyFactory(List.of(course)));
        ApiProvider provider = new ApiProvider(); provider.setId(9L); provider.setProviderType("27"); provider.setStatus(2);
        when(providers.selectById(9L)).thenReturn(provider);
        CoursePlatform platform = new CoursePlatform(); platform.setName("课程"); platform.setDockApiId(9L);
        service.createPlatform(platform);
        verify(mapper).insert(platform);
        verify(course, never()).testConnection(any());
    }
}
