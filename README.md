# ELLA Backend

API do ELLA (assistente financeira) construída em Spring Boot 3.5/Java 21, com segurança via JWT, persistência PostgreSQL e migrações Flyway.

## Stack

- Java 21, Spring Boot 3.5 (Web MVC, Data JPA, Security, Validation, Cache, AOP)
- PostgreSQL + Flyway (migrações em `src/main/resources/db/migration`)
- Springdoc OpenAPI UI (Swagger) para documentação interativa
- JJWT para tokens de acesso/refresh
- PDFBox para leitura/manipulação de PDFs (uploads)
- Testes: Spring Boot Test, Spring Security Test, Reactor Test

## Estrutura de pastas (alto nível)

- `src/main/java/com/ella/backend`
  - `controllers/` – endpoints REST (dashboard, auth, transações, etc.)
  - `services/` – regras de negócio (charts, transações, segurança, etc.)
  - `repositories/` – interfaces JPA
  - `entities/` – modelos persistentes
  - `dto/` – contratos de entrada/saída
  - `security/` – filtros, JWT, configurações CORS/authz
  - `audit/` – captura de eventos e logs de auditoria
- `src/main/resources`
  - `application.properties` – configuração default
  - `db/migration` – scripts Flyway (`V2__create_audit_events_table.sql`, `V3__add_transaction_scope.sql`)
- `pom.xml` – dependências e plugins (maven-compiler, spring-boot)

## Requisitos

- Java 21+
- Maven 3.9+
- PostgreSQL 14+ (database criada; padrão `ella`)

## OCR (PDF escaneado / imagem)

Por padrão, o upload tenta extrair texto via **PDFBox**. Quando o PDF é escaneado (ou seja, contém apenas imagem), o texto extraído pode vir vazio/curto. Para esses casos, o backend suporta **fallback OCR** via **Tesseract (Tess4J)**.

### Pré-requisito (Windows)

- Instale o **Tesseract OCR** no Windows (qualquer distribuição confiável).
- Garanta que:
  - o executável do Tesseract esteja disponível no PATH **ou** que o ambiente esteja configurado para encontrar os dados do modelo (`tessdata`), e
  - o idioma desejado exista em `tessdata` (ex.: `por.traineddata` para português).

> Dica: se você tiver problemas de `tessdata`, prefira apontar explicitamente o caminho via `ella.ocr.tessdata-path`.

### Como habilitar

No `application.properties` (ou via variáveis de ambiente):

```properties
# habilita OCR fallback (default: false)
ella.ocr.enabled=true

# idioma(s) do tesseract (ex.: por, eng, por+eng)
ella.ocr.language=por

# opcional: caminho que CONTÉM a pasta "tessdata"
# exemplo (Windows): C:\Program Files\Tesseract-OCR
ella.ocr.tessdata-path=

# se o texto extraído via PDFBox vier menor que isso, o OCR é tentado
ella.ocr.pdf.min-text-length=200

# qualidade do render para OCR (mais alto = mais lento, mais preciso)
ella.ocr.pdf.render-dpi=220

# limite de páginas processadas por OCR (proteção de performance)
ella.ocr.pdf.max-pages=6
```

Equivalente via env vars:

```bash
set ELLA_OCR_ENABLED=true
set ELLA_OCR_LANGUAGE=por
set ELLA_OCR_TESSDATA_PATH=C:\\Program Files\\Tesseract-OCR
set ELLA_OCR_PDF_RENDER_DPI=220
set ELLA_OCR_PDF_MAX_PAGES=6
```

### Como funciona (resumo)

- Primeiro: `PDFBox` tenta extrair texto do PDF.
- Se `ella.ocr.enabled=true` e o texto vier vazio/curto: renderiza páginas em memória (`BufferedImage`) e roda OCR.
- O OCR é **thread-safe** no backend: cada thread usa sua própria instância de Tesseract (`ThreadLocal`).

## Configuração rápida

1. Defina variáveis de ambiente (recomendado) ou use profile `local` + `.env`:

- `DB_PASSWORD` (obrigatória)
- `JWT_SECRET` (obrigatória)
- `DB_URL` (opcional; default `jdbc:postgresql://localhost:5432/ella`)
- `DB_USERNAME` (opcional; default `postgres`)
- `jwt.expiration` (ms, default `900000` = 15min)
- `jwt.refreshExpiration` (ms, default 7 dias)
- `cors.allowedOrigins` (default `http://localhost:3000,http://localhost:5173,http://localhost:5174`)

2. Certifique-se de que o banco exista (`ella`) e o usuário tenha permissão.
3. Rode as migrações automaticamente ao subir (Flyway executa no start).

## Executar em desenvolvimento

```bash
# na pasta backend
mvn spring-boot:run
```

A API sobe em `http://localhost:8080` (porta padrão Spring Boot).

## E-mail (Resend)

- A chave da Resend deve ser configurada via variável de ambiente `RESEND_API_KEY`.
- No `application.properties` já está parametrizado como `email.resend.api-key=${RESEND_API_KEY:}` (não coloque a chave diretamente no arquivo).

### Local (usando `.env`)

Se quiser guardar variáveis localmente sem usar `setx`, crie `backend/.env` a partir de `backend/.env.example` (o arquivo `.env` fica ignorado pelo git).

Para o Spring carregar `.env` automaticamente, ative o profile `local` (isso usa `spring.config.import` e não depende de loader customizado):

- PowerShell (exemplo): `setx SPRING_PROFILES_ACTIVE local`
- ou execute com profile: `mvn -Dspring-boot.run.profiles=local spring-boot:run`

PowerShell (alternativa):

- `Set-Location backend`
- `./tools/load-env.ps1`
- `./mvnw.cmd -DskipTests -Dspring-boot.run.profiles=local spring-boot:run`

### Deploy/produção

No deploy, configure `RESEND_API_KEY` nas variáveis de ambiente do provedor (Docker/Render/Railway/VM/etc.). Não envie arquivo `.env` no repositório.

## Build e testes

```bash
mvn clean test          # roda a suíte de testes
mvn clean package       # gera o jar em target/
```

## Documentação / Swagger

Com a aplicação rodando, acesse `http://localhost:8080/swagger-ui.html` (via springdoc-openapi-starter).

## Segurança (JWT)

- Geração/validação via JJWT (`jwt.secret`).
- Tokens de acesso com expiração curta (`jwt.expiration`), refresh controlado por `jwt.refreshExpiration`.
- Endpoints protegidos usam Spring Security + PreAuthorize; ver controllers para regras de acesso (ex.: `@PreAuthorize("hasRole('ADMIN') or @securityService.canAccessPerson(#personId)")`).

## Migrações Flyway

- Scripts em `src/main/resources/db/migration` executam na inicialização.
- `V2__create_audit_events_table.sql` cria tabela de auditoria (`audit_events`).
- `V3__add_transaction_scope.sql` adiciona coluna `scope` em `financial_transactions`.

## Troubleshooting rápido

- **Erro de conexão no start**: confirme `spring.datasource.*` e se o Postgres está rodando.
- **Flyway validation failed**: cheque se a base tem migrações manuais fora da pasta ou rode `flyway repair` (via CLI) após avaliar impacto.
- **CORS bloqueado**: ajuste `cors.allowedOrigins` com a origem do frontend.

## Comandos úteis

- `mvn dependency:tree` – inspeciona dependências.
- `mvn -DskipTests spring-boot:run` – sobe rápido ignorando testes.
- `SPRING_PROFILES_ACTIVE=prod mvn spring-boot:run` – ativa profile (se existir configs específicas).

## Notas

- `spring.jpa.hibernate.ddl-auto=validate` exige que o schema exista e esteja alinhado às entidades; migrações criam/ajustam o schema.
- Para produção, sempre defina `JWT_SECRET` e `DB_PASSWORD` via variáveis de ambiente ou system properties.
- Para exigir configuração completa de e-mail na inicialização (fail-fast), use `EMAIL_REQUIRE_CONFIG_ON_STARTUP=true`.
