package com.ella.backend.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
public class EmailStartupValidation implements ApplicationRunner {

    @Value("${email.enabled:true}")
    private boolean emailEnabled;

    @Value("${email.require-config-on-startup:false}")
    private boolean requireConfigOnStartup;

    @Value("${email.from:}")
    private String emailFrom;

    @Value("${email.resend.api-key:}")
    private String resendApiKey;

    @Override
    public void run(ApplicationArguments args) {
        if (!emailEnabled || !requireConfigOnStartup) {
            return;
        }

        if (emailFrom == null || emailFrom.isBlank()) {
            throw new IllegalStateException(
                    "Email is enabled but email.from is blank. Set EMAIL_FROM or set EMAIL_REQUIRE_CONFIG_ON_STARTUP=false."
            );
        }

        if (resendApiKey == null || resendApiKey.isBlank()) {
            throw new IllegalStateException(
                    "Email is enabled but Resend API key is missing. Set RESEND_API_KEY or set EMAIL_REQUIRE_CONFIG_ON_STARTUP=false."
            );
        }
    }
}