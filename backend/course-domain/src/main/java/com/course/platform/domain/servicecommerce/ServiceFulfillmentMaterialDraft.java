package com.course.platform.domain.servicecommerce;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;
import lombok.ToString;

import java.time.LocalDateTime;

@Data
@TableName("service_fulfillment_material_draft")
public class ServiceFulfillmentMaterialDraft {
    @TableId(type = IdType.INPUT) private String id;
    private Long userId;
    private Long productId;
    private Long productVersion;
    private String assetType;
    @JsonIgnore @ToString.Exclude
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String contentEncrypted;
    private String mimeType;
    private Integer width;
    private Integer height;
    private Integer byteSize;
    private String sha256;
    private String state;
    private Long version;
    private LocalDateTime expiresAt;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    private LocalDateTime purgedAt;
}
