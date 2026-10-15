package com.course.platform.service.impl;

import org.junit.jupiter.api.*;
import com.course.platform.common.exception.BusinessException;
import static org.junit.jupiter.api.Assertions.*;

/** 周期订单明细只展示本人原关联，不返回学生口令或其他代理账户。 */
class CoursePrivateTermOrdersReadTest {
    CoursePrivateRightsReadTransactionTest f;
    @BeforeEach void setup() throws Exception {f=new CoursePrivateRightsReadTransactionTest();f.setup();org.springframework.test.util.ReflectionTestUtils.setField(f.service,"associatedOrders",f.f.f.f.termOrders);}
    @AfterEach void clear(){f.clear();}
    @Test void liveOrderPageKeepsAmountAndTrustedQualificationWithoutCredentials(){f.f.qualify();f.auth(7);var page=f.service.orders(7L,f.f.sourceId,1,1);assertEquals(2L,page.get("total"));var row=(java.util.Map<?,?>)((java.util.List<?>)page.get("records")).get(0);assertEquals("7.20",row.get("orderedAmount"));assertEquals("QUALIFIED",row.get("qualificationStatus"));assertNotNull(row.get("orderNo"));assertFalse(row.containsKey("userId"));assertFalse(row.containsKey("studentPassword"));assertEquals(1,((java.util.List<?>)f.service.orders(7L,f.f.sourceId,2,1).get("records")).size());}
    @Test void foreignOwnerAndInvalidPagingCannotReadOrders(){f.auth(8);assertThrows(BusinessException.class,()->f.service.orders(8L,f.f.sourceId,1,20));assertThrows(BusinessException.class,()->f.service.orders(null,f.f.sourceId,0,20));}
}
