package com.course.platform.application.service.servicecommerce;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.course.platform.domain.servicecommerce.ServiceCommerceTypes.*;
import com.course.platform.domain.servicecommerce.ServiceOrderFilter;
import com.course.platform.domain.vo.plugin.PluginSchoolPage;

import java.util.List;
import java.util.Map;

public interface ServiceCommerceService {
    IPage<ProductView> products(int page, int size, boolean admin);

    IPage<ProductView> products(int page, int size, boolean admin, String providerType);

    ProductView saveProduct(Long id, ProductCommand command);

    Lookup lookup(Long productId, Map<String, String> fields);

    PluginSchoolPage schools(Long productId, int page, String keyword);

    QuoteView quote(Long productId, OrderForm form);

    Lookup orderOptions(String orderId);

    QuoteView quoteAction(String orderId, ActionForm form);

    QuoteView confirm(String quoteId);

    QuoteView operation(String quoteId);

    QuoteView adminOperation(String quoteId);

    IPage<OrderView> orders(int page, int size, boolean admin);

    IPage<OrderView> orders(int page, int size, boolean admin, ServiceOrderFilter filter);

    OrderView order(String id);

    OrderView sync(String id);

    List<EventView> events(String id);

    RunLogPage logs(String id, int page);

    OrderText scoreInfo(String id);

    OrderAuditView audit(String orderId);

    QuoteView quoteRefundSettlement(String orderId, RefundSettlementForm form);

    QuoteView confirmRefundSettlement(String operationId);

    QuoteView resolve(String operationId, ResolveForm form);
}
