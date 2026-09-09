package com.course.platform.controller;

import com.course.platform.application.service.projectclient.*;
import com.course.platform.common.result.Result;
import com.course.platform.domain.projectclient.ProjectClientTicketTypes.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/project-client-tickets")
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
public class ProjectClientTicketController {
    private final ProjectClientTicketService tickets;
    private final ProjectApiKeyService keys;

    @GetMapping
    public ResponseEntity<?> list(@RequestParam(required = false) String clientId,
            @RequestParam(required = false) String status, @RequestParam(required = false) String kind,
            @RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "20") int pageSize) {
        return ok(tickets.list(keys.web(), clientId, status, kind, page, pageSize));
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> ticket(@PathVariable String id) { return ok(tickets.ticket(keys.web(), id)); }

    @GetMapping("/{id}/replies")
    public ResponseEntity<?> replies(@PathVariable String id,
            @RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "20") int pageSize) {
        return ok(tickets.replies(keys.web(), id, page, pageSize));
    }

    @GetMapping("/by-request/{id}")
    public ResponseEntity<?> byRequest(@PathVariable String id) { return ok(tickets.byRequest(keys.web(), id)); }

    @PostMapping
    public ResponseEntity<?> create(@Valid @RequestBody CreateForm form) { return ok(tickets.create(keys.web(), form)); }

    @PostMapping("/{id}/replies")
    public ResponseEntity<?> reply(@PathVariable String id, @Valid @RequestBody ReplyForm form) {
        return ok(tickets.reply(keys.web(), id, form));
    }

    @PostMapping("/{id}/decision")
    public ResponseEntity<?> decision(@PathVariable String id, @Valid @RequestBody DecisionForm form) {
        return ok(tickets.decide(keys.web(), id, form));
    }

    @ExceptionHandler(org.springframework.http.converter.HttpMessageNotReadableException.class)
    public ResponseEntity<?> malformed() {
        return ResponseEntity.badRequest().cacheControl(CacheControl.noStore()).body(Result.error(400, "请求体格式错误"));
    }

    private <T> ResponseEntity<Result<T>> ok(T data) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(Result.success(data));
    }
}
