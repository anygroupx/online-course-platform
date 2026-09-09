package com.course.platform.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.course.platform.application.service.projectcenter.ProjectCenterService;
import com.course.platform.common.result.Result;
import com.course.platform.domain.projectcenter.ProjectCenterTypes.*;

import jakarta.validation.Valid;

import lombok.RequiredArgsConstructor;

import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
public class ProjectCenterController {
    private final ProjectCenterService service;

    @GetMapping("/service-projects")
    public ResponseEntity<Result<IPage<ProjectView>>> projects(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        return ok(service.projects(page, pageSize, false));
    }

    @PostMapping("/service-projects/{id}/quotes")
    public ResponseEntity<Result<OperationView>> quote(
            @PathVariable Long id, @Valid @RequestBody QuoteForm form) {
        return ok(service.quote(id, form));
    }

    @PostMapping("/project-accounts/{id}/refresh")
    public ResponseEntity<Result<AccountView>> refresh(@PathVariable String id) {
        return ok(service.refresh(id));
    }

    @GetMapping("/project-operations")
    public ResponseEntity<Result<IPage<OperationView>>> operations(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) Long projectId) {
        return ok(service.operations(page, pageSize, projectId, false));
    }

    @GetMapping("/project-operations/{id}")
    public ResponseEntity<Result<OperationView>> operation(@PathVariable String id) {
        return ok(service.operation(id, false));
    }

    @PostMapping("/project-operations/{id}/confirm")
    public ResponseEntity<Result<OperationView>> confirm(@PathVariable String id) {
        return ok(service.confirm(id));
    }

    @PreAuthorize("hasAuthority('api-provider:update')")
    @GetMapping("/admin/service-project-catalog")
    public ResponseEntity<Result<List<CatalogItem>>> catalog(@RequestParam Long providerId) {
        return ok(service.catalog(providerId));
    }

    @PreAuthorize("hasAuthority('api-provider:update')")
    @GetMapping("/admin/service-projects")
    public ResponseEntity<Result<IPage<ProjectView>>> adminProjects(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        return ok(service.projects(page, pageSize, true));
    }

    @PreAuthorize("hasAuthority('api-provider:update')")
    @PostMapping("/admin/service-projects")
    public ResponseEntity<Result<ProjectView>> create(@Valid @RequestBody ProjectForm form) {
        return ok(service.save(null, form));
    }

    @PreAuthorize("hasAuthority('api-provider:update')")
    @PutMapping("/admin/service-projects/{id}")
    public ResponseEntity<Result<ProjectView>> update(
            @PathVariable Long id, @Valid @RequestBody ProjectForm form) {
        return ok(service.save(id, form));
    }

    @PreAuthorize("hasAuthority('api-provider:update') and hasAuthority('payment:reconcile')")
    @GetMapping("/admin/project-operations")
    public ResponseEntity<Result<IPage<OperationView>>> adminOperations(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) Long projectId) {
        return ok(service.operations(page, pageSize, projectId, true));
    }

    @PreAuthorize("hasAuthority('api-provider:update') and hasAuthority('payment:reconcile')")
    @GetMapping("/admin/project-operations/{id}")
    public ResponseEntity<Result<OperationView>> adminOperation(@PathVariable String id) {
        return ok(service.operation(id, true));
    }

    @PreAuthorize("hasAuthority('api-provider:update') and hasAuthority('payment:reconcile')")
    @PostMapping("/admin/project-operations/{id}/resolve")
    public ResponseEntity<Result<OperationView>> resolve(
            @PathVariable String id, @Valid @RequestBody ResolveForm form) {
        return ok(service.resolve(id, form));
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

    private static <T> ResponseEntity<Result<T>> ok(T data) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(Result.success(data));
    }
}
