package com.ella.backend.email.provider;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.RestClient;

@Component
public class ResendEmailProvider implements EmailProviderClient {

    private static final Logger log = LoggerFactory.getLogger(ResendEmailProvider.class);

    private final RestClient restClient;

    @Value("${email.resend.api-key:}")
    private String apiKey;

    public ResendEmailProvider() {
        this.restClient = RestClient.builder()
                .baseUrl("https://api.resend.com")
                .build();
    }

    @Override
    public void sendHtml(String to, String from, String subject, String html) {
        if (apiKey == null || apiKey.isBlank()) {
            log.warn("Resend email provider not configured (missing email.resend.api-key / RESEND_API_KEY). Skipping email send to={}", to);
            return;
        }

        try {
            restClient.post()
                    .uri("/emails")
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("Authorization", "Bearer " + apiKey)
                    .body(Map.of(
                            "from", from,
                            "to", new String[]{to},
                            "subject", subject,
                            "html", html
                    ))
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientResponseException e) {
            String body = e.getResponseBodyAsString();
            if (body != null && body.length() > 500) {
                body = body.substring(0, 500) + "...";
            }
            log.warn("Resend API call failed. status={}, to={}, from={}, subject={}, body={}",
                    e.getRawStatusCode(),
                    to,
                    from,
                    subject,
                    body,
                    e);
            throw e;
        }
    }
}
