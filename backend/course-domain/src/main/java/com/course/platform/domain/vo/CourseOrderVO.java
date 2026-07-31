package com.course.platform.domain.vo;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 订单响应白名单 VO（仅供已通过订单权限校验的接口返回）
 */
@Data
@Builder
public class CourseOrderVO {
    private Long id;
    private String orderNo;
    private Long userId;
    private Long platformId;
    private String platformName;
    private String schoolName;
    private String studentName;
    private String studentAccount;
    /** 学生密码仅通过受保护的 HTTPS 订单接口传输。 */
    private String studentPassword;
    private Boolean hasStudentPassword;
    private String studentPhoneMasked;
    private String courseId;
    private String courseName;
    private LocalDateTime courseStartTime;
    private LocalDateTime courseEndTime;
    private LocalDateTime examStartTime;
    private LocalDateTime examEndTime;
    private Integer totalChapters;
    private Integer finishedChapters;
    private String progress;
    private BigDecimal amount;
    private Integer isFastMode;
    private Integer retryCount;
    private Integer orderStatus;
    private Integer dockStatus;
    private String loginStatus;
    private String remarks;
    private Integer isSelfOperated;
    private Integer countdownDuration;
    private LocalDateTime countdownStartTime;
    private LocalDateTime countdownEndTime;
    private Integer autoCompleteEnabled;
    private Integer examCountdownDuration;
    private LocalDateTime examCountdownStartTime;
    private LocalDateTime examCountdownEndTime;
    private Integer examAutoCompleteEnabled;
    private String createIp;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
