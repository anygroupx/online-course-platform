package com.course.platform.infra.integration;

import com.course.platform.application.service.integration.PluginReadOnlyConnector;
import org.springframework.stereotype.Component;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class PluginConnectorRegistry {
    private final Map<String, PluginReadOnlyConnector> connectors;

    public PluginConnectorRegistry(List<PluginReadOnlyConnector> candidates) {
        Map<String, PluginReadOnlyConnector> values = new LinkedHashMap<>();
        for (PluginReadOnlyConnector connector : candidates) {
            String type = connector.getProviderType();
            if (type == null || type.isBlank() || values.putIfAbsent(type, connector) != null) {
                throw new IllegalStateException("Read-only connector types must be nonblank and unique");
            }
        }
        connectors = Map.copyOf(values);
    }

    public PluginReadOnlyConnector getConnector(String type) {
        return type == null ? null : connectors.get(type);
    }
}
