package com.course.platform.domain.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 查课响应VO
 * 
 * @author AI Assistant
 * @since 2025-01-17
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CourseInfoResponse {

    /**
     * 学生姓名
     */
    private String studentName;

    /**
     * 学生账号
     */
    private String studentAccount;

    /**
     * 学校名称
     */
    private String schoolName;

    /**
     * 课程列表
     */
    private List<CourseItem> courses;

    /**
     * 查询消息
     */
    private String message;

    /**
     * 课程项
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CourseItem {
        
        /**
         * 课程ID
         */
        private String id;

        /**
         * 课程名称
         */
        private String name;

        /**
         * 课程描述
         */
        private String description;

        /**
         * 课程结束时间
         */
        private String endTime;

        /**
         * 是否已选
         */
        private Boolean selected;
    }
}

