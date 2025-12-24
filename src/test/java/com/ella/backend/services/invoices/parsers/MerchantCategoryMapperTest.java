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
}
