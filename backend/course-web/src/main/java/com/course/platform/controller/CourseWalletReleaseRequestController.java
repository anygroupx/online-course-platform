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
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

/** 代理只能申请本人未用专款转回普通余额，申请期间余额保持可用。 */
@RestController
@RequestMapping("/course-private-operations/releases")
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
public class CourseWalletReleaseRequestController {
    private final CourseWalletReleaseRequestService service;
    @GetMapping
    public Result<IPage<Map<String,Object>>> requests(@RequestParam(defaultValue="1") int page,@RequestParam(defaultValue="20") int pageSize,@RequestParam(required=false) String status){return Result.success(service.page(SecurityUtils.getCurrentUserId(),page,pageSize,status).convert(this::view));}
    @PostMapping
    public Result<Map<String,Object>> submit(@Valid @RequestBody SubmitRequest request){return Result.success(view(service.submit(SecurityUtils.getCurrentUserId(),request.walletId(),request.amount(),request.requestKey(),request.reason())));}
    @PostMapping("/{id}/cancel")
    public Result<Map<String,Object>> cancel(@PathVariable Long id){return Result.success(view(service.cancel(SecurityUtils.getCurrentUserId(),id)));}
    private Map<String,Object> view(CourseWalletReleaseRequest value){var result=new LinkedHashMap<String,Object>();result.put("id",value.getId());result.put("walletId",value.getWalletId());result.put("amount",value.getAmount());result.put("status",value.getStatus());result.put("reason",value.getReason());result.put("reviewReason",value.getReviewReason());result.put("reviewedAt",value.getReviewedAt());result.put("executedTransferId",value.getExecutedTransferId());result.put("version",value.getVersion());result.put("createdAt",value.getCreatedAt());result.put("requestKey",value.getRequestKey());return result;}
    public record SubmitRequest(@NotNull @Positive Long walletId,@NotNull @DecimalMin(value="0",inclusive=false) @DecimalMax("9999999999.99") @Digits(integer=10,fraction=2) BigDecimal amount,@NotBlank @Pattern(regexp="[A-Za-z0-9_-]{1,64}") String requestKey,@NotBlank @Size(max=255) String reason){}
}
