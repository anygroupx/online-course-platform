package com.course.platform.domain.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 课程订单实体类
 *
 * @author AI Assistant
 * @since 2025-01-17
 */
@Data
@TableName("course_order")
public class CourseOrder implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 下单时确定的支付快照，退款不得按当前协议重新推导。 */
    @TableField(updateStrategy = FieldStrategy.NEVER)
    private String priceSource;
    @TableField(updateStrategy = FieldStrategy.NEVER)
    private String paymentSource;
    @TableField(updateStrategy = FieldStrategy.NEVER)
    private Long privateContractId;
    @TableField(updateStrategy = FieldStrategy.NEVER)
    private Long walletId;
    @TableField(updateStrategy = FieldStrategy.NEVER)
    private Integer contractVersion;
    /** 成交周期快照不可随套餐修改，退款和消费结算必须追溯原周期。 */
    @TableField(updateStrategy = FieldStrategy.NEVER)
    private Long privateTermId;
    @TableField(updateStrategy = FieldStrategy.NEVER)
    private Integer privateTermVersion;
    @TableField(updateStrategy = FieldStrategy.NEVER)
    private Long privatePlanVersionId;
    @TableField(updateStrategy = FieldStrategy.NEVER)
    private String privateOperationsMode;
    /** 只由可信状态转换SQL写入，普通实体更新不能伪造完成事实。 */
    @TableField(updateStrategy = FieldStrategy.NEVER)
    private LocalDateTime fulfilledAt;
    @TableField(updateStrategy = FieldStrategy.NEVER)
    private String fulfillmentSource;
    /** 上游确认时刻只是完成的时间上界，实际完成时间未知时必须单独保存。 */
    @TableField(updateStrategy = FieldStrategy.NEVER)
    private LocalDateTime completionConfirmedAt;
    @TableField(updateStrategy = FieldStrategy.NEVER)
    private String completionConfirmationSource;
    @TableField(updateStrategy = FieldStrategy.NEVER)
    private String clientRequestId;
    @TableField(updateStrategy = FieldStrategy.NEVER)
    private String requestFingerprint;
    /** 只有原支付来源退款事务成功后才记录；历史未知状态保持空。 */
    @TableField(updateStrategy = FieldStrategy.NEVER)
    private LocalDateTime refundedAt;

    /** 金融订单只允许逻辑归档，禁止物理删除审计证据。 */
    @TableLogic(value = "0", delval = "1")
    @TableField("is_deleted")
    private Integer isDeleted;

    /**
     * 订单ID
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 订单编号
     */
    @TableField(value = "order_no", updateStrategy = FieldStrategy.NEVER)
    private String orderNo;

    /**
     * 用户ID
     */
    @TableField(value = "user_id", updateStrategy = FieldStrategy.NEVER)
    private Long userId;

    /**
     * 课程平台ID
     */
    @TableField(value = "platform_id", updateStrategy = FieldStrategy.NEVER)
    private Long platformId;

    /**
     * 平台名称
     */
    @TableField("platform_name")
    private String platformName;

    /**
     * 对接接口ID
     */
    @TableField("api_provider_id")
    private Long apiProviderId;

    /**
     * 第三方订单ID
     */
    @TableField("third_order_id")
    private String thirdOrderId;

    // ========== 学生信息 ==========

    /**
     * 学校名称
     */
    @TableField("school_name")
    private String schoolName;

    /**
     * 学生姓名
     */
    @TableField("student_name")
    private String studentName;

    /**
     * 学生账号
     */
    @TableField("student_account")
    private String studentAccount;

    /**
     * 学生密码（实体禁止直接序列化，由授权后的订单 VO 显式返回）
     */
    @TableField("student_password")
    @com.fasterxml.jackson.annotation.JsonIgnore
    private String studentPassword;

    /**
     * 学生手机号
     */
    @TableField("student_phone")
    private String studentPhone;

    // ========== 课程信息 ==========

    /**
     * 课程ID
     */
    @TableField("course_id")
    private String courseId;

    /**
     * 课程名称
     */
    @TableField("course_name")
    private String courseName;

    /**
     * 课程开始时间
     */
    @TableField("course_start_time")
    private LocalDateTime courseStartTime;

    /**
     * 课程结束时间
     */
    @TableField("course_end_time")
    private LocalDateTime courseEndTime;

    /**
     * 考试开始时间
     */
    @TableField("exam_start_time")
    private LocalDateTime examStartTime;

    /**
     * 考试结束时间
     */
    @TableField("exam_end_time")
    private LocalDateTime examEndTime;

    // ========== 进度信息 ==========

    /**
     * 总章节数
     */
    @TableField("total_chapters")
    private Integer totalChapters;

    /**
     * 已完成章节数
     */
    @TableField("finished_chapters")
    private Integer finishedChapters;

    /**
     * 完成进度
     */
    @TableField("progress")
    private String progress;

    // ========== 订单信息 ==========

    /**
     * 订单金额
     */
    @TableField(value = "amount", updateStrategy = FieldStrategy.NEVER)
    private BigDecimal amount;

    /**
     * 对接参数
     */
    @TableField("dock_param")
    private String dockParam;

    /**
     * 是否秒刷：0-否 1-是
     */
    @TableField("is_fast_mode")
    private Integer isFastMode;

    /**
     * 补刷次数
     */
    @TableField("retry_count")
    private Integer retryCount;

    /**
     * 订单状态：0-待处理 1-进行中 2-已完成 3-已取消 4-失败 5-待考试 6-考试中 7-考试完成 8-等待退款
     */
    @TableField("order_status")
    private Integer orderStatus;

    /**
     * 对接状态：0-待对接 1-对接成功 2-对接失败 3-重复订单 4-已取消
     */
    @TableField("dock_status")
    private Integer dockStatus;

    /**
     * 登录状态
     */
    @TableField("login_status")
    private String loginStatus;

    /**
     * 备注
     */
    @TableField("remarks")
    private String remarks;

    // ========== 自营订单倒计时相关字段 ==========

    /**
     * 是否自营订单：0-否 1-是
     */
    @TableField("is_self_operated")
    private Integer isSelfOperated;

    /**
     * 倒计时时长（分钟）
     */
    @TableField("countdown_duration")
    private Integer countdownDuration;

    /**
     * 倒计时开始时间
     */
    @TableField("countdown_start_time")
    private LocalDateTime countdownStartTime;

    /**
     * 倒计时结束时间
     */
    @TableField("countdown_end_time")
    private LocalDateTime countdownEndTime;

    /**
     * 是否启用自动完成：0-否 1-是
     */
    @TableField("auto_complete_enabled")
    private Integer autoCompleteEnabled;

    // ========== 考试倒计时相关字段 ==========

    /**
     * 考试倒计时时长（分钟）
     */
    @TableField("exam_countdown_duration")
    private Integer examCountdownDuration;

    /**
     * 考试倒计时开始时间
     */
    @TableField("exam_countdown_start_time")
    private LocalDateTime examCountdownStartTime;

    /**
     * 考试倒计时结束时间
     */
    @TableField("exam_countdown_end_time")
    private LocalDateTime examCountdownEndTime;

    /**
     * 是否启用考试自动完成：0-否 1-是
     */
    @TableField("exam_auto_complete_enabled")
    private Integer examAutoCompleteEnabled;

    /**
     * 下单IP
     */
    @TableField("create_ip")
    private String createIp;

    /**
     * 创建时间
     */
    @TableField(value = "create_time", fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    @TableField(value = "update_time", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
