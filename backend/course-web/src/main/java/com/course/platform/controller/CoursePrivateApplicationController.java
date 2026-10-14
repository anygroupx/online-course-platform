package com.course.platform.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.course.platform.common.result.Result;
import com.course.platform.domain.dto.CoursePrivateApplicationRequest;
import com.course.platform.security.SecurityUtils;
import com.course.platform.service.impl.CoursePrivateApplicationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

/** 本人申请只作用于会话拥有者，申请不能直接指定权益价格或财务结果。 */
@RestController
@RequestMapping("/course-private-operations/applications")
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
public class CoursePrivateApplicationController {
    private final CoursePrivateApplicationService service;
    @GetMapping public Result<IPage<Map<String,Object>>> applications(@RequestParam(defaultValue="1") int page,@RequestParam(defaultValue="20") int pageSize,@RequestParam(required=false) Long platformId,@RequestParam(required=false) String status){return Result.success(service.page(SecurityUtils.getCurrentUserId(),page,pageSize,platformId,status,null));}
    @PostMapping public Result<Map<String,Object>> submit(@Valid @RequestBody CoursePrivateApplicationRequest request){return Result.success(service.submit(SecurityUtils.getCurrentUserId(),request));}
    @PostMapping("/{id}/cancel") public Result<Map<String,Object>> cancel(@PathVariable Long id){return Result.success(service.cancel(SecurityUtils.getCurrentUserId(),id));}
}
