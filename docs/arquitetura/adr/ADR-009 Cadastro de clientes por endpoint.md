# ADR-009 — Cadastro de clientes por endpoint administrativo

- **Status:** Aprovada (decisão do responsável pelo projeto em 2026-10-01)
- **Data:** 2026-10-01
- **Requisitos:** RF-003 (resolve DP-01, DP-02, DP-03)

## Contexto

Ingestão e busca recebem `{clientId}`, mas o README não diz como um cliente é criado. Com a ADR-008, cada cliente também precisa de uma chave de API gerada de forma segura.

## Decisão

- `POST /clients` (somente ADMIN) cria o cliente e devolve `id`, `name` e a chave de API (uma única vez).
- `GET /clients/{clientId}` para ADMIN ou o próprio cliente.
- Nome **não** precisa ser único (o README não exige; o `id` identifica).
- Listar, editar e excluir clientes: fora do escopo.

## Alternativas consideradas

- **Carga fixa por migration (`V2__seed_clients.sql`) com Cliente A e Cliente B:** mais simples, mas a chave teria de ficar no repositório (o hash na migration e a chave em texto em algum lugar), e não dá para criar clientes nos testes de forma natural.
- **Cadastro implícito na primeira ingestão:** mistura responsabilidades e torna impossível controlar o acesso.

## Consequências

- Testes e scripts criam clientes pela API e recebem a chave na resposta.
- Perdeu a chave, cria-se outro cliente.
