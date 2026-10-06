# RF-006 — Ingestão de documentos do cliente

## Objetivo

Receber um documento de um cliente, processá-lo e armazenar seus chunks com embeddings no pgvector, sempre vinculados ao cliente dono.

## Descrição

O README (seções 7 e 8) define o endpoint `POST /clients/{clientId}/documents` no `DocumentController` e o fluxo do `DocumentService`: extração do texto → chunking → embeddings → persistência. Cada chunk mantém a relação cliente → documento → chunk (`TextSegment` do LangChain4j) → embedding, com `client_id` e `document_id` gravados no próprio chunk. A resposta usa o DTO `DocumentResponseDTO`.

## Atores

- Usuário do cliente ou administrador que envia o documento (Não informado no README; ver RF-009).

## Pré-condições

- Cliente `{clientId}` cadastrado (RF-003).
- Banco pronto (RF-001, RF-002).
- Extração e chunking disponíveis (RF-005).
- Modelo de embeddings acessível (RF-004).
- Requisição autorizada para o cliente (RF-009).

## Fluxo principal

1. O ator envia `POST /clients/{clientId}/documents` com o arquivo PDF e o tipo do documento.
2. O sistema confirma que o cliente existe.
3. O sistema valida o arquivo e o tipo.
4. O sistema extrai o texto e divide em chunks (RF-005).
5. O sistema gera o embedding de cada chunk (RF-004).
6. Em uma única transação, o sistema grava o `document` (com `client_id` do caminho, `file_name`, `document_type` e `created_at`) e todos os `document_chunk` pelo `PgVectorEmbeddingStore`, com `client_id`, `document_id`, `chunk_index`, `document_type` e `file_name` como metadados.
7. O sistema responde `201 Created` com o `DocumentResponseDTO`.

## Fluxos alternativos

- FA-01 — `clientId` de cliente inexistente: como a chave de API sempre pertence a um cliente existente, um `clientId` diferente do da chave recebe `403 Forbidden` (FA-06), sem revelar se o cliente existe (RF-009 RN-03).
- FA-02 — Arquivo ausente, vazio ou que não é PDF: `400 Bad Request`.
- FA-03 — Tipo de documento ausente ou inválido: `400 Bad Request`.
- FA-04 — Falha na extração (RF-005 FA-01/FA-02): `422 Unprocessable Entity` (ver RF-010); nada é gravado.
- FA-05 — Falha no modelo de embeddings: erro de serviço externo (RF-010); a transação é desfeita e nada é gravado.
- FA-06 — Acesso a cliente não autorizado: `403 Forbidden` (RF-009).

## Regras de negócio

- RN-01 — O cliente dono do documento vem sempre do `{clientId}` do caminho, nunca do corpo da requisição.
- RN-02 — `created_at` é preenchido pelo sistema no momento da ingestão.
- RN-03 — Documento e chunks são gravados juntos: ou tudo, ou nada.
- RN-04 — Todo chunk gravado tem embedding.
- RN-05 — `file_name` é o nome original do arquivo enviado.

## Dados de entrada

| Campo | Tipo | Obrigatório |
|---|---|---|
| clientId | número (caminho) | Sim |
| file | arquivo PDF (multipart/form-data) | Sim |
| documentType | texto: CONTRACT, CLAIM ou INSPECTION (`DocumentTypeEnum`) | Sim |

## Dados de saída

`DocumentResponseDTO` (campos propostos, DP-01):

- `id`, `clientId`, `fileName`, `documentType`, `createdAt`, `totalChunks`.

## Critérios de aceite

- CA-01 — Enviar `contrato.pdf` do Cliente A cria 1 `document` com `client_id` de A e N chunks com embedding.
- CA-02 — O `DocumentResponseDTO` traz `totalChunks` igual à quantidade gravada.
- CA-03 — Upload para um `clientId` diferente do da chave (existente ou não) devolve 403 e não grava nada.
- CA-04 — Falha simulada no embedding (mock) não deixa `document` nem chunks gravados.
- CA-05 — Teste unitário do `DocumentService` com Mockito cobre fluxo principal e FA-05.

## Dependências

- RF-002
- RF-003
- RF-004
- RF-005
- RF-009
- RF-010

## Situação atual do projeto

- Implementado em 2026-10-02 (card HU-006, T-407 a T-410):
  - `DocumentController` (`POST /clients/{clientId}/documents`, multipart com `file` e `documentType`): arquivo ausente, vazio ou que não é PDF (`application/pdf` ou extensão `.pdf`) e tipo ausente ou inválido → 400; o dono vem só do caminho.
  - `DocumentService` (sem transação): extração → chunking → embeddings em uma chamada → `DocumentWriter`.
  - `DocumentWriter` (`@Transactional`): apaga o documento anterior do cliente com o mesmo `file_name` (DP-03), grava o `DocumentEntity` (`created_at` do sistema) e os chunks pelo `ChunkRepository`.
  - `ChunkRepository.saveAll`: `TextSegment` com metadados `client_id`, `document_id`, `chunk_index`, `document_type`, `file_name` (`COLUMN_PER_KEY`), um lote por documento; é o único que usa o `EmbeddingStore`. A busca (`searchByClient`) fica para o RF-007.
  - `DocumentResponseDTO`, `DocumentRepository.deleteByClientIdAndFileName`, `spring.servlet.multipart.max-file-size=10MB` (413 acima disso). Em 2026-10-05 o limite passou a 5 MB por decisão do usuário (DP-02) e o código foi alterado no mesmo dia: `max-file-size=5MB`, `max-request-size=6MB`, `server.tomcat.max-swallow-size=10MB` (para o 413 chegar ao cliente; achado #1 da revisão) e detail "O arquivo excede o tamanho máximo permitido de 5 MB."; CT-053 passando com a configuração real ([evidência](../QA/evidencias/execucoes/2026-10-05-rf-006-limite-5mb.md)).
- Em 2026-10-05 (T-411, [ADR-014](../arquitetura/adr/ADR-014%20Pacote%20validator%20e%20excecoes%20proprias.md)): as regras do arquivo (vazio, nome ausente, nome acima de 255 caracteres, não PDF) saíram do `DocumentController` para o `validator/DocumentFileValidator`, que lança `InvalidFileException` (400, tratada no `GlobalExceptionHandler`) no lugar do `ResponseStatusException`; status, títulos e mensagens da API não mudaram. Testes de nome de arquivo no `DocumentFileValidatorTest` (achado #3 da revisão) ([evidência](../QA/evidencias/execucoes/2026-10-05-rf-006-validator.md)).
- O rollback com falha no `saveAll` (CT-056) confirma que o `TransactionAwareDataSourceProxy` põe os chunks na transação do documento; o plano B (compensação) não foi necessário.
- Do RF-010 entraram só o necessário: `InvalidDocumentException` (422) e mensagens em português para parte ou parâmetro ausente, valor inválido e arquivo grande demais.

## Itens a implementar

- `DocumentController` (`POST /clients/{clientId}/documents`, multipart).
- `DocumentService` orquestrando RF-005 e RF-004, com `@Transactional`.
- `DocumentResponseDTO` em `br.com.rag_pgvector.dto`.
- Limite de tamanho de upload (`spring.servlet.multipart.max-file-size`).

## Decisões pendentes

- DP-01 — Campos de `DocumentResponseDTO`. **Definido na arquitetura (05):** `id`, `clientId`, `fileName`, `documentType`, `createdAt`, `totalChunks`.
- DP-02 — Tamanho máximo do arquivo. **Revisto pelo usuário em 2026-10-05: 5 MB** ("bom para um MVP"); acima disso, `413 Payload Too Large` com `ProblemDetail` de title "Arquivo muito grande" e detail "O arquivo excede o tamanho máximo permitido de 5 MB.". Antes: 10 MB, definido na arquitetura (06).
- DP-03 — Reenviar o mesmo arquivo cria outro documento ou substitui? Não informado no README. **Decidido pelo usuário em 2026-10-02: substitui.** "Mesmo arquivo" é o mesmo `fileName` para o mesmo cliente (nunca afeta outro cliente). Na mesma transação da ingestão, o documento anterior do cliente com esse `fileName` é apagado (os chunks saem pelo `ON DELETE CASCADE` da FK de `document_chunk`) e o novo é criado com id novo e seus chunks; se a ingestão falhar, o rollback mantém o anterior. A resposta continua `201 Created` com o id novo. Sem índice único `(client_id, file_name)` nem tratamento de envios simultâneos do mesmo arquivo (evolução possível). Cenário: CT-059.

## Cenários de teste (QA)

Antes de implementar, leia os cenários abaixo em [05 Cenarios de teste.md](../QA/05%20Cenarios%20de%20teste.md) e implemente os automáticos com o ID no `@DisplayName`; os marcados como manual são executados por uma pessoa pelo roteiro em [07 Testes manuais.md](../QA/07%20Testes%20manuais.md). O requisito só está pronto com todos passando ([definição de pronto](../QA/01%20Estrategia%20de%20testes.md)).

- CT-050 (P1) — Ingestão de PDF válido
- CT-051 (P2) — Metadados do chunk gravados nas colunas
- CT-052 (P2) — Validação do upload (DDT)
- CT-053 (P2) — Arquivo acima de 5 MB
- CT-054 (P2) — PDF ilegível ou sem texto não grava nada
- CT-055 (P1) — Falha no embedding não grava nada
- CT-056 (P1) — Falha ao gravar chunks desfaz o documento
- CT-057 (P1) — Upload na conta de outro cliente
- CT-058 (P1) — Dono do documento vem do caminho, data vem do sistema
- CT-059 (P3) — Reenvio do mesmo arquivo
- CT-121 (P2, manual) — Embeddings reais da OpenAI gravados com 768 dimensões
- CT-123 (P2, manual) — PDF corrompido é recusado com 422 e nada é gravado
- CT-124 (P1, manual) — Cliente envia os documentos pelo Swagger UI
- CT-125 (P1, manual) — Ninguém envia documento para a conta de outro cliente
- CT-126 (P3, manual) — Reenvio do mesmo arquivo substitui o documento
- CT-133 (P1, manual) — Falha da OpenAI vira 503 sem detalhes técnicos e sem gravação parcial
- CT-137 (P2, manual) — Upload acima de 5 MB é recusado com 413

## Prioridade

Alta — Faz parte da primeira entrega (pergunta e resposta por cliente): o usuário decidiu em 02/10/2026 (RF-008 DP-06) que todas as funcionalidades RF-001 a RF-012 entram nela.

## Status

- Status: Concluído — entregue na v0.1.0 (Produção, 2026-10-06).
- Prontidão: pronto para desenvolvimento (modelo em ADR-006, cadastro de clientes em ADR-009 e acesso em ADR-008, todas aprovadas); DP-03 decidida em 2026-10-02.
- 2026-10-02: `ChunkRepository.saveAll`, `DocumentService`, `DocumentWriter`, `DocumentController` e `DocumentResponseDTO` implementados; CT-050 a CT-059 passando, inclusive os P1 CT-050, CT-055, CT-056, CT-057 e CT-058 ([evidência](../QA/evidencias/execucoes/2026-10-02-rf-005-rf-006.md)). Aguarda a verificação do QA e a revisão de código.
- 2026-10-05: DP-02 revista pelo usuário: limite de upload passa de 10 MB para 5 MB (413 com title "Arquivo muito grande" e detail "O arquivo excede o tamanho máximo permitido de 5 MB."). CT-053 e CT-137 passam a usar 5 MB; falta ajustar o código, a arquitetura e os guias.
- 2026-10-05 (dev): código alterado para 5 MB (`application.properties`, `GlobalExceptionHandler`, `DocumentController`) e `server.tomcat.max-swallow-size=10MB` (correção proposta do achado #1 da revisão, aguardando re-revisão); `UploadLimitIT` (CT-053) sem sobrescrever o Tomcat: 5 MB + 1 byte e 6.000.000 bytes → 413, 4 MB não é 413. `mvnw verify`: 245 de 245, cobertura 97,7% das linhas ([evidência](../QA/evidencias/execucoes/2026-10-05-rf-006-limite-5mb.md)). Falta a arquitetura (06) e os guias, e o teste manual CT-137.
