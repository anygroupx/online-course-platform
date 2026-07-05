package com.course.platform.domain.dto.aqks;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * AQKS考试信息DTO
 * 
 * 封装实验室安全平台的考试信息
 * 
 * @author AI Assistant
 * @since 2025-12-20
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AqksExamInfo {
    
    /**
     * 课程ID
     */
    private String courseId;
    
    /**
     * 课程名称
     */
    private String courseName;
    
    /**
     * 考试名称
     */
    private String testName;
    
    /**
     * 考试ID（用于获取试卷详情）
     */
    private Integer testId;
    
    /**
     * 试卷ID
     */
    private Integer testPaperId;
    
    /**
     * 是否已通过
     */
    private Boolean isPassed;
    
    /**
     * 是否已参加考试
     */
    private Boolean isJoined;
    
    /**
     * 考试分数
     */
    private Integer score;
    
    /**
     * 及格线
     */
    private Integer borderLine;
    
    /**
     * 剩余考试次数
     */
    private Integer unusedTime;
    
    /**
     * 考试次数限制
     */
    private Integer testCounts;
    
    // ========== 完整考试详情字段（通过TestID和CourseID获取）==========
    
    /**
     * 学院名称
     */
    private String departmentName;
    
    /**
     * 专业名称
     */
    private String specialtyName;
    
    /**
     * 年级
     */
    private Integer grade;
    
    /**
     * 班级名称
     */
    private String className;
    
    /**
     * 学生姓名
     */
    private String menderName;
    
    /**
     * 学号
     */
    private String menderCode;
    
    /**
     * 试卷名称
     */
    private String testPaperName;
    
    /**
     * 考试结束时间
     */
    private String endTime;
    
    /**
     * 在线学时
     */
    private String onlineHours;
    
    /**
     * 是否已提交
     */
    private Boolean isSubmit;
    
    /**
     * 证书编号
     */
    private String certificateId;
    
    /**
     * 更新时间
     */
    private String updateTime;
    
    /**
     * 判断是否需要参加考试
     */
    public boolean needExam() {
        // 未通过且还有剩余次数
        return (isPassed == null || !isPassed) && (unusedTime == null || unusedTime > 0);
    }
    
    /**
     * 生成考试详情备注信息
     * 
     * @return 格式化的考试详情字符串
     */
    public String toRemarkString() {
        StringBuilder sb = new StringBuilder();
        sb.append("【AQKS考试成绩】\n");
        
        if (menderName != null) {
            sb.append("学生姓名: ").append(menderName).append("\n");
        }
        if (menderCode != null) {
            sb.append("学号: ").append(menderCode).append("\n");
        }
        if (departmentName != null) {
            sb.append("学院: ").append(departmentName).append("\n");
        }
        if (specialtyName != null) {
            sb.append("专业: ").append(specialtyName).append("\n");
        }
        if (className != null) {
            sb.append("班级: ").append(className).append("\n");
        }
        if (courseName != null) {
            sb.append("课程: ").append(courseName).append("\n");
        }
        if (testName != null) {
            sb.append("考试: ").append(testName).append("\n");
        }
        
        sb.append("分数: ").append(score != null ? score : 0);
        sb.append(" / 及格线: ").append(borderLine != null ? borderLine : 0).append("\n");
        sb.append("是否通过: ").append(Boolean.TRUE.equals(isPassed) ? "是" : "否").append("\n");
        
        if (certificateId != null && !certificateId.isEmpty()) {
            sb.append("证书编号: ").append(certificateId).append("\n");
        }
        if (endTime != null) {
            sb.append("考试时间: ").append(endTime).append("\n");
        }
        
        return sb.toString();
    }
}
