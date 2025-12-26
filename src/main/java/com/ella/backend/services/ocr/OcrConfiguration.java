package com.ella.backend.services.ocr;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import lombok.extern.slf4j.Slf4j;

@Configuration
@EnableConfigurationProperties(OcrProperties.class)
@Slf4j
public class OcrConfiguration {

    @Bean
    @ConditionalOnProperty(prefix = "ella.ocr", name = "enabled", havingValue = "true")
    public OcrService tesseractOcrService(OcrProperties ocrProperties) {
        log.info("[OCR] Enabled: language='{}' tessdataPath='{}' renderDpi={} maxPages={} minTextLen={}",
                safe(ocrProperties.getLanguage()),
                safe(ocrProperties.getTessdataPath()),
                ocrProperties.getPdf().getRenderDpi(),
                ocrProperties.getPdf().getMaxPages(),
                ocrProperties.getPdf().getMinTextLength());
        return new TesseractOcrService(ocrProperties);
    }

    @Bean
    @ConditionalOnMissingBean(OcrService.class)
    public OcrService disabledOcrService() {
        log.info("[OCR] Disabled (ella.ocr.enabled=false)");
        return new DisabledOcrService();
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }
}
