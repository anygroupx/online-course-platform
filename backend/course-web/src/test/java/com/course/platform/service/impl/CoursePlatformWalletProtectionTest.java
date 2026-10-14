package com.course.platform.service.impl;
import com.course.platform.domain.entity.CoursePlatform;
import com.course.platform.infra.persistence.mapper.*;
import com.course.platform.infra.docking.PlatformDockingStrategyFactory;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

/** 已有协议与资金绑定的商品只能下架，不能物理删除后按名称重新绑定。 */
class CoursePlatformWalletProtectionTest {
    @Test void deletionOfProductWithOperationsPlansIsRejectedEvenBeforeEnrollment(){
        var products=mock(CoursePlatformMapper.class);var contracts=mock(CoursePrivateContractMapper.class);var plans=mock(CoursePrivatePlanMapper.class);var product=new CoursePlatform();product.setId(9L);
        when(products.selectByIdForUpdate(9L)).thenReturn(product);when(plans.countByPlatform(9L)).thenReturn(1);
        var service=new CoursePlatformServiceImpl(products,mock(ApiProviderMapper.class),new PlatformDockingStrategyFactory(List.of()),contracts,plans);
        assertThrows(com.course.platform.common.exception.BusinessException.class,()->service.deletePlatform(9L));verify(products,never()).deleteById(9L);
    }
    @Test void deletionOfBoundProductIsRejected(){
        var products=mock(CoursePlatformMapper.class);var contracts=mock(CoursePrivateContractMapper.class);var product=new CoursePlatform();product.setId(9L);
        when(products.selectByIdForUpdate(9L)).thenReturn(product);when(contracts.countByPlatform(9L)).thenReturn(1);
        var service=new CoursePlatformServiceImpl(products,mock(ApiProviderMapper.class),new PlatformDockingStrategyFactory(List.of()),contracts,mock(CoursePrivatePlanMapper.class));
        assertThrows(com.course.platform.common.exception.BusinessException.class,()->service.deletePlatform(9L));verify(products,never()).deleteById(9L);
    }
}
