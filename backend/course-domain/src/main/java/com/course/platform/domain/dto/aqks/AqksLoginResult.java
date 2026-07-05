package com.course.platform.domain.dto.aqks;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * AQKS登录结果DTO
 * 
 * 封装实验室安全平台登录返回的用户信息
 * 
 * @author AI Assistant
 * @since 2025-12-20
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AqksLoginResult {
    
    /**
     * 登录Token
     * 格式: {ID}-{Type}-{ClassID}-{Grade}-{SpecialtyID}-{DepartmentID}-{AdminLevel}-{TimeStamp}
     */
    private String token;
    
    /**
     * AQKS用户ID（用于刷时长接口调用）
     */
    private String userId;
    
    /**
     * 学号
     */
    private String userName;
    
    /**
     * 姓名
     */
    private String name;
    
    /**
     * 学院名称
     */
    private String departmentName;
    
    /**
     * 学院ID
     */
    private Integer departmentId;
    
    /**
     * 专业名称
     */
    private String specialtyName;
    
    /**
     * 专业ID
     */
    private Integer specialtyId;
    
    /**
     * 年级
     */
    private Integer grade;
    
    /**
     * 班级ID
     */
    private Integer classId;
    
    /**
     * 已学习时长（分钟）
     */
    private String studyTimes;
    
    /**
     * 要求最低学习时长（分钟）
     */
    private String minTimeMinute;
    
    /**
     * 登录Cookie - LoginUserInfo_SYSAQ_Server
     * URL编码格式
     */
    private String serverCookie;
    
    /**
     * 登录Cookie - LoginUserInfo_SYSAQ
     * 双重URL编码格式
     */
    private String doubleCookie;
    
    /**
     * 是否登录成功
     */
    private boolean success;
    
    /**
     * 错误信息（登录失败时）
     */
    private String errorMessage;
    
    /**
     * 计算学习进度百分比
     */
    public int calculateProgress() {
        try {
            int studied = Integer.parseInt(studyTimes);
            int required = Integer.parseInt(minTimeMinute);
            if (required <= 0) return 0;
            return Math.min(100, (studied * 100) / required);
        } catch (NumberFormatException e) {
            return 0;
        }
    }
    
    /**
     * 判断是否已达到学习要求
     */
    public boolean isStudyCompleted() {
        try {
            int studied = Integer.parseInt(studyTimes);
            int required = Integer.parseInt(minTimeMinute);
            return studied >= required;
        } catch (NumberFormatException e) {
            return false;
        }
    }
}
