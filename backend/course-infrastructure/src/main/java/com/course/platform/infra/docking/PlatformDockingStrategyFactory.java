package com.course.platform.infra.docking;

import com.course.platform.application.service.platform.docking.PlatformDockingStrategy;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 平台对接策略工厂
 */
@Component
public class PlatformDockingStrategyFactory {

    private final Map<String, PlatformDockingStrategy> strategyMap = new ConcurrentHashMap<>();

    public PlatformDockingStrategyFactory(List<PlatformDockingStrategy> strategies) {
        for (PlatformDockingStrategy strategy : strategies) {
            strategyMap.put(strategy.getProviderType(), strategy);
        }
    }

    /**
     * 获取对接策略
     *
     * @param providerType 平台类型
     * @return 对应的策略实现，如果不存在返回null
     */
    public PlatformDockingStrategy getStrategy(String providerType) {
        return strategyMap.get(providerType);
    }
    
    /**
     * 注册策略（用于动态添加）
     */
    public void registerStrategy(PlatformDockingStrategy strategy) {
        strategyMap.put(strategy.getProviderType(), strategy);
    }
}
