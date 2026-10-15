package com.course.platform.service.impl;

import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.course.platform.infra.cache.SystemVariableCache;
import com.course.platform.common.constant.Constants;
import com.course.platform.security.ResourceAuthorizationService;
import com.course.platform.common.exception.BusinessException;
import com.course.platform.common.result.ResultCode;
import com.course.platform.common.util.PublicUidUtil;
import com.course.platform.domain.dto.OrderCreateRequest;
import com.course.platform.domain.dto.OrderQueryRequest;
import com.course.platform.domain.entity.CoursePlatform;
import com.course.platform.domain.entity.CourseOrder;
import com.course.platform.domain.entity.CourseOrderProgressLog;
import com.course.platform.domain.entity.User;
import com.course.platform.domain.vo.CourseOrderProgressLogVO;
import com.course.platform.infra.persistence.mapper.CoursePlatformMapper;
import com.course.platform.infra.persistence.mapper.CourseOrderMapper;
import com.course.platform.infra.persistence.mapper.UserMapper;
import com.course.platform.application.service.order.CourseOrderService;
import com.course.platform.application.service.order.CourseOrderProgressLogService;
import com.course.platform.application.service.support.OperationLogService;
import com.course.platform.application.service.platform.PlatformDockingService;
import com.course.platform.domain.dto.DockResult;
import com.course.platform.domain.dto.OrderProgressResult;
import com.course.platform.domain.entity.ApiProvider;
import com.course.platform.domain.exception.ProviderRequestException;
import com.course.platform.infra.persistence.mapper.ApiProviderMapper;
import com.course.platform.service.impl.AccountLedgerServiceImpl;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

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
    @org.springframework.beans.factory.annotation.Autowired
    private CoursePrivateFulfillmentService fulfillment;

    private static final int MAX_COURSE_ID_LENGTH = 2048;

    private final CourseOrderMapper courseOrderMapper;
    private final CoursePlatformMapper coursePlatformMapper;
    private final UserMapper userMapper;
    private final OperationLogService operationLogService;
    private final PlatformDockingService platformDockingService;
    private final ApiProviderMapper apiProviderMapper;
    private final org.springframework.context.ApplicationEventPublisher eventPublisher;
    private final AccountLedgerServiceImpl accountLedgerService;
    private final ResourceAuthorizationService authorizationService;
    private final CourseOrderProgressLogService progressLogService;
    private final CoursePricingService pricingService;
    private final CourseReservedWalletService reservedWalletService;
    private final CourseOrderPaymentService paymentService;

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
    @Transactional(rollbackFor = Exception.class, isolation=org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public Long createOrder(OrderCreateRequest request, Long userId) {
        if (request != null && request.getCourseId() != null
                && request.getCourseId().length() > MAX_COURSE_ID_LENGTH) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "课程ID长度不能超过2048个字符");
        }

        if (request == null || request.getPlatformId() == null || StrUtil.isBlank(request.getStudentAccount())
                || StrUtil.isBlank(request.getStudentPassword()) || StrUtil.isBlank(request.getCourseName()))
            throw new BusinessException(ResultCode.PARAM_ERROR, "订单商品、学生账号、密码和课程不能为空");
        // 账户锁串行化同一用户的重复检查与扣款，所有入口共用本事务。
        User user = pricingService.requireUser(userMapper.selectByIdForUpdate(userId));
        String fingerprint = CourseOrderRequestIdentity.fingerprint(request);
        if (request.getClientRequestId() != null) {
            if (!request.getClientRequestId().matches("[A-Za-z0-9_-]{1,64}"))
                throw new BusinessException(ResultCode.PARAM_ERROR, "订单请求标识无效");
            CourseOrder retry = courseOrderMapper.selectByRequestKey(userId, request.getClientRequestId());
            if (retry != null) {
                if (!fingerprint.equals(retry.getRequestFingerprint()) || Integer.valueOf(1).equals(retry.getIsDeleted()))
                    throw new BusinessException(ResultCode.CONFLICT, "订单请求键已使用或请求内容已变化");
                return retry.getId();
            }
        }
        CoursePlatform platform = pricingService.requirePlatform(request.getPlatformId());
        CoursePricingService.PriceDecision decision = request.getCheckoutIntent()==null&&request.getExpectedPolicyTermId()==null&&request.getExpectedPolicyVersionId()==null&&request.getExpectedPolicyExpiresAt()==null?pricingService.decide(user, platform, true):pricingService.decide(user, platform, true, request);
        if (request.getExpectedAmount() != null && request.getExpectedAmount().compareTo(decision.finalPrice()) != 0
                || !"GENERAL_EXPLICIT".equals(request.getCheckoutIntent()) && !decision.acceptsContractVersion(request.getExpectedContractVersion()) && !decision.matchesPrivatePolicy(request)
                || request.getExpectedPriceSource() != null && !request.getExpectedPriceSource().equals(decision.priceSource())
                || request.getExpectedPaymentSource() != null && !request.getExpectedPaymentSource().equals(decision.paymentSource()))
            throw new BusinessException(ResultCode.CONFLICT, "课程价格或协议版本已变化，请重新确认");
        decision.requirePurchasable();
        BigDecimal amount = decision.finalPrice();

        // 5. 检查是否重复下单
        CourseOrder existingOrder = courseOrderMapper.selectOne(new LambdaQueryWrapper<CourseOrder>()
                .eq(CourseOrder::getUserId, userId)
                .eq(CourseOrder::getPlatformId, request.getPlatformId())
                .eq(CourseOrder::getStudentAccount, request.getStudentAccount())
                .eq(StrUtil.isNotBlank(request.getCourseId()), CourseOrder::getCourseId, request.getCourseId())
                .eq(StrUtil.isBlank(request.getCourseId()), CourseOrder::getCourseName, request.getCourseName())
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
        order.setPriceSource(decision.priceSource());
        order.setPaymentSource(decision.paymentSource());
        order.setPrivateContractId(decision.contractId());
        order.setContractVersion(decision.contractVersion());
        order.setWalletId(decision.walletId());
        // 当前周期来自账户锁下的统一报价，客户端无权自行指定成交周期或规则版本。
        if(decision.term()!=null){order.setPrivateTermId(decision.term().getId());order.setPrivateTermVersion(decision.term().getVersion());order.setPrivatePlanVersionId(decision.term().getVersionId());order.setPrivateOperationsMode(decision.term().getOperationsMode());}
        order.setClientRequestId(request.getClientRequestId());
        order.setRequestFingerprint(request.getClientRequestId() == null ? null : fingerprint);
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
        if (insertResult != 1) throw new BusinessException(ResultCode.ERROR, "订单写入失败");
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

        if (order.getId() == null) throw new BusinessException(ResultCode.ERROR,"订单ID未生成");

        // 成交快照决定资金来源，专户不足不会普通余额补差。
        if ("COURSE_WALLET".equals(decision.paymentSource())) {
            reservedWalletService.charge(order);
        } else {
            accountLedgerService.debit(userId, amount, AccountLedgerServiceImpl.BIZ_ORDER,
                    order.getOrderNo(), "创建课程订单");
        }
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
        if (!authorizationService.canReadAllOrders(org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication())) {
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
        if (StrUtil.isNotBlank(request.getUserUid())) {
            if (!PublicUidUtil.isValid(request.getUserUid())) {
                throw new BusinessException(ResultCode.USER_NOT_FOUND);
            }
            User targetUser = userMapper.selectOne(new LambdaQueryWrapper<User>()
                    .eq(User::getUid, PublicUidUtil.normalize(request.getUserUid()))
                    .last("LIMIT 1"));
            if (targetUser == null) {
                throw new BusinessException(ResultCode.USER_NOT_FOUND);
            }
            queryWrapper.eq(CourseOrder::getUserId, targetUser.getId());
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

        authorizationService.requireCanReadOrder(order);

        return order;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void cancelOrder(Long orderId, Long userId) {
        CourseOrder order = getOrderById(orderId, userId);
        authorizationService.requireCanUpdateOrder(order);

        // 退款先锁原账户，再读取订单最新状态；管理员操作不能向自身入账。
        userMapper.selectByIdForUpdate(order.getUserId());
        order = courseOrderMapper.selectByIdForUpdate(orderId);
        if (order == null) throw new BusinessException(ResultCode.ORDER_NOT_FOUND);
        authorizationService.requireCanUpdateOrder(order);
        if (order.getOrderStatus() != getOrderStatus("pending") && order.getOrderStatus() != getOrderStatus("failed"))
            throw new BusinessException("只能取消待处理或失败的订单");
        order.setOrderStatus(getOrderStatus("cancelled"));
        order.setDockStatus(getDockStatus("cancelled"));
        if (courseOrderMapper.updateById(order) != 1) throw new BusinessException("订单取消失败");
        paymentService.refund(order, userId);
        User user = userMapper.selectById(order.getUserId());

        // 记录日志
        operationLogService.log(userId, "取消订单",
                String.format("取消订单：%s，退回：%s元", order.getOrderNo(), order.getAmount()),
                order.getAmount(), user == null ? null : user.getBalance());

        log.info("订单取消成功：orderId={}, userId={}", orderId, userId);
    }

    @Override
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
    public void retryOrder(Long orderId, Long userId) {
        CourseOrder order = getOrderById(orderId, userId);
        authorizationService.requireCanUpdateOrder(order);
        if (order.getRefundedAt() != null || java.util.Objects.equals(order.getOrderStatus(),getOrderStatus("cancelled")))
            throw new BusinessException(ResultCode.CONFLICT,"已取消或退款的订单不能补单");
        if (java.util.Objects.equals(order.getOrderStatus(),getOrderStatus("processing")) && java.util.Objects.equals(order.getDockStatus(),getDockStatus("pending")))
            throw new BusinessException(ResultCode.CONFLICT,"订单正在提交，请等待结果或核查履约状态");
        int retry = order.getRetryCount() == null ? 0 : order.getRetryCount();
        if (retry >= 5) throw new BusinessException("补单次数已达上限");
        CoursePlatform platform = coursePlatformMapper.selectById(order.getPlatformId());
        if (platform == null || platform.getDockApiId() == null) throw new BusinessException("未配置对接接口");
        ApiProvider provider = apiProviderMapper.selectById(platform.getDockApiId());
        if (provider == null || !Integer.valueOf(1).equals(provider.getStatus())) throw new BusinessException("API配置不存在或已禁用");
        // 原子占用独立提交，HTTP期间不持数据库锁；取消先提交时CAS必失败。
        if (order.getOrderStatus() == null || courseOrderMapper.claimRetry(orderId,order.getOrderStatus(),retry,getOrderStatus("processing"),getOrderStatus("cancelled"),getDockStatus("pending")) != 1)
            throw new BusinessException(ResultCode.CONFLICT,"订单状态已变化或补单正在处理");
        try {
            DockResult result = platformDockingService.retryOrder(order,platform,provider);
            boolean success = result != null && result.isSuccess();
            int dock = success ? getDockStatus("success") : getDockStatus("failed");
            String remote = success ? result.getThirdOrderId() : null;
            String remark = success ? "补单成功" : "补单结果需核查，不能自动退款";
            if (courseOrderMapper.finishRetry(orderId,getOrderStatus("processing"),retry+1,dock,remote,remark) != 1)
                throw new BusinessException(ResultCode.CONFLICT,"补单后订单状态已变化，请核查履约结果");
            order.setRetryCount(retry+1);order.setOrderStatus(getOrderStatus("processing"));order.setDockStatus(dock);
            if (remote != null) order.setThirdOrderId(remote);order.setRemarks(remark);
            operationLogService.log(userId,"补单","补单："+order.getOrderNo()+"，第"+(retry+1)+"次",BigDecimal.ZERO,null);
        } catch (Exception error) {
            courseOrderMapper.finishRetry(orderId,getOrderStatus("processing"),retry+1,getDockStatus("failed"),null,"补单结果不确定，请核查供应商订单");
            throw new BusinessException(ResultCode.CONFLICT,"补单结果需核查，请检查订单执行记录");
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateOrderProgress(Long orderId, Long userId) {
        CourseOrder order = getOrderById(orderId, userId);
        authorizationService.requireCanUpdateOrder(order);

        CoursePlatform platform = coursePlatformMapper.selectById(order.getPlatformId());
        if (platform == null) {
            // 历史订单保留原关联，不能按名称匹配新商品或静默改绑供应商。
            throw new BusinessException(ResultCode.NOT_FOUND,
                    "订单关联的课程平台已删除，无法刷新进度，请联系管理员处理");
        }
        if (Integer.valueOf(1).equals(platform.getIsSelfOperated())) {
            calculateProgressBasedOnCountdown(order);
            return;
        }

        if (platform.getDockApiId() == null) {
            throw new BusinessException("订单关联的课程平台未配置对接接口，无法刷新进度");
        }
        if (order.getApiProviderId() != null && !order.getApiProviderId().equals(platform.getDockApiId())) {
            throw new BusinessException(ResultCode.CONFLICT,
                    "订单原对接接口与当前课程配置不一致，无法刷新进度，请联系管理员处理");
        }
        ApiProvider apiProvider = apiProviderMapper.selectById(platform.getDockApiId());
        if (apiProvider == null || !Integer.valueOf(ApiProvider.STATUS_ACTIVE).equals(apiProvider.getStatus())) {
            throw new ProviderRequestException(ProviderRequestException.Reason.PROVIDER_NOT_ACTIVE);
        }

        String previousProgress = order.getProgress();
        Integer previousOrderStatus = order.getOrderStatus();
        String previousRemarks = order.getRemarks();

        // 手动刷新失败必须返回失败；批量同步由独立入口逐单隔离，不能在这里吞掉异常。
        OrderProgressResult result = platformDockingService.queryOrderProgress(order, platform, apiProvider);
        if (result == null) {
            throw new ProviderRequestException(ProviderRequestException.Reason.INVALID_RESPONSE);
        }
        order.setProgress(result.getProgress());
        order.setOrderStatus(result.getOrderStatus());
        if (StrUtil.isNotBlank(result.getRemarks())) {
            order.setRemarks(result.getRemarks());
        }
        if (result.getCourseStartTime() != null) order.setCourseStartTime(result.getCourseStartTime());
        if (result.getCourseEndTime() != null) order.setCourseEndTime(result.getCourseEndTime());
        if (result.getExamStartTime() != null) order.setExamStartTime(result.getExamStartTime());
        if (result.getExamEndTime() != null) order.setExamEndTime(result.getExamEndTime());

        // 原对象保留上次完成证据供提交后审计，不让HTTP期间的退款或归档被旧快照覆盖。
        CourseOrder before=new CourseOrder();org.springframework.beans.BeanUtils.copyProperties(order,before);before.setOrderStatus(previousOrderStatus);before.setProgress(previousProgress);before.setRemarks(previousRemarks);
        if(courseOrderMapper.updateUpstreamIfUnchanged(order,previousOrderStatus,order.getApiProviderId(),order.getThirdOrderId())!=1)throw new BusinessException(ResultCode.CONFLICT,"订单已变更、取消或退款，本次进度未覆盖");
        if(order.getPrivateTermId()!=null){if(fulfillment==null)throw new BusinessException(ResultCode.CONFLICT,"可信履约服务不可用");fulfillment.recordUpstream(before,result);}
        progressLogService.recordIfChanged(order, previousProgress, previousOrderStatus, previousRemarks,
                CourseOrderProgressLog.SOURCE_MANUAL_REFRESH);
        log.info("订单进度更新成功：orderId={}, progress={}", orderId, result.getProgress());
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

        authorizationService.requireCanReadOrder(order);

        return order;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void cancelOrderByOrderNo(String orderNo, Long userId) {
        CourseOrder order = getOrderByOrderNo(orderNo, userId);
        cancelOrder(order.getId(), userId);
    }

    @Override
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
    public void retryOrderByOrderNo(String orderNo, Long userId) {
        CourseOrder order = getOrderByOrderNo(orderNo, userId);
        retryOrder(order.getId(), userId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateOrderProgressByOrderNo(String orderNo, Long userId) {
        CourseOrder order = getOrderByOrderNo(orderNo, userId);
        updateOrderProgress(order.getId(), userId);
    }

    @Override
    public List<CourseOrderProgressLogVO> getProgressLogsByOrderNo(String orderNo, Long userId) {
        CourseOrder order = getOrderByOrderNo(orderNo, userId);
        if (Integer.valueOf(1).equals(order.getIsSelfOperated())) {
            return List.of();
        }
        return progressLogService.listByOrderId(order.getId()).stream()
                .map(progressLog -> CourseOrderProgressLogVO.builder()
                        .id(progressLog.getId())
                        .progress(progressLog.getProgress())
                        .orderStatus(progressLog.getOrderStatus())
                        .remarks(progressLog.getRemarks())
                        .source(progressLog.getSource())
                        .createTime(progressLog.getCreateTime())
                        .build())
                .toList();
    }
}
