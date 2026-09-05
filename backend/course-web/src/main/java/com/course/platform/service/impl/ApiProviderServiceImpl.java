package com.course.platform.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.course.platform.application.service.platform.ApiProviderService;
import com.course.platform.application.service.platform.docking.PlatformDockingStrategy;
import com.course.platform.common.exception.BusinessException;
import com.course.platform.common.result.ResultCode;
import com.course.platform.common.security.SecretCrypto;
import com.course.platform.domain.entity.ApiProvider;
import com.course.platform.domain.exception.ProviderRequestException;
import com.course.platform.domain.vo.ProviderConnectionTestResult;
import com.course.platform.infra.docking.PlatformDockingStrategyFactory;
import com.course.platform.infra.persistence.mapper.ApiProviderMapper;
import com.course.platform.infra.http.ProviderOutboundPolicyFactory;
import com.course.platform.infra.http.ProviderUrlNormalizer;
import com.course.platform.infra.http.SafeHttpException;
import com.course.platform.infra.http.SsrfGuard;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

/** Provider configuration is an admin-controlled, per-origin allowlist, with explicit verification. */
@Slf4j
@Service
@RequiredArgsConstructor
public class ApiProviderServiceImpl implements ApiProviderService {

    private final ApiProviderMapper apiProviderMapper;
    private final SsrfGuard ssrfGuard;
    private final ProviderOutboundPolicyFactory providerPolicyFactory;
    private final ProviderUrlNormalizer providerUrlNormalizer;
    private final PlatformDockingStrategyFactory strategyFactory;

    @Value("${app.crypto.secret:}")
    private String cryptoSecret;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createApiProvider(ApiProvider input) {
        requireStrategy(input.getProviderType());
        validateStatus(input.getStatus());
        if (!StringUtils.hasText(input.getName())) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "请输入接口名称");
        }
        if (apiProviderMapper.selectCount(new LambdaQueryWrapper<ApiProvider>()
                .eq(ApiProvider::getName, input.getName())) > 0) {
            throw new BusinessException("接口名称已存在");
        }
        ApiProvider provider = writableCopy(input);
        provider.setId(null);
        provider.setApiUrl(validateAndNormalizeProviderUrl(provider.getApiUrl(), provider.getProviderType()));
        provider.setStatus(Integer.valueOf(ApiProvider.STATUS_DISABLED).equals(input.getStatus())
                ? ApiProvider.STATUS_DISABLED : ApiProvider.STATUS_PENDING);
        provider.setConfigVersion(0L);
        encryptSecrets(provider);
        apiProviderMapper.insert(provider);
        log.info("API provider created: providerId={}, status={}", provider.getId(), provider.getStatus());
        return provider.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateApiProvider(ApiProvider input) {
        ApiProvider existing = requireProvider(input.getId());
        validateStatus(input.getStatus());
        ApiProvider update = writableCopy(input);
        if (!StringUtils.hasText(update.getName())) update.setName(existing.getName());
        if (!StringUtils.hasText(update.getProviderType())) update.setProviderType(existing.getProviderType());
        if (!StringUtils.hasText(update.getApiUrl())) update.setApiUrl(existing.getApiUrl());
        requireStrategy(update.getProviderType());
        if (apiProviderMapper.selectCount(new LambdaQueryWrapper<ApiProvider>()
                .eq(ApiProvider::getName, update.getName()).ne(ApiProvider::getId, existing.getId())) > 0) {
            throw new BusinessException("接口名称已存在");
        }
        update.setApiUrl(validateAndNormalizeProviderUrl(update.getApiUrl(), update.getProviderType()));
        boolean targetChanged = !Objects.equals(canonicalExistingUrl(existing), update.getApiUrl())
                || !Objects.equals(existing.getProviderType(), update.getProviderType());
        boolean credentialsChanged = (StringUtils.hasText(update.getUsername())
                && !Objects.equals(update.getUsername(), existing.getUsername()))
                || StringUtils.hasText(update.getPassword()) || StringUtils.hasText(update.getApiKey())
                || StringUtils.hasText(update.getToken()) || StringUtils.hasText(update.getCookie());
        boolean requiresVerification = targetChanged || credentialsChanged;
        preserveBlankCredentials(update, existing);
        encryptSecrets(update);

        int status = input.getStatus() == null ? existing.getStatus() : input.getStatus();
        if (requiresVerification) {
            // A form saved with its old 'active' radio value cannot authorize the new target.
            status = status == ApiProvider.STATUS_DISABLED ? ApiProvider.STATUS_DISABLED : ApiProvider.STATUS_PENDING;
        } else if (status == ApiProvider.STATUS_ACTIVE && !Integer.valueOf(ApiProvider.STATUS_ACTIVE).equals(existing.getStatus())) {
            requireVerified(existing);
        }
        update.setStatus(status);
        update.setConfigVersion(version(existing) + 1);
        UpdateWrapper<ApiProvider> condition = currentVersion(existing);
        if (requiresVerification) clearVerification(condition);
        requireUpdated(apiProviderMapper.update(update, condition));
        log.info("API provider updated: providerId={}, status={}, requiresVerification={}",
                update.getId(), status, requiresVerification);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateStatus(Long id, Integer status) {
        if (status == null || (status != ApiProvider.STATUS_ACTIVE && status != ApiProvider.STATUS_DISABLED)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "状态只能为启用或禁用");
        }
        ApiProvider existing = requireProvider(id);
        if (status == ApiProvider.STATUS_ACTIVE) {
            requireVerified(existing);
            validateAndNormalizeProviderUrl(existing.getApiUrl(), existing.getProviderType());
        }
        // Disabling never performs DNS/HTTP I/O, including during an upstream outage.
        requireUpdated(apiProviderMapper.update(null, currentVersion(existing)
                .set("status", status).set("config_version", version(existing) + 1)
                .set("update_time", LocalDateTime.now())));
    }

    @Override
    public ProviderConnectionTestResult testConnection(Long id, Long operatorId) {
        if (operatorId == null) throw new BusinessException(ResultCode.UNAUTHORIZED);
        ApiProvider stored = requireProvider(id);
        long started = System.nanoTime();
        ApiProvider candidate = runtimeCopy(stored);
        try {
            probe(candidate);
        } catch (ProviderRequestException ex) {
            recordCheck(stored, ex.getReason().name(), ex.getErrorId());
            logProbe(candidate, "test-connection", ex.getReason().name(), ex.getErrorId(), started);
            throw ex;
        }
        LocalDateTime verifiedAt = LocalDateTime.now();
        // HTTP is deliberately outside a DB transaction. An edit/disable/delete during the call
        // invalidates this result instead of approving credentials or an origin we never tested.
        requireUpdated(apiProviderMapper.update(null, currentVersion(stored)
                .set("verified_at", verifiedAt).set("verified_by", operatorId)
                .set("checked_at", verifiedAt).set("last_check_reason", "SUCCESS")
                .set("last_check_error_id", null)));
        logProbe(candidate, "test-connection", "SUCCESS", "-", started);
        URI normalized = providerUrlNormalizer.normalize(candidate.getApiUrl(), candidate.getProviderType());
        return new ProviderConnectionTestResult(normalized.toASCIIString(), normalized.getHost(),
                elapsed(started), verifiedAt, operatorId, stored.getStatus());
    }

    @Override
    public void checkHealth(Long id) {
        ApiProvider stored = apiProviderMapper.selectById(id);
        if (stored == null || !Integer.valueOf(ApiProvider.STATUS_ACTIVE).equals(stored.getStatus())) return;
        long started = System.nanoTime();
        ApiProvider candidate = runtimeCopy(stored);
        String reason = "SUCCESS";
        String errorId = null;
        try {
            probe(candidate);
        } catch (ProviderRequestException ex) {
            reason = ex.getReason().name();
            errorId = ex.getErrorId();
        }
        // Health checks report health only: never activate, disable or approve a configuration.
        if (recordCheck(stored, reason, errorId) && !Objects.equals(reason, stored.getLastCheckReason())) {
            logProbe(candidate, "health-check", reason, errorId, started);
        }
    }

    private void probe(ApiProvider candidate) {
        candidate.setApiUrl(validateAndNormalizeProviderUrl(candidate.getApiUrl(), candidate.getProviderType()));
        // This isolated decrypted copy is used only for the explicit read-only probe. The stored
        // activation state is never changed to bypass normal business request authorization.
        candidate.setStatus(ApiProvider.STATUS_ACTIVE);
        try {
            requireStrategy(candidate.getProviderType()).testConnection(candidate);
        } catch (ProviderRequestException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            // Strategy/JSON converters may quote unsafe response content in their exceptions.
            throw new ProviderRequestException(ProviderRequestException.Reason.INVALID_RESPONSE);
        }
    }

    private boolean recordCheck(ApiProvider stored, String reason, String errorId) {
        return apiProviderMapper.update(null, currentVersion(stored)
                .set("checked_at", LocalDateTime.now()).set("last_check_reason", reason)
                .set("last_check_error_id", errorId)) == 1;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteApiProvider(Long id) {
        requireProvider(id);
        apiProviderMapper.deleteById(id);
        log.info("API provider deleted: providerId={}", id);
    }

    @Override
    public IPage<ApiProvider> queryApiProviders(String keyword, Integer status, Integer page, Integer pageSize) {
        Page<ApiProvider> pageObj = new Page<>(page, pageSize);
        LambdaQueryWrapper<ApiProvider> query = new LambdaQueryWrapper<>();
        if (StrUtil.isNotBlank(keyword)) {
            query.and(q -> q.like(ApiProvider::getName, keyword).or().like(ApiProvider::getProviderType, keyword));
        }
        if (status != null) query.eq(ApiProvider::getStatus, status);
        query.orderByDesc(ApiProvider::getCreateTime);
        return apiProviderMapper.selectPage(pageObj, query);
    }

    @Override
    public ApiProvider loadDecrypted(Long id) {
        ApiProvider stored = apiProviderMapper.selectById(id);
        return stored == null ? null : runtimeCopy(stored);
    }

    private ApiProvider requireProvider(Long id) {
        if (id == null) throw new BusinessException(ResultCode.PARAM_ERROR, "缺少API接口ID");
        ApiProvider provider = apiProviderMapper.selectById(id);
        if (provider == null) throw new BusinessException(ResultCode.NOT_FOUND, "API接口不存在");
        return provider;
    }

    private PlatformDockingStrategy requireStrategy(String type) {
        PlatformDockingStrategy strategy = StringUtils.hasText(type) ? strategyFactory.getStrategy(type) : null;
        if (strategy == null) throw new BusinessException(ResultCode.PARAM_ERROR, "请选择已支持的接口类型");
        return strategy;
    }

    private void requireVerified(ApiProvider provider) {
        if (provider.getVerifiedAt() == null || !"SUCCESS".equals(provider.getLastCheckReason())) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "请先测试连接，验证通过后再启用");
        }
    }

    private void validateStatus(Integer status) {
        if (status != null && (status < ApiProvider.STATUS_DISABLED || status > ApiProvider.STATUS_PENDING)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "无效的接口状态");
        }
    }

    private String canonicalExistingUrl(ApiProvider provider) {
        try {
            return providerUrlNormalizer.normalize(provider.getApiUrl(), provider.getProviderType()).toASCIIString();
        } catch (SafeHttpException ex) {
            return null; // Allow an administrator to fix an invalid legacy URL, requiring fresh verification.
        }
    }

    private String validateAndNormalizeProviderUrl(String value, String type) {
        try {
            URI normalized = providerUrlNormalizer.normalize(value, type);
            ssrfGuard.validate(normalized, providerPolicyFactory.forCandidate(normalized));
            return normalized.toASCIIString();
        } catch (SafeHttpException ex) {
            throw new ProviderRequestException(ProviderRequestException.Reason.valueOf(ex.getReason().name()));
        }
    }

    private ApiProvider writableCopy(ApiProvider input) {
        ApiProvider copy = new ApiProvider();
        copy.setId(input.getId());
        copy.setName(input.getName());
        copy.setProviderType(input.getProviderType());
        copy.setApiUrl(input.getApiUrl());
        copy.setUsername(input.getUsername());
        copy.setPassword(input.getPassword());
        copy.setApiKey(input.getApiKey());
        copy.setToken(input.getToken());
        copy.setCookie(input.getCookie());
        return copy;
    }

    private ApiProvider runtimeCopy(ApiProvider stored) {
        ApiProvider copy = new ApiProvider();
        BeanUtils.copyProperties(stored, copy);
        return decryptSecrets(copy);
    }

    private void preserveBlankCredentials(ApiProvider update, ApiProvider existing) {
        if (!StringUtils.hasText(update.getUsername())) update.setUsername(existing.getUsername());
        if (!StringUtils.hasText(update.getPassword())) update.setPassword(existing.getPassword());
        if (!StringUtils.hasText(update.getApiKey())) update.setApiKey(existing.getApiKey());
        if (!StringUtils.hasText(update.getToken())) update.setToken(existing.getToken());
        if (!StringUtils.hasText(update.getCookie())) update.setCookie(existing.getCookie());
    }

    private long version(ApiProvider provider) {
        return provider.getConfigVersion() == null ? 0 : provider.getConfigVersion();
    }

    private UpdateWrapper<ApiProvider> currentVersion(ApiProvider provider) {
        return new UpdateWrapper<ApiProvider>().eq("id", provider.getId()).eq("config_version", version(provider));
    }

    private void clearVerification(UpdateWrapper<ApiProvider> update) {
        update.set("verified_at", null).set("verified_by", null)
                .set("checked_at", null).set("last_check_reason", null).set("last_check_error_id", null);
    }

    private void requireUpdated(int count) {
        if (count != 1) throw new BusinessException(ResultCode.PARAM_ERROR, "配置已变化或已删除，请刷新后重新操作/测试");
    }

    private long elapsed(long started) {
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started);
    }

    private void logProbe(ApiProvider provider, String operation, String reason, String errorId, long started) {
        String host = "unknown";
        try { host = providerUrlNormalizer.normalize(provider.getApiUrl()).getHost(); }
        catch (SafeHttpException ignored) { /* Never log the unsafe original URL. */ }
        String type = provider.getProviderType();
        if (type == null || !type.matches("[A-Za-z0-9_-]{1,50}")) type = "unknown";
        log.info("Provider probe: providerId={}, providerType={}, operation={}, normalizedHost={}, reason={}, errorId={}, durationMs={}",
                provider.getId(), type, operation, host, reason, errorId, elapsed(started));
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
