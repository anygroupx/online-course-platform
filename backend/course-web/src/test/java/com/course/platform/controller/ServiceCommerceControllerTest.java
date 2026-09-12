package com.course.platform.controller;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.course.platform.application.service.security.SecurityAuditService;
import com.course.platform.application.service.servicecommerce.ServiceCommerceService;
import com.course.platform.domain.servicecommerce.ServiceCommerceTypes.*;
import com.course.platform.domain.servicecommerce.ServiceOrderFilter;
import com.course.platform.shared.exception.GlobalExceptionHandler;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.*;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;
import java.util.*;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = ServiceCommerceControllerTest.Config.class)
class ServiceCommerceControllerTest {
    @Configuration(proxyBeanMethods = false)
    @EnableMethodSecurity
    static class Config {
        @Bean
        ServiceCommerceService service() {
            return mock(ServiceCommerceService.class);
        }

        @Bean
        ServiceCommerceController controller(ServiceCommerceService service) {
            return new ServiceCommerceController(service);
        }
    }

    @Autowired ServiceCommerceController controller;
    @Autowired ServiceCommerceService service;
    MockMvc mvc;

    @BeforeEach
    void setup() {
        reset(service);
        mvc =
                MockMvcBuilders.standaloneSetup(controller)
                        .setMessageConverters(
                                new MappingJackson2HttpMessageConverter(
                                        new ObjectMapper().findAndRegisterModules()))
                        .setControllerAdvice(
                                new GlobalExceptionHandler(mock(SecurityAuditService.class)))
                        .build();
        auth("ROLE_USER");
    }

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    void auth(String... permissions) {
        SecurityContextHolder.getContext()
                .setAuthentication(
                        new UsernamePasswordAuthenticationToken(
                                7L,
                                null,
                                Arrays.stream(permissions)
                                        .map(SimpleGrantedAuthority::new)
                                        .toList()));
    }

    @Test
    void normalUsersCanReadTheStoreWithoutAnAdminPermission() throws Exception {
        when(service.products(1, 20, false)).thenReturn(new Page<>());
        mvc.perform(get("/services"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"));
        verify(service).products(1, 20, false);
    }

    @Test
    void ordinaryUsersCannotPublishProductsInspectOtherOrdersOrReconcileFunds() throws Exception {
        for (String role : List.of("ROLE_USER", "ROLE_ADMIN", "order:update")) {
            auth(role);
            mvc.perform(get("/admin/service-products")).andExpect(status().isForbidden());
            mvc.perform(get("/admin/service-orders")).andExpect(status().isForbidden());
            mvc.perform(get("/admin/service-orders/test/audit")).andExpect(status().isForbidden());
            mvc.perform(get("/admin/service-order-operations/test"))
                    .andExpect(status().isForbidden());
        }
        verifyNoInteractions(service);
    }

    @Test
    void reconciliationRequiresBothFinancialAndProviderPermissions() throws Exception {
        String body =
                "{\"outcome\":\"NOT_ACCEPTED\",\"evidence\":\"已核实上游没有订单和扣款记录\",\"upstreamChecked\":true}";
        auth("api-provider:update");
        mvc.perform(
                        post("/admin/service-order-operations/test/resolve")
                                .contentType("application/json")
                                .content(body))
                .andExpect(status().isForbidden());
        auth("payment:reconcile");
        mvc.perform(
                        post("/admin/service-order-operations/test/resolve")
                                .contentType("application/json")
                                .content(body))
                .andExpect(status().isForbidden());
        verifyNoInteractions(service);
        auth("api-provider:update", "payment:reconcile");
        mvc.perform(
                        post("/admin/service-order-operations/test/resolve")
                                .contentType("application/json")
                                .content(body))
                .andExpect(status().isOk());
        verify(service).resolve(eq("test"), any());
    }

    @Test
    void oversizedPlansAndUnrecognizedActionsNeverReachBusinessService() throws Exception {
        mvc.perform(
                        post("/services/1/quotes")
                                .contentType("application/json")
                                .content(
                                        "{\"quantity\":366,\"distance\":2,\"fields\":{},\"authorizedAccount\":true}"))
                .andExpect(status().isUnprocessableEntity());
        mvc.perform(
                        post("/service-orders/test/quotes")
                                .contentType("application/json")
                                .content("{\"action\":\"arbitrary_act\",\"quantity\":1}"))
                .andExpect(status().isUnprocessableEntity());
        verifyNoInteractions(service);
    }

    @Test
    void confirmationUsesOnlyServerIssuedOperationIdAndReturnsStringAmounts() throws Exception {
        when(service.confirm("op-id"))
                .thenReturn(
                        new QuoteView(
                                "op-id",
                                "order-id",
                                "CREATE",
                                "SUCCEEDED",
                                "服务",
                                10,
                                "5.00",
                                "本次扣款",
                                LocalDateTime.now(),
                                null));
        mvc.perform(
                        post("/service-order-operations/op-id/confirm")
                                .contentType("application/json")
                                .content("{\"amount\":0.01,\"userId\":999,\"providerId\":999}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.amount").isString())
                .andExpect(jsonPath("$.data.amount").value("5.00"))
                .andExpect(jsonPath("$.data.unitCharge").doesNotExist())
                .andExpect(header().string("Cache-Control", "no-store"));
        verify(service).confirm("op-id");
    }

    @Test
    void previewRecoveryAndConfirmationKeepExactDecimalStrings() throws Exception {
        QuoteView ready =
                new QuoteView(
                        "precise-op", null, "CREATE", "READY", "每次公里计划", 3,
                        "0.16", "本次余额扣款", LocalDateTime.of(2099, 1, 1, 0, 0),
                        null, "次", null, "0.05499989");
        QuoteView confirmed =
                new QuoteView(
                        "precise-op", "order-id", "CREATE", "SUCCEEDED", "每次公里计划", 3,
                        "0.16", "本次余额扣款", ready.expiresAt(), null, "次", null, "0.05499989");
        when(service.quote(eq(1L), any())).thenReturn(ready);
        when(service.operation("precise-op")).thenReturn(ready);
        when(service.confirm("precise-op")).thenReturn(confirmed);

        for (var request : List.of(
                post("/services/1/quotes").contentType("application/json")
                        .content("{\"quantity\":3,\"distance\":\"0.11\",\"fields\":{},\"authorizedAccount\":true}"),
                get("/service-order-operations/precise-op"),
                post("/service-order-operations/precise-op/confirm").contentType("application/json")
                        .content("{\"quantity\":9999,\"unitCharge\":\"0.00000001\",\"amount\":\"0.01\"}"))) {
            mvc.perform(request)
                    .andExpect(status().isOk())
                    .andExpect(header().string("Cache-Control", "no-store"))
                    .andExpect(jsonPath("$.data.amount").isString())
                    .andExpect(jsonPath("$.data.amount").value("0.16"))
                    .andExpect(jsonPath("$.data.unitCharge").isString())
                    .andExpect(jsonPath("$.data.unitCharge").value("0.05499989"))
                    .andExpect(jsonPath("$.data.quantity").value(3))
                    .andExpect(jsonPath("$.data.quantityUnit").value("次"));
        }
        verify(service).quote(eq(1L), argThat(form -> form.quantity() == 3));
        verify(service).operation("precise-op");
        verify(service).confirm("precise-op");
        verifyNoMoreInteractions(service);
    }

    @Test
    void legacyQuoteRecoveryPreservesRecordedPriceAndTrailingZeros() throws Exception {
        when(service.operation("legacy-op"))
                .thenReturn(new QuoteView(
                        "legacy-op", null, "CREATE", "READY", "每次公里计划", 3,
                        "0.17", "本次余额扣款", LocalDateTime.of(2099, 1, 1, 0, 0),
                        null, "次", null, "0.05500000"));
        mvc.perform(get("/service-order-operations/legacy-op"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.data.amount").isString())
                .andExpect(jsonPath("$.data.amount").value("0.17"))
                .andExpect(jsonPath("$.data.unitCharge").isString())
                .andExpect(jsonPath("$.data.unitCharge").value("0.05500000"));
        verify(service).operation("legacy-op");
        verifyNoMoreInteractions(service);
    }

    @Test
    void administrativeRefundKeepsExactUnitChargeAndServerCappedAmount() throws Exception {
        auth("api-provider:update", "payment:reconcile");
        QuoteView ready =
                new QuoteView(
                        "refund-op", "order-id", "SETTLE_REFUND", "READY", "每次公里计划", 6,
                        "0.32", "预计退款上限（按实际核实次数结算）", LocalDateTime.of(2099, 1, 1, 0, 0),
                        null, "次", null, "0.05499989");
        when(service.quoteRefundSettlement(eq("order-id"), any())).thenReturn(ready);
        when(service.adminOperation("refund-op")).thenReturn(ready);
        when(service.confirmRefundSettlement("refund-op"))
                .thenReturn(new QuoteView(
                        "refund-op", "order-id", "SETTLE_REFUND", "SUCCEEDED", "每次公里计划", 6,
                        "0.32", "已退回账户余额", ready.expiresAt(), null, "次", null, "0.05499989"));
        for (var request : List.of(
                post("/admin/service-orders/order-id/refund-quotes").contentType("application/json")
                        .content("{\"orderVersion\":1,\"refundedUnits\":6,\"evidence\":\"已核查退款记录和资金流水\",\"upstreamChecked\":true}"),
                get("/admin/service-order-operations/refund-op"),
                post("/admin/service-order-operations/refund-op/settle-refund").contentType("application/json")
                        .content("{\"unitCharge\":\"0.05500000\",\"amount\":\"0.33\",\"userId\":999}"))) {
            mvc.perform(request)
                    .andExpect(status().isOk())
                    .andExpect(header().string("Cache-Control", "no-store"))
                    .andExpect(jsonPath("$.data.amount").isString())
                    .andExpect(jsonPath("$.data.amount").value("0.32"))
                    .andExpect(jsonPath("$.data.unitCharge").isString())
                    .andExpect(jsonPath("$.data.unitCharge").value("0.05499989"))
                    .andExpect(jsonPath("$.data.quantity").value(6));
        }
        verify(service).quoteRefundSettlement(eq("order-id"), any());
        verify(service).adminOperation("refund-op");
        verify(service).confirmRefundSettlement("refund-op");
        verifyNoMoreInteractions(service);
    }

    @Test
    void malformedSensitiveBodyReturnsSafeErrorWithoutReflectingInput() throws Exception {
        mvc.perform(
                        post("/services/1/quotes")
                                .contentType("application/json")
                                .content(
                                        "{\"quantity\":\"sensitive-password\",\"fields\":{\"password\":\"private-value\"}}"))
                .andExpect(status().isBadRequest())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.message").value("请求体格式错误"));
        verifyNoInteractions(service);
    }

    @Test
    void localRefundSettlementIsNotAnOrdinaryUserMutation() throws Exception {
        String body =
                "{\"orderVersion\":1,\"refundedUnits\":6,\"evidence\":\"已核查上游退款单与资金记录\",\"upstreamChecked\":true}";
        for (String permission : List.of("ROLE_USER", "api-provider:update", "payment:reconcile")) {
            auth(permission);
            mvc.perform(
                            post("/admin/service-orders/order-id/refund-quotes")
                                    .contentType("application/json")
                                    .content(body))
                    .andExpect(status().isForbidden());
            mvc.perform(post("/admin/service-order-operations/op-id/settle-refund"))
                    .andExpect(status().isForbidden());
        }
        verifyNoInteractions(service);
        auth("api-provider:update", "payment:reconcile");
        mvc.perform(
                        post("/admin/service-orders/order-id/refund-quotes")
                                .contentType("application/json")
                                .content(body))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"));
        mvc.perform(post("/admin/service-order-operations/op-id/settle-refund"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"));
        verify(service).quoteRefundSettlement(eq("order-id"), any());
        verify(service).confirmRefundSettlement("op-id");
    }

    @Test
    void internshipScheduleAndNestedWordRangesAreValidatedAtTheHttpBoundary() throws Exception {
        String valid =
                "{\"quantity\":0,\"distance\":null,\"fields\":{},\"authorizedAccount\":true,\"schedule\":{\"endDate\":\"2026-10-01\",\"weekdays\":[1,3,5],\"checkInTime\":\"08:30:00\",\"checkOutTime\":\"18:00:00\",\"runMode\":1,\"weeklyReportDay\":7,\"monthlyReportDay\":0,\"reportLengths\":{\"day\":{\"minSize\":200,\"maxSize\":500}}}}";
        for (String invalid :
                List.of(
                        valid.replace("[1,3,5]", "[0,8]"),
                        valid.replace("08:30:00", "29:00:00"),
                        valid.replace("\"runMode\":1", "\"runMode\":9"),
                        valid.replace("\"maxSize\":500", "\"maxSize\":10001"))) {
            mvc.perform(post("/services/1/quotes").contentType("application/json").content(invalid))
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(header().string("Cache-Control", "no-store"));
        }
        verifyNoInteractions(service);
        mvc.perform(post("/services/1/quotes").contentType("application/json").content(valid))
                .andExpect(status().isOk());
        verify(service)
                .quote(eq(1L), argThat(form -> form.distance() == null && form.schedule() != null));
    }
    @Test void httpBindingPreservesOneOrderAndExactLargeTotalDistanceForServiceValidation() throws Exception {
        for (String distance : List.of("0.01", "120.50", "999999.99")) {
            mvc.perform(post("/services/5/quotes").contentType("application/json")
                    .content("{\"quantity\":1,\"distance\":\"" + distance + "\",\"fields\":{},\"authorizedAccount\":true}"))
                    .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"));
            verify(service).quote(eq(5L), argThat(form -> form.quantity() == 1
                    && new java.math.BigDecimal(distance).equals(form.distance())));
        }
    }

    @Test void httpBindingRejectsDistancePrecisionAndStorageOverflowBeforeBusinessCode() throws Exception {
        for (String distance : List.of("0", "-1", "120.501", "1000000")) {
            mvc.perform(post("/services/5/quotes").contentType("application/json")
                    .content("{\"quantity\":1,\"distance\":\"" + distance + "\",\"fields\":{},\"authorizedAccount\":true}"))
                    .andExpect(status().isUnprocessableEntity()).andExpect(header().string("Cache-Control", "no-store"));
        }
        verifyNoInteractions(service);
    }


    @Test
    void statusChecksExposeOnlySuccessfulBeijingTimeAndGenericDelayFlag() throws Exception {
        var checked = LocalDateTime.of(2026, 9, 11, 10, 20, 30);
        var view = new OrderView("order-id", "晨跑", "st***nt", "jiguang", "default", "ACTIVE",
                10, 3, "2.00", "5.00", "0.00", null, checked.minusDays(1), 2L,
                List.of("PAUSE"), null, "次", null, new StatusCheckView(checked, true));
        when(service.sync("order-id")).thenReturn(view);
        when(service.order("order-id")).thenReturn(view);
        for (var request : List.of(get("/service-orders/order-id"), post("/service-orders/order-id/sync"))) {
            mvc.perform(request)
                    .andExpect(status().isOk())
                    .andExpect(header().string("Cache-Control", "no-store"))
                    .andExpect(jsonPath("$.data.statusCheck.checkedAt").value("2026-09-11T10:20:30"))
                    .andExpect(jsonPath("$.data.statusCheck.delayed").value(true))
                    .andExpect(jsonPath("$.data.statusCheck.length()").value(2))
                    .andExpect(jsonPath("$.data.statusCheckToken").doesNotExist())
                    .andExpect(jsonPath("$.data.statusCheckUntil").doesNotExist())
                    .andExpect(jsonPath("$.data.statusCheckAfter").doesNotExist())
                    .andExpect(jsonPath("$.data.providerIdentity").doesNotExist());
        }
        verify(service).sync("order-id");
        verify(service).order("order-id");
        verifyNoMoreInteractions(service);
    }

    @Test
    void legacyOrderResponseHasNoInventedLastCheckTime() throws Exception {
        var mapper = new ObjectMapper().findAndRegisterModules();
        var legacy = mapper.readValue("{\"id\":\"old-order\",\"title\":\"历史订单\",\"providerType\":\"flash\","
                + "\"quantity\":10,\"completed\":0,\"status\":\"ACTIVE\",\"actions\":[]}", OrderView.class);
        org.junit.jupiter.api.Assertions.assertNull(legacy.statusCheck());
        when(service.order("old-order")).thenReturn(legacy);
        mvc.perform(get("/service-orders/old-order"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.statusCheck").doesNotExist());
    }

    @Test
    void orderSearchBindsOptionalFiltersWithoutBreakingUnfilteredClients() throws Exception {
        when(service.orders(1, 20, false)).thenReturn(new Page<>(1, 20));
        mvc.perform(get("/service-orders")).andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"));
        mvc.perform(get("/service-orders").param("keyword", "").param("providerType", "").param("createdFrom", ""))
                .andExpect(status().isOk());
        verify(service, times(2)).orders(1, 20, false);
        var filter = new ServiceOrderFilter("100%_!", "flash", "CONFIRMING", null,
                "00000000-0000-0000-0000-000000000001", "2026-09-01", "2026-09-12");
        when(service.orders(2, 20, false, filter)).thenReturn(new Page<>(2, 20));
        mvc.perform(get("/service-orders").param("page", "2").param("pageSize", "20")
                        .param("keyword", filter.keyword()).param("providerType", filter.providerType())
                        .param("status", filter.status()).param("orderId", filter.orderId())
                        .param("createdFrom", filter.createdFrom()).param("createdTo", filter.createdTo()))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.data.current").value(2)).andExpect(jsonPath("$.data.size").value(20));
        verify(service).orders(2, 20, false, filter);
        verifyNoMoreInteractions(service);
    }

    @Test
    void orderSearchOwnerIdIsBoundLosslesslyAndAdministrativeReadIsPermissionProtected() throws Exception {
        mvc.perform(get("/admin/service-orders").param("ownerId", "8")).andExpect(status().isForbidden());
        verifyNoInteractions(service);
        auth("api-provider:update");
        var filter = new ServiceOrderFilter(null, null, null, Long.MAX_VALUE, null, null, null);
        when(service.orders(1, 20, true, filter)).thenReturn(new Page<>(1, 20));
        mvc.perform(get("/admin/service-orders").param("ownerId", Long.toString(Long.MAX_VALUE)))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"));
        verify(service).orders(1, 20, true, filter);
    }

    @Test
    void orderSearchRejectsInvalidFilterSyntaxBeforeInvokingTheService() throws Exception {
        for (var invalid : List.of(Map.entry("keyword", "x".repeat(101)), Map.entry("providerType", "unknown"),
                Map.entry("status", "active"), Map.entry("orderId", "invalid"), Map.entry("createdFrom", "2026-9-1"),
                Map.entry("createdTo", "2026-09-12T00:00:00"), Map.entry("ownerId", "0"), Map.entry("ownerId", "-1"))) {
            mvc.perform(get("/service-orders").param(invalid.getKey(), invalid.getValue()))
                    .andExpect(status().isUnprocessableEntity()).andExpect(header().string("Cache-Control", "no-store"));
        }
        verifyNoInteractions(service);
    }

    @Test
    void scoreInformationIsAPlainJsonFieldWithNoCacheableResponse() throws Exception {
        String text = "成绩说明 <b>等待核对</b>\nhttps://fixture.invalid/result";
        when(service.scoreInfo("order-id")).thenReturn(new OrderText(text));
        mvc.perform(get("/service-orders/order-id/score-info"))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(content().contentTypeCompatibleWith("application/json"))
                .andExpect(jsonPath("$.data.text").value(text))
                .andExpect(jsonPath("$.data.length()").value(1));
        verify(service).scoreInfo("order-id"); verifyNoMoreInteractions(service);
    }

    @Test
    void runTaskProjectionKeepsItsExplicitEditabilityAndEndTime() throws Exception {
        when(service.logs("order-id", 1)).thenReturn(new RunLogPage(List.of(
                new RunLog("task-1", "2026-09-12 07:30:00", "跑步结束", false, "2026-09-12 08:00:00")), 1, false));
        mvc.perform(get("/service-orders/order-id/logs"))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.data.items[0].editable").value(false))
                .andExpect(jsonPath("$.data.items[0].endTime").value("2026-09-12 08:00:00"));
        verify(service).logs("order-id", 1);
    }

    @Test
    void recoveryBindsBothReceiptNumbersWithoutConflatingTheirRoles() throws Exception {
        auth("api-provider:update", "payment:reconcile");
        mvc.perform(post("/admin/service-order-operations/op-id/resolve").contentType("application/json")
                        .content("{\"outcome\":\"ACCEPTED\",\"externalOrderNo\":\"yid-451\",\"externalSubOrderNo\":\"17\","
                                + "\"evidence\":\"已逐项核实两个编号、账号和实际资金记录\",\"upstreamChecked\":true}"))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"));
        verify(service).resolve(eq("op-id"), argThat(form -> "yid-451".equals(form.externalOrderNo())
                && "17".equals(form.externalSubOrderNo()) && form.refundedUnits() == null));
    }

}
