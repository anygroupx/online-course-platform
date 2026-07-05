package com.course.platform.application.service.course;

import com.course.platform.domain.dto.QueryCourseRequest;
import com.course.platform.domain.vo.CourseInfoResponse;

/**
 * 查课服务接口
 * 
 * @author AI Assistant
 * @since 2025-01-17
 */
public interface CourseQueryService {

    /**
     * 查询课程列表
     * 
     * @param request 查课请求
     * @param userId 用户ID
     * @return 课程信息
     */
    CourseInfoResponse queryCourses(QueryCourseRequest request, Long userId);
}

