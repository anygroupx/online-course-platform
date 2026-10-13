package com.course.platform.service.impl;
import com.course.platform.common.exception.BusinessException;
import com.course.platform.common.result.ResultCode;
import com.course.platform.domain.entity.CourseOrder;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 所有课程退款只使用成交快照，历史普通订单兼容空来源。 */
@Service
@RequiredArgsConstructor
public class CourseOrderPaymentService {
    private final AccountLedgerServiceImpl general;
    private final CourseReservedWalletService wallets;
    private final com.course.platform.infra.persistence.mapper.CourseOrderMapper orders;
    @Transactional(rollbackFor=Exception.class)
    public void refund(CourseOrder order, Long operatorId) {
        if ("COURSE_WALLET".equals(order.getPaymentSource())) {
            wallets.refund(order,operatorId);
        } else if (order.getPaymentSource()==null || "GENERAL".equals(order.getPaymentSource())) {
            general.credit(order.getUserId(),order.getAmount(),AccountLedgerServiceImpl.BIZ_REFUND,order.getOrderNo(),"课程订单退款回普通余额",false);
        } else throw new BusinessException(ResultCode.CONFLICT,"订单原支付来源异常，拒绝退款");
        if (orders.markRefunded(order.getId()) != 1) throw new BusinessException(ResultCode.CONFLICT,"退款状态记录失败");
    }
}
