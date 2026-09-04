package com.course.platform.domain.entity;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class ApiProviderJsonTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    @DisplayName("敏感字段应允许管理端写入但禁止出现在响应 JSON")
    void secrets_shouldBeWriteOnly() throws Exception {
        String json = """
                {
                  "name":"Daytime",
                  "password":"password-secret",
                  "token":"token-secret",
                  "apiKey":"api-key-secret",
                  "cookie":"cookie-secret"
                }
                """;

        ApiProvider provider = objectMapper.readValue(json, ApiProvider.class);

        assertEquals("password-secret", provider.getPassword());
        assertEquals("token-secret", provider.getToken());
        assertEquals("api-key-secret", provider.getApiKey());
        assertEquals("cookie-secret", provider.getCookie());

        String serialized = objectMapper.writeValueAsString(provider);
        assertFalse(serialized.contains("password-secret"));
        assertFalse(serialized.contains("token-secret"));
        assertFalse(serialized.contains("api-key-secret"));
        assertFalse(serialized.contains("cookie-secret"));
        assertFalse(serialized.contains("\"apiKey\""));
    }
}
