package com.ella.backend.services.ocr;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "ella.ocr")
public class OcrProperties {

    /**
     * Enables OCR fallback for scanned/image-only PDFs.
     */
    private boolean enabled = false;

    /**
     * Tesseract language(s), e.g. "por", "eng", or "por+eng".
     */
    private String language = "por";

    /**
     * Optional path that contains the "tessdata" directory.
     * If empty, Tess4J/Tesseract will rely on OS installation and environment.
     */
    private String tessdataPath = "";

    private Pdf pdf = new Pdf();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getLanguage() {
        return language;
    }

    public void setLanguage(String language) {
        this.language = language;
    }

    public String getTessdataPath() {
        return tessdataPath;
    }

    public void setTessdataPath(String tessdataPath) {
        this.tessdataPath = tessdataPath;
    }

    public Pdf getPdf() {
        return pdf;
    }

    public void setPdf(Pdf pdf) {
        this.pdf = pdf;
    }

    public static class Pdf {

        /**
         * If extracted PDF text length is below this threshold, OCR is attempted.
         */
        private int minTextLength = 200;

        /**
         * Render DPI for OCR.
         */
        private int renderDpi = 220;

        /**
         * Max number of pages to OCR.
         */
        private int maxPages = 6;

        public int getMinTextLength() {
            return minTextLength;
        }

        public void setMinTextLength(int minTextLength) {
            this.minTextLength = minTextLength;
        }

        public int getRenderDpi() {
            return renderDpi;
        }

        public void setRenderDpi(int renderDpi) {
            this.renderDpi = renderDpi;
        }

        public int getMaxPages() {
            return maxPages;
        }

        public void setMaxPages(int maxPages) {
            this.maxPages = maxPages;
        }
    }
}
