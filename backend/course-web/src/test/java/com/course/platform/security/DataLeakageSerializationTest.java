package com.course.platform.security;

import com.course.platform.domain.dto.OrderProgressResult;
import com.course.platform.domain.entity.ApiProvider;
import com.course.platform.domain.entity.CourseOrder;
import com.course.platform.domain.entity.PaymentConfig;
import com.course.platform.domain.entity.RefreshToken;
import com.course.platform.domain.entity.User;
import com.course.platform.domain.vo.UserInfoResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;

class DataLeakageSerializationTest {
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();

    @Test
    void publicResponsesAndEntitiesDoNotSerializeSecrets() throws Exception {
        User user = new User();
        user.setPassword("user-password-secret");
        user.setApiKey("api-key-secret");
        user.setApiKeyHash("api-key-hash");
        user.setMfaSecret("mfa-secret");
        assertNoSecrets(mapper.writeValueAsString(user));

        ApiProvider provider = new ApiProvider();
        provider.setPassword("provider-password-secret");
        provider.setToken("provider-token-secret");
        provider.setApiKey("provider-key-secret");
        provider.setCookie("provider-cookie-secret");
        assertNoSecrets(mapper.writeValueAsString(provider));

        PaymentConfig config = new PaymentConfig();
        config.setPrivateKey("payment-private-key-secret");
        config.setAlipayPublicKey("payment-public-key");
        assertNoSecrets(mapper.writeValueAsString(config));

        RefreshToken refreshToken = RefreshToken.builder()
                .token("refresh-token-secret").tokenHash("token-hash")
                .tokenFamilyId("family-secret").replacedBy("replacement-secret").build();
        assertNoSecrets(mapper.writeValueAsString(refreshToken));

        CourseOrder order = new CourseOrder();
        order.setStudentPassword("student-password-secret");
        assertNoSecrets(mapper.writeValueAsString(SensitiveDataMasker.toOrderVO(order)));

        OrderProgressResult progress = OrderProgressResult.builder()
                .studentPassword("progress-password-secret").build();
        assertNoSecrets(mapper.writeValueAsString(progress));

        UserInfoResponse info = UserInfoResponse.builder()
                .apiEnabled(true).apiKeyPrefix("abcd1234").build();
        assertNoSecrets(mapper.writeValueAsString(info));
    }

    private void assertNoSecrets(String json) {
        for (String value : new String[]{"user-password-secret", "api-key-secret", "api-key-hash",
                "mfa-secret", "provider-password-secret", "provider-token-secret", "provider-key-secret",
                "provider-cookie-secret", "payment-private-key-secret", "payment-public-key", "refresh-token-secret",
                "token-hash", "family-secret", "replacement-secret", "student-password-secret",
                "progress-password-secret"}) {
            assertFalse(json.contains(value), () -> "serialized secret: " + value + " in " + json);
        }
        assertFalse(json.contains("studentPassword"));
        assertFalse(json.contains("privateKey"));
        assertFalse(json.contains("refreshToken"));
        assertFalse(json.contains("cookie"));
    }
}
