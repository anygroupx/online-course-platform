package com.course.platform.service.impl;

import com.course.platform.common.exception.BusinessException;
import com.course.platform.common.security.SecurityAuthorities;
import com.course.platform.domain.dto.CourseContractRequest;
import com.course.platform.domain.entity.CoursePlatform;
import com.course.platform.infra.persistence.mapper.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** 复用真Mapper资金测试夹具，验证协议、钱包和审计共同提交或共同回滚。 */
class CoursePrivateContractTransactionTest {
    CourseReservedWalletTransactionTest fixture;
    CoursePrivateContractService service;

    @BeforeEach void setup() throws Exception {
        fixture = new CourseReservedWalletTransactionTest(); fixture.setup();
        fixture.jdbc.execute("ALTER TABLE sys_user ADD COLUMN IF NOT EXISTS uid VARCHAR(36)");
        fixture.jdbc.execute("ALTER TABLE sys_user ADD COLUMN IF NOT EXISTS username VARCHAR(64)");
        fixture.jdbc.update("UPDATE sys_user SET uid='00000000-0000-4000-8000-000000000007',username='agent' WHERE id=7");
        fixture.jdbc.update("INSERT INTO sys_user(id,status,balance,total_recharge,uid,username) VALUES(1,1,0,0,'00000000-0000-4000-8000-000000000001','admin')");
        var products = mock(CoursePlatformMapper.class);
        for (long id : List.of(9L,11L)) { var product=new CoursePlatform(); product.setId(id); product.setStatus(1); product.setName("课程"+id); when(products.selectById(id)).thenReturn(product);when(products.selectByIdForUpdate(id)).thenReturn(product); }
        var permissions = mock(UserAuthorityMapper.class);
        when(permissions.findAuthoritiesByUserId(1L)).thenReturn(List.of(SecurityAuthorities.COURSE_PRIVATE_PRICE_MANAGE));
        // 历史Mapper复用真实资金服务实例，不用mock替代落库；查询用到的其他Mapper不参与资金写入。
        var histories = field(CoursePrivateContractHistoryMapper.class,"histories");
        var transfers = field(CourseReservedTransferMapper.class,"transfers");
        service = new CoursePrivateContractService(fixture.users,products,mock(PlatformCategoryMapper.class),fixture.contracts,fixture.wallets,fixture.ledgers,transfers,histories,permissions,fixture.service,mock(CoursePricingService.class),fixture.clock);
        auth();
    }
    // 夹具没有公开这两个Mapper，读取真实服务依赖避免修改其他任务的测试文件。
    <T> T field(Class<T> type,String name) throws Exception { var field=CourseReservedWalletService.class.getDeclaredField(name); field.setAccessible(true); return type.cast(field.get(fixture.service)); }
    void auth() { SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(1L,null,List.of(new SimpleGrantedAuthority(SecurityAuthorities.COURSE_PRIVATE_PRICE_MANAGE)))); }
    @AfterEach void cleanup() { fixture.cleanup(); }
    CourseContractRequest request(Long platform,Integer version,String state,String reason) {
        var value=new CourseContractRequest(); value.setUserUid("00000000-0000-4000-8000-000000000007"); value.setPlatformId(platform); value.setFixedPrice(new BigDecimal("7.20")); value.setMinInitialDeposit(new BigDecimal("100")); value.setVersion(version); value.setStatus(state); value.setReason(reason); return value;
    }
    void create(String reason) { fixture.tx.executeWithoutResult(tx -> service.create(request(11L,null,"ACTIVE",reason),1L)); }

    @Test void creationCommitsContractWalletAndAuditTogether() {
        create("创建协议");
        assertEquals(1,fixture.jdbc.queryForObject("SELECT COUNT(*) FROM course_private_contract WHERE user_id=7 AND platform_id=11 AND status='PENDING_ACTIVATION'",Integer.class));
        assertEquals(1,fixture.jdbc.queryForObject("SELECT COUNT(*) FROM course_reserved_wallet WHERE user_id=7 AND platform_id=11 AND balance=0",Integer.class));
        assertEquals(1,fixture.jdbc.queryForObject("SELECT COUNT(*) FROM course_private_contract_history WHERE reason='创建协议'",Integer.class));
        assertEquals(new BigDecimal("500.00"),fixture.balance("sys_user",7));
    }
    @Test void historyInsertFailureRollsBackNewContractAndWallet() {
        fixture.jdbc.execute("ALTER TABLE course_private_contract_history ADD CONSTRAINT reject_fixture_history CHECK(reason<>'阻止审计')");
        assertThrows(RuntimeException.class,()->create("阻止审计"));
        assertEquals(0,fixture.jdbc.queryForObject("SELECT COUNT(*) FROM course_private_contract WHERE platform_id=11",Integer.class));
        assertEquals(0,fixture.jdbc.queryForObject("SELECT COUNT(*) FROM course_reserved_wallet WHERE platform_id=11",Integer.class));
    }
    @Test void walletInsertFailureRollsBackNewContract() {
        fixture.jdbc.execute("ALTER TABLE course_reserved_wallet ADD CONSTRAINT reject_fixture_wallet CHECK(platform_id<>11)");
        assertThrows(RuntimeException.class,()->create("创建协议"));
        assertEquals(0,fixture.jdbc.queryForObject("SELECT COUNT(*) FROM course_private_contract WHERE platform_id=11",Integer.class));
        assertEquals(0,fixture.jdbc.queryForObject("SELECT COUNT(*) FROM course_private_contract_history",Integer.class));
    }
    @Test void historyFailureRollsBackConfigAndVersion() {
        fixture.jdbc.execute("ALTER TABLE course_private_contract_history ADD CONSTRAINT reject_fixture_history CHECK(reason<>'阻止审计')");
        assertThrows(RuntimeException.class,()->fixture.tx.executeWithoutResult(tx->service.update(3L,request(9L,0,"SUSPENDED","阻止审计"),1L)));
        assertEquals("PENDING_ACTIVATION",fixture.contracts.selectById(3L).getStatus()); assertEquals(0,fixture.contracts.selectById(3L).getVersion());
    }
    @Test void concurrentCreationHasOneContractOneWalletOneHistory() throws Exception {
        Runnable operation=()->{auth();try{create("并发创建");}finally{SecurityContextHolder.clearContext();}};
        var result=fixture.concurrent(operation,operation);
        assertEquals(1,result.stream().filter(BusinessException.class::isInstance).count());
        assertEquals(1,fixture.jdbc.queryForObject("SELECT COUNT(*) FROM course_private_contract WHERE platform_id=11",Integer.class));
        assertEquals(1,fixture.jdbc.queryForObject("SELECT COUNT(*) FROM course_reserved_wallet WHERE platform_id=11",Integer.class));
        assertEquals(1,fixture.jdbc.queryForObject("SELECT COUNT(*) FROM course_private_contract_history WHERE reason='并发创建'",Integer.class));
    }
}
