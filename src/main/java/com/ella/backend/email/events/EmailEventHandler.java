package com.ella.backend.email.events;

import com.ella.backend.email.EmailMessage;
import com.ella.backend.email.EmailService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class EmailEventHandler {

    private final EmailService emailService;

    @Value("${app.frontend.login-url:https://ella.com/login}")
    private String loginUrl;

    @Value("${app.frontend.privacy-url:https://ella.com/privacidade}")
    private String privacyUrl;

    @Value("${lgpd.contract.version:2025-12-12-v1}")
    private String contractVersion;

    public EmailEventHandler(EmailService emailService) {
        this.emailService = emailService;
    }

    @Async
    @EventListener
    public void onUserRegistered(UserRegisteredEvent event) {
        emailService.send(EmailMessage.builder()
                .to(event.email())
                .subject("Bem-vindo(a) à ELLA Finanças")
                .templateName("welcome")
                .variables(Map.of(
                        "name", event.name(),
                        "loginUrl", loginUrl
                ))
                .build());
    }

    @Async
    @EventListener
    public void onLgpdConsentRequested(LgpdConsentEmailRequestedEvent event) {
        emailService.send(EmailMessage.builder()
                .to(event.email())
                .subject("Confirmação de Tratamento de Dados (LGPD) — ELLA Finanças")
                .templateName("lgpd-consent")
                .variables(Map.of(
                        "name", event.name(),
                        "privacyUrl", privacyUrl,
                        "contractVersion", contractVersion
                ))
                .build());
    }
}
