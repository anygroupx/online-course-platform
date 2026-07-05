package com.course.platform.listener;

import com.course.platform.infra.cache.SystemVariableCache;
import com.course.platform.common.constant.Constants;
import com.course.platform.domain.dto.DockResult;
import com.course.platform.domain.entity.ApiProvider;
import com.course.platform.domain.entity.CourseOrder;
import com.course.platform.domain.entity.CoursePlatform;
import com.course.platform.domain.event.OrderCreatedEvent;
import com.course.platform.infra.persistence.mapper.ApiProviderMapper;
import com.course.platform.infra.persistence.mapper.CourseOrderMapper;
import com.course.platform.infra.persistence.mapper.CoursePlatformMapper;
import com.course.platform.application.service.platform.PlatformDockingService;
import com.course.platform.application.service.platform.AqksStudyService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 订单事件监听器
 * 监听订单创建事件，对第三方订单自动触发对接，对自营订单自动启动刷课
 * 
 * @author AI Assistant
 * @since 2025-11-22
 * 修改记录：2025-12-21 增加自营订单自动启动刷课功能
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderEventListener {

    private final CourseOrderMapper courseOrderMapper;
    private final CoursePlatformMapper coursePlatformMapper;
    private final ApiProviderMapper apiProviderMapper;
    private final PlatformDockingService platformDockingService;
    private final AqksStudyService aqksStudyService;

    /**
     * 获取订单状态值
     * @param key 状态键名
     * @return 状态值
     */
    private static int getOrderStatus(String key) {
        return SystemVariableCache.getStatusValue("order_status", key);
    }

    /**
     * 获取对接状态值
     * @param key 状态键名
     * @return 状态值
     */
    private static int getDockStatus(String key) {
        return SystemVariableCache.getStatusValue("dock_status", key);
    }

    /**
     * 处理订单创建事件
     * 使用 @TransactionalEventListener 确保在事务提交后触发
     * 使用 @Async 异步执行，不阻塞主线程
     *
     * @param event 订单创建事件
     */
    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleOrderCreated(OrderCreatedEvent event) {
        Long orderId = event.getOrderId();
        Integer isSelfOperated = event.getIsSelfOperated();

        // 自营订单：自动启动刷课任务
        if (isSelfOperated != null && isSelfOperated == 1) {
            log.info("自营订单创建成功，自动启动刷课：orderId={}", orderId);
            handleSelfOperatedOrder(orderId);
            return;
        }

        // 处理第三方订单：自动对接
        try {
            // 查询订单详情
            CourseOrder order = courseOrderMapper.selectById(orderId);
            if (order == null) {
                log.error("订单不存在：orderId={}", orderId);
                return;
            }

            // 检查是否已对接（防止重复）
            if (order.getDockStatus() != getDockStatus("pending")) {
                log.warn("订单已对接，跳过：orderId={}, dockStatus={}", orderId, order.getDockStatus());
                return;
            }

            // 查询平台配置
            CoursePlatform platform = coursePlatformMapper.selectById(order.getPlatformId());
            if (platform == null || platform.getDockApiId() == null) {
                log.warn("平台未配置对接接口：platformId={}", order.getPlatformId());
                return;
            }

            // 查询API配置
            ApiProvider apiProvider = apiProviderMapper.selectById(platform.getDockApiId());
            if (apiProvider == null || apiProvider.getStatus() != 1) {
                log.warn("API配置不存在或已禁用：apiProviderId={}", platform.getDockApiId());
                return;
            }

            // 调用对接服务
            log.info("开始对接第三方订单：orderId={}, platform={}", orderId, platform.getName());
            DockResult result = platformDockingService.dockOrder(order, platform, apiProvider);

            // 更新订单状态
            if (result.isSuccess()) {
                order.setOrderStatus(getOrderStatus("processing"));
                order.setDockStatus(getDockStatus("success"));
                if (result.getThirdOrderId() != null) {
                    order.setThirdOrderId(result.getThirdOrderId());
                }
                order.setRemarks("自动对接成功");
                log.info("订单对接成功：orderId={}, thirdOrderId={}", orderId, result.getThirdOrderId());
            } else {
                order.setDockStatus(getDockStatus("failed"));
                order.setRemarks("自动对接失败：" + result.getMessage());
                log.error("订单对接失败：orderId={}, error={}", orderId, result.getMessage());
            }

            courseOrderMapper.updateById(order);

        } catch (Exception e) {
            log.error("处理订单创建事件失败：orderId={}", orderId, e);
            // 尝试更新订单状态为对接失败
            try {
                CourseOrder order = courseOrderMapper.selectById(orderId);
                if (order != null) {
                    order.setDockStatus(getDockStatus("failed"));
                    order.setRemarks("自动对接异常：" + e.getMessage());
                    courseOrderMapper.updateById(order);
                }
            } catch (Exception ex) {
                log.error("更新订单对接状态失败：orderId={}", orderId, ex);
            }
        }
    }

    /**
     * 处理自营订单：自动启动刷课任务
     * 
     * @param orderId 订单ID
     */
    private void handleSelfOperatedOrder(Long orderId) {
        try {
            // 查询订单详情
            CourseOrder order = courseOrderMapper.selectById(orderId);
            if (order == null) {
                log.error("[自营订单自动启动] 订单不存在：orderId={}", orderId);
                return;
            }

            // 调用AQKS刷课服务启动自动刷课
            // AqksStudyService内部会自动验证账号、检查任务状态
            boolean success = aqksStudyService.startAutoStudy(orderId);

            if (success) {
                log.info("[自营订单自动启动] 刷课任务启动成功：orderId={}, account={}", 
                        orderId, order.getStudentAccount());
            } else {
                log.warn("[自营订单自动启动] 刷课任务启动失败（任务可能已运行）：orderId={}", orderId);
            }

        } catch (Exception e) {
            log.error("[自营订单自动启动] 刷课任务启动异常：orderId={}", orderId, e);
            
            // 尝试更新订单备注，记录启动失败信息
            try {
                CourseOrder order = courseOrderMapper.selectById(orderId);
                if (order != null) {
                    order.setRemarks("自动启动刷课失败：" + e.getMessage());
                    courseOrderMapper.updateById(order);
                }
            } catch (Exception ex) {
                log.error("[自营订单自动启动] 更新订单备注失败：orderId={}", orderId, ex);
            }
        }
    }
}
