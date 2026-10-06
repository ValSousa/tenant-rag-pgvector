# 01 — Estratégia de testes

Documento de QA do projeto. Complementa [08 Estrategia de testes.md](../arquitetura/08%20Estrategia%20de%20testes.md) da arquitetura: lá estão as decisões técnicas (ADR-012); aqui estão o plano de testes, os riscos, os critérios de aceite de qualidade e a forma de trabalhar.

## 1. Objetivo

Garantir, com testes automatizados repetíveis, que:

1. **uma consulta do Cliente A nunca devolve dado do Cliente B** (README, seção 12);
2. a API cumpre os critérios de aceite dos requisitos RF-001 a RF-012;
3. o projeto pode evoluir (trocar modelo, atualizar o LangChain4j beta) sem regressão silenciosa.

## 2. Escopo

| Dentro do escopo | Fora do escopo |
|---|---|
| Regras de negócio dos services | Testes de carga e desempenho |
| Extração de PDF e chunking | Testes de interface (não há UI; o Swagger é só ferramenta) |
| SQL gerado pelo `PgVectorEmbeddingStore` e filtro por cliente | Qualidade dos modelos da OpenAI em si |
| Segurança por chave de API (401/403) | Testes de penetração |
| Formato de erro (`ProblemDetail`) | Compatibilidade com outros bancos |
| Migrations Flyway | Deploy e infraestrutura de nuvem |
| Qualidade das respostas RAG com os documentos de exemplo (opt-in, manual) | |

## 3. Riscos e prioridades

A prioridade dos cenários (P1 a P3) vem dos riscos abaixo.

| ID | Risco | Impacto | Probabilidade | Prioridade | Mitigação principal |
|---|---|---|---|---|---|
| R1 | Busca devolve chunk de outro cliente | Crítico (vazamento de dados) | Baixa | **P1** | `TenantIsolationIT` com chunk **idêntico** no outro cliente (CT-110 a CT-114) |
| R2 | Chave de um cliente acessa rota de outro | Crítico | Média | **P1** | Matriz de segurança DDT (CT-080) |
| R3 | Ingestão grava documento sem chunks (gravação parcial) | Alto | Média (store usa conexão própria) | **P1** | Teste de rollback com falha no `addAll` (CT-056) |
| R4 | Atualização do `langchain4j-pgvector` (beta) muda o SQL ou o filtro | Alto | Média | **P1** | `ChunkRepositoryIT` e `TenantIsolationIT` rodando contra o store real |
| R5 | Chunk gravado com `client_id` diferente do documento | Alto | Baixa | **P1** | FK composta + CT-013 |
| R6 | Erro expõe stack trace ou dado interno | Médio | Média | P2 | CT-092 |
| R7 | Respostas do modelo inventam valores | Médio | Média | P2 | Prompt + teste opt-in com gabarito (CT-075, CT-076) |
| R8 | Testes chamam a OpenAI sem querer (custo, instabilidade) | Médio | Baixa | P2 | `FakeEmbeddingModel` `@Primary`, `RagAssistant` mockado, tag `openai` excluída por padrão |
| R9 | Chunking corta valores do seu rótulo ("franquia" / "R$ 3.500") | Médio | Média | P3 | Ajuste de `chunk-size` com documentos de exemplo (CT-075) |

**Regra:** nenhum merge com cenário P1 falhando ou desabilitado.

## 4. Níveis de teste

```text
            ┌────────────────────────┐
            │  Opt-in / manual        │  OpenAI real, gabarito (poucos)
            ├────────────────────────┤
            │  Integração (*IT)       │  Testcontainers + store real
            ├────────────────────────┤
            │  Web slice (*Test)      │  MockMvc, segurança, erros
            ├────────────────────────┤
            │  Unitário (*Test)       │  Mockito, rápidos (maioria)
            └────────────────────────┘
```

| Nível | Ferramentas | Sufixo | Roda no `mvnw test`? | Banco | OpenAI | Documento |
|---|---|---|---|---|---|---|
| Unitário | JUnit 5, Mockito, AssertJ | `*Test` | Sim | Não | Não | [02](02%20Testes%20unitarios.md) |
| Web slice | `@WebMvcTest`, MockMvc, `@MockitoBean`, `spring-security-test` | `*ControllerTest`, `*SecurityTest` | Sim | Não | Não | [02](02%20Testes%20unitarios.md), seção 4 |
| Integração | `@SpringBootTest`, Testcontainers `pgvector/pgvector:pg17` | `*IT` | Sim | Contêiner temporário | Não (fake) | [03](03%20Testes%20de%20integracao.md) |
| Orientado a dados (DDT) | `@ParameterizedTest` + CSV/`@MethodSource` | — (técnica usada nos níveis acima) | Sim | Depende do nível | Não | [04](04%20Testes%20orientados%20a%20dados.md) |
| Opt-in (qualidade RAG) | `@Tag("openai")` | `*OpenAiIT` | **Não** (só com `-Dgroups=openai`, disparado por uma pessoa: execução `Manual`) | `docker compose` ou contêiner | **Sim** | [03](03%20Testes%20de%20integracao.md), seção 9; roteiro em [07](07%20Testes%20manuais.md) |
| Manual / exploratório | Swagger UI, Postman ou curl, log da IDE, `psql` | — | Não | `docker compose` | Sim | [07](07%20Testes%20manuais.md) (roteiros, ao menos um por card quando há algo visível); catálogo em [05](05%20Cenarios%20de%20teste.md) |

Distribuição esperada: cerca de 60% unitários e web, 35% integração, 5% opt-in/manual.

## 5. Ambientes

| Ambiente | Banco | IA | Uso |
|---|---|---|---|
| `mvnw test` (local ou CI) | Testcontainers, criado e destruído a cada execução | `FakeEmbeddingModel` + `RagAssistant` mockado | Todos os testes automatizados obrigatórios |
| Desenvolvimento | `docker compose` (`ragdb`) | OpenAI real (`OPENAI_API_KEY`) | Testes manuais e opt-in |

Pré-requisito único do `mvnw test`: **Docker rodando**. Não precisa de `OPENAI_API_KEY`, internet para IA nem `docker compose up`.

## 6. Dados de teste

- **Fictícios sempre.** Nenhum dado pessoal real (RF-011 RN-01).
- **Criados pelo próprio teste**, via `TestDataBuilder` e `TestPdfFactory` ([03](03%20Testes%20de%20integracao.md), seção 3). Nenhum teste depende de dados deixados por outro.
- **Isolados.** Testes de integração limpam as tabelas antes de cada teste (`TRUNCATE ... RESTART IDENTITY CASCADE`).
- **Determinísticos.** Embeddings vêm do `FakeEmbeddingModel`: mesmo texto → mesmo vetor; vetores "fixos" registrados pelo teste quando a similaridade importa.
- **Massa tabular em CSV** em `src/test/resources/ddt/` ([04](04%20Testes%20orientados%20a%20dados.md)).
- **PDFs de teste gerados em memória** pelo `TestPdfFactory` (PDFBox). Só os 6 PDFs de exemplo (RF-011) ficam em `documents/`.

## 7. Critérios de entrada e saída por etapa do plano

As etapas são as de [09 Plano de implementacao.md](../arquitetura/09%20Plano%20de%20implementacao.md).

| Etapa | Entrada (antes de codificar) | Saída (para considerar pronta) |
|---|---|---|
| 1 Banco e esquema | `PostgresTestcontainersConfig` e `AbstractIntegrationTest` criados | CT-001, CT-002, CT-010 a CT-015 passando |
| 2 Erros e clientes | Matriz de erros e CSV de validação de nome prontos | CT-020 a CT-024, CT-026, CT-090 a CT-093 passando |
| 3 Segurança | `security-matrix.csv` pronto | CT-025, CT-027, CT-080 a CT-085 passando |
| 4 Ingestão | `FakeEmbeddingModel` e `TestPdfFactory` prontos | CT-030 a CT-034, CT-036, CT-040 a CT-045, CT-050 a CT-058 passando |
| 5 Busca e isolamento | `isolation-matrix.csv` pronto | CT-060 a CT-066 e **CT-110 a CT-113** passando — entrega mínima |
| 6 Documentos de exemplo | Gabarito escrito | CT-100 a CT-102 passando |
| 7 Resposta gerada | `gabarito-perguntas.csv` pronto | CT-070 a CT-074, CT-114 passando; CT-075 e CT-076 executados (opt-in) |

## 8. Definição de pronto (qualidade)

Um requisito só está pronto quando:

1. todos os cenários `CT-xxx` listados na seção **"Cenários de teste (QA)"** do RF estão implementados com o ID no `@DisplayName`;
2. `mvnw test` passa sem testes desabilitados (`@Disabled` só com BUG aberto e referência);
3. a cobertura das classes do RF atende a seção 9;
4. os critérios de aceite do RF aparecem na matriz de [06 Rastreabilidade.md](06%20Rastreabilidade.md) com pelo menos um cenário passando.

## 9. Cobertura de código

Ferramenta: JaCoCo (`jacoco-maven-plugin`), relatório em `target/site/jacoco/index.html`.

| Pacote | Meta de linhas | Meta de ramos |
|---|---|---|
| `security`, `repository` (`ChunkRepository`) | 90% | 85% |
| `service`, `ingestion` | 85% | 75% |
| `controller`, `exception` | 80% | 70% |
| Total do projeto | 80% | 70% |
| `config`, `dto`, `entity`, `enums` | sem meta (código declarativo) | — |

Cobertura é indicador, não objetivo: um cenário P1 sem teste é falha mesmo com 100% de linhas.

## 10. Execução

```bash
./mvnw test                                   # todos os obrigatórios (unit + web + IT)
./mvnw test -Dtest=TenantIsolationIT          # uma classe
./mvnw test -Dtest='*IT'                      # só integração
./mvnw test -Dgroups=openai -Dtest.excluded.groups=nenhum   # opt-in: exige OPENAI_API_KEY e gasta tokens
./mvnw verify                                 # testes + relatório JaCoCo
```

Configuração do `pom.xml` (Etapa 1):

```xml
<properties>
  <test.excluded.groups>openai</test.excluded.groups>
</properties>

<plugin>
  <!-- expõe o caminho do jar do Mockito como propriedade -->
  <groupId>org.apache.maven.plugins</groupId>
  <artifactId>maven-dependency-plugin</artifactId>
  <executions><execution><goals><goal>properties</goal></goals></execution></executions>
</plugin>
<plugin>
  <groupId>org.apache.maven.plugins</groupId>
  <artifactId>maven-surefire-plugin</artifactId>
  <configuration>
    <!-- agente do Mockito na própria JVM dos testes; @{argLine} mantém o agente do JaCoCo -->
    <argLine>@{argLine} -javaagent:${org.mockito:mockito-core:jar}</argLine>
    <includes>
      <include>**/*Test.java</include>
      <include>**/*Tests.java</include>
      <include>**/*IT.java</include>
    </includes>
    <excludedGroups>${test.excluded.groups}</excludedGroups>
  </configuration>
</plugin>
<plugin>
  <groupId>org.jacoco</groupId>
  <artifactId>jacoco-maven-plugin</artifactId>
  <executions>
    <execution><goals><goal>prepare-agent</goal></goals></execution>
    <execution><id>report</id><phase>verify</phase><goals><goal>report</goal></goals></execution>
  </executions>
</plugin>
```

Esta configuração já está no `pom.xml` (RF-001). O `-javaagent` do Mockito evita que ele abra um processo Java extra para se anexar à JVM: na primeira execução do RF-001, esse processo falhou por falta de memória e gerou `hs_err_pid*.log` (registrado na evidência `evidencias/execucoes/2026-10-01-997f680.md`).

Por padrão a tag `openai` fica excluída. Para rodar o opt-in, `-Dgroups=openai` seleciona só esses testes e `-Dtest.excluded.groups=nenhum` troca a exclusão por uma tag que não existe.

## 11. Status, evidências e defeitos

### 11.1 Onde fica o controle

Cada cenário tem um registro em `dashboard/tenant-rag-pgvector/Testes/Testes.json`, na lista `cenarios`:

| Campo | Conteúdo |
|---|---|
| `id`, `titulo`, `requisitos`, `prioridade`, `nivel`, `classe` | Vêm do catálogo [05](05%20Cenarios%20de%20teste.md) |
| `execucao` | `JUnit` (automatizado, roda no `mvnw test`) ou `Manual` (executado por uma pessoa: roteiro do [07](07%20Testes%20manuais.md), nível `M`, ou teste opt-in com OpenAI real disparado com `-Dgroups=openai`, nível `O`). Até 2026-10-02 os opt-in usavam `JUnit opt-in` |
| `status` | Ver 11.2 |
| `data` | Data (`AAAA-MM-DD`) da última mudança de status |
| `evidencia` | Caminho do arquivo de evidência, relativo à raiz do projeto (ver 11.3); vazio enquanto não houver execução |
| `bug` | `BUG-xxx` quando o status for `Bug` |
| `observacao` | Motivo de bloqueio ou nota curta |

O catálogo define **o que** testar; o `Testes.json` registra **a situação** de cada cenário. O tipo de execução também aparece no catálogo, na linha de metadados de cada cenário.

### 11.2 Ciclo de status

```text
Não iniciada ──► OK ──► Concluído
      │           │  ▲
      │           ▼  │ (corrigido e passando)
      │          Bug ┘
      ▼
  Bloqueado ──► Não iniciada (quando a decisão sair)
```

| Status | Significa | Quem muda | Exige |
|---|---|---|---|
| **Não iniciada** | Cenário catalogado; o teste ainda não foi escrito ou o roteiro manual ainda não foi executado | — | — |
| **Bloqueado** | Depende de uma decisão ou de outra etapa (ex.: CT-059 ficou bloqueado até a decisão do RF-006 DP-03, tomada em 2026-10-02) | QA | `observacao` com o motivo |
| **OK** | Teste escrito e **passando** na última execução (JUnit), ou roteiro executado com sucesso (Manual / opt-in) | Desenvolvedor (JUnit) ou executor (Manual) | `data` e `evidencia` |
| **Bug** | O teste falhou por defeito no código ou no comportamento | Quem executou | `bug` com o `BUG-xxx` aberto em `bugs[]` e evidência da falha |
| **Concluído** | OK **e** aceito no fechamento da etapa: evidência revisada e a etapa do plano entregue | QA, no fechamento da etapa | Evidência da execução de fechamento |

Regras:

- Um cenário só passa a **Concluído** no fechamento da etapa a que pertence ([seção 7](#7-critérios-de-entrada-e-saída-por-etapa-do-plano)). Até lá, fica em **OK**.
- Se um cenário **Concluído** falhar depois (regressão), volta para **Bug**.
- Teste com falha por problema do próprio teste (dado errado, montagem errada) **não** é Bug: corrige-se o teste e o status continua como estava.

### 11.3 Evidências

A evidência é proporcional ao tipo de execução:

| Execução | O que serve de evidência | Onde guardar | Quando |
|---|---|---|---|
| `JUnit` | Resumo da execução: data, commit, comando, totais e cenários `CT-xxx` aprovados e falhos, extraídos do relatório do Surefire (`target/surefire-reports/*.xml`) e do JaCoCo | `docs/QA/evidencias/execucoes/AAAA-MM-DD-<commit>.md` | Ao mudar status para OK, Bug ou Concluído — não a cada `mvnw test` |
| `Manual` opt-in (nível O) | O mesmo resumo **mais** a resposta completa do modelo para cada pergunta e o veredito (termos esperados encontrados ou não) | `docs/QA/evidencias/CT-xxx/AAAA-MM-DD.md` | Toda execução, porque a resposta do modelo muda de uma vez para outra |
| `Manual` | Passos executados, resultado obtido, executor, data e anexos (saída do `curl`, imagens do Swagger) | `docs/QA/evidencias/CT-xxx/AAAA-MM-DD.md` (+ anexos na mesma pasta) | Toda execução |
| `Bug` (qualquer tipo) | Saída da falha (mensagem e trecho do stack trace do teste) | Mesmo arquivo da execução que falhou | Ao abrir o BUG |

Modelos dos arquivos em [evidencias/README.md](evidencias/README.md). Os relatórios brutos (`target/`) não são versionados: o resumo em Markdown é a evidência que fica no repositório.

**Por que guardar evidência de teste automatizado?** O teste em si prova o comportamento sempre que roda, mas o resumo registra **quando** e **em qual commit** a etapa foi aceita. Isso dá um histórico que o código sozinho não guarda.

### 11.4 Atualização automática

O script `qa/atualizar-status-testes.py` (tarefa T-108 do [Backlog](../backlog/Backlog.md)) lê os relatórios do Surefire depois do `mvnw test`, encontra o `CT-xxx` no `@DisplayName` de cada teste e:

1. gera o resumo `docs/QA/evidencias/execucoes/AAAA-MM-DD-<commit>.md`;
2. muda para **OK** os cenários `JUnit` que passaram e para **Bug** os que falharam (sem criar o `BUG-xxx`, que é aberto por uma pessoa);
3. preenche `data` e `evidencia` no `Testes.json`;
4. atualiza `suites[]` (`aprovados`, `total`, `cobertura` do JaCoCo).

Cenários `Manual` (roteiros e opt-in) são atualizados pelo QA com o resultado informado por quem executou; nunca ficam `OK` sem essa execução. A mudança para **Concluído** é sempre feita por uma pessoa.

### 11.5 Defeitos

- ID `BUG-001`, `BUG-002`… registrado em `Testes.json` (`bugs[]`: `id`, `titulo`, `funcionalidade`, `severidade`, `status`).
- Severidade: **Alta** (P1 falhando, vazamento entre clientes, gravação parcial), **Média** (critério de aceite não atendido), **Baixa** (mensagem, formato).
- Status do bug: `Aberto`, `Em correção`, `Resolvido`. Ao resolver, o cenário volta para **OK** e `bugsResolvidos` aumenta.
- Todo bug corrigido ganha um teste que falhava antes da correção, com o ID no `@DisplayName` (ex.: `"CT-055 / BUG-003 — ..."`).

## 12. Rastreabilidade

Cada cenário tem ID `CT-xxx`, faixa por requisito (RF-001 → CT-001 a CT-009, RF-002 → CT-010 a CT-019, …, RF-012 → CT-110 a CT-119). O catálogo está em [05 Cenarios de teste.md](05%20Cenarios%20de%20teste.md); a ligação critério de aceite → cenário → classe de teste, em [06 Rastreabilidade.md](06%20Rastreabilidade.md). Cada RF em `docs/requirements/` tem a seção "Cenários de teste (QA)" apontando para cá.

Convenção no código:

```java
@Test
@DisplayName("CT-110 — Busca do Cliente A não devolve chunk idêntico do Cliente B")
void naoDeveRetornarChunkDeOutroClienteMesmoComEmbeddingIdentico() { ... }
```

Assim, `grep -r "CT-110" src/test` encontra o teste, e o relatório do Surefire mostra o ID.
