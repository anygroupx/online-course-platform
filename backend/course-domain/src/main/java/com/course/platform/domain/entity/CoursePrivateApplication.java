package com.course.platform.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 公开套餐开通续期升级和重新签约申请。业务时间由服务显式使用统一Clock截断到秒。 */
@Data
@TableName("course_private_application")
public class CoursePrivateApplication implements Serializable {
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private Long platformId;

    private Long planId;

    private Long versionId;

    private Long contractId;

    private Long sourceTermId;
    /** 旧协议历史资金来源不明时，转换申请以此不可变水位限定新的真实转入。 */
    @com.baomidou.mybatisplus.annotation.TableField(updateStrategy=com.baomidou.mybatisplus.annotation.FieldStrategy.NEVER)
    private Long fundingStartLedgerId;

    private String applicationType;

    /** 原始申请原因与审批意见独立保留，不被后续审核覆盖。 */
    @com.baomidou.mybatisplus.annotation.TableField(updateStrategy=com.baomidou.mybatisplus.annotation.FieldStrategy.NEVER)
    private String applicationReason;

    private String effectivePolicy;

    private Boolean acknowledgeLegacyChange;

    private String status;

    private LocalDateTime submittedAt;
    /** 原提交主体由会话记录；后台指定不能冒充代理本人对旧权益变更的确认。 */
    @com.baomidou.mybatisplus.annotation.TableField(updateStrategy=com.baomidou.mybatisplus.annotation.FieldStrategy.NEVER)
    private Long submittedBy;

    private Long reviewedBy;

    private LocalDateTime reviewedAt;

    private String reviewReason;

    private String requestKey;

    private String operationFingerprint;

    private Integer version;
}
