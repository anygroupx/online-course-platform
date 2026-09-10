package com.course.platform.application.service.orderreceipt;

import com.course.platform.domain.entity.*;
import com.course.platform.domain.orderreceipt.OrderReceiptTypes.Verified;

public interface OrderReceiptGateway {
    Verified verify(ApiProvider provider, CoursePlatform platform, CourseOrder order, String receiptId);
}
