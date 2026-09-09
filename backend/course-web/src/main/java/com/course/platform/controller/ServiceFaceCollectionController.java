package com.course.platform.controller;

import com.course.platform.application.service.servicecommerce.ServiceAccountSessions;
import com.course.platform.common.exception.BusinessException;

import jakarta.servlet.http.HttpServletRequest;

import lombok.RequiredArgsConstructor;

import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * The browser must POST a short-lived local ticket; general session DTOs never expose face URLs.
 */
@RestController
@RequiredArgsConstructor
public class ServiceFaceCollectionController {
    private final ServiceAccountSessions service;

    @PreAuthorize("permitAll()")
    @PostMapping(
            value = "/service-face-collection/launch",
            consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    public ResponseEntity<Void> launch(
            @RequestParam String sessionId,
            @RequestParam String ticket,
            HttpServletRequest request) {
        if (request.getQueryString() != null) throw new BusinessException("跳转凭据只能通过表单提交");
        String site = request.getHeader("Sec-Fetch-Site");
        if (site != null && !"same-origin".equals(site))
            throw new BusinessException("请从本平台的授权页面打开采集链接");
        return ResponseEntity.status(HttpStatus.SEE_OTHER)
                .cacheControl(CacheControl.noStore())
                .header("Referrer-Policy", "no-referrer")
                .header("X-Content-Type-Options", "nosniff")
                .location(service.consumeFaceLaunch(sessionId, ticket))
                .build();
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<String> rejected() {
        return ResponseEntity.badRequest()
                .cacheControl(CacheControl.noStore())
                .header("Referrer-Policy", "no-referrer")
                .header("X-Content-Type-Options", "nosniff")
                .contentType(MediaType.TEXT_PLAIN)
                .body("采集跳转已失效或被拒绝。请关闭此页，返回服务表单重新打开；不要转发授权链接。");
    }
}
