package com.course.platform.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.course.platform.application.service.projectclient.*;
import com.course.platform.common.exception.BusinessException;
import com.course.platform.common.result.ResultCode;
import com.course.platform.common.security.TokenHashUtil;
import com.course.platform.config.RateLimitProperties;
import com.course.platform.domain.projectclient.*;
import com.course.platform.domain.projectclient.ProjectClientTypes.Caller;
import com.course.platform.domain.projectclient.ProjectClientTicketTypes.*;
import com.course.platform.domain.servicecommerce.ServiceTime;
import com.course.platform.infra.persistence.mapper.*;
import com.course.platform.security.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.*;
import java.util.*;
import java.util.function.Supplier;

/** Owner-scoped local after-sales. Intentionally has no ledger, wallet or supplier dependency. */
@Service
@RequiredArgsConstructor
public class ProjectClientTicketServiceImpl implements ProjectClientTicketService {
    private final ProjectClientTicketMapper tickets;
    private final ProjectClientTicketReplyMapper replies;
    private final ProjectClientTicketCommandMapper commands;
    private final ProjectClientMapper clients;
    private final UserMapper users;
    private final ProjectApiKeyService keys;
    private final Validator validator;
    private final RateLimitService limiter;
    private final RateLimitProperties limits;
    private final PlatformTransactionManager transactions;

    @Value("${app.native-services.enabled:false}")
    private boolean enabled;
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Set<String> KINDS = Set.of("SUGGESTION", "BUG", "COMPENSATION");
    private static final Set<String> STATES = Set.of("OPEN", "IN_PROGRESS", "RESOLVED", "CLOSED");

    @Override
    public IPage<TicketView> list(Caller c, String clientId, String status, String kind, int page, int size) {
        keys.recheckTickets(c, false);
        bounds(page, size);
        if (clientId != null) client(c, clientId, false);
        filter(status, STATES);
        filter(kind, KINDS);
        var query = new LambdaQueryWrapper<ProjectClientTicket>()
                .eq(ProjectClientTicket::getOwnerId, c.ownerId())
                .eq(c.clientId() != null, ProjectClientTicket::getClientId, c.clientId())
                .eq(clientId != null, ProjectClientTicket::getClientId, clientId)
                .eq(status != null, ProjectClientTicket::getStatus, status)
                .eq(kind != null, ProjectClientTicket::getKind, kind)
                .orderByDesc(ProjectClientTicket::getUpdateTime)
                .orderByDesc(ProjectClientTicket::getId);
        return tickets.selectPage(new Page<>(page, size), query).convert(this::view);
    }

    @Override
    public TicketView ticket(Caller c, String id) {
        keys.recheckTickets(c, false);
        return view(owned(c, id, false));
    }

    @Override
    public IPage<ReplyView> replies(Caller c, String id, int page, int size) {
        keys.recheckTickets(c, false);
        bounds(page, size);
        owned(c, id, false);
        return replies.selectPage(new Page<>(page, size), new LambdaQueryWrapper<ProjectClientTicketReply>()
                .eq(ProjectClientTicketReply::getTicketId, id)
                .orderByAsc(ProjectClientTicketReply::getTicketVersion))
                .convert(r -> new ReplyView(r.getId(), r.getTicketVersion(), r.getAuthor(), r.getContent(), r.getCreateTime()));
    }

    @Override
    public Receipt byRequest(Caller c, String requestId) {
        keys.recheckTickets(c, false);
        uuid(requestId);
        var row = find(c, requestId);
        return row == null ? null : receipt(row);
    }

    @Override
    public Receipt create(Caller c, CreateForm f) {
        validate(f);
        start(c, f.consent());
        uuid(f.requestId());
        uuid(f.clientId());
        String title = text(f.title(), 120), description = text(f.description(), 5000);
        if (!"COMPENSATION".equals(f.kind()) && f.requestedAmount().signum() != 0)
            throw bad("普通工单不能填写补偿申请金额");
        var amount = f.requestedAmount().setScale(2);
        String hash = hash("CREATE", f.clientId(), f.kind(), title, description, amount.toPlainString());
        return tx(() -> {
            lockOwner(c);
            var previous = existing(c, f.requestId(), hash);
            if (previous != null) return previous;
            var client = client(c, f.clientId(), true);
            if (!"ACTIVE".equals(client.getStatus())) throw bad("仅可为可用客户新建工单，已有售后可继续处理");
            var row = new ProjectClientTicket();
            row.setId(UUID.randomUUID().toString());
            row.setOwnerId(c.ownerId());
            row.setClientId(client.getId());
            row.setProjectId(client.getProjectId());
            row.setProjectTitle(client.getProjectTitle());
            row.setKind(f.kind());
            row.setTitle(title);
            row.setDescription(description);
            row.setRequestedAmount(amount);
            row.setStatus("OPEN");
            row.setVersion(0L);
            row.setReviewResult("COMPENSATION".equals(f.kind()) ? "PENDING" : "NONE");
            row.setCreateTime(now());
            row.setUpdateTime(now());
            write(tickets.insert(row));
            return save(c, f.requestId(), hash, row, "CREATE");
        });
    }

    @Override
    public Receipt reply(Caller c, String id, ReplyForm f) {
        validate(f);
        start(c, f.consent());
        uuid(id);
        uuid(f.requestId());
        String content = text(f.content(), 5000);
        String hash = hash("REPLY", id, f.version(), content);
        return tx(() -> {
            lockOwner(c);
            var previous = existing(c, f.requestId(), hash);
            if (previous != null) return previous;
            var row = lockTicket(c, id);
            version(row, f.version());
            open(row);
            row.setVersion(row.getVersion() + 1);
            append(c, row, content);
            row.setStatus("IN_PROGRESS");
            row.setUpdateTime(now());
            write(tickets.updateById(row));
            return save(c, f.requestId(), hash, row, "REPLY");
        });
    }

    @Override
    public Receipt decide(Caller c, String id, DecisionForm f) {
        validate(f);
        start(c, f.consent());
        keys.recheck(c, true, true);
        uuid(id);
        uuid(f.requestId());
        String note = text(f.note(), 2000);
        String hash = hash(f.action(), id, f.version(), note);
        return tx(() -> {
            lockOwner(c);
            keys.recheck(c, true, true);
            var previous = existing(c, f.requestId(), hash);
            if (previous != null) return previous;
            var row = lockTicket(c, id);
            version(row, f.version());
            open(row);
            boolean review = Set.of("APPROVE", "REJECT").contains(f.action());
            if (review != "COMPENSATION".equals(row.getKind()))
                throw bad("补偿工单须审核申请；普通工单只能标记解决或关闭");
            row.setVersion(row.getVersion() + 1);
            if (review) {
                if (!"PENDING".equals(row.getReviewResult())) throw bad("补偿申请已经审核，不能重复修改");
                row.setReviewResult("APPROVE".equals(f.action()) ? "APPROVED" : "REJECTED");
                row.setReviewNote(note);
                row.setReviewedAt(now());
            } else {
                append(c, row, ("RESOLVE".equals(f.action()) ? "标记解决：" : "关闭工单：") + note);
            }
            row.setStatus(Set.of("APPROVE", "RESOLVE").contains(f.action()) ? "RESOLVED" : "CLOSED");
            row.setUpdateTime(now());
            write(tickets.updateById(row));
            return save(c, f.requestId(), hash, row, f.action());
        });
    }

    // Same first lock as customer funding / credential rotation: owner -> client -> ticket.
    // No project lock is acquired, no supplier request is made, and no money is mutated.
    private void lockOwner(Caller c) {
        if (users.selectByIdForUpdate(c.ownerId()) == null) throw missing();
        requireEnabled();
        keys.recheckTickets(c, true);
    }

    private ProjectClientTicket lockTicket(Caller c, String id) {
        var before = owned(c, id, false);
        client(c, before.getClientId(), true);
        return owned(c, id, true);
    }

    private ProjectClient client(Caller c, String id, boolean lock) {
        uuid(id);
        var row = lock ? clients.lock(id) : clients.selectById(id);
        if (row == null || !c.ownerId().equals(row.getOwnerId())
                || (c.clientId() != null && !c.clientId().equals(id))) throw missing();
        return row;
    }

    private ProjectClientTicket owned(Caller c, String id, boolean lock) {
        uuid(id);
        var row = lock ? tickets.lock(id) : tickets.selectById(id);
        if (row == null || !c.ownerId().equals(row.getOwnerId())
                || (c.clientId() != null && !c.clientId().equals(row.getClientId()))) throw missing();
        return row;
    }

    private void append(Caller c, ProjectClientTicket row, String content) {
        var reply = new ProjectClientTicketReply();
        reply.setId(UUID.randomUUID().toString());
        reply.setTicketId(row.getId());
        reply.setTicketVersion(row.getVersion());
        reply.setAuthor(c.clientId() == null ? "OWNER" : "CUSTOMER");
        reply.setContent(content);
        reply.setCreateTime(now());
        write(replies.insert(reply));
    }

    private ProjectClientTicketCommand find(Caller c, String requestId) {
        return commands.selectOne(new LambdaQueryWrapper<ProjectClientTicketCommand>()
                .eq(ProjectClientTicketCommand::getOwnerId, c.ownerId())
                .eq(ProjectClientTicketCommand::getActorSubject, actor(c))
                .eq(ProjectClientTicketCommand::getRequestId, requestId));
    }

    private Receipt existing(Caller c, String requestId, String hash) {
        var row = find(c, requestId);
        if (row == null) return null;
        if (!row.getRequestHash().equals(hash)) throw bad("请求编号已用于其他内容，请先查询原请求结果");
        return receipt(row);
    }

    private Receipt save(Caller c, String requestId, String hash, ProjectClientTicket ticket, String action) {
        var row = new ProjectClientTicketCommand();
        row.setId(UUID.randomUUID().toString());
        row.setOwnerId(c.ownerId());
        row.setActorSubject(actor(c));
        row.setRequestId(requestId);
        row.setRequestHash(hash);
        row.setTicketId(ticket.getId());
        row.setAction(action);
        row.setTicketVersion(ticket.getVersion());
        row.setCreateTime(now());
        write(commands.insert(row));
        return receipt(row);
    }

    private Receipt receipt(ProjectClientTicketCommand r) {
        return new Receipt(r.getRequestId(), r.getTicketId(), r.getAction(), r.getTicketVersion(), r.getCreateTime(),
                "仅记录本平台下游售后；不发送上游工单，不执行补偿入账或退款。");
    }

    private TicketView view(ProjectClientTicket r) {
        return new TicketView(r.getId(), r.getClientId(), r.getProjectId(), r.getProjectTitle(), r.getKind(),
                r.getTitle(), r.getDescription(), r.getRequestedAmount().toPlainString(), r.getStatus(), r.getVersion(),
                r.getReviewResult(), r.getReviewNote(), r.getReviewedAt(), r.getCreateTime(), r.getUpdateTime());
    }

    private void start(Caller c, boolean consent) {
        keys.recheckTickets(c, true);
        requireEnabled();
        if (!consent) throw bad("请确认工单内容与本地处理范围");
        if (!limits.isEnabled() || !limits.isFailClosed()) throw bad("工单写入要求故障时拒绝的安全限流");
        var decision = limiter.check(new RateLimitRequest("project-client-ticket", c.ownerId().toString(),
                60, Duration.ofMinutes(1), "project-client-ticket"));
        if (decision == null) throw bad("安全限流暂不可用");
        if (!decision.allowed()) throw new RateLimitExceededException(decision.retryAfterSeconds());
    }

    private void requireEnabled() {
        if (!enabled) throw bad("下游本地工单尚未开放；已有记录仍可查询");
    }

    private void validate(Object value) {
        if (value == null || !validator.validate(value).isEmpty()) throw bad("请检查工单参数、文本长度与申请金额");
    }

    private static void version(ProjectClientTicket row, long expected) {
        if (row.getVersion() != expected) throw bad("工单已更新，请查询最新内容后重新确认，不要自动重试");
    }

    private static void open(ProjectClientTicket row) {
        if (!Set.of("OPEN", "IN_PROGRESS").contains(row.getStatus())) throw bad("工单已结束，不能重复回复或处理");
    }

    private static String text(String value, int max) {
        String s = value.replace("\r\n", "\n").trim();
        if (s.isBlank() || s.length() > max || s.codePoints().anyMatch(c -> Character.isISOControl(c) && c != '\n' && c != '\t'))
            throw bad("工单仅接受规定长度的纯文本，请勿填写密钥或账号密码");
        return s;
    }

    private static void uuid(String value) {
        try {
            if (!UUID.fromString(value).toString().equals(value)) throw new IllegalArgumentException();
        } catch (RuntimeException e) { throw bad("记录编号不正确"); }
    }

    private static String hash(Object... values) {
        try { return TokenHashUtil.sha256(JSON.writeValueAsString(values)); }
        catch (Exception e) { throw bad("工单请求格式不正确"); }
    }

    private static String actor(Caller c) { return c.clientId() == null ? "OWNER" : c.clientId(); }
    private static LocalDateTime now() { return ServiceTime.now(); }
    private static void filter(String value, Set<String> allowed) {
        if (value != null && !allowed.contains(value)) throw bad("筛选条件不正确");
    }
    private static void bounds(int p, int size) {
        if (p < 1 || p > 10000 || size < 1 || size > 100) throw bad("分页参数不正确");
    }
    private static void write(int changed) {
        if (changed != 1) throw bad("工单未完整保存，事务已回滚；请先查询原请求结果");
    }
    private static BusinessException bad(String message) { return new BusinessException(message); }
    private static BusinessException missing() { return new BusinessException(ResultCode.NOT_FOUND); }
    private <T> T tx(Supplier<T> body) { return new TransactionTemplate(transactions).execute(s -> body.get()); }
}
