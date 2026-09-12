package com.course.platform.controller;

import com.course.platform.application.service.orderreceipt.OrderReceiptRecoveryService;
import com.course.platform.common.result.Result;
import com.course.platform.domain.orderreceipt.OrderReceiptTypes.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/admin/orders/{orderId}/receipt-recoveries")
@PreAuthorize("hasAuthority('order:update') and hasAuthority('api-provider:update')")
public class OrderReceiptRecoveryController {
    private final OrderReceiptRecoveryService recovery;
    @PostMapping("/candidates")
    public ResponseEntity<?> candidates(@PathVariable long orderId) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(Result.success(recovery.candidates(orderId)));
    }
    @GetMapping
    public ResponseEntity<?> recent(@PathVariable long orderId) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(Result.success(recovery.recent(orderId)));
    }
    @PostMapping
    public ResponseEntity<?> preview(@PathVariable long orderId, @RequestBody PreviewForm form) {
        return ok(recovery.preview(orderId, form));
    }
    @GetMapping("/{requestId}")
    public ResponseEntity<?> get(@PathVariable long orderId, @PathVariable String requestId) {
        return ok(recovery.get(orderId, requestId));
    }
    @PostMapping("/{requestId}/confirm")
    public ResponseEntity<?> confirm(@PathVariable long orderId, @PathVariable String requestId, @RequestBody ConfirmForm form) {
        return ok(recovery.confirm(orderId, requestId, form));
    }
    private static ResponseEntity<?> ok(View view) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(Result.success(view));
    }
}
