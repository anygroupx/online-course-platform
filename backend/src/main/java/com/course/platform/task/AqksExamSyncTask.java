package com.course.platform.task;

import com.course.platform.service.AqksStudyService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * AQKS考试状态同步定时任务
 * 
 * 定时查询所有待考试/考试中的AQKS自营订单，
 * 检查考试成绩并自动更新订单状态
 * 
 * 配置项：
 * - course.business.aqks-exam-sync.enabled: 是否启用（默认true）
 * - course.business.aqks-exam-sync.interval-milliseconds: 执行间隔（默认5分钟）
 * 
 * @author AI Assistant
 * @since 2025-12-22
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "course.business.aqks-exam-sync", name = "enabled", havingValue = "true", matchIfMissing = true)
public class AqksExamSyncTask {

    private final AqksStudyService aqksStudyService;

    /**
     * 批量同步AQKS考试状态
     * 
     * 执行间隔通过配置文件 course.business.aqks-exam-sync.interval-milliseconds 控制
     * 默认5分钟执行一次
     */
    @Scheduled(fixedRateString = "${course.business.aqks-exam-sync.interval-milliseconds:300000}")
    public void syncExamStatus() {
        try {
            log.info("[AQKS考试同步任务] 开始执行");
            
            // 调用服务层批量同步方法
            Map<String, Object> result = aqksStudyService.syncExamStatusForPendingOrders();
            
            int total = (int) result.getOrDefault("total", 0);
            int success = (int) result.getOrDefault("success", 0);
            int failed = (int) result.getOrDefault("failed", 0);
            
            log.info("[AQKS考试同步任务] 执行完成: 总计={}, 成功={}, 失败={}", 
                    total, success, failed);
            
        } catch (Exception e) {
            log.error("[AQKS考试同步任务] 执行失败: {}", e.getMessage(), e);
        }
    }
}
