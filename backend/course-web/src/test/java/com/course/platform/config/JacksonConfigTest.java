package com.course.platform.config;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

import java.time.LocalDateTime;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class JacksonConfigTest {

    @Test
    void customizerPreservesExistingBootSettingsAndFormatsLocalDateTime() throws Exception {
        Jackson2ObjectMapperBuilder builder = new Jackson2ObjectMapperBuilder()
                .featuresToDisable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        new JacksonConfig().localDateTimeJacksonCustomizer().customize(builder);
        ObjectMapper objectMapper = builder.build();

        Sample sample = objectMapper.readValue("""
                {"name":"course","future_field":{"enabled":true}}
                """, Sample.class);

        assertFalse(objectMapper.isEnabled(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES));
        assertEquals("course", sample.name());
        assertEquals("\"2026-09-06 00:30:45\"",
                objectMapper.writeValueAsString(LocalDateTime.of(2026, 9, 6, 0, 30, 45)));
        assertFalse(Arrays.stream(JacksonConfig.class.getDeclaredMethods())
                .anyMatch(method -> method.getReturnType().equals(ObjectMapper.class)));
    }

    private record Sample(String name) { }
}
