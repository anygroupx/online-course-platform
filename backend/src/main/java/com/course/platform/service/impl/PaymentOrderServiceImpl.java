package com.course.platform.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.course.platform.domain.entity.PaymentOrder;
import com.course.platform.mapper.PaymentOrderMapper;
import com.course.platform.service.PaymentOrderService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 支付订单服务实现类
 * 
 * @author AI Assistant
 * @date 2025-11-26
 */
@Slf4j
@Service
public class PaymentOrderServiceImpl implements PaymentOrderService {

    @Autowired
    private PaymentOrderMapper paymentOrderMapper;

    @Override
    public PaymentOrder getByOrderNo(String orderNo) {
        return paymentOrderMapper.selectOne(
            new LambdaQueryWrapper<PaymentOrder>()
                .eq(PaymentOrder::getOrderNo, orderNo)
        );
    }

    @Override
    public PaymentOrder getByOrderNoAndUserId(String orderNo, Long userId) {
        return paymentOrderMapper.selectOne(
            new LambdaQueryWrapper<PaymentOrder>()
                .eq(PaymentOrder::getOrderNo, orderNo)
                .eq(PaymentOrder::getUserId, userId)
        );
    }

    @Override
    public Page<PaymentOrder> getUserOrders(Long userId, String status, Integer pageNum, Integer pageSize) {
        Page<PaymentOrder> page = new Page<>(pageNum, pageSize);
        
        LambdaQueryWrapper<PaymentOrder> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(PaymentOrder::getUserId, userId);
        
        if (status != null && !status.trim().isEmpty()) {
            queryWrapper.eq(PaymentOrder::getStatus, status);
        }
        
        queryWrapper.orderByDesc(PaymentOrder::getCreateTime);

        return paymentOrderMapper.selectPage(page, queryWrapper);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void closeTimeoutOrders() {
        // 查询超时的待支付订单
        LambdaQueryWrapper<PaymentOrder> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(PaymentOrder::getStatus, "PENDING");

        List<PaymentOrder> orders = paymentOrderMapper.selectList(queryWrapper);
        
        int closedCount = 0;
        LocalDateTime now = LocalDateTime.now();

        for (PaymentOrder order : orders) {
            // 计算订单是否超时(创建时间 + 超时分钟数 < 当前时间)
            LocalDateTime expireTime = order.getCreateTime().plusMinutes(order.getTimeoutExpress());
            
            if (expireTime.isBefore(now)) {
                order.setStatus("CLOSED");
                order.setCloseTime(now);
                paymentOrderMapper.updateById(order);
                closedCount++;
            }
        }

        if (closedCount > 0) {
            log.info("定时关闭超时订单完成，共关闭{}个订单", closedCount);
        }
    }
}
