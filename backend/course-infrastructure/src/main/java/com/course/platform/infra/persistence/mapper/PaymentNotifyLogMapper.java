package com.course.platform.infra.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.course.platform.domain.entity.PaymentNotifyLog;
import org.apache.ibatis.annotations.Mapper;

/**
 * 支付异步通知日志Mapper接口
 * 
 * @author AI Assistant
 * @date 2025-11-26
 */
@Mapper
public interface PaymentNotifyLogMapper extends BaseMapper<PaymentNotifyLog> {
    
}
