package com.ella.backend.config;

import java.time.Duration;

import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

/**
 * Configuração do RestTemplate para chamadas HTTP.
 */
@Configuration
public class RestTemplateConfig {

    @Bean
    public RestTemplate restTemplate(RestTemplateBuilder builder, AdobeOAuthProperties adobeOAuthProperties) {
        int timeoutSeconds = adobeOAuthProperties != null ? adobeOAuthProperties.getTimeout() : 60;
        if (timeoutSeconds <= 0) timeoutSeconds = 60;

        Duration timeout = Duration.ofSeconds(timeoutSeconds);

        return builder
                .setConnectTimeout(timeout)
                .setReadTimeout(timeout)
                .build();
    }
}
