package com.course.platform.domain.servicecommerce;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;
import lombok.ToString;

import java.time.LocalDateTime;

@Data
@TableName("service_order_fulfillment_asset")
public class ServiceOrderFulfillmentAsset {
    @TableId(type = IdType.INPUT) private String id;
    private String orderId;
    private String assetType;
    @JsonIgnore @ToString.Exclude
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String contentEncrypted;
    private String mimeType;
    private Integer width;
    private Integer height;
    private Integer byteSize;
    private String sha256;
    private Long version;
    private LocalDateTime createTime;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private LocalDateTime purgedAt;
}
