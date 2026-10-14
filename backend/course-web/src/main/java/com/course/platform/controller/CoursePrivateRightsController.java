package com.course.platform.controller;

import com.course.platform.common.result.Result;
import com.course.platform.security.SecurityUtils;
import com.course.platform.service.impl.CoursePrivateRightsReadService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

/** 本人权益只认当前会话，查询参数不能指定其他拥有者。 */
@RestController
@RequestMapping("/course-private-operations")
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
public class CoursePrivateRightsController {
    private final CoursePrivateRightsReadService service;
    @GetMapping("/rights") public Result<Map<String,Object>> rights(@RequestParam(defaultValue="1") int page,@RequestParam(defaultValue="20") int pageSize,@RequestParam(required=false) Long platformId,@RequestParam(required=false) String status){return Result.success(service.contracts(SecurityUtils.getCurrentUserId(),page,pageSize,platformId,status,null));}
    @GetMapping("/terms/{id}/orders") public Result<Map<String,Object>> orders(@PathVariable Long id,@RequestParam(defaultValue="1") int page,@RequestParam(defaultValue="20") int pageSize){return Result.success(service.orders(SecurityUtils.getCurrentUserId(),id,page,pageSize));}
    @GetMapping("/terms/{id}") public Result<Map<String,Object>> term(@PathVariable Long id){return Result.success(service.detail(SecurityUtils.getCurrentUserId(),id));}
}
