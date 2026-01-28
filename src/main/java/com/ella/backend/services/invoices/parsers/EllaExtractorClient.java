package com.ella.backend.services.invoices.parsers;

import java.time.Duration;
import java.util.List;

import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Small isolated client for the local FastAPI service (ella-extractor).
 */
public class EllaExtractorClient {

    private static final Logger log = LoggerFactory.getLogger(EllaExtractorClient.class);

    private static final Duration TIMEOUT = Duration.ofSeconds(10);

    private static final String DEFAULT_BASE_URL = "http://localhost:8000";

    private final RestTemplate restTemplate;
    private final String baseUrl;

    public EllaExtractorClient() {
        this(DEFAULT_BASE_URL, createDefaultRestTemplate());
    }

    public EllaExtractorClient(String baseUrl) {
        this(baseUrl, createDefaultRestTemplate());
    }

    EllaExtractorClient(RestTemplate restTemplate) {
        this(DEFAULT_BASE_URL, restTemplate);
    }

    EllaExtractorClient(String baseUrl, RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
        String url = (baseUrl != null && !baseUrl.isBlank()) ? baseUrl.trim() : DEFAULT_BASE_URL;
        if (url.endsWith("/")) url = url.substring(0, url.length() - 1);
        this.baseUrl = url;
    }

    public ItauPersonnaliteResponse parseItauPersonnalite(byte[] pdfBytes) {
        if (pdfBytes == null || pdfBytes.length == 0) {
            throw new IllegalArgumentException("pdfBytes is empty");
        }

        String magic = pdfBytes.length >= 4
                ? new String(new byte[] { pdfBytes[0], pdfBytes[1], pdfBytes[2], pdfBytes[3] }, java.nio.charset.StandardCharsets.US_ASCII)
                : "";
        log.info("[EllaExtractorClient] Sending PDF to ella-extractor bytes={} magic={} url={}", pdfBytes.length, magic, baseUrl);

        String url = baseUrl + "/parse/itau-personnalite";

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        ByteArrayResource resource = new ByteArrayResource(pdfBytes) {
            @Override
            public String getFilename() {
                return "invoice.pdf";
            }
        };

        HttpHeaders partHeaders = new HttpHeaders();
        partHeaders.setContentType(MediaType.APPLICATION_PDF);
        HttpEntity<ByteArrayResource> filePart = new HttpEntity<>(resource, partHeaders);
        body.add("file", filePart);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        HttpEntity<MultiValueMap<String, Object>> request = new HttpEntity<>(body, headers);

        try {
            ResponseEntity<ItauPersonnaliteResponse> response = restTemplate.postForEntity(url, request, ItauPersonnaliteResponse.class);
            ItauPersonnaliteResponse parsed = response.getBody();
            if (parsed == null) {
                throw new IllegalStateException("ella-extractor returned empty body");
            }
            return parsed;
        } catch (HttpStatusCodeException e) {
            String payload = e.getResponseBodyAsString();
            String msg = "ella-extractor error status=" + e.getStatusCode() + " body=" + (payload != null && payload.length() > 500 ? payload.substring(0, 500) : payload);
            throw new RuntimeException(msg, e);
        }
    }

    private static RestTemplate createDefaultRestTemplate() {
        SimpleClientHttpRequestFactory f = new SimpleClientHttpRequestFactory();
        f.setConnectTimeout((int) TIMEOUT.toMillis());
        f.setReadTimeout((int) TIMEOUT.toMillis());
        RestTemplate rt = new RestTemplate(f);
        return rt;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ItauPersonnaliteResponse(
            String bank,
            String dueDate,
            Double total,
            List<Tx> transactions
    ) {
        @JsonIgnoreProperties(ignoreUnknown = true)
        public record Tx(
                String date,
                String description,
                Double amount,
                String cardFinal,
                Installment installment
        ) {
        }

        @JsonIgnoreProperties(ignoreUnknown = true)
        public record Installment(Integer current, Integer total) {
        }
    }
}
