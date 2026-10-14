package com.course.platform.controller;

import com.course.platform.common.security.SecurityAuthorities;
import com.course.platform.domain.dto.CourseContractRequest;
import com.course.platform.domain.dto.CourseWalletTransferRequest;
import com.course.platform.service.impl.CoursePrivateContractService;
import com.course.platform.service.impl.CourseReservedWalletService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.context.annotation.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** 通过实际Spring方法权限代理验证财务、审计和运营的访问边界。 */
class CourseWalletControllerSecurityTest {
    AnnotationConfigApplicationContext context;
    AdminCourseWalletController admin;
    CourseWalletController mine;
    CoursePrivateContractService service;
    CourseReservedWalletService funds;

    @Configuration @EnableMethodSecurity
    static class Config {
        @Bean CoursePrivateContractService contracts() { return mock(CoursePrivateContractService.class); }
        @Bean CourseReservedWalletService funds() { return mock(CourseReservedWalletService.class); }
        // 资金Bean现在具有周期依赖，夹具补齐依赖但保留原财务权限全部断言。
        @Bean com.course.platform.service.impl.CoursePrivateLegacySyncService legacySync(){return mock(com.course.platform.service.impl.CoursePrivateLegacySyncService.class);}
        @Bean com.course.platform.service.impl.CoursePrivateTermService privateTerms() { return mock(com.course.platform.service.impl.CoursePrivateTermService.class); }
        @Bean com.course.platform.service.impl.CoursePrivateRenewalService renewal() { return mock(com.course.platform.service.impl.CoursePrivateRenewalService.class); }
        @Bean com.course.platform.service.impl.CoursePrivateUpgradeService upgrade(){return mock(com.course.platform.service.impl.CoursePrivateUpgradeService.class);}
        @Bean com.course.platform.service.impl.CoursePrivateSettlementService settlement(){return mock(com.course.platform.service.impl.CoursePrivateSettlementService.class);}
        @Bean CourseWalletController mine(CoursePrivateContractService contracts,CourseReservedWalletService funds) { return new CourseWalletController(contracts,funds); }
        @Bean AdminCourseWalletController admin(CoursePrivateContractService contracts,CourseReservedWalletService funds) { return new AdminCourseWalletController(contracts,funds); }
    }
    @BeforeEach void setup() { context=new AnnotationConfigApplicationContext(Config.class); admin=context.getBean(AdminCourseWalletController.class); mine=context.getBean(CourseWalletController.class); service=context.getBean(CoursePrivateContractService.class); funds=context.getBean(CourseReservedWalletService.class); }
    @AfterEach void cleanup() { SecurityContextHolder.clearContext(); context.close(); }
    void auth(String... permissions) { SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(7L,null,java.util.Arrays.stream(permissions).map(SimpleGrantedAuthority::new).toList())); }

    @Test void ordinaryUserCannotManageOrReadOtherWallets() {
        auth("ROLE_USER"); assertThrows(AccessDeniedException.class,()->admin.contracts(1,20,null,null)); assertThrows(AccessDeniedException.class,()->admin.wallets(1,20,null,null)); assertThrows(AccessDeniedException.class,()->admin.create(new CourseContractRequest())); assertThrows(AccessDeniedException.class,()->admin.release(5L,new CourseWalletTransferRequest())); verifyNoInteractions(service,funds);
    }
    @Test void operatorOldUserAndPlatformUpdateCannotAdjustPriceOrMoney() {
        auth("ROLE_OPERATOR","user:update","platform:update"); assertThrows(AccessDeniedException.class,()->admin.create(new CourseContractRequest())); assertThrows(AccessDeniedException.class,()->admin.release(5L,new CourseWalletTransferRequest())); verifyNoInteractions(service,funds);
    }
    @Test void auditorCanReadButCannotModifyOrRelease() {
        auth(SecurityAuthorities.COURSE_PRIVATE_PRICE_READ,SecurityAuthorities.COURSE_WALLET_AUDIT);
        admin.contracts(1,20,null,null); admin.ledger(5L,1,20);
        assertThrows(AccessDeniedException.class,()->admin.create(new CourseContractRequest())); assertThrows(AccessDeniedException.class,()->admin.release(5L,new CourseWalletTransferRequest()));
    }
    @Test void financeReleaseUsesAuthenticatedOperatorId() {
        auth(SecurityAuthorities.COURSE_WALLET_RELEASE); var request=new CourseWalletTransferRequest(); admin.release(5L,request); verify(funds).release(5L,7L,request); assertThrows(AccessDeniedException.class,()->admin.create(new CourseContractRequest()));
    }
    @Test void privatePriceManageAllowsReadAndWriteOnlyItsDomain() {
        auth(SecurityAuthorities.COURSE_PRIVATE_PRICE_MANAGE); var request=new CourseContractRequest(); admin.contracts(1,20,null,null); admin.create(request); verify(service).create(request,7L); assertThrows(AccessDeniedException.class,()->admin.release(5L,new CourseWalletTransferRequest()));
    }
    @Test void ownEndpointsAlwaysDeriveOwnerFromAuthentication() {
        auth("ROLE_USER"); var request=new CourseWalletTransferRequest(); mine.wallets(1,20); mine.wallet(5L); mine.ledger(5L,1,20); mine.transferIn(5L,request); mine.price(9L);
        verify(service).myWallets(7L,1,20); verify(service).myWallet(7L,5L); verify(service).myLedger(7L,5L,1,20); verify(funds).transferIn(5L,7L,request); verify(service).myPrice(7L,9L);
    }
    @Test void anonymousCannotEnterPersonalEndpoints() {
        SecurityContextHolder.clearContext(); assertThrows(org.springframework.security.core.AuthenticationException.class,()->mine.wallets(1,20)); verifyNoInteractions(service);
    }
    @ParameterizedTest @ValueSource(strings={"USER","OPERATOR","FINANCE","AUDITOR"})
    void priceReferenceRequiresManagePermissionRatherThanRoleOrReadAuthority(String role) {
        auth("ROLE_"+role,SecurityAuthorities.COURSE_PRIVATE_PRICE_READ,"user:read","user:update","platform:update",SecurityAuthorities.COURSE_WALLET_RELEASE);
        assertThrows(AccessDeniedException.class,()->admin.priceReference("00000000-0000-4000-8000-000000000007",9L)); verifyNoInteractions(service,funds);
    }
    @Test void priceReferenceManagePermissionUsesAuthenticatedOperatorOnly() {
        auth(SecurityAuthorities.COURSE_PRIVATE_PRICE_MANAGE); admin.priceReference("00000000-0000-4000-8000-000000000007",9L);
        verify(service).adminPriceReference(7L,"00000000-0000-4000-8000-000000000007",9L);
    }
}
