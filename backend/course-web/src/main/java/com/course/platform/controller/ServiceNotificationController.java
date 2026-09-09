package com.course.platform.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.course.platform.application.service.servicenotification.ServiceNotificationService;
import com.course.platform.common.result.Result;
import com.course.platform.domain.servicenotification.ServiceNotificationTypes.*;

import jakarta.validation.Valid;

import lombok.RequiredArgsConstructor;

import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
public class ServiceNotificationController {
    private final ServiceNotificationService service;

    @GetMapping("/service-orders/{id}/notifications")
    public ResponseEntity<Result<SettingsView>> settings(@PathVariable String id) {
        return ok(service.settings(id));
    }

    @PutMapping("/service-orders/{id}/notifications")
    public ResponseEntity<Result<SettingsView>> configure(
            @PathVariable String id, @Valid @RequestBody ConfigureForm form) {
        return ok(service.configure(id, form));
    }

    @PostMapping("/service-orders/{id}/notifications/challenge")
    public ResponseEntity<Result<SettingsView>> challenge(
            @PathVariable String id, @Valid @RequestBody VersionForm form) {
        return ok(service.challenge(id, form));
    }

    @PostMapping("/service-orders/{id}/notifications/verify")
    public ResponseEntity<Result<SettingsView>> verify(
            @PathVariable String id, @Valid @RequestBody VerifyForm form) {
        return ok(service.verify(id, form));
    }

    @DeleteMapping("/service-orders/{id}/notifications")
    public ResponseEntity<Result<SettingsView>> disconnect(
            @PathVariable String id, @Valid @RequestBody VersionForm form) {
        return ok(service.disconnect(id, form));
    }

    @GetMapping("/service-orders/{id}/notifications/deliveries")
    public ResponseEntity<Result<IPage<DeliveryView>>> deliveries(
            @PathVariable String id,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        return ok(service.deliveries(id, page, pageSize));
    }

    @GetMapping("/service-notification-deliveries/{id}")
    public ResponseEntity<Result<DeliveryView>> delivery(@PathVariable String id) {
        return ok(service.delivery(id));
    }

    @ExceptionHandler(org.springframework.http.converter.HttpMessageNotReadableException.class)
    public ResponseEntity<Result<Void>> malformed() {
        return ResponseEntity.badRequest()
                .cacheControl(CacheControl.noStore())
                .body(
                        Result.error(
                                com.course.platform.common.result.ResultCode.PARAM_ERROR.getCode(),
                                "请求体格式错误"));
    }

    private static <T> ResponseEntity<Result<T>> ok(T value) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(Result.success(value));
    }
}
