package com.ella.backend.services.ocr;

import java.io.IOException;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Service;

@Service
public class PdfTextExtractor {

    public String extractText(PDDocument document) throws IOException {
        PDFTextStripper stripper = new PDFTextStripper();
        return stripper.getText(document);
    }

    /**
     * Some bank PDFs (notably statements with multiple columns/tables) require positional sorting
     * to preserve a readable row order (date/merchant/amount).
     */
    public String extractTextSorted(PDDocument document) throws IOException {
        PDFTextStripper stripper = new PDFTextStripper();
        stripper.setSortByPosition(true);
        stripper.setShouldSeparateByBeads(true);
        return stripper.getText(document);
    }
}
