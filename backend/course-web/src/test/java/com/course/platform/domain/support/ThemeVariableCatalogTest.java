package com.course.platform.domain.support;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ThemeVariableCatalogTest {

    @Test
    void liquidGlassThemeIncludesColorAndMaterialDefinitions() {
        assertEquals(
                "#3463ce",
                ThemeVariableCatalog.getDefinition("brand_primary")
                        .valueForType(ThemeVariableCatalog.LIQUID_GLASS_TYPE)
        );
        assertTrue(ThemeVariableCatalog.isKnownKey(
                ThemeVariableCatalog.LIQUID_GLASS_TYPE,
                "glass_refraction"
        ));
        assertFalse(ThemeVariableCatalog.isKnownKey(
                ThemeVariableCatalog.LIGHT_TYPE,
                "glass_refraction"
        ));
    }

    @Test
    void materialValuesMustMatchTheAllowlistAndSafetyRanges() {
        assertTrue(ThemeVariableCatalog.isSupportedValue(
                ThemeVariableCatalog.LIQUID_GLASS_TYPE,
                "glass_renderer_mode",
                "auto"
        ));
        assertFalse(ThemeVariableCatalog.isSupportedValue(
                ThemeVariableCatalog.LIQUID_GLASS_TYPE,
                "glass_renderer_mode",
                "webgl"
        ));
        assertTrue(ThemeVariableCatalog.isSupportedValue(
                ThemeVariableCatalog.LIQUID_GLASS_TYPE,
                "glass_refraction",
                "100"
        ));
        assertFalse(ThemeVariableCatalog.isSupportedValue(
                ThemeVariableCatalog.LIQUID_GLASS_TYPE,
                "glass_refraction",
                "100.1"
        ));
        assertTrue(ThemeVariableCatalog.isSupportedValue(
                ThemeVariableCatalog.LIQUID_GLASS_TYPE,
                "glass_tint",
                "rgba(255,255,255,0.02)"
        ));
        assertFalse(ThemeVariableCatalog.isSupportedValue(
                ThemeVariableCatalog.LIQUID_GLASS_TYPE,
                "glass_tint",
                "url(javascript:alert(1))"
        ));
    }
}
