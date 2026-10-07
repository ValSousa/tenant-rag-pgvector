# RF-005 — Extração de texto e divisão em chunks

## Objetivo

Extrair o texto de um documento PDF e dividi-lo em trechos (chunks) ordenados, prontos para gerar embeddings.

## Descrição

O fluxo de ingestão do README (seções 7 e 8) começa com "PDF → Extração do texto → Chunking". Os exemplos da seção 3 são arquivos PDF (`contrato.pdf`, `sinistro.pdf`, `vistoria.pdf`). O README não define a biblioteca de extração nem as regras de divisão.

## Atores

- `DocumentService` (chama a extração e a divisão durante a ingestão).

## Pré-condições

- Arquivo PDF recebido pela ingestão (RF-006).
- Regras de chunking definidas (DP-02, ADR-007).

## Fluxo principal

1. O `DocumentService` entrega o conteúdo do PDF ao extrator.
2. O extrator devolve o texto do documento.
3. O texto é normalizado (espaços e quebras de linha repetidos).
4. O texto é dividido em chunks conforme tamanho e sobreposição definidos.
5. Cada chunk recebe `chunk_index` sequencial a partir de 0, na ordem do documento.

## Fluxos alternativos

- FA-01 — PDF corrompido ou ilegível: erro "Não foi possível ler o arquivo PDF."; nada é gravado.
- FA-02 — PDF sem texto extraível (imagem escaneada): erro "O documento não contém texto extraível."; nada é gravado.
- FA-03 — Texto menor que um chunk: gera um único chunk com índice 0.

## Regras de negócio

- RN-01 — Nenhum chunk tem conteúdo vazio.
- RN-02 — `chunk_index` preserva a ordem original do texto.
- RN-03 — Um chunk pertence a um único documento.
- RN-04 — O tamanho do chunk não pode passar do limite de entrada do modelo de embeddings (RF-004).

## Dados de entrada

| Campo | Tipo | Obrigatório |
|---|---|---|
| conteúdo do arquivo | bytes (PDF) | Sim |
| tamanho do chunk | número (configuração) | Sim |
| sobreposição | número (configuração) | Sim |

## Dados de saída

- Lista ordenada de chunks: `chunkIndex` e `content`.

## Critérios de aceite

- CA-01 — Um PDF de exemplo (RF-011) gera ao menos um chunk, com índices 0, 1, 2… sem lacunas.
- CA-02 — Nenhum trecho do texto extraído se perde: toda palavra do texto aparece em pelo menos um chunk, e nenhum chunk passa do tamanho configurado.
- CA-03 — PDF sem texto é rejeitado com a mensagem de FA-02.
- CA-04 — Tamanho e sobreposição vêm de `application.properties`.

## Dependências

- RF-004 (limite de entrada do modelo)

## Situação atual do projeto

- Implementado em 2026-10-02 (card HU-005, T-405 e T-406), no pacote `ingestion`:
  - `PdfTextExtractor` com o `ApachePdfBoxDocumentParser`: PDF ilegível → `InvalidDocumentException` "Não foi possível ler o arquivo PDF."; PDF sem texto → "O documento não contém texto extraível." (422 pelo `GlobalExceptionHandler`).
  - `TextChunker` com `DocumentSplitters.recursive(chunkSize, chunkOverlap)`: normaliza espaços e quebras repetidos, descarta trechos em branco e numera a partir de 0 (`Chunk(index, content)`).
  - `app.rag.chunk-size=1000` e `app.rag.chunk-overlap=200` em `application.properties` (`RagProperties`); sobreposição maior ou igual ao tamanho impede a aplicação de subir.
- Nos testes, `TestPdfFactory` gera os PDFs em memória (com texto, com tamanho, sem texto, corrompido, vazio).

## Itens a implementar

- Dependência `dev.langchain4j:langchain4j-document-parser-apache-pdfbox` (DP-01).
- `PdfTextExtractor` (`ApachePdfBoxDocumentParser`) e `TextChunker` (`DocumentSplitters.recursive`), chamados pelo `DocumentService`.
- Propriedades de tamanho e sobreposição.

## Decisões pendentes

- DP-01 — Biblioteca de extração. **Decidido:** `ApachePdfBoxDocumentParser` do LangChain4j (ADR-007).
- DP-02 — Regras de chunking. **Decidido:** `DocumentSplitters.recursive(1000, 200)` do LangChain4j, em caracteres, equivalente ao `RecursiveCharacterTextSplitter` do LangChain; valores ajustáveis em `application.properties` (ADR-007).
- DP-03 — Aceitar outros formatos além de PDF? **Decidido:** só PDF com texto; PDF escaneado é rejeitado (ADR-007).

## Cenários de teste (QA)

Antes de implementar, leia os cenários abaixo em [05 Cenarios de teste.md](../QA/05%20Cenarios%20de%20teste.md) e implemente os automáticos com o ID no `@DisplayName`; os marcados como manual são executados por uma pessoa pelo roteiro em [07 Testes manuais.md](../QA/07%20Testes%20manuais.md). O requisito só está pronto com todos passando ([definição de pronto](../QA/01%20Estrategia%20de%20testes.md)).

- CT-040 (P2) — Extração de PDF com texto
- CT-041 (P2) — PDF corrompido ou vazio
- CT-042 (P2) — PDF sem texto extraível
- CT-043 (P2) — Texto curto gera um único chunk
- CT-044 (P2) — Contrato do chunking para vários tamanhos (DDT)
- CT-045 (P3) — Tamanho e sobreposição vêm da configuração
- CT-122 (P3, manual) — Chunks do documento sem lacuna e com até 1000 caracteres
- CT-123 (P2, manual) — PDF corrompido é recusado com 422 e nada é gravado

## Prioridade

Alta — Faz parte da primeira entrega (pergunta e resposta por cliente): o usuário decidiu em 02/10/2026 (RF-008 DP-06) que todas as funcionalidades RF-001 a RF-012 entram nela.

## Status

- Status: Concluído — entregue na v0.1.0 (Produção, 2026-10-06).
- Prontidão: pronto para desenvolvimento (ADR-007 aprovada).
- 2026-10-02: `PdfTextExtractor`, `TextChunker`, `Chunk` e as propriedades de chunking implementados; CT-040 a CT-045 passando ([evidência](../QA/evidencias/execucoes/2026-10-02-rf-005-rf-006.md)). O `PdfTextExtractor` também lê os 6 PDFs de exemplo do RF-011 (CT-100). Aguarda a verificação do QA e a revisão de código.
