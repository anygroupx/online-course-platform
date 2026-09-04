package com.course.platform.common.util;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class PublicUidUtilTest {

    @Test
    void generateShouldReturnUniqueUuidV4Values() {
        Set<String> values = new HashSet<>();
        for (int i = 0; i < 1000; i++) {
            String uid = PublicUidUtil.generate();
            assertTrue(PublicUidUtil.isValid(uid));
            assertTrue(values.add(uid));
        }
    }

    @Test
    void validationShouldRejectSequentialOrNonV4Identifiers() {
        assertFalse(PublicUidUtil.isValid("1"));
        assertFalse(PublicUidUtil.isValid("550e8400-e29b-11d4-a716-446655440000"));
        assertFalse(PublicUidUtil.isValid(null));
        assertEquals("550e8400-e29b-41d4-a716-446655440000",
                PublicUidUtil.normalize(" 550E8400-E29B-41D4-A716-446655440000 "));
    }
}
