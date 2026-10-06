# 05 — Cenários de teste

Catálogo de cenários para os desenvolvedores implementarem. Cada cenário tem ID `CT-xxx`, que vai no `@DisplayName` do teste.

**Legenda**

- **Nível:** U = unitário · W = web slice (MockMvc) · I = integração (Testcontainers) · O = opt-in com OpenAI real · M = manual
- **Prioridade:** P1 = crítico (isolamento, segurança, gravação parcial) · P2 = importante · P3 = desejável
- **Execução:** JUnit (`mvnw test`) = teste automatizado obrigatório no build · Manual = executado por uma pessoa, com evidência registrada: roteiro em [07 Testes manuais.md](07%20Testes%20manuais.md) (nível M) ou teste opt-in com OpenAI real (nível O), que só roda com `-Dgroups=openai`, gasta tokens e é disparado por uma pessoa (desde 2026-10-02 os opt-in contam como testes manuais dos cards)
- **Status e evidências:** controlados por cenário no dashboard (`Testes/Testes.json`, lista `cenarios`), conforme [01, seção 11](01%20Estrategia%20de%20testes.md)

Constantes usadas: Cliente A (`id` 1, `CHAVE_A`), Cliente B (`id` 2, `CHAVE_B`), administrador (`CHAVE_ADMIN`).

---

## RF-001 — Ambiente PostgreSQL com pgvector

### CT-001 — Extensão `vector` habilitada no banco
Nível I · P2 · `SchemaMigrationIT` · cobre RF-001 CA-02, RN-01 · Execução: JUnit (`mvnw test`)
```text
Dado o banco de teste iniciado pelo Testcontainers com a imagem pgvector/pgvector:pg17
Quando consulto SELECT extname FROM pg_extension
Então o resultado contém "vector"
```

### CT-002 — Aplicação sobe com o contexto completo
Nível I · P2 · `TenantRagPgvectorApplicationIT` · cobre RF-001 CA-03, CA-04; RF-002 FA-02; RF-012 CA-05 · Execução: JUnit (`mvnw test`)
```text
Dado o banco de teste com as migrations aplicadas
Quando o contexto Spring é carregado com ddl-auto=validate e perfil test
Então o contexto sobe sem erro
E nenhuma chamada é feita à OpenAI (FakeEmbeddingModel.chamadas() = 0)
```

### CT-003 — Ambiente local com docker compose
Nível M · P3 · cobre RF-001 CA-01, CA-03 · Execução: Manual · card HU-001
```text
Dado Docker em execução
Quando executo docker compose up -d e depois mvnw spring-boot:run
Então o contêiner rag-postgres fica healthy, com a extensão vector instalada
E a aplicação sobe conectada ao banco ragdb e responde na porta 8080
(A partir do RF-004 também são exigidas OPENAI_API_KEY e ADMIN_API_KEY; o Swagger é verificado no CT-085.)
```

### CT-116 — Banco e aplicação sobem pelo console da IDE
Nível M · P2 · roteiro em [07](07%20Testes%20manuais.md) · cobre RF-001 CA-01, CA-02, CA-03 · Execução: Manual (log da IDE, docker) · card HU-001
```text
Dado Docker no ar, .env preenchido e a aplicação configurada na IDE
Quando executo docker compose up -d e rodo TenantRagPgvectorApplication pela IDE
Então rag-postgres fica healthy com a extensão vector
E o console mostra o Flyway no ragdb, Tomcat na porta 8080 e Started TenantRagPgvectorApplication, sem ERROR
```

---

## RF-002 — Estrutura do banco e índice vetorial

### CT-010 — Migration V1 cria tabelas e índice HNSW
Nível I · P2 · `SchemaMigrationIT` · cobre RF-002 CA-01, RN-04 · Execução: JUnit (`mvnw test`)
```text
Dado um banco vazio
Quando o Flyway aplica as migrations
Então existem as tabelas client, document e document_chunk
E existe o índice document_chunk_embedding_idx do tipo hnsw com vector_cosine_ops
```

### CT-011 — Coluna de embedding com 768 dimensões
Nível I · P2 · `SchemaMigrationIT` · cobre RF-002 CA-02, RN-03 · Execução: JUnit (`mvnw test`)
```text
Dado o esquema criado
Quando consulto format_type da coluna document_chunk.embedding
Então o tipo é vector(768)
```

### CT-012 — Documento com cliente inexistente é rejeitado
Nível I · P2 · `SchemaMigrationIT` · cobre RF-002 CA-03, RN-01 · Execução: JUnit (`mvnw test`)
```text
Dado nenhum cliente com id 999
Quando insiro um document com client_id 999
Então o banco rejeita com violação de fk_document_client
```

### CT-013 — Chunk não pode ter cliente diferente do documento
Nível I · **P1** · `SchemaMigrationIT` · cobre RF-002 CA-05, RN-02 · Execução: JUnit (`mvnw test`)
```text
Dado o Cliente A com um documento D e o Cliente B
Quando insiro em document_chunk uma linha com document_id = D e client_id = B
Então o banco rejeita com violação de fk_chunk_document_client
```

### CT-014 — Tipo de documento fora da lista é rejeitado pelo banco (DDT)
Nível I · P3 · `SchemaMigrationIT` · massa `document-type-check.csv` · cobre RF-002 RN-06 · Execução: JUnit (`mvnw test`)
```text
Dado o Cliente A
Quando insiro um document com document_type = <valor da massa>
Então o insert é aceito somente para CONTRACT, CLAIM e INSPECTION
```

### CT-015 — Exclusões em cascata e bloqueadas
Nível I · P3 · `SchemaMigrationIT` · cobre RF-002 DP-04 (definido na arquitetura 04) · Execução: JUnit (`mvnw test`)
```text
Dado o Cliente A com um documento e 3 chunks
Quando apago o documento
Então os 3 chunks também são apagados
E quando tento apagar um cliente que tem documentos, o banco rejeita
```

### CT-016 — Entidades JPA gravam e leem client e document
Nível I · P2 · `EntityMappingIT` · cobre RF-002 CA-04 (parte JPA) · Execução: JUnit (`mvnw test`)
```text
Dado o Cliente A gravado pelo ClientRepository e um documento INSPECTION gravado pelo DocumentRepository
Quando leio o cliente pelo hash da chave e o documento pelo id
Então os campos lidos são iguais aos gravados (created_at com precisão de microssegundos)
E a coluna document_type guarda o texto "INSPECTION"
```

### CT-117 — Tabelas, índice HNSW e coluna vector(768) no banco local
Nível M · P3 · roteiro em [07](07%20Testes%20manuais.md) · cobre RF-002 CA-01, CA-02, RN-04 · Execução: Manual (psql) · card HU-002
```text
Dado a aplicação já subiu no banco local
Quando consulto \dt, flyway_schema_history, pg_indexes e format_type da coluna embedding pelo psql
Então existem client, document e document_chunk, a V1 aplicada com sucesso, o índice hnsw com vector_cosine_ops e a coluna vector(768)
```

### CT-118 — Banco rejeita chunk com cliente diferente do documento
Nível M · **P1** · roteiro em [07](07%20Testes%20manuais.md) · cobre RF-002 CA-05, RN-02 · Execução: Manual (psql) · card HU-002
```text
Dado um documento do Cliente A no banco local e o Cliente B cadastrado
Quando insiro pelo psql um chunk com document_id do Cliente A e client_id do Cliente B
Então o banco rejeita com violação de fk_chunk_document_client e nada é gravado
```

---

## RF-003 — Cadastro de clientes

### CT-020 — Administrador cadastra cliente
Nível W + I · **P1** · `ClientControllerTest`, `ApiFlowIT` · cobre RF-003 CA-01 · Execução: JUnit (`mvnw test`)
```text
Dado a chave de administrador
Quando envio POST /clients com {"name": "Cliente A"}
Então recebo 201
E o cabeçalho Location é /clients/{id}
E o corpo tem id, name e apiKey não vazia
```

### CT-021 — Validação do nome do cliente (DDT)
Nível W · P2 · `ClientControllerTest` · massa `client-name-validation.csv` · cobre RF-003 CA-03, RN-01, FA-01 · Execução: JUnit (`mvnw test`)
```text
Dado a chave de administrador
Quando envio POST /clients com name = <valor da massa>
Então recebo o status esperado da massa
E, quando 400, o ProblemDetail lista o campo "name" em errors
```

### CT-023 — Consulta de cliente existente
Nível I · P2 · `ApiFlowIT` · cobre RF-003 CA-02 · Execução: JUnit (`mvnw test`)
```text
Dado um cliente criado pelo administrador
Quando envio GET /clients/{id} com a chave de administrador
Então recebo 200 com id, name e createdAt
```

### CT-024 — Consulta de cliente inexistente pelo administrador
Nível W · P2 · `ClientControllerTest` · cobre RF-003 FA-02; RF-010 CA-01 · Execução: JUnit (`mvnw test`)
```text
Dado a chave de administrador e nenhum cliente 999999
Quando envio GET /clients/999999
Então recebo 404 com Content-Type application/problem+json e title "Recurso não encontrado"
```

### CT-025 — Cliente consulta a si mesmo, mas não a outro
Nível W · **P1** · `SecurityConfigTest` (linhas da matriz CT-080) · cobre RF-003 FA-02, RF-009 RN-01 · Execução: JUnit (`mvnw test`)
```text
Dado a chave do Cliente A
Quando envio GET /clients/1
Então recebo 200
E quando envio GET /clients/2, recebo 403
```

### CT-026 — Chave gerada é aleatória e só o hash é guardado
Nível U · **P1** · `ClientServiceTest` · cobre RF-009 DP-01 (ADR-008) · Execução: JUnit (`mvnw test`)
```text
Dado o ClientService com ClientRepository mockado
Quando crio dois clientes
Então as duas chaves devolvidas são diferentes e têm pelo menos 43 caracteres
E o ClientEntity passado ao repository tem apiKeyHash = SHA-256 da chave
E a chave em texto não é passada ao repository
```

### CT-027 — Cliente não pode cadastrar clientes
Nível W · **P1** · `SecurityConfigTest` (matriz CT-080) · cobre RF-009 DP-02 · Execução: JUnit (`mvnw test`)
```text
Dado a chave do Cliente A
Quando envio POST /clients
Então recebo 403
```

### CT-119 — Administrador cadastra Cliente A e Cliente B pelo Swagger UI
Nível M · P2 · roteiro em [07](07%20Testes%20manuais.md) · cobre RF-003 CA-01; RF-009 DP-01 · Execução: Manual (Swagger UI, psql) · card HU-003
```text
Dado o Swagger UI autorizado com a chave do administrador
Quando cadastro "Cliente A" e "Cliente B" em POST /clients
Então recebo 201 com Location e apiKey diferentes
E no banco só o hash SHA-256 (64 hexadecimais) de cada chave
```

### CT-120 — Consulta de cliente e validação do nome
Nível M · P2 · roteiro em [07](07%20Testes%20manuais.md) · cobre RF-003 CA-02, CA-03, FA-01, FA-02 · Execução: Manual (curl ou Postman) · card HU-003
```text
Dado os Clientes A e B cadastrados
Quando consulto GET /clients/1 como Cliente A e como administrador e cadastro com name vazio ou ausente
Então a consulta responde 200 com id, name e createdAt, sem a chave
E o cadastro inválido responde 400 com o campo name em errors
```

---

## RF-004 — Geração de embeddings

### CT-030 — Embedding com a dimensão configurada
Nível U · P2 · `EmbeddingServiceTest` · cobre RF-004 CA-01 · Execução: JUnit (`mvnw test`)
```text
Dado um EmbeddingModel mockado que devolve vetor de 768 posições
Quando chamo embedQuery("franquia")
Então recebo um Embedding com 768 posições
```

### CT-031 — Texto vazio não chama o modelo (DDT)
Nível U · P2 · `EmbeddingServiceTest` · massa `@NullAndEmptySource` + espaços · cobre RF-004 FA-01 · Execução: JUnit (`mvnw test`)
```text
Dado o EmbeddingService
Quando chamo embedQuery com texto nulo, vazio ou só espaços
Então recebo IllegalArgumentException
E o EmbeddingModel não é chamado
```

### CT-032 — Dimensão diferente gera erro de configuração
Nível U · P2 · `EmbeddingServiceTest` · cobre RF-004 FA-03, RN-02 · Execução: JUnit (`mvnw test`)
```text
Dado um EmbeddingModel que devolve vetor de 1536 posições
Quando chamo embedQuery
Então recebo AiProviderException com "dimensão" na mensagem
```

### CT-033 — Falhas do provedor viram AiProviderException (DDT)
Nível U · P2 · `EmbeddingServiceTest` · cobre RF-004 FA-02 · Execução: JUnit (`mvnw test`)
```text
Dado um EmbeddingModel que lança <timeout | 401 | 429 | 500>
Quando chamo embedQuery ou embedDocuments
Então recebo AiProviderException com a exceção original como causa
```

### CT-034 — Embeddings do documento em uma única chamada
Nível U · P3 · `EmbeddingServiceTest` · cobre arquitetura 06, seção 3.3 · Execução: JUnit (`mvnw test`)
```text
Dado 12 segmentos de texto
Quando chamo embedDocuments
Então EmbeddingModel.embedAll é chamado exatamente uma vez com os 12 segmentos
```

### CT-035 — Textos parecidos ficam mais próximos (OpenAI real)
Nível O · P3 · `RagQualityOpenAiIT` · cobre RF-004 CA-02 · Execução: Manual (opt-in com OpenAI real, `-Dgroups=openai`; roteiro em [07](07%20Testes%20manuais.md)) · card HU-004
```text
Dado o OpenAiEmbeddingModel real
Quando calculo a similaridade de "franquia da apólice" com "valor da franquia" e com "data do sinistro"
Então a similaridade com "valor da franquia" é maior
```

### CT-036 — FakeEmbeddingModel é determinístico
Nível U · P2 · `FakeEmbeddingModelTest` · cobre RF-004 CA-03, RF-012 RN-02 · Execução: JUnit (`mvnw test`)
```text
Dado o FakeEmbeddingModel
Quando gero o embedding do mesmo texto duas vezes
Então os vetores são iguais, com 768 posições e norma 1
E um vetor registrado com fixo("franquia") é devolvido exatamente
```

### CT-121 — Embeddings reais da OpenAI gravados com 768 dimensões
Nível M · P2 · roteiro em [07](07%20Testes%20manuais.md) · cobre RF-004 CA-01, RN-02; RF-006 CA-01 · Execução: Manual (Swagger UI, psql) · card HU-004
```text
Dado os documentos do Cliente A enviados com a OPENAI_API_KEY real
Quando consulto vector_dims(embedding) dos chunks do Cliente A pelo psql
Então cada arquivo tem tantos chunks quanto o totalChunks do upload, todos com 768 dimensões
```

---

## RF-005 — Extração de texto e divisão em chunks

### CT-040 — Extração de PDF com texto
Nível U · P2 · `PdfTextExtractorTest` · cobre RF-005 fluxo principal · Execução: JUnit (`mvnw test`)
```text
Dado TestPdfFactory.comTexto("A franquia é de R$ 3.500,00.")
Quando extraio o texto
Então o resultado contém "A franquia é de R$ 3.500,00."
```

### CT-041 — PDF corrompido ou vazio
Nível U · P2 · `PdfTextExtractorTest` · cobre RF-005 FA-01 · Execução: JUnit (`mvnw test`)
```text
Dado TestPdfFactory.corrompido() ou TestPdfFactory.vazio()
Quando extraio o texto
Então recebo InvalidDocumentException com "Não foi possível ler o arquivo PDF."
```

### CT-042 — PDF sem texto extraível
Nível U · P2 · `PdfTextExtractorTest` · cobre RF-005 FA-02, CA-03 · Execução: JUnit (`mvnw test`)
```text
Dado TestPdfFactory.semTexto()
Quando extraio o texto
Então recebo InvalidDocumentException com "O documento não contém texto extraível."
```

### CT-043 — Texto curto gera um único chunk
Nível U · P2 · `TextChunkerTest` · cobre RF-005 FA-03 · Execução: JUnit (`mvnw test`)
```text
Dado um texto de 300 caracteres
Quando divido em chunks
Então recebo 1 chunk com índice 0
```

### CT-044 — Contrato do chunking para vários tamanhos (DDT)
Nível U · P2 · `TextChunkerTest` · massa `textosParaChunking()` · cobre RF-005 CA-01, CA-02, RN-01, RN-02, RN-04 · Execução: JUnit (`mvnw test`)
```text
Dado o texto da massa e chunk-size 1000 / overlap 200
Quando divido em chunks
Então os índices são 0..n-1 sem lacuna
E nenhum chunk está em branco
E nenhum chunk passa de 1000 caracteres
E toda palavra do texto aparece em algum chunk
```

### CT-045 — Tamanho e sobreposição vêm da configuração
Nível U · P3 · `TextChunkerTest` · cobre RF-005 CA-04 · Execução: JUnit (`mvnw test`)
```text
Dado RagProperties com chunk-size 500 e overlap 50
Quando divido um texto de 3000 caracteres
Então nenhum chunk passa de 500 caracteres
```

### CT-122 — Chunks do documento sem lacuna e com até 1000 caracteres
Nível M · P3 · roteiro em [07](07%20Testes%20manuais.md) · cobre RF-005 CA-01, CA-04, RN-01 · Execução: Manual (psql) · card HU-005
```text
Dado o contrato.pdf do Cliente A enviado
Quando listo chunk_index e length(text) dos chunks do documento pelo psql
Então os índices vão de 0 a totalChunks - 1 sem lacuna
E nenhum chunk está vazio ou passa de 1000 caracteres
```

### CT-123 — PDF corrompido é recusado com 422 e nada é gravado
Nível M · P2 · roteiro em [07](07%20Testes%20manuais.md) · cobre RF-005 FA-01; RF-006 FA-04; RF-010 RN-03 · Execução: Manual (curl ou Postman) · card HU-005
```text
Dado um arquivo corrompido.pdf que não é PDF
Quando o Cliente A envia o arquivo em POST /clients/1/documents
Então recebo 422 "Documento não processável" com "Não foi possível ler o arquivo PDF."
E nenhum documento corrompido.pdf é gravado
```

---

## RF-006 — Ingestão de documentos do cliente

### CT-050 — Ingestão de PDF válido
Nível I · **P1** · `DocumentIngestionIT` (+ parte U em `DocumentServiceTest`) · cobre RF-006 CA-01, CA-02, RN-04 · Execução: JUnit (`mvnw test`)
```text
Dado o Cliente A e um PDF de 3500 caracteres
Quando envio POST /clients/1/documents com documentType CONTRACT e a chave do Cliente A
Então recebo 201 com id, clientId 1, fileName, documentType CONTRACT, createdAt e totalChunks
E document_chunk tem totalChunks linhas com esse document_id
E todas essas linhas têm client_id = 1 e embedding preenchido
```

### CT-051 — Metadados do chunk gravados nas colunas
Nível I · P2 · `ChunkRepositoryIT` (+ parte U em `ChunkRepositoryTest`) · cobre RF-006 fluxo principal passo 6; RF-002 CA-04 · Execução: JUnit (`mvnw test`)
```text
Dado um documento ingerido
Quando consulto document_chunk
Então client_id, document_id, chunk_index, document_type e file_name têm os valores do documento
E chunk_index vai de 0 a n-1
```

### CT-052 — Validação do upload (DDT)
Nível W · P2 · `DocumentControllerTest` · massa da seção 3.7 do documento 04 · cobre RF-006 FA-02, FA-03 · Execução: JUnit (`mvnw test`)
```text
Dado a chave do Cliente A
Quando envio POST /clients/1/documents com <arquivo, nome, content-type, documentType da massa>
Então recebo o status esperado
E, quando 400, o DocumentService não é chamado
```

### CT-053 — Arquivo acima de 5 MB
Nível I (servidor real) · P2 · `UploadLimitIT` · cobre RF-006 DP-02; RF-010 RN-06 · Execução: JUnit (`mvnw test`)
```text
Dado a aplicação em porta aleatória
Quando envio um arquivo de 5 MB + 1 byte
Então recebo 413 com title "Arquivo muito grande" e detail "O arquivo excede o tamanho máximo permitido de 5 MB."
E quando envio um arquivo de 6.000.000 bytes (tamanho do CT-137), também recebo 413
E quando envio um arquivo de 4 MB, o status não é 413
```

### CT-054 — PDF ilegível ou sem texto não grava nada
Nível I · P2 · `DocumentIngestionIT` (+ parte U) · cobre RF-006 FA-04 · Execução: JUnit (`mvnw test`)
```text
Dado o Cliente A
Quando envio um PDF corrompido ou sem texto
Então recebo 422 com a mensagem do RF-005
E document e document_chunk continuam vazios
```

### CT-055 — Falha no embedding não grava nada
Nível U + I · **P1** · `DocumentServiceTest`, `DocumentIngestionIT` · cobre RF-006 FA-05, CA-04, CA-05 · Execução: JUnit (`mvnw test`)
```text
Dado o Cliente A e o FakeEmbeddingModel configurado para falhar na próxima chamada
Quando envio um PDF válido
Então recebo 503 com title "Serviço de IA indisponível"
E document e document_chunk continuam vazios
```

### CT-056 — Falha ao gravar chunks desfaz o documento
Nível I · **P1** · `DocumentIngestionIT` · cobre RF-006 RN-03 · Execução: JUnit (`mvnw test`)
```text
Dado o Cliente A e o ChunkRepository (spy) configurado para lançar exceção em saveAll
Quando envio um PDF válido
Então recebo 500
E document continua vazio (o INSERT do documento foi desfeito)
```

### CT-057 — Upload na conta de outro cliente
Nível I · **P1** · `DocumentIngestionIT` · cobre RF-006 FA-01, FA-06, CA-03 · Execução: JUnit (`mvnw test`)
```text
Dado o Cliente A e o Cliente B
Quando envio POST /clients/2/documents com a chave do Cliente A
Então recebo 403
E nenhum documento é gravado para o Cliente B
```

### CT-058 — Dono do documento vem do caminho, data vem do sistema
Nível I · **P1** · `DocumentIngestionIT` · cobre RF-006 RN-01, RN-02, RN-05 · Execução: JUnit (`mvnw test`)
```text
Dado o Cliente A e o Cliente B
Quando envio POST /clients/1/documents com a chave do Cliente A e uma parte extra clientId=2
Então o documento gravado tem client_id = 1
E created_at está preenchido com o horário da ingestão
E file_name é o nome original do arquivo enviado
```

### CT-059 — Reenvio do mesmo arquivo
Nível I · P3 · `DocumentIngestionIT` (+ parte U em `DocumentWriterTest`) · cobre RF-006 DP-03 (decidida em 2026-10-02: reenvio substitui) · Execução: JUnit (`mvnw test`)
```text
Dado o Cliente A com contrato.pdf já ingerido e o Cliente B com o próprio contrato.pdf
Quando o Cliente A envia contrato.pdf de novo, com outro conteúdo
Então recebo 201 com um id novo
E existe um único documento contrato.pdf do Cliente A, com os chunks do conteúdo novo
E os chunks do documento antigo não existem mais
E os documentos e chunks do Cliente B não mudam
E, se a gravação do reenvio falhar, o documento antigo do Cliente A continua igual (rollback)
```

### CT-124 — Cliente envia os documentos pelo Swagger UI
Nível M · **P1** · roteiro em [07](07%20Testes%20manuais.md) · cobre RF-006 CA-01, CA-02, RN-01, RN-04 · Execução: Manual (Swagger UI, psql) · card HU-006
```text
Dado o Swagger UI autorizado com a chave do Cliente A
Quando envio contrato.pdf, sinistro.pdf e vistoria.pdf do Cliente A
Então cada envio responde 201 com Location, clientId 1 e totalChunks
E no banco cada documento tem totalChunks chunks, todos com client_id 1
```

### CT-125 — Ninguém envia documento para a conta de outro cliente
Nível M · **P1** · roteiro em [07](07%20Testes%20manuais.md) · cobre RF-006 CA-03, FA-06; RF-009 CA-02 · Execução: Manual (curl ou Postman) · card HU-006
```text
Dado os Clientes A e B cadastrados
Quando o Cliente A envia para /clients/2 e /clients/999 e o administrador envia para /clients/1
Então as três respostas são 403 "Acesso negado" com o mesmo detail
E a quantidade de documentos do Cliente B não muda
```

### CT-126 — Reenvio do mesmo arquivo substitui o documento
Nível M · P3 · roteiro em [07](07%20Testes%20manuais.md) · cobre RF-006 DP-03 · Execução: Manual (curl, psql) · card HU-006
```text
Dado o Cliente A com contrato.pdf já enviado
Quando o Cliente A envia contrato.pdf de novo
Então recebo 201 com id novo
E o Cliente A tem um único contrato.pdf, o antigo sem chunks, e o contrato.pdf do Cliente B não muda
```

### CT-137 — Upload acima de 5 MB é recusado com 413
Nível M · P2 · roteiro em [07](07%20Testes%20manuais.md) · cobre RF-006 DP-02; RF-010 RN-06 · Execução: Manual (curl, psql) · card HU-006

Limite reduzido de 10 MB para 5 MB por decisão do usuário em 2026-10-05 ("bom para um MVP"). Só pode ser executado depois que o RF-006 DP-02 / RF-010 RN-06 e o código forem alterados (analista de requisitos e desenvolvedor).

```text
Dado a aplicação rodando com a configuração normal (sem alterar o Tomcat) e o limite de 5 MB
Quando o Cliente A envia um arquivo de 6 MB para /clients/1/documents
Então recebo 413 com title "Arquivo muito grande" e detail "O arquivo excede o tamanho máximo permitido de 5 MB."
E a quantidade de documentos do Cliente A não muda
(Se não vier 413: bug ligado ao achado #1 da revisão do RF-006, server.tomcat.max-swallow-size não definido)
```

---

## RF-007 — Busca semântica por cliente

### CT-060 — Busca devolve chunks do cliente ordenados por relevância
Nível I · P2 · `ChunkRepositoryIT` (+ parte U em `SearchServiceTest`) · cobre RF-007 CA-01, CA-03, RN-03 · Execução: JUnit (`mvnw test`)
```text
Dado o Cliente A com chunks "franquia", "vidros" e "assistência"
E a pergunta registrada com o vetor de "franquia"
Quando envio POST /clients/1/search com a chave do Cliente A
Então recebo 200 com o chunk de "franquia" em primeiro
E os resultados estão em ordem decrescente de score
E há no máximo 5 resultados
```

### CT-061 — Validação e padrão de topK (DDT)
Nível W + U · P2 · `SearchControllerTest`, `SearchServiceTest` · massa `topk-validation.csv` · cobre RF-007 RN-04, DP-02 · Execução: JUnit (`mvnw test`)
```text
Dado a chave do Cliente A e uma pergunta válida
Quando envio topK = <valor da massa>
Então recebo o status esperado
E, quando 200, o SearchService recebe o topK esperado (5 quando ausente)
```

### CT-062 — Validação da pergunta (DDT)
Nível W · P2 · `SearchControllerTest` · massa `question-validation.csv` · cobre RF-007 FA-01; RF-010 CA-02 · Execução: JUnit (`mvnw test`)
```text
Dado a chave do Cliente A
Quando envio question = <valor da massa>
Então recebo o status esperado
E, quando 400, o ProblemDetail lista "question" em errors
```

### CT-063 — Cliente sem documentos recebe lista vazia
Nível I · P2 · `ApiFlowIT` · cobre RF-007 FA-03, CA-04 · Execução: JUnit (`mvnw test`)
```text
Dado o Cliente A sem documentos
Quando envio POST /clients/1/search
Então recebo 200 com results vazio
```

### CT-064 — Falha no embedding da pergunta
Nível W · P2 · `SearchControllerTest` · cobre RF-007 FA-04 · Execução: JUnit (`mvnw test`)
```text
Dado o SearchService mockado lançando AiProviderException
Quando envio POST /clients/1/search
Então recebo 503 com title "Serviço de IA indisponível"
```

### CT-065 — O filtro client_id é sempre enviado ao store
Nível U · **P1** · `ChunkRepositoryTest` · cobre RF-007 RN-01, CA-05 · Execução: JUnit (`mvnw test`)
```text
Dado o ChunkRepository com EmbeddingStore mockado
Quando chamo searchByClient(42, embedding, 5)
Então o EmbeddingSearchRequest enviado tem filtro IsEqualTo("client_id", 42) e maxResults 5
```

### CT-066 — Faixa e significado do score
Nível I · P3 · `ChunkRepositoryIT` · cobre arquitetura 04, seção 5.2 · Execução: JUnit (`mvnw test`)
```text
Dado um chunk com o mesmo vetor da pergunta e outros com vetores ortogonais
Quando busco
Então o chunk idêntico tem score ≥ 0,999
E todos os scores estão entre 0 e 1
```

### CT-127 — Busca do Cliente A pelo Swagger UI
Nível M · P2 · roteiro em [07](07%20Testes%20manuais.md) · cobre RF-007 CA-01, CA-03 · Execução: Manual (Swagger UI) · card HU-007
```text
Dado os documentos do Cliente A enviados e o Swagger UI autorizado com a chave dele
Quando busco "Qual é o valor da franquia da apólice?" com topK 3 e sem topK
Então recebo 200 com no máximo 3 (e 5) resultados do Cliente A, score entre 0 e 1 em ordem decrescente
E o trecho do contrato.pdf com a franquia de R$ 4.000,00 aparece
```

### CT-128 — Cliente sem documentos não vê nada dos outros
Nível M · **P1** · roteiro em [07](07%20Testes%20manuais.md) · cobre RF-007 CA-04, FA-03; RF-008 FA-01; RF-012 CA-02 · Execução: Manual (curl ou Postman) · card HU-007
```text
Dado os Clientes A e B com documentos e o Cliente C recém-cadastrado
Quando o Cliente C busca com topK 20 e pergunta no /ask
Então a busca responde 200 com results vazio
E o /ask responde "Não há documentos deste cliente para responder a esta pergunta." com sources vazio
```

---

## RF-008 — Resposta a perguntas com base nos documentos

### CT-070 — Sem chunks, responde sem chamar o modelo
Nível U · P2 · `AnswerServiceTest` · cobre RF-008 FA-01 · Execução: JUnit (`mvnw test`)
```text
Dado o SearchService devolvendo lista vazia
Quando peço uma resposta
Então answer é "Não há documentos deste cliente para responder a esta pergunta."
E sources está vazio
E o RagAssistant não é chamado
```

### CT-071 — Contexto numerado e fontes
Nível U · P2 · `AnswerServiceTest` · cobre RF-008 RN-03, DP-03 · Execução: JUnit (`mvnw test`)
```text
Dado o SearchService devolvendo 3 chunks
Quando peço uma resposta
Então o context enviado ao RagAssistant tem as linhas [1], [2] e [3] com arquivo e tipo
E sources tem ref 1, 2 e 3 com documentId, fileName, documentType e chunkIndex
```

### CT-072 — Falha do modelo de linguagem
Nível U + W · P2 · `AnswerServiceTest`, `AnswerControllerTest` · cobre RF-008 FA-03 · Execução: JUnit (`mvnw test`)
```text
Dado o RagAssistant lançando exceção
Quando envio POST /clients/1/ask
Então recebo 503 com title "Serviço de IA indisponível"
```

### CT-073 — topK padrão do /ask é 8
Nível U · P3 · `AnswerServiceTest` · cobre RF-008 DP-04 · Execução: JUnit (`mvnw test`)
```text
Dado uma pergunta sem topK
Quando peço uma resposta
Então o SearchService é chamado com topK 8
```

### CT-074 — Validação do /ask (DDT)
Nível W · P2 · `AnswerControllerTest` · massas `question-validation.csv` e `topk-validation.csv` · cobre RF-008 fluxo principal · Execução: JUnit (`mvnw test`)
```text
Dado a chave do Cliente A
Quando envio POST /clients/1/ask com question e topK da massa
Então recebo o status esperado
```

### CT-075 — Perguntas da seção 10 do README (OpenAI real, DDT)
Nível O · P2 · `RagQualityOpenAiIT` · massa `gabarito-perguntas.csv` · cobre RF-008 CA-01, CA-02, CA-03 · Execução: Manual (opt-in com OpenAI real, `-Dgroups=openai`; roteiro em [07](07%20Testes%20manuais.md)) · card HU-008
```text
Dado os 6 PDFs de exemplo ingeridos para os Clientes A e B
Quando cada cliente faz as 5 perguntas da seção 10
Então cada answer contém os termosEsperados do gabarito
E não contém nenhum termoProibido (valores do outro cliente)
E sources cita o tipo de documento esperado
```

### CT-076 — Pergunta sem resposta nos documentos (OpenAI real)
Nível O · P2 · `RagQualityOpenAiIT` · cobre RF-008 FA-02, CA-04 · Execução: Manual (opt-in com OpenAI real, `-Dgroups=openai`; roteiro em [07](07%20Testes%20manuais.md)) · card HU-008
```text
Dado os documentos do Cliente A ingeridos
Quando pergunto "Qual a cor do carro do vizinho?"
Então answer diz que a informação não foi encontrada
E answer não contém valor monetário
```

### CT-129 — Pergunta de perda total respondida com cálculo e fontes
Nível M · P2 · roteiro em [07](07%20Testes%20manuais.md) · cobre RF-008 CA-02, RN-03 · Execução: Manual (Swagger UI ou curl) · card HU-008
```text
Dado os documentos de exemplo dos Clientes A e B enviados com a OpenAI real
Quando cada cliente pergunta no /ask se o sinistro é perda total
Então o Cliente A recebe sim (80% > 75%) e o Cliente B recebe não (53,7% < 70%)
E a answer cita [n] que existem em sources, com CONTRACT e INSPECTION
```

---

## RF-009 — Controle de acesso por cliente

### CT-080 — Matriz de acesso por chave e rota (DDT)
Nível W · **P1** · `SecurityConfigTest` · massa `security-matrix.csv` · cobre RF-009 CA-01, CA-02, CA-03, CA-04, FA-01, FA-02 · Execução: JUnit (`mvnw test`)
```text
Dado a chave da linha (nenhuma, inválida, admin, Cliente A ou Cliente B)
Quando chamo a rota e o método da linha
Então recebo o status esperado (401, 403 ou permitido)
```

### CT-081 — 403 não revela se o outro cliente existe
Nível W · **P1** · `SecurityConfigTest` · cobre RF-009 RN-03 · Execução: JUnit (`mvnw test`)
```text
Dado a chave do Cliente A
Quando envio POST /clients/2/search (cliente existe) e POST /clients/999/search (não existe)
Então as duas respostas têm status 403 e corpos idênticos (exceto instance)
```

### CT-082 — Acesso negado não chega ao service
Nível W · **P1** · `SecurityConfigTest` · cobre RF-009 FA-02, RN-02 · Execução: JUnit (`mvnw test`)
```text
Dado a chave do Cliente A
Quando envio POST /clients/2/search, /clients/2/documents e /clients/2/ask
Então recebo 403 em todas
E SearchService, DocumentService e AnswerService não são chamados
```

### CT-083 — Chave guardada só como hash
Nível U + I · **P1** · `ApiKeyHasherTest`, `ApiFlowIT` · cobre RF-009 DP-01 (ADR-008) · Execução: JUnit (`mvnw test`)
```text
Dado um cliente criado pela API
Quando consulto client.api_key_hash
Então o valor é o SHA-256 hexadecimal da apiKey devolvida, e não a própria chave
```

### CT-084 — clientId não numérico
Nível W · P2 · `SecurityConfigTest` (linha da matriz) · cobre RF-009 RN-01 · Execução: JUnit (`mvnw test`)
```text
Dado a chave do Cliente A
Quando envio POST /clients/abc/search
Então recebo 403
```

### CT-085 — Documentação pública, operações protegidas
Nível W · P2 · `SecurityConfigTest` (linhas da matriz) · cobre ADR-013 · Execução: JUnit (`mvnw test`)
```text
Dado nenhuma chave
Quando acesso /v3/api-docs, /swagger-ui.html e /swagger-ui/index.html
Então /v3/api-docs e /swagger-ui/index.html respondem 200 e /swagger-ui.html redireciona (3xx) para o index
E /v3/api-docs declara o esquema de segurança apiKey no cabeçalho X-API-Key
```

### CT-130 — Autenticação e permissões por chave
Nível M · **P1** · roteiro em [07](07%20Testes%20manuais.md) · cobre RF-009 CA-03, FA-01, DP-02, RN-03 · Execução: Manual (curl ou Postman) · card HU-009
```text
Dado os Clientes A e B cadastrados
Quando chamo a API sem chave, com chave inexistente, como cliente em POST /clients, como administrador na busca e como Cliente A em /clients/2 e /clients/999
Então recebo 401 sem chave ou com chave inexistente e 403 nos demais
E os 403 de /clients/2 e /clients/999 têm o mesmo corpo, exceto instance
```

### CT-131 — Swagger UI público, operações só com Authorize
Nível M · P2 · roteiro em [07](07%20Testes%20manuais.md) · cobre RF-009 CA-01, FA-01; ADR-013 · Execução: Manual (Swagger UI) · card HU-009
```text
Dado a aplicação no ar e uma janela anônima do navegador
Quando abro o Swagger UI e o /v3/api-docs e chamo GET /clients/{clientId} sem e com Authorize
Então as páginas abrem sem chave e declaram o esquema apiKey no cabeçalho X-API-Key
E a operação responde 401 sem Authorize, 200 para o próprio cliente e 403 para outro
```

---

## RF-010 — Tratamento padronizado de erros

### CT-090 — Mapeamento de exceções para HTTP (DDT)
Nível W · P2 · `GlobalExceptionHandlerTest` · massa `excecoes()` · cobre RF-010 RN-01 a RN-06, CA-04 · Execução: JUnit (`mvnw test`)
```text
Dado um controller de teste que lança a exceção da massa
Quando chamo o endpoint
Então recebo o status e o title esperados
```

### CT-091 — Formato ProblemDetail
Nível W · P2 · `GlobalExceptionHandlerTest` · cobre RF-010 DP-02 · Execução: JUnit (`mvnw test`)
```text
Dado qualquer erro tratado
Quando recebo a resposta
Então Content-Type é application/problem+json
E o corpo tem type, title, status, detail e instance
```

### CT-092 — Erro interno sem detalhes técnicos
Nível W · P2 · `GlobalExceptionHandlerTest` · cobre RF-010 FA-01, CA-03, RN-07 · Execução: JUnit (`mvnw test`)
```text
Dado um controller de teste que lança IllegalStateException("SELECT * FROM client")
Quando chamo o endpoint
Então recebo 500 com title "Erro interno"
E o corpo não contém "SELECT", "Exception", "at br.com." nem a mensagem original
```

### CT-093 — Erros de validação listam os campos
Nível W · P2 · `GlobalExceptionHandlerTest` · cobre RF-010 RN-02, CA-02 · Execução: JUnit (`mvnw test`)
```text
Dado uma requisição com question vazia e topK 0
Quando chamo POST /clients/1/search
Então recebo 400
E errors contém um item para "question" e um para "topK", com mensagens em português
```

### CT-132 — Erros no formato ProblemDetail, em português
Nível M · P2 · roteiro em [07](07%20Testes%20manuais.md) · cobre RF-010 CA-01, CA-02, RN-01, RN-02, DP-02 · Execução: Manual (curl ou Postman) · card HU-010
```text
Dado o Cliente A cadastrado
Quando provoco 404 (cliente 999 pelo administrador) e 400 (busca com question vazia e topK 0, upload com documentType inválido e com arquivo que não é PDF)
Então cada resposta é application/problem+json com type, title, status, detail e instance em português, sem stack trace
E o 400 da busca lista question e topK em errors
```

### CT-133 — Falha da OpenAI vira 503 sem detalhes técnicos e sem gravação parcial
Nível M · **P1** · roteiro em [07](07%20Testes%20manuais.md) · cobre RF-010 RN-05, RN-07; RF-004 FA-02; RF-006 FA-05; RF-007 FA-04 · Execução: Manual (curl, log da IDE, psql) · card HU-010
```text
Dado a aplicação reiniciada com OPENAI_API_KEY inválida
Quando faço upload como Cliente C, busca e /ask como Cliente A
Então as três respostas são 503 "Serviço de IA indisponível" sem stack trace nem mensagem da OpenAI
E o console mostra ERROR "Falha no provedor de IA" com a causa
E nada é gravado para o Cliente C
```

---

## RF-011 — Documentos de exemplo por cliente

### CT-100 — PDFs de exemplo com texto (DDT)
Nível I · P2 · `SampleDocumentsIT` · massa: arquivos de `documents/cliente-*/` · cobre RF-011 CA-01 · depende do `PdfTextExtractor` (T-405) · Execução: JUnit (`mvnw test`)
```text
Dado cada um dos 6 PDFs de documents/
Quando extraio o texto com o PdfTextExtractor
Então o texto não é vazio
```

### CT-101 — Sem valores cruzados entre clientes
Nível I · P2 · `SampleDocumentsIT` · cobre RF-011 CA-03, RN-02 · depende do `PdfTextExtractor` (T-405) · Execução: JUnit (`mvnw test`)
```text
Dado os valores exclusivos de cada cliente listados no gabarito (seção "Valores exclusivos")
Quando procuro os valores do Cliente A no texto dos PDFs do Cliente B (e vice-versa)
Então nenhum é encontrado
```

### CT-102 — Gabarito completo
Nível I · P3 · `SampleDocumentsIT` · cobre RF-011 CA-02, RN-04 · Execução: JUnit (`mvnw test`)
```text
Dado documents/gabarito.md
Quando o leio
Então há resposta para as 5 perguntas da seção 10 para cada cliente
E um cliente é perda total e o outro não (A: 80% > 75%, sim; B: 53,7% < 70%, não)
```

### CT-134 — Documentos de exemplo conferem com o gabarito e são ingeridos para os dois clientes
Nível M · P2 · roteiro em [07](07%20Testes%20manuais.md) · cobre RF-011 CA-01, CA-03 · Execução: Manual (leitor de PDF, curl, psql) · card HU-011
```text
Dado os 6 PDFs de documents/ e o gabarito
Quando abro os PDFs, comparo com o gabarito e envio os documentos do Cliente B
Então os dados de cada PDF batem com o gabarito, sem valores do outro cliente
E os envios respondem 201 e o banco tem 3 documentos por cliente com os tipos certos
```

---

## RF-012 — Testes de isolamento entre clientes

### CT-110 — Chunk idêntico de outro cliente não aparece
Nível I · **P1** · `TenantIsolationIT` · cobre RF-012 CA-01; RF-007 CA-02 · Execução: JUnit (`mvnw test`)
```text
Dado o Cliente A com chunks de vetor "vidros"
E o Cliente B com um chunk de vetor idêntico ao da pergunta "franquia"
Quando o Cliente A busca com topK 20
Então há resultados, todos com client_id = A
E o texto do chunk do Cliente B não aparece
```

### CT-111 — Cliente vazio não vê dados de outro
Nível I · **P1** · `TenantIsolationIT` · cobre RF-012 CA-02, FA-01 · Execução: JUnit (`mvnw test`)
```text
Dado o Cliente A sem documentos e o Cliente B com 10 chunks
Quando o Cliente A busca
Então a lista de resultados é vazia
```

### CT-112 — topK alto não "completa" com dados de outro cliente
Nível I · **P1** · `TenantIsolationIT` · cobre RF-012 fluxo principal; arquitetura 04, seção 5.3 · Execução: JUnit (`mvnw test`)
```text
Dado o Cliente A com 3 chunks e o Cliente B com 50
Quando o Cliente A busca com topK 20
Então recebo exatamente 3 resultados, todos do Cliente A
```

### CT-113 — Matriz de isolamento A↔B (DDT)
Nível I · **P1** · `TenantIsolationIT` · massa `isolation-matrix.csv` · cobre RF-012 CA-01, CA-02 · Execução: JUnit (`mvnw test`)
```text
Dado os chunks e o vetor de pergunta da linha
Quando o consultante da linha busca
Então os resultados são todos do consultante, ou a lista é vazia, conforme a massa
```

### CT-114 — Contexto da resposta só tem dados do cliente
Nível I · **P1** · `TenantIsolationIT` · cobre RF-012 CA-04; RF-008 RN-01 · Execução: JUnit (`mvnw test`)
```text
Dado chunks no Cliente A e no Cliente B, com o do Cliente B idêntico à pergunta
E o RagAssistant mockado
Quando envio POST /clients/1/ask com a chave do Cliente A
Então o argumento context capturado só contém textos do Cliente A
```

### CT-115 — Acesso cruzado por credencial em todas as rotas de dados
Nível W · **P1** · `SecurityConfigTest` (linhas da matriz CT-080) · cobre RF-012 CA-03 · Execução: JUnit (`mvnw test`)
```text
Dado a chave do Cliente A
Quando chamo search, documents e ask do Cliente B
Então recebo 403 em todas
```

### CT-135 — Acesso cruzado entre Cliente A e Cliente B devolve 403 em todas as rotas
Nível M · **P1** · roteiro em [07](07%20Testes%20manuais.md) · cobre RF-012 CA-03, FA-02; RF-009 CA-02 · Execução: Manual (curl ou Postman) · card HU-012
```text
Dado os Clientes A e B com documentos
Quando cada um chama GET /clients, documents, search e ask do outro
Então as oito chamadas respondem 403
E a quantidade de documentos de cada cliente não muda
```

### CT-136 — Mesma pergunta, cada cliente só vê os próprios dados
Nível M · **P1** · roteiro em [07](07%20Testes%20manuais.md) · cobre RF-012 CA-01, CA-04; RF-007 CA-02; RF-008 CA-03 · Execução: Manual (curl ou Swagger UI) · card HU-012
```text
Dado os documentos de exemplo dos Clientes A e B enviados com a OpenAI real
Quando os dois fazem a mesma pergunta de franquia na busca (topK 20) e no /ask
Então todos os resultados e fontes de cada um são dos próprios documentos
E nenhum valor exclusivo do outro cliente (gabarito) aparece; A recebe R$ 4.000,00 e B R$ 5.500,00
```

---

## RF-013 — Log de requisições para rastreamento

Criados em 2026-10-05 a partir do RF-013, da ADR-015 e da [estratégia de testes da arquitetura, seção 6.1](../arquitetura/08%20Estrategia%20de%20testes.md); revisados em 2026-10-06, depois que o usuário aprovou a ADR-015 e decidiu as DP-01 a DP-07 do RF-013 como a recomendação. O resultado esperado abaixo segue **as DP decididas em 2026-10-06** (texto chave=valor; trace ID gerado pela aplicação, 32 hexadecimais, devolvido em `X-Trace-Id`, sem aceitar trace ID de entrada; `service` = `spring.application.name`, `environment` = `app.logging.environment`, padrão `local`; `clientId` do caminho `/clients/{clientId}` ou `-`; fora do log: corpo, pergunta, texto e nome do arquivo, nome do cliente, query string, cabeçalhos, IP e User-Agent; Swagger e `/v3/api-docs` sem linha; `INFO` < 400, `WARN` 4xx, `ERROR` 5xx). O campo **DP** de cada cenário diz em qual decisão o esperado se baseia (todas decididas em 2026-10-06). Card HU-016 (Aguardando).

Regras para todos: o log é lido com o `OutputCaptureExtension` do Spring Boot (`CapturedOutput`); comparar com `contains`/`doesNotContain` sobre a linha do `RequestLoggingFilter`, sem depender do prefixo do Logback (data, PID, thread); para casar um campo exato, comparar `campo=valor` seguido de espaço ou fim de linha (`endpoint=/clients/1 ` não casa com `endpoint=/clients/10`). Nada chama a OpenAI (`FakeEmbeddingModel` e `FakeChatModel`).

### CT-138 — Uma linha com os dez campos por requisição
Nível U · P2 · `RequestLoggingFilterTest` · cobre RF-013 CA-01, RN-02, RN-03, RN-06, fluxo principal · DP: DP-02 (formato chave=valor), DP-04 (valores de `service`/`environment`), decididas em 2026-10-06 · Execução: JUnit (`mvnw test`)
```text
Dado o RequestLoggingFilter criado com service "tenant-rag-pgvector" e environment "local"
E uma cadeia (MockFilterChain) que responde 200 a GET /clients/1
Quando o filtro processa a requisição
Então o log capturado tem exatamente uma linha do RequestLoggingFilter
E ela tem service=tenant-rag-pgvector, environment=local, method=GET, endpoint=/clients/1, status=200, clientId=1, traceId=<não vazio> e duration=<inteiro >= 0>ms
E não tem outro campo chave=valor além desses oito (nada de cabeçalhos, IP ou User-Agent; a requisição leva User-Agent "AgenteRastreioLog/1.0" e esse valor não aparece)
E timestamp e level vêm do padrão do log (a linha é INFO)
```

### CT-139 — endpoint sem query string e clientId tirado do caminho (DDT)
Nível U · P2 · `RequestLoggingFilterTest` · massa `@MethodSource` (ver [04, seção 3.11](04%20Testes%20orientados%20a%20dados.md)) · cobre RF-013 CA-02, CA-10 (`clientId=-`), FA-03, RN-06, RN-09 · DP: DP-05 (`clientId`), DP-06 (query string fora), decididas em 2026-10-06 · Execução: JUnit (`mvnw test`)
```text
Dado a requisição da linha da massa (/clients/1, /clients/1/search, /clients/42/ask, /clients, /clients/abc/search, /actuator/env, /clients/1?token=segredo-na-query)
Quando o filtro processa a requisição
Então endpoint é o caminho sem query string (/clients/1 na última linha)
E clientId é o número de /clients/{clientId} (1, 1, 42) ou "-" quando o caminho não tem número de cliente
E a linha não contém "segredo-na-query" nem "token="
```

### CT-140 — Falha na cadeia registra status=500 e a exceção continua
Nível U · P2 · `RequestLoggingFilterTest` · cobre RF-013 FA-02, RN-01, RN-07, RN-08 · DP: DP-07 (nível `ERROR`), decidida em 2026-10-06 · Execução: JUnit (`mvnw test`)
```text
Dado uma cadeia que lança RuntimeException em POST /clients/1/ask
Quando o filtro processa a requisição
Então a mesma exceção é relançada pelo filtro
E o log tem uma linha com status=500, endpoint=/clients/1/ask e nível ERROR
E o MDC não tem mais a chave traceId
```

### CT-141 — traceId novo a cada requisição, no MDC só durante a requisição
Nível U · P2 · `RequestLoggingFilterTest` · cobre RF-013 CA-05, FA-05, RN-04 · DP: DP-03 (origem, formato e cabeçalho `X-Trace-Id`), decidida em 2026-10-06 · Execução: JUnit (`mvnw test`)
```text
Dado duas requisições seguidas sem trace ID de entrada
E uma cadeia que guarda o valor de MDC.get("traceId") durante a chamada
Quando o filtro processa as duas
Então cada linha tem um traceId de 32 caracteres hexadecimais, e os dois são diferentes
E o traceId da linha é igual ao visto no MDC durante a cadeia e ao cabeçalho X-Trace-Id da resposta
E depois de cada chamada MDC.get("traceId") é null
E uma requisição com cabeçalho de entrada X-Trace-Id "abc%0AINFO falso" recebe um traceId gerado (o valor de entrada não aparece no log)
```

### CT-142 — Nível do log pelo status e rotas de apoio sem linha (DDT)
Nível U · P3 · `RequestLoggingFilterTest` · massa `@MethodSource` (ver [04, seção 3.11](04%20Testes%20orientados%20a%20dados.md)) · cobre RF-013 CA-10, FA-04, RN-01, RN-08 · DP: DP-07 (rotas de apoio e nível), decidida em 2026-10-06 · Execução: JUnit (`mvnw test`)
```text
Dado uma cadeia que responde com o status da massa (200, 201, 302, 400, 401, 403, 404, 413, 500, 503)
Quando o filtro processa a requisição
Então a linha é INFO para status < 400, WARN para 4xx e ERROR para 5xx
E GET /swagger-ui.html, /swagger-ui/index.html e /v3/api-docs não geram linha do RequestLoggingFilter
```

### CT-143 — Linha de log para 200, 201, 401, 403 e 404 pela API (DDT)
Nível W · P2 · `RequestLoggingWebTest` (web slice com `WebSliceTest`) · massa `request-log-matrix.csv` · cobre RF-013 CA-01, CA-02, CA-03, CA-10 (`POST /clients` com `clientId=-`), FA-01, FA-03, RN-01, RN-08, RN-09 · DP: DP-05 (`clientId` nos 401/403, nas chamadas do administrador e nas rotas sem cliente), DP-06 (query string), DP-07 (nível), decididas em 2026-10-06 · Execução: JUnit (`mvnw test`)
```text
Dado a chave, o método e a rota da linha da massa (services mockados como no WebSliceTest; corpo válido nas linhas POST)
Quando chamo a API pelo MockMvc
Então a resposta tem o status da massa
E o log capturado tem uma linha do RequestLoggingFilter com method, endpoint, status, clientId e nível da massa e um traceId não vazio
E as requisições recusadas pela segurança (401, 403) também têm a linha
E o texto capturado não contém o valor da coluna naoContem
```

### CT-144 — Erro 500: o mesmo traceId na linha da requisição e no ERROR do GlobalExceptionHandler
Nível W · P2 · `GlobalExceptionHandlerTest` (`ThrowingController`) · cobre RF-013 CA-04, FA-02, RN-04; RF-010 FA-01 · DP: DP-03 (trace ID em todas as linhas da requisição, via `logging.pattern.correlation`), decidida em 2026-10-06 · Execução: JUnit (`mvnw test`)
```text
Dado o ThrowingController de teste que lança IllegalStateException
Quando chamo o endpoint com a chave do dono
Então a resposta é 500 "Erro interno", como antes
E o log tem a linha do RequestLoggingFilter com status=500 e traceId=<T>
E a linha ERROR "Erro inesperado" do GlobalExceptionHandler contém o mesmo valor <T>
```

### CT-145 — Resposta da API igual com e sem o log
Nível U + W · P2 · `RequestLoggingFilterTest` e `RequestLoggingWebTest` · cobre RF-013 CA-08, RN-07, passo 5 do fluxo principal · DP: DP-03 (o cabeçalho `X-Trace-Id` é o único acréscimo permitido), decidida em 2026-10-06 · Execução: JUnit (`mvnw test`)
```text
Dado uma cadeia que escreve status 201, Content-Type application/json, um cabeçalho Location e um corpo JSON
Quando a chamo direto e depois através do RequestLoggingFilter
Então status, corpo e cabeçalhos são iguais nas duas respostas, exceto o X-Trace-Id acrescentado
E no web slice os testes existentes de ClientControllerTest, SearchControllerTest, AnswerControllerTest, DocumentControllerTest, GlobalExceptionHandlerTest e SecurityConfigTest continuam passando sem mudar as asserções de status e corpo
```

### CT-146 — Nenhuma chave, hash ou senha aparece no log
Nível I · **P1** · `RequestLoggingIT` (sobre o `AbstractIntegrationTest`, `OutputCaptureExtension`) · cobre RF-013 CA-06, CA-09, RN-05 · DP: — (os segredos nunca entram no log, RN-05) · Execução: JUnit (`mvnw test`)
```text
Dado o contêiner de teste com uma senha distinta (ex.: withPassword("senha-db-log-it") no PostgresTestcontainersConfig)
E a chave de administrador test-admin-key e a chave falsa da OpenAI test-key-nao-usada do perfil test
Quando o administrador cadastra um cliente (guardo a apiKey devolvida e o api_key_hash gravado)
E esse cliente faz upload, busca, ask e GET /clients/{id}, e há também uma chamada com a chave inválida "chave-invalida-log-it" e uma ao id de outro cliente (403)
Então todas as requisições geram a linha do RequestLoggingFilter
E o texto capturado desde o início do teste não contém a apiKey do cliente, o api_key_hash, test-admin-key, test-key-nao-usada, senha-db-log-it nem chave-invalida-log-it
E nenhuma chamada foi feita à OpenAI (os fakes atenderam)
```

### CT-147 — Linhas de /ask, /search e /documents sem pergunta, texto, nome de arquivo ou nome do cliente
Nível I · **P1** · `RequestLoggingIT` · cobre RF-013 CA-07 (`/ask`, `/search` e `/documents`), RN-06 · DP: DP-06 (o que fica fora do log), decidida em 2026-10-06 · Execução: JUnit (`mvnw test`)
```text
Dado um cliente cadastrado com o nome "Segurado Rastreio Log"
Quando ele envia o PDF "apolice-rastreio-log.pdf" com o texto "Franquia exclusiva R$ 7.777,77"
E pergunta "Qual a franquia rastreio log?" no /search e no /ask
E as três requisições levam o cabeçalho User-Agent "AgenteRastreioLog/1.0"
Então as linhas do RequestLoggingFilter dessas requisições existem, com status 201 e 200
E o texto capturado não contém "Segurado Rastreio Log", "apolice-rastreio-log.pdf", "7.777,77", "franquia rastreio log" nem "AgenteRastreioLog"
```

### CT-148 — Configuração versionada não liga log de cabeçalhos, SQL com parâmetros ou da OpenAI
Nível U · **P1** · `LoggingConfigurationTest` · cobre RF-013 RN-05; ADR-015, decisão item 7 · DP: — · Execução: JUnit (`mvnw test`)
```text
Dado os arquivos src/main/resources/application*.properties e src/test/resources/application-test.properties
Quando leio as propriedades
Então nenhuma logging.level de root, org.springframework.web, org.springframework.security, org.hibernate.orm.jdbc.bind, dev.langchain4j ou de cliente HTTP é DEBUG ou TRACE
E spring.jpa.show-sql não é true
E nenhuma propriedade liga log-requests ou log-responses
```

### CT-149 — Linhas de log das requisições no console da IDE
Nível M · P2 · roteiro em [07](07%20Testes%20manuais.md) · cobre RF-013 CA-01 a CA-05, CA-10, FA-01 a FA-04, RN-01 a RN-04, RN-08, RN-09 · DP: DP-02, DP-03, DP-04, DP-05, DP-07, decididas em 2026-10-06 · Execução: Manual (log da IDE, curl ou Postman) · card HU-016
```text
Dado a aplicação rodando pela IDE e os Clientes A e B cadastrados
Quando faço GET /clients/1 com a chave do A (200), sem chave (401), com a chave do A em /clients/2 (403), GET /clients/999 com a chave de administrador (404) e uma busca com question vazia (400)
Então cada chamada gera uma linha no console com os dez campos e o status devolvido
E o traceId da linha é o mesmo do cabeçalho X-Trace-Id da resposta e é diferente em cada chamada
E abrir o Swagger UI não gera linhas de requisição
```

### CT-150 — Nenhuma chave, pergunta ou nome de arquivo no console da IDE
Nível M · **P1** · roteiro em [07](07%20Testes%20manuais.md) · cobre RF-013 CA-06, CA-07, RN-05, RN-06 · DP: DP-06 (o que fica fora), decidida em 2026-10-06 · Execução: Manual (log da IDE, curl ou Postman) · card HU-016
```text
Dado a aplicação rodando pela IDE com a OPENAI_API_KEY real
Quando o Cliente A faz upload, busca e /ask, há uma chamada com chave inválida e uma GET /clients/1?token=segredo-na-query
Então a busca no console (Ctrl+F) não encontra a chave do Cliente A, a de administrador, a chave inválida, a OPENAI_API_KEY, a DB_PASSWORD, o texto da pergunta, o nome do arquivo, segredo-na-query nem o User-Agent enviado
E as linhas de requisição dessas chamadas estão lá, com endpoint sem query string
```
