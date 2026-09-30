package com.course.platform.service.impl;

import static org.junit.jupiter.api.Assertions.*;

import com.course.platform.common.exception.BusinessException;
import com.course.platform.domain.servicecommerce.ServiceCommerceTypes.OrderForm;
import com.course.platform.domain.servicecommerce.ServiceProduct;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class SelfOperatedCheckoutPreparerTest {
    private final SelfOperatedCheckoutPreparer preparer = new SelfOperatedCheckoutPreparer();

    private ServiceProduct product() {
        var product = new ServiceProduct();
        product.setProviderType("heisha");
        product.setProject("default");
        product.setRemoteProductId("1");
        product.setFulfillmentMode("SELF_OPERATED");
        return product;
    }

    private Map<String, String> fields() {
        return new HashMap<>(Map.of("phone", "13800138000", "password", "  exact-password  ",
                "run_time", "08:00", "note", "请在上午处理"));
    }

    private OrderForm form(Map<String, String> fields) {
        return new OrderForm(10, new BigDecimal("2"), fields, List.of(), true);
    }

    @Test void preservesPasswordAndBusinessIntentWithoutSynthesizingRemoteOptions() {
        var prepared = preparer.prepare(product(), form(fields()));
        assertEquals("  exact-password  ", prepared.fields().get("password"));
        assertEquals("请在上午处理", prepared.fields().get("note"));
        assertEquals("13***00", prepared.accountLabel());
        assertEquals(new BigDecimal("2"), prepared.billablePerUnit());
        assertFalse(prepared.toString().contains("exact-password"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"faceToken", "runPreflightToken", "planOptionId", "fenceOptionId", "imageData", "checkedAt", "orderNo"})
    void rejectsRemoteCredentialsAndFaceBytesInOrdinaryFields(String field) {
        var input = fields();
        input.put(field, "synthetic-secret");
        var failure = assertThrows(BusinessException.class, () -> preparer.prepare(product(), form(input)));
        assertFalse(failure.getMessage().contains("synthetic-secret"));
    }

    @Test void rejectsAccountAndCredentialsInNotes() {
        for (String secret : List.of("13800138000", "  exact-password  ", "faceToken=private")) {
            var input = fields();
            input.put("note", "资料：" + secret + " 请核对");
            assertThrows(BusinessException.class, () -> preparer.prepare(product(), form(input)));
        }
    }
}
