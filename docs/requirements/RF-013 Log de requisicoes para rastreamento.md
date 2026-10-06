# RF-013 — Log de requisições para rastreamento

## Objetivo

Registrar em log cada requisição às APIs, com endpoint, método HTTP, resultado, duração e identificador de rastreamento (trace ID), para permitir acompanhar e investigar o uso em produção sem expor informações sensíveis.

## Descrição

Origem: pedido do usuário em 2026-10-05, arquivo `observabilidade.md` (na raiz do repositório pai). O README não trata de observabilidade nem de logs, e o usuário pediu que o README não seja alterado; este requisito tem como fonte só esse pedido.

O pedido diz: "APIs devem possuir logs adequados para rastreamento em produção, permitindo identificar endpoint, método HTTP, resultado da operação, duração e correlation/trace ID. Logs não devem expor informações sensíveis, como API keys, tokens, senhas ou dados pessoais desnecessários." Lista os campos `timestamp`, `level`, `service`, `environment`, `method`, `endpoint`, `status`, `clientId`, `traceId` e `duration`, e dá um exemplo em texto simples:

```text
2026-10-05T17:35:21.123 INFO service=client-api environment=prod method=GET endpoint=/clients/123 clientId=123 status=200 duration=42ms traceId=abc123
```

Hoje o projeto só registra falhas: o `GlobalExceptionHandler` loga exceções e o RF-010 (RN-07, FA-01) manda os detalhes de erro só para o log. Não há log por requisição, trace ID nem duração.

Ponto para o arquiteto: a visão geral da arquitetura (`docs/arquitetura/01 Visao geral.md`, "Fora do escopo") lista "observabilidade avançada". Este pedido é um log básico por requisição (uma linha por chamada), não métricas, tracing distribuído nem painel; cabe ao arquiteto dizer se ele entra no escopo como está ou se a visão geral precisa ser ajustada.

## Atores

- Consumidor da API (cliente ou administrador): faz as requisições que geram o log.
- Pessoa que opera ou investiga a aplicação (desenvolvedor, suporte): lê o log.

## Pré-condições

- A aplicação está em execução.
- Autenticação por chave de API em funcionamento (RF-009), para que o cliente da requisição seja conhecido.

## Fluxo principal

1. O consumidor chama um endpoint da API.
2. A aplicação gera um trace ID novo para a requisição (UUID sem hífens, 32 caracteres hexadecimais) e o coloca no contexto de log (MDC), para que apareça em todas as linhas de log da requisição (DP-03).
3. A aplicação processa a requisição normalmente.
4. Ao terminar, a aplicação registra uma linha de log em texto chave=valor com `timestamp`, `level`, `service`, `environment`, `method`, `endpoint`, `status`, `clientId`, `traceId` e `duration`.
5. A resposta é devolvida ao consumidor sem mudança de conteúdo, com o trace ID no cabeçalho de resposta `X-Trace-Id` (DP-03).

## Fluxos alternativos

- FA-01 — Requisição recusada pela segurança (sem chave, chave inválida ou acesso a outro cliente): a linha de log é registrada mesmo assim, com o `status` devolvido (401 ou 403), nível `WARN` e o `clientId` do caminho, se houver (DP-05, DP-07).
- FA-02 — Requisição que termina em erro (4xx ou 5xx tratado pelo `GlobalExceptionHandler`): a linha de log é registrada com o `status` devolvido (nível `WARN` para 4xx, `ERROR` para 5xx); o log de erro que já existe (RF-010 FA-01) continua e traz o mesmo trace ID da requisição.
- FA-03 — Requisição em rota sem `/clients/{clientId}` no caminho (ex.: `POST /clients`): o campo `clientId` é registrado como `-` (DP-05).
- FA-04 — Requisição às rotas de apoio (Swagger UI e `/v3/api-docs`): não gera linha de log de requisição (DP-07).
- FA-05 — Requisição que chega com um trace ID num cabeçalho: o valor de entrada não é aceito; a aplicação gera o seu próprio trace ID (DP-03).

## Regras de negócio

- RN-01 — Toda requisição às APIs gera uma linha de log de requisição, inclusive as recusadas e as que terminam em erro ("resultado da operação" no pedido). Ficam fora as rotas de apoio: Swagger UI e `/v3/api-docs` (DP-07).
- RN-02 — A linha traz os campos do pedido: `timestamp`, `level`, `service`, `environment`, `method`, `endpoint`, `status`, `clientId`, `traceId` e `duration`, em texto chave=valor, como no exemplo do pedido (DP-02). `service` é o `spring.application.name` (`tenant-rag-pgvector`); `environment` é a propriedade `app.logging.environment`, com valor `${APP_ENVIRONMENT:local}` (DP-04).
- RN-03 — `duration` é o tempo da requisição em milissegundos (no exemplo, `duration=42ms`).
- RN-04 — Cada requisição tem um trace ID gerado pela aplicação (UUID sem hífens, 32 caracteres hexadecimais), novo a cada requisição; o mesmo valor aparece em todas as linhas de log daquela requisição (MDC) e é devolvido no cabeçalho de resposta `X-Trace-Id`. Trace ID vindo de fora não é aceito por enquanto (DP-03).
- RN-05 — Nenhum log, de nenhum nível, registra o valor de chaves de API ou segredos: o cabeçalho `X-API-Key` (de cliente ou de administrador), `ADMIN_API_KEY`, `OPENAI_API_KEY`, a senha do banco (`DB_PASSWORD`) nem o hash das chaves. Isso segue a regra do projeto de que chaves de API nunca ficam em claro.
- RN-06 — O log não traz dados pessoais desnecessários. Ficam fora, além dos segredos da RN-05: corpo da requisição e da resposta, pergunta, texto e nome do arquivo dos documentos, nome do cliente, query string, todos os cabeçalhos, IP e User-Agent (DP-06).
- RN-07 — O log não altera a resposta da API nem o resultado da operação.
- RN-08 — O `level` da linha depende do `status`: `INFO` para status menor que 400, `WARN` para 4xx e `ERROR` para 5xx (DP-07).
- RN-09 — `clientId` é o número do caminho `/clients/{clientId}`, em qualquer resultado (inclusive 401/403 e chamadas do administrador); `-` quando a rota não tem cliente. O log não registra quem chamou (DP-05).

## Dados de entrada

| Campo | Tipo | Obrigatório |
|---|---|---|
| método HTTP da requisição | texto (`GET`, `POST`…) | Sim |
| caminho da requisição (endpoint) | texto | Sim |
| status HTTP da resposta | número | Sim |
| instante de início e de fim da requisição | data e hora | Sim |
| `clientId` (número do caminho `/clients/{clientId}`) | número | Não (`-` quando a rota não tem cliente; DP-05) |
| `service` (`spring.application.name`) e `environment` (`app.logging.environment`, `${APP_ENVIRONMENT:local}`) | texto | Sim (DP-04) |

## Dados de saída

- Uma linha de log por requisição com os dez campos da RN-02, em texto chave=valor (DP-02).
- O trace ID no cabeçalho de resposta `X-Trace-Id` (DP-03).

## Critérios de aceite

- CA-01 — Cada requisição a um endpoint da API gera exatamente uma linha de log de requisição em texto chave=valor com os dez campos da RN-02; sem `APP_ENVIRONMENT` definida, a linha traz `service=tenant-rag-pgvector` e `environment=local`.
- CA-02 — `GET /clients/{id}` com a chave do próprio cliente gera uma linha `INFO` com `method=GET`, `endpoint=/clients/{id}`, `status=200`, `clientId={id}`, `duration` em milissegundos e um `traceId` de 32 caracteres hexadecimais igual ao do cabeçalho de resposta `X-Trace-Id`.
- CA-03 — Uma requisição sem `X-API-Key` a `/clients/{id}` gera uma linha `WARN` com `status=401` e `clientId={id}`; a chave de um cliente na rota de outro gera uma linha `WARN` com `status=403` e o `clientId` do caminho (não o de quem chamou).
- CA-04 — Uma requisição que termina em 500 gera uma linha `ERROR` com `status=500`, e o log de erro do `GlobalExceptionHandler` daquela requisição traz o mesmo `traceId`.
- CA-05 — Duas requisições seguidas têm `traceId` diferentes; um trace ID enviado pelo consumidor num cabeçalho de entrada não é usado.
- CA-06 — Com chaves conhecidas nos testes (de cliente, de administrador, da OpenAI e senha do banco), nenhuma linha de log capturada durante as requisições contém esses valores nem o hash da chave do cliente.
- CA-07 — As linhas de log de `POST /clients/{id}/ask`, `POST /clients/{id}/search` e `POST /clients/{id}/documents` não contêm o corpo da requisição ou da resposta, a pergunta, o texto nem o nome do arquivo, o nome do cliente, a query string, cabeçalhos, IP nem User-Agent (DP-06).
- CA-08 — A resposta da API (status, cabeçalhos já existentes e corpo) é a mesma com e sem o log; a única diferença é o cabeçalho novo `X-Trace-Id`.
- CA-09 — Os testes automáticos verificam os campos do log com `mvnw test`, sem chamar a OpenAI.
- CA-10 — Requisições ao Swagger UI e a `/v3/api-docs` não geram linha de log de requisição; uma requisição a rota sem cliente (ex.: `POST /clients`) gera linha com `clientId=-`.

## Dependências

- RF-009 — identifica o cliente autenticado e produz os 401/403 que também são registrados.
- RF-010 — o log de erro existente deve ser relacionado à requisição pelo trace ID.

## Situação atual do projeto

Atualizado em 2026-10-06 (desenvolvimento):

- Implementado conforme a ADR-015: `logging/RequestLoggingFilter`, antes da cadeia do Spring Security, gera o trace ID (MDC `traceId` e cabeçalho `X-Trace-Id`) e registra uma linha por requisição com os dez campos; propriedades `logging.pattern.correlation` e `app.logging.environment=${APP_ENVIRONMENT:local}`; `APP_ENVIRONMENT` opcional no `.env.example`.
- O `X-Trace-Id` sai em toda resposta registrada, inclusive nos 401/403 da segurança, nos erros do `GlobalExceptionHandler` e no 413 (conferido também com o Tomcat real).
- Cenários automáticos CT-138 a CT-148 passando (`mvnw verify`: 316 testes, evidência `docs/QA/evidencias/execucoes/2026-10-06-rf-013.md`). Faltam os manuais CT-149 e CT-150.

## Itens a implementar

Decisões tomadas em 2026-10-06 (recomendações da [ADR-015](../arquitetura/adr/ADR-015%20Log%20por%20requisicao%20com%20filtro%20e%20MDC.md) aprovadas pelo usuário); as tarefas ficam com o planejador. Lembretes:

- Escopo: a ADR-015 (decisão 1) trata o log básico por requisição como dentro do escopo; métricas, tracing distribuído e painéis continuam em "observabilidade avançada", fora do escopo de `docs/arquitetura/01 Visao geral.md`.
- Pela regra do projeto, o código de produção não cita RF nem CT.

## Decisões pendentes

- DP-01 — Prioridade e entrega. **Decidido** (decidida pelo usuário em 2026-10-06, aprovando a recomendação da [ADR-015](../arquitetura/adr/ADR-015%20Log%20por%20requisicao%20com%20filtro%20e%20MDC.md)): prioridade Média; desenvolver agora (2026-10-06), numa próxima release — não na v0.1.0, que já está em Produção.
- DP-02 — Formato do log. **Decidido** (decidida pelo usuário em 2026-10-06, aprovando a recomendação da [ADR-015](../arquitetura/adr/ADR-015%20Log%20por%20requisicao%20com%20filtro%20e%20MDC.md)): texto chave=valor, como no exemplo do pedido.
- DP-03 — Origem do trace ID. **Decidido** (decidida pelo usuário em 2026-10-06, aprovando a recomendação da [ADR-015](../arquitetura/adr/ADR-015%20Log%20por%20requisicao%20com%20filtro%20e%20MDC.md)): gerado pela aplicação a cada requisição (UUID sem hífens, 32 caracteres hexadecimais), devolvido no cabeçalho de resposta `X-Trace-Id` e presente em todas as linhas de log da requisição (MDC); trace ID vindo de fora não é aceito por enquanto.
- DP-04 — Valor de `service` e `environment`. **Decidido** (decidida pelo usuário em 2026-10-06, aprovando a recomendação da [ADR-015](../arquitetura/adr/ADR-015%20Log%20por%20requisicao%20com%20filtro%20e%20MDC.md)): `service` = `spring.application.name` (`tenant-rag-pgvector`); `environment` = propriedade `app.logging.environment=${APP_ENVIRONMENT:local}`.
- DP-05 — `clientId` no log. **Decidido** (decidida pelo usuário em 2026-10-06, aprovando a recomendação da [ADR-015](../arquitetura/adr/ADR-015%20Log%20por%20requisicao%20com%20filtro%20e%20MDC.md)): o número do caminho `/clients/{clientId}` em qualquer resultado (inclusive 401/403 e chamadas do administrador); `-` quando a rota não tem cliente; não registrar quem chamou.
- DP-06 — O que fica fora do log. **Decidido** (decidida pelo usuário em 2026-10-06, aprovando a recomendação da [ADR-015](../arquitetura/adr/ADR-015%20Log%20por%20requisicao%20com%20filtro%20e%20MDC.md)): além dos segredos da RN-05, corpo da requisição e da resposta, pergunta, texto e nome do arquivo, nome do cliente, query string, todos os cabeçalhos, IP e User-Agent.
- DP-07 — Abrangência e nível. **Decidido** (decidida pelo usuário em 2026-10-06, aprovando a recomendação da [ADR-015](../arquitetura/adr/ADR-015%20Log%20por%20requisicao%20com%20filtro%20e%20MDC.md)): Swagger UI e `/v3/api-docs` fora do log; nível `INFO` para status < 400, `WARN` para 4xx e `ERROR` para 5xx.

## Cenários de teste (QA)

Antes de implementar, leia os cenários abaixo em [05 Cenarios de teste.md](../QA/05%20Cenarios%20de%20teste.md) e implemente os automáticos com o ID no `@DisplayName`; os marcados como manual são executados por uma pessoa pelo roteiro em [07 Testes manuais.md](../QA/07%20Testes%20manuais.md). O requisito só está pronto com todos passando ([definição de pronto](../QA/01%20Estrategia%20de%20testes.md)).

Criados em 2026-10-05 pelo analista de QA e revisados em 2026-10-06, conforme as DP-02 a DP-07 decididas pelo usuário (o esperado já seguia a recomendação aprovada da ADR-015); entre colchetes, a decisão em que o esperado se baseia.

- CT-138 (P2) — Uma linha com os dez campos por requisição [DP-02, DP-04]
- CT-139 (P2) — endpoint sem query string e clientId tirado do caminho, `-` sem cliente (DDT) — CA-10, RN-09 [DP-05, DP-06]
- CT-140 (P2) — Falha na cadeia registra status=500 e a exceção continua — RN-08 [DP-07]
- CT-141 (P2) — traceId novo a cada requisição, no MDC só durante a requisição; trace ID de entrada não aceito — FA-05 [DP-03]
- CT-142 (P3) — Nível do log pelo status e rotas de apoio sem linha (DDT) — CA-10, FA-04, RN-08 [DP-07]
- CT-143 (P2) — Linha de log para 200, 201, 401, 403 e 404 pela API (DDT) — CA-10, RN-08, RN-09 [DP-05, DP-06, DP-07]
- CT-144 (P2) — Erro 500: o mesmo traceId na linha da requisição e no ERROR do GlobalExceptionHandler [DP-03]
- CT-145 (P2) — Resposta da API igual com e sem o log [DP-03]
- CT-146 (P1) — Nenhuma chave, hash ou senha aparece no log
- CT-147 (P1) — Linhas de /ask, /search e /documents sem pergunta, texto, nome de arquivo ou nome do cliente — CA-07 [DP-06]
- CT-148 (P1) — Configuração versionada não liga log de cabeçalhos, SQL com parâmetros ou da OpenAI
- CT-149 (P2, manual) — Linhas de log das requisições no console da IDE [DP-02, DP-03, DP-04, DP-05, DP-07]
- CT-150 (P1, manual) — Nenhuma chave, pergunta ou nome de arquivo no console da IDE [DP-06]

## Prioridade

Média — O pedido do usuário (2026-10-05) melhora o acompanhamento em produção, mas o cliente já recebe o valor prometido pelo README (pergunta e resposta isoladas por cliente) sem ele. Prioridade confirmada pelo usuário em 2026-10-06 (DP-01), com desenvolvimento agora, para uma próxima release.

## Status

- Status: Em andamento — implementado em 2026-10-06 (card HU-016 Em teste, fase Manual); faltam os testes manuais CT-149 e CT-150.
- Prontidão: pronto para desenvolvimento — DP-01 a DP-07 decididas pelo usuário em 2026-10-06 (recomendações da ADR-015). Os cenários de QA marcados com DP ficam para revisão do QA.
- 2026-10-05: requisito criado a partir do pedido do usuário (arquivo `observabilidade.md`). Não entra na release v0.1.0.
- 2026-10-06: o usuário decidiu as DP-01 a DP-07, aprovando as recomendações da ADR-015; regras, fluxos e critérios de aceite ajustados às decisões. Entra numa próxima release (não na v0.1.0, já em Produção).
- 2026-10-06: desenvolvimento concluído pelo desenvolvedor (T-801 a T-806); CT-138 a CT-148 passando.
