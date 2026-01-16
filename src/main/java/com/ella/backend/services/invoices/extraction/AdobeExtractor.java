package com.ella.backend.services.invoices.extraction;

import java.util.Base64;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import com.ella.backend.config.AdobeOAuthProperties;

import lombok.extern.slf4j.Slf4j;

/**
 * Extrator de PDF usando Adobe PDF Extract API.
 *
 * Observação: esta implementação segue o fluxo simplificado descrito no plano desta fase.
 * O parsing do JSON retornado pela Adobe foi mantido simples (retorna o JSON bruto).
 */
@Slf4j
@Component
public class AdobeExtractor {

    private final AdobeOAuthProperties adobeProperties;
    private final RestTemplate restTemplate;

    @Autowired
    public AdobeExtractor(AdobeOAuthProperties adobeProperties, ObjectProvider<RestTemplate> restTemplateProvider) {
        this.adobeProperties = adobeProperties;
        this.restTemplate = restTemplateProvider != null ? restTemplateProvider.getIfAvailable() : null;
    }

    // Construtor auxiliar para testes unitários (sem Spring).
    AdobeExtractor(AdobeOAuthProperties adobeProperties, RestTemplate restTemplate) {
        this.adobeProperties = adobeProperties;
        this.restTemplate = restTemplate;
    }

    /**
     * Extrai texto de um PDF usando Adobe API.
     *
     * @param pdfBytes Bytes do PDF
     * @return Texto extraído (ou null se falhar)
     */
    public String extract(byte[] pdfBytes) {
        log.debug("[AdobeExtractor] Starting Adobe extraction");

        if (pdfBytes == null || pdfBytes.length == 0) {
            log.debug("[AdobeExtractor] Empty PDF bytes");
            return null;
        }

        // VALIDAÇÃO 1: Adobe está habilitado?
        if (adobeProperties == null || !adobeProperties.isEnabled()) {
            log.debug("[AdobeExtractor] Adobe Extractor is disabled");
            return null;
        }

        // VALIDAÇÃO 2: Credenciais estão configuradas?
        if (!adobeProperties.isConfigured()) {
            log.debug("[AdobeExtractor] Adobe credentials not configured");
            return null;
        }

        // VALIDAÇÃO 3: RestTemplate está disponível?
        if (restTemplate == null) {
            log.warn("[AdobeExtractor] RestTemplate not available");
            return null;
        }

        try {
            HttpHeaders headers = prepareHeaders();
            String pdfBase64 = Base64.getEncoder().encodeToString(pdfBytes);
            String body = prepareBody(pdfBase64);

            String result = callAdobeApi(headers, body);
            if (result == null || result.isEmpty()) {
                log.warn("[AdobeExtractor] Adobe API returned empty result");
                return null;
            }

            log.info("[AdobeExtractor] Adobe extraction successful: {} chars", result.length());
            return result;
        } catch (Exception e) {
            log.warn("[AdobeExtractor] Error during Adobe extraction", e);
            return null;
        }
    }

    private HttpHeaders prepareHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        headers.set("Authorization", "Bearer " + adobeProperties.getAccessToken());
        headers.set("x-api-key", adobeProperties.getClientId());

        return headers;
    }

    private String prepareBody(String pdfBase64) {
        return String.format("{\"assetID\":\"%s\"}", pdfBase64);
    }

    private String callAdobeApi(HttpHeaders headers, String body) {
        try {
            String endpoint = adobeProperties.getApiEndpoint();
            if (endpoint == null) endpoint = "";
            String url = endpoint.endsWith("/") ? (endpoint + "extract") : (endpoint + "/extract");

            HttpEntity<String> request = new HttpEntity<>(body, headers);
            log.debug("[AdobeExtractor] Calling Adobe at: {}", url);

            String response = restTemplate.postForObject(url, request, String.class);
            if (response == null) {
                log.warn("[AdobeExtractor] Adobe API returned null");
                return null;
            }

            return parseAdobeResponse(response);
        } catch (RestClientException e) {
            log.warn("[AdobeExtractor] Error calling Adobe API: {}", e.getMessage());
            return null;
        } catch (Exception e) {
            log.warn("[AdobeExtractor] Unexpected error", e);
            return null;
        }
    }

    /**
     * Extrai texto do JSON retornado pela Adobe.
     *
     * Implementação intencionalmente simples nesta fase:
     * retorna o JSON bruto para permitir evolução na Fase 4.
     */
    private String parseAdobeResponse(String jsonResponse) {
        try {
            log.debug("[AdobeExtractor] Parsing Adobe JSON response");
            return jsonResponse;
        } catch (Exception e) {
            log.warn("[AdobeExtractor] Error parsing Adobe response", e);
            return null;
        }
    }

    /**
     * Retorna status do AdobeExtractor.
     */
    public String getStatus() {
        if (adobeProperties == null || !adobeProperties.isEnabled()) {
            return "DISABLED";
        }
        if (!adobeProperties.isConfigured()) {
            return "NOT_CONFIGURED";
        }
        return "READY";
    }
}
