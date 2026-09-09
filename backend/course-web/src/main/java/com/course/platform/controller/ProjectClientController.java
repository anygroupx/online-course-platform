package com.course.platform.controller;

import com.course.platform.application.service.projectclient.*;
import com.course.platform.common.result.Result;
import com.course.platform.domain.projectclient.ProjectClientTypes.*;

import jakarta.validation.Valid;

import lombok.RequiredArgsConstructor;

import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
public class ProjectClientController {
    private final ProjectClientService clients;
    private final ProjectApiKeyService keys;

    @GetMapping("/project-clients/catalog")
    public ResponseEntity<?> catalog(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "50") int pageSize) {
        return ok(clients.projects(keys.web(), page, pageSize));
    }

    @GetMapping("/project-clients")
    public ResponseEntity<?> list(
            @RequestParam(required = false) Long projectId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        return ok(clients.clients(keys.web(), projectId, page, pageSize));
    }

    @GetMapping("/project-clients/stats")
    public ResponseEntity<?> stats() {
        return ok(clients.stats(keys.web()));
    }

    @GetMapping("/project-clients/{id}")
    public ResponseEntity<?> client(@PathVariable String id) {
        return ok(clients.client(keys.web(), id));
    }

    @PostMapping("/project-clients/quotes")
    public ResponseEntity<?> quote(@Valid @RequestBody QuoteForm form) {
        return ok(clients.quote(keys.web(), form));
    }

    @PostMapping("/project-clients/{id}/status")
    public ResponseEntity<?> status(@PathVariable String id, @Valid @RequestBody StatusForm form) {
        return ok(clients.status(keys.web(), id, form));
    }

    @GetMapping("/project-client-operations")
    public ResponseEntity<?> operations(
            @RequestParam(required = false) String clientId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        return ok(clients.operations(keys.web(), clientId, page, pageSize));
    }

    @GetMapping("/project-client-operations/by-request/{id}")
    public ResponseEntity<?> byRequest(@PathVariable String id) {
        return ok(clients.byRequest(keys.web(), id));
    }

    @GetMapping("/project-client-operations/{id}")
    public ResponseEntity<?> operation(@PathVariable String id) {
        return ok(clients.operation(keys.web(), id));
    }

    @PostMapping("/project-client-operations/{id}/confirm")
    public ResponseEntity<?> confirm(
            @PathVariable String id, @Valid @RequestBody ConfirmForm form) {
        return ok(clients.confirm(keys.web(), id, form));
    }

    @GetMapping("/project-api-keys/{subject}")
    public ResponseEntity<?> settings(@PathVariable String subject) {
        return ok(keys.settings(subject));
    }

    @PostMapping("/project-api-keys/{subject}")
    public ResponseEntity<?> issue(@PathVariable String subject, @Valid @RequestBody KeyForm form) {
        return ok(keys.issue(subject, form));
    }

    @DeleteMapping("/project-api-keys/{subject}")
    public ResponseEntity<?> revoke(
            @PathVariable String subject, @Valid @RequestBody KeyRevoke form) {
        return ok(keys.revoke(subject, form));
    }

    @GetMapping("/project-api-calls")
    public ResponseEntity<?> calls(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        return ok(keys.calls(page, pageSize));
    }

    @ExceptionHandler(org.springframework.http.converter.HttpMessageNotReadableException.class)
    public ResponseEntity<?> malformed() {
        return ResponseEntity.badRequest()
                .cacheControl(CacheControl.noStore())
                .body(Result.error(400, "请求体格式错误"));
    }

    private <T> ResponseEntity<Result<T>> ok(T value) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(Result.success(value));
    }
}
