package com.course.platform.controller;

import com.course.platform.application.service.projectcenter.ProjectRecordsService;
import com.course.platform.application.service.projectclient.ProjectApiKeyService;
import com.course.platform.common.result.Result;
import com.course.platform.domain.projectcenter.ProjectRecordTypes.LedgerFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@RestController
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
public class ProjectRecordsController {
    private final ProjectRecordsService records;
    private final ProjectApiKeyService keys;
    private static final String ADMIN = "hasAuthority('api-provider:update') and hasAuthority('payment:reconcile')";

    @GetMapping("/admin/project-reports/owners")
    @PreAuthorize(ADMIN)
    public ResponseEntity<?> owners(@RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "20") int pageSize) {
        return ok(records.owners(keyword, page, pageSize));
    }
    @GetMapping("/admin/project-reports/owners/{id}")
    @PreAuthorize(ADMIN)
    public ResponseEntity<?> owner(@PathVariable long id) { return ok(records.owner(id)); }

    @GetMapping("/admin/project-reports/owners/{id}/accounts")
    @PreAuthorize(ADMIN)
    public ResponseEntity<?> accounts(@PathVariable long id,
            @RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "20") int pageSize) {
        return ok(records.accounts(id, page, pageSize));
    }
    @GetMapping("/admin/project-reports/owners/{id}/clients")
    @PreAuthorize(ADMIN)
    public ResponseEntity<?> clients(@PathVariable long id, @RequestParam(required = false) Long projectId,
            @RequestParam(required = false) String status, @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        return ok(records.customers(id, projectId, status, page, pageSize));
    }
    @GetMapping("/admin/project-reports/ledger")
    @PreAuthorize(ADMIN)
    public ResponseEntity<?> adminLedger(@RequestParam(required = false) Long ownerId,
            @RequestParam(required = false) Long projectId, @RequestParam(required = false) String clientId,
            @RequestParam(required = false) String book, @RequestParam(required = false) String direction,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate throughDate,
            @RequestParam(required = false) String keyword, @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        return ok(records.adminLedger(new LedgerFilter(ownerId, projectId, clientId, book, direction, fromDate, throughDate, keyword), page, pageSize));
    }
    @GetMapping("/project-ledger")
    public ResponseEntity<?> ownLedger(@RequestParam(required = false) Long projectId,
            @RequestParam(required = false) String clientId, @RequestParam(required = false) String book,
            @RequestParam(required = false) String direction,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate throughDate,
            @RequestParam(required = false) String keyword, @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        return ok(records.ownLedger(keys.web(), new LedgerFilter(null, projectId, clientId, book, direction, fromDate, throughDate, keyword), page, pageSize));
    }
    private static ResponseEntity<?> ok(Object body) { return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(Result.success(body)); }
}
