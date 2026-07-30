package com.course.platform.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.course.platform.common.exception.BusinessException;
import com.course.platform.common.result.ResultCode;
import com.course.platform.domain.entity.AccountLedger;
import com.course.platform.domain.entity.User;
import com.course.platform.infra.persistence.mapper.AccountLedgerMapper;
import com.course.platform.infra.persistence.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

/**
 * 资金账本服务：余额原子更新 + 不可变流水
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

    private final UserMapper userMapper;
    private final AccountLedgerMapper accountLedgerMapper;

    @Transactional(rollbackFor = Exception.class)
    public void credit(Long userId, BigDecimal amount, String bizType, String bizNo, String remark, boolean countRecharge) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException(ResultCode.PARAM_ERROR.getCode(), "金额必须大于0");
        }
        if (exists(userId, bizType, bizNo, 1)) {
            log.info("账本入账已存在，跳过：userId={}, bizType={}, bizNo={}", userId, bizType, bizNo);
            return;
        }
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ResultCode.USER_NOT_FOUND);
        }
        BigDecimal before = user.getBalance() == null ? BigDecimal.ZERO : user.getBalance();
        int updated = userMapper.increaseBalance(userId, amount, countRecharge ? 1 : 0);
        if (updated != 1) {
            throw new BusinessException(ResultCode.ERROR.getCode(), "余额更新失败");
        }
        insertLedger(userId, bizType, bizNo, 1, amount, before, before.add(amount), remark);
    }

    @Transactional(rollbackFor = Exception.class)
    public void debit(Long userId, BigDecimal amount, String bizType, String bizNo, String remark) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException(ResultCode.PARAM_ERROR.getCode(), "金额必须大于0");
        }
        if (exists(userId, bizType, bizNo, -1)) {
            log.info("账本出账已存在，跳过：userId={}, bizType={}, bizNo={}", userId, bizType, bizNo);
            return;
        }
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ResultCode.USER_NOT_FOUND);
        }
        BigDecimal before = user.getBalance() == null ? BigDecimal.ZERO : user.getBalance();
        int updated = userMapper.decreaseBalance(userId, amount);
        if (updated != 1) {
            throw new BusinessException(ResultCode.BALANCE_INSUFFICIENT);
        }
        insertLedger(userId, bizType, bizNo, -1, amount, before, before.subtract(amount), remark);
    }

    private boolean exists(Long userId, String bizType, String bizNo, int direction) {
        Long count = accountLedgerMapper.selectCount(new LambdaQueryWrapper<AccountLedger>()
                .eq(AccountLedger::getUserId, userId)
                .eq(AccountLedger::getBizType, bizType)
                .eq(AccountLedger::getBizNo, bizNo)
                .eq(AccountLedger::getDirection, direction));
        return count != null && count > 0;
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
        ledger.setRemark(remark);
        try {
            accountLedgerMapper.insert(ledger);
        } catch (DuplicateKeyException e) {
            log.warn("账本唯一键冲突，视为已处理：{} {} {}", bizType, bizNo, userId);
        }
    }
}
