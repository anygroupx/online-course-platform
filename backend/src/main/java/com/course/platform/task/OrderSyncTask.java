package com.course.platform.task;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.course.platform.domain.entity.ApiProvider;
import com.course.platform.mapper.ApiProviderMapper;
import com.course.platform.service.PlatformDockingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 订单同步定时任务
 * 定时批量同步第三方订单进度
 * 
 * @author AI Assistant
 * @since 2025-11-22
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "course.business.order-sync", name = "enabled", havingValue = "true", matchIfMissing = true)
public class OrderSyncTask {

    private final ApiProviderMapper apiProviderMapper;
    private final PlatformDockingService platformDockingService;

    /**
     * 批量同步所有第三方订单进度
     * 同步间隔通过配置文件 course.business.order-sync.interval-milliseconds 控制
     */
    @Scheduled(fixedRateString = "${course.business.order-sync.interval-milliseconds:300000}")
    public void syncAllOrderProgress() {
        try {
            log.info("开始批量同步订单进度");

            // 查询所有启用的 ApiProvider
            List<ApiProvider> apiProviders = apiProviderMapper.selectList(
                    new LambdaQueryWrapper<ApiProvider>()
                            .eq(ApiProvider::getStatus, 1)
            );

            if (apiProviders.isEmpty()) {
                log.info("没有启用的API配置，跳过同步");
                return;
            }

            int totalSynced = 0;

            // 遍历每个 provider 进行同步
            for (ApiProvider provider : apiProviders) {
                try {
                    log.info("同步API提供商订单：providerId={}, name={}", provider.getId(), provider.getName());

                    // 调用批量同步服务
                    Map<String, Object> result = platformDockingService.batchSyncOrderProgress(
                            provider.getId(),
                            provider.getLastSyncTime(), // 使用上次同步时间进行增量同步
                            0 // offset从0开始
                    );

                    Integer updated = (Integer) result.get("totalUpdated");
                    if (updated != null) {
                        totalSynced += updated;
                        log.info("同步完成：providerId={}, 更新订单数={}", provider.getId(), updated);
                    }

                } catch (Exception e) {
                    log.error("同步API提供商订单失败：providerId={}, error={}", provider.getId(), e.getMessage(), e);
                    // 继续处理下一个provider，不中断整体流程
                }
            }

            log.info("批量同步订单进度完成，总计更新：{} 个订单", totalSynced);

        } catch (Exception e) {
            log.error("批量同步订单进度失败：{}", e.getMessage(), e);
        }
    }
}
