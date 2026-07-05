package com.course.platform.service.impl;

import com.course.platform.infra.cache.SystemVariableCache;
import com.course.platform.common.constant.Constants;
import com.course.platform.common.exception.BusinessException;
import com.course.platform.infra.external.AqksApiClient;
import com.course.platform.domain.dto.aqks.AqksLoginResult;
import com.course.platform.domain.entity.CourseOrder;
import com.course.platform.infra.persistence.mapper.CourseOrderMapper;
import com.course.platform.application.service.platform.AqksStudyService;
import com.course.platform.application.service.support.OperationLogService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Map;
import java.util.concurrent.*;

/**
 * AQKS自动刷课服务实现
 * 
 * 管理自营订单的自动刷课任务
 * 
 * @author AI Assistant
 * @since 2025-12-20
 */
@Slf4j
@Service
public class AqksStudyServiceImpl implements AqksStudyService {
    
    private final AqksApiClient aqksApiClient;
    private final CourseOrderMapper courseOrderMapper;
    private final OperationLogService operationLogService;
    
    /**
     * 自动刷课任务执行器
     */
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(10);
    
    /**
     * 正在运行的任务映射：orderId -> Future
     */
    private final Map<Long, ScheduledFuture<?>> runningTasks = new ConcurrentHashMap<>();
    
    /**
     * 任务会话缓存：orderId -> 登录信息
     */
    private final Map<Long, AqksLoginResult> sessionCache = new ConcurrentHashMap<>();
    
    /**
     * 刷课间隔（秒）- 每10秒发送一次心跳包
     */
    private static final int STUDY_INTERVAL_SECONDS = 10;
    
    /**
     * 每次刷课增量（秒）- 默认10秒
     */
    private static final int STUDY_DELTA_SECONDS = 10;
    
    public AqksStudyServiceImpl(AqksApiClient aqksApiClient, 
                                 CourseOrderMapper courseOrderMapper,
                                 OperationLogService operationLogService) {
        this.aqksApiClient = aqksApiClient;
        this.courseOrderMapper = courseOrderMapper;
        this.operationLogService = operationLogService;
    }
    
    @Override
    public boolean startAutoStudy(Long orderId) {
        log.info("[AQKS自动刷课] 启动任务: orderId={}", orderId);
        
        // 检查是否已在运行
        if (runningTasks.containsKey(orderId)) {
            log.warn("[AQKS自动刷课] 任务已在运行: orderId={}", orderId);
            return false;
        }
        
        // 获取订单信息
        CourseOrder order = courseOrderMapper.selectById(orderId);
        if (order == null) {
            throw new BusinessException("订单不存在");
        }
        
        // 验证是否为自营订单
        if (order.getIsSelfOperated() == null || order.getIsSelfOperated() != 1) {
            throw new BusinessException("只有自营订单才能使用此功能");
        }
        
        // 登录获取会话
        AqksLoginResult loginResult = aqksApiClient.login(
                order.getStudentAccount(),
                order.getStudentPassword()
        );
        
        if (!loginResult.isSuccess()) {
            throw new BusinessException("AQKS登录失败: " + loginResult.getErrorMessage());
        }
        
        // 检查学习进度（Source: AURA-X-KYS - 智能状态同步）
        int currentTime = 0;
        int required = 0;
        try {
            currentTime = Integer.parseInt(loginResult.getStudyTimes());
            required = Integer.parseInt(loginResult.getMinTimeMinute());
        } catch (NumberFormatException e) {
            log.warn("[AQKS自动刷课] 解析时长失败，使用默认值: orderId={}", orderId);
        }
        
        // 如果进度已达100%，自动切换订单状态为待考试
        if (required > 0 && currentTime >= required) {
            log.info("[AQKS自动刷课] 学习进度已达100%，切换状态为待考试: orderId={}, {}/{}", 
                    orderId, currentTime, required);
            
            // 更新订单状态为待考试（使用系统变量缓存）
            // Source: AURA-X-KYS - 统一状态管理
            int examPendingStatus = SystemVariableCache.getStatusValue("order_status", "exam_pending");
            order.setOrderStatus(examPendingStatus);  // 5-待考试
            String progress = String.format("100%% (%d/%d分钟)", currentTime, required);
            order.setProgress(progress);
            courseOrderMapper.updateById(order);
            
            // 记录日志
            operationLogService.log(1L, "启动刷课-自动切换状态",
                    String.format("订单号: %s, 进度已达100%%，自动切换为待考试", order.getOrderNo()),
                    BigDecimal.ZERO, null);
            
            throw new BusinessException("学习进度已达100%，订单已自动切换为待考试状态");
        }
        
        // 检查是否已经完成
        if (loginResult.isStudyCompleted()) {
            log.info("[AQKS自动刷课] 学习已完成，无需刷课: orderId={}", orderId);
            return false;
        }
        
        // 如果订单状态是待处理，启动刷课时切换为进行中
        // Source: AURA-X-KYS - 统一状态管理
        int pendingStatus = SystemVariableCache.getStatusValue("order_status", "pending");
        int processingStatus = SystemVariableCache.getStatusValue("order_status", "processing");
        
        if (order.getOrderStatus() != null && order.getOrderStatus() == pendingStatus) {
            order.setOrderStatus(processingStatus);  // 1-进行中
            courseOrderMapper.updateById(order);
            log.info("[AQKS自动刷课] 订单状态切换为进行中: orderId={}", orderId);
        }
        
        // 缓存会话信息
        sessionCache.put(orderId, loginResult);
        
        // 创建定时任务
        ScheduledFuture<?> future = scheduler.scheduleAtFixedRate(
                () -> executeStudyTask(orderId, order),
                0,  // 立即执行第一次
                STUDY_INTERVAL_SECONDS,
                TimeUnit.SECONDS
        );
        
        runningTasks.put(orderId, future);
        
        // 记录日志
        operationLogService.log(1L, "启动自动刷课",
                String.format("订单号: %s, 学生: %s", order.getOrderNo(), order.getStudentAccount()),
                BigDecimal.ZERO, null);
        
        log.info("[AQKS自动刷课] 任务启动成功: orderId={}, 间隔={}秒", orderId, STUDY_INTERVAL_SECONDS);
        
        return true;
    }
    
    @Override
    public boolean stopAutoStudy(Long orderId) {
        log.info("[AQKS自动刷课] 停止任务: orderId={}", orderId);
        
        ScheduledFuture<?> future = runningTasks.remove(orderId);
        if (future != null) {
            future.cancel(false);
            sessionCache.remove(orderId);
            
            // 记录日志
            CourseOrder order = courseOrderMapper.selectById(orderId);
            if (order != null) {
                operationLogService.log(1L, "停止自动刷课",
                        String.format("订单号: %s", order.getOrderNo()),
                        BigDecimal.ZERO, null);
            }
            
            log.info("[AQKS自动刷课] 任务停止成功: orderId={}", orderId);
            return true;
        }
        
        return false;
    }
    
    @Override
    public boolean addStudyTimeOnce(Long orderId, int deltaSeconds) {
        log.info("[AQKS手动刷课] orderId={}, delta={}秒", orderId, deltaSeconds);
        
        CourseOrder order = courseOrderMapper.selectById(orderId);
        if (order == null) {
            throw new BusinessException("订单不存在");
        }
        
        // 验证是否为自营订单
        if (order.getIsSelfOperated() == null || order.getIsSelfOperated() != 1) {
            throw new BusinessException("只有自营订单才能使用此功能");
        }
        
        // 登录
        AqksLoginResult loginResult = aqksApiClient.login(
                order.getStudentAccount(),
                order.getStudentPassword()
        );
        
        if (!loginResult.isSuccess()) {
            throw new BusinessException("AQKS登录失败: " + loginResult.getErrorMessage());
        }
        
        // 刷时长（单位：秒）
        boolean success = aqksApiClient.addStudyTime(
                loginResult.getUserId(),
                deltaSeconds,
                loginResult.getServerCookie()
        );
        
        if (success) {
            // 记录日志
            operationLogService.log(1L, "手动刷时长",
                    String.format("订单号: %s, 增加: %d秒", order.getOrderNo(), deltaSeconds),
                    BigDecimal.ZERO, null);
            
            log.info("[AQKS手动刷课] 成功: +{}秒", deltaSeconds);
        }
        
        return success;
    }
    
    @Override
    public AqksLoginResult getStudyStatus(Long orderId) {
        log.debug("[AQKS状态查询] orderId={}", orderId);
        
        CourseOrder order = courseOrderMapper.selectById(orderId);
        if (order == null) {
            throw new BusinessException("订单不存在");
        }
        
        // 从缓存获取或重新登录
        AqksLoginResult cached = sessionCache.get(orderId);
        if (cached != null) {
            // 刷新最新状态
            AqksLoginResult fresh = aqksApiClient.login(
                    order.getStudentAccount(),
                    order.getStudentPassword()
            );
            if (fresh.isSuccess()) {
                sessionCache.put(orderId, fresh);
                return fresh;
            }
            return cached;
        }
        
        return aqksApiClient.login(order.getStudentAccount(), order.getStudentPassword());
    }
    
    @Override
    public boolean isAutoStudyRunning(Long orderId) {
        return runningTasks.containsKey(orderId);
    }
    
    @Override
    public int getRunningTaskCount() {
        return runningTasks.size();
    }
    
    // ==================== 私有方法 ====================
    
    /**
     * 执行单次刷课任务
     */
    private void executeStudyTask(Long orderId, CourseOrder order) {
        try {
            log.debug("[AQKS刷课任务] 执行: orderId={}", orderId);
            
            // 获取缓存的会话，会话过期则重新登录
            AqksLoginResult session = sessionCache.get(orderId);
            if (session == null) {
                session = aqksApiClient.login(order.getStudentAccount(), order.getStudentPassword());
                if (!session.isSuccess()) {
                    log.error("[AQKS刷课任务] 登录失败，停止任务: orderId={}", orderId);
                    stopAutoStudy(orderId);
                    return;
                }
                sessionCache.put(orderId, session);
            }
            
            // 刷时长（单位：秒）
            boolean success = aqksApiClient.addStudyTime(
                    session.getUserId(),
                    STUDY_DELTA_SECONDS,
                    session.getServerCookie()
            );
            
            if (success) {
                // 通过重新登录获取最新时长
                AqksLoginResult freshLogin = aqksApiClient.login(
                        order.getStudentAccount(), 
                        order.getStudentPassword()
                );
                
                int currentTime = 0;
                int required = 0;
                
                if (freshLogin.isSuccess()) {
                    sessionCache.put(orderId, freshLogin);  // 更新缓存
                    try {
                        currentTime = Integer.parseInt(freshLogin.getStudyTimes());
                        required = Integer.parseInt(freshLogin.getMinTimeMinute());
                    } catch (NumberFormatException e) {
                        required = 450;  // 默认值
                    }
                } else {
                    try {
                        required = Integer.parseInt(session.getMinTimeMinute());
                    } catch (NumberFormatException e) {
                        required = 450;
                    }
                }
                
                log.info("[AQKS刷课任务] 成功: orderId={}, +{}秒, 当前={}/{}分钟", 
                        orderId, STUDY_DELTA_SECONDS, currentTime, required);
                
                // 更新订单进度
                if (required > 0) {
                    String progress = String.format("%d%% (%d/%d分钟)", 
                            Math.min(100, (currentTime * 100) / required),
                            currentTime, required);
                    order.setProgress(progress);
                    courseOrderMapper.updateById(order);
                }
                
                // 检查是否完成
                if (required > 0 && currentTime >= required) {
                    log.info("[AQKS刷课任务] 学习时长已满足，停止任务: orderId={}", orderId);
                    
                    // 更新订单状态为待考试（使用系统变量缓存）
                    // Source: AURA-X-KYS - 统一状态管理
                    int examPendingStatus = SystemVariableCache.getStatusValue("order_status", "exam_pending");
                    order.setOrderStatus(examPendingStatus);  // 5-待考试
                    courseOrderMapper.updateById(order);
                    
                    // 停止任务
                    stopAutoStudy(orderId);
                    
                    // 记录日志
                    operationLogService.log(1L, "自动刷课完成",
                            String.format("订单号: %s, 学习时长已满足", order.getOrderNo()),
                            BigDecimal.ZERO, null);
                }
            } else {
                log.warn("[AQKS刷课任务] 刷时长失败: orderId={}", orderId);
                
                // 尝试重新登录
                session = aqksApiClient.login(order.getStudentAccount(), order.getStudentPassword());
                if (session.isSuccess()) {
                    sessionCache.put(orderId, session);
                }
            }
            
        } catch (Exception e) {
            log.error("[AQKS刷课任务] 执行异常: orderId={}", orderId, e);
        }
    }
    
    @Override
    public java.util.Map<String, Integer> getAqksStatistics() {
        log.info("[AQKS服务] 获取统计数据");
        
        java.util.Map<String, Integer> stats = new java.util.HashMap<>();
        
        // 运行中任务数
        stats.put("runningCount", runningTasks.size());
        
        // 使用MyBatis-Plus查询
        com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<CourseOrder> baseQuery = 
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<CourseOrder>()
                        .eq(CourseOrder::getIsSelfOperated, 1);
        
        // 自营订单总数
        Long total = courseOrderMapper.selectCount(baseQuery);
        stats.put("total", total.intValue());
        
        // 待考试订单数（使用系统变量缓存）
        // Source: AURA-X-KYS - 统一状态管理
        int examPendingStatus = SystemVariableCache.getStatusValue("order_status", "exam_pending");
        Long pendingExam = courseOrderMapper.selectCount(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<CourseOrder>()
                        .eq(CourseOrder::getIsSelfOperated, 1)
                        .eq(CourseOrder::getOrderStatus, examPendingStatus));
        stats.put("pendingExam", pendingExam.intValue());
        
        // 已完成订单数（状态=2 已完成 或 7 考试完成）
        int completedStatus = SystemVariableCache.getStatusValue("order_status", "completed");
        int examCompletedStatus = SystemVariableCache.getStatusValue("order_status", "exam_completed");
        Long completed = courseOrderMapper.selectCount(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<CourseOrder>()
                        .eq(CourseOrder::getIsSelfOperated, 1)
                        .and(w -> w.eq(CourseOrder::getOrderStatus, completedStatus)
                                .or().eq(CourseOrder::getOrderStatus, examCompletedStatus)));
        stats.put("completed", completed.intValue());
        
        log.info("[AQKS服务] 统计结果: {}", stats);
        return stats;
    }
    
    @Override
    public com.course.platform.domain.dto.aqks.AqksExamInfo checkAndUpdateExamStatus(Long orderId) {
        log.info("[AQKS考试检查] 开始检查订单: orderId={}", orderId);
        
        // 获取订单
        CourseOrder order = courseOrderMapper.selectById(orderId);
        if (order == null) {
            throw new BusinessException("订单不存在");
        }
        
        // 验证是否为自营订单
        if (order.getIsSelfOperated() == null || order.getIsSelfOperated() != 1) {
            throw new BusinessException("只有自营订单才能使用此功能");
        }
        
        // 调用API获取考试状态
        com.course.platform.domain.dto.aqks.AqksExamInfo examInfo = 
                aqksApiClient.checkExamStatus(order.getStudentAccount(), order.getStudentPassword());
        
        if (examInfo == null) {
            log.warn("[AQKS考试检查] 未获取到考试信息: orderId={}", orderId);
            return null;
        }
        
        // Source: AURA-X-KYS - 统一状态管理
        int examPendingStatus = SystemVariableCache.getStatusValue("order_status", "exam_pending");
        int examProcessingStatus = SystemVariableCache.getStatusValue("order_status", "exam_processing");
        int examCompletedStatus = SystemVariableCache.getStatusValue("order_status", "exam_completed");
        int completedStatus = SystemVariableCache.getStatusValue("order_status", "completed");
        
        Integer currentStatus = order.getOrderStatus();
        Integer newStatus = currentStatus;
        String statusChangeReason = "";
        
        // 根据考试结果判断订单状态
        if (Boolean.TRUE.equals(examInfo.getIsPassed())) {
            // 考试已通过
            log.info("[AQKS考试检查] 考试已通过: orderId={}, score={}", orderId, examInfo.getScore());
            
            // 先切换为考试完成，再切换为已完成
            if (currentStatus != completedStatus && currentStatus != examCompletedStatus) {
                newStatus = completedStatus;  // 直接切换为已完成
                statusChangeReason = "考试已通过，分数: " + examInfo.getScore();
            }
        } else if (Boolean.TRUE.equals(examInfo.getIsSubmit())) {
            // 已提交但未通过
            log.info("[AQKS考试检查] 考试已提交但未通过: orderId={}, score={}", orderId, examInfo.getScore());
            
            // 保持在考试中状态
            if (currentStatus == examPendingStatus) {
                newStatus = examProcessingStatus;
                statusChangeReason = "考试已提交，未通过，分数: " + examInfo.getScore();
            }
        } else {
            // 未参加考试或考试中
            log.info("[AQKS考试检查] 考试进行中或未开始: orderId={}", orderId);
            
            // 如果是待考试状态，切换为考试中
            if (currentStatus == examPendingStatus) {
                newStatus = examProcessingStatus;
                statusChangeReason = "考试进行中";
            }
        }
        
        // 更新订单
        boolean statusChanged = !newStatus.equals(currentStatus);
        
        // 生成考试详情备注
        String examRemark = examInfo.toRemarkString();
        String existingRemarks = order.getRemarks();
        String newRemarks;
        
        // 合并备注，避免重复
        if (existingRemarks != null && existingRemarks.contains("【AQKS考试成绩】")) {
            // 替换旧的考试成绩信息
            int startIdx = existingRemarks.indexOf("【AQKS考试成绩】");
            int endIdx = existingRemarks.indexOf("\n\n", startIdx);
            if (endIdx == -1) {
                endIdx = existingRemarks.length();
            }
            newRemarks = existingRemarks.substring(0, startIdx) + examRemark;
            if (endIdx < existingRemarks.length()) {
                newRemarks += existingRemarks.substring(endIdx);
            }
        } else {
            newRemarks = (existingRemarks != null ? existingRemarks + "\n\n" : "") + examRemark;
        }
        
        order.setRemarks(newRemarks);
        
        if (statusChanged) {
            order.setOrderStatus(newStatus);
            log.info("[AQKS考试检查] 订单状态变更: orderId={}, {} -> {}", 
                    orderId, currentStatus, newStatus);
        }
        
        courseOrderMapper.updateById(order);
        
        // 记录操作日志
        operationLogService.log(Constants.DEFAULT_ADMIN_ID, "AQKS考试检查",
                String.format("订单号: %s, 考试%s, 分数: %d, %s", 
                        order.getOrderNo(),
                        Boolean.TRUE.equals(examInfo.getIsPassed()) ? "通过" : "未通过",
                        examInfo.getScore(),
                        statusChanged ? "状态变更: " + statusChangeReason : "状态未变"),
                java.math.BigDecimal.ZERO, null);
        
        log.info("[AQKS考试检查] 完成: orderId={}, passed={}, score={}", 
                orderId, examInfo.getIsPassed(), examInfo.getScore());
        
        return examInfo;
    }
    
    @Override
    public java.util.Map<String, Object> syncExamStatusForPendingOrders() {
        log.info("[AQKS考试同步] 开始批量同步考试状态");
        
        java.util.Map<String, Object> result = new java.util.HashMap<>();
        int total = 0;
        int success = 0;
        int failed = 0;
        java.util.List<String> errors = new java.util.ArrayList<>();
        
        // 获取待考试和考试中的自营订单
        // Source: AURA-X-KYS - 统一状态管理
        int examPendingStatus = SystemVariableCache.getStatusValue("order_status", "exam_pending");
        int examProcessingStatus = SystemVariableCache.getStatusValue("order_status", "exam_processing");
        
        java.util.List<CourseOrder> orders = courseOrderMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<CourseOrder>()
                        .eq(CourseOrder::getIsSelfOperated, 1)
                        .in(CourseOrder::getOrderStatus, examPendingStatus, examProcessingStatus)
        );
        
        total = orders.size();
        log.info("[AQKS考试同步] 找到 {} 个待处理订单", total);
        
        for (CourseOrder order : orders) {
            try {
                com.course.platform.domain.dto.aqks.AqksExamInfo examInfo = 
                        checkAndUpdateExamStatus(order.getId());
                if (examInfo != null) {
                    success++;
                } else {
                    failed++;
                    errors.add("订单 " + order.getOrderNo() + ": 未获取到考试信息");
                }
            } catch (Exception e) {
                failed++;
                errors.add("订单 " + order.getOrderNo() + ": " + e.getMessage());
                log.error("[AQKS考试同步] 处理失败: orderId={}, error={}", 
                        order.getId(), e.getMessage(), e);
            }
            
            // 每个订单之间稍作延迟，避免请求过快
            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        
        result.put("total", total);
        result.put("success", success);
        result.put("failed", failed);
        result.put("errors", errors);
        
        log.info("[AQKS考试同步] 完成: 总计={}, 成功={}, 失败={}", total, success, failed);
        
        return result;
    }
}
