package com.course.platform.infra.servicecommerce;

import com.course.platform.application.service.servicecommerce.NativeServiceGateway;
import com.course.platform.domain.entity.ApiProvider;
import com.course.platform.domain.servicecommerce.*;
import com.course.platform.domain.servicecommerce.ServiceCommerceTypes.*;

import lombok.RequiredArgsConstructor;

import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@Primary
@RequiredArgsConstructor
public class NativeServiceGatewayRouter implements NativeServiceGateway {
    private final PhpNativeServiceGateway templates;
    private final WuxinNativeServiceGateway wuxin;
    private final InternshipNativeServiceGateway internship;
    private final SsbenzDistanceGateway distance;

    private NativeServiceGateway forProvider(ApiProvider p) {
        return switch (p.getProviderType()) {
            case "wuxin" -> wuxin;
            case "sxdk_tw" -> internship;
            case SsbenzDistanceGateway.TYPE -> distance;
            case "flash", "heisha", "jiguang" -> templates;
            default ->
                    throw new com.course.platform.common.exception.BusinessException("不支持的原生服务接口");
        };
    }

    @Override
    public Lookup lookup(ApiProvider p, ServiceProduct product, Map<String, String> fields) {
        return forProvider(p).lookup(p, product, fields);
    }

    @Override
    public PreparedOrder prepare(ApiProvider p, ServiceProduct product, OrderForm form) {
        return forProvider(p).prepare(p, product, form);
    }

    @Override
    public int refundRemaining(ApiProvider p, ServiceOrder order) {
        return forProvider(p).refundRemaining(p, order);
    }

    @Override
    public void checkAddTimes(ApiProvider p, ServiceOrder order, int quantity) {
        forProvider(p).checkAddTimes(p, order, quantity);
    }

    @Override
    public PreparedAction prepareScheduledAction(
            ApiProvider p, ServiceProduct product, ServiceOrder order, ActionForm form) {
        return forProvider(p).prepareScheduledAction(p, product, order, form);
    }

    @Override
    public com.course.platform.domain.vo.plugin.PluginSchoolPage schools(
            ApiProvider p, ServiceProduct product, int page, String keyword) {
        return forProvider(p).schools(p, product, page, keyword);
    }

    @Override
    public RemoteResult execute(
            ApiProvider p,
            ServiceProduct product,
            ServiceOrder order,
            String action,
            Map<String, Object> fields) {
        return forProvider(p).execute(p, product, order, action, fields);
    }

    @Override
    public RemoteResult sync(ApiProvider p, ServiceOrder order) {
        return forProvider(p).sync(p, order);
    }

    @Override
    public RunLogPage logs(ApiProvider p, ServiceOrder order, int page) {
        return forProvider(p).logs(p, order, page);
    }

    @Override
    public Lookup orderOptions(ApiProvider p, ServiceOrder order) {
        return forProvider(p).orderOptions(p, order);
    }

    @Override
    public Map<String, Object> prepareAction(
            ApiProvider p, ServiceOrder order, String action, Map<String, String> fields) {
        return forProvider(p).prepareAction(p, order, action, fields);
    }
}
