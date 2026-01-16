package com.ella.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import lombok.Data;

/**
 * Carrega credenciais da Adobe do application.properties.
 *
 * Uso em application.properties:
 * adobe.oauth.client-id=${ADOBE_CLIENT_ID}
 * adobe.oauth.access-token=${ADOBE_ACCESS_TOKEN}
 * adobe.oauth.api-endpoint=https://pdf-services.adobe.io
 */
@Data
@Component
@ConfigurationProperties(prefix = "adobe.oauth")
public class AdobeOAuthProperties {

    /**
     * Client ID da Adobe (OAuth Server-to-Server).
     * Obtém de: https://console.adobe.io/
     */
    private String clientId;

    /**
     * Access Token da Adobe.
     *
     * IMPORTANTE: Expira em 24 horas.
     */
    private String accessToken;

    /**
     * Endpoint da API da Adobe.
     * Default: https://pdf-services.adobe.io
     */
    private String apiEndpoint = "https://pdf-services.adobe.io";

    /**
     * Timeout em segundos para chamadas à Adobe.
     * Default: 60 segundos.
     */
    private int timeout = 60;

    /**
     * Habilitar/desabilitar Adobe Extractor.
     * Default: true.
     */
    private boolean enabled = true;

    /**
     * Validar que as credenciais estão configuradas.
     */
    public boolean isConfigured() {
        return clientId != null && !clientId.isEmpty()
                && accessToken != null && !accessToken.isEmpty();
    }
}
