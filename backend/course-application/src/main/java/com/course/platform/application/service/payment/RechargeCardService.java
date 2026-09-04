package com.course.platform.application.service.payment;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.course.platform.domain.dto.CardQueryRequest;
import com.course.platform.domain.dto.CardRechargeRequest;
import com.course.platform.domain.dto.GenerateCardRequest;
import com.course.platform.domain.dto.IssuedRechargeCard;
import com.course.platform.domain.entity.RechargeCard;

import java.util.List;

/**
 * 充值卡密服务接口
 * 
 * @author AI Assistant
 * @since 2025-01-17
 * Source: 基于现有系统架构设计
 */
public interface RechargeCardService {

    /**
     * 生成充值卡密
     * 
     * @param request 生成请求
     * @param operatorId 操作人ID
     * @return 生成的卡密列表
     */
    List<IssuedRechargeCard> generateCards(GenerateCardRequest request, Long operatorId);

    /**
     * 分页查询充值卡密
     * 
     * @param request 查询请求
     * @param page 当前页
     * @param pageSize 每页数量
     * @return 卡密分页数据
     */
    IPage<RechargeCard> queryCards(CardQueryRequest request, Integer page, Integer pageSize);

    /**
     * 用户使用卡密充值
     * 
     * @param request 充值请求
     * @param userId 用户ID
     * @return 充值金额
     */
    java.math.BigDecimal rechargeByCard(CardRechargeRequest request, Long userId);

    /**
     * 禁用卡密
     * 
     * @param cardId 卡密ID
     * @param operatorId 操作人ID
     */
    void disableCard(Long cardId, Long operatorId);

    /**
     * 获取卡密详情
     * 
     * @param cardId 卡密ID
     * @return 卡密信息
     */
    RechargeCard getCardById(Long cardId);
}
