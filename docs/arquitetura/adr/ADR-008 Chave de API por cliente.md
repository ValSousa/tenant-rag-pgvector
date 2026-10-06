# ADR-008 — Autenticação por chave de API por cliente

- **Status:** Aprovada (decisão do responsável pelo projeto em 2026-10-01)
- **Data:** 2026-10-01
- **Requisitos:** RF-009 (resolve DP-01, DP-02), RF-003, RF-006, RF-007, RF-008

## Contexto

A regra de segurança do README exige controle de acesso além do filtro por cliente: uma consulta "autorizada somente para o Cliente A" não pode alcançar o Cliente B. Hoje qualquer chamador escolhe o `{clientId}` do caminho. O README não define o mecanismo. O projeto não tem usuários finais nem provedor de identidade.

## Decisão

- **Spring Security**, API stateless.
- Cada cliente tem **uma chave de API** gerada no cadastro (32 bytes aleatórios), enviada no cabeçalho `X-API-Key`. O banco guarda só o SHA-256 (`client.api_key_hash`, único).
- Uma **chave de administrador** vinda de variável de ambiente (`ADMIN_API_KEY`) permite apenas cadastrar e consultar clientes.
- `/clients/{clientId}/**` só aceita a chave do próprio cliente; qualquer outro `clientId` → 403 antes de chegar ao controller.
- Detalhes em [07 Seguranca e isolamento.md](../07%20Seguranca%20e%20isolamento.md).

## Alternativas consideradas

- **JWT com `clientId` em claim (OAuth2 Resource Server):** padrão de mercado, mas exige emissor de tokens (Keycloak ou similar) — infraestrutura demais para um projeto de estudo. A arquitetura permite migrar: só muda o filtro de autenticação; o `ClientAccessAuthorizationManager` continua comparando `clientId`.
- **Cabeçalho `X-Client-Id` sem segredo:** trivial de falsificar; não é controle de acesso.
- **Sem controle de acesso nesta etapa:** contraria a seção 12 do README.

## Consequências

- Isolamento garantido em duas camadas independentes (acesso + SQL).
- A chave só é mostrada uma vez; não há rotação nem revogação (fora do escopo).
- Testes de controller precisam enviar a chave (`spring-security-test` facilita).
- Sem HTTPS, a chave trafega aberta: aceitável só em `localhost`.
