package com.course.platform.application.service.orderreceipt;

import com.course.platform.domain.orderreceipt.OrderReceiptTypes.*;

public interface OrderReceiptRecoveryService {
    java.util.List<View> recent(long orderId);
    View preview(long orderId, PreviewForm form);
    View get(long orderId, String requestId);
    View confirm(long orderId, String requestId, ConfirmForm form);
}
