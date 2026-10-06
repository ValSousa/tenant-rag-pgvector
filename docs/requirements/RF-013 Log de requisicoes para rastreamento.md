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
2. A aplicação associa um trace ID à requisição (origem conforme DP-03).
3. A aplicação processa a requisição normalmente.
4. Ao terminar, a aplicação registra uma linha de log com `timestamp`, `level`, `service`, `environment`, `method`, `endpoint`, `status`, `clientId`, `traceId` e `duration`.
5. A resposta é devolvida ao consumidor sem mudança de conteúdo.

## Fluxos alternativos

- FA-01 — Requisição recusada pela segurança (sem chave, chave inválida ou acesso a outro cliente): a linha de log é registrada mesmo assim, com o `status` devolvido (401 ou 403).
- FA-02 — Requisição que termina em erro (4xx ou 5xx tratado pelo `GlobalExceptionHandler`): a linha de log é registrada com o `status` devolvido; o log de erro que já existe (RF-010 FA-01) continua e pode ser relacionado à requisição pelo trace ID.
- FA-03 — Requisição sem cliente identificado (sem autenticação ou rota sem `clientId`): o campo `clientId` fica vazio ou é omitido (conforme DP-05).

## Regras de negócio

- RN-01 — Toda requisição às APIs gera uma linha de log de requisição, inclusive as recusadas e as que terminam em erro ("resultado da operação" no pedido). Quais rotas de apoio entram (Swagger, `/v3/api-docs`) fica na DP-07.
- RN-02 — A linha traz os campos do pedido: `timestamp`, `level`, `service`, `environment`, `method`, `endpoint`, `status`, `clientId`, `traceId` e `duration`.
- RN-03 — `duration` é o tempo da requisição em milissegundos (no exemplo, `duration=42ms`).
- RN-04 — Cada requisição tem um trace ID; o mesmo valor aparece em todas as linhas de log daquela requisição (correlation ID).
- RN-05 — Nenhum log, de nenhum nível, registra o valor de chaves de API ou segredos: o cabeçalho `X-API-Key` (de cliente ou de administrador), `ADMIN_API_KEY`, `OPENAI_API_KEY`, a senha do banco (`DB_PASSWORD`) nem o hash das chaves. Isso segue a regra do projeto de que chaves de API nunca ficam em claro.
- RN-06 — O log não traz dados pessoais desnecessários; o que fica fora além dos segredos (corpo da requisição e da resposta, pergunta do `/ask`, texto dos documentos, nome do cliente, nome do arquivo) é definido na DP-06.
- RN-07 — O log não altera a resposta da API nem o resultado da operação.

## Dados de entrada

| Campo | Tipo | Obrigatório |
|---|---|---|
| método HTTP da requisição | texto (`GET`, `POST`…) | Sim |
| caminho da requisição (endpoint) | texto | Sim |
| status HTTP da resposta | número | Sim |
| instante de início e de fim da requisição | data e hora | Sim |
| `clientId` (caminho `/clients/{clientId}` e chave autenticada) | número | Não (DP-05) |
| trace ID recebido em cabeçalho | texto | Não (DP-03) |
| `service` e `environment` | texto | Sim (origem na DP-04) |

## Dados de saída

- Uma linha de log por requisição com os dez campos da RN-02, no formato definido na DP-02.
- Talvez o trace ID num cabeçalho da resposta (DP-03).

## Critérios de aceite

- CA-01 — Cada requisição a um endpoint da API gera exatamente uma linha de log de requisição com os dez campos da RN-02.
- CA-02 — `GET /clients/{id}` com a chave do próprio cliente gera uma linha com `method=GET`, `endpoint=/clients/{id}`, `status=200`, `clientId={id}`, `duration` em milissegundos e um `traceId` não vazio.
- CA-03 — Uma requisição sem `X-API-Key` gera uma linha com `status=401`; a chave de um cliente na rota de outro gera uma linha com `status=403`.
- CA-04 — Uma requisição que termina em 500 gera uma linha com `status=500`, e o log de erro do `GlobalExceptionHandler` daquela requisição traz o mesmo `traceId`.
- CA-05 — Duas requisições seguidas sem trace ID de entrada têm `traceId` diferentes.
- CA-06 — Com chaves conhecidas nos testes (de cliente, de administrador, da OpenAI e senha do banco), nenhuma linha de log capturada durante as requisições contém esses valores nem o hash da chave do cliente.
- CA-07 — A linha de log de `POST /clients/{id}/ask` e de `POST /clients/{id}/documents` não contém a pergunta nem o texto do documento (conforme DP-06).
- CA-08 — A resposta da API (status, cabeçalhos já existentes e corpo) é a mesma com e sem o log.
- CA-09 — Os testes automáticos verificam os campos do log com `mvnw test`, sem chamar a OpenAI.

## Dependências

- RF-009 — identifica o cliente autenticado e produz os 401/403 que também são registrados.
- RF-010 — o log de erro existente deve ser relacionado à requisição pelo trace ID.

## Situação atual do projeto

Atualizado em 2026-10-05:

- Não há log por requisição, trace ID nem medição de duração.
- O `GlobalExceptionHandler` (RF-010) já registra as exceções inesperadas no log, sem expor detalhes na resposta.
- As chaves de cliente são guardadas só como hash SHA-256 (RF-009 DP-01); a chave de administrador, a chave da OpenAI e a senha do banco vêm de variáveis de ambiente ou do `.env`.

## Itens a implementar

A definir pelo arquiteto e pelo planejador depois das decisões pendentes. Lembretes:

- Ponto para o arquiteto: compatibilizar com "observabilidade avançada" fora do escopo em `docs/arquitetura/01 Visao geral.md`.
- Pela regra do projeto, o código de produção não cita RF nem CT.

## Decisões pendentes

- DP-01 — Prioridade e entrega. O usuário disse em 2026-10-05 "vamos desenvolver em outro momento": não entra na release v0.1.0 e não tem prazo. Prioridade registrada como Média (ver "Prioridade"); falta o usuário confirmar a prioridade e em qual entrega o requisito entra.
- DP-02 — Formato do log: texto simples chave=valor, como no exemplo do pedido, ou JSON estruturado (uma linha JSON por requisição).
- DP-03 — Origem do trace ID: gerado pela aplicação a cada requisição e/ou aceito de um cabeçalho de entrada (ex.: `X-Trace-Id` ou `traceparent`); se é devolvido num cabeçalho da resposta; se também aparece nas linhas de log de erro já existentes (necessário para o CA-04).
- DP-04 — Valor de `service` e `environment`: o projeto só roda localmente e o exemplo mostra `service=client-api` e `environment=prod`. Falta decidir se os valores são fixos ou vêm de propriedade ou variável de ambiente, e quais são.
- DP-05 — `clientId` no log: é o id interno do cliente (não é dado pessoal). Falta decidir quando registrar: só nas rotas `/clients/{clientId}/**`; nas chamadas do administrador (registra o `clientId` do caminho ou marca como administrador); e nas requisições sem autenticação ou recusadas (vazio ou omitido).
- DP-06 — O que fica fora do log, além dos segredos da RN-05 (que nunca entram): corpo da requisição e da resposta, pergunta do `/ask`, conteúdo dos documentos, nome do cliente, nome do arquivo enviado e query string.
- DP-07 — Abrangência e nível: se as rotas de apoio (`/swagger-ui`, `/v3/api-docs`) também geram linha de log; qual `level` cada resultado usa (ex.: `INFO` para 2xx, `WARN` para 4xx, `ERROR` para 5xx — o pedido só mostra `INFO` para 200).

## Cenários de teste (QA)

Antes de implementar, leia os cenários abaixo em [05 Cenarios de teste.md](../QA/05%20Cenarios%20de%20teste.md) e implemente os automáticos com o ID no `@DisplayName`; os marcados como manual são executados por uma pessoa pelo roteiro em [07 Testes manuais.md](../QA/07%20Testes%20manuais.md). O requisito só está pronto com todos passando ([definição de pronto](../QA/01%20Estrategia%20de%20testes.md)).

Criados em 2026-10-05 pelo analista de QA, com o resultado esperado pela recomendação da ADR-015 para as DP-02 a DP-07 ainda abertas; entre colchetes, a decisão que afeta o esperado. Depois da decisão do usuário, o QA revisa os cenários marcados.

- CT-138 (P2) — Uma linha com os dez campos por requisição [DP-02, DP-04]
- CT-139 (P2) — endpoint sem query string e clientId tirado do caminho (DDT) [DP-05, DP-06]
- CT-140 (P2) — Falha na cadeia registra status=500 e a exceção continua [DP-07]
- CT-141 (P2) — traceId novo a cada requisição, no MDC só durante a requisição [DP-03]
- CT-142 (P3) — Nível do log pelo status e rotas de apoio sem linha (DDT) [DP-07]
- CT-143 (P2) — Linha de log para 200, 201, 401, 403 e 404 pela API (DDT) [DP-05, DP-06, DP-07]
- CT-144 (P2) — Erro 500: o mesmo traceId na linha da requisição e no ERROR do GlobalExceptionHandler [DP-03]
- CT-145 (P2) — Resposta da API igual com e sem o log [DP-03]
- CT-146 (P1) — Nenhuma chave, hash ou senha aparece no log
- CT-147 (P1) — Linhas de /ask, /search e /documents sem pergunta, texto, nome de arquivo ou nome do cliente [DP-06]
- CT-148 (P1) — Configuração versionada não liga log de cabeçalhos, SQL com parâmetros ou da OpenAI
- CT-149 (P2, manual) — Linhas de log das requisições no console da IDE [DP-02, DP-03, DP-04, DP-05, DP-07]
- CT-150 (P1, manual) — Nenhuma chave, pergunta ou nome de arquivo no console da IDE [DP-06]

## Prioridade

Média — O pedido do usuário (2026-10-05) melhora o acompanhamento em produção, mas o cliente já recebe o valor prometido pelo README (pergunta e resposta isoladas por cliente) sem ele; o usuário disse que o desenvolvimento fica para outro momento (DP-01).

## Status

- Status: Pendente
- Prontidão: aguardando informação (AGUARDANDO_INFORMACAO) — DP-01 a DP-07 em aberto; depois disso, arquitetura, planejamento e cenários de QA.
- 2026-10-05: requisito criado a partir do pedido do usuário (arquivo `observabilidade.md`). Não entra na release v0.1.0.
