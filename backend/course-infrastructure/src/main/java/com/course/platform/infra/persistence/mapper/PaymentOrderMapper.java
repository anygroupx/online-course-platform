package com.course.platform.infra.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.course.platform.domain.entity.PaymentOrder;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;

/**
 * 支付订单Mapper接口
 */
@Mapper
public interface PaymentOrderMapper extends BaseMapper<PaymentOrder> {

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
}
