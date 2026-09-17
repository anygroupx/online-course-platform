package com.course.platform.domain.vo.plugin;

import java.util.List;

/** Reviewed evidence and delivered capabilities are deliberately separate. */
public record PluginIntegrationDescriptor(
        String id, String archive, String name, String category, String evidenceLevel,
        String integrationStatus, String providerType, String duplicateOf,
        List<String> observedFeatures, List<String> blockers, List<String> evidence,
        List<String> availableCapabilities, List<String> serviceCapabilities, List<PluginProjectOption> projects) {
    public PluginIntegrationDescriptor {
        observedFeatures = List.copyOf(observedFeatures);
        blockers = List.copyOf(blockers);
        evidence = List.copyOf(evidence);
        availableCapabilities = List.copyOf(availableCapabilities);
        serviceCapabilities = List.copyOf(serviceCapabilities);
        projects = List.copyOf(projects);
    }
}
