package com.course.platform.controller;

import com.course.platform.common.result.Result;
import com.course.platform.security.SecurityUtils;
import com.course.platform.service.impl.CoursePrivateSettlementService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/** 人工解除原周期事实复核需独立调整与结算权限，审批主体不接受客户端指定。 */
@RestController
@RequestMapping("/admin/course-private-operations/terms")
@RequiredArgsConstructor
public class AdminCoursePrivateSettlementController {
    private final CoursePrivateSettlementService service;
    @PostMapping("/{id}/resolve-review")
    @PreAuthorize("hasAuthority('course-private-term:adjust') and hasAuthority('course-private-settlement:execute')")
    public Result<CoursePrivateSettlementService.ReviewResult> resolve(@PathVariable Long id,@Valid @RequestBody ReviewRequest request){return Result.success(service.resolveReview(id,request.expectedVersion(),request.expectedSettlementId(),SecurityUtils.getCurrentUserId(),request.reason(),request.evidence(),request.requestKey()));}
    public record ReviewRequest(@NotNull @Min(0) Integer expectedVersion,@NotNull @Positive Long expectedSettlementId,@NotBlank @Size(max=255) String reason,@NotBlank @Size(max=2000) String evidence,@NotBlank @Pattern(regexp="[A-Za-z0-9_-]{1,64}") String requestKey){}
}
