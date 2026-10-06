# 07 — Segurança e isolamento entre clientes

## 1. As três camadas do README

O README (seção 12) exige "similaridade semântica + filtro de tenant/client + controle de acesso". Cada camada tem um responsável e um teste:

| Camada | Onde | O que garante | Teste (RF-012) |
|---|---|---|---|
| Controle de acesso | `SecurityConfig` + `ClientAccessAuthorizationManager` | Só a chave do Cliente A entra em `/clients/{A}/**` | MockMvc: chave de A em rota de B → 403 |
| Filtro de tenant | `ChunkRepository`: filtro `metadataKey("client_id").isEqualTo(clientId)` do `PgVectorEmbeddingStore`, que vira `WHERE client_id = ...` no SQL | Mesmo que a camada acima falhe, a consulta só lê linhas do `clientId` informado | Testcontainers: chunk idêntico no Cliente B não volta na busca de A |
| Similaridade | `ORDER BY embedding <=> :q` | Ordena por relevância **dentro** do conjunto já filtrado | Busca do contrato devolve chunks do contrato primeiro |

Nenhuma camada substitui outra. Em especial, a similaridade nunca é usada para separar clientes.

## 2. Autenticação por chave de API (ADR-008)

### Chaves

| Tipo | Origem | Guardada como | Papel |
|---|---|---|---|
| Administrador | Variável de ambiente `ADMIN_API_KEY` → `app.security.admin-api-key` | Só em memória, nunca no banco | `ROLE_ADMIN` |
| Cliente | Gerada em `POST /clients` (32 bytes de `SecureRandom`, Base64 URL sem padding) | `client.api_key_hash` = SHA-256 hex | `ROLE_CLIENT` + `ClientPrincipal(clientId)` |

SHA-256 simples (sem bcrypt) é suficiente porque a chave é aleatória com 256 bits de entropia — não há dicionário para atacar — e permite localizar o cliente pelo hash com um índice único.

### Filtro

`ApiKeyAuthenticationFilter` (`OncePerRequestFilter`, registrado antes do `AnonymousAuthenticationFilter`):

1. Sem cabeçalho `X-API-Key` → segue anônimo (a regra de rota devolverá 401).
2. Compara com a chave de admin usando `MessageDigest.isEqual` (tempo constante) → autentica como ADMIN.
3. Senão, `clientRepository.findByApiKeyHash(sha256(chave))` → autentica `ClientPrincipal(clientId)` como CLIENT.
4. Não encontrou → 401.

A chave nunca é registrada em log.

### Regras de rota

```text
/swagger-ui.html, /swagger-ui/**,
/v3/api-docs/**                    → permitAll (só documentação; ADR-013)
POST /clients                      → hasRole("ADMIN")
GET  /clients/{clientId}           → hasRole("ADMIN") ou cliente dono
/clients/{clientId}/**             → somente o cliente dono (ClientAccessAuthorizationManager)
qualquer outra rota                → denyAll
```

`ClientAccessAuthorizationManager` lê a variável `clientId` do `RequestAuthorizationContext` e concede acesso apenas se o principal for `ClientPrincipal` com o mesmo id. Comparação numérica: `clientId` que não é número → nega.

Configuração do Spring Security 7: `csrf` desabilitado (API stateless sem cookie), `sessionManagement` `STATELESS`, `httpBasic` e `formLogin` desabilitados, `AuthenticationEntryPoint` e `AccessDeniedHandler` escrevendo `ProblemDetail` (401/403).

## 3. Regras de código para não furar o isolamento

1. Todo método de repository que lê `document` ou `document_chunk` recebe `clientId` e o usa no `WHERE`. Revisão de código recusa método sem isso.
2. `clientId` vem do `@PathVariable`, já validado pela segurança. Nunca do corpo da requisição, nunca de cabeçalho além da chave.
3. `AnswerService` não acessa repository: usa apenas o resultado do `SearchService`.
4. Só o `ChunkRepository` injeta o `EmbeddingStore`. O `RagAssistant` (AiServices) não recebe `ContentRetriever`; ele nunca busca chunks sozinho.
5. Mensagens de erro não dizem se outro cliente existe nem quantos documentos tem.
6. Logs registram `clientId`, nunca conteúdo de chunk nem pergunta completa em nível acima de `DEBUG`. Nenhum log, de nenhum nível, registra a chave de API (`X-API-Key`), `ADMIN_API_KEY`, `OPENAI_API_KEY`, a senha do banco nem o hash das chaves; o log por requisição proposto na ADR-015 (em revisão) só escreve campos fixos e não lê cabeçalhos nem corpo ([06](06%20Integracoes%20e%20configuracao.md), seção 9, lista as configurações de log proibidas em arquivo versionado).

## 4. Riscos conhecidos

| Risco | Situação | Mitigação |
|---|---|---|
| Chave de cliente vazada | Quem tiver a chave lê os documentos daquele cliente | Fora do escopo: rotação e revogação de chaves |
| Prompt injection em documento | Texto malicioso dentro de um PDF pode tentar mudar o comportamento do modelo | O modelo só recebe chunks do próprio cliente, então não há como alcançar dados de outro; o dano fica restrito à resposta do próprio cliente |
| HNSW devolvendo menos de K | Não vaza dados; reduz qualidade | `hnsw.iterative_scan` (04, seção 5) |
| Dados enviados à OpenAI | Chunks e perguntas saem da máquina para gerar embeddings e respostas | Aceitável porque os documentos são fictícios (RF-011); documentos reais exigiriam rever a ADR-006 |
| Sem HTTPS | Chave trafega em texto aberto | Aceitável só em `localhost`; qualquer exposição fora da máquina exige TLS |
| Documentação pública (Swagger) | Revela a forma da API, não os dados; as operações continuam exigindo chave | `SWAGGER_ENABLED=false` se a aplicação for exposta; chave do Swagger fica só no navegador de quem usa |
