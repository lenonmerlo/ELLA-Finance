package com.ella.backend.services.ocr;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

import javax.imageio.ImageIO;

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
    private final GoogleVisionOcrService googleVisionOcrService;

    /**
     * Runs OCR on up to {@code ella.ocr.pdf.max-pages} pages using in-memory BufferedImages.
     */
    public String extractText(PDDocument document) {
        if (document == null) return "";

        boolean tesseractEnabled = ocrProperties.isEnabled();
        boolean googleEnabled = googleVisionOcrService.isEnabled();

        if (!tesseractEnabled && !googleEnabled) return "";

        int dpi = Math.max(72, ocrProperties.getPdf().getRenderDpi());
        int maxPages = Math.max(1, ocrProperties.getPdf().getMaxPages());

        int totalPages = document.getNumberOfPages();
        int pagesToProcess = Math.min(totalPages, maxPages);

        long startMs = System.currentTimeMillis();

        try {
            PDFRenderer renderer = new PDFRenderer(document);
            StringBuilder sb = new StringBuilder();

            int googlePages = 0;
            int tesseractPages = 0;

            for (int pageIndex = 0; pageIndex < pagesToProcess; pageIndex++) {
                BufferedImage image = renderer.renderImageWithDPI(pageIndex, dpi, ImageType.RGB);
                try {
                    String pageText = "";

                    // 1) Try Google Vision first (when enabled)
                    if (googleEnabled) {
                        try {
                            byte[] imageBytes = toPngBytes(image);
                            long gs = System.currentTimeMillis();
                            log.info("[GoogleVision]: Processando página {}/{} (pngBytes={})",
                                    pageIndex + 1, pagesToProcess, imageBytes.length);
                            pageText = googleVisionOcrService.extractTextFromImage(imageBytes);
                            long ge = System.currentTimeMillis() - gs;
                            log.info("[GoogleVision]: Página {}/{} concluída textLen={} elapsedMs={}",
                                    pageIndex + 1, pagesToProcess, pageText == null ? 0 : pageText.length(), ge);
                            if (pageText != null && !pageText.isBlank()) {
                                googlePages++;
                            }
                        } catch (Exception e) {
                            // fallback to Tesseract
                            log.warn("[GoogleVision]: Falha na página {}/{} (fallback para Tesseract): {}",
                                    pageIndex + 1, pagesToProcess, e.toString());
                            pageText = "";
                        }
                    }

                    // 2) Fallback: Tesseract (only if enabled) OR if Google produced blank text
                    if ((pageText == null || pageText.isBlank()) && tesseractEnabled) {
                        String t = ocrService.extractText(image);
                        if (t != null && !t.isBlank()) {
                            tesseractPages++;
                            pageText = t;
                        }
                    }

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
            log.info("[OCR] Completed: pages={}/{} dpi={} elapsedMs={} textLen={} googlePages={} tesseractPages={}",
                    pagesToProcess, totalPages, dpi, elapsedMs, result.length(), googlePages, tesseractPages);
            return result;
        } catch (Exception e) {
            throw new OcrException("Falha ao executar OCR no PDF", e);
        }
    }

    private static byte[] toPngBytes(BufferedImage image) throws IOException {
        if (image == null) return new byte[0];
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream(64 * 1024)) {
            ImageIO.write(image, "png", baos);
            return baos.toByteArray();
        }
    }
}
