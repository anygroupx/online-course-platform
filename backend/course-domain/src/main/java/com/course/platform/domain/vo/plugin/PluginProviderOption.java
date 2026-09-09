package com.course.platform.domain.vo.plugin;

/** No URLs, usernames, balances or credentials in the integration selector. */
public record PluginProviderOption(Long id, String name, String providerType, Integer status, boolean verified) {}
