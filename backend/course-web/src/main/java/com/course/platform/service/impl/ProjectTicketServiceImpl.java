package com.course.platform.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.course.platform.application.service.projectcenter.*;
import com.course.platform.application.service.projectcenter.ProjectAccountAccess.Context;
import com.course.platform.common.exception.BusinessException;
import com.course.platform.common.result.ResultCode;
import com.course.platform.common.security.SecretCrypto;
import com.course.platform.config.RateLimitProperties;
import com.course.platform.domain.projectcenter.*;
import com.course.platform.domain.projectcenter.ProjectTicketTypes.*;
import com.course.platform.domain.servicecommerce.ServiceTime;
import com.course.platform.infra.persistence.mapper.*;
import com.course.platform.security.*;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import java.util.function.Supplier;

/**
 * Owner-bound text support, with a durable draft → single dispatch → local receipt lifecycle. No
 * supplier-wide ticket listing and no ledger mutation (including approved compensation).
 */
@Service
@RequiredArgsConstructor
public class ProjectTicketServiceImpl implements ProjectTicketService {
    private final ServiceProjectTicketMapper tickets;
    private final ServiceProjectTicketOperationMapper operations;
    private final ProjectAccountAccess access;
    private final ProjectTicketGateway gateway;
    private final RateLimitService limiter;
    private final RateLimitProperties limits;
    private final PlatformTransactionManager transactions;

    @Value("${app.native-services.enabled:false}")
    private boolean enabled;

    @Value("${app.crypto.secret}")
    private String cryptoSecret;

    private static final ObjectMapper JSON = new ObjectMapper().findAndRegisterModules();

    @Override
    public IPage<TicketView> tickets(int page, int size, String accountId, boolean admin) {
        Long uid = user();
        if (admin) admin();
        bounds(page, size);
        if (accountId != null) uuid(accountId);
        var query =
                new LambdaQueryWrapper<ServiceProjectTicket>()
                        .eq(!admin, ServiceProjectTicket::getUserId, uid)
                        .eq(accountId != null, ServiceProjectTicket::getAccountId, accountId)
                        .orderByDesc(ServiceProjectTicket::getCreateTime);
        var rows = tickets.selectPage(new Page<>(page, size), query);
        var result = new Page<TicketView>(page, size, rows.getTotal());
        result.setRecords(rows.getRecords().stream().map(t -> view(t, admin, false)).toList());
        return result;
    }

    @Override
    public TicketView ticket(String id, boolean admin) {
        return view(owned(id, admin), admin, true);
    }

    @Override
    public TicketView refresh(String id, boolean admin) {
        Long uid = user();
        var t = owned(id, admin);
        rate(uid, "read", 30);
        if (t.getRemoteTicketId() == null) throw bad("尚未取得可核实的上游工单绑定，请检查原操作");
        var ctx = access.forTickets(t.getAccountId(), admin);
        var receipt =
                sanitize(
                        gateway.ticket(
                                ctx.provider(),
                                t.getRemoteProjectId(),
                                ctx.customerKey(),
                                t.getRemoteTicketId()),
                        ctx);
        checkReceipt(t, receipt);
        return tx(
                () -> {
                    var current = tickets.lock(id);
                    if (!Objects.equals(current.getVersion(), t.getVersion()))
                        throw bad("工单状态已经变化，请重新读取");
                    // Read-only refresh never resolves an uncertain write, even if the text looks
                    // identical.
                    current.setSnapshotEncrypted(encrypt(receipt));
                    current.setCheckedAt(now());
                    touch(current);
                    write(tickets.updateById(current));
                    return view(current, admin, true);
                });
    }

    @Override
    public OperationView submit(String accountId, SubmitForm f) {
        Long uid = user();
        if (f == null
                || !f.confirmedPolicy()
                || !Set.of("suggestion", "bug", "compensation")
                        .contains(f.type() == null ? "" : f.type())) throw bad("请确认工单类型和提交规则");
        var ctx = access.forTickets(accountId, false);
        text(f.title(), 1, 120, ctx);
        text(f.description(), 1, 4000, ctx);
        BigDecimal amount =
                f.compensationAmount() == null ? BigDecimal.ZERO : f.compensationAmount();
        if (amount.signum() < 0
                || amount.compareTo(new BigDecimal("100000")) > 0
                || amount.stripTrailingZeros().scale() > 6
                || ("compensation".equals(f.type()) ? amount.signum() <= 0 : amount.signum() != 0))
            throw bad("补偿申请须填写正额度，非补偿工单不得附带补偿额度");
        rate(uid, "draft", 20);
        var normalized =
                new SubmitForm(f.type(), f.title().trim(), f.description().trim(), amount, true);
        return tx(
                () -> {
                    var t = new ServiceProjectTicket();
                    t.setId(newId());
                    t.setAccountId(accountId);
                    t.setUserId(uid);
                    t.setProjectId(ctx.account().getProjectId());
                    t.setProviderId(ctx.provider().getId());
                    t.setProviderIdentity(ctx.account().getProviderIdentity());
                    t.setRemoteProjectId(ctx.account().getRemoteProjectId());
                    t.setProjectTitle(ctx.projectTitle());
                    t.setState("DRAFT");
                    t.setRequestEncrypted(encrypt(normalized));
                    t.setVersion(0L);
                    t.setCreateTime(now());
                    t.setUpdateTime(now());
                    write(tickets.insert(t));
                    var op = prepare(t, "SUBMIT", normalized, ctx, uid);
                    return opView(op, false);
                });
    }

    @Override
    public OperationView reply(String ticketId, ReplyForm f) {
        Long uid = user();
        var seed = owned(ticketId, false);
        var ctx = access.forTickets(seed.getAccountId(), false);
        if (f == null || !f.confirmedPolicy()) throw bad("请确认发送本人项目工单的回复");
        text(f.content(), 1, 4000, ctx);
        rate(uid, "draft", 20);
        return tx(
                () -> {
                    var t = tickets.lock(ticketId);
                    ready(t, f.version());
                    return opView(
                            prepare(
                                    t,
                                    "REPLY",
                                    new ReplyForm(f.content().trim(), f.version(), true),
                                    ctx,
                                    uid),
                            false);
                });
    }

    @Override
    public OperationView review(String ticketId, ReviewForm f) {
        Long uid = user();
        reconciler();
        var seed = owned(ticketId, true);
        var ctx = access.forTickets(seed.getAccountId(), true);
        if (f == null
                || !f.upstreamChecked()
                || !Set.of("approved", "rejected").contains(f.result() == null ? "" : f.result()))
            throw bad("须核实上游工单后选择补偿审核结论");
        text(f.note(), 10, 1000, ctx);
        rate(uid, "review", 10);
        return tx(
                () -> {
                    var t = tickets.lock(ticketId);
                    ready(t, f.version());
                    var receipt = decode(t.getSnapshotEncrypted(), Receipt.class);
                    if (!"compensation".equals(receipt.type()) || !receipt.reviewResult().isEmpty())
                        throw bad("只有尚未审核的补偿工单可以提交审核");
                    return opView(
                            prepare(
                                    t,
                                    "REVIEW",
                                    new ReviewForm(f.result(), f.note().trim(), f.version(), true),
                                    ctx,
                                    uid),
                            true);
                });
    }

    private ServiceProjectTicketOperation prepare(
            ServiceProjectTicket t, String action, Object payload, Context ctx, Long uid) {
        var op = new ServiceProjectTicketOperation();
        op.setId(newId());
        op.setTicketId(t.getId());
        op.setActorId(uid);
        op.setAction(action);
        op.setState("READY");
        op.setProviderVersion(ctx.provider().getConfigVersion());
        op.setTicketVersion(t.getVersion() + 1);
        op.setPayloadEncrypted(encrypt(payload));
        op.setExpiresAt(now().plusMinutes(10));
        op.setCreateTime(now());
        op.setUpdateTime(now());
        write(operations.insert(op));
        t.setPendingOperationId(op.getId());
        touch(t);
        write(tickets.updateById(t));
        return op;
    }

    private record Dispatch(
            ServiceProjectTicket ticket, ServiceProjectTicketOperation operation, Context context) {
        @Override
        public String toString() {
            return "TicketDispatch[REDACTED]";
        }
    }

    @Override
    public OperationView confirm(String id, boolean admin) {
        Long uid = user();
        var seed = ownedOperation(id, admin);
        mutationAuthority(seed, uid, admin);
        rate(uid, "confirm", 20);
        Dispatch d =
                tx(
                        () -> {
                            var t = tickets.lock(seed.getTicketId());
                            var op = operations.lock(id);
                            if (!"READY".equals(op.getState())) return null;
                            if (!op.getExpiresAt().isAfter(now())) {
                                expire(t, op);
                                return null;
                            }
                            if (!id.equals(t.getPendingOperationId())) throw bad("工单操作已失效");
                            if (!Objects.equals(t.getVersion(), op.getTicketVersion()))
                                throw bad("工单回执已更新，请等待本预览过期后重新核实");
                            var ctx = access.forTickets(t.getAccountId(), admin);
                            if (!Objects.equals(
                                    op.getProviderVersion(), ctx.provider().getConfigVersion()))
                                throw bad("上游配置已变化，请等待预览过期后重新创建");
                            op.setState("DISPATCHING");
                            op.setUpdateTime(now());
                            write(operations.updateById(op));
                            return new Dispatch(t, op, ctx);
                        });
        if (d == null) return operation(id, admin);
        try {
            var t = d.ticket();
            var op = d.operation();
            var c = d.context();
            Receipt receipt;
            switch (op.getAction()) {
                case "SUBMIT" ->
                        receipt =
                                gateway.submitTicket(
                                        c.provider(),
                                        t.getRemoteProjectId(),
                                        c.customerKey(),
                                        decode(op.getPayloadEncrypted(), SubmitForm.class));
                case "REPLY" ->
                        receipt =
                                gateway.replyTicket(
                                        c.provider(),
                                        t.getRemoteProjectId(),
                                        c.customerKey(),
                                        t.getRemoteTicketId(),
                                        decode(op.getPayloadEncrypted(), ReplyForm.class)
                                                .content());
                case "REVIEW" -> {
                    var f = decode(op.getPayloadEncrypted(), ReviewForm.class);
                    receipt =
                            gateway.reviewTicket(
                                    c.provider(),
                                    t.getRemoteProjectId(),
                                    t.getRemoteTicketId(),
                                    f.result(),
                                    f.note());
                }
                default -> throw bad("工单操作类型不合法");
            }
            receipt = sanitize(receipt, c);
            checkReceipt(t, receipt);
            settle(id, receipt, null, null);
        } catch (Exception e) {
            unknown(id);
        }
        return operation(id, admin);
    }

    @Override
    public OperationView operation(String id, boolean admin) {
        var op = ownedOperation(id, admin);
        if ("DISPATCHING".equals(op.getState())
                && op.getUpdateTime().isBefore(now().minusMinutes(2))) unknown(id);
        if ("READY".equals(op.getState()) && !op.getExpiresAt().isAfter(now()))
            tx(
                    () -> {
                        var t = tickets.lock(op.getTicketId());
                        var current = operations.lock(id);
                        if ("READY".equals(current.getState())
                                && !current.getExpiresAt().isAfter(now())) expire(t, current);
                        return null;
                    });
        return opView(operations.selectById(id), admin);
    }

    @Override
    public OperationView resolve(String id, ResolveForm f) {
        Long uid = user();
        reconciler();
        var op = ownedOperation(id, true);
        if (f == null
                || !f.upstreamChecked()
                || !Set.of("ACCEPTED", "NOT_ACCEPTED")
                        .contains(f.outcome() == null ? "" : f.outcome()))
            throw bad("请提供已经逐项核实的上游受理结论");
        var t = owned(op.getTicketId(), true);
        text(f.evidence(), 10, 1000, null);
        rate(uid, "resolve", 10);
        if (Set.of("SUCCEEDED", "NOT_ACCEPTED").contains(op.getState())) return opView(op, true);
        if (!"UNKNOWN".equals(op.getState())) throw bad("只能人工核对结果未知的工单操作");
        Receipt receipt = null;
        if ("ACCEPTED".equals(f.outcome())) {
            // The evidenced source DTO omits customer_id. A manually supplied ticket id cannot
            // prove ownership.
            if ("SUBMIT".equals(op.getAction()))
                throw bad("上游协议未返回可独立验证的客户归属，不能凭工单编号认领未知提交；请联系上游提供归属核实协议");
            var ctx = access.forTickets(t.getAccountId(), true);
            receipt =
                    sanitize(
                            gateway.ticket(
                                    ctx.provider(),
                                    t.getRemoteProjectId(),
                                    ctx.customerKey(),
                                    t.getRemoteTicketId()),
                            ctx);
            checkReceipt(t, receipt);
            if ("REPLY".equals(op.getAction())) {
                var reply = decode(op.getPayloadEncrypted(), ReplyForm.class);
                if (receipt.replies().stream()
                        .noneMatch(
                                r ->
                                        "customer".equals(r.sender())
                                                && reply.content().equals(r.content())))
                    throw bad("当前回执未包含已核实的原回复，请继续核对");
            }
            if ("REVIEW".equals(op.getAction())) {
                var review = decode(op.getPayloadEncrypted(), ReviewForm.class);
                if (!review.result().equals(receipt.reviewResult())
                        || !review.note().equals(receipt.reviewNote()))
                    throw bad("当前上游审核回执不匹配原请求，请继续核对");
            }
        }
        settle(id, receipt, uid, f);
        return operation(id, true);
    }

    private void settle(String id, Receipt receipt, Long resolver, ResolveForm resolution) {
        var seed = operations.selectById(id);
        tx(
                () -> {
                    var t = tickets.lock(seed.getTicketId());
                    var op = operations.lock(id);
                    boolean manual = resolution != null;
                    if (!(manual ? "UNKNOWN" : "DISPATCHING").equals(op.getState())
                            || !id.equals(t.getPendingOperationId())) return null;
                    boolean accepted = !manual || "ACCEPTED".equals(resolution.outcome());
                    if (accepted) {
                        checkReceipt(t, receipt);
                        if ("SUBMIT".equals(op.getAction())) t.setRemoteTicketId(receipt.id());
                        t.setSnapshotEncrypted(encrypt(receipt));
                        t.setCheckedAt(now());
                        t.setState("ACTIVE");
                        op.setState("SUCCEEDED");
                    } else {
                        t.setState(t.getRemoteTicketId() == null ? "CANCELLED" : "ACTIVE");
                        op.setState("NOT_ACCEPTED");
                    }
                    if (manual) {
                        op.setResolvedBy(resolver);
                        op.setResolutionEvidenceEncrypted(encrypt(resolution));
                    }
                    t.setPendingOperationId(null);
                    touch(t);
                    op.setUpdateTime(now());
                    write(tickets.updateById(t));
                    write(operations.updateById(op));
                    return null;
                });
    }

    private void unknown(String id) {
        var seed = operations.selectById(id);
        if (seed == null) return;
        tx(
                () -> {
                    var t = tickets.lock(seed.getTicketId());
                    var op = operations.lock(id);
                    if (!"DISPATCHING".equals(op.getState())
                            || !id.equals(t.getPendingOperationId())) return null;
                    op.setState("UNKNOWN");
                    op.setUpdateTime(now());
                    t.setState("UNKNOWN");
                    touch(t);
                    write(tickets.updateById(t));
                    write(operations.updateById(op));
                    return null;
                });
    }

    private void expire(ServiceProjectTicket t, ServiceProjectTicketOperation op) {
        op.setState("EXPIRED");
        op.setUpdateTime(now());
        write(operations.updateById(op));
        if (op.getId().equals(t.getPendingOperationId())) {
            t.setPendingOperationId(null);
            t.setState(t.getRemoteTicketId() == null ? "EXPIRED" : "ACTIVE");
            touch(t);
            write(tickets.updateById(t));
        }
    }

    private void ready(ServiceProjectTicket t, Long version) {
        if (t == null
                || !Objects.equals(t.getVersion(), version)
                || !"ACTIVE".equals(t.getState())
                || t.getPendingOperationId() != null
                || t.getRemoteTicketId() == null) throw bad("工单状态已变化或原操作尚待确认，请检查原操作");
    }

    private void checkReceipt(ServiceProjectTicket t, Receipt r) {
        if (r == null
                || !t.getRemoteProjectId().equals(r.projectId())
                || (t.getRemoteTicketId() != null && !t.getRemoteTicketId().equals(r.id())))
            throw bad("上游工单回执不匹配");
        var f = decode(t.getRequestEncrypted(), SubmitForm.class);
        if (!f.type().equals(r.type())
                || !f.title().equals(r.title())
                || !f.description().equals(r.description())
                || f.compensationAmount().compareTo(r.compensationAmount()) != 0)
            throw bad("上游工单内容与原绑定不匹配，已停止更新");
    }

    private ServiceProjectTicket owned(String id, boolean admin) {
        Long uid = user();
        if (admin) admin();
        uuid(id);
        var t = tickets.selectById(id);
        if (t == null || (!admin && !uid.equals(t.getUserId()))) throw missing();
        return t;
    }

    private ServiceProjectTicketOperation ownedOperation(String id, boolean admin) {
        user();
        if (admin) admin();
        uuid(id);
        var op = operations.selectById(id);
        if (op == null) throw missing();
        owned(op.getTicketId(), admin);
        return op;
    }

    private static void mutationAuthority(
            ServiceProjectTicketOperation op, Long uid, boolean admin) {
        if (admin) {
            reconciler();
            if (!"REVIEW".equals(op.getAction())) throw missing();
        } else if ("REVIEW".equals(op.getAction())) throw missing();
        if (!uid.equals(op.getActorId())) throw missing();
    }

    private TicketView view(ServiceProjectTicket t, boolean admin, boolean detail) {
        var f = decode(t.getRequestEncrypted(), SubmitForm.class);
        var r =
                t.getSnapshotEncrypted() == null
                        ? null
                        : decode(t.getSnapshotEncrypted(), Receipt.class);
        return new TicketView(
                t.getId(),
                t.getAccountId(),
                t.getProjectId(),
                t.getProjectTitle(),
                admin ? t.getUserId() : null,
                t.getState(),
                f.type(),
                f.title(),
                detail ? f.description() : null,
                f.compensationAmount().stripTrailingZeros().toPlainString(),
                r == null ? null : r.status(),
                r == null ? null : r.reviewResult(),
                detail && r != null ? r.reviewNote() : null,
                r != null && r.hasAttachment(),
                detail && r != null ? r.replies() : List.of(),
                t.getVersion(),
                t.getPendingOperationId(),
                t.getCreateTime(),
                t.getCheckedAt());
    }

    private OperationView opView(ServiceProjectTicketOperation op, boolean admin) {
        String content = "", result = null;
        if ("REPLY".equals(op.getAction()))
            content = decode(op.getPayloadEncrypted(), ReplyForm.class).content();
        if ("SUBMIT".equals(op.getAction()))
            content = decode(op.getPayloadEncrypted(), SubmitForm.class).description();
        if ("REVIEW".equals(op.getAction()) && admin) {
            var f = decode(op.getPayloadEncrypted(), ReviewForm.class);
            content = f.note();
            result = f.result();
        }
        List<String> warnings = new ArrayList<>();
        warnings.add("预览仅保存本地草稿，确认后仅发送一次；不要在工单中填写密码、验证码或密钥。");
        warnings.add("补偿申请和审核不会自动增加任何账户余额；审核通过不等于已到账。");
        if (Set.of("DISPATCHING", "UNKNOWN").contains(op.getState()))
            warnings.add("结果未知，请检查原操作；不会自动重发。未知的新工单不能凭任意上游编号认领。");
        return new OperationView(
                op.getId(),
                op.getTicketId(),
                op.getAction(),
                op.getState(),
                content,
                result,
                op.getExpiresAt(),
                op.getCreateTime(),
                List.copyOf(warnings));
    }

    private static Receipt sanitize(Receipt r, Context ctx) {
        if (r == null) throw bad("上游工单回执不完整");
        java.util.function.UnaryOperator<String> redact =
                value ->
                        value == null
                                ? null
                                : value.replace(ctx.customerKey(), "[REDACTED]")
                                        .replace(ctx.provider().getApiKey(), "[REDACTED]");
        return new Receipt(
                r.id(),
                r.projectId(),
                r.type(),
                redact.apply(r.title()),
                redact.apply(r.description()),
                r.compensationAmount(),
                r.status(),
                r.reviewResult(),
                redact.apply(r.reviewNote()),
                r.createdAt(),
                r.updatedAt(),
                r.hasAttachment(),
                r.replies().stream()
                        .map(
                                reply ->
                                        new Reply(
                                                reply.id(),
                                                reply.sender(),
                                                redact.apply(reply.content()),
                                                reply.createdAt(),
                                                reply.hasAttachment()))
                        .toList());
    }

    private String encrypt(Object value) {
        try {
            return SecretCrypto.encrypt(JSON.writeValueAsString(value), cryptoSecret);
        } catch (Exception e) {
            throw bad("工单内容无法安全保存");
        }
    }

    private <T> T decode(String value, Class<T> type) {
        try {
            if (!SecretCrypto.isEncrypted(value)) throw bad("工单记录格式错误");
            return JSON.readValue(SecretCrypto.decrypt(value, cryptoSecret), type);
        } catch (Exception e) {
            throw bad("工单内容无法安全读取");
        }
    }

    private static void text(String value, int min, int max, Context ctx) {
        if (value == null
                || value.trim().length() < min
                || value.length() > max
                || value.codePoints()
                        .anyMatch(
                                c ->
                                        Character.isISOControl(c)
                                                && c != '\n'
                                                && c != '\r'
                                                && c != '\t')) throw bad("工单文本长度或格式不合法");
        if (ctx != null
                && (value.contains(ctx.customerKey())
                        || value.contains(ctx.provider().getApiKey())))
            throw bad("工单内容不得包含项目或上游密钥");
    }

    private Long user() {
        Long uid = SecurityUtils.getCurrentUserId();
        if (!enabled) throw bad("项目中心尚未启用，请先完成迁移与配置");
        return uid;
    }

    private static void admin() {
        SecurityUtils.requireAuthority("api-provider:update");
    }

    private static void reconciler() {
        admin();
        SecurityUtils.requireAuthority("payment:reconcile");
    }

    private void rate(Long uid, String action, int count) {
        if (!limits.isEnabled() || !limits.isFailClosed()) throw bad("项目工单要求故障时拒绝的安全限流");
        var decision =
                limiter.check(
                        new RateLimitRequest(
                                "project-ticket:" + action,
                                uid.toString(),
                                count,
                                Duration.ofMinutes(10),
                                "project-ticket"));
        if (decision == null) throw bad("安全限流暂不可用");
        if (!decision.allowed()) throw new RateLimitExceededException(decision.retryAfterSeconds());
    }

    private static void bounds(int page, int size) {
        if (page < 1 || page > 10000 || size < 1 || size > 100) throw bad("分页参数不合法");
    }

    private static void uuid(String id) {
        if (id == null
                || !id.matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}"))
            throw missing();
    }

    private static String newId() {
        return UUID.randomUUID().toString();
    }

    private static LocalDateTime now() {
        return ServiceTime.now();
    }

    private static void touch(ServiceProjectTicket t) {
        t.setVersion(t.getVersion() + 1);
        t.setUpdateTime(now());
    }

    private static void write(int rows) {
        if (rows != 1) throw bad("工单记录未能保存，请检查原操作");
    }

    private <T> T tx(Supplier<T> work) {
        return new TransactionTemplate(transactions).execute(s -> work.get());
    }

    private static BusinessException bad(String message) {
        return new BusinessException(message);
    }

    private static BusinessException missing() {
        return new BusinessException(ResultCode.NOT_FOUND);
    }
}
