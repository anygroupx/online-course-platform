package com.course.platform.controller;

import com.course.platform.application.service.servicecommerce.ServiceAccountSessions;
import com.course.platform.common.result.Result;
import com.course.platform.domain.servicecommerce.ServiceAccountTypes.*;

import jakarta.validation.Valid;

import lombok.RequiredArgsConstructor;

import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
public class ServiceAccountSessionController {
    private final ServiceAccountSessions service;

    @PostMapping("/services/{productId}/account-sessions")
    public ResponseEntity<Result<SessionView>> start(
            @PathVariable Long productId, @Valid @RequestBody StartForm form) {
        return ok(service.start(productId, form));
    }

    @GetMapping("/service-account-sessions/{id}")
    public ResponseEntity<Result<SessionView>> get(@PathVariable String id) {
        return ok(service.get(id));
    }

    @PostMapping("/service-account-sessions/{id}/send-code")
    public ResponseEntity<Result<SessionView>> send(@PathVariable String id) {
        return ok(service.sendCode(id));
    }

    @PostMapping("/service-account-sessions/{id}/verify")
    public ResponseEntity<Result<SessionView>> verify(
            @PathVariable String id, @Valid @RequestBody VerifyForm form) {
        return ok(service.verify(id, form));
    }

    @PostMapping("/service-account-sessions/{id}/refresh-rules")
    public ResponseEntity<Result<SessionView>> refresh(@PathVariable String id) {
        return ok(service.refreshRules(id));
    }

    @PostMapping("/service-account-sessions/{id}/face-collection")
    public ResponseEntity<Result<SessionView>> collect(
            @PathVariable String id, @RequestBody FaceConsentForm form) {
        return ok(service.collectFace(id, form));
    }

    @PostMapping("/service-account-sessions/{id}/face-check")
    public ResponseEntity<Result<SessionView>> checkFace(@PathVariable String id) {
        return ok(service.checkFace(id));
    }

    @PostMapping("/service-account-sessions/{id}/face-launch")
    public ResponseEntity<Result<FaceLaunchTicket>> launchTicket(@PathVariable String id) {
        return ok(service.issueFaceLaunch(id));
    }

    @DeleteMapping("/service-account-sessions/{id}")
    public ResponseEntity<Result<Void>> revoke(@PathVariable String id) {
        service.revoke(id);
        return ok(null);
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

    private <T> ResponseEntity<Result<T>> ok(T value) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(Result.success(value));
    }
}
