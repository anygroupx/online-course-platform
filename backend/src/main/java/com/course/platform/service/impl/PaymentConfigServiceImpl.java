package com.course.platform.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.course.platform.config.AlipayClientFactory;
import com.course.platform.domain.entity.PaymentConfig;
import com.course.platform.mapper.PaymentConfigMapper;
import com.course.platform.service.PaymentConfigService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 支付配置服务实现类
 * 
 * @author AI Assistant
 * @date 2025-11-26
 */
@Slf4j
@Service
public class PaymentConfigServiceImpl implements PaymentConfigService {

    @Autowired
    private PaymentConfigMapper paymentConfigMapper;

    @Autowired
    private AlipayClientFactory alipayClientFactory;

    @Override
    public PaymentConfig getActiveConfig() {
        return paymentConfigMapper.selectOne(
            new LambdaQueryWrapper<PaymentConfig>()
                .eq(PaymentConfig::getIsActive, 1)
                .eq(PaymentConfig::getStatus, 1)
                .last("LIMIT 1")
        );
    }

    @Override
    public List<PaymentConfig> getAllConfigs() {
        return paymentConfigMapper.selectList(
            new LambdaQueryWrapper<PaymentConfig>()
                .orderByDesc(PaymentConfig::getIsActive)
                .orderByDesc(PaymentConfig::getCreateTime)
        );
    }

    @Override
    public PaymentConfig getById(Long id) {
        return paymentConfigMapper.selectById(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean create(PaymentConfig config) {
        // 设置默认值
        if (config.getSignType() == null) {
            config.setSignType("RSA2");
        }
        if (config.getFormat() == null) {
            config.setFormat("json");
        }
        if (config.getCharset() == null) {
            config.setCharset("utf-8");
        }
        if (config.getStatus() == null) {
            config.setStatus(1);
        }
        if (config.getIsActive() == null) {
            config.setIsActive(0);
        }

        int result = paymentConfigMapper.insert(config);
        
        if (result > 0 && config.getIsActive() == 1) {
            // 如果新配置被激活,需要刷新客户端
            alipayClientFactory.refreshClient();
        }

        return result > 0;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean update(PaymentConfig config) {
        int result = paymentConfigMapper.updateById(config);
        
        if (result > 0) {
            // 如果更新的是激活配置,需要刷新客户端
            PaymentConfig activeConfig = getActiveConfig();
            if (activeConfig != null && activeConfig.getId().equals(config.getId())) {
                alipayClientFactory.refreshClient();
            }
        }

        return result > 0;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean activate(Long id) {
        // 1. 取消所有配置的激活状态
        // 注意：使用 update(null, wrapper) 不会触发自动填充，需要手动设置更新时间
        LambdaUpdateWrapper<PaymentConfig> updateWrapper = new LambdaUpdateWrapper<>();
        updateWrapper.set(PaymentConfig::getIsActive, 0)
                     .set(PaymentConfig::getUpdateTime, java.time.LocalDateTime.now());
        paymentConfigMapper.update(null, updateWrapper);

        // 2. 激活指定配置
        PaymentConfig config = paymentConfigMapper.selectById(id);
        if (config == null) {
            throw new RuntimeException("配置不存在");
        }

        config.setIsActive(1);
        int result = paymentConfigMapper.updateById(config);

        if (result > 0) {
            // 刷新客户端
            alipayClientFactory.refreshClient();
            log.info("支付配置已激活，ID：{}，环境：{}", id, config.getEnvType());
        }

        return result > 0;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean delete(Long id) {
        PaymentConfig config = paymentConfigMapper.selectById(id);
        
        if (config == null) {
            return false;
        }

        if (config.getIsActive() == 1) {
            throw new RuntimeException("无法删除激活的配置，请先激活其他配置");
        }

        return paymentConfigMapper.deleteById(id) > 0;
    }
}
