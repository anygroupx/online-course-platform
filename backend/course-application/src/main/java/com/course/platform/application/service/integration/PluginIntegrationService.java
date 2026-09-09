package com.course.platform.application.service.integration;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.course.platform.domain.dto.PluginPageQuery;
import com.course.platform.domain.vo.plugin.*;

import java.util.List;

public interface PluginIntegrationService {
    List<PluginIntegrationDescriptor> listIntegrations();
    IPage<PluginProviderOption> listProviders(String pluginId, PluginPageQuery query);
    List<PluginProduct> fetchCatalog(String pluginId, Long providerId, String project);
    PluginSchoolPage searchSchools(String pluginId, Long providerId, PluginPageQuery query);
}
