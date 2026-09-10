package com.course.platform.controller;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.course.platform.application.service.security.SecurityAuditService;
import com.course.platform.application.service.servicecommerce.ServiceCommerceService;
import com.course.platform.domain.servicecommerce.ServiceCommerceTypes.*;
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
                .andExpect(jsonPath("$.data.amount").value("5.00"))
                .andExpect(header().string("Cache-Control", "no-store"));
        verify(service).confirm("op-id");
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

}
