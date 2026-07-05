package com.course.platform.application.service.platform;

import com.course.platform.domain.dto.aqks.AqksLoginResult;

/**
 * AQKS自动刷课服务接口
 * 
 * 提供自营订单的自动刷课、手动刷课、状态查询等功能
 * 
 * @author AI Assistant
 * @since 2025-12-20
 */
public interface AqksStudyService {
    
    /**
     * 启动自动刷课任务
     * 
     * @param orderId 订单ID
     * @return 是否启动成功
     */
    boolean startAutoStudy(Long orderId);
    
    /**
     * 停止自动刷课任务
     * 
     * @param orderId 订单ID
     * @return 是否停止成功
     */
    boolean stopAutoStudy(Long orderId);
    
    /**
     * 手动刷一次时长
     * 
     * @param orderId 订单ID
     * @param deltaSeconds 要累加的时长（秒），默认10秒
     * @return 是否成功
     */
    boolean addStudyTimeOnce(Long orderId, int deltaSeconds);
    
    /**
     * 获取订单的实时学习状态
     * 
     * @param orderId 订单ID
     * @return 登录结果（包含学习进度）
     */
    AqksLoginResult getStudyStatus(Long orderId);
    
    /**
     * 检查自动刷课任务是否在运行
     * 
     * @param orderId 订单ID
     * @return 是否在运行
     */
    boolean isAutoStudyRunning(Long orderId);
    
    /**
     * 获取当前运行的自动刷课任务数量
     * 
     * @return 任务数量
     */
    int getRunningTaskCount();
    
    /**
     * 获取AQKS统计数据
     * 返回运行中任务数、待考试订单数、已完成订单数、自营订单总数
     * 
     * @return 统计数据Map
     */
    java.util.Map<String, Integer> getAqksStatistics();
    
    /**
     * 检查并更新单个订单的考试状态
     * 
     * 流程：
     * 1. 登录AQKS平台
     * 2. 获取考试成绩
     * 3. 根据考试结果更新订单状态
     * 4. 将考试详情添加到订单备注
     * 
     * @param orderId 订单ID
     * @return 考试信息，如果失败返回null
     */
    com.course.platform.domain.dto.aqks.AqksExamInfo checkAndUpdateExamStatus(Long orderId);
    
    /**
     * 批量同步所有待考试/考试中订单的考试状态
     * 
     * 用于定时任务或手动触发
     * 
     * @return 处理结果，包含成功数、失败数等
     */
    java.util.Map<String, Object> syncExamStatusForPendingOrders();
}
