# Aprovações pendentes

Pontos da arquitetura que precisam de aprovação manual do responsável pelo projeto. Mantido pelo `arquiteto-de-software`: cada ADR `Em revisão`, mudança de escopo ou de custo e escolha que o requisito não decide entra numa linha; quando o responsável decidir, a linha é atualizada (não apagada).

| # | Arquivo | Descrição | Status |
|---|---|---|---|
| 1 | adr/ADR-014 Pacote validator e excecoes proprias.md | Criar o pacote `validator` (`DocumentFileValidator`) e trocar `ResponseStatusException` por exceções próprias (`InvalidFileException` → 400) | Aprovada (2026-10-05) |
| 2 | adr/ADR-015 Log por requisicao com filtro e MDC.md | Aprovar a solução do log por requisição: filtro servlet `RequestLoggingFilter` no novo pacote `logging`, antes do Spring Security, com trace ID no MDC e sem dependência nova (o log básico entra no escopo; observabilidade avançada continua fora) | Aguardando aprovação |
| 3 | adr/ADR-015 Log por requisicao com filtro e MDC.md | RF-013 DP-01 — Prioridade e entrega. Recomendação: manter Média e entregar depois da v0.1.0, como Etapa 8 do plano, sem prazo | Aguardando aprovação |
| 4 | adr/ADR-015 Log por requisicao com filtro e MDC.md | RF-013 DP-02 — Formato do log. Recomendação: texto chave=valor, como no exemplo do pedido (JSON fica disponível só mudando `logging.structured.format.console`) | Aguardando aprovação |
| 5 | adr/ADR-015 Log por requisicao com filtro e MDC.md | RF-013 DP-03 — Trace ID. Recomendação: gerado pela aplicação a cada requisição, devolvido no cabeçalho de resposta `X-Trace-Id`, presente em todas as linhas de log da requisição; não aceitar trace ID de entrada por enquanto | Aguardando aprovação |
| 6 | adr/ADR-015 Log por requisicao com filtro e MDC.md | RF-013 DP-04 — `service` e `environment`. Recomendação: `service` = `spring.application.name` (`tenant-rag-pgvector`); `environment` = `app.logging.environment`, variável `APP_ENVIRONMENT`, padrão `local` | Aguardando aprovação |
| 7 | adr/ADR-015 Log por requisicao com filtro e MDC.md | RF-013 DP-05 — `clientId`. Recomendação: número do caminho `/clients/{clientId}` em qualquer resultado (inclusive 401/403 e chamadas do administrador); `-` quando a rota não tem cliente; não registrar quem chamou | Aguardando aprovação |
| 8 | adr/ADR-015 Log por requisicao com filtro e MDC.md | RF-013 DP-06 — O que fica fora do log. Recomendação: corpo de requisição e resposta, pergunta, texto e nome do arquivo, nome do cliente, query string, cabeçalhos, IP e `User-Agent` | Aguardando aprovação |
| 9 | adr/ADR-015 Log por requisicao com filtro e MDC.md | RF-013 DP-07 — Rotas de apoio e nível. Recomendação: Swagger UI e `/v3/api-docs` fora do log; `INFO` para status < 400, `WARN` para 4xx, `ERROR` para 5xx | Aguardando aprovação |

As ADRs 001 a 013 foram aprovadas em 2026-10-01 e a ADR-014 em 2026-10-05. Pendentes desde 2026-10-05: a ADR-015 (log por requisição do RF-013) e as decisões DP-01 a DP-07 do RF-013, com a recomendação técnica de cada uma na seção "Pontos para aprovação" da ADR-015. As DPs são decisões do responsável; ao decidir, o `analista-de-requisitos` registra a resposta no RF-013 e o arquiteto ajusta a ADR-015.
