# RF-009 — Controle de acesso por cliente

## Objetivo

Garantir que uma requisição só acesse dados do cliente para o qual está autorizada, completando a regra de segurança "similaridade + filtro de cliente + controle de acesso".

## Descrição

A regra fundamental do README (seção 12) diz que a busca deve considerar similaridade semântica, filtro de tenant e controle de acesso, para que um documento do Cliente B nunca volte "durante uma consulta autorizada somente para o Cliente A". O filtro por `client_id` está em RF-007. Hoje, porém, qualquer chamador pode colocar qualquer `{clientId}` no caminho, e o README não define o mecanismo de autorização.

## Atores

- Usuário do cliente (chamador da API).
- Aplicação (valida a autorização).

## Pré-condições

- Mecanismo de autenticação definido: chave de API por cliente (DP-01, ADR-008).
- Credenciais do chamador associadas a um cliente.

## Fluxo principal

1. O chamador envia a requisição com sua credencial para `/clients/{clientId}/...`.
2. O sistema identifica a qual cliente a credencial pertence.
3. O sistema compara esse cliente com o `{clientId}` do caminho.
4. Se forem iguais, a requisição segue para o controller.

## Fluxos alternativos

- FA-01 — Sem credencial ou credencial inválida: `401 Unauthorized`.
- FA-02 — Credencial válida de outro cliente: `403 Forbidden`; nenhuma consulta ao banco de documentos é feita.

## Regras de negócio

- RN-01 — Toda rota `/clients/{clientId}/**` exige autorização para aquele `clientId`.
- RN-02 — A autorização acontece antes de qualquer acesso a documentos ou chunks.
- RN-03 — A mensagem de erro não revela se o outro cliente existe ou tem documentos.

## Dados de entrada

| Campo | Tipo | Obrigatório |
|---|---|---|
| credencial do chamador | cabeçalho `X-API-Key` | Sim |
| clientId | número (caminho) | Sim |

## Dados de saída

- Nenhum dado próprio; libera ou bloqueia a requisição.

## Critérios de aceite

- CA-01 — Credencial do Cliente A em `/clients/{A}/search` é aceita.
- CA-02 — Credencial do Cliente A em `/clients/{B}/search` e `/clients/{B}/documents` recebe 403.
- CA-03 — Requisição sem credencial recebe 401.
- CA-04 — Testes automatizados cobrem CA-01 a CA-03 (RF-012).

## Dependências

- RF-003
- RF-010

## Situação atual do projeto

Atualizado em 2026-10-01: a base foi feita junto com o RF-003.

- `spring-boot-starter-security` no `pom.xml`; `SecurityConfig` stateless com as regras de rota da arquitetura 07: Swagger liberado, `POST /clients` só ADMIN, `GET /clients/{clientId}` ADMIN ou o próprio cliente, `/clients/{clientId}/**` só o próprio cliente, demais rotas negadas.
- `ApiKeyAuthenticationFilter` (cabeçalho `X-API-Key`; chave de admin comparada em tempo constante; chave de cliente localizada pelo hash SHA-256), `ApiKeyHasher`, `ClientPrincipal` e `ClientAccessAuthorizationManager`; 401 e 403 em `ProblemDetail` via `ProblemDetailSecurityHandler`.
- Testes: CT-025, CT-027 e CT-083 passando, mais as linhas de 401 do RF-003 em `SecurityConfigTest`.
- Pendente: `security-matrix.csv` com CT-080 a CT-082, CT-084 e CT-085 (T-302, T-303).

Atualizado em 2026-10-02 (T-302 e T-303 concluídas):

- `ddt/security-matrix.csv` com 40 linhas cobrindo todos os endpoints atuais (`POST /clients`, `GET /clients/{clientId}`, `POST /clients/{clientId}/documents`, `/search`, `/ask`, Swagger e rota desconhecida) com dono, outro cliente, admin, chave inválida e sem chave; consumida por `SecurityConfigTest` (CT-080). `PERMITIDO` exige 2xx ou 3xx com os services mockados.
- `SecurityConfigTest` com CT-081 (403 idêntico para cliente existente e inexistente), CT-082 (403 sem chamar nenhum service), CT-084 (`clientId` não numérico → 403), CT-085 (Swagger UI e `/v3/api-docs` sem chave, esquema `apiKey` no cabeçalho `X-API-Key`; o teste carrega o springdoc na fatia web) e CT-115 (acesso cruzado A↔B em search, documents e ask → 403).
- Achado #3 da revisão do RF-003 corrigido: `.requestMatchers("/clients/{clientId}").denyAll()` entre a regra do `GET` e a `/**`, então PUT, DELETE e PATCH em `/clients/{id}` recebem 403 até com a chave do próprio cliente (antes 405 do MVC). Aguarda reconferência da revisão.
- `ClientAccessAuthorizationManagerTest` cobre o caminho em que o principal não é `ClientPrincipal` (admin em `/clients/{id}/**`), além de id de outro cliente, id inválido e ausência de autenticação.
- Build: `mvnw verify` 229 de 229, cobertura de 98,3% das linhas ([evidência](../QA/evidencias/execucoes/2026-10-02-rf-009.md)).

## Itens a implementar

- Dependência `spring-boot-starter-security`; `SecurityConfig`, `ApiKeyAuthenticationFilter` e `ClientAccessAuthorizationManager` (docs/arquitetura/07).
- Filtro ou regra de autorização que compare o cliente da credencial com o `{clientId}`.

## Decisões pendentes

- DP-01 — Mecanismo. **Decidido:** chave de API por cliente no cabeçalho `X-API-Key`, guardada como hash SHA-256, mais uma chave de administrador por variável de ambiente (ADR-008). JWT fica como evolução.
- DP-02 — Quem pode cadastrar clientes e enviar documentos. **Decidido:** o administrador só cadastra e consulta clientes; envio de documentos, busca e perguntas exigem a chave do próprio cliente (ADR-008).

## Cenários de teste (QA)

Antes de implementar, leia os cenários abaixo em [05 Cenarios de teste.md](../QA/05%20Cenarios%20de%20teste.md) e implemente os automáticos com o ID no `@DisplayName`; os marcados como manual são executados por uma pessoa pelo roteiro em [07 Testes manuais.md](../QA/07%20Testes%20manuais.md). O requisito só está pronto com todos passando ([definição de pronto](../QA/01%20Estrategia%20de%20testes.md)).

- CT-025 (P1) — Cliente consulta a si mesmo, mas não a outro
- CT-026 (P1) — Chave gerada é aleatória e só o hash é guardado
- CT-027 (P1) — Cliente não pode cadastrar clientes
- CT-080 (P1) — Matriz de acesso por chave e rota (DDT)
- CT-081 (P1) — 403 não revela se o outro cliente existe
- CT-082 (P1) — Acesso negado não chega ao service
- CT-083 (P1) — Chave guardada só como hash
- CT-084 (P2) — clientId não numérico
- CT-085 (P2) — Documentação pública, operações protegidas
- CT-119 (P2, manual) — Administrador cadastra Cliente A e Cliente B pelo Swagger UI
- CT-125 (P1, manual) — Ninguém envia documento para a conta de outro cliente
- CT-130 (P1, manual) — Autenticação e permissões por chave
- CT-131 (P2, manual) — Swagger UI público, operações só com Authorize
- CT-135 (P1, manual) — Acesso cruzado entre Cliente A e Cliente B devolve 403 em todas as rotas

## Prioridade

Alta — Faz parte da primeira entrega (pergunta e resposta por cliente): o usuário decidiu em 02/10/2026 (RF-008 DP-06) que todas as funcionalidades RF-001 a RF-012 entram nela.

## Status

- Status: Concluído — entregue na v0.1.0 (Produção, 2026-10-06).
- Prontidão: pronto para desenvolvimento (ADR-008 aprovada).
- 2026-10-01: T-203 e T-301 concluídas com o RF-003; T-302 e T-303 em andamento ([evidência](../QA/evidencias/execucoes/2026-10-01-rf-003-rf-004.md)). Achado #1 (Média) da [revisão do RF-003](../revisao/RF-003%20Revisao%20de%20codigo.md): se a consulta da chave no banco falha, a resposta sai 401 em vez de 500 — corrigir antes de fechar.
- 2026-10-02: achado #1 do RF-003 corrigido e reconferido (revisão do RF-003).
- 2026-10-02: T-302 e T-303 concluídas (5 de 5 tarefas); CT-025 a CT-027 e CT-080 a CT-085 passando, mais o CT-115 do RF-012; achado #3 do RF-003 corrigido (a reconferir). Card HU-009 em Em teste, fase QA ([evidência](../QA/evidencias/execucoes/2026-10-02-rf-009.md)).
