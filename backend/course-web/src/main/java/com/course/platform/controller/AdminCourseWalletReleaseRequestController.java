package com.course.platform.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.course.platform.common.result.Result;
import com.course.platform.domain.entity.CourseWalletReleaseRequest;
import com.course.platform.security.SecurityUtils;
import com.course.platform.service.impl.CourseWalletReleaseRequestService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/** 财务审核和真实转回是独立权限；批准动作必须同时具备两项当前权限。 */
@RestController
@RequestMapping("/admin/course-private-operations/releases")
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
public class AdminCourseWalletReleaseRequestController {
    private final CourseWalletReleaseRequestService service;
    @GetMapping
    @PreAuthorize("hasAnyAuthority('course-wallet-release:review','course-wallet:audit')")
    public Result<IPage<java.util.Map<String,Object>>> requests(@RequestParam(defaultValue="1") int page,@RequestParam(defaultValue="20") int pageSize,@RequestParam(required=false) String status){return Result.success(service.page(null,page,pageSize,status).convert(service::administrativeView));}
    @PostMapping("/{id}/approve")
    @PreAuthorize("hasAuthority('course-wallet-release:review') and hasAuthority('course-wallet:release')")
    public Result<java.util.Map<String,Object>> approve(@PathVariable Long id,@Valid @RequestBody ReviewRequest request){return Result.success(service.administrativeView(service.review(id,request.version(),SecurityUtils.getCurrentUserId(),true,request.reviewReason())));}
    @PostMapping("/{id}/reject")
    @PreAuthorize("hasAuthority('course-wallet-release:review')")
    public Result<java.util.Map<String,Object>> reject(@PathVariable Long id,@Valid @RequestBody ReviewRequest request){return Result.success(service.administrativeView(service.review(id,request.version(),SecurityUtils.getCurrentUserId(),false,request.reviewReason())));}
    public record ReviewRequest(@NotNull @Min(0) Integer version,@NotBlank @Size(max=255) String reviewReason){}
}
