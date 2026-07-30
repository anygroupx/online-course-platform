package com.course.platform.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.course.platform.application.service.payment.PaymentConfigService;
import com.course.platform.common.security.SecretCrypto;
import com.course.platform.config.AlipayClientFactory;
import com.course.platform.domain.entity.PaymentConfig;
import com.course.platform.infra.persistence.mapper.PaymentConfigMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * 支付配置服务实现类（私钥本地 AES-GCM 加密存储）
 */
@Slf4j
@Service
public class PaymentConfigServiceImpl implements PaymentConfigService {

    @Autowired
    private PaymentConfigMapper paymentConfigMapper;

    @Autowired
    private AlipayClientFactory alipayClientFactory;

    @Value("${app.crypto.secret:}")
    private String cryptoSecret;

    @Override
    public PaymentConfig getActiveConfig() {
        PaymentConfig config = paymentConfigMapper.selectOne(
            new LambdaQueryWrapper<PaymentConfig>()
                .eq(PaymentConfig::getIsActive, 1)
                .eq(PaymentConfig::getStatus, 1)
                .last("LIMIT 1")
        );
        return decryptSecrets(config);
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
        encryptSecrets(config);

        int result = paymentConfigMapper.insert(config);
        if (result > 0 && config.getIsActive() != null && config.getIsActive() == 1) {
            alipayClientFactory.refreshClient();
        }
        return result > 0;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean update(PaymentConfig config) {
        PaymentConfig existing = paymentConfigMapper.selectById(config.getId());
        if (existing == null) {
            return false;
        }
        // 若前端未回传完整私钥（脱敏/空），保留原密文
        if (!StringUtils.hasText(config.getPrivateKey())) {
            config.setPrivateKey(existing.getPrivateKey());
        }
        if (!StringUtils.hasText(config.getAlipayPublicKey())) {
            config.setAlipayPublicKey(existing.getAlipayPublicKey());
        }
        encryptSecrets(config);
        int result = paymentConfigMapper.updateById(config);
        if (result > 0) {
            alipayClientFactory.refreshClient();
        }
        return result > 0;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean activate(Long id) {
        paymentConfigMapper.update(null, new LambdaUpdateWrapper<PaymentConfig>()
                .set(PaymentConfig::getIsActive, 0));
        int result = paymentConfigMapper.update(null, new LambdaUpdateWrapper<PaymentConfig>()
                .eq(PaymentConfig::getId, id)
                .set(PaymentConfig::getIsActive, 1)
                .set(PaymentConfig::getStatus, 1));
        if (result > 0) {
            alipayClientFactory.refreshClient();
        }
        return result > 0;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean delete(Long id) {
        int result = paymentConfigMapper.deleteById(id);
        if (result > 0) {
            alipayClientFactory.refreshClient();
        }
        return result > 0;
    }

    private void encryptSecrets(PaymentConfig config) {
        if (config == null) {
            return;
        }
        if (StringUtils.hasText(cryptoSecret)) {
            config.setPrivateKey(SecretCrypto.encrypt(config.getPrivateKey(), cryptoSecret));
            config.setAlipayPublicKey(SecretCrypto.encrypt(config.getAlipayPublicKey(), cryptoSecret));
        }
    }

    private PaymentConfig decryptSecrets(PaymentConfig config) {
        if (config == null || !StringUtils.hasText(cryptoSecret)) {
            return config;
        }
        config.setPrivateKey(SecretCrypto.decrypt(config.getPrivateKey(), cryptoSecret));
        config.setAlipayPublicKey(SecretCrypto.decrypt(config.getAlipayPublicKey(), cryptoSecret));
        return config;
    }
}
