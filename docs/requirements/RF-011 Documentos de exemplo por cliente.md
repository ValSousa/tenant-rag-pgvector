# RF-011 — Documentos de exemplo por cliente

## Objetivo

Ter documentos fictícios de contrato, sinistro e vistoria para dois clientes, permitindo testar a ingestão, a busca, o isolamento e as perguntas de exemplo do README.

## Descrição

O README (seção 3) prevê a pasta `documents/` com `cliente-a/` e `cliente-b/`, cada uma com `contrato.pdf`, `sinistro.pdf` e `vistoria.pdf`. As perguntas da seção 10 exigem que esses documentos tragam franquia, danos identificados, data do sinistro, custo do reparo, valor do veículo e o percentual de perda total da apólice. Os seis PDFs foram escritos pelo usuário (DP-01) e o gabarito está em `documents/gabarito.md`.

## Atores

- Desenvolvedor (cria e carrega os documentos).

## Pré-condições

- Conteúdo dos documentos definido (DP-01, decidido: redigido pelo usuário).

## Fluxo principal

1. O desenvolvedor cria os seis PDFs com conteúdo fictício.
2. O desenvolvedor cadastra Cliente A e Cliente B (RF-003).
3. O desenvolvedor envia cada PDF para o cliente correspondente (RF-006), com o tipo `CONTRACT`, `CLAIM` ou `INSPECTION`.
4. As perguntas da seção 10 passam a ter resposta nos documentos.

## Fluxos alternativos

- FA-01 — PDF gerado como imagem: a ingestão rejeita (RF-005 FA-02); gerar PDF com texto.

## Regras de negócio

- RN-01 — Todos os dados são fictícios; nenhum dado pessoal real.
- RN-02 — Os valores do Cliente A e do Cliente B são diferentes (franquia, datas, valores), para que um vazamento entre clientes seja detectável.
- RN-03 — O contrato traz o percentual de perda total; a vistoria traz custo do reparo e valor do veículo.
- RN-04 — Em ao menos um cliente o sinistro é perda total e no outro não, para testar as duas conclusões da última pergunta.

## Dados de entrada

| Campo | Tipo | Obrigatório |
|---|---|---|
| contrato.pdf (por cliente) | PDF com texto | Sim |
| sinistro.pdf (por cliente) | PDF com texto | Sim |
| vistoria.pdf (por cliente) | PDF com texto | Sim |

## Dados de saída

- Seis PDFs em `documents/cliente-a/` e `documents/cliente-b/`.
- Tabela com as respostas esperadas das perguntas da seção 10 para cada cliente (gabarito dos testes).

## Critérios de aceite

- CA-01 — Os seis arquivos existem e têm texto extraível.
- CA-02 — Existe gabarito com a resposta esperada de cada pergunta da seção 10, por cliente.
- CA-03 — Os valores do Cliente A não aparecem nos documentos do Cliente B, e vice-versa.

## Dependências

- RF-003
- RF-006

## Situação atual do projeto

- `documents/cliente-a/` e `documents/cliente-b/` têm `contrato.pdf`, `sinistro.pdf` e `vistoria.pdf`, com texto extraível (verificado com `pdftotext` em 2026-10-02).
- `documents/gabarito.md` traz as respostas das cinco perguntas por cliente e a lista de valores exclusivos de cada um. Cliente A é perda total (reparo de 80%, limite de 75%); Cliente B não é (reparo de cerca de 53,7%, limite de 70%) — atende à RN-04.
- Nenhum valor exclusivo de um cliente aparece nos PDFs do outro — atende à RN-02 e à CA-03, verificado também pelo `SampleDocumentsIT` desde 2026-10-02.
- `SampleDocumentsIT` (T-603) implementado em 2026-10-02 com o `PdfTextExtractor` real (RF-005): CT-100 (texto dos 6 PDFs), CT-101 (valores exclusivos do gabarito presentes no próprio cliente e ausentes no outro) e CT-102 (gabarito completo) passando ([evidência](../QA/evidencias/execucoes/2026-10-02-rf-005-rf-006.md)).
- Falta: `http/requests.http` (T-602).

## Itens a implementar

- `documents/cliente-a/` e `documents/cliente-b/` com os três PDFs cada.
- Gabarito das respostas (por exemplo, `documents/gabarito.md`).
- Opcional: script ou coleção de requisições HTTP para carregar os documentos.

## Decisões pendentes

- DP-01 — Conteúdo dos documentos. **Decidido:** redigido pelo usuário (T-601, 2026-10-02).
- DP-02 — Os PDFs ficam no repositório? **Decidido:** sim; são fictícios e pequenos (cerca de 27 KB cada).

## Cenários de teste (QA)

Antes de implementar, leia os cenários abaixo em [05 Cenarios de teste.md](../QA/05%20Cenarios%20de%20teste.md) e implemente os automáticos com o ID no `@DisplayName`; os marcados como manual são executados por uma pessoa pelo roteiro em [07 Testes manuais.md](../QA/07%20Testes%20manuais.md). O requisito só está pronto com todos passando ([definição de pronto](../QA/01%20Estrategia%20de%20testes.md)).

- CT-100 (P2) — PDFs de exemplo com texto (DDT)
- CT-101 (P2) — Sem valores cruzados entre clientes
- CT-102 (P3) — Gabarito completo
- CT-134 (P2, manual) — Documentos de exemplo conferem com o gabarito e são ingeridos para os dois clientes

## Prioridade

Alta — Faz parte da primeira entrega (pergunta e resposta por cliente): o usuário decidiu em 02/10/2026 (RF-008 DP-06) que todas as funcionalidades RF-001 a RF-012 entram nela.

## Status

- Status: Em andamento
- Prontidão: pronto para desenvolvimento; T-601 e T-603 concluídas (T-603 em 2026-10-02), T-602 livre.
