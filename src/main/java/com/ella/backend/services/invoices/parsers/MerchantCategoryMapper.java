package com.ella.backend.services.invoices.parsers;

import java.text.Normalizer;

import com.ella.backend.enums.TransactionType;

final class MerchantCategoryMapper {

    private MerchantCategoryMapper() {
    }

    static String categorize(String description, TransactionType type) {
        if (description == null || description.isBlank()) return "Outros";

        String n = normalize(description);
        String nCompact = compact(n);

        // Créditos/estornos/pagamentos
        if (type == TransactionType.INCOME) {
            if (n.contains("INCLUSAO DE PAGAMENTO") || n.contains("PAGAMENTO")) {
                return "Pagamento";
            }
            if (n.contains("ESTORNO") || n.contains("CREDITO") || n.contains("REEMBOLSO") || n.contains("DEVOLUCAO")
                    || n.contains("PAYGOAL") || n.contains("CASHBACK") || n.contains("PONTOS")) {
                return "Reembolso";
            }
            return "Outros";
        }

        // Taxas
        if (n.contains("ANUIDADE") || n.contains("ANULIDADE")) {
            return "Taxas e Juros";
        }

        // ===== Assinaturas / Streaming / Software =====
        if (containsAny(n, nCompact,
                "NETFLIX",
                "DISNEY PLUS",
                "DISNEY+",
                "AMAZON PRIME",
                "PRIME VIDEO",
                "HBO MAX",
                "GLOBOPLAY",
                "GLOBO PLAY",
                "PARAMOUNT PLUS",
                "PARAMOUNT+",
                "APPLE TV",
                "APPLE TV PLUS",
                "APPLE TV+",
                "SPOTIFY",
                "APPLE MUSIC",
                "YOUTUBE MUSIC",
                "DEEZER",
                "TIDAL",
                "PLAYSTATION PLUS",
                "PS PLUS",
                "XBOX GAME PASS",
                "GAME PASS",
                "NINTENDO SWITCH ONLINE",
                "NINTENDO ONLINE",
                "SWITCH ONLINE",
                "STEAM",
                "EPIC GAMES",
                "MICROSOFT 365",
                "OFFICE 365",
                "ONEDRIVE",
                "MICROSOFT TEAMS",
                "TEAMS",
                "XBOX LIVE",
                "XBOX GOLD",
                "ADOBE",
                "ADOBE CREATIVE CLOUD",
                "ADOBE CC",
                "PHOTOSHOP",
                "PREMIERE",
                "LIGHTROOM",
                "ICLOUD",
                "ICLOUD PLUS",
                "ICLOUD+",
                "APPLE ONE",
                "GOOGLE ONE",
                "GOOGLE WORKSPACE",
                "WORKSPACE",
                "YOUTUBE PREMIUM",
                // Nubank frequentemente traz "D*GOOGLE ..." e "GOOGLE BRASIL PAGAMENTOS"
                "D GOOGLE",
                "DGOOGLE",
                "GOOGLE BRASIL PAGAMENTOS",
                "BRASIL PAGAMENTOS")) {
            return "Assinaturas";
        }

        // Google Payments (evita tornar "GOOGLE" genérico demais)
        if (n.contains("GOOGLE") && n.contains("PAGAMENTOS")) {
            return "Assinaturas";
        }

        // Apple cobranças recorrentes (normalize transforma "APPLE.COM/BILL" em "APPLE COM BILL")
        if (n.contains("APPLE COM BILL") || (n.contains("APPLE") && n.contains("COM") && n.contains("BILL"))) {
            return "Assinaturas";
        }

        // Mapeamentos explícitos (mais específicos primeiro)
        if (n.contains("AIRBNB")) return "Hospedagem";
        if (containsAny(n, nCompact, "BOOKING", "BOOKING COM", "EXPEDIA", "TRIVAGO")) return "Hospedagem";

        // Companhias aéreas (Viagem)
        // Atenção a falsos positivos por substring curta (ex.: "GOL" dentro de "GOLD'S GYM"; "AZUL" dentro de "ZONA AZUL")
        if (containsAny(n, nCompact, "LATAM", "UNITED")
                || n.matches(".*\\bGOL\\b.*")
                || containsAny(n, nCompact, "GOL LINHAS", "GOL LINHAS AEREAS", "GOL LINHAS AEREAS SA")
                || containsAny(n, nCompact, "AZUL LINHAS", "AZUL LINHAS AEREAS", "AZUL AEREAS")) {
            return "Viagem";
        }

        // compat: manter regra legada (normalmente não bate após normalize, mas não atrapalha)
        if (n.contains("APPLE.COM/BILL") || (n.contains("APPLE.COM") && n.contains("BILL"))) return "Assinaturas";

        // Delivery (prioridade antes de Transporte, porque UBER EATS contém UBER)
        // normalize transforma "IFD*" em "IFD" (o '*' vira espaço)
        if (n.equals("IFD") || n.startsWith("IFD ")) return "iFood";
        if (n.contains("IFOOD")) return "iFood";
        if (containsAny(n, nCompact, "UBER EATS", "UBEREATS", "RAPPI", "LOGGI", "99FOOD", "99 FOOD", "AIQFOME")) {
            return "Alimentação";
        }

        // ===== Vestuário / Moda =====
        // Marcas esportivas/premium
        if (containsAny(n, nCompact,
                "ADIDAS",
                "NIKE",
                "PUMA",
                "MIZUNO",
                "ASICS",
                "NEW BALANCE",
                "REEBOK",
                "SAUCONY",
                "CONVERSE",
                "VANS")) {
            return "Vestuário";
        }

        // Marcas nacionais / lojas esportivas
        if (containsAny(n, nCompact,
                "LUPO",
                "HAVAIANAS",
                "MORMAII",
                "SPEEDO",
                "MOTTA SPORT",
                "BIRDEN",
                "GALAPAGOS",
                "FUTFANATICS",
                "CENTAURO",
                "BOLOVO")) {
            return "Vestuário";
        }

        // Lojas de departamento/multimarcas
        if (containsAny(n, nCompact,
                "SHEIN",
                "RENNER",
                "LOJAS RENNER",
                "RIACHUELO",
                "LOJAS RIACHUELO",
                "ZARA",
                "VIVARA",
                "FOREVER 21",
                "FASHION",
                "MARCELOSHOES",
                "MARCELOS SHOES")) {
            return "Vestuário";
        }

        // H&M e C&C têm pontuação; normalização+compact pega "HM" e "CC"
        if (containsAny(n, nCompact, "H&M")) {
            return "Vestuário";
        }
        if (containsAny(n, nCompact, "C&C")) {
            return "Vestuário";
        }

        // Marketplaces de vestuário (mais específico que e-commerce genérico)
        if (containsAny(n, nCompact, "MERCADOLIVREFASHION", "MERCADOLIVREROUPAS")) {
            return "Vestuário";
        }
        if (n.contains("AMAZON") && n.contains("FASHION")) {
            return "Vestuário";
        }

        // Marketplaces/lojas online que muitas vezes são vestuário
        if (containsAny(n, nCompact, "SHOPEE", "ALIEXPRESS", "ALI EXPRESS", "WISH")) {
            return "Vestuário";
        }

        // ===== Academia / Saúde (fitness) =====
        if (containsAny(n, nCompact,
                "VIX ACADEMIA",
                "SMART FIT",
                "SMARTFIT",
                "BLUEFIT",
                "BODYTECH",
                "GOLD'S GYM",
                "GOLDS GYM",
                "GOLDSGYM",
                "GOLD GYM",
                "XTREME",
                "FITDANCE",
                "COMPANHIA ATHLETICA",
                "CA ACADEMIA",
                "GYMPASS",
                "FITPASS",
                "CLASSPASS",
                "FITPRO",
                "YOGA",
                "PILATES")) {
            return "Academia/Saúde";
        }

        // Fallback por palavra-chave (evita perder academias locais)
        if (n.contains("ACADEMIA") || n.contains(" GYM") || n.endsWith(" GYM")) {
            return "Academia/Saúde";
        }

        // "OTICA" precisa ser palavra inteira (evita "BOTICARIO" virar Saúde)
        if (n.contains("VISAOEXPRESS") || n.matches(".*\\bOTICA\\b.*") || n.matches(".*\\bOTICAS\\b.*")) {
            return "Saúde";
        }

        // ===== Plano de Saúde (operadoras) =====
        if (containsAny(n, nCompact,
                "UNIMED",
                "BRADESCO SAUDE",
                "BRADESCO SAÚDE",
                "AMIL",
                "SULAMERICA",
                "SULAMÉRICA",
                "HAPVIDA")) {
            return "Plano de Saúde";
        }

        // ===== Seguros =====
        if (containsAny(n, nCompact,
                "BRADESCO AUTO",
                "MONGERAL",
                "SEGURO",
                "SEGUROS",
                "SEGURADORA",
                "SEGURADOR",
                "PEPAY",
                "SEGUROFATURA",
                "SUPROTEGIDO",
                "PROTEGIDO")) {
            return "Seguro";
        }

        // ===== Farmácias / Drogarias / Óticas =====
        if (containsAny(n, nCompact,
                "FARMACIA DO DR",
                "FARMACIA DR",
                "DROGARIA PACHECO",
                "DROGARIAS PACHECO",
                "DROGARIA ARAUJO",
                "ARAUJO FARMACIAS",
                "FARMACIA SANTA CLARA",
                "SANTA CLARA",
                "RAIA",
                "DROGASIL",
                "RAIA DROGASIL",
                "ULTRAFARMA",
                "ULTRA FARMA",
                "NOTREDAME",
                "NOTRE DAME",
                "PAGUE MENOS",
                "FARMACIAS PAGUE MENOS",
                "FARMACIA GLOBAL",
                "GLOBAL FARMACIAS",
                "MANIPULADA",
                "FARMACIA MANIPULADA",
                "FARMACIA ONLINE",
                "FARMACIA.COM.BR",
                "FARMACIASBRASILEIRAS",
                "CONSULTA REMEDIOS",
                "CONSULTARMEDIOS",
                "DROGARIA ONLINE",
                "DROGARIA.COM.BR",
                "OTICA CAROL",
                "CAROL OTICAS",
                "OTICA SATO",
                "SATO OTICAS",
                "OTICA DINIZ",
                "DINIZ OTICAS",
                "OTICA MISTER",
                "MISTER OTICAS",
                "OTICA PREMIER",
                "PREMIER OTICAS")) {
            return "Saúde";
        }

        // Fallback: termos genéricos de farmácia/drogaria
        if (n.contains("FARMACIA") || n.contains("DROGARIA") || n.contains("REMEDIO") || n.contains("REMÉDIO")) {
            return "Saúde";
        }

        // Pet (cuidado para não bater com "petro")
        if (n.contains("PET STOCK") || n.matches(".*\\bPET\\b.*")) {
            return "Pet";
        }

        // Amazon / e-commerce
        if (n.contains("AMAZONMKTPLC") || n.contains("AMAZON BR") || n.equals("AMAZON") || n.startsWith("AMAZON ") || n.contains(" AMAZON")) {
            return "E-commerce";
        }

        // ===== Supermercados / Alimentação =====
        if (containsAny(n, nCompact,
                "CARREFOUR",
                "EXTRA",
                "EXTRA SUPERMERCADO",
                "PAO DE ACUCAR",
                "PÃO DE AÇÚCAR",
                "ZONA SUL",
                "PREZUNIC",
                "COOP",
                "SONDA",
                "WALMART",
                "ATACADAO",
                "ATACADÃO",
                "ASSAI",
                "ASSAÍ",
                "UNIAO SUPRIMENTOS",
                "UNION SUPRIMENTOS",
                "EXTRA PLUS",
                "EXTRAPLUS",
                "MERCADO CENTRAL",
                "HORTO MERCAD",
                "HORTOMERCAD",
                "SUPERMERCADO LOCAL",
                "LOCAL SUPER",
                "PADARIA",
                "CONFEITARIA")) {
            return "Alimentação";
        }

        // Serviços específicos
        if (n.contains("ZZRSV SP JARDINS LINK")) {
            return "Serviços";
        }

        // Lazer: bares/restaurantes/churrascarias etc
        // Inclui prefixos tipo BARZIN/BARZINHO; evita colisões comuns como BARRA
        if (n.matches("^BAR(?!R).*")
            || n.contains(" BAR ")
            || n.startsWith("BAR ")
                || n.contains("CHURRASC")
                || n.contains("CHOPPERIA")
                || n.contains("RESTAURANTE")
                || n.contains("BOTECO")
                || n.contains("BUTECO")
                || n.contains("CASA DE SHOW")
                || n.contains("FLUENTE")
                || n.contains("BEBIDA")
                || n.contains("GRAU DE BEBIDA")
                || n.contains("PIMENTA CARIOCA")
                || n.contains("CHURRASCANAL")
                || n.contains("CHOPPERIA DA PRACA")) {
            return "Lazer";
        }

        // ===== Educação =====
        if (containsAny(n, nCompact,
                "DEVSUPERIOR",
                "PG DEVSUPERIOR",
                "UDEMY",
                "COURSERA",
                "ALURA",
                "PLATZI",
                "SKILLSHARE",
                "LINKEDIN LEARNING",
                "LINKEDIN LEARN",
                "EDTECH",
                "CAMBLY",
                "PREPLY",
                "ENGLISH LIVE",
                "ENGLISHLIVE",
                "BABBEL",
                "DUOLINGO",
                "DUOLINGO PLUS",
                "BUSUU",
                "VOXY",
                "MOSALINGUA",
                "ESCOLA TECNICA",
                "ESCOLA TÉCNICA",
                "CURSO PREPARATORIO",
                "CURSO PREPARATÓRIO",
                "CENTRO DE TREINAMENTO",
                "INSTITUTO EDUCACIONAL",
                "CODECADEMY",
                "TREEHOUSE",
                "DATACAMP",
                "HACKERRANK",
                "LEETCODE")) {
            return "Educação";
        }

        // ===== Beleza =====
        if (containsAny(n, nCompact,
                "SALAO",
                "SALAO DE BELEZA",
                "SALÃO",
                "SALÃO DE BELEZA",
                "BARBEARIA",
                "CASH BARBER",
                "MANICURE",
                "PEDICURE",
                "NATURA",
                "BOTICARIO",
                "O BOTICARIO",
                "AVON",
                "MARY KAY",
                "MARYKAY",
                "SEPHORA",
                "COSMETICOS",
                "COSMÉTICOS")) {
            return "Beleza";
        }

        // Heurísticas genéricas já existentes (mantém compatibilidade)
        if (n.contains("UBER") || n.contains(" 99") || n.contains("99 ") || n.contains("CABIFY") || n.contains("LYFT") || n.contains("EASY TAXI") || n.contains("EASYTAXI")) {
            return "Transporte";
        }
        if (n.contains("POSTO") || n.contains("COMBUST") || n.contains("IPIRANGA") || n.contains("SHELL") || n.contains("PETRO")) return "Transporte";
        if (n.contains("ESTACIONAMENTO") || n.contains("PARKING") || n.contains("ZONA AZUL")) return "Transporte";

        if (n.contains("PIZZA") || n.contains("LANCHONETE") || n.contains("CAFE") || n.contains("CAFÉ") || n.contains("SORVETERIA") || n.contains("ACAI") || n.contains("AÇAÍ") || n.contains("JUICE BAR")) {
            return "Alimentação";
        }
        if (n.contains("MERCADO") || n.contains("SUPERMERC") || n.contains("ATACADO")) return "Alimentação";

        // Fallback de assinaturas
        if (n.contains("STREAM") || n.contains("SUBSCRIPTION") || n.contains("ASSINAT")) return "Assinaturas";

        if (n.contains("HOSPITAL") || n.contains("CLINICA") || n.contains("CONSULTA") || n.contains("MEDIC")) return "Saúde";
        if (n.contains("INTERNET")) return "Internet";
        if (n.contains("TELEFONE") || n.contains("CELULAR")) return "Celular";
        if (n.contains("ALUGUEL") || n.contains("RENT")) return "Aluguel";
        if (n.contains("AGUA")) return "Água";
        if (n.contains("ENERGIA") || n.contains("LUZ")) return "Luz";

        return "Outros";
    }

    private static String normalize(String input) {
        String noAccents = Normalizer.normalize(input, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");

        // Uppercase primeiro
        String upper = noAccents.toUpperCase();

        // Remove prefixos comuns de adquirência/marketplace para facilitar substring match
        // (ex.: EC*ADIDAS, MP GALAPAGOS, MP*GALAPAGOS)
        upper = upper.replaceAll("\\bEC\\s*\\*", "")
                .replaceAll("\\bMP\\s*\\*", "")
                .replaceAll("\\bMP\\s+", "");

        // Normaliza pontuação para espaço (H&M -> H M; C&C -> C C)
        upper = upper.replaceAll("[^A-Z0-9 ]", " ");

        return upper.replaceAll("\\s+", " ").trim();
    }

    private static String compact(String normalized) {
        if (normalized == null) return "";
        return normalized.replace(" ", "");
    }

    private static boolean containsAny(String normalized, String compact, String... needles) {
        if (needles == null || needles.length == 0) return false;
        String n = normalized == null ? "" : normalized;
        String c = compact == null ? "" : compact;

        for (String needle : needles) {
            if (needle == null || needle.isBlank()) continue;
            String nn = normalize(needle);
            if (!nn.isEmpty() && n.contains(nn)) return true;
            String nnCompact = compact(nn);
            // Evita falsos positivos com tokens curtos (ex.: "NB" dentro de "AMAZONBR")
            if (nnCompact.length() > 2 && c.contains(nnCompact)) return true;
        }
        return false;
    }
}
