package com.course.platform.controller;

import com.course.platform.application.service.projectcenter.ProjectReportingService;
import com.course.platform.application.service.projectclient.ProjectApiKeyService;
import com.course.platform.common.result.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
public class ProjectReportingController {
    private final ProjectReportingService reports;
    private final ProjectApiKeyService keys;

    @GetMapping("/project-clients/usage")
    public ResponseEntity<?> owner() {
        return ok(reports.owner(keys.web()));
    }

    @GetMapping("/project-clients/usage/projects")
    public ResponseEntity<?> projects(@RequestParam(defaultValue = "1") int page,
                                      @RequestParam(defaultValue = "20") int pageSize) {
        return ok(reports.projects(keys.web(), page, pageSize));
    }

    @PreAuthorize("hasAuthority('api-provider:update') and hasAuthority('payment:reconcile')")
    @GetMapping("/admin/project-reports/overview")
    public ResponseEntity<?> system() {
        return ok(reports.system());
    }

    private static ResponseEntity<?> ok(Object value) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(Result.success(value));
    }
}
