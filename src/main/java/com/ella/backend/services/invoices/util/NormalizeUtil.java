package com.ella.backend.services.invoices.util;

import java.text.Normalizer;
import java.util.Locale;

public class NormalizeUtil {

    /**
     * Normaliza texto: lowercase, remove acentos, colapsa espaços
     * Exemplo: "Itaú Personalité" => "itau personalite"
     */
    public static String normalize(String text) {
        if (text == null || text.isBlank()) return "";

        // 1. Lowercase
        String result = text.toLowerCase(Locale.forLanguageTag("pt-BR"));

        // 2. Remove acentos (NFD decomposition)
        result = Normalizer.normalize(result, Normalizer.Form.NFD);
        result = result.replaceAll("\\p{M}", ""); // Remove combining marks

        // 3. Normaliza espaçamentos de PDF (NBSP/Unicode separators) para espaço comum.
        // PDFBox frequentemente introduz NBSP (\u00A0) e outros separadores que NÃO casam com \s.
        result = result.replace('\u00A0', ' ');
        result = result.replaceAll("\\p{Z}+", " ");

        // 4. Colapsa espaços
        result = result.replaceAll("\\s+", " ").trim();

        return result;
    }

    /**
     * Verifica se texto contém keyword (case-insensitive, sem acentos)
     */
    public static boolean containsKeyword(String text, String keyword) {
        if (text == null || keyword == null) return false;
        return normalize(text).contains(normalize(keyword));
    }
}
