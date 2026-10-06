# ADR-001 — Monólito Spring Boot em camadas

- **Status:** Aprovada (definida no README)
- **Data:** 2026-10-01
- **Requisitos:** todos

## Contexto

O README define uma aplicação Spring Boot única, com pacotes `controller`, `service`, `repository`, `entity`, `dto`, `exception` e `config`. O projeto é de estudo, com um único desenvolvedor e volume pequeno.

## Decisão

Uma aplicação Spring Boot (Web MVC) em camadas, pacote base `br.com.rag_pgvector`, com a regra de dependência `controller → service → repository → entity`. Pacotes adicionais `ingestion` (PDF e chunking, Java puro) e `security` (autenticação) seguem a mesma regra. Configuração em `application.properties`.

## Alternativas consideradas

- **Arquitetura hexagonal (ports and adapters):** isola melhor o domínio, mas acrescenta interfaces e mapeamentos que escondem o que o projeto quer ensinar. Pode ser adotada depois, se o projeto crescer.
- **Microsserviços (ingestão e busca separadas):** sem justificativa de escala; aumenta muito a complexidade operacional.

## Consequências

- Simples de entender, rodar e depurar.
- O isolamento entre clientes depende de disciplina nas camadas (07, seção 3): repository sempre com `clientId`.
- O pacote `ingestion` já separa a parte "pura" e facilita uma futura extração.
