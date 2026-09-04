package com.course.platform.service.impl;

import com.course.platform.common.exception.BusinessException;
import com.course.platform.common.result.ResultCode;
import com.course.platform.domain.entity.AccountLedger;
import com.course.platform.domain.entity.User;
import com.course.platform.infra.persistence.mapper.AccountLedgerMapper;
import com.course.platform.infra.persistence.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Set;

/**
 * 资金账本服务：账户行锁、余额更新和不可变流水处于同一事务。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AccountLedgerServiceImpl {

    public static final String BIZ_PAYMENT = "PAYMENT";
    public static final String BIZ_ORDER = "ORDER";
    public static final String BIZ_RECHARGE = "RECHARGE";
    public static final String BIZ_REFUND = "REFUND";
    public static final String BIZ_API_FEE = "API_FEE";
    public static final String BIZ_ADJUST = "ADJUST";

    private static final Set<String> ALLOWED_BIZ_TYPES = Set.of(
            BIZ_PAYMENT, BIZ_ORDER, BIZ_RECHARGE, BIZ_REFUND, BIZ_API_FEE, BIZ_ADJUST
    );
    private static final BigDecimal MAX_AMOUNT = new BigDecimal("9999999999.99");

    private final UserMapper userMapper;
    private final AccountLedgerMapper accountLedgerMapper;

    @Transactional(rollbackFor = Exception.class)
    public void credit(Long userId, BigDecimal amount, String bizType, String bizNo,
                       String remark, boolean countRecharge) {
        BigDecimal normalizedAmount = validate(userId, amount, bizType, bizNo);

        // 先锁账户，再检查幂等键。相同用户的所有资金操作因此严格串行。
        User user = requireLockedUser(userId);
        AccountLedger existing = accountLedgerMapper.selectByBizKey(userId, bizType, bizNo, 1);
        if (existing != null) {
            requireSameAmount(existing, normalizedAmount);
            log.info("账本入账已存在，幂等返回：userId={}, bizType={}, bizNo={}", userId, bizType, bizNo);
            return;
        }

        BigDecimal before = balanceOf(user);
        BigDecimal after = before.add(normalizedAmount);
        int updated = userMapper.increaseBalance(userId, normalizedAmount, countRecharge ? 1 : 0);
        if (updated != 1) {
            throw new BusinessException(ResultCode.ERROR.getCode(), "余额更新失败");
        }
        insertLedger(userId, bizType, bizNo, 1, normalizedAmount, before, after, remark);
    }

    @Transactional(rollbackFor = Exception.class)
    public void debit(Long userId, BigDecimal amount, String bizType, String bizNo, String remark) {
        BigDecimal normalizedAmount = validate(userId, amount, bizType, bizNo);

        User user = requireLockedUser(userId);
        AccountLedger existing = accountLedgerMapper.selectByBizKey(userId, bizType, bizNo, -1);
        if (existing != null) {
            requireSameAmount(existing, normalizedAmount);
            log.info("账本出账已存在，幂等返回：userId={}, bizType={}, bizNo={}", userId, bizType, bizNo);
            return;
        }

        BigDecimal before = balanceOf(user);
        if (before.compareTo(normalizedAmount) < 0) {
            throw new BusinessException(ResultCode.BALANCE_INSUFFICIENT);
        }
        BigDecimal after = before.subtract(normalizedAmount);
        int updated = userMapper.decreaseBalance(userId, normalizedAmount);
        if (updated != 1) {
            throw new BusinessException(ResultCode.BALANCE_INSUFFICIENT);
        }
        insertLedger(userId, bizType, bizNo, -1, normalizedAmount, before, after, remark);
    }

    private User requireLockedUser(Long userId) {
        User user = userMapper.selectByIdForUpdate(userId);
        if (user == null) {
            throw new BusinessException(ResultCode.USER_NOT_FOUND);
        }
        return user;
    }

    private BigDecimal validate(Long userId, BigDecimal amount, String bizType, String bizNo) {
        if (userId == null || userId <= 0) {
            throw new BusinessException(ResultCode.PARAM_ERROR.getCode(), "用户ID无效");
        }
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0 || amount.compareTo(MAX_AMOUNT) > 0) {
            throw new BusinessException(ResultCode.PARAM_ERROR.getCode(), "金额必须大于0且不超过9999999999.99");
        }
        if (amount.stripTrailingZeros().scale() > 2) {
            throw new BusinessException(ResultCode.PARAM_ERROR.getCode(), "金额最多保留两位小数");
        }
        if (!ALLOWED_BIZ_TYPES.contains(bizType)) {
            throw new BusinessException(ResultCode.PARAM_ERROR.getCode(), "资金业务类型无效");
        }
        if (bizNo == null || bizNo.isBlank() || bizNo.length() > 64) {
            throw new BusinessException(ResultCode.PARAM_ERROR.getCode(), "资金业务单号无效");
        }
        return amount.setScale(2, RoundingMode.UNNECESSARY);
    }

    private BigDecimal balanceOf(User user) {
        return user.getBalance() == null ? BigDecimal.ZERO.setScale(2) : user.getBalance().setScale(2);
    }

    private void requireSameAmount(AccountLedger existing, BigDecimal amount) {
        if (existing.getAmount() == null || existing.getAmount().compareTo(amount) != 0) {
            throw new BusinessException(ResultCode.ERROR.getCode(), "资金幂等键冲突");
        }
    }

    private void insertLedger(Long userId, String bizType, String bizNo, int direction,
                              BigDecimal amount, BigDecimal before, BigDecimal after, String remark) {
        AccountLedger ledger = new AccountLedger();
        ledger.setUserId(userId);
        ledger.setBizType(bizType);
        ledger.setBizNo(bizNo);
        ledger.setDirection(direction);
        ledger.setAmount(amount);
        ledger.setBalanceBefore(before);
        ledger.setBalanceAfter(after);
        ledger.setRemark(remark == null ? null : remark.substring(0, Math.min(remark.length(), 255)));

        // 不吞唯一键或其他写入异常：任何失败都必须回滚同事务中的余额更新。
        if (accountLedgerMapper.insert(ledger) != 1) {
            throw new BusinessException(ResultCode.ERROR.getCode(), "资金流水写入失败");
        }
    }
}
