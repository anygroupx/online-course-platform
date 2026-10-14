package com.course.platform.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.course.platform.common.result.Result;
import com.course.platform.domain.dto.AdminCoursePrivateApplicationRequest;
import com.course.platform.security.SecurityUtils;
import com.course.platform.service.impl.CoursePrivateApplicationService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

/** 权益分配及审核权限与套餐发布、资金释放分开，审批主体只来自当前会话。 */
@RestController
@RequestMapping("/admin/course-private-operations/applications")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('course-private-contract:review')")
public class AdminCoursePrivateApplicationController {
    private final CoursePrivateApplicationService service;
    @GetMapping public Result<IPage<Map<String,Object>>> applications(@RequestParam(defaultValue="1") int page,@RequestParam(defaultValue="20") int pageSize,@RequestParam(required=false) Long platformId,@RequestParam(required=false) String status,@RequestParam(required=false) String userUid){return Result.success(service.page(null,page,pageSize,platformId,status,userUid));}
    @PostMapping public Result<Map<String,Object>> assign(@Valid @RequestBody AdminCoursePrivateApplicationRequest request){return Result.success(service.assign(request,SecurityUtils.getCurrentUserId()));}
    @PostMapping("/{id}/approve") public Result<Map<String,Object>> approve(@PathVariable Long id,@Valid @RequestBody ReviewRequest request){return Result.success(service.review(id,request.version(),SecurityUtils.getCurrentUserId(),true,request.reviewReason()));}
    @PostMapping("/{id}/reject") public Result<Map<String,Object>> reject(@PathVariable Long id,@Valid @RequestBody ReviewRequest request){return Result.success(service.review(id,request.version(),SecurityUtils.getCurrentUserId(),false,request.reviewReason()));}
    @PostMapping("/{id}/grant-renewal")
    @PreAuthorize("hasAuthority('course-private-contract:review') and hasAuthority('course-private-term:adjust') and hasAuthority('course-private-settlement:execute')")
    public Result<Map<String,Object>> grantRenewal(@PathVariable Long id,@Valid @RequestBody ReviewRequest request){return Result.success(service.grantRenewal(id,request.version(),SecurityUtils.getCurrentUserId(),request.reviewReason()));}
    public record ReviewRequest(@NotNull @Min(0) Integer version,@NotBlank @Size(max=255) String reviewReason){}
}
