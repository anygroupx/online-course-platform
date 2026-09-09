package com.course.platform.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.course.platform.application.service.integration.PluginIntegrationService;
import com.course.platform.application.service.integration.PluginReadOnlyConnector;
import com.course.platform.application.service.platform.ApiProviderService;
import com.course.platform.common.exception.BusinessException;
import com.course.platform.common.result.ResultCode;
import com.course.platform.domain.dto.PluginPageQuery;
import com.course.platform.domain.entity.ApiProvider;
import com.course.platform.domain.exception.ProviderRequestException;
import com.course.platform.domain.vo.plugin.*;
import com.course.platform.infra.integration.PluginConnectorRegistry;
import com.course.platform.infra.integration.PluginResearchCatalog;
import com.course.platform.infra.persistence.mapper.ApiProviderMapper;
import com.course.platform.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.function.Supplier;

@Service
@RequiredArgsConstructor
public class PluginIntegrationServiceImpl implements PluginIntegrationService {
    private final PluginResearchCatalog research;
    private final PluginConnectorRegistry connectors;
    private final ApiProviderMapper providerMapper;
    private final ApiProviderService providers;

    @Override public List<PluginIntegrationDescriptor> listIntegrations() {
        SecurityUtils.requireAuthority("api-provider:update");
        return research.list();
    }

    @Override public IPage<PluginProviderOption> listProviders(String pluginId, PluginPageQuery query) {
        SecurityUtils.requireAuthority("api-provider:update");
        PluginReadOnlyConnector connector = requireConnector(pluginId);
        requireQuery(query);
        LambdaQueryWrapper<ApiProvider> filter = new LambdaQueryWrapper<ApiProvider>()
                .select(ApiProvider::getId, ApiProvider::getName, ApiProvider::getProviderType,
                        ApiProvider::getStatus, ApiProvider::getVerifiedAt)
                .eq(ApiProvider::getProviderType, connector.getProviderType())
                .like(!query.keyword().isEmpty(), ApiProvider::getName, query.keyword())
                .orderByDesc(ApiProvider::getId);
        IPage<ApiProvider> found = providerMapper.selectPage(new Page<>(query.page(), query.pageSize()), filter);
        Page<PluginProviderOption> result = new Page<>(found.getCurrent(), found.getSize(), found.getTotal());
        result.setRecords(found.getRecords().stream().map(p -> new PluginProviderOption(
                p.getId(), p.getName(), p.getProviderType(), p.getStatus(), p.getVerifiedAt() != null)).toList());
        return result;
    }

    @Override public List<PluginProduct> fetchCatalog(String pluginId, Long providerId, String project) {
        SecurityUtils.requireAuthority("api-provider:update");
        PluginReadOnlyConnector connector = requireConnector(pluginId);
        if (project != null && !project.isEmpty()
                && connector.projects().stream().noneMatch(p -> p.id().equals(project))) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "请选择支持的项目，此接口不接受其他项目参数");
        }
        ApiProvider provider = requireActiveProvider(providerId, connector);
        return invoke(() -> connector.fetchCatalog(provider, project));
    }

    @Override public PluginSchoolPage searchSchools(String pluginId, Long providerId, PluginPageQuery query) {
        SecurityUtils.requireAuthority("api-provider:update");
        PluginReadOnlyConnector connector = requireConnector(pluginId);
        if (!connector.supportsSchools()) throw unsupported();
        requireQuery(query);
        ApiProvider provider = requireActiveProvider(providerId, connector);
        return invoke(() -> connector.searchSchools(provider, query));
    }

    private PluginReadOnlyConnector requireConnector(String pluginId) {
        PluginIntegrationDescriptor descriptor = research.find(pluginId);
        if (descriptor == null) throw new BusinessException(ResultCode.NOT_FOUND, "未找到插件研究记录");
        PluginReadOnlyConnector connector = connectors.getConnector(descriptor.providerType());
        if (connector == null) throw unsupported();
        return connector;
    }

    private ApiProvider requireActiveProvider(Long id, PluginReadOnlyConnector connector) {
        if (id == null || id <= 0) throw new BusinessException(ResultCode.PARAM_ERROR, "请选择有效的接口配置");
        ApiProvider provider = providers.loadDecrypted(id);
        if (provider == null) throw new BusinessException(ResultCode.NOT_FOUND, "接口配置不存在");
        if (!connector.getProviderType().equals(provider.getProviderType())) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "接口类型与所选插件不匹配");
        }
        if (!Integer.valueOf(ApiProvider.STATUS_ACTIVE).equals(provider.getStatus()) || provider.getVerifiedAt() == null) {
            throw new ProviderRequestException(ProviderRequestException.Reason.PROVIDER_NOT_ACTIVE);
        }
        return provider;
    }

    private void requireQuery(PluginPageQuery query) {
        if (query == null) throw new BusinessException(ResultCode.PARAM_ERROR, "缺少分页参数");
    }

    private <T> T invoke(Supplier<T> operation) {
        try {
            return operation.get();
        } catch (ProviderRequestException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw new ProviderRequestException(ProviderRequestException.Reason.INVALID_RESPONSE);
        }
    }

    private ProviderRequestException unsupported() {
        return new ProviderRequestException(ProviderRequestException.Reason.UNSUPPORTED_OPERATION);
    }
}
