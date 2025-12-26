package com.ella.backend.services.ocr;

import java.awt.image.BufferedImage;

public class DisabledOcrService implements OcrService {

    @Override
    public String extractText(BufferedImage image) {
        throw new IllegalStateException("OCR está desabilitado. Habilite com ella.ocr.enabled=true");
    }
}
