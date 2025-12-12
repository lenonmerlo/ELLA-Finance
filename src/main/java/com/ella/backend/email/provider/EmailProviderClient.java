package com.ella.backend.email.provider;

public interface EmailProviderClient {
    void sendHtml(String to, String from, String subject, String html);
}
