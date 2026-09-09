package com.course.platform.controller;

import com.course.platform.application.service.catalogrefresh.CatalogRefreshService;
import com.course.platform.common.result.Result;
import com.course.platform.domain.catalogrefresh.CatalogRefreshTypes.*;

import jakarta.validation.Valid;

import lombok.RequiredArgsConstructor;

import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/admin/platforms/price-refreshes")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('platform:update')")
public class CatalogRefreshController {
    private final CatalogRefreshService service;

    @PostMapping
    public ResponseEntity<Result<View>> preview(@Valid @RequestBody PreviewForm form) {
        return ok(service.preview(form));
    }

    @GetMapping("/{id}")
    public ResponseEntity<Result<View>> get(@PathVariable String id) {
        return ok(service.get(id));
    }

    @PostMapping("/{id}/confirm")
    public ResponseEntity<Result<View>> confirm(
            @PathVariable String id, @Valid @RequestBody ConfirmForm form) {
        return ok(service.confirm(id, form));
    }

    private <T> ResponseEntity<Result<T>> ok(T value) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(Result.success(value));
    }
}
