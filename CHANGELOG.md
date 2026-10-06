# Changelog

Todas as mudanças relevantes do projeto ficam registradas aqui. Formato baseado em [Keep a Changelog](https://keepachangelog.com/pt-BR/1.1.0/); versões seguem [SemVer](https://semver.org/lang/pt-BR/).

## [Não lançado]

Código já presente na cópia de trabalho, mas fora da release v0.1.0 porque o card do requisito ainda não está `Concluído` (regra de escopo por card):

- RF-010 (Tratamento padronizado de erros), card HU-010 — Em teste, fase Manual: faltam o passo 4 do CT-132 e o CT-133 (execução por uma pessoa).
  - `GlobalExceptionHandler`: `type` `about:blank` em todo `ProblemDetail` e `detail` em português para JSON malformado ou ausente, 405, 406 ("Formato de resposta não suportado"), 415 e recurso inexistente.
  - Testes: `GlobalExceptionHandlerTest` (CT-090 a CT-093), com um controller só de teste em `/clients/{clientId}/teste-erro`.
  - As partes do RF-010 usadas pelos requisitos da v0.1.0 (erros em `ProblemDetail` com títulos em português, 422 de PDF ilegível, 413 de arquivo grande, 503 do provedor de IA) já estão no código; o requisito entra numa release quando o HU-010 fechar.
- RF-011 (Documentos de exemplo por cliente), card HU-011 — Em andamento: T-601 e T-603 (`SampleDocumentsIT`, CT-100 a CT-102) concluídas; falta a T-602 (`http/requests.http`).
  - Seis PDFs fictícios em `documents/cliente-a/` e `documents/cliente-b/` (contrato, sinistro, vistoria) e `documents/gabarito.md`; Cliente A é perda total, Cliente B não.
  - Massa `ddt/gabarito-perguntas.csv` (CT-075, opt-in) preenchida a partir do gabarito.
- RF-012 (Testes de isolamento entre clientes), card HU-012 — Em andamento: falta a T-108 e os testes manuais CT-135 e CT-136.
  - `TenantIsolationIT` e `isolation-matrix.csv` (CT-110 a CT-113, P1): chunk idêntico de outro cliente nunca aparece, cliente vazio recebe lista vazia, `topK` alto não completa com dados de outro cliente, contra PostgreSQL + pgvector real.
  - CT-114 (P1): a resposta do `/ask` ao Cliente A só recebe trechos do Cliente A, conferido no prompt enviado ao modelo. CT-115 (P1): acesso cruzado por credencial em search, documents e ask → 403.
- Evoluções planejadas de requisitos que já estão na v0.1.0 (ainda não fazem parte do texto dos requisitos):
  - HU-013 (RF-003) — Bloqueado: restringir o formato do nome do cliente; decisão pendente sobre o formato aceito, a mensagem do 400 e os clientes já cadastrados.
  - HU-015 e HU-014 (RF-007) — Aguardando: decidir e depois implementar um limiar mínimo de similaridade na busca (hoje `minScore` 0, RF-007 DP-03).

## [0.1.0] - 2026-10-06

Primeira entrega de funcionalidade, em Produção desde 2026-10-06 com aprovação do usuário: cliente cadastrado envia PDFs, busca e pergunta só sobre os próprios documentos. Escopo pedido pelo usuário em 2026-10-05: cards HU-001 a HU-009 (RF-001 a RF-009), todos `Concluído`, com testes automáticos e manuais OK e revisões aprovadas para fechamento. Checklist e pendências em `Releases.json` (notas da release).

Histórico desta versão: preparada em 2026-10-01 só com o RF-001; em 2026-10-02 o RF-001 saiu porque o HU-001 voltou para os testes manuais (CT-116). A versão não tinha sido publicada, por isso foi reaproveitada.

### Adicionado

- RF-001 — Ambiente PostgreSQL com pgvector (card HU-001).
  - `docker-compose.yml` com `pgvector/pgvector:pg17` e `docker/init.sql` (extensão `vector`).
  - Credenciais `DB_USER`, `DB_PASSWORD`, `OPENAI_API_KEY` e `ADMIN_API_KEY` sem valor padrão, lidas de variáveis de ambiente ou de um `.env` local fora do git (modelo `.env.example`), usado pela aplicação e pelo Compose.
  - JPA, driver PostgreSQL, Flyway (`ddl-auto=validate`), Testcontainers, Surefire (testes `openai` excluídos), JaCoCo; perfil `test`.
- RF-002 — Estrutura do banco e índice vetorial (card HU-002).
  - Migration `V1__create_tables.sql`: tabelas `client`, `document`, `document_chunk` (`VECTOR(768)`), índice HNSW `vector_cosine_ops` e FK composta `(document_id, client_id)`.
  - `ClientEntity`, `DocumentEntity`, `DocumentTypeEnum` (`CONTRACT`, `CLAIM`, `INSPECTION`), `ClientRepository`, `DocumentRepository`.
- RF-003 — Cadastro de clientes (card HU-003).
  - `POST /clients` (só administrador) → 201 com a chave de API do cliente, exibida uma única vez e guardada só como SHA-256; `GET /clients/{clientId}` para o administrador ou o próprio cliente.
  - Erros em `ProblemDetail` com títulos em português; falha ao consultar a chave no banco → 500.
- RF-004 — Geração de embeddings (card HU-004).
  - `AiConfig` monta à mão o `OpenAiEmbeddingModel` (`text-embedding-3-small`, 768 dimensões), sem starter do LangChain4j; `EmbeddingService` confere a dimensão e transforma falhas em 503.
  - `mvnw test` não chama a OpenAI (`FakeEmbeddingModel`); o CT-035 roda com a OpenAI real só quando pedido (opt-in).
- RF-005 — Extração de texto e divisão em chunks (card HU-005).
  - `PdfTextExtractor` (`ApachePdfBoxDocumentParser`): PDF ilegível ou sem texto → 422 com mensagem em português.
  - `TextChunker` (`DocumentSplitters.recursive`, 1000/200 caracteres em `app.rag.chunk-size` e `app.rag.chunk-overlap`), com `chunk_index` a partir de 0.
- RF-006 — Ingestão de documentos do cliente (card HU-006).
  - `POST /clients/{clientId}/documents` (multipart `file` + `documentType`) → 201 com `DocumentResponseDTO`; 400 para arquivo ausente, vazio ou não PDF e tipo inválido; 413 acima de 5 MB ("Arquivo muito grande"); 422 para PDF ilegível; 503 se o embedding falhar.
  - `DocumentService` (fora de transação) e `DocumentWriter` (`@Transactional`): documento e chunks gravados juntos ou nada.
  - `ChunkRepository.saveAll`: chunks no `PgVectorEmbeddingStore` com `client_id`, `document_id`, `chunk_index`, `document_type` e `file_name` em colunas.
  - Reenviar um arquivo com o mesmo nome substitui o documento anterior do mesmo cliente (RF-006 DP-03).
  - Validação do arquivo no `DocumentFileValidator` (pacote `validator`) com `InvalidFileException` tratada no `GlobalExceptionHandler`; sem `ResponseStatusException` (ADR-014, T-411). Resposta da API inalterada.
- RF-007 — Busca semântica por cliente (card HU-007).
  - `POST /clients/{clientId}/search` (`question` até 2000 caracteres; `topK` opcional, padrão 5, de 1 a 20) → resultados do mais parecido para o menos parecido, com `score` de 0 a 1; lista vazia para cliente sem documentos.
  - `ChunkRepository.searchByClient`: filtro `client_id` sempre enviado ao `PgVectorEmbeddingStore` (vira `WHERE client_id = ...` no SQL) e `SET LOCAL hnsw.iterative_scan = strict_order` na mesma transação.
- RF-008 — Resposta a perguntas com base nos documentos (card HU-008).
  - `POST /clients/{clientId}/ask` (`topK` opcional, padrão 8) → `answer` e `sources` numeradas; cliente sem documentos recebe a resposta fixa sem chamar o modelo; 503 se a OpenAI falhar.
  - `ChatModel` (`gpt-4o-mini`, temperatura 0,1) e `RagAssistant` (`AiServices`, sem `ContentRetriever`): o modelo só recebe os trechos da busca filtrada por `client_id`.
  - CT-075 e CT-076 (OpenAI real, opt-in) executados em 2026-10-05.
- RF-009 — Controle de acesso por cliente (card HU-009).
  - Chave de API por cliente e chave de administrador; a chave do Cliente A só acessa `/clients/{A}/**`; 401 sem chave ou com chave desconhecida; 403 com o mesmo corpo para cliente existente ou não.
  - `PUT`, `DELETE` e `PATCH` em `/clients/{clientId}` → 403; Swagger e `/v3/api-docs` públicos.
  - Testes: `security-matrix.csv` (40 linhas, todos os endpoints) no `SecurityConfigTest`.

### Pendências conhecidas

- Documentação: resolvida em 2026-10-05 — guias e arquitetura atualizados para o limite de 5 MB.
- Achados de revisão abertos, todos Baixa ou Mínima (nenhum Alta ou Média): RF-001 #7, RF-003 #4, RF-004 #3, RF-006 #2 e #4, RF-007 #1 a #4, RF-008 #1 a #4, RF-009 #1 (requisição recusada pelo firewall do Spring Security sai 401, não 400).
- Código no commit `4f27e73` (2026-10-06, feito pelo usuário); sem tag por decisão do usuário (projeto de estudo).
