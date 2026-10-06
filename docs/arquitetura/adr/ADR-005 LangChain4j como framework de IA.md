# ADR-005 — LangChain4j como framework de IA

- **Status:** Aprovada (decisão do responsável pelo projeto em 2026-10-01)
- **Data:** 2026-10-01
- **Requisitos:** RF-004, RF-005, RF-007, RF-008 (resolve RF-004 DP-02)

## Histórico

| Data | Decisão |
|---|---|
| 2026-10-01 | Versão inicial: Spring AI 2.0 apenas como cliente dos modelos. |
| 2026-10-01 | **Substituída por esta versão**: LangChain4j, para o projeto ficar semelhante a um projeto anterior feito com LangChain (Python). |

## Contexto

O responsável estuda RAG e já fez um projeto com LangChain, pgvector e Gemini/OpenAI. LangChain4j é a versão Java inspirada no LangChain, com os mesmos conceitos: `EmbeddingModel`, `ChatModel`, `EmbeddingStore`, `TextSegment`, `DocumentSplitter`, `DocumentParser` e `AiServices`. Ele tem módulos para OpenAI, Gemini e pgvector.

O LangChain4j publica starters para Spring Boot 4 (`*-spring-boot4-starter`), mas ainda em versões beta.

## Decisão

- Usar **LangChain4j** (BOM `dev.langchain4j:langchain4j-bom`, linha 1.20.x) com os módulos:
  - `langchain4j` (núcleo: `AiServices`, splitters);
  - `langchain4j-open-ai` (modelos, ADR-006);
  - `langchain4j-pgvector` (store, ADR-004);
  - `langchain4j-document-parser-apache-pdfbox` (PDF, ADR-007).
- **Sem starters Spring Boot**: os beans (`EmbeddingModel`, `ChatModel`, `EmbeddingStore<TextSegment>`, `RagAssistant`) são criados à mão em `config/AiConfig`, lendo `application.properties`. Evita depender dos starters beta para Boot 4 e deixa explícito como cada peça é montada.
- A resposta gerada usa **`AiServices`** com uma interface `RagAssistant` (`@SystemMessage`, `@UserMessage`), sem `ContentRetriever` automático. A recuperação é feita antes, de forma explícita e filtrada por cliente (ADR-011).

## Alternativas consideradas

- **Spring AI 2.0 (versão anterior):** integração nativa com Spring Boot 4, mas com conceitos e nomes diferentes dos do LangChain.
- **Starters `langchain4j-*-spring-boot4-starter`:** menos código de configuração, porém beta.
- **`AiServices` com `EmbeddingStoreContentRetriever`:** RAG automático, mais próximo do "chain" do LangChain. Rejeitado como padrão porque o filtro por cliente teria de vir por `dynamicFilter` a partir de dados da consulta, o que é menos explícito do que a busca chamada pelo service. Pode ser feito como exercício, se o filtro for comprovado pelos testes de isolamento.

## Consequências

- Os conceitos e nomes de classes batem com o que foi estudado no LangChain.
- Spring AI sai do projeto.
- O LangChain4j usa Jackson 2 internamente; o Spring Boot 4 usa Jackson 3. Os pacotes são diferentes e convivem no classpath, mas isso deve ser conferido na primeira compilação.
- Trocar OpenAI por Gemini é trocar o módulo (`langchain4j-google-ai-gemini`) e os beans em `AiConfig`; o restante do código depende só das interfaces `EmbeddingModel` e `ChatModel`.
