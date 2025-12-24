package com.ella.backend.services.invoices.parsers;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Component;

@Component
public class InvoiceParserFactory {

    private final List<InvoiceParserStrategy> parsers;

    public InvoiceParserFactory() {
        this.parsers = List.of(
                new MercadoPagoInvoiceParser(),
                new BradescoInvoiceParser(),
                new ItauInvoiceParser(),
                new C6InvoiceParser()
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
}
