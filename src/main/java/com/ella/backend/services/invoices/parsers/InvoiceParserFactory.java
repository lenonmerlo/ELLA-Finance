package com.ella.backend.services.invoices.parsers;

import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class InvoiceParserFactory {

    private static final Logger log = LoggerFactory.getLogger(InvoiceParserFactory.class);

    private final List<InvoiceParserStrategy> parsers;

    public InvoiceParserFactory(@Value("${ella.extractor.base-url:http://localhost:8000}") String ellaExtractorBaseUrl) {
        log.info("[InvoiceParserFactory] Using ella.extractor.base-url={}", ellaExtractorBaseUrl);
        EllaExtractorClient extractorClient = new EllaExtractorClient(ellaExtractorBaseUrl);
        this.parsers = List.of(
                // More specific parsers first
            new ItauPersonaliteInvoiceParser(extractorClient),
                new ItauInvoiceParser(),
                new BradescoInvoiceParser(),
                new BancoDoBrasilInvoiceParser(),
            new SicrediExtractorParser(extractorClient),
                new MercadoPagoInvoiceParser(),
                new NubankInvoiceParser(),
                new C6InvoiceParser(),
                // Santander has broader applicability signals; keep it last to avoid stealing other banks.
                new SantanderInvoiceParser()
        );
    }

    public Optional<InvoiceParserStrategy> getParser(String text) {
        if (text == null || text.isBlank()) return Optional.empty();
        for (InvoiceParserStrategy parser : parsers) {
            try {
                if (parser.isApplicable(text)) {
                    return Optional.of(parser);
                }
            } catch (Exception ignored) {
            }
        }
        return Optional.empty();
    }

    public List<InvoiceParserStrategy> getParsers() {
        return parsers;
    }
}
