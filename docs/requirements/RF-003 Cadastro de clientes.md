# RF-003 — Cadastro de clientes

## Objetivo

Permitir que clientes (tenants) existam no sistema, para que documentos e consultas possam ser associados a eles.

## Descrição

O README define a tabela `client` e a entidade `ClientEntity` ("Identificar o cliente" e "Relacionar documentos ao cliente"), e os endpoints de ingestão e busca recebem `{clientId}` no caminho. Porém o README não diz como um cliente é criado. Sem clientes cadastrados, nenhum documento pode ser ingerido.

## Atores

- Administrador do sistema, autenticado pela chave de administrador (ADR-008).

## Pré-condições

- RF-002 concluído (tabela `client`).

## Fluxo principal

Conforme a DP-01 (endpoint REST, ADR-009 aprovada):

1. O administrador envia `POST /clients` com o nome do cliente.
2. O sistema valida que o nome foi informado.
3. O sistema grava o cliente e devolve `201 Created` com `id` e `name`.
4. O administrador consulta `GET /clients/{clientId}` e recebe os dados do cliente.

## Fluxos alternativos

- FA-01 — Nome vazio ou ausente: `400 Bad Request` (RF-010).
- FA-02 — `GET` de cliente inexistente pelo administrador: `404 Not Found` (RF-010). Com chave de cliente, qualquer `clientId` diferente do próprio recebe `403`, exista ou não (RF-009 RN-03).

## Regras de negócio

- RN-01 — `name` é obrigatório e tem no máximo 255 caracteres.
- RN-02 — O `id` é gerado pelo banco.

## Dados de entrada

| Campo | Tipo | Obrigatório |
|---|---|---|
| name | texto (até 255) | Sim |

## Dados de saída

- `id` (número) e `name` (texto) do cliente.

## Critérios de aceite

- CA-01 — É possível criar os clientes "Cliente A" e "Cliente B" usados nos exemplos do README.
- CA-02 — Cliente criado pode ser consultado pelo `id`.
- CA-03 — Nome vazio é rejeitado com 400.

## Dependências

- RF-002
- RF-010

## Situação atual do projeto

- `ClientEntity` e `ClientRepository` (RF-002); `ClientService`, `ClientController`, DTOs, `ApiKeyHasher` e a autenticação por chave de API (`SecurityConfig`, `ApiKeyAuthenticationFilter`) implementados em 2026-10-01.

## Itens a implementar

- `ClientRepository` (não listado no README, necessário para validar a existência do cliente).
- `ClientController` com `POST /clients` (ADMIN) e `GET /clients/{clientId}` (ADMIN ou o próprio cliente); a resposta do `POST` traz a chave de API do cliente uma única vez.

## Decisões pendentes

- DP-01 — Forma de cadastro. **Decidido:** endpoints `POST /clients` e `GET /clients/{clientId}` (ADR-009).
- DP-02 — Nome de cliente deve ser único? **Decidido:** não; o `id` identifica o cliente (ADR-009).
- DP-03 — Listagem, alteração e exclusão de clientes. **Decidido:** fora do escopo (ADR-009).

## Cenários de teste (QA)

Antes de implementar, leia os cenários abaixo em [05 Cenarios de teste.md](../QA/05%20Cenarios%20de%20teste.md) e implemente os automáticos com o ID no `@DisplayName`; os marcados como manual são executados por uma pessoa pelo roteiro em [07 Testes manuais.md](../QA/07%20Testes%20manuais.md). O requisito só está pronto com todos passando ([definição de pronto](../QA/01%20Estrategia%20de%20testes.md)).

- CT-020 (P1) — Administrador cadastra cliente
- CT-021 (P2) — Validação do nome do cliente (DDT)
- CT-023 (P2) — Consulta de cliente existente
- CT-024 (P2) — Consulta de cliente inexistente pelo administrador
- CT-025 (P1) — Cliente consulta a si mesmo, mas não a outro
- CT-026 (P1) — Chave gerada é aleatória e só o hash é guardado
- CT-027 (P1) — Cliente não pode cadastrar clientes
- CT-119 (P2, manual) — Administrador cadastra Cliente A e Cliente B pelo Swagger UI
- CT-120 (P2, manual) — Consulta de cliente e validação do nome

## Prioridade

Alta — Faz parte da primeira entrega (pergunta e resposta por cliente): o usuário decidiu em 02/10/2026 (RF-008 DP-06) que todas as funcionalidades RF-001 a RF-012 entram nela.

## Status

- Status: Em andamento — card HU-003 Concluído em 2026-10-05 (testes automáticos e manuais OK, revisão aprovada); entra na release v0.1.0 (Planejada).
- Prontidão: pronto para desenvolvimento (ADR-009 aprovada).
- 2026-10-01: `POST /clients` e `GET /clients/{clientId}` implementados, com chave gerada por `SecureRandom` e só o hash SHA-256 gravado; Swagger UI configurado (T-205). CT-020, CT-021, CT-023 a CT-027 passando ([evidência](../QA/evidencias/execucoes/2026-10-01-rf-003-rf-004.md)). Para os cenários P1 de acesso foram feitas as partes necessárias do RF-009 (filtro de chave, regras de rota, 401/403 em `ProblemDetail`) e do RF-010 (`GlobalExceptionHandler`). Falta a T-303 (matriz `security-matrix.csv`) e o CT-063 do `ApiFlowIT` (T-505, depende do RF-007).
