# 06 — Rastreabilidade

Gerado a partir de [05 Cenarios de teste.md](05%20Cenarios%20de%20teste.md) e dos requisitos em `docs/requirements/`. Ao incluir um cenário ou critério novo, atualizar os dois e regenerar esta matriz.

## 1. Resumo

- **Cenários:** 108 — P1: 33, P2: 61, P3: 14
- **Por nível** (um cenário pode ter mais de um): U 28, W 24, I 34, O 3, M 25
- **Execução:** `mvnw test` 80, manual 28 (25 roteiros de nível M em [07 Testes manuais.md](07%20Testes%20manuais.md) + 3 opt-in com OpenAI real, nível O, disparados por uma pessoa; desde 2026-10-02)
- **Bloqueados por decisão ou etapa:** nenhum com status Bloqueado (CT-059 desbloqueado em 2026-10-02 com a decisão do RF-006 DP-03: reenvio substitui). Os 13 cenários do RF-013 (CT-138 a CT-150, 2026-10-05) estão `Não iniciada`: o card HU-016 está Aguardando o desenvolvimento; o resultado esperado segue as DP-02 a DP-07 do RF-013, decididas em 2026-10-06

## 2. Critérios de aceite e fluxos alternativos → cenários

### [RF-001 — Ambiente PostgreSQL com pgvector](../requirements/RF-001%20Ambiente%20PostgreSQL%20com%20pgvector.md)

| Item | Descrição | Cenários |
|---|---|---|
| FA-01 | Porta ocupada: o contêiner não sobe; o desenvolvedor altera o mapeamento de porta no `docker-compose.yml` e... | — |
| FA-02 | Banco fora do ar: a aplicação falha na inicialização com erro de conexão; o desenvolvedor sobe o banco e ex... | — |
| FA-03 | Imagem sem pgvector: a criação da extensão falha; usar uma imagem que inclua pgvector. | — |
| CA-01 | `docker compose up -d` sobe o banco sem erros. | CT-003, CT-116 |
| CA-02 | `SELECT extname FROM pg_extension;` retorna `vector`. | CT-001, CT-116 |
| CA-03 | `mvnw spring-boot:run` inicia a aplicação conectada ao banco. | CT-002, CT-003, CT-116 |
| CA-04 | `mvnw test` continua passando (ver decisão DP-03). | CT-002 |

Regras e decisões também cobertas: RN-01 (CT-001)

### [RF-002 — Estrutura do banco e índice vetorial](../requirements/RF-002%20Estrutura%20do%20banco%20e%20indice%20vetorial.md)

| Item | Descrição | Cenários |
|---|---|---|
| FA-01 | Migration já aplicada: o Flyway não executa de novo. | — |
| FA-02 | Esquema diferente das entidades: a validação falha e a aplicação não sobe, indicando a divergência. | CT-002 |
| FA-03 | Dimensão do embedding diferente da coluna: a gravação de chunks falha (tratada em RF-006). | — |
| CA-01 | Em banco vazio, a aplicação sobe e cria as três tabelas e o índice HNSW. | CT-010, CT-117 |
| CA-02 | A consulta ao catálogo do PostgreSQL (`format_type` da coluna `document_chunk.embedding`) devolve `vector(7... | CT-011, CT-117 |
| CA-03 | Inserir `document` com `client_id` inexistente falha por chave estrangeira. | CT-012 |
| CA-04 | As entidades `ClientEntity` e `DocumentEntity` leem e gravam nas tabelas sem erro, e o `PgVectorEmbeddingSt... | CT-016, CT-051 |
| CA-05 | Inserir chunk com `client_id` diferente do `client_id` do documento falha por chave estrangeira. | CT-013, CT-118 |

Regras e decisões também cobertas: DP-04 (CT-015), RN-01 (CT-012), RN-02 (CT-013, CT-118), RN-03 (CT-011), RN-04 (CT-010, CT-117), RN-06 (CT-014)

### [RF-003 — Cadastro de clientes](../requirements/RF-003%20Cadastro%20de%20clientes.md)

| Item | Descrição | Cenários |
|---|---|---|
| FA-01 | Nome vazio ou ausente: `400 Bad Request` (RF-010). | CT-021, CT-120 |
| FA-02 | `GET` de cliente inexistente pelo administrador: `404 Not Found` (RF-010). Com chave de cliente, qualquer `... | CT-024, CT-025, CT-120 |
| CA-01 | É possível criar os clientes "Cliente A" e "Cliente B" usados nos exemplos do README. | CT-020, CT-119 |
| CA-02 | Cliente criado pode ser consultado pelo `id`. | CT-023, CT-120 |
| CA-03 | Nome vazio é rejeitado com 400. | CT-021, CT-120 |

Regras e decisões também cobertas: RN-01 (CT-021)

### [RF-004 — Geração de embeddings](../requirements/RF-004%20Geracao%20de%20embeddings.md)

| Item | Descrição | Cenários |
|---|---|---|
| FA-01 | Texto vazio: rejeitar sem chamar o modelo. | CT-031 |
| FA-02 | Provedor indisponível ou erro de autenticação: lançar exceção específica; o chamador não grava dados parcia... | CT-033, CT-133 |
| FA-03 | Vetor com dimensão diferente da configurada: lançar exceção de configuração. | CT-032 |
| CA-01 | Dado um texto, o serviço devolve vetor com a dimensão configurada. | CT-030, CT-121 |
| CA-02 | Textos parecidos ("franquia da apólice" e "valor da franquia") têm distância de cosseno menor que textos se... | CT-035 |
| CA-03 | Nos testes, o `EmbeddingModel` do LangChain4j é substituído por um fake determinístico, sem chamar a OpenAI. | CT-036 |

Regras e decisões também cobertas: RN-02 (CT-032, CT-121)

### [RF-005 — Extração de texto e divisão em chunks](../requirements/RF-005%20Extracao%20de%20texto%20e%20divisao%20em%20chunks.md)

| Item | Descrição | Cenários |
|---|---|---|
| FA-01 | PDF corrompido ou ilegível: erro "Não foi possível ler o arquivo PDF."; nada é gravado. | CT-041, CT-123 |
| FA-02 | PDF sem texto extraível (imagem escaneada): erro "O documento não contém texto extraível."; nada é gravado. | CT-042 |
| FA-03 | Texto menor que um chunk: gera um único chunk com índice 0. | CT-043 |
| CA-01 | Um PDF de exemplo (RF-011) gera ao menos um chunk, com índices 0, 1, 2… sem lacunas. | CT-044, CT-122 |
| CA-02 | Nenhum trecho do texto extraído se perde: toda palavra do texto aparece em pelo menos um chunk, e nenhum ch... | CT-044 |
| CA-03 | PDF sem texto é rejeitado com a mensagem de FA-02. | CT-042 |
| CA-04 | Tamanho e sobreposição vêm de `application.properties`. | CT-045, CT-122 |

Regras e decisões também cobertas: RN-01 (CT-044, CT-122), RN-02 (CT-044), RN-04 (CT-044)

### [RF-006 — Ingestão de documentos do cliente](../requirements/RF-006%20Ingestao%20de%20documentos%20do%20cliente.md)

| Item | Descrição | Cenários |
|---|---|---|
| FA-01 | `clientId` de cliente inexistente: como a chave de API sempre pertence a um cliente existente, um `clientId... | CT-057 |
| FA-02 | Arquivo ausente, vazio ou que não é PDF: `400 Bad Request`. | CT-052 |
| FA-03 | Tipo de documento ausente ou inválido: `400 Bad Request`. | CT-052 |
| FA-04 | Falha na extração (RF-005 FA-01/FA-02): `422 Unprocessable Entity` (ver RF-010); nada é gravado. | CT-054, CT-123 |
| FA-05 | Falha no modelo de embeddings: erro de serviço externo (RF-010); a transação é desfeita e nada é gravado. | CT-055, CT-133 |
| FA-06 | Acesso a cliente não autorizado: `403 Forbidden` (RF-009). | CT-057, CT-125 |
| CA-01 | Enviar `contrato.pdf` do Cliente A cria 1 `document` com `client_id` de A e N chunks com embedding. | CT-050, CT-121, CT-124 |
| CA-02 | O `DocumentResponseDTO` traz `totalChunks` igual à quantidade gravada. | CT-050, CT-124 |
| CA-03 | Upload para um `clientId` diferente do da chave (existente ou não) devolve 403 e não grava nada. | CT-057, CT-125 |
| CA-04 | Falha simulada no embedding (mock) não deixa `document` nem chunks gravados. | CT-055 |
| CA-05 | Teste unitário do `DocumentService` com Mockito cobre fluxo principal e FA-05. | CT-055 |

Regras e decisões também cobertas: DP-02 (CT-053, CT-137), DP-03 (CT-059, CT-126), RN-01 (CT-058, CT-124), RN-02 (CT-058), RN-03 (CT-056), RN-04 (CT-050, CT-124), RN-05 (CT-058)

### [RF-007 — Busca semântica por cliente](../requirements/RF-007%20Busca%20semantica%20por%20cliente.md)

| Item | Descrição | Cenários |
|---|---|---|
| FA-01 | Pergunta vazia ou ausente: `400 Bad Request`. | CT-062 |
| FA-02 | `clientId` de cliente inexistente: recebe `403 Forbidden`, igual a qualquer `clientId` diferente do da chav... | — |
| FA-03 | Cliente sem documentos: `200 OK` com lista vazia, mesmo que outros clientes tenham documentos. | CT-063, CT-128 |
| FA-04 | Falha no modelo de embeddings: erro de serviço externo (RF-010). | CT-064, CT-133 |
| FA-05 | Acesso a cliente não autorizado: `403 Forbidden` (RF-009). | — |
| CA-01 | "Qual é o valor da franquia da apólice?" feita pelo Cliente A devolve chunks do contrato do Cliente A. | CT-060, CT-127 |
| CA-02 | Nenhum resultado de uma busca do Cliente A pertence a documento do Cliente B (verificado em RF-012). | CT-110, CT-136 |
| CA-03 | Resultados vêm em ordem decrescente de `score`, no máximo K itens. | CT-060, CT-127 |
| CA-04 | Cliente sem documentos recebe lista vazia. | CT-063, CT-128 |
| CA-05 | O SQL executado (log do driver ou `EXPLAIN`) mostra o filtro por `client_id`. | CT-065 |

Regras e decisões também cobertas: DP-02 (CT-061), RN-01 (CT-065), RN-03 (CT-060), RN-04 (CT-061)

### [RF-008 — Resposta a perguntas com base nos documentos](../requirements/RF-008%20Resposta%20a%20perguntas%20com%20base%20nos%20documentos.md)

| Item | Descrição | Cenários |
|---|---|---|
| FA-01 | Nenhum chunk recuperado: responder que não há documentos do cliente para responder, sem chamar o modelo. | CT-070, CT-128 |
| FA-02 | Chunks sem a informação pedida: a resposta diz que a informação não foi encontrada nos documentos do cliente. | CT-076 |
| FA-03 | Modelo de linguagem indisponível: erro de serviço externo (RF-010). | CT-072 |
| FA-04 | `clientId` diferente do da chave, exista ou não: 403 (RF-009). | — |
| CA-01 | As cinco perguntas da seção 10 do README, feitas pelos Clientes A e B sobre os documentos de exemplo (RF-01... | CT-075 |
| CA-02 | A pergunta de perda total compara o percentual do contrato com custo do reparo e valor do veículo da vistor... | CT-075, CT-129 |
| CA-03 | Nenhuma resposta ao Cliente A menciona valores que só existem nos documentos do Cliente B. | CT-075, CT-136 |
| CA-04 | Pergunta sem resposta nos documentos recebe "informação não encontrada", e não um valor inventado. Verifica... | CT-076 |

Regras e decisões também cobertas: DP-03 (CT-071), DP-04 (CT-073), RN-01 (CT-114), RN-03 (CT-071, CT-129)

### [RF-009 — Controle de acesso por cliente](../requirements/RF-009%20Controle%20de%20acesso%20por%20cliente.md)

| Item | Descrição | Cenários |
|---|---|---|
| FA-01 | Sem credencial ou credencial inválida: `401 Unauthorized`. | CT-080, CT-130, CT-131 |
| FA-02 | Credencial válida de outro cliente: `403 Forbidden`; nenhuma consulta ao banco de documentos é feita. | CT-080, CT-082 |
| CA-01 | Credencial do Cliente A em `/clients/{A}/search` é aceita. | CT-080, CT-131 |
| CA-02 | Credencial do Cliente A em `/clients/{B}/search` e `/clients/{B}/documents` recebe 403. | CT-080, CT-125, CT-135 |
| CA-03 | Requisição sem credencial recebe 401. | CT-080, CT-130 |
| CA-04 | Testes automatizados cobrem CA-01 a CA-03 (RF-012). | CT-080 |

Regras e decisões também cobertas: DP-01 (CT-026, CT-083, CT-119), DP-02 (CT-027, CT-130), RN-01 (CT-025, CT-084), RN-02 (CT-082), RN-03 (CT-081, CT-130)

### [RF-010 — Tratamento padronizado de erros](../requirements/RF-010%20Tratamento%20padronizado%20de%20erros.md)

| Item | Descrição | Cenários |
|---|---|---|
| FA-01 | Exceção não mapeada: `500 Internal Server Error` com mensagem genérica; detalhes só no log. | CT-092 |
| CA-01 | `GET /clients/999999` chamado pelo administrador devolve 404 no formato padronizado. (Com chave de cliente,... | CT-024, CT-132 |
| CA-02 | Pergunta vazia devolve 400 com o campo inválido identificado. | CT-062, CT-093, CT-132 |
| CA-03 | Exceção inesperada devolve 500 sem stack trace no corpo. | CT-092 |
| CA-04 | Testes de controller (MockMvc) cobrem 400, 404 e 500. | CT-090 |

Regras e decisões também cobertas: DP-02 (CT-091, CT-132), RN-01 (CT-090, CT-132), RN-02 (CT-093, CT-132), RN-03 (CT-123), RN-05 (CT-133), RN-06 (CT-053, CT-090, CT-137), RN-07 (CT-092, CT-133)

### [RF-011 — Documentos de exemplo por cliente](../requirements/RF-011%20Documentos%20de%20exemplo%20por%20cliente.md)

| Item | Descrição | Cenários |
|---|---|---|
| FA-01 | PDF gerado como imagem: a ingestão rejeita (RF-005 FA-02); gerar PDF com texto. | — |
| CA-01 | Os seis arquivos existem e têm texto extraível. | CT-100, CT-134 |
| CA-02 | Existe gabarito com a resposta esperada de cada pergunta da seção 10, por cliente. | CT-102 |
| CA-03 | Os valores do Cliente A não aparecem nos documentos do Cliente B, e vice-versa. | CT-101, CT-134 |

Regras e decisões também cobertas: RN-02 (CT-101), RN-04 (CT-102)

### [RF-012 — Testes de isolamento entre clientes](../requirements/RF-012%20Testes%20de%20isolamento%20entre%20clientes.md)

| Item | Descrição | Cenários |
|---|---|---|
| FA-01 | Cliente A sem documentos e Cliente B com documentos: a busca de A devolve lista vazia. | CT-111 |
| FA-02 | Credencial de A acessando rota de B: 403 (RF-009). | CT-135 |
| CA-01 | Busca do Cliente A com chunk idêntico no Cliente B não devolve o chunk de B. | CT-110, CT-113, CT-136 |
| CA-02 | Cliente sem documentos recebe lista vazia, mesmo com outros clientes populados. | CT-111, CT-113, CT-128 |
| CA-03 | Acesso cruzado por credencial devolve 403 (quando RF-009 estiver implementado). | CT-115, CT-135 |
| CA-04 | Resposta gerada (RF-008) para o Cliente A não usa chunks de B (quando RF-008 estiver implementado). | CT-114, CT-136 |
| CA-05 | `mvnw test` executa todos esses casos sem exigir chave de API de provedor de IA. | CT-002 |

Regras e decisões também cobertas: RN-02 (CT-036)

### [RF-013 — Log de requisições para rastreamento](../requirements/RF-013%20Log%20de%20requisicoes%20para%20rastreamento.md)

| Item | Descrição | Cenários |
|---|---|---|
| FA-01 | Requisição recusada pela segurança (sem chave, chave inválida ou acesso a outro cliente): a linha de log é... | CT-143, CT-149 |
| FA-02 | Requisição que termina em erro (4xx ou 5xx tratado pelo `GlobalExceptionHandler`): a linha de log é regist... | CT-140, CT-144, CT-149 |
| FA-03 | Requisição em rota sem `/clients/{clientId}` no caminho (ex.: `POST /clients`): o campo `clientId` é regis... | CT-139, CT-143, CT-149 |
| FA-04 | Requisição às rotas de apoio (Swagger UI e `/v3/api-docs`): não gera linha de log de requisição (DP-07). | CT-142, CT-149 |
| FA-05 | Requisição que chega com um trace ID num cabeçalho: o valor de entrada não é aceito; a aplicação gera o se... | CT-141 |
| CA-01 | Cada requisição a um endpoint da API gera exatamente uma linha de log de requisição em texto chave=valor c... | CT-138, CT-143, CT-149 |
| CA-02 | `GET /clients/{id}` com a chave do próprio cliente gera uma linha `INFO` com `method=GET`, `endpoint=/clie... | CT-139, CT-143, CT-149 |
| CA-03 | Uma requisição sem `X-API-Key` a `/clients/{id}` gera uma linha `WARN` com `status=401` e `clientId={id}`;... | CT-143, CT-149 |
| CA-04 | Uma requisição que termina em 500 gera uma linha `ERROR` com `status=500`, e o log de erro do `GlobalExcep... | CT-144 |
| CA-05 | Duas requisições seguidas têm `traceId` diferentes; um trace ID enviado pelo consumidor num cabeçalho de e... | CT-141, CT-149 |
| CA-06 | Com chaves conhecidas nos testes (de cliente, de administrador, da OpenAI e senha do banco), nenhuma linha... | CT-146, CT-150 |
| CA-07 | As linhas de log de `POST /clients/{id}/ask`, `POST /clients/{id}/search` e `POST /clients/{id}/documents`... | CT-147, CT-150 |
| CA-08 | A resposta da API (status, cabeçalhos já existentes e corpo) é a mesma com e sem o log; a única diferença... | CT-145 |
| CA-09 | Os testes automáticos verificam os campos do log com `mvnw test`, sem chamar a OpenAI. | CT-138 a CT-148 (CT-146 confere que a OpenAI não é chamada) |
| CA-10 | Requisições ao Swagger UI e a `/v3/api-docs` não geram linha de log de requisição; uma requisição a rota s... | CT-139, CT-142, CT-143, CT-149 |

Regras e decisões também cobertas: RN-01 (CT-140, CT-142, CT-143, CT-149), RN-02 (CT-138, CT-149), RN-03 (CT-138, CT-149), RN-04 (CT-141, CT-144, CT-149), RN-05 (CT-146, CT-148, CT-150), RN-06 (CT-138, CT-139, CT-147, CT-150), RN-07 (CT-140, CT-145), RN-08 (CT-140, CT-142, CT-143, CT-149), RN-09 (CT-139, CT-143, CT-149). Esperado conforme as DP decididas em 2026-10-06 (recomendação da ADR-015 aprovada): DP-02 (CT-138, CT-149), DP-03 (CT-141, CT-144, CT-145, CT-149), DP-04 (CT-138, CT-149), DP-05 (CT-139, CT-143, CT-149), DP-06 (CT-139, CT-143, CT-147, CT-150), DP-07 (CT-140, CT-142, CT-143, CT-149)

## 3. Critérios de aceite sem cenário

Nenhum. Todo critério de aceite (CA) tem ao menos um cenário.

Fluxos alternativos (FA) de ambiente — RF-001 FA-01 a FA-03 (porta ocupada, banco fora, imagem sem pgvector) — são orientações de operação e não têm teste automatizado.

## 4. Classes de teste → cenários

| Classe | Nível | Cenários |
|---|---|---|
| `ApiFlowIT` | Integração | CT-020, CT-023, CT-063, CT-083 |
| `ChunkRepositoryIT` | Integração | CT-051, CT-060, CT-066 |
| `DocumentIngestionIT` | Integração | CT-050, CT-054, CT-055, CT-056, CT-057, CT-058, CT-059 |
| `SampleDocumentsIT` | Integração | CT-100, CT-101, CT-102 |
| `SchemaMigrationIT` | Integração | CT-001, CT-010, CT-011, CT-012, CT-013, CT-014, CT-015 |
| `EntityMappingIT` | Integração | CT-016 |
| `TenantIsolationIT` | Integração | CT-110, CT-111, CT-112, CT-113, CT-114 |
| `TenantRagPgvectorApplicationIT` | Integração | CT-002 |
| `UploadLimitIT` | Integração | CT-053 |
| `RequestLoggingIT` | Integração | CT-146, CT-147 |
| `RagQualityOpenAiIT` | Opt-in (execução manual) | CT-035, CT-075, CT-076 |
| `AnswerServiceTest` | Unitário | CT-070, CT-071, CT-072, CT-073 |
| `ApiKeyHasherTest` | Unitário | CT-083 |
| `ChunkRepositoryTest` | Unitário | CT-051, CT-065 |
| `ClientServiceTest` | Unitário | CT-026 |
| `DocumentServiceTest` | Unitário | CT-050, CT-055 |
| `EmbeddingServiceTest` | Unitário | CT-030, CT-031, CT-032, CT-033, CT-034 |
| `FakeEmbeddingModelTest` | Unitário | CT-036 |
| `LoggingConfigurationTest` | Unitário | CT-148 |
| `PdfTextExtractorTest` | Unitário | CT-040, CT-041, CT-042 |
| `RequestLoggingFilterTest` | Unitário | CT-138, CT-139, CT-140, CT-141, CT-142, CT-145 |
| `SearchServiceTest` | Unitário | CT-060, CT-061 |
| `TextChunkerTest` | Unitário | CT-043, CT-044, CT-045 |
| `AnswerControllerTest` | Web slice | CT-072, CT-074 |
| `ClientControllerTest` | Web slice | CT-020, CT-021, CT-024 |
| `DocumentControllerTest` | Web slice | CT-052 |
| `GlobalExceptionHandlerTest` | Web slice | CT-090, CT-091, CT-092, CT-093, CT-144 |
| `RequestLoggingWebTest` | Web slice | CT-143, CT-145 |
| `SearchControllerTest` | Web slice | CT-061, CT-062, CT-064 |
| `SecurityConfigTest` | Web slice | CT-025, CT-027, CT-080, CT-081, CT-082, CT-084, CT-085, CT-115 |
| Roteiros manuais ([07](07%20Testes%20manuais.md)) | Manual | CT-003, CT-116, CT-117, CT-118, CT-119, CT-120, CT-121, CT-122, CT-123, CT-124, CT-125, CT-126, CT-127, CT-128, CT-129, CT-130, CT-131, CT-132, CT-133, CT-134, CT-135, CT-136, CT-137, CT-149, CT-150 |

## 5. Cenários P1 (não podem falhar nem ser desabilitados)

| Cenário | Título | Classe |
|---|---|---|
| CT-013 | Chunk não pode ter cliente diferente do documento | `SchemaMigrationIT` |
| CT-020 | Administrador cadastra cliente | `ClientControllerTest`, `ApiFlowIT` |
| CT-025 | Cliente consulta a si mesmo, mas não a outro | `SecurityConfigTest` |
| CT-026 | Chave gerada é aleatória e só o hash é guardado | `ClientServiceTest` |
| CT-027 | Cliente não pode cadastrar clientes | `SecurityConfigTest` |
| CT-050 | Ingestão de PDF válido | `DocumentIngestionIT`, `DocumentServiceTest` |
| CT-055 | Falha no embedding não grava nada | `DocumentServiceTest`, `DocumentIngestionIT` |
| CT-056 | Falha ao gravar chunks desfaz o documento | `DocumentIngestionIT` |
| CT-057 | Upload na conta de outro cliente | `DocumentIngestionIT` |
| CT-058 | Dono do documento vem do caminho, data vem do sistema | `DocumentIngestionIT` |
| CT-065 | O filtro client_id é sempre enviado ao store | `ChunkRepositoryTest` |
| CT-080 | Matriz de acesso por chave e rota (DDT) | `SecurityConfigTest` |
| CT-081 | 403 não revela se o outro cliente existe | `SecurityConfigTest` |
| CT-082 | Acesso negado não chega ao service | `SecurityConfigTest` |
| CT-083 | Chave guardada só como hash | `ApiKeyHasherTest`, `ApiFlowIT` |
| CT-110 | Chunk idêntico de outro cliente não aparece | `TenantIsolationIT` |
| CT-111 | Cliente vazio não vê dados de outro | `TenantIsolationIT` |
| CT-112 | topK alto não "completa" com dados de outro cliente | `TenantIsolationIT` |
| CT-113 | Matriz de isolamento A↔B (DDT) | `TenantIsolationIT` |
| CT-114 | Contexto da resposta só tem dados do cliente | `TenantIsolationIT` |
| CT-115 | Acesso cruzado por credencial em todas as rotas de dados | `SecurityConfigTest` |
| CT-118 | Banco rejeita chunk com cliente diferente do documento | Manual (psql) |
| CT-124 | Cliente envia os documentos pelo Swagger UI | Manual (Swagger UI, psql) |
| CT-125 | Ninguém envia documento para a conta de outro cliente | Manual (curl ou Postman) |
| CT-128 | Cliente sem documentos não vê nada dos outros | Manual (curl ou Postman) |
| CT-130 | Autenticação e permissões por chave | Manual (curl ou Postman) |
| CT-133 | Falha da OpenAI vira 503 sem detalhes técnicos e sem gravação parcial | Manual (curl, log da IDE, psql) |
| CT-135 | Acesso cruzado entre Cliente A e Cliente B devolve 403 em todas as rotas | Manual (curl ou Postman) |
| CT-136 | Mesma pergunta, cada cliente só vê os próprios dados | Manual (curl ou Swagger UI) |
| CT-146 | Nenhuma chave, hash ou senha aparece no log | `RequestLoggingIT` |
| CT-147 | Linhas de /ask, /search e /documents sem pergunta, texto, nome de arquivo ou nome do cliente | `RequestLoggingIT` |
| CT-148 | Configuração versionada não liga log de cabeçalhos, SQL com parâmetros ou da OpenAI | `LoggingConfigurationTest` |
| CT-150 | Nenhuma chave, pergunta ou nome de arquivo no console da IDE | Manual (log da IDE, curl ou Postman) |
