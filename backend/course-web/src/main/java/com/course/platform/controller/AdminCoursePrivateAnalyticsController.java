package com.course.platform.controller;

import com.course.platform.common.result.Result;
import com.course.platform.security.SecurityUtils;
import com.course.platform.service.impl.CoursePrivateAnalyticsService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

/** 聚合分析只向独立分析权限开放，不返回账户拥有者或原始资金流水。 */
@RestController
@RequestMapping("/admin/course-private-operations/analytics")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('course-private-analytics:read')")
public class AdminCoursePrivateAnalyticsController {
    private final CoursePrivateAnalyticsService service;
    @GetMapping public Result<Map<String,Object>> summary(@RequestParam(required=false) Long platformId,@RequestParam(required=false) Long planId){return Result.success(service.summary(SecurityUtils.getCurrentUserId(),platformId,planId));}
}
