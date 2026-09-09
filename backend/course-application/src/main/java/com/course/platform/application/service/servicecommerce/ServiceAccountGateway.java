package com.course.platform.application.service.servicecommerce;

import com.course.platform.domain.entity.ApiProvider;
import com.course.platform.domain.servicecommerce.ServiceAccountTypes.*;
import com.course.platform.domain.servicecommerce.ServiceCommerceTypes.*;
import com.course.platform.domain.servicecommerce.ServiceProduct;

public interface ServiceAccountGateway {
    void sendCode(ApiProvider provider, ServiceProduct product, String account);

    AccountSnapshot authenticate(
            ApiProvider provider,
            ServiceProduct product,
            String mode,
            String account,
            String secret,
            String schoolName);

    AccountSnapshot refreshRules(
            ApiProvider provider, ServiceProduct product, AccountSnapshot account);

    AccountSnapshot collectFace(
            ApiProvider provider, ServiceProduct product, AccountSnapshot account);

    AccountSnapshot checkFace(
            ApiProvider provider, ServiceProduct product, AccountSnapshot account);

    PreparedOrder prepareAuthenticated(
            ApiProvider provider, ServiceProduct product, OrderForm form, AccountSnapshot account);
}
