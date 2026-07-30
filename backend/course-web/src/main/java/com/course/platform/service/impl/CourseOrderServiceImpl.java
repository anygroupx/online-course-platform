package com.course.platform.service.impl;

import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.course.platform.infra.cache.SystemVariableCache;
import com.course.platform.common.constant.Constants;
import com.course.platform.security.SecurityUtils;
import com.course.platform.common.exception.BusinessException;
import com.course.platform.common.result.ResultCode;
import com.course.platform.domain.dto.OrderCreateRequest;
import com.course.platform.domain.dto.OrderQueryRequest;
import com.course.platform.domain.entity.CoursePlatform;
import com.course.platform.domain.entity.CourseOrder;
import com.course.platform.domain.entity.User;
import com.course.platform.infra.persistence.mapper.CoursePlatformMapper;
import com.course.platform.infra.persistence.mapper.CourseOrderMapper;
import com.course.platform.infra.persistence.mapper.UserMapper;
import com.course.platform.application.service.order.CourseOrderService;
import com.course.platform.application.service.support.OperationLogService;
import com.course.platform.application.service.platform.PlatformDockingService;
import com.course.platform.domain.dto.DockResult;
import com.course.platform.domain.dto.OrderProgressResult;
import com.course.platform.domain.entity.ApiProvider;
import com.course.platform.infra.persistence.mapper.ApiProviderMapper;
import com.course.platform.service.impl.AccountLedgerServiceImpl;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

/**
 * 课程订单服务实现类
 *
 * @author AI Assistant
 * @since 2025-01-17
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CourseOrderServiceImpl implements CourseOrderService {

    private final CourseOrderMapper courseOrderMapper;
    private final CoursePlatformMapper coursePlatformMapper;
    private final UserMapper userMapper;
    private final OperationLogService operationLogService;
    private final PlatformDockingService platformDockingService;
    private final ApiProviderMapper apiProviderMapper;
    private final org.springframework.context.ApplicationEventPublisher eventPublisher;
    private final AccountLedgerServiceImpl accountLedgerService;

    /**
     * 获取订单状态值
     */
    private static int getOrderStatus(String key) {
        return SystemVariableCache.getStatusValue("order_status", key);
    }

    /**
     * 获取对接状态值
     */
    private static int getDockStatus(String key) {
        return SystemVariableCache.getStatusValue("dock_status", key);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createOrder(OrderCreateRequest request, Long userId) {
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

        // 3. 计算订单金额
        BigDecimal amount = calculateOrderAmount(platform, user);

        // 4. 检查余额
        if (user.getBalance().compareTo(amount) < 0) {
            throw new BusinessException(ResultCode.BALANCE_INSUFFICIENT);
        }

        // 5. 检查是否重复下单
        CourseOrder existingOrder = courseOrderMapper.selectOne(new LambdaQueryWrapper<CourseOrder>()
                .eq(CourseOrder::getUserId, userId)
                .eq(CourseOrder::getPlatformId, request.getPlatformId())
                .eq(CourseOrder::getStudentAccount, request.getStudentAccount())
                .eq(CourseOrder::getCourseName, request.getCourseName())
                .in(CourseOrder::getOrderStatus, getOrderStatus("pending"), getOrderStatus("processing"))
        );

        if (existingOrder != null) {
            throw new BusinessException(ResultCode.ORDER_EXISTS);
        }

        // 6. 创建订单
        CourseOrder order = new CourseOrder();
        order.setOrderNo(generateOrderNo());
        order.setUserId(userId);
        order.setPlatformId(request.getPlatformId());
        order.setPlatformName(platform.getName());
        order.setSchoolName(request.getSchoolName());
        order.setStudentName(request.getStudentName());
        order.setStudentAccount(request.getStudentAccount());
        order.setStudentPassword(request.getStudentPassword());
        order.setCourseId(request.getCourseId());
        order.setCourseName(request.getCourseName());
        order.setAmount(amount);
        order.setIsFastMode(request.getIsFastMode() != null && request.getIsFastMode() ? 1 : 0);
        order.setRetryCount(0);
        order.setOrderStatus(getOrderStatus("pending"));
        order.setDockStatus(getDockStatus("pending"));

        // 设置自营订单标识 - 根据平台配置自动设置
        order.setIsSelfOperated(platform.getIsSelfOperated());

        // 设置API提供商ID - 用于批量同步时精确匹配订单
        if (platform.getDockApiId() != null) {
            order.setApiProviderId(platform.getDockApiId());
        }

        int insertResult = courseOrderMapper.insert(order);
        log.info("订单插入结果: {}, 插入后订单ID: {}", insertResult, order.getId());

        // 如果ID为空，重新查询获取
        if (order.getId() == null) {
            CourseOrder savedOrder = courseOrderMapper.selectOne(new LambdaQueryWrapper<CourseOrder>()
                    .eq(CourseOrder::getOrderNo, order.getOrderNo())
                    .orderByDesc(CourseOrder::getCreateTime)
                    .last("LIMIT 1")
            );
            if (savedOrder != null) {
                order.setId(savedOrder.getId());
                log.info("重新获取订单ID: {}", order.getId());
            }
        }

        // 7. 扣除余额（原子账本）
        accountLedgerService.debit(
                userId,
                amount,
                AccountLedgerServiceImpl.BIZ_ORDER,
                order.getOrderNo(),
                String.format("创建订单：%s - %s", platform.getName(), request.getCourseName())
        );
        user = userMapper.selectById(userId);

        // 8. 记录日志
        operationLogService.log(userId, "创建订单",
                String.format("创建订单：%s - %s，扣费：%s元", platform.getName(), request.getCourseName(), amount),
                amount.negate(), user.getBalance());

        log.info("订单创建成功：orderId={}, userId={}, amount={}", order.getId(), userId, amount);

        // 9. 发布订单创建事件（事务提交后触发对接）
        eventPublisher.publishEvent(new com.course.platform.domain.event.OrderCreatedEvent(
                this, order.getId(), platform.getId(), platform.getIsSelfOperated()));

        return order.getId();
    }

    @Override
    public IPage<CourseOrder> queryOrders(OrderQueryRequest request, Long userId) {
        Page<CourseOrder> page = new Page<>(request.getPage(), request.getPageSize());

        LambdaQueryWrapper<CourseOrder> queryWrapper = new LambdaQueryWrapper<>();

        // 非管理员只能查看自己的订单
        if (!(SecurityUtils.isAdmin() || Constants.DEFAULT_ADMIN_ID.equals(userId))) {
            queryWrapper.eq(CourseOrder::getUserId, userId);
        }

        // 添加查询条件
        if (StrUtil.isNotBlank(request.getOrderNo())) {
            queryWrapper.eq(CourseOrder::getOrderNo, request.getOrderNo());
        }
        if (request.getPlatformId() != null) {
            queryWrapper.eq(CourseOrder::getPlatformId, request.getPlatformId());
        }
        if (StrUtil.isNotBlank(request.getStudentAccount())) {
            // 支持学生账号模糊搜索
            queryWrapper.like(CourseOrder::getStudentAccount, request.getStudentAccount());
        }
        if (request.getOrderStatus() != null) {
            queryWrapper.eq(CourseOrder::getOrderStatus, request.getOrderStatus());
        }
        if (request.getDockStatus() != null) {
            queryWrapper.eq(CourseOrder::getDockStatus, request.getDockStatus());
        }
        if (request.getUserId() != null) {
            queryWrapper.eq(CourseOrder::getUserId, request.getUserId());
        }

        queryWrapper.orderByDesc(CourseOrder::getCreateTime);

        IPage<CourseOrder> result = courseOrderMapper.selectPage(page, queryWrapper);

        // 计算基于倒计时的进度
        if (result.getRecords() != null && !result.getRecords().isEmpty()) {
            for (CourseOrder order : result.getRecords()) {
                calculateProgressBasedOnCountdown(order);
            }
        }

        return result;
    }

    @Override
    public CourseOrder getOrderById(Long orderId, Long userId) {
        CourseOrder order = courseOrderMapper.selectById(orderId);

        if (order == null) {
            throw new BusinessException(ResultCode.ORDER_NOT_FOUND);
        }

        // 非管理员只能查看自己的订单
        if (!(SecurityUtils.isAdmin() || Constants.DEFAULT_ADMIN_ID.equals(userId)) && !order.getUserId().equals(userId)) {
            throw new BusinessException(ResultCode.FORBIDDEN);
        }

        return order;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void cancelOrder(Long orderId, Long userId) {
        CourseOrder order = getOrderById(orderId, userId);

        // 只能取消待处理的订单 - 修改使用==而不是equals
        if (order.getOrderStatus() != getOrderStatus("pending") && order.getOrderStatus() != getOrderStatus("failed")) {
            throw new BusinessException("只能取消待处理或失败的订单");
        }

        // 更新订单状态
        order.setOrderStatus(getOrderStatus("cancelled"));
        order.setDockStatus(getDockStatus("cancelled"));
        courseOrderMapper.updateById(order);

        // 退回余额（原子账本）
        accountLedgerService.credit(
                userId,
                order.getAmount(),
                AccountLedgerServiceImpl.BIZ_REFUND,
                order.getOrderNo(),
                String.format("取消订单退款：%s", order.getOrderNo()),
                false
        );
        User user = userMapper.selectById(userId);

        // 记录日志
        operationLogService.log(userId, "取消订单",
                String.format("取消订单：%s，退回：%s元", order.getOrderNo(), order.getAmount()),
                order.getAmount(), user.getBalance());

        log.info("订单取消成功：orderId={}, userId={}", orderId, userId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void retryOrder(Long orderId, Long userId) {
        CourseOrder order = getOrderById(orderId, userId);

        // 检查补单次数
        if (order.getRetryCount() >= 5) {
            throw new BusinessException("补单次数已达上限");
        }

        // 查询平台和API配置
        CoursePlatform platform = coursePlatformMapper.selectById(order.getPlatformId());
        if (platform.getDockApiId() == null) {
            throw new BusinessException("未配置对接接口");
        }

        ApiProvider apiProvider = apiProviderMapper.selectById(platform.getDockApiId());
        if (apiProvider == null || apiProvider.getStatus() != 1) {
            throw new BusinessException("API配置不存在或已禁用");
        }

        try {
            // 调用第三方补单接口
            DockResult dockResult = platformDockingService.retryOrder(order, platform, apiProvider);

            // 更新订单状态和补单次数
            order.setRetryCount(order.getRetryCount() + 1);

            if (dockResult.isSuccess()) {
                order.setOrderStatus(getOrderStatus("processing"));
                order.setDockStatus(getDockStatus("success"));
                if (dockResult.getThirdOrderId() != null) {
                    order.setThirdOrderId(dockResult.getThirdOrderId());
                }
                order.setRemarks("补单成功");
            } else {
                order.setDockStatus(getDockStatus("failed"));
                order.setRemarks("补单失败：" + dockResult.getMessage());
            }

            courseOrderMapper.updateById(order);

            // 记录日志
            operationLogService.log(userId, "补单",
                    String.format("补单：%s，第%d次", order.getOrderNo(), order.getRetryCount()),
                    BigDecimal.ZERO, null);

        } catch (Exception e) {
            log.error("补单失败：orderId={}, error={}", orderId, e.getMessage(), e);
            throw new BusinessException("补单失败：" + e.getMessage());
        }
    }

    @Override
    public void updateOrderProgress(Long orderId, Long userId) {
        CourseOrder order = getOrderById(orderId, userId);

        // 如果是自营平台，使用模拟进度
        CoursePlatform platform = coursePlatformMapper.selectById(order.getPlatformId());
        if (platform.getIsSelfOperated() == 1) {
            calculateProgressBasedOnCountdown(order);
            return;
        }

        // 第三方平台，调用API查询进度
        if (platform.getDockApiId() != null) {
            ApiProvider apiProvider = apiProviderMapper.selectById(platform.getDockApiId());
            if (apiProvider != null && apiProvider.getStatus() == 1) {
                try {
                    OrderProgressResult result = platformDockingService.queryOrderProgress(order, platform, apiProvider);

                    // 更新订单状态和进度
                    order.setProgress(result.getProgress());
                    order.setOrderStatus(result.getOrderStatus());
                    if (StrUtil.isNotBlank(result.getRemarks())) {
                        order.setRemarks(result.getRemarks());
                    }

                    if (result.getCourseStartTime() != null) order.setCourseStartTime(result.getCourseStartTime());
                    if (result.getCourseEndTime() != null) order.setCourseEndTime(result.getCourseEndTime());
                    if (result.getExamStartTime() != null) order.setExamStartTime(result.getExamStartTime());
                    if (result.getExamEndTime() != null) order.setExamEndTime(result.getExamEndTime());

                    courseOrderMapper.updateById(order);
                    log.info("订单进度更新成功：orderId={}, progress={}", orderId, result.getProgress());
                } catch (Exception e) {
                    log.error("更新订单进度失败：orderId={}, error={}", orderId, e.getMessage(), e);
                    // 不抛出异常，避免中断批量操作，仅记录日志
                }
            }
        }
    }

    /**
     * 计算订单金额
     */
    private BigDecimal calculateOrderAmount(CoursePlatform platform, User user) {
        BigDecimal basePrice = platform.getBasePrice();
        BigDecimal userRate = user.getRate();

        BigDecimal amount;
        // 使用equals方法比较字符串
        if (Constants.RATE_TYPE_MULTIPLY.equals(platform.getRateType())) {
            // 乘法计算
            amount = basePrice.multiply(userRate);
        } else {
            // 加法计算
            amount = basePrice.add(userRate);
        }

        return amount.setScale(2, BigDecimal.ROUND_HALF_UP);
    }

    /**
     * 生成订单编号
     */
    private String generateOrderNo() {
        return "ORD" + IdUtil.getSnowflakeNextIdStr();
    }

    /**
     * 计算基于倒计时的进度
     * 当订单处于进行中状态且有倒计时时，根据倒计时剩余时间计算进度百分比
     */
    private void calculateProgressBasedOnCountdown(CourseOrder order) {
        // 只有进行中状态的自营订单才需要计算基于倒计时的进度
        if (order.getOrderStatus() != null && order.getOrderStatus() == getOrderStatus("processing") &&
            order.getIsSelfOperated() != null && order.getIsSelfOperated() == 1 &&
            order.getCountdownStartTime() != null && order.getCountdownEndTime() != null &&
            order.getCountdownDuration() != null && order.getCountdownDuration() > 0) {

            java.time.LocalDateTime now = java.time.LocalDateTime.now();
            java.time.LocalDateTime startTime = order.getCountdownStartTime();
            java.time.LocalDateTime endTime = order.getCountdownEndTime();

            // 如果倒计时已过期，进度设为100%
            if (endTime.isBefore(now)) {
                order.setProgress("100%");
                return;
            }

            // 计算总倒计时时长（分钟）
            long totalMinutes = order.getCountdownDuration();

            // 计算已过去的时间（分钟）
            long elapsedMinutes = java.time.Duration.between(startTime, now).toMinutes();

            // 确保已过去时间不为负数
            elapsedMinutes = Math.max(0, elapsedMinutes);

            // 计算进度百分比
            int progressPercent = (int) Math.min(100, Math.max(0, (elapsedMinutes * 100) / totalMinutes));

            // 设置进度
            order.setProgress(progressPercent + "%");

            log.debug("计算倒计时进度：orderId={}, 总时长={}分钟, 已过去={}分钟, 进度={}%",
                    order.getId(), totalMinutes, elapsedMinutes, progressPercent);
        }
    }

    @Override
    public CourseOrder getOrderByOrderNo(String orderNo, Long userId) {
        if (StrUtil.isBlank(orderNo)) {
            throw new BusinessException("订单编号不能为空");
        }

        CourseOrder order = courseOrderMapper.selectOne(new LambdaQueryWrapper<CourseOrder>()
                .eq(CourseOrder::getOrderNo, orderNo));

        if (order == null) {
            throw new BusinessException(ResultCode.ORDER_NOT_FOUND);
        }

        // 非管理员只能查看自己的订单
        if (!(SecurityUtils.isAdmin() || Constants.DEFAULT_ADMIN_ID.equals(userId)) && !order.getUserId().equals(userId)) {
            throw new BusinessException(ResultCode.FORBIDDEN);
        }

        return order;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void cancelOrderByOrderNo(String orderNo, Long userId) {
        CourseOrder order = getOrderByOrderNo(orderNo, userId);
        cancelOrder(order.getId(), userId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void retryOrderByOrderNo(String orderNo, Long userId) {
        CourseOrder order = getOrderByOrderNo(orderNo, userId);
        retryOrder(order.getId(), userId);
    }

    @Override
    public void updateOrderProgressByOrderNo(String orderNo, Long userId) {
        CourseOrder order = getOrderByOrderNo(orderNo, userId);
        updateOrderProgress(order.getId(), userId);
    }
}
