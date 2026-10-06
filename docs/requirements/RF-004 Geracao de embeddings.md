# RF-004 — Geração de embeddings

## Objetivo

Transformar um texto (chunk de documento ou pergunta) em um vetor numérico, usando um modelo de embeddings, para permitir a busca por similaridade.

## Descrição

O README (seção 7) define o `EmbeddingService`: "Texto → Modelo de embeddings → Vector", e prevê `AiConfig` para a configuração. O mesmo serviço é usado na ingestão (RF-006) e na busca (RF-007). O README não escolhe o modelo e avisa que a dimensão do vetor depende dele.

## Atores

- `DocumentService` (ingestão).
- `SearchService` (busca).
- Provedor do modelo de embeddings (sistema externo ou local).

## Pré-condições

- Modelo de embeddings acessível: OpenAI `text-embedding-3-small` (DP-01, decidido).
- Credenciais ou endereço do provedor configurados.

## Fluxo principal

1. O serviço chamador envia um texto não vazio ao `EmbeddingService`.
2. O `EmbeddingService` chama o modelo configurado.
3. O modelo devolve um vetor de números reais.
4. O `EmbeddingService` confere que o vetor tem a dimensão esperada e o devolve.

## Fluxos alternativos

- FA-01 — Texto vazio: rejeitar sem chamar o modelo.
- FA-02 — Provedor indisponível ou erro de autenticação: lançar exceção específica; o chamador não grava dados parciais (RF-006) e a API responde erro (RF-010).
- FA-03 — Vetor com dimensão diferente da configurada: lançar exceção de configuração.

## Regras de negócio

- RN-01 — O mesmo modelo é usado para chunks e perguntas; vetores de modelos diferentes não são comparáveis.
- RN-02 — A dimensão do vetor é igual à da coluna `document_chunk.embedding` (RF-002).
- RN-03 — A chave da OpenAI vem da variável de ambiente `OPENAI_API_KEY`, nunca do código nem do repositório.
- RN-04 — Trocar de modelo exige nova migration de dimensão e reprocessamento dos documentos.

## Dados de entrada

| Campo | Tipo | Obrigatório |
|---|---|---|
| texto | String | Sim |

## Dados de saída

- `Embedding` do LangChain4j (vetor `float[]`) com 768 dimensões.

## Critérios de aceite

- CA-01 — Dado um texto, o serviço devolve vetor com a dimensão configurada.
- CA-02 — Textos parecidos ("franquia da apólice" e "valor da franquia") têm distância de cosseno menor que textos sem relação. Verificado só com a OpenAI real, em teste opt-in (fora do `mvnw test`).
- CA-03 — Nos testes, o `EmbeddingModel` do LangChain4j é substituído por um fake determinístico, sem chamar a OpenAI.

## Dependências

- Nenhuma. A dimensão escolhida aqui é usada pela estrutura do banco.

## Situação atual do projeto

- LangChain4j 1.20.2 (`langchain4j`, `langchain4j-open-ai`), `EmbeddingService`, `AiConfig` (`EmbeddingModel`), `OpenAiProperties` e `RagProperties` implementados em 2026-10-01. Nos testes, `FakeEmbeddingModel` via `AiTestConfig`.

## Itens a implementar

- Dependências `dev.langchain4j:langchain4j` e `dev.langchain4j:langchain4j-open-ai` (BOM `langchain4j-bom`).
- `EmbeddingService` em `br.com.rag_pgvector.service`.
- `AiConfig` em `br.com.rag_pgvector.config`, criando o `OpenAiEmbeddingModel` com `dimensions = 768`.
- Propriedades do modelo em `application.properties`.

## Decisões pendentes

- DP-01 — Modelo de embeddings. **Decidido:** OpenAI `text-embedding-3-small` com `dimensions = 768` (ADR-006). Gemini `gemini-embedding-001` fica como alternativa documentada.
- DP-02 — Biblioteca de integração. **Decidido:** LangChain4j (ADR-005).

## Cenários de teste (QA)

Antes de implementar, leia os cenários abaixo em [05 Cenarios de teste.md](../QA/05%20Cenarios%20de%20teste.md) e implemente os automáticos com o ID no `@DisplayName`; os marcados como manual são executados por uma pessoa pelo roteiro em [07 Testes manuais.md](../QA/07%20Testes%20manuais.md). O requisito só está pronto com todos passando ([definição de pronto](../QA/01%20Estrategia%20de%20testes.md)).

- CT-030 (P2) — Embedding com a dimensão configurada
- CT-031 (P2) — Texto vazio não chama o modelo (DDT)
- CT-032 (P2) — Dimensão diferente gera erro de configuração
- CT-033 (P2) — Falhas do provedor viram AiProviderException (DDT)
- CT-034 (P3) — Embeddings do documento em uma única chamada
- CT-035 (P3, manual) — Textos parecidos ficam mais próximos (OpenAI real)
- CT-036 (P2) — FakeEmbeddingModel é determinístico
- CT-121 (P2, manual) — Embeddings reais da OpenAI gravados com 768 dimensões
- CT-133 (P1, manual) — Falha da OpenAI vira 503 sem detalhes técnicos e sem gravação parcial

## Prioridade

Alta — Faz parte da primeira entrega (pergunta e resposta por cliente): o usuário decidiu em 02/10/2026 (RF-008 DP-06) que todas as funcionalidades RF-001 a RF-012 entram nela.

## Status

- Status: Concluído — entregue na v0.1.0 (Produção, 2026-10-06).
- Prontidão: pronto para desenvolvimento (decisões em ADR-005 e ADR-006).
- 2026-10-01: `EmbeddingService` (`embedQuery`, `embedDocuments` em uma chamada), `AiConfig` com `OpenAiEmbeddingModel` de 768 dimensões, `FakeEmbeddingModel` e `AiTestConfig`. CT-030 a CT-034 e CT-036 passando ([evidência](../QA/evidencias/execucoes/2026-10-01-rf-003-rf-004.md)). CT-035 escrito em `RagQualityOpenAiIT` (opt-in), ainda não executado: precisa de `OPENAI_API_KEY`.
