package com.ella.backend.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

@Component
public class EmailStartupValidation implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(EmailStartupValidation.class);

    private final Environment environment;

    public EmailStartupValidation(Environment environment) {
        this.environment = environment;
    }

    @Value("${email.enabled:true}")
    private boolean emailEnabled;

    @Value("${email.require-config-on-startup:false}")
    private boolean requireConfigOnStartup;

    @Value("${email.from:}")
    private String emailFrom;

    @Value("${email.resend.api-key:}")
    private String resendApiKey;

    @Value("${app.frontend.base-url:}")
    private String frontendBaseUrl;

    @Override
    public void run(ApplicationArguments args) {
        String[] profiles = environment.getActiveProfiles();
        log.info("Active profiles: {}", (profiles == null || profiles.length == 0) ? "(default)" : String.join(",", profiles));

        if (!emailEnabled) {
            log.info("Email disabled (email.enabled=false). No emails will be sent.");
            return;
        }

        boolean fromConfigured = emailFrom != null && !emailFrom.isBlank();
        boolean resendConfigured = resendApiKey != null && !resendApiKey.isBlank();
        boolean frontendConfigured = frontendBaseUrl != null && !frontendBaseUrl.isBlank();

        if (!fromConfigured || !resendConfigured || !frontendConfigured) {
            log.warn(
                "Email enabled but configuration is incomplete: fromConfigured={}, resendApiKeyConfigured={}, frontendBaseUrlConfigured={}, requireConfigOnStartup={}",
                    fromConfigured,
                    resendConfigured,
                frontendConfigured,
                    requireConfigOnStartup
            );
        }

        if (!requireConfigOnStartup) {
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

        if (frontendBaseUrl == null || frontendBaseUrl.isBlank()) {
            throw new IllegalStateException(
                    "Email is enabled but app.frontend.base-url is blank. Set APP_FRONTEND_BASE_URL (ex: https://seu-app.vercel.app) or set EMAIL_REQUIRE_CONFIG_ON_STARTUP=false."
            );
        }
    }
}