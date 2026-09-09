package com.course.platform.infra.integration;

import com.course.platform.application.service.integration.PluginReadOnlyConnector;
import com.course.platform.infra.external.ApiHttpClient;
import com.course.platform.infra.http.ProviderUrlNormalizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class PluginConnectorConfiguration {
    @Bean
    public PluginReadOnlyConnector flashReadOnlyConnector(ApiHttpClient http, ProviderUrlNormalizer normalizer) {
        return new PhpTemplateReadOnlyConnector(PhpTemplateReadOnlyConnector.Protocol.FLASH, http, normalizer);
    }
    @Bean
    public PluginReadOnlyConnector heishaReadOnlyConnector(ApiHttpClient http, ProviderUrlNormalizer normalizer) {
        return new PhpTemplateReadOnlyConnector(PhpTemplateReadOnlyConnector.Protocol.HEISHA, http, normalizer);
    }
    @Bean
    public PluginReadOnlyConnector jiguangReadOnlyConnector(ApiHttpClient http, ProviderUrlNormalizer normalizer) {
        return new PhpTemplateReadOnlyConnector(PhpTemplateReadOnlyConnector.Protocol.JIGUANG, http, normalizer);
    }
}
