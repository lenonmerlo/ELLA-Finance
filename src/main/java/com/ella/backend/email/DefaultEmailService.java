package com.ella.backend.email;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.ella.backend.email.provider.EmailProviderClient;
import com.ella.backend.email.template.TemplateRenderer;

@Service
public class DefaultEmailService implements EmailService {

    private static final Logger log = LoggerFactory.getLogger(DefaultEmailService.class);

    private final EmailProviderClient providerClient;
    private final TemplateRenderer templateRenderer;

    @Value("${email.enabled:true}")
    private boolean enabled;

    @Value("${email.from}")
    private String from;

    @Value("${email.fail-fast:false}")
    private boolean failFast;

    public DefaultEmailService(EmailProviderClient providerClient, TemplateRenderer templateRenderer) {
        this.providerClient = providerClient;
        this.templateRenderer = templateRenderer;
    }

    @Override
    public void send(EmailMessage message) {
        if (!enabled) {
            log.debug("Email disabled. Skipping send to={}, subject={}, template={}",
                    message.getTo(), message.getSubject(), message.getTemplateName());
            return;
        }

        if (from == null || from.isBlank()) {
            log.warn("Email enabled but email.from is blank. Skipping send to={}, subject={}, template={}",
                    message.getTo(), message.getSubject(), message.getTemplateName());
            return;
        }

        try {
            String html = templateRenderer.render(message.getTemplateName(), message.getVariables());
            providerClient.sendHtml(message.getTo(), from, message.getSubject(), html);
        } catch (Exception e) {
            log.warn("Falha ao enviar email (provider). to={}, subject={}, template={}",
                    message.getTo(), message.getSubject(), message.getTemplateName(), e);
            if (failFast) {
                throw e;
            }
        }
    }
}
