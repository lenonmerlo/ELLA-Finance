package com.ella.backend.services.invoices.extraction;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.Environment;

import com.ella.backend.config.QualityScoreConfig;
import com.ella.backend.services.invoices.InvoiceParsingException;
import com.ella.backend.services.invoices.parsers.InvoiceParserFactory;
import com.ella.backend.services.invoices.quality.ParseQualityEvaluator;
import com.ella.backend.services.invoices.quality.ParseQualityValidator;
import com.ella.backend.services.ocr.OcrProperties;
import com.ella.backend.services.ocr.PdfOcrExtractor;
import com.ella.backend.services.ocr.PdfTextExtractor;

class ExtractionPipelineUnsupportedBankMessageTest {

    private static byte[] pdfWithText(String text) throws Exception {
        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage();
            doc.addPage(page);

            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                cs.beginText();
                cs.setFont(PDType1Font.HELVETICA, 12);
                cs.newLineAtOffset(50, 750);

                for (String line : text.split("\\r?\\n")) {
                    cs.showText(line);
                    cs.newLineAtOffset(0, -16);
                }

                cs.endText();
            }

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            doc.save(out);
            return out.toByteArray();
        }
    }

    private static ExtractionPipeline buildPipeline() {
        InvoiceParserFactory invoiceParserFactory = new InvoiceParserFactory("http://localhost:8000");
        PdfTextExtractor pdfTextExtractor = new PdfTextExtractor();
        PdfOcrExtractor pdfOcrExtractor = org.mockito.Mockito.mock(PdfOcrExtractor.class);

        OcrProperties ocrProperties = new OcrProperties();
        ocrProperties.setEnabled(false);

        Environment environment = org.mockito.Mockito.mock(Environment.class);
        org.mockito.Mockito.when(environment.getActiveProfiles()).thenReturn(new String[0]);
        org.mockito.Mockito.when(environment.getProperty(org.mockito.ArgumentMatchers.anyString())).thenReturn(null);

        // Evaluator/validator are irrelevant for this test because we should fail before parsing.
        ParseQualityEvaluator evaluator = org.mockito.Mockito.mock(ParseQualityEvaluator.class);
        ParseQualityValidator validator = org.mockito.Mockito.mock(ParseQualityValidator.class);

        QualityScoreConfig qualityScoreConfig = new QualityScoreConfig();
        AdobeExtractor adobeExtractor = org.mockito.Mockito.mock(AdobeExtractor.class);
        AdobeFallbackStrategy strategy = new AdobeFallbackStrategy(qualityScoreConfig);

        return new ExtractionPipeline(
                invoiceParserFactory,
                pdfTextExtractor,
                pdfOcrExtractor,
                ocrProperties,
                environment,
                evaluator,
                qualityScoreConfig,
                validator,
                adobeExtractor,
                strategy
        );
    }

    @Test
    void mercadoPagoReturnsFriendlyUnsupportedMessage() throws Exception {
        String text = String.join("\n",
                "Mercado Pago",
                "Essa é sua fatura de dezembro",
                "Total a pagar",
                "R$ 2.449,67",
                "Vence em",
                "23/12/2025",
                "Lançamentos",
                "17/12 UBER TRIP 18,40"
        );

        byte[] pdfBytes = pdfWithText(text);
        ExtractionPipeline pipeline = buildPipeline();

        InvoiceParsingException ex = assertThrows(
                InvoiceParsingException.class,
                () -> pipeline.extractFromPdf(new ByteArrayInputStream(pdfBytes), null, null)
        );

        String normalized = com.ella.backend.services.invoices.util.NormalizeUtil.normalize(ex.getMessage());
        assertTrue(normalized.contains("mercado pago"));
        assertTrue(normalized.contains("nao suport"));
    }
}
