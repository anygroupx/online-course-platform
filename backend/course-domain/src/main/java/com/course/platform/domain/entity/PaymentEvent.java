package com.course.platform.domain.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@TableName("payment_event")
public class PaymentEvent implements Serializable {
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;
    private String orderNo;
    private String eventType;
    private String providerEventId;
    private String payload;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
