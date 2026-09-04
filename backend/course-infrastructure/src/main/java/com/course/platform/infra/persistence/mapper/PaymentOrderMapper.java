package com.course.platform.infra.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.course.platform.domain.entity.PaymentOrder;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;

/**
 * 支付订单Mapper接口
 */
@Mapper
public interface PaymentOrderMapper extends BaseMapper<PaymentOrder> {

    @Select("SELECT * FROM payment_order WHERE order_no = #{orderNo} LIMIT 1")
    PaymentOrder selectByOrderNo(@Param("orderNo") String orderNo);

    @Select("SELECT * FROM payment_order WHERE order_no = #{orderNo} LIMIT 1 FOR UPDATE")
    PaymentOrder selectByOrderNoForUpdate(@Param("orderNo") String orderNo);

    /**
     * 条件抢占支付成功处理权，避免回调与主动同步重复入账
     */
    @Update("""
            UPDATE payment_order
            SET status = 'PAID',
                alipay_trade_no = #{alipayTradeNo},
                buyer_logon_id = #{buyerLogonId},
                buyer_user_id = #{buyerUserId},
                paid_time = #{paidTime},
                update_time = NOW()
            WHERE order_no = #{orderNo}
              AND status = 'PENDING'
            """)
    int markPaidIfPending(@Param("orderNo") String orderNo,
                          @Param("alipayTradeNo") String alipayTradeNo,
                          @Param("buyerLogonId") String buyerLogonId,
                          @Param("buyerUserId") String buyerUserId,
                          @Param("paidTime") LocalDateTime paidTime);

    /** 原子抢占退款执行权；并发请求最多一个能调用支付渠道。 */
    @Update("""
            UPDATE payment_order
            SET status = 'REFUNDING',
                refund_reason = #{refundReason},
                update_time = NOW()
            WHERE order_no = #{orderNo}
              AND status = 'PAID'
            """)
    int markRefundingIfPaid(@Param("orderNo") String orderNo,
                            @Param("refundReason") String refundReason);

    @Update("""
            UPDATE payment_order
            SET status = 'REFUNDED',
                refund_amount = amount,
                refund_reason = #{refundReason},
                refund_time = #{refundTime},
                update_time = NOW()
            WHERE order_no = #{orderNo}
              AND status = 'REFUNDING'
            """)
    int markRefundedIfRefunding(@Param("orderNo") String orderNo,
                                @Param("refundReason") String refundReason,
                                @Param("refundTime") LocalDateTime refundTime);

    @Update("""
            UPDATE payment_order
            SET status = 'CLOSED', close_time = #{closeTime}, update_time = NOW()
            WHERE order_no = #{orderNo} AND status = 'PENDING'
            """)
    int closeIfPending(@Param("orderNo") String orderNo,
                       @Param("closeTime") LocalDateTime closeTime);
}
