package com.course.platform.service.impl;

import com.course.platform.common.exception.BusinessException;
import com.course.platform.common.security.SecurityAuthorities;
import com.course.platform.domain.entity.*;
import com.course.platform.domain.dto.CourseContractRequest;
import com.course.platform.infra.persistence.mapper.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.time.*;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** 协议写入白名单、激活资格、乐观版本与资金锁序的回归。 */
class CoursePrivateContractServiceTest {
    UserMapper users = mock(UserMapper.class);
    CoursePlatformMapper platforms = mock(CoursePlatformMapper.class);
    PlatformCategoryMapper categories = mock(PlatformCategoryMapper.class);
    CoursePrivateContractMapper contracts = mock(CoursePrivateContractMapper.class);
    CourseReservedWalletMapper wallets = mock(CourseReservedWalletMapper.class);
    CourseReservedWalletLedgerMapper ledgers = mock(CourseReservedWalletLedgerMapper.class);
    CourseReservedTransferMapper transfers = mock(CourseReservedTransferMapper.class);
    CoursePrivateContractHistoryMapper histories = mock(CoursePrivateContractHistoryMapper.class);
    UserAuthorityMapper authorities = mock(UserAuthorityMapper.class);
    CourseReservedWalletService funds = mock(CourseReservedWalletService.class);
    CoursePricingService pricing = mock(CoursePricingService.class);
    Clock clock = Clock.fixed(Instant.parse("2026-10-13T12:00:00Z"),ZoneId.of("Asia/Shanghai"));
    CoursePrivateContractService service;
    CoursePrivateContract existing;
    CourseReservedWallet wallet;
    User target;

    @BeforeEach void setup() {
        service = new CoursePrivateContractService(users,platforms,categories,contracts,wallets,ledgers,transfers,histories,authorities,funds,pricing,clock);
        User operator = new User(); operator.setId(1L); operator.setStatus(1);
        target = new User(); target.setId(7L); target.setUid("00000000-0000-4000-8000-000000000007"); target.setStatus(1);
        when(users.selectById(1L)).thenReturn(operator); when(users.selectById(7L)).thenReturn(target);
        when(users.selectByIdForUpdate(7L)).thenReturn(target); when(users.selectOne(any())).thenReturn(target);
        CoursePlatform product = new CoursePlatform(); product.setId(9L); product.setStatus(1); product.setName("课程X");
        when(platforms.selectById(9L)).thenReturn(product);when(platforms.selectByIdForUpdate(9L)).thenReturn(product);
        when(authorities.findAuthoritiesByUserId(1L)).thenReturn(List.of(SecurityAuthorities.COURSE_PRIVATE_PRICE_MANAGE));
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(1L,null,List.of(new SimpleGrantedAuthority(SecurityAuthorities.COURSE_PRIVATE_PRICE_MANAGE))));
        when(contracts.insert(any(CoursePrivateContract.class))).thenAnswer(invocation -> { invocation.<CoursePrivateContract>getArgument(0).setId(3L); return 1; });
        when(wallets.insert(any(CourseReservedWallet.class))).thenAnswer(invocation -> { invocation.<CourseReservedWallet>getArgument(0).setId(5L); return 1; });
        existing = new CoursePrivateContract(); existing.setId(3L); existing.setUserId(7L); existing.setPlatformId(9L); existing.setStatus("PENDING_ACTIVATION"); existing.setFixedPrice(new BigDecimal("7.20")); existing.setMinInitialDeposit(new BigDecimal("100")); existing.setVersion(2);
        wallet = new CourseReservedWallet(); wallet.setId(5L); wallet.setContractId(3L); wallet.setUserId(7L); wallet.setPlatformId(9L); wallet.setBalance(BigDecimal.ZERO);
        when(contracts.selectById(3L)).thenReturn(existing); when(contracts.selectByIdForUpdate(3L)).thenReturn(existing);
        when(wallets.selectByBinding(7L,9L)).thenReturn(wallet); when(wallets.selectByIdForUpdate(5L)).thenReturn(wallet);
        when(contracts.updateConfig(any(),eq(2))).thenReturn(1); when(funds.snapshot(any())).thenReturn("{}");
    }
    @AfterEach void cleanup() { SecurityContextHolder.clearContext(); }
    CourseContractRequest command(String status,String threshold,Integer version) {
        return form(target.getUid(),null,9L,new BigDecimal("7.20"),new BigDecimal(threshold),status,null,null,version,"管理员配置");
    }
    CourseContractRequest form(String uid, Long userId, Long platformId, BigDecimal price, BigDecimal minimum, String status, LocalDateTime from, LocalDateTime until, Integer version, String reason) {
        var value = new CourseContractRequest(); value.setUserUid(uid); value.setUserId(userId); value.setPlatformId(platformId); value.setFixedPrice(price); value.setMinInitialDeposit(minimum); value.setStatus(status); value.setValidFrom(from); value.setValidUntil(until); value.setVersion(version); value.setReason(reason); return value;
    }
    @Test void zeroThresholdCreatesActivatedContractWalletAndAudit() {
        service.create(command("ACTIVE","0",null),1L);
        ArgumentCaptor<CoursePrivateContract> saved=ArgumentCaptor.forClass(CoursePrivateContract.class);
        verify(contracts).insert(saved.capture()); assertEquals("ACTIVE",saved.getValue().getStatus()); assertNotNull(saved.getValue().getActivatedAt());
        verify(wallets).insert(any(CourseReservedWallet.class)); verify(funds).history(any(),eq(1L),eq("管理员配置"),isNull());
        var order=inOrder(users,contracts,wallets,funds); order.verify(users).selectByIdForUpdate(7L); order.verify(contracts).selectByBinding(7L,9L); order.verify(contracts).insert(any(CoursePrivateContract.class)); order.verify(wallets).insert(any(CourseReservedWallet.class)); order.verify(funds).history(any(),eq(1L),eq("管理员配置"),isNull());
    }
    @Test void positiveThresholdCannotCreateActiveContract() {
        service.create(command("ACTIVE","100",null),1L); var saved=ArgumentCaptor.forClass(CoursePrivateContract.class); verify(contracts).insert(saved.capture()); assertEquals("PENDING_ACTIVATION",saved.getValue().getStatus()); assertNull(saved.getValue().getActivatedAt());
    }
    @Test void unsupportedInitialStatusRejected() { assertThrows(BusinessException.class,()->service.create(command("SUSPENDED","0",null),1L)); verify(contracts,never()).insert(any(CoursePrivateContract.class)); }
    @Test void duplicateBindingRejectedBeforeWrites() { when(contracts.selectByBinding(7L,9L)).thenReturn(existing); assertThrows(BusinessException.class,()->service.create(command("ACTIVE","0",null),1L)); verify(wallets,never()).insert(any(CourseReservedWallet.class)); }
    @Test void staleOrMissingVersionRejected() { assertThrows(BusinessException.class,()->service.update(3L,command("SUSPENDED","100",1),1L)); assertThrows(BusinessException.class,()->service.update(3L,command("SUSPENDED","100",null),1L)); verify(contracts,never()).updateConfig(any(),any()); }
    @Test void activationCannotBypassRealDeposit() { assertThrows(BusinessException.class,()->service.update(3L,command("ACTIVE","100",2),1L)); verify(contracts,never()).updateConfig(any(),any()); }
    @Test void fundedWalletMayActivateAndAuditWithIncrementedVersion() {
        wallet.setBalance(new BigDecimal("100")); service.update(3L,command("ACTIVE","100",2),1L); assertNotNull(existing.getActivatedAt()); assertEquals(3,existing.getVersion()); verify(funds).history(eq(existing),eq(1L),eq("管理员配置"),eq("{}"));
        var order=inOrder(users,contracts,wallets); order.verify(users).selectByIdForUpdate(7L); order.verify(contracts).selectByIdForUpdate(3L); order.verify(wallets).selectByIdForUpdate(5L); order.verify(contracts).updateConfig(existing,2);
    }
    @Test void alreadyActivatedQualificationSurvivesLowBalance() { existing.setActivatedAt(LocalDateTime.now(clock).minusDays(1)); existing.setStatus("SUSPENDED"); service.update(3L,command("ACTIVE","100",2),1L); assertEquals("ACTIVE",existing.getStatus()); }
    @Test void terminatedContractCannotRevive() { existing.setStatus("TERMINATED"); assertThrows(BusinessException.class,()->service.update(3L,command("ACTIVE","0",2),1L)); }
    @Test void modernOperationsCannotBeChangedThroughLegacyConfiguration(){existing.setOperationsMode("TIME_PREPAID");assertThrows(BusinessException.class,()->service.update(3L,command("SUSPENDED","100",2),1L));verify(contracts,never()).updateConfig(any(),any());}
    @Test void explicitVersionUpdateFailureDoesNotAuditSuccess() { when(contracts.updateConfig(any(),eq(2))).thenReturn(0); assertThrows(BusinessException.class,()->service.update(3L,command("SUSPENDED","100",2),1L)); verify(funds,never()).history(any(),any(),any(),any()); }
    @Test void disabledTargetCannotGetContract() { target.setStatus(0); assertThrows(BusinessException.class,()->service.create(command("ACTIVE","0",null),1L)); }
    @Test void disabledOperatorCannotWriteEvenWithPermission() { users.selectById(1L).setStatus(0); assertThrows(BusinessException.class,()->service.create(command("ACTIVE","0",null),1L)); verify(contracts,never()).insert(any(CoursePrivateContract.class)); }
    @Test void publicUidAndInternalIdMustIdentifySameAgent() { var form=command("ACTIVE","0",null); form.setUserId(8L); assertThrows(BusinessException.class,()->service.create(form,1L)); verify(contracts,never()).insert(any(CoursePrivateContract.class)); }
    @Test void disabledCategoryCannotReceiveNewContract() {
        platforms.selectById(9L).setCategoryId(4L); var category=new PlatformCategory(); category.setId(4L); category.setStatus(0); when(categories.selectById(4L)).thenReturn(category);
        assertThrows(BusinessException.class,()->service.create(command("ACTIVE","0",null),1L)); verify(contracts,never()).insert(any(CoursePrivateContract.class));
    }
    @Test void revokedPermissionCannotUseStaleAuthenticationAuthority() { when(authorities.findAuthoritiesByUserId(1L)).thenReturn(List.of()); assertThrows(BusinessException.class,()->service.create(command("ACTIVE","0",null),1L)); verify(contracts,never()).insert(any(CoursePrivateContract.class)); }
    @Test void principalCannotClaimAnotherOperatorIdentity() { assertThrows(BusinessException.class,()->service.create(command("ACTIVE","0",null),2L)); }
    @Test void invalidMoneyDateAndMissingReasonRejectedBeforeWrite() {
        var invalid = form(target.getUid(),null,9L,new BigDecimal("7.201"),BigDecimal.ZERO,"ACTIVE",null,null,null,"原因");
        assertThrows(BusinessException.class,()->service.create(invalid,1L));
        var dates = form(target.getUid(),null,9L,new BigDecimal("7.20"),BigDecimal.ZERO,"ACTIVE",LocalDateTime.now(clock),LocalDateTime.now(clock).minusDays(1),null,"原因");
        assertThrows(BusinessException.class,()->service.create(dates,1L));
        var reason = form(target.getUid(),null,9L,new BigDecimal("7.20"),BigDecimal.ZERO,"ACTIVE",null,null,null," ");
        assertThrows(BusinessException.class,()->service.create(reason,1L));
    }
    @Test void pagesRejectOversizeOrZeroWithoutQuerying() { assertThrows(BusinessException.class,()->service.myWallets(1L,1,101)); assertThrows(BusinessException.class,()->service.myWallets(1L,0,10)); verify(wallets,never()).selectPage(any(),any()); }
    @Test void missingAndForeignWalletBothReturnSameNotFound() {
        when(wallets.selectById(55L)).thenReturn(null); when(wallets.selectById(5L)).thenReturn(wallet);
        var missing=assertThrows(BusinessException.class,()->service.myWallet(1L,55L)); var foreign=assertThrows(BusinessException.class,()->service.myWallet(1L,5L)); assertEquals(missing.getCode(),foreign.getCode()); assertEquals(missing.getMessage(),foreign.getMessage());
    }
    @Test void priceListUsesOnlyCurrentOwnerAndOmitsDisabledCategories() {
        var visible=platforms.selectById(9L); var hidden=new CoursePlatform(); hidden.setId(10L); hidden.setCategoryId(4L);
        when(platforms.selectList(any())).thenReturn(List.of(visible,hidden)); when(categories.selectList(any())).thenReturn(List.of());
        var unavailable=new com.course.platform.domain.vo.CoursePricePreview(9L,new BigDecimal("7.20"),"PRIVATE","COURSE_WALLET",3L,2,5L,BigDecimal.ZERO,false,"专户余额不足");
        when(pricing.preview(1L,9L)).thenReturn(unavailable); var result=service.myPrices(1L);
        assertEquals(1,result.size()); assertFalse(result.get(9L).canPurchase()); verify(pricing).preview(1L,9L); verify(pricing,never()).preview(any(),eq(10L));
    }
    @Test void ownWalletWhitelistDoesNotExposeInternalUserIdOrCredentials() {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(7L,null,List.of(new SimpleGrantedAuthority("ROLE_USER"))));
        when(wallets.selectById(5L)).thenReturn(wallet); var result=service.myWallet(7L,5L);
        assertEquals("课程X",result.get("platformName")); assertEquals(new BigDecimal("7.20"),result.get("fixedPrice")); assertFalse(result.containsKey("userId")); assertFalse(result.containsKey("password")); assertFalse(result.containsKey("apiKey"));
    }
    @Test void disabledFeatureRejectsNewContractsButAllowsSafeExistingMaintenance() {
        org.springframework.test.util.ReflectionTestUtils.setField(service,"enabled",false);
        assertThrows(BusinessException.class,()->service.create(command("ACTIVE","0",null),1L)); verify(contracts,never()).insert(any(CoursePrivateContract.class));
        service.update(3L,command("SUSPENDED","100",2),1L); verify(contracts).updateConfig(existing,2);
    }
    void useRealPricing() {
        target.setRate(new BigDecimal("1.50")); target.setBalance(BigDecimal.ZERO);
        CoursePlatform product=platforms.selectById(9L); product.setBasePrice(new BigDecimal("10.00")); product.setRateType("MULTIPLY");
        pricing=new CoursePricingService(users,platforms,categories,contracts,wallets,clock);
        service=new CoursePrivateContractService(users,platforms,categories,contracts,wallets,ledgers,transfers,histories,authorities,funds,pricing,clock);
    }
    @Test void priceReferenceUsesExactUuidAndOnlyThreePublicFields() {
        useRealPricing(); platforms.selectById(9L).setBasePrice(new BigDecimal("1.23"));
        var value=service.adminPriceReference(1L,target.getUid(),9L);
        assertEquals(Set.of("basePrice","ordinaryPrice","rateType"),value.keySet());
        assertEquals(new BigDecimal("1.23"),value.get("basePrice")); assertEquals(new BigDecimal("1.85"),value.get("ordinaryPrice")); assertEquals("MULTIPLY",value.get("rateType"));
        var query=ArgumentCaptor.forClass(com.baomidou.mybatisplus.core.conditions.query.QueryWrapper.class); verify(users).selectOne(query.capture());
        assertTrue(query.getValue().getSqlSegment().startsWith("(uid = ")); assertEquals(List.of(target.getUid()),List.copyOf(query.getValue().getParamNameValuePairs().values()));
        verifyNoInteractions(wallets,ledgers,transfers,histories,funds);
    }
    @Test void priceReferenceUsesAddAndIsAvailableWithoutAnActivatedWallet() {
        useRealPricing(); platforms.selectById(9L).setRateType("ADD");
        assertEquals(new BigDecimal("11.50"),service.adminPriceReference(1L,target.getUid(),9L).get("ordinaryPrice"));
        verifyNoInteractions(wallets,funds);
    }
    @Test void existingPrivatePriceDoesNotReplaceOrdinaryAdministrativeReference() {
        useRealPricing(); when(contracts.selectByBinding(7L,9L)).thenReturn(existing);
        assertEquals(new BigDecimal("15.00"),service.adminPriceReference(1L,target.getUid(),9L).get("ordinaryPrice"));
        assertEquals(new BigDecimal("7.20"),pricing.preview(7L,9L).finalPrice()); assertEquals(2,existing.getVersion());
        verify(contracts,never()).updateConfig(any(),any()); verify(wallets,never()).increaseBalance(any(),any()); verify(wallets,never()).decreaseBalance(any(),any());
    }
    @Test void malformedMissingAndUnknownUuidCannotFallBackToInternalId() {
        useRealPricing();
        for(String uid : List.of(""," ","7","invalid-uid")) assertThrows(BusinessException.class,()->service.adminPriceReference(1L,uid,9L));
        assertThrows(BusinessException.class,()->service.adminPriceReference(1L,null,9L));
        verify(users,never()).selectOne(any());
        when(users.selectOne(any())).thenReturn(null); assertThrows(BusinessException.class,()->service.adminPriceReference(1L,"00000000-0000-4000-8000-000000000099",9L));
    }
    @Test void referenceRejectsDisabledTargetAndDisabledOperator() {
        useRealPricing(); target.setStatus(0); assertThrows(BusinessException.class,()->service.adminPriceReference(1L,target.getUid(),9L));
        target.setStatus(1); users.selectById(1L).setStatus(0); assertThrows(BusinessException.class,()->service.adminPriceReference(1L,target.getUid(),9L));
    }
    @Test void referenceReloadsDatabasePermissionAndRejectsForgedOperator() {
        useRealPricing(); when(authorities.findAuthoritiesByUserId(1L)).thenReturn(List.of());
        assertThrows(BusinessException.class,()->service.adminPriceReference(1L,target.getUid(),9L)); verify(users,never()).selectOne(any());
        assertThrows(BusinessException.class,()->service.adminPriceReference(2L,target.getUid(),9L));
    }
    @Test void referenceRejectsUnavailablePlatformAndCategory() {
        useRealPricing(); assertThrows(BusinessException.class,()->service.adminPriceReference(1L,target.getUid(),null)); assertThrows(BusinessException.class,()->service.adminPriceReference(1L,target.getUid(),0L));
        platforms.selectById(9L).setStatus(0); assertThrows(BusinessException.class,()->service.adminPriceReference(1L,target.getUid(),9L)); platforms.selectById(9L).setStatus(1);
        platforms.selectById(9L).setCategoryId(4L); assertThrows(BusinessException.class,()->service.adminPriceReference(1L,target.getUid(),9L));
        var category=new PlatformCategory(); category.setStatus(0); when(categories.selectById(4L)).thenReturn(category); assertThrows(BusinessException.class,()->service.adminPriceReference(1L,target.getUid(),9L));
    }
}
