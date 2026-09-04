package com.course.platform.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.course.platform.application.service.payment.RechargeCardService;
import com.course.platform.application.service.support.OperationLogService;
import com.course.platform.common.exception.BusinessException;
import com.course.platform.common.result.ResultCode;
import com.course.platform.common.security.TokenHashUtil;
import com.course.platform.domain.dto.CardQueryRequest;
import com.course.platform.domain.dto.CardRechargeRequest;
import com.course.platform.domain.dto.GenerateCardRequest;
import com.course.platform.domain.dto.IssuedRechargeCard;
import com.course.platform.domain.entity.RechargeCard;
import com.course.platform.domain.entity.User;
import com.course.platform.infra.cache.SystemVariableCache;
import com.course.platform.infra.persistence.mapper.RechargeCardMapper;
import com.course.platform.infra.persistence.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/** 充值卡密服务：高熵一次性密钥、哈希存储和条件抢占防双花。 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RechargeCardServiceImpl implements RechargeCardService {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final int CARD_INSERT_ATTEMPTS = 5;

    private final RechargeCardMapper rechargeCardMapper;
    private final UserMapper userMapper;
    private final OperationLogService operationLogService;
    private final AccountLedgerServiceImpl accountLedgerService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public List<IssuedRechargeCard> generateCards(GenerateCardRequest request, Long operatorId) {
        List<IssuedRechargeCard> issuedCards = new ArrayList<>();
        for (int i = 0; i < request.getCount(); i++) {
            issuedCards.add(generateOneCard(request.getAmount()));
        }

        operationLogService.log(operatorId, "生成充值卡密",
                String.format("生成%d张面额%s元的充值卡密", request.getCount(), request.getAmount()),
                BigDecimal.ZERO, null);
        log.info("生成充值卡密成功：operatorId={}, count={}, amount={}",
                operatorId, request.getCount(), request.getAmount());
        return issuedCards;
    }

    private IssuedRechargeCard generateOneCard(BigDecimal amount) {
        for (int attempt = 1; attempt <= CARD_INSERT_ATTEMPTS; attempt++) {
            String cardNo = generateCardNo();
            String plainSecret = TokenHashUtil.randomHex(16); // 128 bits

            RechargeCard card = new RechargeCard();
            card.setCardNo(cardNo);
            card.setCardPassword(null);
            card.setPasswordHash(TokenHashUtil.sha256(plainSecret));
            card.setAmount(amount);
            card.setStatus(status("unused"));
            try {
                if (rechargeCardMapper.insert(card) != 1) {
                    throw new IllegalStateException("充值卡写入失败");
                }
                return IssuedRechargeCard.builder()
                        .id(card.getId())
                        .cardNo(cardNo)
                        .cardPassword(plainSecret)
                        .amount(amount)
                        .status(card.getStatus())
                        .createTime(card.getCreateTime())
                        .build();
            } catch (DuplicateKeyException collision) {
                if (attempt == CARD_INSERT_ATTEMPTS) {
                    throw collision;
                }
                log.warn("充值卡号碰撞，正在重试：attempt={}", attempt);
            }
        }
        throw new IllegalStateException("充值卡生成失败");
    }

    @Override
    public IPage<RechargeCard> queryCards(CardQueryRequest request, Integer page, Integer pageSize) {
        Page<RechargeCard> pageParam = new Page<>(page, pageSize);
        LambdaQueryWrapper<RechargeCard> wrapper = new LambdaQueryWrapper<>();
        if (request.getCardNo() != null && !request.getCardNo().trim().isEmpty()) {
            wrapper.like(RechargeCard::getCardNo, request.getCardNo().trim());
        }
        if (request.getStatus() != null) {
            wrapper.eq(RechargeCard::getStatus, request.getStatus());
        }
        if (request.getUsedBy() != null) {
            wrapper.eq(RechargeCard::getUsedBy, request.getUsedBy());
        }
        wrapper.orderByDesc(RechargeCard::getCreateTime);
        return rechargeCardMapper.selectPage(pageParam, wrapper);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public BigDecimal rechargeByCard(CardRechargeRequest request, Long userId) {
        if (userId == null || userMapper.selectById(userId) == null) {
            throw new BusinessException(ResultCode.USER_NOT_FOUND);
        }

        RechargeCard card = rechargeCardMapper.selectByCardNo(request.getCardNo());
        if (card == null || !matchesSecret(request.getCardPassword(), card)) {
            throw new BusinessException("卡号或卡密错误");
        }

        int unused = status("unused");
        int used = status("used");
        int disabled = status("disabled");
        if (card.getStatus() == null || card.getStatus() == disabled) {
            throw new BusinessException("该卡密不可用");
        }
        if (card.getStatus() == used) {
            throw new BusinessException("该卡密已被使用");
        }

        // 条件更新是卡密的唯一消费点；并发请求只有一个能从 unused 变成 used。
        int claimed = rechargeCardMapper.claimUnusedCard(
                card.getId(), userId, LocalDateTime.now(), unused, used
        );
        if (claimed != 1) {
            throw new BusinessException("该卡密已被使用或不可用");
        }

        BigDecimal rechargeAmount = card.getAmount();
        accountLedgerService.credit(
                userId,
                rechargeAmount,
                AccountLedgerServiceImpl.BIZ_RECHARGE,
                "CARD-" + card.getId(),
                "充值卡入账：" + card.getCardNo(),
                true
        );
        User latestUser = userMapper.selectById(userId);
        operationLogService.log(userId, "卡密充值",
                String.format("使用充值卡：%s，充值金额：%s元", card.getCardNo(), rechargeAmount),
                rechargeAmount, latestUser == null ? null : latestUser.getBalance());
        log.info("卡密充值成功：userId={}, cardNo={}, amount={}", userId, card.getCardNo(), rechargeAmount);
        return rechargeAmount;
    }

    private boolean matchesSecret(String providedSecret, RechargeCard card) {
        String expectedHash = card.getPasswordHash();
        // 仅用于迁移窗口兼容；migration 015 会清空所有历史明文。
        if ((expectedHash == null || expectedHash.isBlank()) && card.getCardPassword() != null) {
            expectedHash = TokenHashUtil.sha256(card.getCardPassword());
        }
        if (expectedHash == null || providedSecret == null) {
            return false;
        }
        byte[] provided = TokenHashUtil.sha256(providedSecret).getBytes(StandardCharsets.US_ASCII);
        byte[] expected = expectedHash.getBytes(StandardCharsets.US_ASCII);
        return MessageDigest.isEqual(provided, expected);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void disableCard(Long cardId, Long operatorId) {
        RechargeCard card = rechargeCardMapper.selectById(cardId);
        if (card == null) {
            throw new BusinessException(ResultCode.CARD_NOT_FOUND);
        }
        int updated = rechargeCardMapper.disableIfUnused(cardId, status("unused"), status("disabled"));
        if (updated != 1) {
            throw new BusinessException("仅未使用的卡密可以禁用");
        }
        operationLogService.log(operatorId, "禁用卡密",
                String.format("禁用卡密：%s", card.getCardNo()), BigDecimal.ZERO, null);
        log.info("禁用卡密成功：operatorId={}, cardId={}", operatorId, cardId);
    }

    @Override
    public RechargeCard getCardById(Long cardId) {
        RechargeCard card = rechargeCardMapper.selectById(cardId);
        if (card == null) {
            throw new BusinessException(ResultCode.CARD_NOT_FOUND);
        }
        return card;
    }

    private int status(String name) {
        return SystemVariableCache.getStatusValue("card_status", name);
    }

    private String generateCardNo() {
        StringBuilder cardNo = new StringBuilder(16);
        for (int i = 0; i < 16; i++) {
            cardNo.append(SECURE_RANDOM.nextInt(10));
        }
        return cardNo.toString();
    }
}
