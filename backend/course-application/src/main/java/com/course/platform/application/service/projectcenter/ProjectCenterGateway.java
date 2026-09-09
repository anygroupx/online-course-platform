package com.course.platform.application.service.projectcenter;

import com.course.platform.domain.entity.ApiProvider;
import com.course.platform.domain.projectcenter.ProjectCenterTypes.*;

import java.math.BigDecimal;
import java.util.List;

public interface ProjectCenterGateway {
    List<CatalogItem> projects(ApiProvider provider);

    CustomerReceipt provision(ApiProvider provider, String remoteProjectId);

    CustomerReceipt customer(ApiProvider provider, String remoteProjectId, String customerId);

    AdjustmentReceipt adjust(
            ApiProvider provider,
            String remoteProjectId,
            String customerId,
            BigDecimal signedUnits,
            String operationId);
}
