package com.course.platform.service.impl;

import com.course.platform.common.exception.BusinessException;
import com.course.platform.common.result.ResultCode;
import com.course.platform.domain.dto.CourseWalletTransferRequest;
import com.course.platform.domain.entity.*;
import com.course.platform.infra.persistence.mapper.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Objects;

/** 课程专款资金闭环，所有写入遵循用户→协议→钱包锁序。 */
@Service
@RequiredArgsConstructor
public class CourseReservedWalletService {
    /** 仅控制新增预存，已有资金退款和审批转回始终保留。 */
    @org.springframework.beans.factory.annotation.Value("${course.private-wallet.enabled:false}")
    private boolean enabled = true;
    private static final BigDecimal MAX = new BigDecimal("9999999999.99");
    private final UserMapper users;
    private final CoursePlatformMapper products;
    private final CoursePrivateContractMapper contracts;
    private final CourseReservedWalletMapper wallets;
    private final CourseReservedWalletLedgerMapper ledgers;
    private final CourseReservedTransferMapper transfers;
    private final CoursePrivateContractHistoryMapper histories;
    private final AccountLedgerServiceImpl generalLedger;
    private final ObjectMapper json;
    private final Clock courseWalletClock;
    // 手工构造的旧资金夹具不需要运营依赖，生产容器必须注入实际周期服务。
    @org.springframework.beans.factory.annotation.Autowired
    private CoursePrivateTermService privateTerms;
    @org.springframework.beans.factory.annotation.Autowired private CoursePrivateLegacySyncService legacySync;

    @Transactional(rollbackFor = Exception.class, isolation=org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public CourseReservedTransfer transferIn(Long walletId, Long userId, CourseWalletTransferRequest request) {
        BigDecimal amount = money(request.getAmount(), false);
        requestKey(request.getRequestKey());
        CourseReservedWallet initial = owned(walletId, userId);
        User user = users.selectByIdForUpdate(userId);
        if (user == null || !Integer.valueOf(1).equals(user.getStatus())) throw new BusinessException(ResultCode.ACCOUNT_DISABLED);
        CourseReservedTransfer existing = transfers.selectByRequestKey(userId, request.getRequestKey());
        if (existing != null) return sameTransfer(existing, walletId, amount, "TRANSFER_IN");
        // 开关只阻止新资金操作，已经提交的同键重试仍应返回真实原结果。
        if (!enabled) throw conflict("课程密价预存功能尚未开启");
        CoursePrivateContract contract = contracts.selectByIdForUpdate(initial.getContractId());
        CourseReservedWallet wallet = wallets.selectByIdForUpdate(walletId);
        CoursePricingService.requireBinding(contract, wallet, userId, initial.getPlatformId());
        requireTransferAllowed(contract, wallet);
        String bizNo = transferNo();
        generalLedger.debit(userId, amount, AccountLedgerServiceImpl.BIZ_COURSE_TRANSFER,
                bizNo, "普通余额转入课程专户");
        change(wallet, amount, 1, "TRANSFER_IN", bizNo, null, userId, "普通余额转入课程专户");
        CourseReservedTransfer transfer = insertTransfer(wallet, amount, request, "TRANSFER_IN", bizNo, userId, null);
        // 未激活钱包不能消费，故成功转入后的真实余额就是激活依据，失败操作不计入。
        if (legacy(contract) && "PENDING_ACTIVATION".equals(contract.getStatus()) && wallet.getBalance().compareTo(contract.getMinInitialDeposit()) >= 0) {
            String before = snapshot(contract);
            Integer expected = contract.getVersion();
            contract.setStatus("ACTIVE"); contract.setActivatedAt(now()); contract.setUpdatedBy(userId);
            if (contracts.updateConfig(contract, expected) != 1) throw conflict("协议已变化，请重试");
            contract.setVersion(expected + 1);
            history(contract, userId, "成功划转激活：" + bizNo, before);
        }
        if(!legacy(contract)){if(privateTerms==null)throw conflict("权益周期服务不可用");privateTerms.afterWalletChanged(userId,walletId);}
        return transfer;
    }

    /** 财务处理路径必须由控制器独立权限校验；不提供代理自行转出接口。 */
    @Transactional(rollbackFor = Exception.class, isolation=org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public CourseReservedTransfer release(Long walletId, Long operatorId, CourseWalletTransferRequest request) {
        var authentication = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !Objects.equals(authentication.getPrincipal(), operatorId)
                || authentication.getAuthorities().stream().noneMatch(a -> "course-wallet:release".equals(a.getAuthority())))
            throw new BusinessException(ResultCode.FORBIDDEN);
        User operator = users.selectById(operatorId);
        if (operator == null || !Integer.valueOf(1).equals(operator.getStatus())) throw new BusinessException(ResultCode.ACCOUNT_DISABLED);
        BigDecimal amount = money(request.getAmount(), false); requestKey(request.getRequestKey());
        if (request.getReason() == null || request.getReason().isBlank() || request.getReason().length() > 255)
            throw new BusinessException(ResultCode.PARAM_ERROR, "财务转回必须填写不超过255字的审批原因");
        CourseReservedWallet initial = wallets.selectById(walletId);
        if (initial == null) throw missing();
        if (users.selectByIdForUpdate(initial.getUserId()) == null) throw missing();
        CourseReservedTransfer existing = transfers.selectByRequestKey(initial.getUserId(), request.getRequestKey());
        if (existing != null) return sameTransfer(existing, walletId, amount, "TRANSFER_OUT");
        CoursePrivateContract contract = contracts.selectByIdForUpdate(initial.getContractId());
        CourseReservedWallet wallet = wallets.selectByIdForUpdate(walletId);
        CoursePricingService.requireBinding(contract, wallet, initial.getUserId(), initial.getPlatformId());
        // 管理员可批准未用预存款转回；已扣订单款不在可用余额中，退款仍回原钱包。
        String bizNo = transferNo();
        change(wallet, amount, -1, "TRANSFER_OUT", bizNo, null, operatorId, request.getReason());
        generalLedger.credit(wallet.getUserId(), amount, AccountLedgerServiceImpl.BIZ_COURSE_TRANSFER, bizNo, "财务批准课程专户转回：" + request.getReason(), false);
        var result=insertTransfer(wallet, amount, request, "TRANSFER_OUT", bizNo, operatorId, operatorId);
        if(!legacy(contract)){if(privateTerms==null)throw conflict("权益周期服务不可用");privateTerms.afterWalletChanged(wallet.getUserId(),walletId);}
        return result;
    }

    @Transactional(rollbackFor = Exception.class)
    public void charge(CourseOrder order) {
        users.selectByIdForUpdate(order.getUserId());
        CoursePrivateContract contract = contracts.selectByIdForUpdate(order.getPrivateContractId());
        CourseReservedWallet wallet = wallets.selectByIdForUpdate(order.getWalletId());
        CoursePricingService.requireBinding(contract, wallet, order.getUserId(), order.getPlatformId());
        BigDecimal amount = money(order.getAmount(), false);
        CourseReservedWalletLedger existing = ledgers.selectByBizKey(wallet.getId(), "ORDER", order.getOrderNo(), -1);
        if (existing != null) { sameLedger(existing, amount); return; }
        if (!"ACTIVE".equals(contract.getStatus()) || contract.getActivatedAt() == null
                || contract.getValidFrom() != null && now().isBefore(contract.getValidFrom())
                || contract.getValidUntil() != null && !now().isBefore(contract.getValidUntil()))
            throw conflict("专户协议当前不可购买");
        if (contract.getFixedPrice().compareTo(amount) != 0 || !Objects.equals(contract.getVersion(), order.getContractVersion()))
            throw conflict("成交价或协议版本已变化，请重新确认");
        CoursePrivateContractTerm term=null;
        if(!legacy(contract)){if(privateTerms==null)throw conflict("权益周期服务不可用");term=privateTerms.requireOrderBinding(order);}
        change(wallet, amount, -1, "ORDER", order.getOrderNo(), order.getOrderNo(), order.getUserId(), "课程专户订单消费");
        // 成功扣款和周期关联必须一起提交，关联失败会回滚原订单及原钱包出账。
        if(term!=null)privateTerms.bindOrder(order,term);
    }

    @Transactional(rollbackFor = Exception.class)
    public void refund(CourseOrder order, Long operatorId) {
        if (users.selectByIdForUpdate(order.getUserId()) == null) throw missing();
        CourseReservedWallet wallet = wallets.selectByIdForUpdate(order.getWalletId());
        if (wallet == null || !Objects.equals(wallet.getUserId(), order.getUserId()) || !Objects.equals(wallet.getPlatformId(), order.getPlatformId())
                || !Objects.equals(wallet.getContractId(), order.getPrivateContractId())) throw conflict("原订单支付钱包绑定异常");
        BigDecimal amount = money(order.getAmount(), false);
        // 退款不依赖当前协议状态和价格，但必须有同额原始出账作为来源。
        CourseReservedWalletLedger debit = ledgers.selectByBizKey(wallet.getId(), "ORDER", order.getOrderNo(), -1);
        if (debit == null) throw conflict("原订单扣款流水不存在，不能退款");
        sameLedger(debit, amount);
        CourseReservedWalletLedger existing = ledgers.selectByBizKey(wallet.getId(), "REFUND", order.getOrderNo(), 1);
        if (existing != null) { sameLedger(existing, amount); return; }
        change(wallet, amount, 1, "REFUND", order.getOrderNo(), order.getOrderNo(), operatorId, "订单退款回原课程专户");
    }

    public CourseReservedWallet owned(Long id, Long userId) {
        CourseReservedWallet wallet = wallets.selectById(id);
        if (wallet == null || !Objects.equals(wallet.getUserId(), userId)) throw missing();
        return wallet;
    }
    private void requireTransferAllowed(CoursePrivateContract contract, CourseReservedWallet wallet) {
        CoursePlatform product = products.selectById(wallet.getPlatformId());
        if (product == null || !Integer.valueOf(1).equals(product.getStatus())) throw conflict("课程商品已下架或删除，不能转入");
        if(!legacy(contract)){if("SUSPENDED".equals(contract.getStatus())||"TERMINATED".equals(contract.getStatus()))throw conflict("协议已暂停或终止，不能转入");return;}
        if (!"ACTIVE".equals(contract.getStatus()) && !"PENDING_ACTIVATION".equals(contract.getStatus())
                || contract.getValidUntil() != null && !now().isBefore(contract.getValidUntil())) throw conflict("协议已暂停、到期或终止，不能转入");
    }
    private void change(CourseReservedWallet wallet, BigDecimal amount, int direction, String type, String bizNo, String orderNo, Long operatorId, String remark) {
        BigDecimal before = wallet.getBalance(); BigDecimal after = direction == 1 ? before.add(amount) : before.subtract(amount);
        if (after.signum() < 0) throw new BusinessException(ResultCode.BALANCE_INSUFFICIENT, "课程专户余额不足");
        if (after.compareTo(MAX) > 0) throw conflict("课程专户余额超出存储上限");
        int updated = direction == 1 ? wallets.increaseBalance(wallet.getId(), amount) : wallets.decreaseBalance(wallet.getId(), amount);
        if (updated != 1) throw conflict("课程专户余额更新失败");
        CourseReservedWalletLedger ledger = new CourseReservedWalletLedger();
        ledger.setWalletId(wallet.getId()); ledger.setUserId(wallet.getUserId()); ledger.setBizType(type); ledger.setBizNo(bizNo); ledger.setDirection(direction);
        ledger.setAmount(amount); ledger.setBalanceBefore(before); ledger.setBalanceAfter(after); ledger.setRelatedOrderNo(orderNo); ledger.setOperatorId(operatorId); ledger.setRemark(remark);
        // 资金账龄与资格窗口使用同一业务Clock，不依赖通用自动填充的机器时间。
        ledger.setCreateTime(now());
        if (ledgers.insert(ledger) != 1) throw conflict("课程专户流水写入失败");
        wallet.setBalance(after);
    }
    private CourseReservedTransfer insertTransfer(CourseReservedWallet wallet, BigDecimal amount, CourseWalletTransferRequest request, String type, String bizNo, Long operatorId, Long approvedBy) {
        CourseReservedTransfer result = new CourseReservedTransfer(); result.setWalletId(wallet.getId()); result.setUserId(wallet.getUserId()); result.setAmount(amount);
        result.setType(type); result.setRequestKey(request.getRequestKey()); result.setBizNo(bizNo); result.setStatus("SUCCEEDED"); result.setOperatorId(operatorId); result.setApprovedBy(approvedBy);
        result.setReason(request.getReason()); result.setCompletedAt(now());
        if (transfers.insert(result) != 1) throw conflict("资金划转单写入失败"); return result;
    }
    private CourseReservedTransfer sameTransfer(CourseReservedTransfer value, Long walletId, BigDecimal amount, String type) {
        if (!Objects.equals(value.getWalletId(), walletId) || value.getAmount().compareTo(amount) != 0 || !type.equals(value.getType())) throw conflict("资金幂等键冲突");
        return value;
    }
    private void sameLedger(CourseReservedWalletLedger value, BigDecimal amount) { if (value.getAmount().compareTo(amount) != 0) throw conflict("资金幂等键金额冲突"); }
    public void history(CoursePrivateContract value, Long operator, String reason, String before) {
        var history = new CoursePrivateContractHistory(); history.setContractId(value.getId()); history.setOperatorId(operator); history.setReason(reason); history.setBeforeJson(before); history.setAfterJson(snapshot(value));
        if (histories.insert(history) != 1) throw conflict("协议审计写入失败");
        // 原创建、改价和资金激活共用此审计入口，兼容周期与原操作一并提交。
        if(legacy(value)&&legacySync!=null)legacySync.sync(value.getId(),operator,reason);
    }
    public String snapshot(CoursePrivateContract value) { try { return json.writeValueAsString(value); } catch (Exception e) { throw conflict("协议审计序列化失败"); } }
    public static BigDecimal money(BigDecimal amount, boolean zeroAllowed) {
        if (amount == null || (zeroAllowed ? amount.signum() < 0 : amount.signum() <= 0) || amount.compareTo(MAX) > 0 || amount.stripTrailingZeros().scale() > 2)
            throw new BusinessException(ResultCode.PARAM_ERROR, "金额范围或精度无效，最多两位小数");
        return amount.setScale(2, RoundingMode.UNNECESSARY);
    }
    private void requestKey(String key) { if (key == null || !key.matches("[A-Za-z0-9_-]{1,64}")) throw new BusinessException(ResultCode.PARAM_ERROR,"必须提供1到64位请求幂等键"); }
    // 请求键保证幂等，业务单号用于跨账本关联，首次操作生成后由划转单保存。
    private String transferNo() { return "CWT" + java.util.UUID.randomUUID().toString().replace("-", ""); }
    private boolean legacy(CoursePrivateContract contract){return contract.getOperationsMode()==null||"LEGACY_PERMANENT".equals(contract.getOperationsMode());}
    private LocalDateTime now() { return LocalDateTime.now(courseWalletClock).truncatedTo(java.time.temporal.ChronoUnit.SECONDS); }
    private BusinessException missing() { return new BusinessException(ResultCode.NOT_FOUND,"课程专户不存在"); }
    private BusinessException conflict(String message) { return new BusinessException(ResultCode.CONFLICT,message); }
}
