# ADR-011 — Endpoint separado para a resposta gerada

- **Status:** Aprovada (decisão do responsável pelo projeto em 2026-10-01)
- **Data:** 2026-10-01
- **Requisitos:** RF-008 (resolve DP-02, DP-03, DP-04, DP-05; deixa DP-06 em aberto)

## Contexto

O README define `POST /clients/{clientId}/search` devolvendo os top-K chunks, e o fluxo de consulta termina em "Resposta". As perguntas da seção 10 pedem respostas em linguagem natural, inclusive cruzando contrato e vistoria. Gerar texto exige um modelo de linguagem, mais lento e menos previsível que a busca.

## Decisão

- Manter `/search` exatamente como o README: só recuperação, rápida e determinística.
- Criar `POST /clients/{clientId}/ask` para a geração: `AnswerService` usa o `SearchService` (top-K padrão 8) e o `RagAssistant` (`AiServices` do LangChain4j com `ChatModel` da OpenAI), sem `ContentRetriever`: os trechos chegam ao assistente já filtrados por cliente.
- Resposta com `answer` e `sources` numeradas; o prompt exige citações `[n]` e proíbe inventar valores ([06](../06%20Integracoes%20e%20configuracao.md), seção 3.4).
- Cálculos (percentual do reparo) feitos pelo modelo nesta etapa, com o prompt pedindo os números usados.
- Sem chunks recuperados, responde sem chamar o modelo.

## Alternativas consideradas

- **`AiServices` com `EmbeddingStoreContentRetriever` (RAG automático do LangChain4j):** mais parecido com uma "chain" do LangChain, mas o filtro por cliente dependeria de `dynamicFilter`; preferiu-se a busca explícita. Pode ser feito como exercício, desde que os testes de isolamento passem.
- **Um único `/search` que também gera resposta:** mistura duas operações com custos e falhas diferentes; quebra testes de busca quando o modelo cai.
- **Cálculos em código determinístico (extrair valores e calcular em Java):** mais confiável, mas exige extração estruturada de campos dos documentos — outra funcionalidade. Candidato a evolução se o gabarito mostrar erros de cálculo.
- **Busca por tipo de documento (várias buscas, uma por tipo) para perguntas cruzadas:** aumenta a chance de trazer contrato e vistoria juntos; fica como evolução se `topK = 8` não bastar.

## Consequências

- A busca pode ser entregue e testada antes da geração (Etapa 5 do plano), e a geração depois (Etapa 7).
- RF-008 DP-06 (entra ou não na primeira entrega) foi decidida pelo usuário em 2026-10-02: entra.
