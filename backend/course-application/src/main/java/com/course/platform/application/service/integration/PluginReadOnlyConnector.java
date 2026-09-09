package com.course.platform.application.service.integration;

import com.course.platform.application.service.platform.docking.ProviderConnectionProbe;
import com.course.platform.domain.dto.PluginPageQuery;
import com.course.platform.domain.entity.ApiProvider;
import com.course.platform.domain.exception.ProviderRequestException;
import com.course.platform.domain.vo.plugin.*;

import java.util.List;

/** No order, refund, arbitrary-action, account-login or executable-plugin API by design. */
public interface PluginReadOnlyConnector extends ProviderConnectionProbe {
    List<PluginProduct> fetchCatalog(ApiProvider provider, String project);

    default boolean supportsSchools() { return false; }
    default List<PluginProjectOption> projects() { return List.of(); }

    default PluginSchoolPage searchSchools(ApiProvider provider, PluginPageQuery query) {
        throw new ProviderRequestException(ProviderRequestException.Reason.UNSUPPORTED_OPERATION);
    }
}
