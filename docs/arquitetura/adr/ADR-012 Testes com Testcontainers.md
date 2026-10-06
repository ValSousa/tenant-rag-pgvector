# ADR-012 — Testes de integração com Testcontainers e embeddings fixos

- **Status:** Aprovada (decisão do responsável pelo projeto em 2026-10-01)
- **Data:** 2026-10-01
- **Requisitos:** RF-012 (resolve DP-01), RF-001 DP-03

## Contexto

Com JPA no classpath, o `contextLoads` atual passa a exigir banco. O isolamento entre clientes está no SQL, então precisa ser testado contra PostgreSQL + pgvector real. Chamar a OpenAI nos testes exigiria chave, teria custo, dependeria de internet e daria resultados variáveis.

## Decisão

- **Testcontainers** com a imagem `pgvector/pgvector:pg17` e `@ServiceConnection` do Spring Boot; contêiner reaproveitado entre classes.
- **`FakeEmbeddingModel`** (implementa `EmbeddingModel` do LangChain4j) determinístico como `@Primary` nos testes; `RagAssistant` mockado com `@MockitoBean`.
- O `PgVectorEmbeddingStore` é **real** nos testes de integração, contra o banco do Testcontainers: é ele que gera o SQL com o filtro por cliente.
- Perfil `test` define uma chave OpenAI falsa só para os beans subirem; ela nunca é usada.
- Unitários (`*Test`) e integração (`*IT`) rodam juntos em `mvnw test`.
- Casos obrigatórios em [08 Estrategia de testes.md](../08%20Estrategia%20de%20testes.md), seção 5.

## Alternativas consideradas

- **Usar o banco do `docker compose`:** testes dependem do estado do banco local e de alguém lembrar de subir o contêiner.
- **H2 em memória:** não tem `vector` nem `<=>`; o teste não provaria nada.
- **OpenAI real nos testes:** custo, rede e resultados variáveis; fica para validação manual com o gabarito.

## Consequências

- `mvnw test` precisa de Docker rodando, mas não de `OPENAI_API_KEY` nem de internet para a IA.
- O Spring Boot 4 usa Testcontainers 2.x (nomes de artefato e pacotes mudaram); gerar o `pom.xml` pelo start.spring.io.
- A qualidade real das respostas (RF-008 CA-01) é validada manualmente, não em `mvnw test`.
