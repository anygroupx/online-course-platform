package com.course.platform.service.impl;

import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.course.platform.infra.cache.SystemVariableCache;
import com.course.platform.common.constant.Constants;
import com.course.platform.common.exception.BusinessException;
import com.course.platform.common.result.ResultCode;
import com.course.platform.domain.dto.CardQueryRequest;
import com.course.platform.domain.dto.CardRechargeRequest;
import com.course.platform.domain.dto.GenerateCardRequest;
import com.course.platform.domain.entity.RechargeCard;
import com.course.platform.domain.entity.User;
import com.course.platform.infra.persistence.mapper.RechargeCardMapper;
import com.course.platform.infra.persistence.mapper.UserMapper;
import com.course.platform.application.service.support.OperationLogService;
import com.course.platform.application.service.payment.RechargeCardService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 充值卡密服务实现类
 * 
 * @author AI Assistant
 * @since 2025-01-17
 * Source: 基于现有系统架构设计
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RechargeCardServiceImpl implements RechargeCardService {

    private final RechargeCardMapper rechargeCardMapper;
    private final UserMapper userMapper;
    private final OperationLogService operationLogService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public List<RechargeCard> generateCards(GenerateCardRequest request, Long operatorId) {
        List<RechargeCard> cards = new ArrayList<>();
        
        for (int i = 0; i < request.getCount(); i++) {
            RechargeCard card = new RechargeCard();
            
            // 生成卡号（16位数字）
            String cardNo = generateCardNo();
            
            // 生成卡密（8位随机字符串）
            String cardPassword = RandomUtil.randomString(8);
            
            card.setCardNo(cardNo);
            card.setCardPassword(cardPassword);
            card.setAmount(request.getAmount());
            card.setStatus(SystemVariableCache.getStatusValue("card_status", "unused"));
            
            rechargeCardMapper.insert(card);
            cards.add(card);
        }
        
        // 记录操作日志
        operationLogService.log(operatorId, "生成充值卡密", 
                String.format("生成%d张面额%s元的充值卡密", request.getCount(), request.getAmount()),
                BigDecimal.ZERO, null);
        
        log.info("生成充值卡密成功：operatorId={}, count={}, amount={}", operatorId, request.getCount(), request.getAmount());
        
        return cards;
    }

    @Override
    public IPage<RechargeCard> queryCards(CardQueryRequest request, Integer page, Integer pageSize) {
        Page<RechargeCard> pageParam = new Page<>(page, pageSize);
        
        LambdaQueryWrapper<RechargeCard> wrapper = new LambdaQueryWrapper<>();
        
        if (request.getCardNo() != null && !request.getCardNo().trim().isEmpty()) {
            wrapper.like(RechargeCard::getCardNo, request.getCardNo());
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
        // 1. 查询用户信息
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ResultCode.USER_NOT_FOUND);
        }
        
        // 2. 查询卡密信息
        RechargeCard card = rechargeCardMapper.selectOne(new LambdaQueryWrapper<RechargeCard>()
                .eq(RechargeCard::getCardNo, request.getCardNo())
                .eq(RechargeCard::getCardPassword, request.getCardPassword())
        );
        
        if (card == null) {
            throw new BusinessException("卡号或卡密错误");
        }
        
        // 3. 检查卡密状态
        if (card.getStatus().equals(SystemVariableCache.getStatusValue("card_status", "used"))) {
            throw new BusinessException("该卡密已被使用");
        }
        
        if (card.getStatus().equals(SystemVariableCache.getStatusValue("card_status", "disabled"))) {
            throw new BusinessException("该卡密已被禁用");
        }
        
        // 4. 更新卡密状态
        card.setStatus(SystemVariableCache.getStatusValue("card_status", "used"));
        card.setUsedBy(userId);
        card.setUsedTime(LocalDateTime.now());
        rechargeCardMapper.updateById(card);
        
        // 5. 更新用户余额
        BigDecimal rechargeAmount = card.getAmount();
        user.setBalance(user.getBalance().add(rechargeAmount));
        user.setTotalRecharge(user.getTotalRecharge().add(rechargeAmount));
        userMapper.updateById(user);
        
        // 6. 记录操作日志
        operationLogService.log(userId, "卡密充值", 
                String.format("使用卡密充值：%s，充值金额：%s元", request.getCardNo(), rechargeAmount),
                rechargeAmount, user.getBalance());
        
        log.info("卡密充值成功：userId={}, cardNo={}, amount={}", userId, request.getCardNo(), rechargeAmount);
        
        return rechargeAmount;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void disableCard(Long cardId, Long operatorId) {
        RechargeCard card = rechargeCardMapper.selectById(cardId);
        if (card == null) {
            throw new BusinessException(ResultCode.CARD_NOT_FOUND);
        }
        
        if (card.getStatus().equals(SystemVariableCache.getStatusValue("card_status", "used"))) {
            throw new BusinessException("已使用的卡密无法禁用");
        }
        
        card.setStatus(SystemVariableCache.getStatusValue("card_status", "disabled"));
        rechargeCardMapper.updateById(card);
        
        // 记录操作日志
        operationLogService.log(operatorId, "禁用卡密", 
                String.format("禁用卡密：%s", card.getCardNo()),
                BigDecimal.ZERO, null);
        
        log.info("禁用卡密成功：operatorId={}, cardId={}", operatorId, cardId);
    }

    @Override
    public RechargeCard getCardById(Long cardId) {
        return rechargeCardMapper.selectById(cardId);
    }

    /**
     * 生成卡号
     * 
     * @return 16位数字卡号
     */
    private String generateCardNo() {
        String cardNo;
        do {
            // 生成16位数字卡号
            cardNo = String.format("%016d", RandomUtil.randomLong(1000000000000000L, 9999999999999999L));
        } while (rechargeCardMapper.selectOne(new LambdaQueryWrapper<RechargeCard>()
                .eq(RechargeCard::getCardNo, cardNo)) != null);
        
        return cardNo;
    }
}
