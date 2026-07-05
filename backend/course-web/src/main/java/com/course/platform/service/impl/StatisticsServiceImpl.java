package com.course.platform.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.course.platform.infra.cache.SystemVariableCache;
import com.course.platform.common.constant.Constants;
import com.course.platform.domain.entity.CourseOrder;
import com.course.platform.domain.entity.User;
import com.course.platform.domain.vo.StatisticsResponse;
import com.course.platform.infra.persistence.mapper.CourseOrderMapper;
import com.course.platform.infra.persistence.mapper.UserMapper;
import com.course.platform.application.service.system.StatisticsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

/**
 * 统计服务实现类
 * 
 * @author AI Assistant
 * @since 2025-01-17
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StatisticsServiceImpl implements StatisticsService {

    private final UserMapper userMapper;
    private final CourseOrderMapper courseOrderMapper;

    @Override
    public StatisticsResponse getStatistics(Long userId) {
        LocalDateTime todayStart = LocalDateTime.of(LocalDate.now(), LocalTime.MIN);
        LocalDateTime todayEnd = LocalDateTime.of(LocalDate.now(), LocalTime.MAX);

        StatisticsResponse.StatisticsResponseBuilder builder = StatisticsResponse.builder();

        // 订单统计
        LambdaQueryWrapper<CourseOrder> orderWrapper = new LambdaQueryWrapper<>();
        if (!Constants.DEFAULT_ADMIN_ID.equals(userId)) {
            orderWrapper.eq(CourseOrder::getUserId, userId);
        }

        Long totalOrders = courseOrderMapper.selectCount(orderWrapper);
        builder.totalOrders(totalOrders);

        // 今日订单
        LambdaQueryWrapper<CourseOrder> todayOrderWrapper = new LambdaQueryWrapper<>();
        if (!Constants.DEFAULT_ADMIN_ID.equals(userId)) {
            todayOrderWrapper.eq(CourseOrder::getUserId, userId);
        }
        todayOrderWrapper.between(CourseOrder::getCreateTime, todayStart, todayEnd);
        Long todayOrders = courseOrderMapper.selectCount(todayOrderWrapper);
        builder.todayOrders(todayOrders);

        // 用户统计（仅管理员）
        if (Constants.DEFAULT_ADMIN_ID.equals(userId)) {
            Long totalUsers = userMapper.selectCount(null);
            builder.totalUsers(totalUsers);

            LambdaQueryWrapper<User> todayUserWrapper = new LambdaQueryWrapper<>();
            todayUserWrapper.between(User::getCreateTime, todayStart, todayEnd);
            Long todayNewUsers = userMapper.selectCount(todayUserWrapper);
            builder.todayNewUsers(todayNewUsers);
        } else {
            // 代理统计
            LambdaQueryWrapper<User> agentWrapper = new LambdaQueryWrapper<>();
            agentWrapper.eq(User::getParentId, userId);
            Long totalAgents = userMapper.selectCount(agentWrapper);
            builder.totalAgents(totalAgents);

            LambdaQueryWrapper<User> todayLoginWrapper = new LambdaQueryWrapper<>();
            todayLoginWrapper.eq(User::getParentId, userId);
            todayLoginWrapper.between(User::getLastLoginTime, todayStart, todayEnd);
            Long todayLoginAgents = userMapper.selectCount(todayLoginWrapper);
            builder.todayLoginAgents(todayLoginAgents);
        }

        // 交易额统计 - 计算实际金额
        BigDecimal totalAmount = calculateTotalAmount(orderWrapper);
        builder.totalAmount(totalAmount);
        
        LambdaQueryWrapper<CourseOrder> todayAmountWrapper = new LambdaQueryWrapper<>();
        if (!Constants.DEFAULT_ADMIN_ID.equals(userId)) {
            todayAmountWrapper.eq(CourseOrder::getUserId, userId);
        }
        todayAmountWrapper.between(CourseOrder::getCreateTime, todayStart, todayEnd);
        BigDecimal todayAmount = calculateTotalAmount(todayAmountWrapper);
        builder.todayAmount(todayAmount);

        // 订单状态统计
        LambdaQueryWrapper<CourseOrder> pendingWrapper = new LambdaQueryWrapper<>();
        if (!Constants.DEFAULT_ADMIN_ID.equals(userId)) {
            pendingWrapper.eq(CourseOrder::getUserId, userId);
        }
        pendingWrapper.eq(CourseOrder::getOrderStatus, SystemVariableCache.getStatusValue("order_status", "pending"));
        Long pendingOrders = courseOrderMapper.selectCount(pendingWrapper);
        builder.pendingOrders(pendingOrders);

        LambdaQueryWrapper<CourseOrder> processingWrapper = new LambdaQueryWrapper<>();
        if (!Constants.DEFAULT_ADMIN_ID.equals(userId)) {
            processingWrapper.eq(CourseOrder::getUserId, userId);
        }
        processingWrapper.eq(CourseOrder::getOrderStatus, SystemVariableCache.getStatusValue("order_status", "processing"));
        Long processingOrders = courseOrderMapper.selectCount(processingWrapper);
        builder.processingOrders(processingOrders);

        return builder.build();
    }

    /**
     * 计算总金额
     */
    private BigDecimal calculateTotalAmount(LambdaQueryWrapper<CourseOrder> queryWrapper) {
        List<CourseOrder> orders = courseOrderMapper.selectList(queryWrapper);
        return orders.stream()
                .map(CourseOrder::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
