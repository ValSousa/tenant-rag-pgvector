# Arquitetura — tenant-rag-pgvector

Documentação de arquitetura para quem vai implementar o projeto. Ela parte do `README.md` da raiz (a especificação) e dos requisitos em `docs/requirements/` (RF-001 a RF-013). Os cenários de teste e a estratégia de QA estão em [`docs/QA/`](../QA/README.md).

## Ordem de leitura

| # | Documento | Para que serve |
|---|---|---|
| 1 | [01 Visao geral.md](01%20Visao%20geral.md) | Contexto, objetivos, restrições, stack e visão de contêineres |
| 2 | [02 Componentes e camadas.md](02%20Componentes%20e%20camadas.md) | Pacotes, classes, responsabilidades e regras de dependência |
| 3 | [03 Fluxos principais.md](03%20Fluxos%20principais.md) | Diagramas de sequência: cadastro, ingestão, busca e resposta |
| 4 | [04 Modelo de dados.md](04%20Modelo%20de%20dados.md) | DDL final, migrations, mapeamento JPA e consulta vetorial |
| 5 | [05 API REST.md](05%20API%20REST.md) | Endpoints, DTOs, códigos HTTP, formato de erro e Swagger UI |
| 6 | [06 Integracoes e configuracao.md](06%20Integracoes%20e%20configuracao.md) | PostgreSQL, OpenAI, LangChain4j, `AiConfig`, `docker-compose.yml`, propriedades |
| 7 | [07 Seguranca e isolamento.md](07%20Seguranca%20e%20isolamento.md) | Chave de API por cliente e as três camadas de isolamento |
| 8 | [08 Estrategia de testes.md](08%20Estrategia%20de%20testes.md) | Pirâmide de testes, Testcontainers e testes de isolamento |
| 9 | [09 Plano de implementacao.md](09%20Plano%20de%20implementacao.md) | Ordem de construção, dependências do `pom.xml` e definição de pronto |

## Decisões técnicas (ADRs)

| ADR | Título | Status |
|---|---|---|
| [ADR-001](adr/ADR-001%20Monolito%20em%20camadas.md) | Monólito Spring Boot em camadas | Aprovada |
| [ADR-002](adr/ADR-002%20PostgreSQL%20com%20pgvector.md) | PostgreSQL com pgvector como banco relacional e vetorial | Aprovada |
| [ADR-003](adr/ADR-003%20Flyway%20para%20o%20esquema.md) | Flyway para versionar o esquema | Aprovada |
| [ADR-004](adr/ADR-004%20Busca%20vetorial%20com%20PgVectorEmbeddingStore.md) | Busca vetorial com PgVectorEmbeddingStore filtrado por cliente | Aprovada |
| [ADR-005](adr/ADR-005%20LangChain4j%20como%20framework%20de%20IA.md) | LangChain4j como framework de IA | Aprovada |
| [ADR-006](adr/ADR-006%20Modelos%20da%20OpenAI.md) | Modelos da OpenAI (embeddings de 768 dimensões) | Aprovada |
| [ADR-007](adr/ADR-007%20Extracao%20e%20chunking%20com%20LangChain4j.md) | Extração e chunking com LangChain4j (PDFBox + splitter recursivo) | Aprovada |
| [ADR-008](adr/ADR-008%20Chave%20de%20API%20por%20cliente.md) | Autenticação por chave de API por cliente | Aprovada |
| [ADR-009](adr/ADR-009%20Cadastro%20de%20clientes%20por%20endpoint.md) | Cadastro de clientes por endpoint administrativo | Aprovada |
| [ADR-010](adr/ADR-010%20Erros%20com%20ProblemDetail.md) | Erros no formato ProblemDetail (RFC 9457) | Aprovada |
| [ADR-011](adr/ADR-011%20Endpoint%20separado%20para%20resposta.md) | Endpoint separado para a resposta gerada | Aprovada |
| [ADR-012](adr/ADR-012%20Testes%20com%20Testcontainers.md) | Testes de integração com Testcontainers e embeddings fixos | Aprovada |
| [ADR-013](adr/ADR-013%20Documentacao%20da%20API%20com%20Swagger.md) | Documentação da API com OpenAPI e Swagger UI | Aprovada |
| [ADR-014](adr/ADR-014%20Pacote%20validator%20e%20excecoes%20proprias.md) | Pacote `validator` e exceções próprias no lugar de `ResponseStatusException` | Aprovada |
| [ADR-015](adr/ADR-015%20Log%20por%20requisicao%20com%20filtro%20e%20MDC.md) | Log por requisição com filtro servlet e MDC | Aprovada |

- **Aprovada**: decisão que está no README ou que foi aprovada pelo responsável pelo projeto (ADRs 001 a 013 em 2026-10-01; ADR-014 em 2026-10-05; ADR-015 em 2026-10-06).
- **Em revisão**: proposta ainda não aprovada; lista alternativas e consequências. Hoje: nenhuma.

## Rastreabilidade requisito → arquitetura

| Requisito | Onde está resolvido | ADRs |
|---|---|---|
| RF-001 Ambiente PostgreSQL com pgvector | 06, 09 | ADR-002 |
| RF-002 Estrutura do banco e índice vetorial | 04 | ADR-002, ADR-003, ADR-006 |
| RF-003 Cadastro de clientes | 02, 03, 05 | ADR-009, ADR-013 |
| RF-004 Geração de embeddings | 02, 06 | ADR-005, ADR-006 |
| RF-005 Extração de texto e chunking | 02, 06 | ADR-005, ADR-007 |
| RF-006 Ingestão de documentos | 02, 03, 05 | ADR-004, ADR-010, ADR-013, ADR-014 |
| RF-007 Busca semântica por cliente | 03, 04, 05 | ADR-004, ADR-013 |
| RF-008 Resposta a perguntas | 03, 05, 06 | ADR-005, ADR-006, ADR-011, ADR-013 |
| RF-009 Controle de acesso | 07 | ADR-008 |
| RF-010 Tratamento de erros | 02, 05 | ADR-010, ADR-014 |
| RF-011 Documentos de exemplo | 09 | — |
| RF-012 Testes de isolamento | 08 | ADR-012 |
| RF-013 Log de requisições para rastreamento | 01 (escopo), 02 (seção 3.9), 05 (cabeçalho `X-Trace-Id`), 06 (seção 9), 07 (regra 6), 08 (seção 6.1), 09 (Etapa 8, pronta para desenvolvimento) | ADR-015, ADR-008, ADR-010 |

## Decisões que ainda dependem de você

A lista do que aguarda aprovação manual fica em [Aprovacoes pendentes.md](Aprovacoes%20pendentes.md) (arquivo, descrição e status).

Nenhuma aprovação pendente. As ADRs 001 a 015 estão aprovadas; a ADR-015 (log por requisição, RF-013) e as decisões DP-01 a DP-07 do RF-013 foram aprovadas em 2026-10-06, e a Etapa 8 do [plano](09%20Plano%20de%20implementacao.md) está pronta para desenvolvimento.

A decisão de conteúdo que faltava (RF-008 DP-06) foi tomada em 2026-10-02: a resposta gerada entra na primeira entrega. O texto dos documentos de exemplo (RF-011 DP-01) foi decidido em 2026-10-02: escrito pelo usuário.
