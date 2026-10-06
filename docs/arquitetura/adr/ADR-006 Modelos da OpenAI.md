# ADR-006 — Modelos da OpenAI (embeddings de 768 dimensões)

- **Status:** Aprovada (decisão do responsável pelo projeto em 2026-10-01)
- **Data:** 2026-10-01
- **Requisitos:** RF-002, RF-004, RF-008 (resolve RF-004 DP-01, RF-002 DP-01, RF-008 DP-01)

## Histórico

| Data | Decisão |
|---|---|
| 2026-10-01 | Versão inicial: modelos locais com Ollama (`nomic-embed-text`, `llama3.2`). |
| 2026-10-01 | **Substituída por esta versão**: OpenAI, escolhida pelo responsável entre Gemini e OpenAI para manter o padrão de um projeto anterior. |

## Contexto

O README não escolhe o modelo e avisa que a dimensão `1536` é só exemplo. A dimensão define a coluna `VECTOR(n)` e não pode mudar sem reprocessar os documentos. Os documentos do projeto são fictícios (RF-011), então enviá-los a um provedor externo não expõe dado real.

## Decisão

| Uso | Modelo | Configuração |
|---|---|---|
| Embeddings | `text-embedding-3-small` | `dimensions = 768` → coluna `VECTOR(768)` |
| Chat (RF-008) | `gpt-4o-mini` (configurável) | `temperature = 0.1` |

- Chave em `OPENAI_API_KEY` (variável de ambiente), nunca no repositório.
- Os nomes dos modelos ficam em `application.properties`; trocar o modelo de chat não afeta o banco.
- `text-embedding-3-small` aceita reduzir a dimensão (`dimensions`). 768 mantém o índice leve e é suficiente para o volume do projeto.

## Alternativas consideradas

| Opção | Dimensão | Observação |
|---|---|---|
| OpenAI `text-embedding-3-small` com 768 (escolhida) | 768 | Barato; mesma dimensão já usada no esquema |
| OpenAI `text-embedding-3-small` com 1536 (padrão do modelo) | 1536 | Um pouco mais preciso; dobra o tamanho do vetor e do índice |
| Gemini `gemini-embedding-001` | 768 (configurável de 128 a 3072) | Igual ao projeto anterior; troca simples pelo módulo `langchain4j-google-ai-gemini` |
| Ollama local (versão anterior) | 768 | Gratuito e offline; não é o padrão que o responsável quer estudar |

## Consequências

- Rodar a aplicação exige internet e `OPENAI_API_KEY`; há custo por uso (baixo para o volume do projeto).
- `mvnw test` **não** chama a OpenAI: os testes usam um `EmbeddingModel` falso e um `ChatModel` mockado (ADR-012).
- O contêiner do Ollama sai do `docker-compose.yml`.
- Trocar o modelo de **embeddings** depois (inclusive para Gemini) exige: manter 768 ou criar migration para a nova dimensão, recriar o índice HNSW, ajustar `app.rag.embedding-dimension` e reprocessar todos os documentos. Embeddings de modelos diferentes não podem ser misturados na mesma tabela.
