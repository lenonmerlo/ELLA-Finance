package com.ella.backend.services;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class InvoiceUploadServiceQualityHeuristicsTest {

    @Test
    void isLikelyGarbledMerchant_flagsMixedLetterDigitTokens() {
        assertTrue(InvoiceUploadService.isLikelyGarbledMerchant("bPU3OTOSY6GLEy SM"));
        assertTrue(InvoiceUploadService.isLikelyGarbledMerchant("PPSOubF6SOSO3T SM"));
        assertTrue(InvoiceUploadService.isLikelyGarbledMerchant("xY9aZ1bC2dE3fG4"));
    }

    @Test
    void isLikelyGarbledMerchant_doesNotFlagNormalMerchants() {
        assertFalse(InvoiceUploadService.isLikelyGarbledMerchant("TOKIO MARINE*AUTO"));
        assertFalse(InvoiceUploadService.isLikelyGarbledMerchant("ESPACO LASER"));
        assertFalse(InvoiceUploadService.isLikelyGarbledMerchant("SUPERMERCADO ANGELONI"));
    }
}
