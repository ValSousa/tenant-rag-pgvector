# Guias — tenant-rag-pgvector

Guias de uso e operação para quem chega ao projeto. Descrevem só o que já existe no código; o que ainda não foi entregue aparece como "planejado (RF-xxx)". Atualizado em 2026-10-05 (entregas até o RF-010; limite de upload de 5 MB).

| Guia | Para que serve |
|---|---|
| [Como executar.md](Como%20executar.md) | Pré-requisitos, variáveis de ambiente, `docker compose`, `mvnw`, perfis e Swagger UI |
| [Como testar.md](Como%20testar.md) | `mvnw test`, testes por classe, teste opt-in com a OpenAI real, cobertura e onde ficam os resultados |
| [Uso da API.md](Uso%20da%20API.md) | Fluxo com `curl` e Swagger UI: cadastro e consulta de clientes, upload de PDFs (inclusive os de exemplo), busca, pergunta com resposta e fontes, isolamento entre clientes e erros |

Outras pastas de `docs/`: [requisitos](../requirements/), [arquitetura](../arquitetura/README.md), [QA](../QA/README.md), [backlog](../backlog/Backlog.md) e [revisões de código](../revisao/). Histórico de versões: [CHANGELOG](../../CHANGELOG.md). Documentos de exemplo e gabarito: [`documents/gabarito.md`](../../documents/gabarito.md).
