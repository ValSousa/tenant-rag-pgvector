# 07 — Testes manuais

Roteiros dos testes que uma **pessoa** executa para ver a funcionalidade funcionando de verdade: Swagger UI, Postman ou `curl`, o console da IDE e o banco local (`psql`). Eles complementam os testes automáticos do `mvnw test`, não os substituem. Cada roteiro é um cenário `CT-xxx` do [catálogo](05%20Cenarios%20de%20teste.md) com execução **Manual** e entra em `testes[]` do card (HU) no dashboard.

Criado em 2026-10-02 pelo QA. Situação em 2026-10-05: executados pela pessoa e `OK` — CT-116 (HU-001), CT-117 e CT-118 (HU-002), CT-119 e CT-120 (HU-003), CT-035 (opt-in) e CT-121 (HU-004), CT-122 e CT-123 (HU-005), CT-124, CT-125, CT-126 e CT-137 (HU-006, este com o limite de 5 MB decidido pelo usuário), CT-127 e CT-128 (HU-007) e CT-075, CT-076 e CT-129 (HU-008; os dois primeiros opt-in, e o CT-075 passou na reexecução, depois de uma falha isolada no caso "perda total A"), CT-130 e CT-131 (HU-009) e CT-134 (HU-011, resultado informado pelo usuário, sem prints). O CT-132 (HU-010) foi executado em parte (falta o passo 4, arquivo que não é PDF). Os demais roteiros seguem `Não iniciada` no `Testes/Testes.json` do dashboard. Evidências em [evidencias/](evidencias/README.md). Os roteiros CT-149 e CT-150 (HU-016, RF-013, log de requisições) foram criados em 2026-10-05, antes da implementação, e só podem ser executados depois dela. Só quem executou pode informar o resultado; o QA então grava a evidência e muda o status (ver "Depois de executar").

## Resumo por card

| Card | RF | Testes manuais | Ferramenta |
|---|---|---|---|
| HU-001 | RF-001 | CT-116 | log da IDE, docker |
| HU-002 | RF-002 | CT-117, CT-118 | psql |
| HU-003 | RF-003 | CT-119, CT-120 | Swagger UI, psql; curl ou Postman |
| HU-004 | RF-004 | CT-035 (opt-in), CT-121 | mvnw (OpenAI real); Swagger UI + psql |
| HU-005 | RF-005 | CT-122, CT-123 | psql; curl ou Postman |
| HU-006 | RF-006 | CT-124, CT-125, CT-126, CT-137 (limite de 5 MB) | Swagger UI + psql; curl ou Postman; curl + psql; curl + psql |
| HU-007 | RF-007 | CT-127, CT-128 | Swagger UI; curl ou Postman |
| HU-008 | RF-008 | CT-075 (opt-in), CT-076 (opt-in), CT-129 | mvnw (OpenAI real); Swagger UI ou curl |
| HU-009 | RF-009 | CT-130, CT-131 | curl ou Postman; Swagger UI |
| HU-010 | RF-010 | CT-132, CT-133 | curl ou Postman; curl + log da IDE + psql |
| HU-011 | RF-011 | CT-134 | leitor de PDF, curl, psql |
| HU-012 | RF-012 | CT-135, CT-136 | curl ou Postman; curl ou Swagger UI |
| HU-016 | RF-013 | CT-149, CT-150 (criados antes da implementação; card Bloqueado) | log da IDE + curl ou Postman |

O CT-003 (RF-001, ambiente com `docker compose`) já era manual e está OK desde 2026-10-01; o CT-116 o repete com o que a aplicação tem hoje (migrations, chaves obrigatórias).

## Preparação comum

1. Banco e aplicação no ar conforme [Como executar](../guias/Como%20executar.md) (`.env` com `DB_USER`, `DB_PASSWORD`, `OPENAI_API_KEY` válida e `ADMIN_API_KEY`). Os testes que chamam a OpenAI gastam alguns tokens.
2. Para os ids baterem com os roteiros (Cliente A = 1, Cliente B = 2, Cliente C = 3), comece com o banco zerado: `docker compose down -v` e `docker compose up -d` (apaga os dados locais). Com o banco já usado, troque os ids pelos devolvidos no cadastro.
3. Variáveis usadas nos comandos (Git Bash; no PowerShell use `curl.exe`, ver [Uso da API](../guias/Uso%20da%20API.md)):

   ```bash
   CHAVE_ADMIN="minha-chave-local"   # o valor de ADMIN_API_KEY do seu .env
   CHAVE_CLIENTE_A="..."             # apiKey devolvida no cadastro do Cliente A (CT-119)
   CHAVE_CLIENTE_B="..."             # apiKey do Cliente B (CT-119)
   CHAVE_CLIENTE_C="..."             # apiKey do Cliente C (CT-128)
   ```

4. Banco local: `docker exec -it rag-postgres psql -U rag -d ragdb` (troque `rag` pelo `DB_USER` do `.env`; no Git Bash, se o `-it` reclamar, rode pelo PowerShell ou prefixe com `winpty`).
5. Nunca cole chave real nas evidências: troque por `***`.

### Ordem sugerida

Alguns roteiros usam dados criados por outros. Ordem que funciona do começo ao fim:

CT-116 → CT-117 → CT-131 → CT-119 → CT-120 → CT-130 → CT-124 → CT-134 → CT-121 → CT-122 → CT-118 → CT-123 → CT-125 → CT-126 → CT-137 → CT-127 → CT-128 → CT-129 → CT-136 → CT-135 → CT-132 → CT-133 (troca a chave da OpenAI; deixe por último). Os do HU-016 (CT-149 → CT-150) só existem depois da implementação do RF-013 e rodam com a chave real, antes do CT-133. Os opt-in CT-035, CT-075 e CT-076 são independentes (usam banco do Testcontainers).

---

## HU-001 / RF-001 — Ambiente local

### CT-116 — Banco e aplicação sobem pelo console da IDE

- **Card / requisito:** HU-001 / RF-001 (CA-01, CA-02, CA-03)
- **Ferramenta:** log da IDE (console) e `docker`
- **Prioridade:** P2
- **Pré-condições:** Docker no ar; `.env` preenchido; JDK 21 configurado na IDE; *Working directory* da configuração de execução = pasta do projeto (ao lado do `pom.xml`).
- **Passos:**
  1. `docker compose up -d` e depois `docker compose ps`.
  2. `docker exec rag-postgres psql -U rag -d ragdb -tAc "SELECT extname || ' ' || extversion FROM pg_extension WHERE extname = 'vector'"`.
  3. Na IDE, rode a classe `TenantRagPgvectorApplication` e acompanhe o console.
  4. Abra `http://localhost:8080/swagger-ui.html` no navegador.
- **Resultado esperado:**
  - `rag-postgres` com `STATUS` `healthy`; a consulta devolve `vector 0.x.x`.
  - No console: `HikariPool-1 - Start completed`, linha do Flyway com `jdbc:postgresql://localhost:5432/ragdb`, `Successfully applied 1 migration ... now at version v1` (banco zerado) ou `Schema "public" is up to date. No migration necessary.`, `Tomcat started on port 8080 (http)` e `Started TenantRagPgvectorApplication in ... seconds`, sem `ERROR`.
  - A página do Swagger UI abre.
- **Evidência a anexar:** saída do `docker compose ps` e da consulta, trecho do console da IDE da subida (até o `Started ...`).

---

## HU-002 / RF-002 — Esquema do banco

### CT-117 — Tabelas, índice HNSW e coluna vector(768) no banco local

- **Card / requisito:** HU-002 / RF-002 (CA-01, CA-02, RN-04)
- **Ferramenta:** `psql`
- **Prioridade:** P3
- **Pré-condições:** CT-116 executado (a aplicação subiu pelo menos uma vez e o Flyway aplicou a V1).
- **Passos:** no `psql`:
  1. `\dt`
  2. `SELECT version, description, success FROM flyway_schema_history;`
  3. `SELECT indexname, indexdef FROM pg_indexes WHERE tablename = 'document_chunk';`
  4. `SELECT format_type(atttypid, atttypmod) FROM pg_attribute WHERE attrelid = 'document_chunk'::regclass AND attname = 'embedding';`
- **Resultado esperado:**
  1. Tabelas `client`, `document`, `document_chunk` e `flyway_schema_history`.
  2. Uma linha: `1 | create tables | t`.
  3. Entre os índices, `document_chunk_embedding_idx` com `USING hnsw (embedding vector_cosine_ops)` e `document_chunk_client_id_idx`.
  4. `vector(768)`.
- **Evidência a anexar:** saída do `psql` dos quatro comandos.

### CT-118 — Banco rejeita chunk com cliente diferente do documento

- **Card / requisito:** HU-002 / RF-002 (CA-05, RN-02)
- **Ferramenta:** `psql`
- **Prioridade:** P1 (isolamento entre clientes)
- **Pré-condições:** Clientes A (id 1) e B (id 2) cadastrados (CT-119) e pelo menos um documento do Cliente A (CT-124).
- **Passos:** no `psql`, tente gravar um chunk que aponta para um documento do Cliente A, mas com `client_id` do Cliente B:

  ```sql
  INSERT INTO document_chunk (embedding_id, embedding, text, client_id, document_id, chunk_index, document_type, file_name)
  SELECT gen_random_uuid(), array_fill(0.1::real, ARRAY[768])::vector, 'teste manual CT-118', 2, d.id, 999, d.document_type, d.file_name
  FROM document d WHERE d.client_id = 1 LIMIT 1;

  SELECT count(*) FROM document_chunk WHERE text = 'teste manual CT-118';
  ```

- **Resultado esperado:** o `INSERT` falha com `violates foreign key constraint "fk_chunk_document_client"`; a contagem é `0`.
- **Evidência a anexar:** saída do `psql` com a mensagem de erro e a contagem.

---

## HU-003 / RF-003 — Cadastro de clientes

### CT-119 — Administrador cadastra Cliente A e Cliente B pelo Swagger UI

- **Card / requisito:** HU-003 / RF-003 (CA-01); RF-009 DP-01
- **Ferramenta:** Swagger UI e `psql`
- **Prioridade:** P2
- **Pré-condições:** aplicação no ar; banco zerado (ver Preparação, item 2).
- **Passos:**
  1. Abra `http://localhost:8080/swagger-ui.html`, clique em **Authorize**, informe a chave do administrador (`ADMIN_API_KEY`) no campo de `apiKey (apiKey)` e confirme.
  2. Em **Clientes**, abra `POST /clients`, **Try it out**, corpo `{"name": "Cliente A"}`, **Execute**.
  3. Repita com `{"name": "Cliente B"}`.
  4. Guarde as duas `apiKey` (ver Preparação, item 3).
  5. No `psql`: `SELECT id, name, api_key_hash, created_at FROM client ORDER BY id;`
- **Resultado esperado:**
  - As duas chamadas respondem `201`, com cabeçalho `location: /clients/1` e `/clients/2` e corpo com `id`, `name` e `apiKey` não vazia; as duas chaves são diferentes.
  - No banco, `api_key_hash` tem 64 caracteres hexadecimais e é diferente da `apiKey` devolvida (só o hash é guardado).
- **Evidência a anexar:** print do Swagger com a resposta 201 (cubra a `apiKey`) e a saída do `SELECT`.

### CT-120 — Consulta de cliente e validação do nome

- **Card / requisito:** HU-003 / RF-003 (CA-02, CA-03, FA-01, FA-02)
- **Ferramenta:** `curl` ou Postman
- **Prioridade:** P2
- **Pré-condições:** CT-119 executado.
- **Passos:**
  1. `curl -i http://localhost:8080/clients/1 -H "X-API-Key: $CHAVE_CLIENTE_A"`
  2. `curl -i http://localhost:8080/clients/1 -H "X-API-Key: $CHAVE_ADMIN"`
  3. `curl -i -X POST http://localhost:8080/clients -H "X-API-Key: $CHAVE_ADMIN" -H "Content-Type: application/json" -d '{"name": ""}'`
  4. Repita o passo 3 com `-d '{}'`.
- **Resultado esperado:**
  1. e 2. `200` com `{"id": 1, "name": "Cliente A", "createdAt": "..."}`; a resposta não traz a chave.
  3. e 4. `400`, `Content-Type: application/problem+json`, `title` `Requisição inválida` e `errors` com o campo `name`; nenhum cliente novo no banco.
- **Evidência a anexar:** saída do `curl -i` (ou print do Postman) de cada passo, com as chaves cobertas.

---

## HU-004 / RF-004 — Geração de embeddings

### CT-035 — Textos parecidos ficam mais próximos (OpenAI real, opt-in)

- **Card / requisito:** HU-004 / RF-004 (CA-02)
- **Ferramenta:** `mvnw` no terminal (teste JUnit opt-in `RagQualityOpenAiIT`, tag `openai`), executado por uma pessoa
- **Prioridade:** P3
- **Pré-condições:** Docker no ar (o teste usa o PostgreSQL do Testcontainers, não o do `docker compose`); `JAVA_HOME` no JDK 21; `OPENAI_API_KEY` definida **como variável de ambiente do terminal** (o teste não lê o `.env` e, sem a variável, é pulado em vez de falhar). Gasta poucos tokens.
- **Passos:**
  1. PowerShell, na pasta do projeto:

     ```powershell
     $env:OPENAI_API_KEY = "sk-..."
     .\mvnw.cmd test "-Dgroups=openai" "-Dtest.excluded.groups=nenhum" "-Dtest=RagQualityOpenAiIT#deveAproximarTextosParecidos"
     ```

     (Git Bash: `OPENAI_API_KEY=sk-... ./mvnw test -Dgroups=openai -Dtest.excluded.groups=nenhum -Dtest='RagQualityOpenAiIT#deveAproximarTextosParecidos'`.) Para rodar o CT-035, o CT-075 e o CT-076 de uma vez, use `-Dtest=RagQualityOpenAiIT`.
  2. Abra `target/surefire-reports/TEST-br.com.rag_pgvector.RagQualityOpenAiIT.xml`.
- **Resultado esperado:** `BUILD SUCCESS` e `Tests run: 1, Failures: 0, Errors: 0, Skipped: 0`; no XML, o caso `CT-035 — Textos parecidos ficam mais próximos (OpenAI real)` sem falha. `Skipped: 1` quer dizer que a variável não chegou ao teste: não conta como execução.
- **Evidência a anexar:** trecho final da saída do Maven (resumo dos testes) e o `testcase` do XML. Falha isolada: reexecutar uma vez antes de tratar como bug (estratégia, regra das respostas de LLM).

### CT-121 — Embeddings reais da OpenAI gravados com 768 dimensões

- **Card / requisito:** HU-004 / RF-004 (CA-01, RN-02); RF-006 CA-01
- **Ferramenta:** Swagger UI e `psql`
- **Prioridade:** P2
- **Pré-condições:** documentos do Cliente A enviados (CT-124) com a `OPENAI_API_KEY` real.
- **Passos:** no `psql`:

  ```sql
  SELECT file_name, count(*) AS chunks, min(vector_dims(embedding)) AS dim_min, max(vector_dims(embedding)) AS dim_max
  FROM document_chunk WHERE client_id = 1 GROUP BY file_name ORDER BY file_name;
  ```

- **Resultado esperado:** uma linha por arquivo (`contrato.pdf`, `sinistro.pdf`, `vistoria.pdf`), `chunks` igual ao `totalChunks` devolvido no upload e `dim_min` = `dim_max` = `768`.
- **Evidência a anexar:** saída do `SELECT`.

---

## HU-005 / RF-005 — Extração de texto e chunks

### CT-122 — Chunks do documento sem lacuna e com até 1000 caracteres

- **Card / requisito:** HU-005 / RF-005 (CA-01, CA-04, RN-01)
- **Ferramenta:** `psql`
- **Prioridade:** P3
- **Pré-condições:** `contrato.pdf` do Cliente A enviado (CT-124); anote o `id` e o `totalChunks` da resposta.
- **Passos:** no `psql` (troque `<id>` pelo id do documento):

  ```sql
  SELECT chunk_index, length(text) AS tamanho, left(text, 60) AS inicio
  FROM document_chunk WHERE document_id = <id> ORDER BY chunk_index;
  ```

- **Resultado esperado:** `chunk_index` de `0` a `totalChunks - 1`, sem lacuna nem repetição; nenhum `tamanho` acima de `1000` (`app.rag.chunk-size`) nem `0`; o início dos trechos corresponde ao texto do `contrato.pdf`.
- **Evidência a anexar:** saída do `SELECT` e a resposta do upload com o `totalChunks`.

### CT-123 — PDF corrompido é recusado com 422 e nada é gravado

- **Card / requisito:** HU-005 / RF-005 (FA-01); RF-006 FA-04; RF-010 RN-03
- **Ferramenta:** `curl` ou Postman
- **Prioridade:** P2
- **Pré-condições:** Cliente A cadastrado (CT-119).
- **Passos:**
  1. Crie um arquivo que tem extensão `.pdf` mas não é PDF: `echo "isto nao e um PDF" > corrompido.pdf`.
  2. `curl -i -X POST http://localhost:8080/clients/1/documents -H "X-API-Key: $CHAVE_CLIENTE_A" -F "file=@corrompido.pdf;type=application/pdf" -F "documentType=CONTRACT"`
  3. No `psql`: `SELECT count(*) FROM document WHERE file_name = 'corrompido.pdf';`
- **Resultado esperado:** `422`, `title` `Documento não processável`, `detail` `Não foi possível ler o arquivo PDF.`; contagem `0`.
- **Evidência a anexar:** saída do `curl -i` e do `SELECT`.

---

## HU-006 / RF-006 — Ingestão de documentos

### CT-124 — Cliente envia os documentos pelo Swagger UI

- **Card / requisito:** HU-006 / RF-006 (CA-01, CA-02, RN-01, RN-04)
- **Ferramenta:** Swagger UI e `psql`
- **Prioridade:** P1 (dono do documento e dos chunks)
- **Pré-condições:** CT-119 executado; `OPENAI_API_KEY` real.
- **Passos:**
  1. No Swagger, **Authorize** com a chave do **Cliente A** (se a do administrador estiver salva, clique em **Logout** antes).
  2. Em **Documentos**, `POST /clients/{clientId}/documents`, **Try it out**: `clientId` = `1`, `documentType` = `CONTRACT`, `file` = `documents/cliente-a/contrato.pdf`; **Execute**.
  3. Repita com `sinistro.pdf` (`CLAIM`) e `vistoria.pdf` (`INSPECTION`) da mesma pasta.
  4. No `psql`:

     ```sql
     SELECT d.id, d.client_id, d.file_name, d.document_type, d.created_at,
            count(c.embedding_id) AS chunks, min(c.client_id) AS chunk_client_min, max(c.client_id) AS chunk_client_max
     FROM document d LEFT JOIN document_chunk c ON c.document_id = d.id
     WHERE d.client_id = 1 GROUP BY d.id ORDER BY d.id;
     ```

- **Resultado esperado:** cada envio responde `201`, com `location: /clients/1/documents/{id}` e corpo com `id`, `clientId` `1`, `fileName` sem pasta, `documentType` enviado, `createdAt` e `totalChunks` maior que 0. No banco, 3 documentos do cliente 1; para cada um, `chunks` = `totalChunks` e `chunk_client_min` = `chunk_client_max` = `1`.
- **Evidência a anexar:** print do Swagger de uma das respostas 201 e a saída do `SELECT`.

### CT-125 — Ninguém envia documento para a conta de outro cliente

- **Card / requisito:** HU-006 / RF-006 (CA-03, FA-06); RF-009 CA-02
- **Ferramenta:** `curl` ou Postman
- **Prioridade:** P1 (isolamento e segurança)
- **Pré-condições:** Clientes A e B cadastrados (CT-119).
- **Passos:**
  1. No `psql`, anote `SELECT count(*) FROM document WHERE client_id = 2;`.
  2. Cliente A enviando para o Cliente B: `curl -i -X POST http://localhost:8080/clients/2/documents -H "X-API-Key: $CHAVE_CLIENTE_A" -F "file=@documents/cliente-a/contrato.pdf;type=application/pdf" -F "documentType=CONTRACT"`
  3. Cliente A enviando para um cliente que não existe: mesmo comando com `/clients/999/documents`.
  4. Administrador enviando para o Cliente A: mesmo comando com `/clients/1/documents` e `-H "X-API-Key: $CHAVE_ADMIN"`.
  5. Repita a contagem do passo 1.
- **Resultado esperado:** passos 2, 3 e 4 respondem `403`, `title` `Acesso negado`, `detail` `Você não tem permissão para acessar este recurso.` (o mesmo nos três); a contagem do Cliente B não muda.
- **Evidência a anexar:** saída do `curl -i` dos passos 2 a 4 e as duas contagens.

### CT-126 — Reenvio do mesmo arquivo substitui o documento

- **Card / requisito:** HU-006 / RF-006 (DP-03, decidida em 2026-10-02)
- **Ferramenta:** `curl` e `psql`
- **Prioridade:** P3
- **Pré-condições:** CT-124 executado (Cliente A com `contrato.pdf`) e, se possível, CT-134 (Cliente B com o próprio `contrato.pdf`).
- **Passos:**
  1. No `psql`: `SELECT id, client_id, file_name FROM document WHERE file_name = 'contrato.pdf' ORDER BY id;` e anote o id do contrato do Cliente A.
  2. `curl -i -X POST http://localhost:8080/clients/1/documents -H "X-API-Key: $CHAVE_CLIENTE_A" -F "file=@documents/cliente-a/contrato.pdf;type=application/pdf" -F "documentType=CONTRACT"`
  3. Repita o `SELECT` do passo 1 e rode `SELECT count(*) FROM document_chunk WHERE document_id = <id antigo>;`.
- **Resultado esperado:** `201` com `id` novo; o Cliente A continua com um único `contrato.pdf`, agora com o id novo; o documento antigo não tem mais chunks (contagem `0`); o `contrato.pdf` do Cliente B (se houver) mantém o mesmo id.
- **Evidência a anexar:** saída do `curl -i` e dos `SELECT` antes e depois.

### CT-137 — Upload acima de 5 MB é recusado com 413

- **Card / requisito:** HU-006 / RF-006 (DP-02); RF-010 RN-06
- **Ferramenta:** `curl` (Git Bash ou `curl.exe` no PowerShell) e `psql`
- **Prioridade:** P2
- **Limite de 5 MB:** por decisão do usuário em 2026-10-05, o limite de upload passou de 10 MB para 5 MB ("bom para um MVP"); requisito e código alterados no mesmo dia ([execução](evidencias/execucoes/2026-10-05-rf-006-limite-5mb.md)). Com uma versão anterior da aplicação (limite de 10 MB), este roteiro não vale.
- **Pré-condições:** limite de 5 MB implementado (ver acima); aplicação rodando com o `application.properties` normal (sem mudar o Tomcat); Cliente A cadastrado (CT-119).
- **Passos:**
  1. Na pasta do projeto, crie um arquivo de 6 MB (acima do limite de 5 MB). No PowerShell ou no `cmd`: `fsutil file createnew grande.pdf 6000000` (o conteúdo é só zeros; o limite é conferido antes de ler o PDF). No Git Bash, como alternativa: `dd if=/dev/zero of=grande.pdf bs=1000 count=6000`.
  2. No `psql`, anote `SELECT count(*) FROM document WHERE client_id = 1;`.
  3. `curl -i -X POST http://localhost:8080/clients/1/documents -H "X-API-Key: $CHAVE_CLIENTE_A" -F "file=@grande.pdf;type=application/pdf" -F "documentType=CONTRACT"`
  4. Repita a contagem do passo 2 e apague o arquivo (`del grande.pdf` ou `rm grande.pdf`).
- **Resultado esperado:** `413` com `ProblemDetail`: `title` `Arquivo muito grande`, `detail` `O arquivo excede o tamanho máximo permitido de 5 MB.`; a contagem de documentos do Cliente A não muda. **Atenção:** a revisão do RF-006 tem o achado #1 aberto ([RF-006 Revisao de codigo](../revisao/RF-006%20Revisao%20de%20codigo.md)): sem `server.tomcat.max-swallow-size` no `application.properties`, o Tomcat pode fechar a conexão sem devolver o 413 (o `curl` mostra erro de conexão, como `Connection reset` ou `Recv failure`, e nenhum status). Se não vier o `413`, registre o resultado como bug ligado ao achado #1 (o QA abre o `BUG-xxx`); nada deve ter sido gravado em nenhum dos casos.
- **Evidência a anexar:** saída completa do `curl -i` (chave coberta) e as duas contagens.

---

## HU-007 / RF-007 — Busca semântica

### CT-127 — Busca do Cliente A pelo Swagger UI

- **Card / requisito:** HU-007 / RF-007 (CA-01, CA-03)
- **Ferramenta:** Swagger UI
- **Prioridade:** P2
- **Pré-condições:** CT-124 executado; Swagger autorizado com a chave do Cliente A.
- **Passos:**
  1. Em **Busca**, `POST /clients/{clientId}/search`, **Try it out**, `clientId` = `1`, corpo `{"question": "Qual é o valor da franquia da apólice?", "topK": 3}`; **Execute**.
  2. Repita sem `topK` (`{"question": "Qual é o valor da franquia da apólice?"}`).
- **Resultado esperado:** `200` com `clientId` `1`, a pergunta e `results` com no máximo 3 itens (passo 1) e no máximo 5 (passo 2); cada item com `chunkId`, `documentId` (ids do Cliente A no CT-124), `fileName`, `documentType`, `chunkIndex`, `content` e `score` entre 0 e 1, em ordem decrescente de `score`; o trecho do `contrato.pdf` (`CONTRACT`) com a franquia de `R$ 4.000,00` aparece entre os resultados.
- **Evidência a anexar:** print do Swagger com a resposta do passo 1.

### CT-128 — Cliente sem documentos não vê nada dos outros

- **Card / requisito:** HU-007 / RF-007 (CA-04, FA-03); RF-008 FA-01; RF-012 CA-02
- **Ferramenta:** `curl` ou Postman
- **Prioridade:** P1 (isolamento entre clientes)
- **Pré-condições:** Clientes A e B com documentos (CT-124, CT-134).
- **Passos:**
  1. Cadastre o Cliente C como administrador: `curl -i -X POST http://localhost:8080/clients -H "X-API-Key: $CHAVE_ADMIN" -H "Content-Type: application/json" -d '{"name": "Cliente C"}'` e guarde a `apiKey` em `CHAVE_CLIENTE_C`.
  2. `curl -i -X POST http://localhost:8080/clients/3/search -H "X-API-Key: $CHAVE_CLIENTE_C" -H "Content-Type: application/json" -d '{"question": "Qual é o valor da franquia da apólice?", "topK": 20}'`
  3. `curl -i -X POST http://localhost:8080/clients/3/ask -H "X-API-Key: $CHAVE_CLIENTE_C" -H "Content-Type: application/json" -d '{"question": "Qual é o valor da franquia da apólice?"}'`
- **Resultado esperado:** passo 2: `200` com `"results": []`. Passo 3: `200` com `answer` `Não há documentos deste cliente para responder a esta pergunta.` e `sources` vazio (resposta imediata, sem chamar o modelo). Nenhum valor dos Clientes A ou B aparece.
- **Evidência a anexar:** saída do `curl -i` dos três passos (chave coberta).

---

## HU-008 / RF-008 — Resposta com base nos documentos

### CT-075 — Perguntas da seção 10 do README (OpenAI real, opt-in, DDT)

- **Card / requisito:** HU-008 / RF-008 (CA-01, CA-02, CA-03)
- **Ferramenta:** `mvnw` no terminal (teste JUnit opt-in `RagQualityOpenAiIT`, tag `openai`, massa `src/test/resources/ddt/gabarito-perguntas.csv`), executado por uma pessoa
- **Prioridade:** P2
- **Pré-condições:** as mesmas do CT-035 (Docker, JDK 21, `OPENAI_API_KEY` como variável de ambiente do terminal). Ingere os 6 PDFs de `documents/` e faz 10 perguntas ao `gpt-4o-mini`: gasta mais tokens que o CT-035.
- **Passos:**
  1. PowerShell: `$env:OPENAI_API_KEY = "sk-..."` e `.\mvnw.cmd test "-Dgroups=openai" "-Dtest.excluded.groups=nenhum" "-Dtest=RagQualityOpenAiIT"` (roda o CT-035, o CT-075 e o CT-076 juntos).
  2. Abra `target/surefire-reports/TEST-br.com.rag_pgvector.RagQualityOpenAiIT.xml`.
- **Resultado esperado:** `BUILD SUCCESS`, `Tests run: 12, Failures: 0, Errors: 0, Skipped: 0` (CT-035 + 10 casos do CT-075, as 5 perguntas para os Clientes A e B + CT-076). Cada caso do CT-075 passa quando a `answer` contém os termos esperados do gabarito (ex.: `4.000` para a franquia do A, `80%` e perda total para o A), não contém nenhum valor do outro cliente (ex.: `5.500`, `Ricardo`, `Honda` numa resposta ao A) e `sources` cita o tipo de documento esperado.
- **Evidência a anexar:** resumo da saída do Maven e os `testcase` do XML; em caso de falha, a mensagem do AssertJ (traz a `answer` completa do modelo). Falha isolada: reexecutar uma vez antes de tratar como bug. Para registrar a resposta completa também quando passa, faça as mesmas perguntas pelo `/ask` (CT-129, CT-136).
- **Pendência conhecida (decisão do usuário, 2026-10-02):** o `RagQualityOpenAiIT` só mostra a resposta do modelo quando o caso falha; fazer o teste registrar a resposta também quando passa fica para correção posterior (sem tarefa criada). Até lá, use o `/ask` como dito acima.

### CT-076 — Pergunta sem resposta nos documentos (OpenAI real, opt-in)

- **Card / requisito:** HU-008 / RF-008 (FA-02, CA-04)
- **Ferramenta:** `mvnw` no terminal (`RagQualityOpenAiIT`, tag `openai`), executado por uma pessoa
- **Prioridade:** P2
- **Pré-condições:** as mesmas do CT-035.
- **Passos:** rodar junto com o CT-075 (comando acima) ou sozinho com `"-Dtest=RagQualityOpenAiIT#deveInformarQueAInformacaoNaoFoiEncontrada"`.
- **Resultado esperado:** caso `CT-076 — Pergunta sem resposta nos documentos (OpenAI real)` sem falha: a resposta a "Qual a cor do carro do vizinho?" para o Cliente A diz que a informação não foi encontrada e não traz valor em `R$`.
- **Evidência a anexar:** a mesma do CT-075. Conferência visual opcional pelo `/ask`: `perguntar 1 "$CHAVE_CLIENTE_A" "Qual a cor do carro do vizinho?"` ([Uso da API](../guias/Uso%20da%20API.md), seção 7).

### CT-129 — Pergunta de perda total respondida com cálculo e fontes

- **Card / requisito:** HU-008 / RF-008 (CA-02, RN-03)
- **Ferramenta:** Swagger UI ou `curl`
- **Prioridade:** P2
- **Pré-condições:** documentos dos Clientes A e B enviados (CT-124, CT-134); `OPENAI_API_KEY` real.
- **Passos:**
  1. Swagger autorizado com a chave do Cliente A: em **Resposta**, `POST /clients/{clientId}/ask`, `clientId` = `1`, corpo `{"question": "O sinistro pode ser considerado perda total segundo as regras da apólice?"}`; **Execute**.
  2. Repita como Cliente B (Logout, Authorize com a chave do B, `clientId` = `2`).
- **Resultado esperado:** `200` com `answer` e `sources`. Cliente A: **sim**, com 75% (limite do contrato) e 80% (R$ 96.000,00 / R$ 120.000,00). Cliente B: **não**, com 70% e cerca de 53,7% (R$ 51.000,00 / R$ 95.000,00). A `answer` cita trechos como `[n]` e cada `ref` citado existe em `sources`; `sources` inclui `CONTRACT` e `INSPECTION`. O texto exato muda a cada execução: vale o conteúdo, comparado com o [gabarito](../../documents/gabarito.md).
- **Evidência a anexar:** print do Swagger (ou JSON completo) das duas respostas.

---

## HU-009 / RF-009 — Controle de acesso

### CT-130 — Autenticação e permissões por chave

- **Card / requisito:** HU-009 / RF-009 (CA-03, FA-01, DP-02, RN-03)
- **Ferramenta:** `curl` ou Postman
- **Prioridade:** P1 (segurança)
- **Pré-condições:** Clientes A e B cadastrados (CT-119).
- **Passos:**
  1. Sem chave: `curl -i http://localhost:8080/clients/1`
  2. Chave inexistente: `curl -i http://localhost:8080/clients/1 -H "X-API-Key: chave-que-nao-existe"`
  3. Cliente tentando cadastrar cliente: `curl -i -X POST http://localhost:8080/clients -H "X-API-Key: $CHAVE_CLIENTE_A" -H "Content-Type: application/json" -d '{"name": "Cliente X"}'`
  4. Administrador buscando nos documentos de um cliente: `curl -i -X POST http://localhost:8080/clients/1/search -H "X-API-Key: $CHAVE_ADMIN" -H "Content-Type: application/json" -d '{"question": "franquia"}'`
  5. Cliente A consultando o Cliente B (existe) e o 999 (não existe): `curl -i http://localhost:8080/clients/2 -H "X-API-Key: $CHAVE_CLIENTE_A"` e o mesmo com `/clients/999`.
  6. Cliente A consultando a si mesmo: `curl -i http://localhost:8080/clients/1 -H "X-API-Key: $CHAVE_CLIENTE_A"`
- **Resultado esperado:** 1 e 2: `401`, `title` `Não autenticado`. 3 e 4: `403`, `title` `Acesso negado`. 5: as duas respostas `403` com corpo idêntico, exceto `instance` (não dá para saber se o 999 existe). 6: `200`. Nenhum cliente "Cliente X" no banco.
- **Evidência a anexar:** saída do `curl -i` de cada passo (chaves cobertas).

### CT-131 — Swagger UI público, operações só com Authorize

- **Card / requisito:** HU-009 / RF-009 (CA-01, FA-01); ADR-013
- **Ferramenta:** Swagger UI
- **Prioridade:** P2
- **Pré-condições:** aplicação no ar; Clientes A e B cadastrados para os passos 4 e 5 (no banco zerado, faça os passos 1 a 3 antes do CT-119 e os passos 4 e 5 depois).
- **Passos:**
  1. Em uma janela anônima, abra `http://localhost:8080/swagger-ui.html` e `http://localhost:8080/v3/api-docs`.
  2. No Swagger, sem **Authorize**, rode `GET /clients/{clientId}` com `clientId` = `1`.
  3. Abra **Authorize** e confira o esquema exibido.
  4. **Authorize** com a chave do Cliente A e rode de novo `GET /clients/{clientId}` com `1` e depois com `2`.
  5. **Logout** e confira que a chamada volta a responder 401.
- **Resultado esperado:** 1: as duas páginas abrem sem chave; o Swagger lista **Clientes**, **Documentos**, **Busca** e **Resposta**; o `/v3/api-docs` tem `securitySchemes.apiKey` com `"in": "header"` e `"name": "X-API-Key"`. 2: `401`. 3: esquema `apiKey (apiKey)`, cabeçalho `X-API-Key`. 4: `200` para o 1 e `403` para o 2. 5: `401`.
- **Evidência a anexar:** prints do Swagger (lista de grupos, diálogo Authorize, respostas 401/200/403).

---

## HU-010 / RF-010 — Tratamento de erros

### CT-132 — Erros no formato ProblemDetail, em português

- **Card / requisito:** HU-010 / RF-010 (CA-01, CA-02, RN-01, RN-02, DP-02)
- **Ferramenta:** `curl` ou Postman
- **Prioridade:** P2
- **Pré-condições:** Cliente A cadastrado (CT-119).
- **Passos:**
  1. `curl -i http://localhost:8080/clients/999 -H "X-API-Key: $CHAVE_ADMIN"`
  2. `curl -i -X POST http://localhost:8080/clients/1/search -H "X-API-Key: $CHAVE_CLIENTE_A" -H "Content-Type: application/json" -d '{"question": "", "topK": 0}'`
  3. `curl -i -X POST http://localhost:8080/clients/1/documents -H "X-API-Key: $CHAVE_CLIENTE_A" -F "file=@documents/cliente-a/contrato.pdf;type=application/pdf" -F "documentType=RECEIPT"`
  4. `curl -i -X POST http://localhost:8080/clients/1/documents -H "X-API-Key: $CHAVE_CLIENTE_A" -F "file=@pom.xml;type=application/xml" -F "documentType=CONTRACT"`
- **Resultado esperado:** todas com `Content-Type: application/problem+json` e corpo com `type`, `title`, `status`, `detail` e `instance`, sem stack trace nem nome de classe Java.
  1. `404`, `title` `Recurso não encontrado`, `detail` `Cliente 999 não encontrado.`
  2. `400`, `title` `Requisição inválida`, `errors` com um item para `question` e outro para `topK`, mensagens em português.
  3. `400`, `detail` `O valor informado em 'documentType' é inválido.`
  4. `400`, `detail` `O arquivo deve ser um PDF.`
- **Evidência a anexar:** saída do `curl -i` de cada passo.

### CT-133 — Falha da OpenAI vira 503 sem detalhes técnicos e sem gravação parcial

- **Card / requisito:** HU-010 / RF-010 (RN-05, RN-07); RF-004 FA-02; RF-006 FA-05; RF-007 FA-04
- **Ferramenta:** `curl`, log da IDE e `psql`
- **Prioridade:** P1 (gravação parcial)
- **Pré-condições:** Clientes A e C cadastrados (CT-119, CT-128); Cliente C sem documentos. Deixe este teste por último: ele troca a chave da OpenAI.
- **Passos:**
  1. Pare a aplicação, troque `OPENAI_API_KEY` no `.env` (ou na configuração de execução da IDE) por `sk-invalida-teste-manual` e suba de novo pela IDE.
  2. Upload como Cliente C: `curl -i -X POST http://localhost:8080/clients/3/documents -H "X-API-Key: $CHAVE_CLIENTE_C" -F "file=@documents/cliente-a/contrato.pdf;type=application/pdf" -F "documentType=CONTRACT"`
  3. Busca como Cliente A: `curl -i -X POST http://localhost:8080/clients/1/search -H "X-API-Key: $CHAVE_CLIENTE_A" -H "Content-Type: application/json" -d '{"question": "Qual é o valor da franquia da apólice?"}'`
  4. Pergunta como Cliente A: o mesmo corpo em `/clients/1/ask`.
  5. Console da IDE: procure as linhas `ERROR` de cada chamada.
  6. No `psql`: `SELECT count(*) FROM document WHERE client_id = 3;` e `SELECT count(*) FROM document_chunk WHERE client_id = 3;`
  7. Volte a chave real no `.env` e reinicie a aplicação.
- **Resultado esperado:** passos 2 a 4: `503`, `title` `Serviço de IA indisponível`, `detail` `O serviço de IA está indisponível no momento. Tente novamente mais tarde.`, sem stack trace, sem a chave e sem a mensagem da OpenAI no corpo. Console: uma linha `ERROR ... GlobalExceptionHandler : Falha no provedor de IA: ...` por chamada, com a causa (erro de autenticação da OpenAI) e o stack trace só no log. Banco: as duas contagens `0` (nada gravado para o Cliente C).
- **Evidência a anexar:** saída do `curl -i` dos passos 2 a 4, trecho do console com uma das linhas `ERROR` (sem a chave) e as contagens.

---

## HU-011 / RF-011 — Documentos de exemplo

### CT-134 — Documentos de exemplo conferem com o gabarito e são ingeridos para os dois clientes

- **Card / requisito:** HU-011 / RF-011 (CA-01, CA-03)
- **Ferramenta:** leitor de PDF, `curl` e `psql`
- **Prioridade:** P2
- **Pré-condições:** CT-124 executado (documentos do Cliente A enviados); Cliente B cadastrado.
- **Passos:**
  1. Abra os 6 PDFs de `documents/cliente-a/` e `documents/cliente-b/` e confira, com o [gabarito](../../documents/gabarito.md) (tabela "Documentos"), o segurado, a apólice, o sinistro, o veículo e a placa de cada cliente; confira que os valores de um cliente não aparecem nos PDFs do outro (seção "Valores exclusivos").
  2. Envie os documentos do Cliente B com a função `enviar` de [Uso da API](../guias/Uso%20da%20API.md), seção 5: `enviar 2 "$CHAVE_CLIENTE_B" cliente-b`.
  3. No `psql`: `SELECT c.name, d.file_name, d.document_type FROM document d JOIN client c ON c.id = d.client_id ORDER BY c.id, d.file_name;`
- **Resultado esperado:** 1: o texto dos PDFs bate com o gabarito (A: Paulo Almeida, AP-2026-00482, PQR-4A27; B: Ricardo Martins, AP-2026-00731, RIC-7B42) e dá para selecionar o texto (não é imagem). 2: três respostas JSON com `clientId` `2` e `totalChunks` maior que 0. 3: seis linhas, três por cliente, com `CONTRACT`, `CLAIM` e `INSPECTION` para `contrato.pdf`, `sinistro.pdf` e `vistoria.pdf`.
- **Evidência a anexar:** saída do passo 2 e do `SELECT`; anotação do que foi conferido no passo 1.

---

## HU-012 / RF-012 — Isolamento entre clientes

### CT-135 — Acesso cruzado entre Cliente A e Cliente B devolve 403 em todas as rotas

- **Card / requisito:** HU-012 / RF-012 (CA-03, FA-02); RF-009 CA-02
- **Ferramenta:** `curl` ou Postman
- **Prioridade:** P1 (isolamento entre clientes)
- **Pré-condições:** Clientes A e B com documentos (CT-124, CT-134).
- **Passos:** com a chave do Cliente A, chame as rotas do Cliente B; depois, com a chave do Cliente B, as do Cliente A:

  ```bash
  cruzado() {  # uso: cruzado <chave> <clientId do outro>
    curl -s -o /dev/null -w "GET clients      %{http_code}\n" "http://localhost:8080/clients/$2" -H "X-API-Key: $1"
    curl -s -o /dev/null -w "POST documents   %{http_code}\n" -X POST "http://localhost:8080/clients/$2/documents" -H "X-API-Key: $1" \
      -F "file=@documents/cliente-a/contrato.pdf;type=application/pdf" -F "documentType=CONTRACT"
    curl -s -o /dev/null -w "POST search      %{http_code}\n" -X POST "http://localhost:8080/clients/$2/search" -H "X-API-Key: $1" \
      -H "Content-Type: application/json" -d '{"question": "Qual é o valor da franquia da apólice?"}'
    curl -s -o /dev/null -w "POST ask         %{http_code}\n" -X POST "http://localhost:8080/clients/$2/ask" -H "X-API-Key: $1" \
      -H "Content-Type: application/json" -d '{"question": "Qual é o valor da franquia da apólice?"}'
  }
  cruzado "$CHAVE_CLIENTE_A" 2
  cruzado "$CHAVE_CLIENTE_B" 1
  ```

  Antes e depois, no `psql`: `SELECT client_id, count(*) FROM document GROUP BY client_id ORDER BY client_id;`
- **Resultado esperado:** as oito chamadas respondem `403`; a contagem de documentos por cliente não muda. Mostre também o corpo de uma delas (`curl -i`): `title` `Acesso negado`, sem dados do outro cliente.
- **Evidência a anexar:** saída da função para os dois clientes e as contagens antes e depois.

### CT-136 — Mesma pergunta, cada cliente só vê os próprios dados

- **Card / requisito:** HU-012 / RF-012 (CA-01, CA-04); RF-007 CA-02; RF-008 CA-03
- **Ferramenta:** `curl` ou Swagger UI
- **Prioridade:** P1 (isolamento entre clientes)
- **Pré-condições:** Clientes A e B com os documentos de exemplo (CT-124, CT-134); `OPENAI_API_KEY` real. Os dois clientes têm arquivos com o mesmo nome (`contrato.pdf` etc.): o dono de cada resultado é conferido pelo `documentId` e pelo conteúdo.
- **Passos:**
  1. No `psql`, anote os ids dos documentos de cada cliente: `SELECT client_id, id, file_name FROM document ORDER BY client_id, id;`
  2. Busca com `topK` 20 para cada cliente:
     `curl -s -X POST http://localhost:8080/clients/1/search -H "X-API-Key: $CHAVE_CLIENTE_A" -H "Content-Type: application/json" -d '{"question": "Qual é o valor da franquia da apólice?", "topK": 20}'` e o mesmo para `/clients/2/search` com `$CHAVE_CLIENTE_B`.
  3. Pergunta para cada cliente: `perguntar 1 "$CHAVE_CLIENTE_A" "Qual é o valor da franquia da apólice?"` e `perguntar 2 "$CHAVE_CLIENTE_B" "Qual é o valor da franquia da apólice?"` (função de [Uso da API](../guias/Uso%20da%20API.md), seção 7).
- **Resultado esperado:** busca do A: todos os `documentId` são do Cliente A e nenhum `content` tem valor exclusivo do B (`R$ 5.500,00`, `Ricardo Martins`, `AP-2026-00731`, `RIC-7B42`, `Honda Civic`, lista completa no gabarito); o inverso para o B. `/ask` do A: `R$ 4.000,00` e nenhum valor do B; `/ask` do B: `R$ 5.500,00` e nenhum valor do A; os `documentId` em `sources` são do próprio cliente.
- **Evidência a anexar:** JSON das duas buscas e das duas respostas, e a saída do `SELECT`.

---

## HU-016 / RF-013 — Log de requisições

Criados em 2026-10-05, antes da implementação: o card HU-016 está Bloqueado (aprovação da ADR-015 e das DP-01 a DP-07 do RF-013) e o desenvolvimento fica para outro momento. Só execute depois que o card chegar a Em teste. O resultado esperado segue a recomendação da ADR-015; o que depende de decisão pendente está marcado com a DP e pode mudar depois da decisão do usuário (o QA ajusta o roteiro antes da execução).

### CT-149 — Linhas de log das requisições no console da IDE

- **Card / requisito:** HU-016 / RF-013 (CA-01 a CA-05, FA-01 a FA-03, RN-01 a RN-04)
- **Ferramenta:** log da IDE e `curl` (ou Postman)
- **Prioridade:** P2
- **Decisões que afetam o esperado:** DP-02 (formato texto chave=valor; se for JSON, os mesmos campos aparecem como campos do JSON), DP-03 (cabeçalho `X-Trace-Id`), DP-04 (`service`/`environment`), DP-05 (`clientId`), DP-07 (Swagger sem linha e nível)
- **Pré-condições:** aplicação rodando pela IDE ([Como executar](../guias/Como%20executar.md)), sem `APP_ENVIRONMENT` definido; Clientes A (id 1) e B (id 2) cadastrados (CT-119); limpe o console da IDE antes do passo 1.
- **Passos:**
  1. `curl -i http://localhost:8080/clients/1 -H "X-API-Key: $CHAVE_CLIENTE_A"`
  2. `curl -i http://localhost:8080/clients/1` (sem chave)
  3. `curl -i http://localhost:8080/clients/2 -H "X-API-Key: $CHAVE_CLIENTE_A"`
  4. `curl -i http://localhost:8080/clients/999 -H "X-API-Key: $CHAVE_ADMIN"`
  5. `curl -i -X POST http://localhost:8080/clients/1/search -H "X-API-Key: $CHAVE_CLIENTE_A" -H "Content-Type: application/json" -d '{"question": "", "topK": 0}'`
  6. Repita o passo 1.
  7. Abra `http://localhost:8080/swagger-ui.html` no navegador.
  8. No console da IDE, procure as linhas do `RequestLoggingFilter` (Ctrl+F `RequestLoggingFilter` ou `method=`).
- **Resultado esperado:**
  - Uma linha por chamada dos passos 1 a 6, cada uma com os dez campos: data e hora, nível, `service=tenant-rag-pgvector`, `environment=local`, `method`, `endpoint`, `status`, `clientId`, `traceId` e `duration=<n>ms`.
  - Passo 1: `INFO ... method=GET endpoint=/clients/1 status=200 clientId=1`. Passo 2: `WARN ... status=401 clientId=1`. Passo 3: `WARN ... endpoint=/clients/2 status=403 clientId=2`. Passo 4: `WARN ... endpoint=/clients/999 status=404 clientId=999`. Passo 5: `WARN ... method=POST endpoint=/clients/1/search status=400 clientId=1`. Passo 6: igual ao passo 1, com outro `traceId`.
  - O `traceId` de cada linha é o valor do cabeçalho `X-Trace-Id` da resposta do `curl -i` correspondente (32 caracteres hexadecimais) e é diferente em todas as chamadas; ele também aparece entre colchetes no começo da linha.
  - Passo 7: o Swagger abre e nenhuma linha do `RequestLoggingFilter` aparece para `/swagger-ui` ou `/v3/api-docs`.
  - As respostas (status e corpo) são as mesmas de antes do log (CT-130, CT-132).
- **Evidência a anexar:** saída do `curl -i` dos passos 1 a 6 (chaves trocadas por `***`) e trecho do console com as seis linhas.

### CT-150 — Nenhuma chave, pergunta ou nome de arquivo no console da IDE

- **Card / requisito:** HU-016 / RF-013 (CA-06, CA-07, RN-05, RN-06)
- **Ferramenta:** log da IDE e `curl` (ou Postman)
- **Prioridade:** P1 (segurança: segredos fora do log)
- **Decisões que afetam o esperado:** DP-06 (pergunta, texto e nome do arquivo, nome do cliente e query string fora do log). Chaves, hash e senha ficam fora em qualquer decisão (RN-05).
- **Pré-condições:** aplicação rodando pela IDE com a `OPENAI_API_KEY` real (o upload e o `/ask` gastam alguns tokens); Cliente A cadastrado; limpe o console da IDE antes do passo 1. Tenha à mão (sem colar na evidência) os valores de `CHAVE_CLIENTE_A`, `ADMIN_API_KEY`, `OPENAI_API_KEY` e `DB_PASSWORD` do `.env`.
- **Passos:**
  1. Upload: `curl -i -X POST http://localhost:8080/clients/1/documents -H "X-API-Key: $CHAVE_CLIENTE_A" -F "file=@documents/cliente-a/vistoria.pdf;type=application/pdf" -F "documentType=INSPECTION"` (substitui a vistoria do Cliente A pelo mesmo arquivo).
  2. Busca: `curl -i -X POST http://localhost:8080/clients/1/search -H "X-API-Key: $CHAVE_CLIENTE_A" -H "Content-Type: application/json" -d '{"question": "Quais danos foram identificados na vistoria?"}'`
  3. Pergunta: o mesmo corpo em `/clients/1/ask`.
  4. Chave inválida: `curl -i http://localhost:8080/clients/1 -H "X-API-Key: chave-invalida-manual"`
  5. Administrador: `curl -i http://localhost:8080/clients/1 -H "X-API-Key: $CHAVE_ADMIN"`
  6. Query string: `curl -i "http://localhost:8080/clients/1?token=segredo-na-query" -H "X-API-Key: $CHAVE_CLIENTE_A"`
  7. No console da IDE, use a busca (Ctrl+F) para cada valor: a chave do Cliente A, a `ADMIN_API_KEY`, `chave-invalida-manual`, a `OPENAI_API_KEY` (procure também só o começo, ex.: os 10 primeiros caracteres), a `DB_PASSWORD`, `danos foram identificados`, `vistoria.pdf`, `Cliente A` (nome do cliente) e `segredo-na-query`.
- **Resultado esperado:** passos 1 a 6 geram uma linha do `RequestLoggingFilter` cada (`201`, `200`, `200`, `401`, `200`, `200`); a do passo 6 tem `endpoint=/clients/1`, sem `?token=`. Passo 7: nenhuma das buscas encontra resultado no console (os valores secretos não aparecem em nenhuma linha, de nenhum nível, inclusive no log de inicialização).
- **Evidência a anexar:** trecho do console com as seis linhas de requisição e uma anotação com a lista de valores procurados e "0 ocorrências" para cada um (sem colar os valores secretos; escreva o nome da variável).

---

## Depois de executar

1. Grave a evidência em `docs/QA/evidencias/CT-xxx/AAAA-MM-DD.md` (modelo em [evidencias/README.md](evidencias/README.md); prints na mesma pasta; chaves cobertas).
2. Informe o resultado ao QA (`/testar-requisito`): passou → cenário `OK` com `data` e `evidencia`; falhou por defeito → `Bug` com `BUG-xxx` e o card volta para Em andamento.
3. Quando todos os testes manuais de um card estiverem `OK`, o card sai da fase Manual e vai para Em revisão.
