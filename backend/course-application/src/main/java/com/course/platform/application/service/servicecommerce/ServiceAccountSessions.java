package com.course.platform.application.service.servicecommerce;

import com.course.platform.domain.entity.ApiProvider;
import com.course.platform.domain.servicecommerce.ServiceAccountTypes.*;
import com.course.platform.domain.servicecommerce.ServiceCommerceTypes.OrderForm;
import com.course.platform.domain.servicecommerce.ServiceProduct;

public interface ServiceAccountSessions {
    boolean faceCollectionConfigured();

    SessionView start(Long productId, StartForm form);

    SessionView sendCode(String id);

    SessionView verify(String id, VerifyForm form);

    SessionView get(String id);

    SessionView refreshRules(String id);

    SessionView collectFace(String id, FaceConsentForm form);

    SessionView checkFace(String id);

    FaceLaunchTicket issueFaceLaunch(String id);

    java.net.URI consumeFaceLaunch(String id, String ticket);

    void revoke(String id);

    AccountPreparation prepare(ServiceProduct product, ApiProvider provider, OrderForm form);

    void consume(
            String id, Long version, ServiceProduct product, ApiProvider provider, Long userId);
}
