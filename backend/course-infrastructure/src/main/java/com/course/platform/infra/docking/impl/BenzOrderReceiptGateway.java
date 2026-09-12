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
    public java.util.List<Verified> findCandidates(ApiProvider provider, CoursePlatform platform, CourseOrder order) {
        return BenzReceiptMatcher.candidates(read(provider, order), order, platform).stream().map(Verified::new).toList();
    }

    @Override
    public Verified verify(ApiProvider provider, CoursePlatform platform, CourseOrder order, String receiptId) {
        BenzReceiptMatcher.select(read(provider, order), order, platform, receiptId, true);
        return new Verified(receiptId);
    }

    private String read(ApiProvider provider, CourseOrder order) {
        if (!"27".equals(provider.getProviderType()))
            throw new ProviderRequestException(ProviderRequestException.Reason.UNSUPPORTED_OPERATION);
        return http.postForString(provider, provider.getApiUrl() + "/api.php?act=chadan",
                BenzReceiptMatcher.query(order, provider.getUsername(), provider.getApiKey()));
    }
}
