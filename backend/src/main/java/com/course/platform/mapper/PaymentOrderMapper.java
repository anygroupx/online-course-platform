package com.course.platform.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.course.platform.domain.entity.PaymentOrder;
import org.apache.ibatis.annotations.Mapper;

/**
 * 支付订单Mapper接口
 * 
 * @author AI Assistant
 * @date 2025-11-26
 */
@Mapper
public interface PaymentOrderMapper extends BaseMapper<PaymentOrder> {
    
}
