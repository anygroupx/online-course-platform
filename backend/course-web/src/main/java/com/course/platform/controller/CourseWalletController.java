package com.course.platform.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.course.platform.common.result.Result;
import com.course.platform.domain.dto.CourseWalletTransferRequest;
import com.course.platform.domain.entity.CourseReservedTransfer;
import com.course.platform.domain.vo.CoursePricePreview;
import com.course.platform.security.SecurityUtils;
import com.course.platform.service.impl.CoursePrivateContractService;
import com.course.platform.service.impl.CourseReservedWalletService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/** 本人专户与价格接口，所有资金归属从登录主体取得，不接受客户端代理ID。 */
@RestController
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
public class CourseWalletController {
    private final CoursePrivateContractService service;
    private final CourseReservedWalletService funds;

    @GetMapping("/courses/{platformId}/my-pricing")
    public Result<CoursePricePreview> price(@PathVariable Long platformId) {
        return Result.success(service.myPrice(SecurityUtils.getCurrentUserId(),platformId));
    }
    @GetMapping("/courses/my-pricing")
    public Result<Map<Long,CoursePricePreview>> prices() { return Result.success(service.myPrices(SecurityUtils.getCurrentUserId())); }

    @GetMapping("/course-wallets")
    public Result<IPage<Map<String,Object>>> wallets(@RequestParam(defaultValue="1") int page,@RequestParam(defaultValue="20") int pageSize) {
        return Result.success(service.myWallets(SecurityUtils.getCurrentUserId(),page,pageSize));
    }
    @GetMapping("/course-wallets/{id}")
    public Result<Map<String,Object>> wallet(@PathVariable Long id) { return Result.success(service.myWallet(SecurityUtils.getCurrentUserId(),id)); }

    @GetMapping("/course-wallets/{id}/ledger")
    public Result<IPage<Map<String,Object>>> ledger(@PathVariable Long id,@RequestParam(defaultValue="1") int page,@RequestParam(defaultValue="20") int pageSize) {
        return Result.success(service.myLedger(SecurityUtils.getCurrentUserId(),id,page,pageSize));
    }
    @PostMapping("/course-wallets/{id}/transfer-in")
    public Result<CourseReservedTransfer> transferIn(@PathVariable Long id,@Valid @RequestBody CourseWalletTransferRequest request) {
        return Result.success(funds.transferIn(id,SecurityUtils.getCurrentUserId(),request));
    }
    // 静态路径优先于/{id}，不提供本人转出或提现路径。
    @GetMapping("/course-wallets/transfers")
    public Result<IPage<Map<String,Object>>> transfers(@RequestParam(defaultValue="1") int page,@RequestParam(defaultValue="20") int pageSize) {
        return Result.success(service.myTransfers(SecurityUtils.getCurrentUserId(),page,pageSize));
    }
}
