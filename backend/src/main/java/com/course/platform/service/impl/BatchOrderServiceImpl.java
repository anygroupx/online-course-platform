package com.course.platform.service.impl;

import com.course.platform.shared.exception.BusinessException;
import com.course.platform.shared.result.ResultCode;
import com.course.platform.domain.dto.BatchOrderRequest;
import com.course.platform.domain.dto.OrderCreateRequest;
import com.course.platform.domain.entity.CoursePlatform;
import com.course.platform.domain.entity.User;
import com.course.platform.mapper.CoursePlatformMapper;
import com.course.platform.mapper.UserMapper;
import com.course.platform.service.BatchOrderService;
import com.course.platform.service.CourseOrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * 批量订单服务实现类
 * 
 * @author AI Assistant
 * @since 2025-01-17
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BatchOrderServiceImpl implements BatchOrderService {

    private final UserMapper userMapper;
    private final CoursePlatformMapper coursePlatformMapper;
    private final CourseOrderService courseOrderService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public List<Long> batchCreateOrders(BatchOrderRequest request, Long userId) {
        // 1. 查询用户信息
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ResultCode.USER_NOT_FOUND);
        }

        // 2. 查询课程平台信息
        CoursePlatform platform = coursePlatformMapper.selectById(request.getPlatformId());
        if (platform == null) {
            throw new BusinessException(ResultCode.COURSE_NOT_FOUND);
        }

        // 3. 计算总金额
        BigDecimal totalAmount = calculateTotalAmount(platform, user, request.getOrders().size());

        // 4. 检查余额
        if (user.getBalance().compareTo(totalAmount) < 0) {
            throw new BusinessException(ResultCode.BALANCE_INSUFFICIENT.getCode(), 
                    String.format("余额不足，需要%s元，当前余额%s元", totalAmount, user.getBalance()));
        }

        // 5. 批量创建订单
        List<Long> orderIds = new ArrayList<>();
        for (BatchOrderRequest.BatchOrderItem item : request.getOrders()) {
            try {
                OrderCreateRequest orderRequest = new OrderCreateRequest();
                orderRequest.setPlatformId(request.getPlatformId());
                orderRequest.setSchoolName(item.getSchoolName());
                orderRequest.setStudentName(item.getStudentName());
                orderRequest.setStudentAccount(item.getStudentAccount());
                orderRequest.setStudentPassword(item.getStudentPassword());
                orderRequest.setCourseId(item.getCourseId());
                orderRequest.setCourseName(item.getCourseName());

                Long orderId = courseOrderService.createOrder(orderRequest, userId);
                orderIds.add(orderId);
            } catch (Exception e) {
                log.error("批量下单失败：account={}, course={}, error={}", 
                        item.getStudentAccount(), item.getCourseName(), e.getMessage());
                // 继续处理下一个订单
            }
        }

        log.info("批量订单创建完成：userId={}, total={}, success={}", userId, request.getOrders().size(), orderIds.size());

        return orderIds;
    }

    /**
     * 计算总金额
     */
    private BigDecimal calculateTotalAmount(CoursePlatform platform, User user, int count) {
        BigDecimal basePrice = platform.getBasePrice();
        BigDecimal userRate = user.getRate();

        BigDecimal singleAmount;
        if ("MULTIPLY".equals(platform.getRateType())) {
            singleAmount = basePrice.multiply(userRate);
        } else {
            singleAmount = basePrice.add(userRate);
        }

        return singleAmount.multiply(BigDecimal.valueOf(count)).setScale(2, BigDecimal.ROUND_HALF_UP);
    }
}

