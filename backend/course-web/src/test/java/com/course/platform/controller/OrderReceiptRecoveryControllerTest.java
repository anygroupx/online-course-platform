package com.course.platform.controller;

import com.course.platform.application.service.orderreceipt.OrderReceiptRecoveryService;
import com.course.platform.application.service.security.SecurityAuditService;
import com.course.platform.common.exception.BusinessException;
import com.course.platform.common.result.ResultCode;
import com.course.platform.domain.orderreceipt.OrderReceiptTypes.Candidates;
import com.course.platform.shared.exception.GlobalExceptionHandler;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import java.time.LocalDateTime;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = OrderReceiptRecoveryControllerTest.Config.class)
class OrderReceiptRecoveryControllerTest {
    @Configuration(proxyBeanMethods = false)
    @EnableMethodSecurity
    static class Config {
        @Bean OrderReceiptRecoveryService recovery() { return mock(OrderReceiptRecoveryService.class); }
        @Bean OrderReceiptRecoveryController controller(OrderReceiptRecoveryService recovery) {
            return new OrderReceiptRecoveryController(recovery);
        }
    }
    @Autowired OrderReceiptRecoveryService recovery;
    @Autowired OrderReceiptRecoveryController controller;
    MockMvc mvc;
    static final String PATH = "/admin/orders/1/receipt-recoveries/candidates";

    @BeforeEach void setup() {
        reset(recovery);
        auth("order:update", "api-provider:update");
        mvc = MockMvcBuilders.standaloneSetup(controller)
                .setMessageConverters(new MappingJackson2HttpMessageConverter(new ObjectMapper().findAndRegisterModules()
                        .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)))
                .setControllerAdvice(new GlobalExceptionHandler(mock(SecurityAuditService.class))).build();
    }
    @AfterEach void clear() { SecurityContextHolder.clearContext(); }
    void auth(String... permissions) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(7L, null,
                Arrays.stream(permissions).map(SimpleGrantedAuthority::new).toList()));
    }

    @Test void explicitCandidateReadIsNoStoreAndContainsOnlyOrderScopeTimeAndIds() throws Exception {
        when(recovery.candidates(1)).thenReturn(new Candidates(1L, List.of("receipt-9", "receipt-10"),
                LocalDateTime.of(2026,9,11,12,0), "CURRENT_RESPONSE"));
        mvc.perform(post(PATH)).andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.data.length()").value(4)).andExpect(jsonPath("$.data.orderId").value(1))
                .andExpect(jsonPath("$.data.scope").value("CURRENT_RESPONSE"))
                .andExpect(jsonPath("$.data.checkedAt").value("2026-09-11T12:00:00"))
                .andExpect(jsonPath("$.data.receiptIds[0]").value("receipt-9"))
                .andExpect(jsonPath("$.data.receiptIds[1]").value("receipt-10"))
                .andExpect(jsonPath("$.data.studentPassword").doesNotExist());
        verify(recovery).candidates(1); verifyNoMoreInteractions(recovery);
    }
    @Test void roleOrEitherPermissionAloneCannotDiscoverCandidateIds() throws Exception {
        for (String permission : List.of("ROLE_SUPER_ADMIN", "order:update", "api-provider:update")) {
            auth(permission);
            mvc.perform(post(PATH)).andExpect(status().isForbidden()).andExpect(jsonPath("$.data").doesNotExist());
        }
        verifyNoInteractions(recovery);
    }
    @Test void candidateConflictsAreErrorsNotSuccessfulEmptyLists() throws Exception {
        when(recovery.candidates(1)).thenThrow(new BusinessException(ResultCode.CONFLICT, "订单记录已变化，请重新查找"));
        mvc.perform(post(PATH)).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value(-110))
                .andExpect(jsonPath("$.data").doesNotExist());
        verify(recovery).candidates(1); verifyNoMoreInteractions(recovery);
    }
    @Test void bodyCannotOverrideTheOrderOrSupplyCredentialsForLookup() throws Exception {
        mvc.perform(post(PATH).contentType("application/json")
                .content("{\"orderId\":2,\"receiptId\":\"chosen\",\"apiKey\":\"client-supplied\",\"consent\":true}"))
                .andExpect(status().isOk());
        verify(recovery).candidates(1); verifyNoMoreInteractions(recovery);
    }
    @Test void ordinaryRecoveryReadsDoNotInvokeCandidateDiscovery() throws Exception {
        mvc.perform(get("/admin/orders/1/receipt-recoveries")).andExpect(status().isOk());
        verify(recovery).recent(1); verifyNoMoreInteractions(recovery);
    }
}
