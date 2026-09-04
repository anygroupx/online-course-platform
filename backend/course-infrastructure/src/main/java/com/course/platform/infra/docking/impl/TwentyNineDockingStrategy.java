package com.course.platform.infra.docking.impl;

import com.course.platform.infra.external.ApiHttpClient;
import org.springframework.stereotype.Component;

/**
 * 旧版 29 类型标识兼容适配器。
 *
 * <p>协议实现统一由 {@link DaytimeDockingStrategy} 提供，避免继续复用参数不兼容的 Benz 策略。</p>
 */
@Component
public class TwentyNineDockingStrategy extends DaytimeDockingStrategy {

    public TwentyNineDockingStrategy(ApiHttpClient apiHttpClient) {
        super(apiHttpClient);
    }

    @Override
    public String getProviderType() {
        return "29";
    }
}
