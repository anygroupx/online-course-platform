package com.course.platform.controller;

import com.course.platform.common.result.Result;
import com.course.platform.domain.dto.QueryCourseRequest;
import com.course.platform.domain.vo.CourseInfoResponse;
import com.course.platform.application.service.course.CourseQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

/**
 * 查课控制器
 * 
 * @author AI Assistant
 * @since 2025-01-17
 */
@Tag(name = "查课功能", description = "查询学生课程列表接口")
@RestController
@RequestMapping("/courses/query")
@RequiredArgsConstructor
public class CourseQueryController {

    private final CourseQueryService courseQueryService;

    /**
     * 查询课程
     */
    @Operation(summary = "查询课程", description = "根据学生账号密码查询课程列表")
    @PostMapping
    public Result<CourseInfoResponse> queryCourses(@Valid @RequestBody QueryCourseRequest request,
                                                     Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        CourseInfoResponse response = courseQueryService.queryCourses(request, userId);
        return Result.success(response);
    }
}

