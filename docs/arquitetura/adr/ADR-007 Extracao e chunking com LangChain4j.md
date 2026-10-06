# ADR-007 — Extração e chunking com LangChain4j (PDFBox + splitter recursivo)

- **Status:** Aprovada (decisão do responsável pelo projeto em 2026-10-01)
- **Data:** 2026-10-01
- **Requisitos:** RF-005 (resolve DP-01, DP-02, DP-03)

## Histórico

| Data | Decisão |
|---|---|
| 2026-10-01 | Versão inicial: PDFBox direto e `TextChunker` próprio. |
| 2026-10-01 | **Revisada**: com a adoção do LangChain4j (ADR-005), usar o parser e o splitter da biblioteca, equivalentes ao `PyPDFLoader` e ao `RecursiveCharacterTextSplitter` do LangChain. |

## Contexto

O fluxo de ingestão do README começa por "PDF → Extração do texto → Chunking". O responsável quer reproduzir o padrão do LangChain. O LangChain4j oferece `ApachePdfBoxDocumentParser` e `DocumentSplitters.recursive(...)`, que divide por parágrafo, depois por linha, frase e palavra até caber no tamanho máximo.

## Decisão

- **Extração:** `ApachePdfBoxDocumentParser` (módulo `langchain4j-document-parser-apache-pdfbox`), em `PdfTextExtractor`.
- **Chunking:** `DocumentSplitters.recursive(chunkSize, chunkOverlap)` em caracteres, com `app.rag.chunk-size = 1000` e `app.rag.chunk-overlap = 200`, em `TextChunker`.
- `PdfTextExtractor` e `TextChunker` continuam como classes do projeto (pacote `ingestion`), que envolvem a biblioteca, validam o resultado e numeram os chunks (`chunk_index` a partir de 0).
- Texto vazio após a extração → `InvalidDocumentException` (422).
- Só PDF com texto; PDF escaneado é rejeitado.

## Alternativas consideradas

- **Chunking próprio (versão anterior):** mais didático sobre o algoritmo, mas diferente do que foi usado no LangChain.
- **Splitter por tokens (`DocumentSplitters.recursive(maxTokens, overlapTokens, tokenCountEstimator)`):** mais preciso para o limite do modelo; exige um estimador de tokens da OpenAI. 1000 caracteres ficam muito abaixo do limite de entrada do `text-embedding-3-small`, então caracteres bastam.

## Consequências

- Mesmo comportamento conhecido do LangChain; tamanho e sobreposição ajustáveis por propriedade.
- O objetivo de aprendizado "Chunking" passa a ser explorado ajustando os parâmetros e observando os resultados com os documentos do RF-011, e não implementando o algoritmo.
- Os testes de `TextChunker` verificam o contrato (índices sem lacuna, nenhum chunk vazio, tamanho máximo), não o algoritmo interno.
