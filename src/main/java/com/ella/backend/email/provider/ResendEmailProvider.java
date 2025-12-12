package com.ella.backend.email.provider;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Map;

@Component
public class ResendEmailProvider implements EmailProviderClient {

    private final RestClient restClient;

    @Value("${email.resend.api-key}")
    private String apiKey;

    public ResendEmailProvider() {
        this.restClient = RestClient.builder()
                .baseUrl("https://api.resend.com")
                .build();
    }

    @Override
    public void sendHtml(String to, String from, String subject, String html) {
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
    }
}
