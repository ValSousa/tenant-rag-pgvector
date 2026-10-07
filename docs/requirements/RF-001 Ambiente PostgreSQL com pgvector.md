# RF-001 — Ambiente PostgreSQL com pgvector

## Objetivo

Disponibilizar localmente um PostgreSQL com a extensão pgvector, via Docker Compose, e conectar a aplicação a ele, para que embeddings possam ser armazenados e consultados.

## Descrição

O README define PostgreSQL + pgvector como banco (seção 2), prevê os arquivos `docker-compose.yml` e `docker/init.sql` (seção 3) e o comando `docker compose up -d` para subir o banco (seção 4). Nenhum desses itens existe hoje, e o `pom.xml` não tem dependências de acesso a banco. Este requisito é a base de todos os demais.

## Atores

- Desenvolvedor (sobe o ambiente e executa a aplicação).
- Aplicação Spring Boot (conecta ao banco).

## Pré-condições

- Docker e Docker Compose instalados.
- Porta do PostgreSQL disponível na máquina.

## Fluxo principal

1. O desenvolvedor executa `docker compose up -d` na raiz do projeto.
2. O Docker Compose sobe um contêiner PostgreSQL com a extensão pgvector disponível.
3. Na primeira inicialização, `docker/init.sql` executa `CREATE EXTENSION IF NOT EXISTS vector;`.
4. O desenvolvedor executa `mvnw spring-boot:run`.
5. A aplicação lê as propriedades de conexão em `application.properties` e conecta ao banco.
6. A aplicação sobe sem erros.

## Fluxos alternativos

- FA-01 — Porta ocupada: o contêiner não sobe; o desenvolvedor altera o mapeamento de porta no `docker-compose.yml` e na URL de conexão.
- FA-02 — Banco fora do ar: a aplicação falha na inicialização com erro de conexão; o desenvolvedor sobe o banco e executa de novo.
- FA-03 — Imagem sem pgvector: a criação da extensão falha; usar uma imagem que inclua pgvector.

## Regras de negócio

- RN-01 — A extensão `vector` deve estar habilitada antes de qualquer migration criar colunas `VECTOR`.
- RN-02 — Usuário, senha e URL do banco ficam em propriedades (com possibilidade de sobrescrever por variável de ambiente), nunca no código Java.
- RN-03 — Os dados do banco ficam em volume Docker, para sobreviverem a reinícios do contêiner.

## Dados de entrada

| Campo | Tipo | Obrigatório |
|---|---|---|
| POSTGRES_DB | texto (variável do contêiner) | Sim |
| POSTGRES_USER | texto (variável do contêiner) | Sim |
| POSTGRES_PASSWORD | texto (variável do contêiner) | Sim |
| spring.datasource.url | texto (JDBC URL) | Sim |
| spring.datasource.username | texto | Sim |
| spring.datasource.password | texto | Sim |

## Dados de saída

- Banco PostgreSQL acessível na porta configurada, com a extensão `vector` instalada.

## Critérios de aceite

- CA-01 — `docker compose up -d` sobe o banco sem erros.
- CA-02 — `SELECT extname FROM pg_extension;` retorna `vector`.
- CA-03 — `mvnw spring-boot:run` inicia a aplicação conectada ao banco.
- CA-04 — `mvnw test` continua passando (ver decisão DP-03).

## Dependências

- Nenhuma.

## Situação atual do projeto

Atualizado em 2026-10-01, após a implementação (T-101 a T-103):

- `docker-compose.yml` com o serviço `postgres` (`pgvector/pgvector:pg17`, banco `ragdb`, volume `pgdata`, healthcheck) e `docker/init.sql` criando a extensão `vector`.
- `pom.xml` com JPA, Flyway, driver PostgreSQL e Testcontainers 2; Surefire roda `*Test`, `*Tests` e `*IT` e exclui a tag `openai`; JaCoCo gera relatório no `verify`; agente do Mockito carregado na JVM dos testes.
- `application.properties` com datasource (sobrescrevível por `DB_URL`, `DB_USER`, `DB_PASSWORD`), JPA `validate` e Flyway.
- Testes: `SchemaMigrationIT` (CT-001) e `TenantRagPgvectorApplicationIT` (CT-002) passando contra PostgreSQL + pgvector do Testcontainers; CT-003 executado manualmente (pgvector 0.8.6, PostgreSQL 17.11).
- `application-test.properties` (perfil `test`) criado no RF-004, com a chave falsa da OpenAI e a chave de administrador de teste (T-103 concluída).

## Itens a implementar

- `docker-compose.yml` com o serviço do PostgreSQL + pgvector, portas, variáveis e volume.
- `docker/init.sql` criando a extensão `vector`.
- Dependências no `pom.xml`: `spring-boot-starter-data-jpa` e driver `org.postgresql:postgresql`.
- Propriedades `spring.datasource.*` em `application.properties` (o projeto usa `.properties`, não o `application.yml` citado no README).

## Decisões pendentes

- DP-01 — Imagem e versão do PostgreSQL. **Definido na arquitetura (06):** `pgvector/pgvector:pg17`.
- DP-02 — Nome do banco, usuário e senha de desenvolvimento. **Definido na arquitetura (06):** `ragdb` / `rag` / `rag`, só para uso local.
- DP-03 — Banco do `mvnw test`. **Decidido:** Testcontainers com a imagem pgvector; `mvnw test` precisa do Docker rodando, mas não do `docker compose up` (ADR-012). O contêiner de teste recebe o mesmo `docker/init.sql` do compose.

## Cenários de teste (QA)

Antes de implementar, leia os cenários abaixo em [05 Cenarios de teste.md](../QA/05%20Cenarios%20de%20teste.md) e implemente os automáticos com o ID no `@DisplayName`; os marcados como manual são executados por uma pessoa pelo roteiro em [07 Testes manuais.md](../QA/07%20Testes%20manuais.md). O requisito só está pronto com todos passando ([definição de pronto](../QA/01%20Estrategia%20de%20testes.md)).

- CT-001 (P2) — Extensão `vector` habilitada no banco
- CT-002 (P2) — Aplicação sobe com o contexto completo
- CT-003 (P3, manual) — Ambiente local com docker compose
- CT-116 (P2, manual) — Banco e aplicação sobem pelo console da IDE

## Prioridade

Alta — Faz parte da primeira entrega (pergunta e resposta por cliente): o usuário decidiu em 02/10/2026 (RF-008 DP-06) que todas as funcionalidades RF-001 a RF-012 entram nela.

## Status

- Status: Concluído — entregue na v0.1.0 (Produção, 2026-10-06).
- Prontidão: implementado em 2026-10-01 (T-101, T-102, T-103); CT-001, CT-002 e CT-003 OK. Falta a revisão; a etapa 1 fecha com o RF-002.
- 2026-10-01: T-103 concluída com o `application-test.properties` (RF-004); CT-001 e CT-002 continuam passando ([evidência](../QA/evidencias/execucoes/2026-10-01-rf-003-rf-004.md)). Revisão de código feita ([RF-001 Revisao de codigo](../revisao/RF-001%20Revisao%20de%20codigo.md)); os 6 achados estão corrigidos.
