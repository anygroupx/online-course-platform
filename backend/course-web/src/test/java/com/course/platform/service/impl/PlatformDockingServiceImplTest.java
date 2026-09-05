package com.course.platform.service.impl;

import com.course.platform.application.service.platform.ApiProviderService;
import com.course.platform.application.service.platform.docking.PlatformDockingStrategy;
import com.course.platform.domain.dto.DockResult;
import com.course.platform.domain.dto.PlatformItem;
import com.course.platform.domain.dto.ProductImportRequest;
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

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class PlatformDockingServiceImplTest {

    private PlatformDockingStrategyFactory strategyFactory;
    private ApiProviderMapper apiProviderMapper;
    private ApiProviderService apiProviderService;
    private CoursePlatformMapper coursePlatformMapper;
    private CourseOrderMapper courseOrderMapper;
    private PlatformCategoryMapper platformCategoryMapper;
    private PlatformDockingStrategy strategy;
    private PlatformDockingServiceImpl service;

    @BeforeEach
    void setUp() {
        strategyFactory = mock(PlatformDockingStrategyFactory.class);
        apiProviderMapper = mock(ApiProviderMapper.class);
        apiProviderService = mock(ApiProviderService.class);
        strategy = mock(PlatformDockingStrategy.class);
        coursePlatformMapper = mock(CoursePlatformMapper.class);
        courseOrderMapper = mock(CourseOrderMapper.class);
        platformCategoryMapper = mock(PlatformCategoryMapper.class);
        service = new PlatformDockingServiceImpl(
                strategyFactory,
                apiProviderMapper,
                coursePlatformMapper,
                courseOrderMapper,
                platformCategoryMapper,
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

    @Test
    @DisplayName("余额刷新只能回写 ID 和余额，不能回写解密凭据")
    void refreshBalance_shouldNotWritePlainSecretsBack() {
        ApiProvider decrypted = new ApiProvider();
        decrypted.setId(9L);
        decrypted.setStatus(1);
        decrypted.setProviderType("Daytime");
        decrypted.setApiKey("plain-key");
        decrypted.setPassword("plain-password");
        when(apiProviderService.loadDecrypted(9L)).thenReturn(decrypted);
        when(strategyFactory.getStrategy("Daytime")).thenReturn(strategy);
        when(strategy.queryBalance(decrypted)).thenReturn(new BigDecimal("88.66"));

        BigDecimal balance = service.refreshProviderBalance(9L);

        assertEquals(new BigDecimal("88.66"), balance);
        ArgumentCaptor<ApiProvider> captor = ArgumentCaptor.forClass(ApiProvider.class);
        verify(apiProviderMapper).updateById(captor.capture());
        ApiProvider update = captor.getValue();
        assertEquals(9L, update.getId());
        assertEquals(new BigDecimal("88.66"), update.getBalance());
        assertNull(update.getApiKey());
        assertNull(update.getPassword());
    }

    @Test
    @DisplayName("选择导入只能创建被选中的第三方商品")
    void importSelectedProducts_shouldOnlyImportSelectedIds() {
        ApiProvider decrypted = new ApiProvider();
        decrypted.setId(9L);
        decrypted.setStatus(1);
        decrypted.setProviderType("Daytime");
        when(apiProviderService.loadDecrypted(9L)).thenReturn(decrypted);
        when(strategyFactory.getStrategy("Daytime")).thenReturn(strategy);
        when(strategy.fetchPlatformList(decrypted)).thenReturn(List.of(
                PlatformItem.builder().id("a").name("课程A").price(new BigDecimal("5.00")).build(),
                PlatformItem.builder().id("b").name("课程B").price(new BigDecimal("10.00"))
                        .categoryId("remote-category").categoryName("远程分类").build()
        ));

        ProductImportRequest request = new ProductImportRequest();
        request.setApiProviderId(9L);
        request.setProductIds(List.of("b"));
        request.setPriceMultiplier(new BigDecimal("2.00"));
        request.setSyncCategories(false);

        Map<String, Object> result = service.importSelectedProducts(request);

        ArgumentCaptor<CoursePlatform> captor = ArgumentCaptor.forClass(CoursePlatform.class);
        verify(coursePlatformMapper).insert(captor.capture());
        CoursePlatform inserted = captor.getValue();
        assertEquals("b", inserted.getDockParam());
        assertEquals("课程B", inserted.getName());
        assertEquals(new BigDecimal("20.0000"), inserted.getBasePrice());
        assertNull(inserted.getCategoryId());
        verifyNoInteractions(platformCategoryMapper);
        assertEquals(1, result.get("requested"));
        assertEquals(1, result.get("created"));
        assertEquals(0, result.get("missing"));
    }

    @Test
    @DisplayName("商品分类筛选必须在服务端再次执行")
    void fetchProviderProducts_shouldEnforceCategoryFilterLocally() {
        ApiProvider decrypted = new ApiProvider();
        decrypted.setId(9L);
        decrypted.setStatus(1);
        decrypted.setProviderType("Daytime");
        when(apiProviderService.loadDecrypted(9L)).thenReturn(decrypted);
        when(strategyFactory.getStrategy("Daytime")).thenReturn(strategy);
        when(strategy.fetchPlatformList(decrypted, "wanted")).thenReturn(List.of(
                PlatformItem.builder().id("a").name("课程A").categoryId("wanted").build(),
                PlatformItem.builder().id("b").name("课程B").categoryId("unexpected").build()
        ));
        when(coursePlatformMapper.selectList(any())).thenReturn(Collections.emptyList());

        List<PlatformItem> items = service.fetchProviderProducts(9L, "wanted");

        assertEquals(1, items.size());
        assertEquals("a", items.get(0).getId());
    }

    @Test
    @DisplayName("缺失价格的商品不得被静默导入为零价")
    void importSelectedProducts_shouldRejectMissingPrice() {
        ApiProvider decrypted = new ApiProvider();
        decrypted.setId(9L);
        decrypted.setStatus(1);
        decrypted.setProviderType("Daytime");
        when(apiProviderService.loadDecrypted(9L)).thenReturn(decrypted);
        when(strategyFactory.getStrategy("Daytime")).thenReturn(strategy);
        when(strategy.fetchPlatformList(decrypted)).thenReturn(List.of(
                PlatformItem.builder().id("bad").name("缺失价格商品").price(null).build()
        ));

        ProductImportRequest request = new ProductImportRequest();
        request.setApiProviderId(9L);
        request.setProductIds(List.of("bad"));
        request.setSyncCategories(false);

        Map<String, Object> result = service.importSelectedProducts(request);

        assertEquals(0, result.get("success"));
        assertEquals(1, result.get("fail"));
        assertEquals(0, result.get("created"));
    }

}
