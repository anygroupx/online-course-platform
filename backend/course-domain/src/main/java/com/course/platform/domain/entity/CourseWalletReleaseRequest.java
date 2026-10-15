package com.course.platform.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 申请不冻结本金，财务执行后才关联成功转回。业务时间由服务显式使用统一Clock截断到秒。 */
@Data
@TableName("course_wallet_release_request")
public class CourseWalletReleaseRequest implements Serializable {
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long walletId;

    private Long userId;

    private BigDecimal amount;

    private String status;

    private String reason;

    private String requestKey;

    private String operationFingerprint;

    private Long reviewedBy;

    private LocalDateTime reviewedAt;

    private String reviewReason;

    private Long executedTransferId;

    private Integer version;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
