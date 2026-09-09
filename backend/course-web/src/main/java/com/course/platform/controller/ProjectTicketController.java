package com.course.platform.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.course.platform.application.service.projectcenter.ProjectTicketService;
import com.course.platform.common.result.Result;
import com.course.platform.domain.projectcenter.ProjectTicketTypes.*;

import jakarta.validation.Valid;

import lombok.RequiredArgsConstructor;

import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
public class ProjectTicketController {
    private final ProjectTicketService service;

    @GetMapping("/project-tickets")
    public ResponseEntity<Result<IPage<TicketView>>> tickets(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) String accountId) {
        return ok(service.tickets(page, pageSize, accountId, false));
    }

    @GetMapping("/project-tickets/{id}")
    public ResponseEntity<Result<TicketView>> ticket(@PathVariable String id) {
        return ok(service.ticket(id, false));
    }

    @GetMapping("/project-tickets/{id}/image")
    public ResponseEntity<byte[]> image(@PathVariable String id, @RequestParam(required = false) String replyId) {
        return ProjectClientTicketController.imageResponse(service.image(id, replyId, false));
    }

    @GetMapping("/project-ticket-operations/{id}/image")
    public ResponseEntity<byte[]> operationImage(@PathVariable String id) {
        return ProjectClientTicketController.imageResponse(service.operationImage(id, false));
    }

    @PreAuthorize("hasAuthority('api-provider:update')")
    @GetMapping("/admin/project-tickets/{id}/image")
    public ResponseEntity<byte[]> adminImage(@PathVariable String id, @RequestParam(required = false) String replyId) {
        return ProjectClientTicketController.imageResponse(service.image(id, replyId, true));
    }

    @PreAuthorize("hasAuthority('api-provider:update')")
    @GetMapping("/admin/project-ticket-operations/{id}/image")
    public ResponseEntity<byte[]> adminOperationImage(@PathVariable String id) {
        return ProjectClientTicketController.imageResponse(service.operationImage(id, true));
    }

    @PostMapping("/project-tickets/{id}/refresh")
    public ResponseEntity<Result<TicketView>> refresh(@PathVariable String id) {
        return ok(service.refresh(id, false));
    }

    @PostMapping("/project-accounts/{id}/tickets")
    public ResponseEntity<Result<OperationView>> submit(
            @PathVariable String id, @Valid @RequestBody SubmitForm form) {
        return ok(service.submit(id, form));
    }

    @PostMapping("/project-tickets/{id}/reply-quotes")
    public ResponseEntity<Result<OperationView>> reply(
            @PathVariable String id, @Valid @RequestBody ReplyForm form) {
        return ok(service.reply(id, form));
    }

    @GetMapping("/project-ticket-operations/{id}")
    public ResponseEntity<Result<OperationView>> operation(@PathVariable String id) {
        return ok(service.operation(id, false));
    }

    @PostMapping("/project-ticket-operations/{id}/confirm")
    public ResponseEntity<Result<OperationView>> confirm(@PathVariable String id) {
        return ok(service.confirm(id, false));
    }

    @PreAuthorize("hasAuthority('api-provider:update')")
    @GetMapping("/admin/project-tickets")
    public ResponseEntity<Result<IPage<TicketView>>> adminTickets(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) String accountId) {
        return ok(service.tickets(page, pageSize, accountId, true));
    }

    @PreAuthorize("hasAuthority('api-provider:update')")
    @GetMapping("/admin/project-tickets/{id}")
    public ResponseEntity<Result<TicketView>> adminTicket(@PathVariable String id) {
        return ok(service.ticket(id, true));
    }

    @PreAuthorize("hasAuthority('api-provider:update')")
    @PostMapping("/admin/project-tickets/{id}/refresh")
    public ResponseEntity<Result<TicketView>> adminRefresh(@PathVariable String id) {
        return ok(service.refresh(id, true));
    }

    @PreAuthorize("hasAuthority('api-provider:update')")
    @GetMapping("/admin/project-ticket-operations/{id}")
    public ResponseEntity<Result<OperationView>> adminOperation(@PathVariable String id) {
        return ok(service.operation(id, true));
    }

    @PreAuthorize("hasAuthority('api-provider:update') and hasAuthority('payment:reconcile')")
    @PostMapping("/admin/project-tickets/{id}/review-quotes")
    public ResponseEntity<Result<OperationView>> review(
            @PathVariable String id, @Valid @RequestBody ReviewForm form) {
        return ok(service.review(id, form));
    }

    @PreAuthorize("hasAuthority('api-provider:update') and hasAuthority('payment:reconcile')")
    @PostMapping("/admin/project-ticket-operations/{id}/confirm")
    public ResponseEntity<Result<OperationView>> adminConfirm(@PathVariable String id) {
        return ok(service.confirm(id, true));
    }

    @PreAuthorize("hasAuthority('api-provider:update') and hasAuthority('payment:reconcile')")
    @PostMapping("/admin/project-ticket-operations/{id}/resolve")
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
