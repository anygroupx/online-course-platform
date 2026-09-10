package com.course.platform.infra.docking.impl;

import com.course.platform.application.service.orderreceipt.OrderReceiptGateway;
import com.course.platform.domain.entity.*;
import com.course.platform.domain.exception.ProviderRequestException;
import com.course.platform.domain.orderreceipt.OrderReceiptTypes.Verified;
import com.course.platform.infra.external.ApiHttpClient;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class BenzOrderReceiptGateway implements OrderReceiptGateway {
    private final ApiHttpClient http;

    @Override
    public Verified verify(ApiProvider provider, CoursePlatform platform, CourseOrder order, String receiptId) {
        if (!"27".equals(provider.getProviderType()))
            throw new ProviderRequestException(ProviderRequestException.Reason.UNSUPPORTED_OPERATION);
        String response = http.postForString(provider, provider.getApiUrl() + "/api.php?act=chadan",
                BenzReceiptMatcher.query(order, provider.getUsername(), provider.getApiKey()));
        BenzReceiptMatcher.select(response, order, platform, receiptId, true);
        return new Verified(receiptId);
    }
}
