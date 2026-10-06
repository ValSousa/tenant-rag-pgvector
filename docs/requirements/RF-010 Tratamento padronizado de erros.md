# RF-010 — Tratamento padronizado de erros

## Objetivo

Devolver erros da API com código HTTP correto e corpo em formato único, sem expor detalhes internos nem dados de outros clientes.

## Descrição

O README (seção 3) prevê `ResourceNotFoundException` e `GlobalExceptionHandler` no pacote `exception`, mas não diz quando cada erro ocorre nem o formato da resposta. Os demais requisitos dependem deste para os fluxos alternativos.

## Atores

- Consumidor da API.

## Pré-condições

- Nenhuma.

## Fluxo principal

1. Uma exceção é lançada em controller ou service.
2. O `GlobalExceptionHandler` captura a exceção.
3. O handler escolhe o código HTTP pelo tipo da exceção.
4. O handler devolve o corpo de erro padronizado.

## Fluxos alternativos

- FA-01 — Exceção não mapeada: `500 Internal Server Error` com mensagem genérica; detalhes só no log.

## Regras de negócio

Mapeamento decidido (DP-01, ADR-010):

- RN-01 — `ResourceNotFoundException` (cliente ou documento inexistente) → `404 Not Found`.
- RN-02 — Validação de entrada (campo obrigatório, arquivo inválido) → `400 Bad Request`, listando os campos.
- RN-03 — Documento sem texto extraível → `422 Unprocessable Entity`.
- RN-04 — Sem credencial → `401`; acesso a outro cliente → `403` (RF-009).
- RN-05 — Falha do provedor de IA (embeddings ou modelo de linguagem) → `503 Service Unavailable`.
- RN-06 — Arquivo acima do limite (5 MB, RF-006 DP-02; revisto pelo usuário em 2026-10-05, antes 10 MB) → `413 Payload Too Large`, com title "Arquivo muito grande" e detail "O arquivo excede o tamanho máximo permitido de 5 MB.".
- RN-07 — Nenhuma resposta de erro traz stack trace, SQL ou dados de outro cliente.

## Dados de entrada

| Campo | Tipo | Obrigatório |
|---|---|---|
| exceção lançada | Exception | Sim |

## Dados de saída

Decidido (DP-02, ADR-010): `ProblemDetail` (RFC 9457) com `type`, `title`, `status`, `detail`, `instance` e, para validação, `errors`.

## Critérios de aceite

- CA-01 — `GET /clients/999999` chamado pelo administrador devolve 404 no formato padronizado. (Com chave de cliente, qualquer `clientId` alheio devolve 403 antes de consultar o banco — ADR-008.)
- CA-02 — Pergunta vazia devolve 400 com o campo inválido identificado.
- CA-03 — Exceção inesperada devolve 500 sem stack trace no corpo.
- CA-04 — Testes de controller (MockMvc) cobrem 400, 404 e 500.

## Dependências

- Nenhuma.

## Situação atual do projeto

Atualizado em 2026-10-02 (antes: 2026-10-01): a base foi feita junto com o RF-003 e o RF-004; a T-201 foi concluída em 2026-10-02.

- `ResourceNotFoundException`, `AiProviderException` e `GlobalExceptionHandler` (`@RestControllerAdvice` sobre `ResponseEntityExceptionHandler`): `ProblemDetail` com títulos em português, lista `errors` nos erros de validação, 404, 401, 403, 503 e 500 sem detalhes técnicos.
- `spring-boot-starter-validation` no `pom.xml` e `spring.mvc.problemdetails.enabled=true`.
- Já coberto indiretamente: 400 e 404 (CT-021, CT-024), 401 e 403 (`SecurityConfigTest`).
- 2026-10-02, com o RF-005 e o RF-006: `InvalidDocumentException` → 422 "Documento não processável"; 413 "Arquivo muito grande" para upload acima de 10 MB (limite reduzido para 5 MB pelo usuário em 2026-10-05, RF-006 DP-02; mudança no código pendente); títulos em português também quando o Spring monta o corpo sozinho (parte ou parâmetro ausente, `ResponseStatusException`) e mensagens em português para parte ou parâmetro ausente, valor inválido e arquivo grande demais. Coberto indiretamente por CT-052, CT-053, CT-054 e CT-056.
- 2026-10-02, fechamento da T-201: `type` (`about:blank`, padrão da RFC 9457) em todo `ProblemDetail`, porque o Spring 7 o omitia do JSON; `detail` em português também para JSON malformado ou ausente, 405, 406 (título novo "Formato de resposta não suportado"), 415 e endereço inexistente (pendência da revisão do RF-003). `GlobalExceptionHandlerTest` (controller só de teste que lança a exceção pedida) com CT-090 a CT-093 passando; CT-024, CT-053 e CT-062 continuam passando. `mvnw verify`: 245 testes, todos aprovados, 97,9% das linhas ([evidência](../QA/evidencias/execucoes/2026-10-02-rf-010.md)).
- Pendente: só os testes manuais CT-132 e CT-133 (e os manuais CT-123 e CT-137, dos cards do RF-005 e do RF-006), executados por uma pessoa.

## Itens a implementar

- `ResourceNotFoundException` e demais exceções necessárias em `br.com.rag_pgvector.exception`.
- `GlobalExceptionHandler` com `@RestControllerAdvice`.
- Dependência `spring-boot-starter-validation` para validar os DTOs.

## Decisões pendentes

- DP-01 — Mapeamento de códigos HTTP. **Decidido:** conforme as regras acima (ADR-010).
- DP-02 — Formato do corpo de erro. **Decidido:** `ProblemDetail` (ADR-010).
- DP-03 — Idioma das mensagens de erro. **Decidido:** português (ADR-010).

## Cenários de teste (QA)

Antes de implementar, leia os cenários abaixo em [05 Cenarios de teste.md](../QA/05%20Cenarios%20de%20teste.md) e implemente os automáticos com o ID no `@DisplayName`; os marcados como manual são executados por uma pessoa pelo roteiro em [07 Testes manuais.md](../QA/07%20Testes%20manuais.md). O requisito só está pronto com todos passando ([definição de pronto](../QA/01%20Estrategia%20de%20testes.md)).

- CT-024 (P2) — Consulta de cliente inexistente pelo administrador
- CT-053 (P2) — Arquivo acima de 5 MB
- CT-062 (P2) — Validação da pergunta (DDT)
- CT-090 (P2) — Mapeamento de exceções para HTTP (DDT)
- CT-091 (P2) — Formato ProblemDetail
- CT-092 (P2) — Erro interno sem detalhes técnicos
- CT-093 (P2) — Erros de validação listam os campos
- CT-123 (P2, manual) — PDF corrompido é recusado com 422 e nada é gravado
- CT-132 (P2, manual) — Erros no formato ProblemDetail, em português
- CT-133 (P1, manual) — Falha da OpenAI vira 503 sem detalhes técnicos e sem gravação parcial
- CT-137 (P2, manual) — Upload acima de 5 MB é recusado com 413

## Prioridade

Alta — Faz parte da primeira entrega (pergunta e resposta por cliente): o usuário decidiu em 02/10/2026 (RF-008 DP-06) que todas as funcionalidades RF-001 a RF-012 entram nela.

## Status

- Status: Em teste (fase QA)
- Prontidão: pronto para desenvolvimento (ADR-010 aprovada).
- 2026-10-01: T-201 em andamento ([evidência](../QA/evidencias/execucoes/2026-10-01-rf-003-rf-004.md)). Achado #2 da [revisão do RF-003](../revisao/RF-003%20Revisao%20de%20codigo.md): erros que o Spring MVC monta sozinho (ex.: 405) ainda saem com título em inglês.
- 2026-10-02: o `handleExceptionInternal` passou a traduzir o título também quando o Spring passa o corpo nulo (parte ou parâmetro ausente, `ResponseStatusException`), achado durante o RF-006 ([evidência](../QA/evidencias/execucoes/2026-10-02-rf-005-rf-006.md)); a verificação do achado #2 fica com o CT-090 a CT-093.
- 2026-10-02: T-201 concluída; CT-024, CT-053, CT-062 e CT-090 a CT-093 passando no `mvnw verify` (245 de 245, [evidência](../QA/evidencias/execucoes/2026-10-02-rf-010.md)); card HU-010 em Em teste, fase QA. Faltam a verificação do QA, a revisão e os testes manuais.
- 2026-10-05: RN-06 atualizada: o limite de upload passa de 10 MB para 5 MB por decisão do usuário (RF-006 DP-02), com detail "O arquivo excede o tamanho máximo permitido de 5 MB.". CT-053 e CT-137 passam a usar 5 MB; falta ajustar o código.
