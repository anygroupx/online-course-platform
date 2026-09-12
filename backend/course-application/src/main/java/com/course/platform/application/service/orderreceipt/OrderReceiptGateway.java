package com.course.platform.application.service.orderreceipt;

import com.course.platform.domain.entity.*;
import com.course.platform.domain.orderreceipt.OrderReceiptTypes.Verified;

public interface OrderReceiptGateway {
    java.util.List<Verified> findCandidates(ApiProvider provider, CoursePlatform platform, CourseOrder order);
    Verified verify(ApiProvider provider, CoursePlatform platform, CourseOrder order, String receiptId);
}
