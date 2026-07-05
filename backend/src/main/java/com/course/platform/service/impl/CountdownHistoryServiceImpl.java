package com.course.platform.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.course.platform.domain.entity.CountdownHistory;
import com.course.platform.domain.entity.User;
import com.course.platform.domain.entity.CourseOrder;
import com.course.platform.domain.dto.CountdownHistoryDTO;
import com.course.platform.mapper.CountdownHistoryMapper;
import com.course.platform.mapper.UserMapper;
import com.course.platform.mapper.CourseOrderMapper;
import com.course.platform.service.CountdownHistoryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 倒计时历史记录服务实现类
 * 
 * @author AI Assistant
 * @since 2025-01-17
 * Source: 基于倒计时管理功能需求设计
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CountdownHistoryServiceImpl implements CountdownHistoryService {

    private final CountdownHistoryMapper countdownHistoryMapper;
    private final UserMapper userMapper;
    private final CourseOrderMapper courseOrderMapper;

    @Override
    public void recordHistory(Long orderId, String orderNo, String operationType, 
                             Integer oldDuration, Integer newDuration, String reason, 
                             Long operatorId, String operatorName) {
        try {
            CountdownHistory history = new CountdownHistory();
            history.setOrderId(orderId);
            history.setOrderNo(orderNo);
            history.setOperationType(operationType);
            history.setOldDuration(oldDuration);
            history.setNewDuration(newDuration);
            history.setReason(reason);
            history.setOperatorId(operatorId);
            history.setOperatorName(operatorName);
            
            countdownHistoryMapper.insert(history);
            
            log.info("记录倒计时历史：orderId={}, operationType={}, operatorId={}", 
                    orderId, operationType, operatorId);
        } catch (Exception e) {
            log.error("记录倒计时历史失败：orderId={}, error={}", orderId, e.getMessage(), e);
        }
    }

    @Override
    public List<CountdownHistory> getOrderHistory(Long orderId) {
        return countdownHistoryMapper.selectList(new LambdaQueryWrapper<CountdownHistory>()
                .eq(CountdownHistory::getOrderId, orderId)
                .orderByDesc(CountdownHistory::getCreateTime));
    }

    @Override
    public List<CountdownHistory> getAllHistory(Integer pageNum, Integer pageSize) {
        Page<CountdownHistory> page = new Page<>(pageNum, pageSize);
        Page<CountdownHistory> result = countdownHistoryMapper.selectPage(page, 
                new LambdaQueryWrapper<CountdownHistory>()
                        .orderByDesc(CountdownHistory::getCreateTime));
        return result.getRecords();
    }

    @Override
    public List<CountdownHistoryDTO> getAllHistoryWithDetails(Integer pageNum, Integer pageSize) {
        Page<CountdownHistory> page = new Page<>(pageNum, pageSize);
        Page<CountdownHistory> result = countdownHistoryMapper.selectPage(page, 
                new LambdaQueryWrapper<CountdownHistory>()
                        .orderByDesc(CountdownHistory::getCreateTime));
        
        List<CountdownHistory> histories = result.getRecords();
        if (histories.isEmpty()) {
            return List.of();
        }
        
        // 获取所有订单ID
        List<Long> orderIds = histories.stream()
                .map(CountdownHistory::getOrderId)
                .distinct()
                .collect(Collectors.toList());
        
        // 批量查询订单信息
        List<CourseOrder> orders = courseOrderMapper.selectList(
                new LambdaQueryWrapper<CourseOrder>()
                        .in(CourseOrder::getId, orderIds)
                        .select(CourseOrder::getId, CourseOrder::getUserId, CourseOrder::getOrderStatus));
        
        // 获取所有用户ID
        List<Long> userIds = orders.stream()
                .map(CourseOrder::getUserId)
                .distinct()
                .collect(Collectors.toList());
        
        // 批量查询用户信息
        List<User> users = userMapper.selectList(
                new LambdaQueryWrapper<User>()
                        .in(User::getId, userIds)
                        .select(User::getId, User::getUsername));
        
        // 构建映射
        Map<Long, CourseOrder> orderMap = orders.stream()
                .collect(Collectors.toMap(CourseOrder::getId, order -> order));
        Map<Long, User> userMap = users.stream()
                .collect(Collectors.toMap(User::getId, user -> user));
        
        // 转换为DTO
        return histories.stream().map(history -> {
            CountdownHistoryDTO dto = new CountdownHistoryDTO();
            dto.setId(history.getId());
            dto.setOrderId(history.getOrderId());
            dto.setOrderNo(history.getOrderNo());
            dto.setOperationType(history.getOperationType());
            dto.setOldDuration(history.getOldDuration());
            dto.setNewDuration(history.getNewDuration());
            dto.setReason(history.getReason());
            dto.setOperatorId(history.getOperatorId());
            dto.setOperatorName(history.getOperatorName());
            dto.setCreateTime(history.getCreateTime());
            
            // 设置账号信息
            CourseOrder order = orderMap.get(history.getOrderId());
            if (order != null) {
                dto.setOrderStatus(order.getOrderStatus());
                dto.setOrderStatusText(getOrderStatusText(order.getOrderStatus()));
                
                User user = userMap.get(order.getUserId());
                if (user != null) {
                    dto.setUsername(user.getUsername());
                }
            }
            
            return dto;
        }).collect(Collectors.toList());
    }
    
    /**
     * 获取订单状态文本
     */
    private String getOrderStatusText(Integer status) {
        if (status == null) return "未知";
        return switch (status) {
            case 0 -> "待考试";
            case 1 -> "进行中";
            case 2 -> "已完成";
            case 3 -> "已取消";
            case 4 -> "失败";
            default -> "未知";
        };
    }
}
