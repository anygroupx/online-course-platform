package com.course.platform.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.course.platform.common.result.Result;
import com.course.platform.security.SecurityUtils;
import com.course.platform.service.impl.CoursePrivatePlanService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

/** 本人可见套餐只返回发布规则，不暴露内部成本依据或审核身份。 */
@RestController
@RequestMapping("/course-private-operations")
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
public class CoursePrivatePlanController {
    private final CoursePrivatePlanService service;
    @GetMapping("/plans")
    public Result<IPage<Map<String,Object>>> plans(@RequestParam(defaultValue="1") int page,@RequestParam(defaultValue="20") int pageSize,@RequestParam(required=false) Long platformId){return Result.success(service.visiblePlans(SecurityUtils.getCurrentUserId(),page,pageSize,platformId));}
}
