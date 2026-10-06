# RF-008 — Resposta a perguntas com base nos documentos

## Objetivo

Gerar uma resposta em linguagem natural para a pergunta de um cliente, usando somente os chunks recuperados dos documentos desse cliente (a etapa "Generation" do RAG).

## Descrição

O objetivo do README (seção 1) é um fluxo de RAG completo, e o fluxo de consulta (seção 9) termina em "Resposta". A seção 10 lista perguntas que o sistema deve responder, inclusive uma que cruza documentos: "O sinistro pode ser considerado perda total segundo as regras da apólice?" (percentual do contrato + custo do reparo e valor do veículo da vistoria). A busca (RF-007) só devolve chunks; transformar chunks em resposta exige um modelo de linguagem, que o README não define ("Outras tecnologias de IA somente serão adicionadas quando forem necessárias").

## Atores

- Usuário do cliente que faz a pergunta (Não informado no README; ver RF-009).
- Modelo de linguagem (sistema externo ou local).

## Pré-condições

- RF-007 concluído.
- Modelo de linguagem acessível: OpenAI `gpt-4o-mini` (DP-01, decidido).
- Requisição autorizada para o cliente (RF-009).

## Fluxo principal

1. O ator envia a pergunta para `POST /clients/{clientId}/ask` (DP-02, ADR-011).
2. O sistema recupera os top-K chunks do cliente (RF-007).
3. O sistema monta o prompt com instruções, os chunks recuperados e a pergunta.
4. O modelo de linguagem gera a resposta.
5. O sistema devolve a resposta e as fontes (documento e chunk) usadas.

## Fluxos alternativos

- FA-01 — Nenhum chunk recuperado: responder que não há documentos do cliente para responder, sem chamar o modelo.
- FA-02 — Chunks sem a informação pedida: a resposta diz que a informação não foi encontrada nos documentos do cliente.
- FA-03 — Modelo de linguagem indisponível: erro de serviço externo (RF-010).
- FA-04 — `clientId` diferente do da chave, exista ou não: 403 (RF-009).

## Regras de negócio

- RN-01 — O prompt contém apenas chunks do cliente da requisição (herdado de RF-007).
- RN-02 — O modelo é instruído a responder só com base nos trechos fornecidos e a não inventar valores.
- RN-03 — A resposta indica as fontes usadas.
- RN-04 — Perguntas que cruzam documentos (contrato + vistoria) devem recuperar chunks dos dois documentos (ver DP-04).

## Dados de entrada

| Campo | Tipo | Obrigatório |
|---|---|---|
| clientId | número (caminho) | Sim |
| question | texto | Sim |

## Dados de saída

Decidido (DP-03, ADR-011):

- `answer` (texto) e `sources` (lista com `documentId`, `fileName`, `documentType`, `chunkIndex`).

## Critérios de aceite

- CA-01 — As cinco perguntas da seção 10 do README, feitas pelos Clientes A e B sobre os documentos de exemplo (RF-011), recebem respostas que contêm os termos esperados do gabarito. Verificado com a OpenAI real, em teste opt-in.
- CA-02 — A pergunta de perda total compara o percentual do contrato com custo do reparo e valor do veículo da vistoria e conclui corretamente.
- CA-03 — Nenhuma resposta ao Cliente A menciona valores que só existem nos documentos do Cliente B.
- CA-04 — Pergunta sem resposta nos documentos recebe "informação não encontrada", e não um valor inventado. Verificado com a OpenAI real, em teste opt-in.

## Dependências

- RF-007
- RF-009
- RF-010
- RF-011

## Situação atual do projeto

Atualizado em 2026-10-02 (card HU-008, T-701 a T-703; T-705 escrita; T-704 do RF-012):

- `AiConfig`: `ChatModel` (`OpenAiChatModel`, `app.openai.chat-model=gpt-4o-mini`, `app.openai.temperature=0.1`, chave só por `OPENAI_API_KEY`) e `RagAssistant` (`service/RagAssistant`, `AiServices` sem `ContentRetriever`, prompt da [arquitetura 06, seção 3.4](../arquitetura/06%20Integracoes%20e%20configuracao.md)).
- `AnswerController` (`POST /clients/{clientId}/ask`), protegido pela regra `/clients/{clientId}/**` do `SecurityConfig` (outro cliente → 403, sem chave → 401). `AskRequestDTO` com os mesmos limites do `SearchRequestDTO` (`question` obrigatória até 2000 caracteres; `topK` opcional de 1 a 20; fora disso → 400).
- `AnswerService`: chama o `SearchService` (a mesma busca filtrada por `client_id` do RF-007) com `topK` padrão 8 (`app.rag.answer-top-k`); sem chunks responde "Não há documentos deste cliente para responder a esta pergunta." sem chamar o modelo (FA-01); senão monta o contexto `[n] (arquivo, TIPO) texto` e devolve `answer` e `sources` (`ref`, `documentId`, `fileName`, `documentType`, `chunkIndex`); falha do modelo → `AiProviderException` → 503 (FA-03).
- Testes: CT-070 a CT-074 e CT-114 (P1) passando (`AnswerServiceTest`, `AnswerControllerTest`, `ApiFlowIT`, `TenantIsolationIT`), com o `FakeChatModel` no lugar da OpenAI (o `mvnw test` não chama a OpenAI). CT-075 e CT-076 escritos em `RagQualityOpenAiIT` (opt-in, tag `openai`), não executados sem autorização do usuário; CA-01, CA-02 e CA-04 dependem deles. `mvnw verify` 174 de 174 [evidência](../QA/evidencias/execucoes/2026-10-02-rf-008.md).

## Itens a implementar

- `OpenAiChatModel` e interface `RagAssistant` (`AiServices` do LangChain4j) no `AiConfig`.
- Serviço de resposta que usa o `SearchService` e o modelo.
- `AnswerController` (`POST /clients/{clientId}/ask`), `AskRequestDTO`, `AnswerResponseDTO` e `SourceResponseDTO`.

## Decisões pendentes

- DP-01 — Modelo de linguagem. **Decidido:** OpenAI `gpt-4o-mini`, configurável, via LangChain4j (ADR-005, ADR-006).
- DP-02 — Endpoint. **Decidido:** `POST /clients/{clientId}/ask`, separado da busca (ADR-011).
- DP-03 — Formato da resposta. **Decidido:** `answer` e `sources` numeradas (ADR-011).
- DP-04 — Chunks de documentos diferentes em perguntas cruzadas. **Decidido:** começar com top-K 8 e medir; busca por tipo de documento fica como evolução (ADR-011).
- DP-05 — Cálculos. **Decidido:** feitos pelo modelo nesta etapa, com o prompt pedindo os números usados (ADR-011).
- DP-06 — Este requisito entra na primeira entrega? **Decidido (usuário, 2026-10-02):** sim. O mais importante do projeto é o usuário fazer a pergunta e receber a resposta; a primeira entrega só fica pronta com o `POST /clients/{clientId}/ask` funcionando.

## Cenários de teste (QA)

Antes de implementar, leia os cenários abaixo em [05 Cenarios de teste.md](../QA/05%20Cenarios%20de%20teste.md) e implemente os automáticos com o ID no `@DisplayName`; os marcados como manual são executados por uma pessoa pelo roteiro em [07 Testes manuais.md](../QA/07%20Testes%20manuais.md). O requisito só está pronto com todos passando ([definição de pronto](../QA/01%20Estrategia%20de%20testes.md)).

- CT-070 (P2) — Sem chunks, responde sem chamar o modelo
- CT-071 (P2) — Contexto numerado e fontes
- CT-072 (P2) — Falha do modelo de linguagem
- CT-073 (P3) — topK padrão do /ask é 8
- CT-074 (P2) — Validação do /ask (DDT)
- CT-075 (P2, manual) — Perguntas da seção 10 do README (OpenAI real, DDT)
- CT-076 (P2, manual) — Pergunta sem resposta nos documentos (OpenAI real)
- CT-114 (P1) — Contexto da resposta só tem dados do cliente
- CT-128 (P1, manual) — Cliente sem documentos não vê nada dos outros
- CT-129 (P2, manual) — Pergunta de perda total respondida com cálculo e fontes
- CT-136 (P1, manual) — Mesma pergunta, cada cliente só vê os próprios dados

## Prioridade

Alta — Faz parte da primeira entrega (pergunta e resposta por cliente): o usuário decidiu em 02/10/2026 (RF-008 DP-06) que todas as funcionalidades RF-001 a RF-012 entram nela.

## Status

- Status: Em andamento — card HU-008 Concluído em 2026-10-05 (testes automáticos e manuais OK, revisão aprovada); entra na release v0.1.0 (Planejada).
- Prontidão: pronto para desenvolvimento; todas as decisões tomadas (ADR-005, ADR-006, ADR-011; DP-06 em 2026-10-02: entra na primeira entrega). No plano, é a Etapa 7, depois do RF-007.
