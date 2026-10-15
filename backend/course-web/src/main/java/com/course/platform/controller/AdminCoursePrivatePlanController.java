package com.course.platform.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.course.platform.common.result.Result;
import com.course.platform.domain.dto.CoursePrivatePlanRequest;
import com.course.platform.domain.entity.*;
import com.course.platform.security.SecurityUtils;
import com.course.platform.service.impl.CoursePrivatePlanService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

/** 运营价格配置与财务成本核验使用独立权限；客户端不能提交审核主体。 */
@RestController
@RequestMapping("/admin/course-private-operations")
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
public class AdminCoursePrivatePlanController {
    private final CoursePrivatePlanService service;
    @GetMapping("/plans")
    @PreAuthorize("hasAnyAuthority('course-private-plan:read','course-private-plan:manage','course-private-plan:publish')")
    public Result<IPage<CoursePrivatePlan>> plans(@RequestParam(defaultValue="1") int page,@RequestParam(defaultValue="20") int pageSize,@RequestParam(required=false) Long platformId,@RequestParam(required=false) String status){return Result.success(service.adminPlans(actor(),page,pageSize,platformId,status));}
    @GetMapping("/plans/{id}")
    @PreAuthorize("hasAnyAuthority('course-private-plan:read','course-private-plan:manage','course-private-plan:publish')")
    public Result<Map<String,Object>> detail(@PathVariable Long id){return Result.success(service.detail(id,actor()));}
    @GetMapping("/plans/{id}/price-reference")
    @PreAuthorize("hasAnyAuthority('course-private-plan:read','course-private-plan:manage','course-private-plan:publish')")
    public Result<Map<String,Object>> reference(@PathVariable Long id,@RequestParam String userUid){return Result.success(service.ordinaryReference(id,actor(),userUid));}
    @PostMapping("/plans")
    @PreAuthorize("hasAuthority('course-private-plan:manage')")
    public Result<CoursePrivatePlan> create(@Valid @RequestBody CoursePrivatePlanRequest request){return Result.success(service.save(null,request,actor()));}
    @PutMapping("/plans/{id}")
    @PreAuthorize("hasAuthority('course-private-plan:manage')")
    public Result<CoursePrivatePlan> update(@PathVariable Long id,@Valid @RequestBody CoursePrivatePlanRequest request){return Result.success(service.save(id,request,actor()));}
    @PostMapping("/plans/{id}/publish")
    @PreAuthorize("hasAuthority('course-private-plan:publish')")
    public Result<CoursePrivatePlanVersion> publish(@PathVariable Long id,@Valid @RequestBody PublishRequest request){return Result.success(service.publish(id,request.expectedDraftVersion(),request.costVersionId(),request.riskApprovalId(),actor(),request.reason()));}
    @PostMapping("/plans/{id}/disable")
    @PreAuthorize("hasAuthority('course-private-plan:publish')")
    public Result<CoursePrivatePlan> disable(@PathVariable Long id,@Valid @RequestBody PublishRequest request){return Result.success(service.disable(id,request.expectedDraftVersion(),actor(),request.reason()));}
    @GetMapping("/costs")
    @PreAuthorize("hasAnyAuthority('course-private-cost:verify','course-private-risk:approve','course-private-analytics:read')")
    public Result<IPage<CoursePrivateCostVersion>> costs(@RequestParam(defaultValue="1") int page,@RequestParam(defaultValue="20") int pageSize,@RequestParam(required=false) Long platformId){return Result.success(service.costList(actor(),page,pageSize,platformId));}
    @PostMapping("/costs")
    @PreAuthorize("hasAuthority('course-private-cost:verify')")
    public Result<CoursePrivateCostVersion> cost(@Valid @RequestBody CostRequest request){return Result.success(service.verifyCost(request.platformId(),request.unitCost(),request.evidence(),request.validFrom(),request.validUntil(),actor(),request.reason()));}
    @GetMapping("/risk-approvals")
    @PreAuthorize("hasAnyAuthority('course-private-risk:approve','course-private-plan:publish')")
    public Result<IPage<CoursePrivateRiskApproval>> risks(@RequestParam(defaultValue="1") int page,@RequestParam(defaultValue="20") int pageSize,@RequestParam(required=false) Long planId){return Result.success(service.riskList(actor(),page,pageSize,planId));}
    @PostMapping("/risk-approvals")
    @PreAuthorize("hasAuthority('course-private-risk:approve')")
    public Result<CoursePrivateRiskApproval> approveRisk(@Valid @RequestBody RiskRequest request){return Result.success(service.approveRisk(request.planId(),request.draftVersion(),request.costVersionId(),actor(),request.reason()));}
    private Long actor(){return SecurityUtils.getCurrentUserId();}
    public record PublishRequest(@NotNull @Min(0) Integer expectedDraftVersion,@Positive Long costVersionId,@Positive Long riskApprovalId,@NotBlank @Size(max=255) String reason){}
    public record RiskRequest(@NotNull @Positive Long planId,@NotNull @Min(0) Integer draftVersion,@NotNull @Positive Long costVersionId,@NotBlank @Size(max=255) String reason){}
    public record CostRequest(@NotNull @Positive Long platformId,@NotNull @DecimalMin("0") @DecimalMax("9999999999.99") @Digits(integer=10,fraction=2) BigDecimal unitCost,@NotBlank @Size(max=2000) String evidence,LocalDateTime validFrom,LocalDateTime validUntil,@NotBlank @Size(max=255) String reason){}
}
