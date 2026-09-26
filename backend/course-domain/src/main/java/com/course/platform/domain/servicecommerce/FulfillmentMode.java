package com.course.platform.domain.servicecommerce;

import com.course.platform.common.exception.BusinessException;

/** Immutable fulfillment routing copied from a product to every created order. */
public enum FulfillmentMode {
    UPSTREAM,
    SELF_OPERATED;

    public static String normalize(String value) {
        if (value == null || value.isBlank()) return UPSTREAM.name();
        try {
            return valueOf(value).name();
        } catch (IllegalArgumentException ex) {
            throw new BusinessException("履约方式不正确");
        }
    }

    public static boolean selfOperated(ServiceProduct product) {
        return product != null && SELF_OPERATED.name().equals(product.getFulfillmentMode());
    }

    public static boolean selfOperated(ServiceOrder order) {
        return order != null && SELF_OPERATED.name().equals(order.getFulfillmentMode());
    }
}
