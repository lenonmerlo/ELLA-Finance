package com.ella.backend.services.ocr;

import java.awt.image.BufferedImage;

import net.sourceforge.tess4j.Tesseract;
import net.sourceforge.tess4j.TesseractException;

public class TesseractOcrService implements OcrService {

    private final OcrProperties ocrProperties;

    /**
     * Tess4J's {@link Tesseract} is not thread-safe. Keep one instance per thread.
     */
    private final ThreadLocal<Tesseract> threadLocalTesseract;

    public TesseractOcrService(OcrProperties ocrProperties) {
        this.ocrProperties = ocrProperties;
        this.threadLocalTesseract = ThreadLocal.withInitial(this::createTesseract);
    }

    @Override
    public String extractText(BufferedImage image) {
        if (image == null) return "";

        try {
            String text = threadLocalTesseract.get().doOCR(image);
            return text == null ? "" : text;
        } catch (TesseractException e) {
            throw new OcrException("Falha ao executar OCR (Tesseract)", e);
        }
    }

    private Tesseract createTesseract() {
        Tesseract tesseract = new Tesseract();

        String datapath = ocrProperties.getTessdataPath();
        if (datapath != null && !datapath.isBlank()) {
            tesseract.setDatapath(datapath);
        }

        String language = ocrProperties.getLanguage();
        if (language != null && !language.isBlank()) {
            tesseract.setLanguage(language);
        }

        return tesseract;
    }
}
