package com.course.platform.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.course.platform.application.service.platform.ApiProviderService;
import com.course.platform.common.exception.BusinessException;
import com.course.platform.common.result.ResultCode;
import com.course.platform.common.security.SecretCrypto;
import com.course.platform.domain.entity.ApiProvider;
import com.course.platform.infra.persistence.mapper.ApiProviderMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * 第三方API接口服务实现类（敏感字段加密存储）
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ApiProviderServiceImpl implements ApiProviderService {

    private final ApiProviderMapper apiProviderMapper;

    @Value("${app.crypto.secret:}")
    private String cryptoSecret;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createApiProvider(ApiProvider apiProvider) {
        ApiProvider existing = apiProviderMapper.selectOne(new LambdaQueryWrapper<ApiProvider>()
                .eq(ApiProvider::getName, apiProvider.getName()));
        if (existing != null) {
            throw new BusinessException("接口名称已存在");
        }
        encryptSecrets(apiProvider);
        apiProviderMapper.insert(apiProvider);
        log.info("API接口创建成功：id={}, name={}", apiProvider.getId(), apiProvider.getName());
        return apiProvider.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateApiProvider(ApiProvider apiProvider) {
        ApiProvider existing = apiProviderMapper.selectById(apiProvider.getId());
        if (existing == null) {
            throw new BusinessException(ResultCode.NOT_FOUND.getCode(), "API接口不存在");
        }
        // 列表接口仅返回脱敏账号；编辑时未重新填写账号则保留原值。
        if (!StringUtils.hasText(apiProvider.getUsername())) {
            apiProvider.setUsername(existing.getUsername());
        }
        // 未提交敏感字段时保留原密文
        if (!StringUtils.hasText(apiProvider.getPassword())) {
            apiProvider.setPassword(existing.getPassword());
        }
        if (!StringUtils.hasText(apiProvider.getToken())) {
            apiProvider.setToken(existing.getToken());
        }
        if (!StringUtils.hasText(apiProvider.getApiKey())) {
            apiProvider.setApiKey(existing.getApiKey());
        }
        if (!StringUtils.hasText(apiProvider.getCookie())) {
            apiProvider.setCookie(existing.getCookie());
        }
        encryptSecrets(apiProvider);
        apiProviderMapper.updateById(apiProvider);
        log.info("API接口更新成功：id={}", apiProvider.getId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteApiProvider(Long id) {
        ApiProvider apiProvider = apiProviderMapper.selectById(id);
        if (apiProvider == null) {
            throw new BusinessException(ResultCode.NOT_FOUND.getCode(), "API接口不存在");
        }
        apiProviderMapper.deleteById(id);
        log.info("API接口删除成功：id={}", id);
    }

    @Override
    public IPage<ApiProvider> queryApiProviders(String keyword, Integer status, Integer page, Integer pageSize) {
        Page<ApiProvider> pageObj = new Page<>(page, pageSize);
        LambdaQueryWrapper<ApiProvider> queryWrapper = new LambdaQueryWrapper<>();
        if (StrUtil.isNotBlank(keyword)) {
            queryWrapper.like(ApiProvider::getName, keyword)
                    .or()
                    .like(ApiProvider::getProviderType, keyword);
        }
        if (status != null) {
            queryWrapper.eq(ApiProvider::getStatus, status);
        }
        queryWrapper.orderByDesc(ApiProvider::getCreateTime);
        return apiProviderMapper.selectPage(pageObj, queryWrapper);
    }

    /**
     * 运行时解密（供对接服务调用）
     */
    @Override
    public ApiProvider loadDecrypted(Long id) {
        ApiProvider provider = apiProviderMapper.selectById(id);
        return decryptSecrets(provider);
    }

    private void encryptSecrets(ApiProvider provider) {
        if (provider == null || !StringUtils.hasText(cryptoSecret)) {
            return;
        }
        provider.setPassword(SecretCrypto.encrypt(provider.getPassword(), cryptoSecret));
        provider.setToken(SecretCrypto.encrypt(provider.getToken(), cryptoSecret));
        provider.setApiKey(SecretCrypto.encrypt(provider.getApiKey(), cryptoSecret));
        provider.setCookie(SecretCrypto.encrypt(provider.getCookie(), cryptoSecret));
    }

    private ApiProvider decryptSecrets(ApiProvider provider) {
        if (provider == null) {
            return null;
        }
        if (!StringUtils.hasText(cryptoSecret)) {
            if (hasEncryptedSecrets(provider)) {
                throw new BusinessException("API凭据已加密，但系统未配置解密密钥");
            }
            return provider;
        }
        provider.setPassword(SecretCrypto.decrypt(provider.getPassword(), cryptoSecret));
        provider.setToken(SecretCrypto.decrypt(provider.getToken(), cryptoSecret));
        provider.setApiKey(SecretCrypto.decrypt(provider.getApiKey(), cryptoSecret));
        provider.setCookie(SecretCrypto.decrypt(provider.getCookie(), cryptoSecret));
        return provider;
    }

    private boolean hasEncryptedSecrets(ApiProvider provider) {
        return SecretCrypto.isEncrypted(provider.getPassword())
                || SecretCrypto.isEncrypted(provider.getToken())
                || SecretCrypto.isEncrypted(provider.getApiKey())
                || SecretCrypto.isEncrypted(provider.getCookie());
    }
}
