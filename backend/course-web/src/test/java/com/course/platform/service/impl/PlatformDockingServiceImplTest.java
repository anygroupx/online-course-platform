package com.course.platform.service.impl;

import com.course.platform.application.service.platform.ApiProviderService;
import com.course.platform.application.service.platform.docking.PlatformDockingStrategy;
import com.course.platform.domain.dto.DockResult;
import com.course.platform.domain.entity.ApiProvider;
import com.course.platform.domain.entity.CourseOrder;
import com.course.platform.domain.entity.CoursePlatform;
import com.course.platform.infra.docking.PlatformDockingStrategyFactory;
import com.course.platform.infra.persistence.mapper.ApiProviderMapper;
import com.course.platform.infra.persistence.mapper.CourseOrderMapper;
import com.course.platform.infra.persistence.mapper.CoursePlatformMapper;
import com.course.platform.infra.persistence.mapper.PlatformCategoryMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PlatformDockingServiceImplTest {

    private PlatformDockingStrategyFactory strategyFactory;
    private ApiProviderMapper apiProviderMapper;
    private ApiProviderService apiProviderService;
    private PlatformDockingStrategy strategy;
    private PlatformDockingServiceImpl service;

    @BeforeEach
    void setUp() {
        strategyFactory = mock(PlatformDockingStrategyFactory.class);
        apiProviderMapper = mock(ApiProviderMapper.class);
        apiProviderService = mock(ApiProviderService.class);
        strategy = mock(PlatformDockingStrategy.class);
        service = new PlatformDockingServiceImpl(
                strategyFactory,
                apiProviderMapper,
                mock(CoursePlatformMapper.class),
                mock(CourseOrderMapper.class),
                mock(PlatformCategoryMapper.class),
                apiProviderService
        );
    }

    @Test
    @DisplayName("对接入口应使用按 ID 重新加载的解密配置")
    void dockOrder_shouldUseDecryptedProvider() {
        ApiProvider encryptedReference = new ApiProvider();
        encryptedReference.setId(9L);
        encryptedReference.setProviderType("Daytime");
        encryptedReference.setApiKey("ENC:v1:ciphertext");

        ApiProvider decrypted = new ApiProvider();
        decrypted.setId(9L);
        decrypted.setProviderType("Daytime");
        decrypted.setApiKey("plain-key");

        CourseOrder order = new CourseOrder();
        CoursePlatform platform = new CoursePlatform();
        when(apiProviderService.loadDecrypted(9L)).thenReturn(decrypted);
        when(strategyFactory.getStrategy("Daytime")).thenReturn(strategy);
        when(strategy.dockOrder(order, platform, decrypted))
                .thenReturn(DockResult.success("ok", "remote-id"));

        DockResult result = service.dockOrder(order, platform, encryptedReference);

        assertEquals("remote-id", result.getThirdOrderId());
        verify(apiProviderService).loadDecrypted(9L);
    }

    @Test
    @DisplayName("批量同步结束时只能回写同步时间，不能回写已解密凭据")
    void batchSync_shouldNotWritePlainSecretsBack() {
        ApiProvider decrypted = new ApiProvider();
        decrypted.setId(9L);
        decrypted.setProviderType("27");
        decrypted.setApiKey("plain-key");
        decrypted.setPassword("plain-password");
        decrypted.setLastSyncTime(1000L);

        when(apiProviderService.loadDecrypted(9L)).thenReturn(decrypted);
        when(strategyFactory.getStrategy("27")).thenReturn(strategy);
        when(strategy.batchQueryOrderProgress(decrypted, 400L, 0)).thenReturn(Collections.emptyList());

        service.batchSyncOrderProgress(9L, 1000L, 0);

        ArgumentCaptor<ApiProvider> captor = ArgumentCaptor.forClass(ApiProvider.class);
        verify(apiProviderMapper).updateById(captor.capture());
        ApiProvider update = captor.getValue();
        assertEquals(9L, update.getId());
        assertNull(update.getApiKey());
        assertNull(update.getPassword());
    }
}
