package com.course.platform.controller;

import com.course.platform.application.service.projectclient.*;
import com.course.platform.common.exception.BusinessException;
import com.course.platform.common.result.*;
import com.course.platform.domain.projectclient.ProjectClientTypes.*;
import com.course.platform.domain.projectclient.ProjectClientTicketTypes.*;
import com.course.platform.application.service.projectclient.ProjectClientTicketService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

import lombok.RequiredArgsConstructor;

import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.function.Function;

/** Header-only local project API. Never populates JWT/RBAC context from these credentials. */
@RestController
@RequestMapping("/external/projects/v1")
@RequiredArgsConstructor
public class ExternalProjectClientController {
    private final ProjectClientService clients;
    private final com.course.platform.application.service.projectcenter.ProjectReportingService reports;
    private final ProjectClientTicketService tickets;
    private final ProjectApiKeyService keys;

    @GetMapping("/catalog")
    public ResponseEntity<?> catalog(
            HttpServletRequest request,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "50") int pageSize) {
        return invoke(request, "CATALOG", c -> clients.projects(c, page, pageSize));
    }

    @GetMapping("/clients")
    public ResponseEntity<?> list(
            HttpServletRequest request,
            @RequestParam(required = false) Long projectId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        return invoke(request, "CLIENTS", c -> clients.clients(c, projectId, page, pageSize));
    }

    @GetMapping("/clients/{id}")
    public ResponseEntity<?> client(HttpServletRequest request, @PathVariable String id) {
        return invoke(request, "CLIENT", c -> visible(c, clients.client(c, id)));
    }

    @GetMapping("/self")
    public ResponseEntity<?> self(HttpServletRequest request) {
        return invoke(
                request,
                "SELF",
                c -> {
                    if (c.clientId() == null) throw new BusinessException(ResultCode.FORBIDDEN);
                    return visible(c, clients.client(c, c.clientId()));
                });
    }

    @GetMapping("/stats")
    public ResponseEntity<?> stats(HttpServletRequest request) {
        return invoke(request, "STATS", clients::stats);
    }

    @GetMapping("/usage")
    public ResponseEntity<?> usage(HttpServletRequest request) {
        return invoke(request, "USAGE", reports::owner);
    }

    @GetMapping("/usage/projects")
    public ResponseEntity<?> usageProjects(HttpServletRequest request,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        return invoke(request, "USAGE_PROJECTS", c -> reports.projects(c, page, pageSize));
    }

    @PostMapping("/quotes")
    public ResponseEntity<?> quote(HttpServletRequest request, @Valid @RequestBody QuoteForm form) {
        return invoke(request, "QUOTE", c -> clients.quote(c, form));
    }

    @PostMapping("/operations/{id}/confirm")
    public ResponseEntity<?> confirm(
            HttpServletRequest request,
            @PathVariable String id,
            @Valid @RequestBody ConfirmForm form) {
        return invoke(request, "CONFIRM", c -> clients.confirm(c, id, form));
    }

    @GetMapping("/operations/{id}")
    public ResponseEntity<?> operation(HttpServletRequest request, @PathVariable String id) {
        return invoke(request, "OPERATION", c -> clients.operation(c, id));
    }

    @GetMapping("/operations/by-request/{id}")
    public ResponseEntity<?> byRequest(HttpServletRequest request, @PathVariable String id) {
        return invoke(request, "BY_REQUEST", c -> clients.byRequest(c, id));
    }

    @GetMapping("/operations")
    public ResponseEntity<?> operations(
            HttpServletRequest request,
            @RequestParam(required = false) String clientId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        return invoke(request, "OPERATIONS", c -> clients.operations(c, clientId, page, pageSize));
    }

    @PostMapping("/clients/{id}/status")
    public ResponseEntity<?> status(
            HttpServletRequest request,
            @PathVariable String id,
            @Valid @RequestBody StatusForm form) {
        return invoke(request, "CLIENT_STATUS", c -> clients.status(c, id, form));
    }

    @GetMapping("/tickets")
    public ResponseEntity<?> tickets(HttpServletRequest request,
            @RequestParam(required = false) String clientId, @RequestParam(required = false) String status,
            @RequestParam(required = false) String kind, @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        return invoke(request, "TICKETS", c -> tickets.list(c, clientId, status, kind, page, pageSize));
    }

    @GetMapping("/tickets/{id}")
    public ResponseEntity<?> ticket(HttpServletRequest request, @PathVariable String id) {
        return invoke(request, "TICKET", c -> tickets.ticket(c, id));
    }

    @GetMapping("/tickets/{id}/replies")
    public ResponseEntity<?> replies(HttpServletRequest request, @PathVariable String id,
            @RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "20") int pageSize) {
        return invoke(request, "TICKET_REPLIES", c -> tickets.replies(c, id, page, pageSize));
    }

    @GetMapping("/tickets/by-request/{id}")
    public ResponseEntity<?> ticketRequest(HttpServletRequest request, @PathVariable String id) {
        return invoke(request, "TICKET_REQUEST", c -> tickets.byRequest(c, id));
    }

    @PostMapping("/tickets")
    public ResponseEntity<?> createTicket(HttpServletRequest request, @Valid @RequestBody CreateForm form) {
        return invoke(request, "TICKET_CREATE", c -> tickets.create(c, form));
    }

    @PostMapping("/tickets/{id}/replies")
    public ResponseEntity<?> replyTicket(HttpServletRequest request, @PathVariable String id,
            @Valid @RequestBody ReplyForm form) {
        return invoke(request, "TICKET_REPLY", c -> tickets.reply(c, id, form));
    }

    @PostMapping("/tickets/{id}/decision")
    public ResponseEntity<?> decideTicket(HttpServletRequest request, @PathVariable String id,
            @Valid @RequestBody DecisionForm form) {
        return invoke(request, "TICKET_DECISION", c -> tickets.decide(c, id, form));
    }

    @GetMapping("/tickets/images/{id}")
    public ResponseEntity<byte[]> ticketImage(HttpServletRequest request, @PathVariable String id) {
        Caller caller = authenticate(request);
        boolean success = false;
        try {
            byte[] image = tickets.image(caller, id);
            success = true;
            return ProjectClientTicketController.imageResponse(image);
        } finally { keys.record(caller, "TICKET_IMAGE", success); }
    }

    private Object visible(Caller c, ClientView view) {
        return c.clientId() == null
                ? view
                : new BalanceView(
                        view.id(),
                        view.projectId(),
                        view.projectTitle(),
                        view.status(),
                        view.balance());
    }

    private Caller authenticate(HttpServletRequest request) {
        if (request.getParameter("key") != null
                || request.getParameter("api_key") != null
                || request.getParameter("token") != null
                || request.getParameter("customer_api_key") != null)
            throw new BusinessException(ResultCode.PARAM_ERROR);
        var header = Collections.list(request.getHeaders("X-Project-Key"));
        if (header.size() != 1) throw new BusinessException(ResultCode.UNAUTHORIZED);
        return keys.authenticate(header.get(0));
    }

    private <T> ResponseEntity<Result<T>> invoke(
            HttpServletRequest request, String action, Function<Caller, T> call) {
        Caller caller = authenticate(request);
        boolean success = false;
        try {
            T result = call.apply(caller);
            success = true;
            return ResponseEntity.ok()
                    .cacheControl(CacheControl.noStore())
                    .body(Result.success(result));
        } finally {
            keys.record(caller, action, success);
        }
    }

    @ExceptionHandler(org.springframework.http.converter.HttpMessageNotReadableException.class)
    public ResponseEntity<?> malformed() {
        return ResponseEntity.badRequest()
                .cacheControl(CacheControl.noStore())
                .body(Result.error(400, "请求体格式错误"));
    }
}
