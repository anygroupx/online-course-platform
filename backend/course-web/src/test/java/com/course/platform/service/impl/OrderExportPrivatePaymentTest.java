package com.course.platform.service.impl;
import com.course.platform.domain.entity.CourseOrder;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

/** 财务导出仅增加显式可选白名单，不改变旧格式或泄漏口令。 */
class OrderExportPrivatePaymentTest {
    @Test void financialFormatIncludesActualSourcesAndRecordedRefundOnly(){
        var order=new CourseOrder();order.setOrderNo("order");order.setStudentAccount("student");order.setStudentPassword("绝不导出此口令");order.setAmount(new BigDecimal("7.20"));order.setPriceSource("PRIVATE");order.setPaymentSource("COURSE_WALLET");order.setRefundedAt(LocalDateTime.now());
        String text=new OrderExportServiceImpl().exportAsTxt(List.of(order),5);assertTrue(text.contains("7.20"));assertTrue(text.contains("专属密价"));assertTrue(text.contains("课程专户"));assertTrue(text.contains("已退款"));assertFalse(text.contains(order.getStudentPassword()));
    }
}
