package com.course.platform.controller;

import com.course.platform.shared.result.Result;
import com.course.platform.domain.dto.BatchOrderRequest;
import com.course.platform.service.BatchOrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 批量订单控制器
 * 
 * @author AI Assistant
 * @since 2025-01-17
 */
@Tag(name = "批量订单", description = "批量下单接口")
@RestController
@RequestMapping("/orders/batch")
@RequiredArgsConstructor
public class BatchOrderController {

    private final BatchOrderService batchOrderService;

    /**
     * 批量创建订单
     */
    @Operation(summary = "批量创建订单", description = "一次提交多个订单")
    @PostMapping
    public Result<List<Long>> batchCreateOrders(@Valid @RequestBody BatchOrderRequest request,
                                                  Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        List<Long> orderIds = batchOrderService.batchCreateOrders(request, userId);
        return Result.success("批量订单创建成功", orderIds);
    }
}

