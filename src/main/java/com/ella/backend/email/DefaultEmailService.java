package com.ella.backend.email;

import com.ella.backend.email.provider.EmailProviderClient;
import com.ella.backend.email.template.TemplateRenderer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class DefaultEmailService implements EmailService {

    private final EmailProviderClient providerClient;
    private final TemplateRenderer templateRenderer;

    @Value("${email.enabled:true}")
    private boolean enabled;

    @Value("${email.from}")
    private String from;

    public DefaultEmailService(EmailProviderClient providerClient, TemplateRenderer templateRenderer) {
        this.providerClient = providerClient;
        this.templateRenderer = templateRenderer;
    }

    @Override
    public void send(EmailMessage message) {
        if (!enabled) return;

        String html = templateRenderer.render(message.getTemplateName(), message.getVariables());
        providerClient.sendHtml(message.getTo(), from, message.getSubject(), html);
    }
}
