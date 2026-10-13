package com.course.platform.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.course.platform.domain.dto.CourseContractRequest;
import com.course.platform.domain.dto.CourseWalletTransferRequest;
import com.course.platform.domain.entity.CourseReservedTransfer;
import com.course.platform.service.impl.CoursePrivateContractService;
import com.course.platform.service.impl.CourseReservedWalletService;
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

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** 真实HTTP绑定验证静态路径优先、公开UUID入参以及资金字段白名单。 */
class CourseWalletHttpTest {
    CoursePrivateContractService service = mock(CoursePrivateContractService.class);
    CourseReservedWalletService funds = mock(CourseReservedWalletService.class);
    MockMvc mvc;
    @BeforeEach void setup() {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(7L,null,List.of(new SimpleGrantedAuthority("ROLE_USER"))));
        mvc=MockMvcBuilders.standaloneSetup(new CourseWalletController(service,funds),new AdminCourseWalletController(service,funds)).build();
    }
    @AfterEach void cleanup() { SecurityContextHolder.clearContext(); }
    @Test void transferListUsesStaticMappingInsteadOfWalletIdConversion() throws Exception {
        when(service.myTransfers(7L,1,20)).thenReturn(new Page<>(1,20));
        mvc.perform(get("/course-wallets/transfers")).andExpect(status().isOk()).andExpect(jsonPath("$.code").value(1));
        verify(service).myTransfers(7L,1,20); verify(service,never()).myWallet(any(),any());
    }
    @Test void publicUuidIsAcceptedWithoutInternalUserId() throws Exception {
        mvc.perform(post("/admin/course-private-contracts").contentType(MediaType.APPLICATION_JSON).content("""
          {"userUid":"00000000-0000-4000-8000-000000000007","platformId":9,"fixedPrice":7.20,
           "minInitialDeposit":0,"status":"ACTIVE","reason":"管理员配置"}
          """)).andExpect(status().isOk());
        var form=ArgumentCaptor.forClass(CourseContractRequest.class); verify(service).create(form.capture(),eq(7L)); assertNull(form.getValue().getUserId()); assertEquals("00000000-0000-4000-8000-000000000007",form.getValue().getUserUid());
    }
    @Test void forgedOwnerOrBalanceCannotChangeTransferBinding() throws Exception {
        var transfer=new CourseReservedTransfer(); transfer.setId(3L); when(funds.transferIn(eq(5L),eq(7L),any())).thenReturn(transfer);
        mvc.perform(post("/course-wallets/5/transfer-in").contentType(MediaType.APPLICATION_JSON).content("""
          {"amount":10.00,"requestKey":"request_1","userId":999,"walletId":999,"balance":999999}
          """)).andExpect(status().isOk());
        var form=ArgumentCaptor.forClass(CourseWalletTransferRequest.class); verify(funds).transferIn(eq(5L),eq(7L),form.capture()); assertEquals(0,new BigDecimal("10").compareTo(form.getValue().getAmount()));
    }
    @Test void badMoneyAndRequestKeysAreRejectedBeforeFundingService() throws Exception {
        for (String body : List.of("{\"amount\":0,\"requestKey\":\"one\"}","{\"amount\":-1,\"requestKey\":\"one\"}","{\"amount\":1.001,\"requestKey\":\"one\"}","{\"amount\":10000000000,\"requestKey\":\"one\"}","{\"amount\":1,\"requestKey\":\"unsafe key\"}")) {
            mvc.perform(post("/course-wallets/5/transfer-in").contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isBadRequest());
        }
        verifyNoInteractions(funds);
    }
    @Test void administrativeReferenceBindsPublicUuidAndReturnsNoFundingData() throws Exception {
        String uid="00000000-0000-4000-8000-000000000007";
        when(service.adminPriceReference(7L,uid,9L)).thenReturn(Map.of("basePrice",new BigDecimal("1.23"),"ordinaryPrice",new BigDecimal("1.85"),"rateType","MULTIPLY"));
        mvc.perform(get("/admin/course-private-contracts/price-reference").param("userUid",uid).param("platformId","9").param("operatorId","999"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(1)).andExpect(jsonPath("$.data.basePrice").value(1.23)).andExpect(jsonPath("$.data.ordinaryPrice").value(1.85)).andExpect(jsonPath("$.data.rateType").value("MULTIPLY"))
                .andExpect(jsonPath("$.data.balance").doesNotExist()).andExpect(jsonPath("$.data.walletBalance").doesNotExist()).andExpect(jsonPath("$.data.userId").doesNotExist());
        verify(service).adminPriceReference(7L,uid,9L);
    }
    @Test void administrativeReferenceRequiresUuidAndPlatformQueryParameters() throws Exception {
        mvc.perform(get("/admin/course-private-contracts/price-reference").param("platformId","9")).andExpect(status().isBadRequest());
        mvc.perform(get("/admin/course-private-contracts/price-reference").param("userUid","00000000-0000-4000-8000-000000000007")).andExpect(status().isBadRequest());
        verifyNoInteractions(service,funds);
    }
}
