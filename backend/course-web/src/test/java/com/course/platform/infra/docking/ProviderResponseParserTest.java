package com.course.platform.infra.docking;

import com.course.platform.domain.exception.ProviderRequestException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

class ProviderResponseParserTest {
    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"<html>password=secret</html>", "[]", "{", "{}", "{\"code\":\"secret\"}",
            "{\"code\":1.5}", "{\"code\":1,\"data\":unclosed"})
    void malformedOrNonProtocolResponsesAreClassifiedWithoutEchoingThem(String value) {
        var error = assertThrows(ProviderRequestException.class, () -> ProviderResponseParser.parseObject(value));
        assertEquals(ProviderRequestException.Reason.INVALID_RESPONSE, error.getReason());
        assertNull(error.getCause());
        assertFalse(error.getMessage().contains("secret"));
    }

    @Test
    void emptyButWellFormedCataloguesAreValid() {
        var json = ProviderResponseParser.parseObject("{\"code\":\"1\",\"data\":[]}");
        assertTrue(ProviderResponseParser.requireArray(json, "data").isEmpty());
    }

    @Test
    void missingOrWrongShapeCatalogueCannotPassAsASuccessfulProbe() {
        for (String value : new String[]{"{\"code\":1}", "{\"code\":1,\"data\":\"secret\"}"}) {
            var json = ProviderResponseParser.parseObject(value);
            assertThrows(ProviderRequestException.class, () -> ProviderResponseParser.requireArray(json, "data"));
        }
    }
}
