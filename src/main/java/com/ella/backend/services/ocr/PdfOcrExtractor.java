package com.ella.backend.services.ocr;

import java.awt.image.BufferedImage;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class PdfOcrExtractor {

    private final OcrProperties ocrProperties;
    private final OcrService ocrService;

    /**
     * Runs OCR on up to {@code ella.ocr.pdf.max-pages} pages using in-memory BufferedImages.
     */
    public String extractText(PDDocument document) {
        if (document == null) return "";
        if (!ocrProperties.isEnabled()) return "";

        int dpi = Math.max(72, ocrProperties.getPdf().getRenderDpi());
        int maxPages = Math.max(1, ocrProperties.getPdf().getMaxPages());

        int totalPages = document.getNumberOfPages();
        int pagesToProcess = Math.min(totalPages, maxPages);

        long startMs = System.currentTimeMillis();

        try {
            PDFRenderer renderer = new PDFRenderer(document);
            StringBuilder sb = new StringBuilder();

            for (int pageIndex = 0; pageIndex < pagesToProcess; pageIndex++) {
                BufferedImage image = renderer.renderImageWithDPI(pageIndex, dpi, ImageType.RGB);
                try {
                    String pageText = ocrService.extractText(image);
                    if (pageText != null && !pageText.isBlank()) {
                        sb.append(pageText).append('\n');
                    }
                } finally {
                    // Help GC/free native memory sooner
                    image.flush();
                }
            }

            String result = sb.toString();
            long elapsedMs = System.currentTimeMillis() - startMs;
            log.info("[OCR] Completed: pages={}/{} dpi={} elapsedMs={} textLen={}",
                    pagesToProcess, totalPages, dpi, elapsedMs, result.length());
            return result;
        } catch (Exception e) {
            throw new OcrException("Falha ao executar OCR no PDF", e);
        }
    }
}
