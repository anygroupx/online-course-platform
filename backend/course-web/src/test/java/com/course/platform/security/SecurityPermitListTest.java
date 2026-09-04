package com.course.platform.security;

import com.course.platform.config.SecurityConfig;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SecurityPermitListTest {
    @Test
    void authPermitListIsExplicitAndDoesNotExposeAuthenticatedEndpoints() throws Exception {
        Field field = SecurityConfig.class.getDeclaredField("PERMIT_ALL_PATHS");
        field.setAccessible(true);
        String[] paths = (String[]) field.get(null);
        assertFalse(Arrays.asList(paths).contains("/auth/**"));
        assertFalse(Arrays.asList(paths).contains("/auth/current"));
        assertFalse(Arrays.asList(paths).contains("/auth/logout"));
        assertTrue(Arrays.asList(paths).contains("/auth/login"));
        assertTrue(Arrays.asList(paths).contains("/auth/refresh"));
    }
}
