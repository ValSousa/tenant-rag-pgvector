# Revisão de código — RF-001

- **Data:** 2026-10-01 (revisão inicial); 2026-10-01 (reconferência, depois do RF-002 ao RF-004); 2026-10-05 (reconferência depois do teste manual CT-116)
- **Escopo:** alterações não commitadas sobre o commit `997f680` (branch `feature/ingestao-documentos`): `pom.xml`, `application.properties`, `.gitignore`, `docker-compose.yml`, `docker/init.sql`, `TenantRagPgvectorApplicationIT` (antes `TenantRagPgvectorApplicationTests`), `SchemaMigrationIT` (parte CT-001) e `support/` (`PostgresTestcontainersConfig`, `AbstractIntegrationTest`), `application-test.properties`; na reconferência de 2026-10-05, também `.env.example` (só a existência e o `.gitignore`; o conteúdo não pôde ser lido, ver abaixo) e a importação do `.env` em `application.properties`
- **Tarefas cobertas:** T-101, T-102, T-103, T-106, T-107 (parte do RF-001); card HU-001 (testes CT-001, CT-002, CT-003, CT-116)
- **Revisores:** Claude Code (revisão manual + `/code-review`; reconferência pelo agente revisor-de-codigo), a pedido do responsável pelo projeto
- **Resultado:** nenhum defeito grave. Os 6 achados da primeira revisão continuam corrigidos (conferido no código em 2026-10-01). A reconferência encontrou 2 pontos novos (baixa/mínima): o CT-002 não confere a parte "nenhuma chamada à OpenAI" do cenário, e os documentos de QA ainda citam o nome antigo da classe. Nenhum impede o fechamento. Reconferência de 2026-10-05: o que mudou desde a aprovação (credenciais do banco saíram do `docker-compose.yml` e do `application.properties` para o `.env` local, fora do git) está correto e reforça a RN-02; nenhum achado novo no código; o #7 (Baixa) continua aberto; uma pendência de documentação para o `arquiteto-de-software` (seção 5 da arquitetura 06 ainda mostra `rag/rag` fixo no compose).
- **Aprovado para fechamento:** Sim — nenhum achado Alta ou Média aberto; o único aberto (#7) é de gravidade Baixa; o CT-116 (manual) e os automáticos CT-001 a CT-003 estão OK.

## Achados

| # | Gravidade | Onde | Problema | Correção sugerida | Status |
|---|---|---|---|---|---|
| 1 | Média | Execução dos testes | A execução das 15:01 falhou antes de começar: a JVM dos testes não conseguiu memória para iniciar (`Failed to allocate initial concurrent mark overflow mark stack`, em `target/surefire-reports/2026-10-01T15-01-19_682-jvmRun1.dumpstream`). A evidência registrada ([execucoes/2026-10-01-997f680.md](../QA/evidencias/execucoes/2026-10-01-997f680.md), 14:18) passou, então o build está instável nesta máquina. O `-javaagent` do Mockito resolveu o processo extra, mas não a memória da própria JVM dos testes. | Limitar a memória dos testes no `argLine` do Surefire: `-Xmx384m -XX:+UseSerialGC` (aplicado com 384 MB) | Corrigido (RF-002) |
| 2 | Baixa | `pom.xml:128` | A versão do `jacoco-maven-plugin` não está fixada, e o Spring Boot 4.1.1 não a define. O Maven usa a mais recente disponível (hoje 0.8.15), que pode mudar entre execuções. | Declarar `<version>0.8.15</version>` | Corrigido (RF-002) |
| 3 | Baixa | `pom.xml:119` | O `argLine` começa com `@{argLine}`, que só recebe valor quando o `prepare-agent` do JaCoCo roda. Com `-Djacoco.skip=true`, o texto fica sem substituição e a JVM dos testes não inicia (`Could not find or load main class @{argLine}`). | Declarar `<argLine></argLine>` vazio em `<properties>` | Corrigido (RF-002) |
| 4 | Baixa | `src/test/java/br/com/rag_pgvector/support/PostgresTestcontainersConfig.java:22` | `MountableFile.forHostPath("docker/init.sql")` é resolvido a partir da pasta onde a JVM roda. Pelo Maven funciona (pasta do módulo). Pela IDE, ou a partir da pasta acima (onde está o `CLAUDE.md`), o arquivo não é encontrado e o contêiner não sobe. | Montar o caminho a partir da propriedade `basedir`, ou carregar o arquivo pelo classpath | Corrigido (RF-002: `forClasspathResource`, com o `pom.xml` copiando o `init.sql` para o classpath de teste) |
| 5 | Baixa | `src/test/java/br/com/rag_pgvector/TenantRagPgvectorApplicationTests.java` | O CT-002 precisa de Docker (estende `AbstractIntegrationTest`), mas a classe usa o sufixo `*Tests`. Pela tabela de níveis do [QA 01](../QA/01%20Estrategia%20de%20testes.md), `*Test`/`*Tests` é teste unitário, sem banco; integração usa `*IT`. | Renomear para `*IT` (ex.: `ApplicationContextIT`) ou registrar a exceção no QA 01 | Corrigido (renomeada para `TenantRagPgvectorApplicationIT`) |
| 6 | Mínima | `pom.xml:85` | `testcontainers-junit-jupiter` está declarado, mas não é usado (não há `@Testcontainers`; o contêiner é um bean com `@ServiceConnection`). | Remover, ou manter se for usado depois | Corrigido (dependência removida do `pom.xml`) |
| 7 | Baixa | `src/test/java/br/com/rag_pgvector/TenantRagPgvectorApplicationIT.java:11` | O [CT-002](../QA/05%20Cenarios%20de%20teste.md) pede "nenhuma chamada é feita à OpenAI (`FakeEmbeddingModel.chamadas()` = 0)", mas o teste é vazio (só sobe o contexto). Desde o RF-004 o contexto tem o `OpenAiEmbeddingModel` real ao lado do fake; nada garante que o `EmbeddingService` recebe o fake. Observação: o `@BeforeEach` da classe base zera `chamadas()` antes do teste, então só olhar o contador não pega chamadas feitas na subida. | Injetar o `EmbeddingModel` que o contexto resolve e conferir `isInstanceOf(FakeEmbeddingModel.class)`, e conferir `fakeEmbeddings.chamadas()` igual a 0 | Aberto (reconferido em 2026-10-05: o teste continua vazio; desde o RF-008 vale o mesmo para o `ChatModel`/`FakeChatModel`) |
| 8 | Mínima | `docs/QA/05 Cenarios de teste.md:27`, `docs/QA/06 Rastreabilidade.md:198` | Os documentos de QA ainda citam `TenantRagPgvectorApplicationTests`, nome anterior ao achado 5. | Trocar para `TenantRagPgvectorApplicationIT` (tarefa do analista de QA) | Corrigido (2026-10-01) |

## Resumo por área

| Área | Situação | Justificativa |
|---|---|---|
| Arquitetura | Aderente | Compose, `init.sql` e Testcontainers usam a mesma imagem e o mesmo script; o código segue a ADR-012; a divergência é só no texto da arquitetura 06 (pendência abaixo). |
| Qualidade (análise estática) | Aderente | `pom.xml` com versões definidas e sem dependência sobrando; arquivos de suporte sem import não usado, `TODO` ou código comentado. |
| Segurança | Nenhum problema identificado | Nenhuma credencial versionada: `DB_USER`/`DB_PASSWORD` sem valor padrão, `.env` no `.gitignore`; o conteúdo do `.env.example` fica a confirmar (leitura bloqueada nesta sessão). |
| Testes | Necessita ajustes | CT-001 e CT-002 passando e CT-003/CT-116 OK; o CT-002 não confere a ausência de chamada à OpenAI (achado #7, Baixa, não impeditivo). |

## Pendências

### Pendência para o RF-002

A migration `V1__create_tables.sql` precisa repetir `CREATE EXTENSION IF NOT EXISTS vector`, como pede [04 Modelo de dados](../arquitetura/04%20Modelo%20de%20dados.md) (linha 109). Hoje o CT-001 só passa por causa do `docker/init.sql`. Não é falha do RF-001.

Situação em 2026-10-01: resolvida, a `V1__create_tables.sql` tem o `CREATE EXTENSION IF NOT EXISTS vector` na linha 3.

### Pendência para o arquiteto-de-software (registrada em 2026-10-05)

A [arquitetura 06](../arquitetura/06%20Integracoes%20e%20configuracao.md), seção 5, ainda mostra o `docker-compose.yml` com `POSTGRES_USER: rag`, `POSTGRES_PASSWORD: rag` e `pg_isready -U rag`, e a seção 6 diz que usuário e senha do banco ficam no `application-dev.properties`. O código atual lê `DB_USER`/`DB_PASSWORD` do `.env` local (`${DB_USER:?...}` no compose e `spring.config.import=optional:file:.env[.properties]` no `application.properties`), como já descreve o guia [Como executar](../guias/Como%20executar.md). O RF-001 (DP-02 e "Situação atual") também cita `rag/rag` fixo. É desatualização de documento, não defeito do código: atualizar a arquitetura 06 (e, pelo analista de requisitos, o texto do RF-001).

## Reconferência de 2026-10-05

Motivo: o card HU-001 voltou de Concluído para Em teste, fase Manual, em 2026-10-02 (decisão do usuário) só para aguardar o teste manual CT-116, executado e registrado como OK em 2026-10-05 ([evidência](../QA/evidencias/CT-116/2026-10-05.md)). O card está Em revisão.

O que foi conferido no código atual:

- `docker-compose.yml`: mesma imagem `pgvector/pgvector:pg17`, banco `ragdb`, porta 5432, volume `pgdata` (RN-03), montagem do `docker/init.sql` e healthcheck. Mudança desde a aprovação: `POSTGRES_USER`/`POSTGRES_PASSWORD` vêm de `${DB_USER:?...}`/`${DB_PASSWORD:?...}` (o compose para com mensagem clara se faltarem) e o healthcheck usa `$${POSTGRES_USER}`. Correto; atende a RN-02 e o CA-01 (confirmado no CT-116: `rag-postgres` healthy).
- `docker/init.sql`: inalterado (`CREATE EXTENSION IF NOT EXISTS vector;`); CA-02 confirmado no CT-116 (`vector 0.8.6`).
- `application.properties`: `spring.config.import=optional:file:.env[.properties]` (o `.env` é opcional e as variáveis de ambiente têm prioridade); datasource com `${DB_URL:...}`, `${DB_USER}` e `${DB_PASSWORD}` sem valor padrão; `ddl-auto=validate`, `open-in-view=false`, Flyway ligado. Nenhuma senha ou chave versionada. CA-03 confirmado no CT-116 (Flyway e Hibernate em `jdbc:postgresql://localhost:5432/ragdb`, `Started`).
- `.gitignore`: ignora `.env`, `application-dev.properties` e `*.log`; libera `.env.example` (`git check-ignore` confirma que `.env` e os `hs_err_pid*.log` da raiz, restos da falha de memória do achado #1, ficam fora do git).
- `.env.example`: o arquivo existe e será versionado, mas a leitura está bloqueada pelas permissões desta sessão (regra de bloqueio do `.env*`). Que ele só tem valores de exemplo fica **a confirmar** por uma pessoa; o guia Como executar mostra o modelo com `DB_USER=rag`, sem chave real.
- `pom.xml`: sem mudança no que toca ao RF-001 desde a reconferência de 2026-10-01 (JaCoCo 0.8.15, `<argLine></argLine>`, `argLine` com Mockito e `-Xmx384m -XX:+UseSerialGC`, `init.sql` copiado para o classpath de teste, Testcontainers 2 sem `testcontainers-junit-jupiter`).
- `PostgresTestcontainersConfig` e `AbstractIntegrationTest`: mesma imagem e mesmo `init.sql` (`forClasspathResource`); base comum a todos os `*IT` com `FakeEmbeddingModel` e `FakeChatModel` (`AiTestConfig`, `@Primary`) e `TRUNCATE` antes de cada teste. `application-test.properties` só com valores falsos (agora também `spring.datasource.username/password=test`, só para não deixar placeholder sem valor; o `@ServiceConnection` substitui a conexão).
- Testes: relatórios do Surefire de 2026-10-02 19:59 (nenhum fonte, `pom.xml`, `docker/` ou `docker-compose.yml` mais novo que eles): `SchemaMigrationIT` 12 de 12 (CT-001 incluído) e `TenantRagPgvectorApplicationIT` 1 de 1 (CT-002). Testes não reexecutados nesta reconferência.

Achados anteriores: #1 a #6 e #8 continuam corrigidos; #7 continua aberto (Baixa). Nenhum achado novo no código.

Conferência complementar (2026-10-05, depois da interrupção da primeira passada): o documento foi conferido de novo contra `docker-compose.yml`, `docker/init.sql`, `application.properties`, `application-test.properties`, `.gitignore`, `pom.xml`, `PostgresTestcontainersConfig`, `AbstractIntegrationTest` e `TenantRagPgvectorApplicationIT`; tudo bate com o registrado acima. Nenhum desses arquivos é mais novo que os relatórios do Surefire de 2026-10-02 19:59. Os dois itens antigos de "Pontos conferidos sem problema" que citavam `rag/rag` e o padrão de `DB_USER`/`DB_PASSWORD` receberam nota de situação. O conteúdo do `.env.example` continua **a confirmar** (leitura bloqueada pelas permissões; não impede o fechamento, porque o arquivo é só modelo e a regra do projeto é não ter chave real nele).

Veredito: aprovado para fechamento. Card HU-001 movido de Em revisão para Concluído em 2026-10-05.

## Pontos conferidos sem problema

- `maven-dependency-plugin:properties` expõe `${org.mockito:mockito-core:jar}` para o `-javaagent` do Surefire.
- O Flyway sem pasta de migrations só registra o aviso "No migrations found" e não impede a subida.
- `ddl-auto=validate` passa porque ainda não há entidades.
- Os IDs CT-001 e CT-002 nos `@DisplayName` batem com o [catálogo de cenários](../QA/05%20Cenarios%20de%20teste.md).
- `docker-compose.yml` e o Testcontainers usam a mesma imagem (`pgvector/pgvector:pg17`) e o mesmo `docker/init.sql`.
- Nenhum teste chama a OpenAI; a tag `openai` fica excluída por padrão.
- Reconferência (2026-10-01): `argLine` com `@{argLine}`, agente do Mockito, `-Xmx384m -XX:+UseSerialGC`; `<argLine></argLine>` vazio em `<properties>`; JaCoCo 0.8.15 fixado; `init.sql` copiado para o classpath de teste e montado com `forClasspathResource`; `testcontainers-junit-jupiter` removido; classe renomeada para `*IT`.
- `docker-compose.yml` e `docker/init.sql` iguais à [arquitetura 06](../arquitetura/06%20Integracoes%20e%20configuracao.md), seção 5: volume `pgdata` (RN-03), healthcheck, credenciais `rag/rag` só de uso local. (Situação em 2026-10-05: as credenciais `rag/rag` fixas saíram do compose e vêm do `.env`; o texto da arquitetura 06 ficou desatualizado, ver a pendência para o `arquiteto-de-software`.)
- `application.properties`: datasource sobrescrevível por `DB_URL`, `DB_USER` e `DB_PASSWORD` (RN-02), `ddl-auto=validate`, `open-in-view=false`; nenhum segredo real versionado (as chaves de OpenAI e administrador vêm de variável de ambiente, sem valor padrão). (Situação em 2026-10-05: `DB_USER` e `DB_PASSWORD` também ficaram sem valor padrão; só o `DB_URL` mantém o padrão `jdbc:postgresql://localhost:5432/ragdb`.)
- `application-test.properties` só tem valores falsos (`test-key-nao-usada`, `test-admin-key`).
- `AbstractIntegrationTest` limpa as três tabelas com `TRUNCATE ... RESTART IDENTITY CASCADE` e zera o `FakeEmbeddingModel` antes de cada teste; todas as subclasses usam a mesma configuração, então o contexto e o contêiner são reaproveitados.
- Relatórios do Surefire (15:36, mais novos que todos os fontes): 54 testes, 0 falhas; CT-001 e CT-002 passando.
- Reconferência (2026-10-05): credenciais do banco fora de arquivo versionado (`.env` local ignorado pelo git, compose e `application.properties` sem valor padrão); CA-01, CA-02 e CA-03 confirmados pelo CT-116.

**Próximo responsável:** `gerente-de-release` (aprovado).
