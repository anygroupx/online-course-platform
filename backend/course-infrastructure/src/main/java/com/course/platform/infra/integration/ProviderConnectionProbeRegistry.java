package com.course.platform.infra.integration;

import com.course.platform.application.service.platform.docking.ProviderConnectionProbe;
import org.springframework.stereotype.Component;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Unified verification, NOT unified order dispatch. Duplicate types fail closed at startup. */
@Component
public class ProviderConnectionProbeRegistry {
    private final Map<String, ProviderConnectionProbe> probes;

    public ProviderConnectionProbeRegistry(List<ProviderConnectionProbe> candidates) {
        Map<String, ProviderConnectionProbe> values = new LinkedHashMap<>();
        for (ProviderConnectionProbe probe : candidates) {
            String type = probe.getProviderType();
            if (type == null || type.isBlank() || values.putIfAbsent(type, probe) != null) {
                throw new IllegalStateException("Provider probe types must be nonblank and unique");
            }
        }
        probes = Map.copyOf(values);
    }

    public ProviderConnectionProbe getProbe(String type) {
        return type == null ? null : probes.get(type);
    }
}
