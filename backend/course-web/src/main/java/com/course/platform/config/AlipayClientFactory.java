package com.course.platform.config;

import com.alipay.api.AlipayClient;
import com.alipay.api.DefaultAlipayClient;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.course.platform.domain.entity.PaymentConfig;
import com.course.platform.infra.persistence.mapper.PaymentConfigMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;

/**
 * 支付宝客户端工厂
 * 单例模式管理AlipayClient实例
 * 
 * @author AI Assistant
 * @date 2025-11-26
 */
@Slf4j
@Component
public class AlipayClientFactory {

    @Autowired
    private PaymentConfigMapper paymentConfigMapper;

    private volatile AlipayClient alipayClient;
    private volatile PaymentConfig currentConfig;

    /**
     * 初始化时加载配置
     */
    @PostConstruct
    public void init() {
        refreshClient();
    }

    /**
     * 获取支付宝客户端实例
     */
    public AlipayClient getClient() {
        if (alipayClient == null) {
            synchronized (this) {
                if (alipayClient == null) {
                    refreshClient();
                }
            }
        }
        return alipayClient;
    }

    /**
     * 获取当前激活的配置
     */
    public PaymentConfig getCurrentConfig() {
        if (currentConfig == null) {
            synchronized (this) {
                if (currentConfig == null) {
                    refreshClient();
                }
            }
        }
        return currentConfig;
    }

    /**
     * 刷新客户端配置
     * 配置变更时可调用此方法
     */
    public synchronized void refreshClient() {
        try {
            // 查询激活的配置
            LambdaQueryWrapper<PaymentConfig> queryWrapper = new LambdaQueryWrapper<>();
            queryWrapper.eq(PaymentConfig::getIsActive, 1)
                       .eq(PaymentConfig::getStatus, 1)
                       .last("LIMIT 1");
            
            PaymentConfig config = paymentConfigMapper.selectOne(queryWrapper);
            
            if (config == null) {
                log.warn("未找到激活的支付配置，支付功能将不可用");
                this.currentConfig = null;
                this.alipayClient = null;
                return;
            }

            // 创建新的客户端实例
            this.alipayClient = new DefaultAlipayClient(
                config.getGatewayUrl(),
                config.getAppId(),
                config.getPrivateKey(),
                config.getFormat(),
                config.getCharset(),
                config.getAlipayPublicKey(),
                config.getSignType()
            );

            this.currentConfig = config;
            
            log.info("支付宝客户端初始化成功，环境：{}，APPID：{}", 
                    config.getEnvType(), config.getAppId());

        } catch (Exception e) {
            log.error("支付宝客户端初始化失败", e);
            throw new RuntimeException("支付宝客户端初始化失败：" + e.getMessage());
        }
    }

    /**
     * 检查支付功能是否可用
     */
    public boolean isAvailable() {
        return alipayClient != null && currentConfig != null;
    }
}
