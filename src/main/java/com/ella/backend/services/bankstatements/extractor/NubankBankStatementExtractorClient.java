package com.ella.backend.services.bankstatements.extractor;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;

import com.ella.backend.entities.BankStatementTransaction;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@Component
public class NubankBankStatementExtractorClient {

    private static final Logger log = LoggerFactory.getLogger(NubankBankStatementExtractorClient.class);

    // Statements can be heavier to extract than invoices.
    private static final Duration TIMEOUT = Duration.ofSeconds(30);

    private final RestTemplate restTemplate;
    private final String baseUrl;

    @Autowired
    public NubankBankStatementExtractorClient(
            @Value("${ella.extractor.base-url:http://localhost:8000}") String baseUrl
    ) {
        this(baseUrl, createDefaultRestTemplate());
    }

    NubankBankStatementExtractorClient(String baseUrl, RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
        String url = (baseUrl != null && !baseUrl.isBlank()) ? baseUrl.trim() : "http://localhost:8000";
        if (url.endsWith("/")) url = url.substring(0, url.length() - 1);
        this.baseUrl = url;
        log.info("[NubankBankStatementExtractorClient] Configured baseUrl={}", this.baseUrl);
    }

    public NubankBankStatementResponse parseNubankBankStatement(byte[] pdfBytes) {
        if (pdfBytes == null || pdfBytes.length == 0) {
            throw new IllegalArgumentException("pdfBytes is empty");
        }

        String url = baseUrl + "/parse/nubank-bank-statement";

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        ByteArrayResource resource = new ByteArrayResource(pdfBytes) {
            @Override
            public String getFilename() {
                return "statement.pdf";
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
            ResponseEntity<NubankBankStatementResponse> response = restTemplate.postForEntity(url, request, NubankBankStatementResponse.class);
            NubankBankStatementResponse parsed = response.getBody();
            if (parsed == null) {
                throw new IllegalStateException("ella-extractor returned empty body");
            }
            return parsed;
        } catch (HttpStatusCodeException e) {
            String payload = e.getResponseBodyAsString();
            String msg = "ella-extractor error (Nubank statement) status=" + e.getStatusCode()
                    + " body=" + (payload != null && payload.length() > 500 ? payload.substring(0, 500) : payload);
            throw new RuntimeException(msg, e);
        }
    }

    private static RestTemplate createDefaultRestTemplate() {
        SimpleClientHttpRequestFactory f = new SimpleClientHttpRequestFactory();
        f.setConnectTimeout((int) TIMEOUT.toMillis());
        f.setReadTimeout((int) TIMEOUT.toMillis());
        return new RestTemplate(f);
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record NubankBankStatementResponse(
            String bank,
            String statementDate,
            Double openingBalance,
            Double closingBalance,
            List<Tx> transactions,
            String reason
    ) {
        public LocalDate statementDateAsLocalDate() {
            return statementDate == null || statementDate.isBlank() ? null : LocalDate.parse(statementDate);
        }

        public BigDecimal openingBalanceAsBigDecimal() {
            return openingBalance == null ? null : BigDecimal.valueOf(openingBalance);
        }

        public BigDecimal closingBalanceAsBigDecimal() {
            return closingBalance == null ? null : BigDecimal.valueOf(closingBalance);
        }

        @JsonIgnoreProperties(ignoreUnknown = true)
        public record Tx(
                String transactionDate,
                String description,
                Double amount,
                Double balance,
                String type
        ) {
            public LocalDate transactionDateAsLocalDate() {
                return transactionDate == null || transactionDate.isBlank() ? null : LocalDate.parse(transactionDate);
            }

            public BigDecimal amountAsBigDecimal() {
                return amount == null ? null : BigDecimal.valueOf(amount);
            }

            public BigDecimal balanceAsBigDecimal() {
                return balance == null ? null : BigDecimal.valueOf(balance);
            }

            public BankStatementTransaction.Type typeAsEnumOrNull() {
                if (type == null || type.isBlank()) return null;
                try {
                    return BankStatementTransaction.Type.valueOf(type.trim().toUpperCase(java.util.Locale.ROOT));
                } catch (Exception ignored) {
                    return null;
                }
            }
        }
    }
}
