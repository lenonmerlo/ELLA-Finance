package com.ella.backend.services.ocr;

import java.awt.image.BufferedImage;

public interface OcrService {

    /**
     * Extracts text from an image using OCR.
     */
    String extractText(BufferedImage image);
}
