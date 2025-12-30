package com.ella.backend.services.invoices.parsers;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import com.ella.backend.enums.TransactionType;

class MerchantCategoryMapperTest {

    @Test
    void mapsLazerKeywords() {
        assertEquals("Lazer", MerchantCategoryMapper.categorize("BUTECO DO ZE", TransactionType.EXPENSE));
        assertEquals("Lazer", MerchantCategoryMapper.categorize("BOTECO DA ESQUINA", TransactionType.EXPENSE));
        assertEquals("Lazer", MerchantCategoryMapper.categorize("CASA DE SHOWS XYZ", TransactionType.EXPENSE));
        assertEquals("Lazer", MerchantCategoryMapper.categorize("FLUENTE", TransactionType.EXPENSE));
        assertEquals("Lazer", MerchantCategoryMapper.categorize("BALTHAZAR RESTAURANT NEW YORK", TransactionType.EXPENSE));
    }

    @Test
    void mapsVestuarioBrands() {
        assertEquals("Vestuário", MerchantCategoryMapper.categorize("EC*ADIDAS", TransactionType.EXPENSE));
        assertEquals("Vestuário", MerchantCategoryMapper.categorize("NIKE.COM", TransactionType.EXPENSE));
        assertEquals("Vestuário", MerchantCategoryMapper.categorize("ADIDAS BR", TransactionType.EXPENSE));
        assertEquals("Vestuário", MerchantCategoryMapper.categorize("MIZUNO STORE", TransactionType.EXPENSE));
        assertEquals("Vestuário", MerchantCategoryMapper.categorize("BOLOVO", TransactionType.EXPENSE));
        assertEquals("Vestuário", MerchantCategoryMapper.categorize("BIRDEN", TransactionType.EXPENSE));
        assertEquals("Vestuário", MerchantCategoryMapper.categorize("PUMA", TransactionType.EXPENSE));

        assertEquals("Vestuário", MerchantCategoryMapper.categorize("H&M BRASIL", TransactionType.EXPENSE));
        assertEquals("Vestuário", MerchantCategoryMapper.categorize("C&C", TransactionType.EXPENSE));

        assertEquals("Vestuário", MerchantCategoryMapper.categorize("AMAZONMKTPLC*FASHION", TransactionType.EXPENSE));
        assertEquals("E-commerce", MerchantCategoryMapper.categorize("AMAZON BR", TransactionType.EXPENSE));

        assertEquals("Vestuário", MerchantCategoryMapper.categorize("MERCADOLIVREFASHION", TransactionType.EXPENSE));
    }

    @Test
    void mapsAcademiaSaudeMerchants() {
        assertEquals("Academia/Saúde", MerchantCategoryMapper.categorize("BODYTECH", TransactionType.EXPENSE));
        assertEquals("Academia/Saúde", MerchantCategoryMapper.categorize("SMART FIT", TransactionType.EXPENSE));
        assertEquals("Academia/Saúde", MerchantCategoryMapper.categorize("GOLD'S GYM", TransactionType.EXPENSE));
        assertEquals("Academia/Saúde", MerchantCategoryMapper.categorize("GYMPASS", TransactionType.EXPENSE));
        assertEquals("Academia/Saúde", MerchantCategoryMapper.categorize("PILATES CENTER", TransactionType.EXPENSE));
    }

    @Test
    void mapsFarmaciasEOticasAsSaude() {
        assertEquals("Saúde", MerchantCategoryMapper.categorize("DROGARIA PACHECO", TransactionType.EXPENSE));
        assertEquals("Saúde", MerchantCategoryMapper.categorize("RAIA DROGASIL", TransactionType.EXPENSE));
        assertEquals("Saúde", MerchantCategoryMapper.categorize("ULTRA FARMA", TransactionType.EXPENSE));
        assertEquals("Saúde", MerchantCategoryMapper.categorize("CONSULTA REMEDIOS", TransactionType.EXPENSE));
        assertEquals("Saúde", MerchantCategoryMapper.categorize("OTICA DINIZ", TransactionType.EXPENSE));
    }

    @Test
    void mapsSupermercadosAsAlimentacao() {
        assertEquals("Alimentação", MerchantCategoryMapper.categorize("CARREFOUR BRASIL", TransactionType.EXPENSE));
        assertEquals("Alimentação", MerchantCategoryMapper.categorize("PAO DE ACUCAR", TransactionType.EXPENSE));
        assertEquals("Alimentação", MerchantCategoryMapper.categorize("ASSAI ATACADISTA", TransactionType.EXPENSE));
        assertEquals("Alimentação", MerchantCategoryMapper.categorize("PADARIA DO BAIRRO", TransactionType.EXPENSE));
    }

    @Test
    void mapsEducacaoPlatforms() {
        assertEquals("Educação", MerchantCategoryMapper.categorize("ALURA CURSOS", TransactionType.EXPENSE));
        assertEquals("Educação", MerchantCategoryMapper.categorize("UDEMY BRASIL", TransactionType.EXPENSE));
        assertEquals("Educação", MerchantCategoryMapper.categorize("ENGLISHLIVE", TransactionType.EXPENSE));
        assertEquals("Educação", MerchantCategoryMapper.categorize("LEETCODE", TransactionType.EXPENSE));
    }

    @Test
    void mapsAssinaturasAndPriorities() {
        assertEquals("Assinaturas", MerchantCategoryMapper.categorize("NETFLIX", TransactionType.EXPENSE));
        assertEquals("Assinaturas", MerchantCategoryMapper.categorize("AMAZON PRIME", TransactionType.EXPENSE));
        assertEquals("Assinaturas", MerchantCategoryMapper.categorize("APPLE.COM/BILL", TransactionType.EXPENSE));
        assertEquals("E-commerce", MerchantCategoryMapper.categorize("AMAZON BR", TransactionType.EXPENSE));
        assertEquals("Alimentação", MerchantCategoryMapper.categorize("UBER EATS", TransactionType.EXPENSE));
        assertEquals("Transporte", MerchantCategoryMapper.categorize("UBER TRIP", TransactionType.EXPENSE));
    }

    @Test
    void mapsBelezaMerchants() {
        assertEquals("Beleza", MerchantCategoryMapper.categorize("NATURA", TransactionType.EXPENSE));
        assertEquals("Beleza", MerchantCategoryMapper.categorize("O BOTICARIO", TransactionType.EXPENSE));
        assertEquals("Beleza", MerchantCategoryMapper.categorize("SEPHORA", TransactionType.EXPENSE));
        assertEquals("Beleza", MerchantCategoryMapper.categorize("MERCADOLIVRE COSMETICOS", TransactionType.EXPENSE));
    }

    @Test
    void mapsPlanoSaudeESeguroKeywords() {
        assertEquals("Plano de Saúde", MerchantCategoryMapper.categorize("UNIMED LITORAL", TransactionType.EXPENSE));
        assertEquals("Seguro", MerchantCategoryMapper.categorize("BRADESCO AUTO", TransactionType.EXPENSE));
        assertEquals("Vestuário", MerchantCategoryMapper.categorize("VIVARA FOR", TransactionType.EXPENSE));
        assertEquals("Reembolso", MerchantCategoryMapper.categorize("PAYGOAL", TransactionType.INCOME));
    }

    @Test
    void mapsIfoodIfdPrefix() {
        assertEquals("iFood", MerchantCategoryMapper.categorize("IFD*EMPREENDIMENTOS PA", TransactionType.EXPENSE));
        assertEquals("iFood", MerchantCategoryMapper.categorize("IFD*VITOR SILVA PRANDO", TransactionType.EXPENSE));
    }

    @Test
    void mapsBarPrefixAsLazer() {
        assertEquals("Lazer", MerchantCategoryMapper.categorize("BARZIN", TransactionType.EXPENSE));
    }

    @Test
    void mapsNubankSpecificMerchants() {
        assertEquals("Seguro", MerchantCategoryMapper.categorize("Pepay*Segurofatura", TransactionType.EXPENSE));
        assertEquals("Assinaturas", MerchantCategoryMapper.categorize("D*Google Gardenscape", TransactionType.EXPENSE));
        assertEquals("Assinaturas", MerchantCategoryMapper.categorize("GOOGLE BRASIL PAGAMENTOS LTDA.", TransactionType.EXPENSE));
    }

    @Test
    void mapsTripExampleMerchants() {
        assertEquals("Taxas e Juros", MerchantCategoryMapper.categorize("ANUIDADE DIFERENCIADA", TransactionType.EXPENSE));
        assertEquals("Viagem", MerchantCategoryMapper.categorize("TicketeTICKETE CO TRAVEDubai", TransactionType.EXPENSE));
        assertEquals("Lazer", MerchantCategoryMapper.categorize("STATUE CRUISES 877-523-9849", TransactionType.EXPENSE));
        assertEquals("Vestuário", MerchantCategoryMapper.categorize("FERRAGAMO New York", TransactionType.EXPENSE));
        assertEquals("E-commerce", MerchantCategoryMapper.categorize("APPLE STORE #R095 NEW YORK", TransactionType.EXPENSE));
        assertEquals("Lazer", MerchantCategoryMapper.categorize("WWW.STATUEOFLIBERTYTICK866-5689827", TransactionType.EXPENSE));
        assertEquals("Alimentação", MerchantCategoryMapper.categorize("DIM SUM PALACE (46TH S 131-27305592", TransactionType.EXPENSE));
        assertEquals("Lazer", MerchantCategoryMapper.categorize("2US TENNIS ASC FLUSHING", TransactionType.EXPENSE));
    }

    @Test
    void mapsSicrediRecurringMerchantsFromMarkdownList() {
        assertEquals("Alimentação", MerchantCategoryMapper.categorize("Orbita Blue", TransactionType.EXPENSE));
        assertEquals("Vestuário", MerchantCategoryMapper.categorize("NETSHOES", TransactionType.EXPENSE));
        assertEquals("Assinaturas", MerchantCategoryMapper.categorize("Smiles Club Smiles", TransactionType.EXPENSE));

        assertEquals("Saúde", MerchantCategoryMapper.categorize("Pronace", TransactionType.EXPENSE));
        assertEquals("Saúde", MerchantCategoryMapper.categorize("Globo Formulas", TransactionType.EXPENSE));
        assertEquals("Saúde", MerchantCategoryMapper.categorize("Regitec Assistencia", TransactionType.EXPENSE));
        assertEquals("Saúde", MerchantCategoryMapper.categorize("Asa Unikka Pharma", TransactionType.EXPENSE));
        assertEquals("Saúde", MerchantCategoryMapper.categorize("Control Vita", TransactionType.EXPENSE));
        assertEquals("Saúde", MerchantCategoryMapper.categorize("Consultorio Dr X", TransactionType.EXPENSE));

        assertEquals("Academia/Saúde", MerchantCategoryMapper.categorize("Paypal Keeprunning", TransactionType.EXPENSE));
        assertEquals("Academia/Saúde", MerchantCategoryMapper.categorize("Fina Fit Indust", TransactionType.EXPENSE));

        assertEquals("Transporte", MerchantCategoryMapper.categorize("Motolibre", TransactionType.EXPENSE));
        assertEquals("Lazer", MerchantCategoryMapper.categorize("Libreproducoes", TransactionType.EXPENSE));

        assertEquals("Vestuário", MerchantCategoryMapper.categorize("Liz Lingerie", TransactionType.EXPENSE));
        assertEquals("Alimentação", MerchantCategoryMapper.categorize("Super Adega", TransactionType.EXPENSE));
        assertEquals("Alimentação", MerchantCategoryMapper.categorize("Crepe Potiguar", TransactionType.EXPENSE));
        assertEquals("Alimentação", MerchantCategoryMapper.categorize("Bec Italo", TransactionType.EXPENSE));
        assertEquals("Lazer", MerchantCategoryMapper.categorize("One Park Ceara", TransactionType.EXPENSE));
    }

    @Test
    void mapsSantanderMerchantsFromScreenshot() {
        assertEquals("Seguro", MerchantCategoryMapper.categorize("TOKIO MARINE*AUTO", TransactionType.EXPENSE));
        assertEquals("Saúde", MerchantCategoryMapper.categorize("P9ESPACOLASERES", TransactionType.EXPENSE));
        assertEquals("Lazer", MerchantCategoryMapper.categorize("SEAWORLD/BUSCH GARDENS", TransactionType.EXPENSE));
    }
}
