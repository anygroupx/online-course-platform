package com.course.platform.service.impl;

import com.course.platform.domain.entity.*;
import com.course.platform.infra.persistence.mapper.*;
import java.math.BigDecimal;
import java.time.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** 验证商品报价、协议生命周期和预览冲突，金额以服务端为准。 */
class CoursePricingServiceTest {
    CoursePrivateContractMapper contracts = mock(CoursePrivateContractMapper.class);
    CourseReservedWalletMapper wallets = mock(CourseReservedWalletMapper.class);
    UserMapper users = mock(UserMapper.class);
    CoursePlatformMapper platforms = mock(CoursePlatformMapper.class);
    PlatformCategoryMapper categories = mock(PlatformCategoryMapper.class);
    Clock clock = Clock.fixed(Instant.parse("2026-10-13T12:00:00Z"), ZoneId.of("Asia/Shanghai"));
    CoursePricingService service;
    User user;
    CoursePlatform platform;
    CoursePrivateContract contract;
    CourseReservedWallet wallet;

    @BeforeEach void setup() {
        service = new CoursePricingService(users, platforms, categories, contracts, wallets, clock);
        user = new User(); user.setId(7L); user.setStatus(1); user.setRate(new BigDecimal("1.50")); user.setBalance(new BigDecimal("500"));
        platform = new CoursePlatform(); platform.setId(9L); platform.setStatus(1); platform.setBasePrice(new BigDecimal("10")); platform.setRateType("MULTIPLY");
        contract = new CoursePrivateContract(); contract.setId(3L); contract.setUserId(7L); contract.setPlatformId(9L); contract.setVersion(2);
        contract.setFixedPrice(new BigDecimal("7.20")); contract.setStatus("ACTIVE"); contract.setActivatedAt(LocalDateTime.now(clock)); contract.setMinInitialDeposit(BigDecimal.ZERO);
        wallet = new CourseReservedWallet(); wallet.setId(5L); wallet.setContractId(3L); wallet.setUserId(7L); wallet.setPlatformId(9L); wallet.setBalance(new BigDecimal("20"));
        when(users.selectById(7L)).thenReturn(user); when(platforms.selectById(9L)).thenReturn(platform);
    }
    void privatePrice() { when(contracts.selectByBinding(7L,9L)).thenReturn(contract); when(wallets.selectByBinding(7L,9L)).thenReturn(wallet); }
    void money(String value, BigDecimal actual) { assertEquals(0,new BigDecimal(value).compareTo(actual)); }
    @Test void ordinaryMultiplyAndHalfUp() { platform.setBasePrice(new BigDecimal("1.23")); money("1.85",service.preview(7L,9L).finalPrice()); }
    @Test void ordinaryAdd() { platform.setRateType("ADD"); money("11.50",service.preview(7L,9L).finalPrice()); }
    @Test void ordinaryReferenceSharesMultiplyRoundingWithNormalPurchasePrice() {
        platform.setBasePrice(new BigDecimal("1.23"));
        assertEquals(new BigDecimal("1.85"),service.ordinaryPrice(user,platform));
        assertEquals(service.preview(7L,9L).finalPrice(),service.ordinaryPrice(user,platform));
    }
    @Test void ordinaryReferenceSharesAddRoundingAndDoesNotUsePrivateContract() {
        privatePrice(); platform.setRateType("ADD"); platform.setBasePrice(new BigDecimal("1.235"));
        assertEquals(new BigDecimal("2.74"),service.ordinaryPrice(user,platform));
        assertEquals(new BigDecimal("7.20"),service.preview(7L,9L).finalPrice());
    }
    @Test void ordinaryReferenceRejectsMissingOrUnpayableMoney() {
        platform.setBasePrice(null); assertThrows(com.course.platform.common.exception.BusinessException.class,()->service.ordinaryPrice(user,platform));
        platform.setBasePrice(BigDecimal.ZERO); assertThrows(com.course.platform.common.exception.BusinessException.class,()->service.ordinaryPrice(user,platform));
        platform.setBasePrice(new BigDecimal("9999999999.99")); assertThrows(com.course.platform.common.exception.BusinessException.class,()->service.ordinaryPrice(user,platform));
    }
    @Test void fixedPriceIgnoresRateAndBasePriceChanges() {
        privatePrice(); money("7.20",service.preview(7L,9L).finalPrice()); user.setRate(new BigDecimal("99")); platform.setBasePrice(new BigDecimal("999"));
        var price=service.preview(7L,9L); money("7.20",price.finalPrice()); assertEquals("COURSE_WALLET",price.paymentSource()); assertTrue(price.canPurchase());
    }
    @Test void differentUserDoesNotSeePrivatePrice() { privatePrice(); user.setId(8L); when(users.selectById(8L)).thenReturn(user); money("15.00",service.preview(8L,9L).finalPrice()); }
    @ParameterizedTest @ValueSource(strings={"PENDING_ACTIVATION","SUSPENDED","EXPIRED","TERMINATED"})
    void unusableContractNeverFallsBackToGeneral(String status) { privatePrice(); contract.setStatus(status); var p=service.preview(7L,9L); assertFalse(p.canPurchase()); assertEquals("PRIVATE",p.priceSource()); assertEquals("COURSE_WALLET",p.paymentSource()); }
    @Test void futureOrExpiredContractBlocksPurchase() { privatePrice(); contract.setValidFrom(LocalDateTime.now(clock).plusSeconds(1)); assertFalse(service.preview(7L,9L).canPurchase()); contract.setValidFrom(null); contract.setValidUntil(LocalDateTime.now(clock)); assertFalse(service.preview(7L,9L).canPurchase()); }
    @Test void insufficientWalletDoesNotUseGeneralBalance() { privatePrice(); wallet.setBalance(new BigDecimal("1")); assertFalse(service.preview(7L,9L).canPurchase()); }
    @Test void activationThresholdIsNotContinuingMinimum() { privatePrice(); contract.setMinInitialDeposit(new BigDecimal("100")); assertTrue(service.preview(7L,9L).canPurchase()); }
    @Test void corruptWalletBindingFailsClosed() { privatePrice(); wallet.setPlatformId(10L); assertThrows(RuntimeException.class,()->service.preview(7L,9L)); }
    @Test void disabledUserOrProductCannotPreview() { user.setStatus(0); assertThrows(RuntimeException.class,()->service.preview(7L,9L)); user.setStatus(1); platform.setStatus(0); assertThrows(RuntimeException.class,()->service.preview(7L,9L)); }
}
