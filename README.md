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

## Configuração rápida

1. Copie `src/main/resources/application.properties` ou defina variáveis de ambiente:
   - `spring.datasource.url` (default `jdbc:postgresql://localhost:5432/ella`)
   - `spring.datasource.username` (default `postgres`)
   - `spring.datasource.password` (default `123456`)
   - `jwt.secret` (valor default presente no properties; ideal trocar em produção)
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
- Para produção, sempre sobrescreva `jwt.secret`, `spring.datasource.*` e `cors.allowedOrigins` via variáveis de ambiente ou system properties.
