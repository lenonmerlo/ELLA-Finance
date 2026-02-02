package com.ella.backend.services.invoices.extraction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.Environment;

import com.ella.backend.config.QualityScoreConfig;
import com.ella.backend.services.invoices.parsers.InvoiceParserFactory;
import com.ella.backend.services.invoices.parsers.ParseResult;
import com.ella.backend.services.invoices.quality.ParseQualityEvaluator;
import com.ella.backend.services.invoices.quality.ParseQualityValidator;
import com.ella.backend.services.ocr.OcrProperties;
import com.ella.backend.services.ocr.PdfOcrExtractor;
import com.ella.backend.services.ocr.PdfTextExtractor;

class ExtractionPipelineAdobeFallbackTest {

    private static final String PDFBOX_MARKER = "BASE_PDFBOX";
    private static final String ADOBE_MARKER = "BASE_ADOBE";

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

    private static String itauText(String marker) {
        return String.join("\n",
                marker,
                "Banco Itaú",
                "Itaucard",
                "Resumo da fatura",
                "Total desta fatura",
                "Pagamento mínimo",
                "Vencimento: 23/12/2025",
                "",
                "Pagamentos efetuados",
                "21/11/2025 PAGAMENTO EFETUADO -100,00",
                "",
                "Lançamentos: compras e saques",
                "17/12 UBER TRIP 18,40"
        );
    }

    private static ExtractionPipeline buildPipeline(
            ParseQualityEvaluator evaluator,
            ParseQualityValidator validator,
            AdobeExtractor adobeExtractor
    ) {
        InvoiceParserFactory invoiceParserFactory = new InvoiceParserFactory("http://localhost:8000");
        PdfTextExtractor pdfTextExtractor = new PdfTextExtractor();
        PdfOcrExtractor pdfOcrExtractor = org.mockito.Mockito.mock(PdfOcrExtractor.class);

        OcrProperties ocrProperties = new OcrProperties();
        ocrProperties.setEnabled(false);

        Environment environment = org.mockito.Mockito.mock(Environment.class);
        when(environment.getActiveProfiles()).thenReturn(new String[0]);
        when(environment.getProperty(anyString())).thenReturn(null);

        QualityScoreConfig qualityScoreConfig = new QualityScoreConfig();
        qualityScoreConfig.setMinScoreForHighQuality(75);

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
    void pdfBom_naoTentaAdobe() throws Exception {
        String text = itauText(PDFBOX_MARKER);
        byte[] pdfBytes = pdfWithText(text);

        ParseQualityEvaluator evaluator = org.mockito.Mockito.mock(ParseQualityEvaluator.class);
        ParseQualityValidator validator = org.mockito.Mockito.mock(ParseQualityValidator.class);
        AdobeExtractor adobeExtractor = org.mockito.Mockito.mock(AdobeExtractor.class);

        when(evaluator.evaluate(any(ParseResult.class), anyString())).thenReturn(90);
        when(validator.isValid(any(ParseResult.class), any(QualityScoreConfig.class))).thenReturn(true);

        ExtractionPipeline pipeline = buildPipeline(evaluator, validator, adobeExtractor);

        ExtractionResult result = pipeline.extractFromPdf(new ByteArrayInputStream(pdfBytes), null, null);

        assertEquals("PDFBox", result.source());
        assertNull(result.fallbackDecision());
        verify(adobeExtractor, never()).extract(any());
    }

    @Test
    void pdfRuim_adobeMelhora_escolheAdobe() throws Exception {
        String pdfboxText = itauText(PDFBOX_MARKER);
        String adobeText = itauText(ADOBE_MARKER);

        byte[] pdfBytes = pdfWithText(pdfboxText);

        ParseQualityEvaluator evaluator = org.mockito.Mockito.mock(ParseQualityEvaluator.class);
        ParseQualityValidator validator = org.mockito.Mockito.mock(ParseQualityValidator.class);
        AdobeExtractor adobeExtractor = org.mockito.Mockito.mock(AdobeExtractor.class);

        when(adobeExtractor.extract(any())).thenReturn(adobeText);

        when(evaluator.evaluate(any(ParseResult.class), anyString())).thenAnswer(inv -> {
            String raw = inv.getArgument(1, String.class);
            if (raw != null && raw.contains(ADOBE_MARKER)) return 85;
            return 40;
        });

        when(validator.isValid(any(ParseResult.class), any(QualityScoreConfig.class))).thenReturn(true);

        ExtractionPipeline pipeline = buildPipeline(evaluator, validator, adobeExtractor);

        ExtractionResult result = pipeline.extractFromPdf(new ByteArrayInputStream(pdfBytes), null, null);

        assertEquals("Adobe", result.source());
        assertEquals(AdobeFallbackStrategy.DECISION_ADOBE, result.fallbackDecision());
        assertEquals("Adobe", result.parseResult().getSource());
        verify(adobeExtractor, times(1)).extract(any());
    }

    @Test
    void pdfRuim_adobeFalha_usarPdfBoxMesmoAssim() throws Exception {
        String pdfboxText = itauText(PDFBOX_MARKER);
        byte[] pdfBytes = pdfWithText(pdfboxText);

        ParseQualityEvaluator evaluator = org.mockito.Mockito.mock(ParseQualityEvaluator.class);
        ParseQualityValidator validator = org.mockito.Mockito.mock(ParseQualityValidator.class);
        AdobeExtractor adobeExtractor = org.mockito.Mockito.mock(AdobeExtractor.class);

        when(adobeExtractor.extract(any())).thenReturn(null);
        when(evaluator.evaluate(any(ParseResult.class), anyString())).thenReturn(40);
        when(validator.isValid(any(ParseResult.class), any(QualityScoreConfig.class))).thenReturn(true);

        ExtractionPipeline pipeline = buildPipeline(evaluator, validator, adobeExtractor);

        ExtractionResult result = pipeline.extractFromPdf(new ByteArrayInputStream(pdfBytes), null, null);

        assertEquals("PDFBox", result.source());
        assertEquals(AdobeFallbackStrategy.DECISION_PDFBOX_FALLBACK, result.fallbackDecision());
        verify(adobeExtractor, times(1)).extract(any());
    }
}
