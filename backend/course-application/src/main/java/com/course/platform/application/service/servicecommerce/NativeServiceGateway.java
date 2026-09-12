package com.course.platform.application.service.servicecommerce;

import com.course.platform.domain.entity.ApiProvider;
import com.course.platform.domain.servicecommerce.*;
import com.course.platform.domain.servicecommerce.ServiceCommerceTypes.*;

import java.util.Map;

public interface NativeServiceGateway {
    Lookup lookup(ApiProvider provider, ServiceProduct product, Map<String, String> fields);

    PreparedOrder prepare(ApiProvider provider, ServiceProduct product, OrderForm form);

    int refundRemaining(ApiProvider provider, ServiceOrder order);

    void checkAddTimes(ApiProvider provider, ServiceOrder order, int quantity);

    default Lookup orderOptions(ApiProvider provider, ServiceOrder order) {
        throw new com.course.platform.common.exception.BusinessException("该服务不支持读取计划配置");
    }

    default Map<String, Object> prepareAction(
            ApiProvider provider, ServiceOrder order, String action, Map<String, String> fields) {
        if (fields != null && !fields.isEmpty())
            throw new com.course.platform.common.exception.BusinessException("此操作不接受额外参数");
        return Map.of();
    }

    default PreparedAction prepareScheduledAction(
            ApiProvider provider, ServiceProduct product, ServiceOrder order, ActionForm form) {
        throw new com.course.platform.common.exception.BusinessException("该服务不支持实习计划操作");
    }

    default com.course.platform.domain.vo.plugin.PluginSchoolPage schools(
            ApiProvider provider, ServiceProduct product, int page, String keyword) {
        throw new com.course.platform.common.exception.BusinessException("该服务不支持学校查询");
    }

    RemoteResult execute(
            ApiProvider provider,
            ServiceProduct product,
            ServiceOrder order,
            String action,
            Map<String, Object> fields);

    RunLogPage logs(ApiProvider provider, ServiceOrder order, int page);

    RemoteResult sync(ApiProvider provider, ServiceOrder order);

    default OrderText scoreInfo(ApiProvider provider, ServiceOrder order) {
        throw new com.course.platform.common.exception.BusinessException("该服务不支持成绩信息查询");
    }
}
