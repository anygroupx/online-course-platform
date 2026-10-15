package com.course.platform.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.course.platform.common.result.Result;
import com.course.platform.domain.dto.CourseContractRequest;
import com.course.platform.domain.dto.CourseWalletTransferRequest;
import com.course.platform.domain.entity.CoursePrivateContractHistory;
import com.course.platform.domain.entity.CourseReservedTransfer;
import com.course.platform.security.SecurityUtils;
import com.course.platform.service.impl.CoursePrivateContractService;
import com.course.platform.service.impl.CourseReservedWalletService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import static com.course.platform.common.security.SecurityAuthorities.*;

/** 协议配置、资金审计与财务转回均使用独立RBAC权限。 */
@RestController
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
public class AdminCourseWalletController {
    private final CoursePrivateContractService service;
    private final CourseReservedWalletService funds;

    @GetMapping("/admin/course-private-contracts")
    @PreAuthorize("hasAnyAuthority('"+COURSE_PRIVATE_PRICE_READ+"','"+COURSE_PRIVATE_PRICE_MANAGE+"')")
    public Result<IPage<Map<String,Object>>> contracts(@RequestParam(defaultValue="1") int page,@RequestParam(defaultValue="20") int pageSize,@RequestParam(required=false) String userUid,@RequestParam(required=false) Long platformId) {
        return Result.success(service.adminContracts(SecurityUtils.getCurrentUserId(),page,pageSize,userUid,platformId));
    }
    @GetMapping("/admin/course-private-contracts/price-reference")
    @PreAuthorize("hasAuthority('"+COURSE_PRIVATE_PRICE_MANAGE+"')")
    public Result<Map<String,Object>> priceReference(@RequestParam String userUid,@RequestParam Long platformId) {
        return Result.success(service.adminPriceReference(SecurityUtils.getCurrentUserId(),userUid,platformId));
    }
    @PostMapping("/admin/course-private-contracts")
    @PreAuthorize("hasAuthority('"+COURSE_PRIVATE_PRICE_MANAGE+"')")
    public Result<Map<String,Object>> create(@Valid @RequestBody CourseContractRequest request) { return Result.success(service.create(request,SecurityUtils.getCurrentUserId())); }

    @PutMapping("/admin/course-private-contracts/{id}")
    @PreAuthorize("hasAuthority('"+COURSE_PRIVATE_PRICE_MANAGE+"')")
    public Result<Map<String,Object>> update(@PathVariable Long id,@Valid @RequestBody CourseContractRequest request) {
        return Result.success(service.update(id,request,SecurityUtils.getCurrentUserId()));
    }
    @PostMapping("/admin/course-private-contracts/{id}/status")
    @PreAuthorize("hasAuthority('"+COURSE_PRIVATE_PRICE_MANAGE+"')")
    public Result<Map<String,Object>> status(@PathVariable Long id,@Valid @RequestBody CourseContractRequest request) {
        // 状态操作携带完整配置及版本，避免覆盖并发修改或丢失可空有效期。
        return Result.success(service.update(id,request,SecurityUtils.getCurrentUserId()));
    }
    @GetMapping("/admin/course-private-contracts/{id}/history")
    @PreAuthorize("hasAnyAuthority('"+COURSE_PRIVATE_PRICE_READ+"','"+COURSE_PRIVATE_PRICE_MANAGE+"','"+COURSE_WALLET_AUDIT+"')")
    public Result<IPage<CoursePrivateContractHistory>> history(@PathVariable Long id,@RequestParam(defaultValue="1") int page,@RequestParam(defaultValue="20") int pageSize) {
        return Result.success(service.adminHistory(SecurityUtils.getCurrentUserId(),id,page,pageSize));
    }
    @GetMapping("/admin/course-wallets")
    @PreAuthorize("hasAnyAuthority('"+COURSE_WALLET_READ_ANY+"','"+COURSE_WALLET_AUDIT+"','"+COURSE_WALLET_RELEASE+"')")
    public Result<IPage<Map<String,Object>>> wallets(@RequestParam(defaultValue="1") int page,@RequestParam(defaultValue="20") int pageSize,@RequestParam(required=false) String userUid,@RequestParam(required=false) Long platformId) {
        return Result.success(service.adminWallets(SecurityUtils.getCurrentUserId(),page,pageSize,userUid,platformId));
    }
    @GetMapping("/admin/course-wallets/{id}/ledger")
    @PreAuthorize("hasAnyAuthority('"+COURSE_WALLET_READ_ANY+"','"+COURSE_WALLET_AUDIT+"','"+COURSE_WALLET_RELEASE+"')")
    public Result<IPage<Map<String,Object>>> ledger(@PathVariable Long id,@RequestParam(defaultValue="1") int page,@RequestParam(defaultValue="20") int pageSize) {
        return Result.success(service.adminLedger(SecurityUtils.getCurrentUserId(),id,page,pageSize));
    }
    @PostMapping("/admin/course-wallets/{id}/release")
    @PreAuthorize("hasAuthority('"+COURSE_WALLET_RELEASE+"')")
    public Result<CourseReservedTransfer> release(@PathVariable Long id,@Valid @RequestBody CourseWalletTransferRequest request) {
        return Result.success(funds.release(id,SecurityUtils.getCurrentUserId(),request));
    }
    @GetMapping("/admin/course-wallet-transfers")
    @PreAuthorize("hasAnyAuthority('"+COURSE_WALLET_READ_ANY+"','"+COURSE_WALLET_AUDIT+"','"+COURSE_WALLET_RELEASE+"')")
    public Result<IPage<Map<String,Object>>> transfers(@RequestParam(defaultValue="1") int page,@RequestParam(defaultValue="20") int pageSize,@RequestParam(required=false) String userUid,@RequestParam(required=false) Long platformId) {
        return Result.success(service.adminTransfers(SecurityUtils.getCurrentUserId(),page,pageSize,userUid,platformId));
    }
}
