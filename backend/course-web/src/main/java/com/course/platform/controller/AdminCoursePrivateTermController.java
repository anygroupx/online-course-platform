package com.course.platform.controller;

import com.course.platform.common.result.Result;
import com.course.platform.security.SecurityUtils;
import com.course.platform.service.impl.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

/** 周期读取与全局状态调整使用独立权限，恢复不会增加购买天数。 */
@RestController
@RequestMapping("/admin/course-private-operations")
@RequiredArgsConstructor
public class AdminCoursePrivateTermController {
    private final CoursePrivateRightsReadService reads;
    private final CoursePrivateLifecycleControlService lifecycle;
    @GetMapping("/contracts") @PreAuthorize("hasAuthority('course-private-term:read')") public Result<Map<String,Object>> contracts(@RequestParam(defaultValue="1") int page,@RequestParam(defaultValue="20") int pageSize,@RequestParam(required=false) Long platformId,@RequestParam(required=false) String status,@RequestParam(required=false) String userUid){return Result.success(reads.contracts(null,page,pageSize,platformId,status,userUid));}
    @GetMapping("/terms/{id}/orders") @PreAuthorize("hasAuthority('course-private-term:read')") public Result<Map<String,Object>> orders(@PathVariable Long id,@RequestParam(defaultValue="1") int page,@RequestParam(defaultValue="20") int pageSize){return Result.success(reads.orders(null,id,page,pageSize));}
    @GetMapping("/terms/{id}") @PreAuthorize("hasAuthority('course-private-term:read')") public Result<Map<String,Object>> term(@PathVariable Long id){return Result.success(reads.detail(null,id));}
    @GetMapping("/terms") @PreAuthorize("hasAuthority('course-private-term:read')") public Result<Map<String,Object>> periods(@RequestParam(defaultValue="1") int page,@RequestParam(defaultValue="20") int pageSize,@RequestParam(required=false) Long platformId,@RequestParam(required=false) String status,@RequestParam(required=false) String userUid){return Result.success(reads.periods(page,pageSize,platformId,status,userUid));}
    @PostMapping("/contracts/{id}/state") @PreAuthorize("hasAuthority('course-private-term:adjust')") public Result<CoursePrivateLifecycleControlService.StateResult> state(@PathVariable Long id,@Valid @RequestBody StateRequest request){return Result.success(lifecycle.change(id,request.expectedVersion(),SecurityUtils.getCurrentUserId(),request.action(),request.reason(),request.requestKey()));}
    public record StateRequest(@NotNull @Min(0) Integer expectedVersion,@NotBlank @Pattern(regexp="SUSPEND|RESUME|TERMINATE") String action,@NotBlank @Size(max=255) String reason,@NotBlank @Pattern(regexp="[A-Za-z0-9_-]{1,64}") String requestKey){}
}
