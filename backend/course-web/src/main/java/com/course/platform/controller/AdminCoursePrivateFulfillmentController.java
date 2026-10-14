package com.course.platform.controller;

import com.course.platform.common.result.Result;
import com.course.platform.security.SecurityUtils;
import com.course.platform.service.impl.CoursePrivateFulfillmentService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDateTime;

/** 普通状态编辑不构成履约证据，人工补验必须同时具备订单修改和结算执行权限。 */
@RestController
@RequestMapping("/admin/course-private-operations/orders")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('order:update') and hasAuthority('course-private-settlement:execute')")
public class AdminCoursePrivateFulfillmentController {
    private final CoursePrivateFulfillmentService service;
    @PostMapping("/{id}/verify-fulfillment")
    public Result<Void> verify(@PathVariable Long id,@Valid @RequestBody VerificationRequest request){service.verifyExact(id,request.actualFulfilledAt(),request.reason(),request.evidence(),SecurityUtils.getCurrentUserId());return Result.success();}
    public record VerificationRequest(@NotNull LocalDateTime actualFulfilledAt,@NotBlank @Size(max=255) String reason,@NotBlank @Size(max=2000) String evidence){}
}
