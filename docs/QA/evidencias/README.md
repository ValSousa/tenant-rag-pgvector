# Evidências de teste

Regras em [01 Estrategia de testes.md, seção 11.3](../01%20Estrategia%20de%20testes.md). Esta pasta guarda só arquivos Markdown e anexos pequenos (imagens, saídas de texto). Relatórios brutos de `target/` não são versionados.

## Estrutura

```text
docs/QA/evidencias/
├── README.md                          (este arquivo)
├── execucoes/                         (testes JUnit — resumo por execução relevante)
│   └── 2026-10-15-a1b2c3d.md
├── CT-003/                            (teste manual)
│   ├── 2026-10-15.md
│   └── 2026-10-15-swagger.png
└── CT-075/                            (teste opt-in com OpenAI real)
    └── 2026-11-02.md
```

Nome do arquivo: data da execução (`AAAA-MM-DD`); se houver mais de uma no mesmo dia, acrescentar `-2`, `-3`.

## Arquivos registrados

Lista real da pasta (atualizada em 2026-10-05). A árvore acima é só um exemplo de estrutura.

| Arquivo | Tipo | Conteúdo |
|---|---|---|
| [execucoes/2026-10-01-997f680.md](execucoes/2026-10-01-997f680.md) | Execução JUnit | RF-001 (CT-001, CT-002) |
| [execucoes/2026-10-01-rf-002.md](execucoes/2026-10-01-rf-002.md) | Execução JUnit | RF-002 |
| [execucoes/2026-10-01-rf-003-rf-004.md](execucoes/2026-10-01-rf-003-rf-004.md) | Execução JUnit | RF-003 e RF-004 |
| [execucoes/2026-10-02-rf-003-achado-1.md](execucoes/2026-10-02-rf-003-achado-1.md) | Execução JUnit | Correção do achado #1 da revisão do RF-003 (500 em vez de 401) |
| [execucoes/2026-10-02-997f680-rf-003.md](execucoes/2026-10-02-997f680-rf-003.md) | Execução JUnit | Verificação do QA do RF-003 depois da correção do achado #1 (55 testes) |
| [execucoes/2026-10-02-rf-005-rf-006.md](execucoes/2026-10-02-rf-005-rf-006.md) | Execução JUnit | Implementação do RF-005 e do RF-006, T-603 e CT-051 (112 testes) |
| [execucoes/2026-10-02-997f680-rf-005-rf-006.md](execucoes/2026-10-02-997f680-rf-005-rf-006.md) | Execução JUnit | Verificação do QA do RF-005 e do RF-006 (112 testes) |
| [execucoes/2026-10-02-997f680-rf-002-rf-004.md](execucoes/2026-10-02-997f680-rf-002-rf-004.md) | Execução JUnit | Verificação do QA do RF-002 (CT-051) e do RF-004 (112 testes) |
| [execucoes/2026-10-02-rf-007.md](execucoes/2026-10-02-rf-007.md) | Execução JUnit | Implementação do RF-007, T-504 e CT-063 (149 testes) |
| [execucoes/2026-10-02-qa-rf-007.md](execucoes/2026-10-02-qa-rf-007.md) | Execução JUnit | Verificação do QA do RF-007, CT-111 a CT-113 e CT-063 (149 testes) |
| [execucoes/2026-10-02-rf-008.md](execucoes/2026-10-02-rf-008.md) | Execução JUnit | Implementação do RF-008 e T-704 / CT-114 (174 testes) |
| [execucoes/2026-10-02-qa-rf-008.md](execucoes/2026-10-02-qa-rf-008.md) | Execução JUnit | Verificação do QA do RF-008 e CT-114 (174 testes) |
| [execucoes/2026-10-02-rf-009.md](execucoes/2026-10-02-rf-009.md) | Execução JUnit | Implementação do RF-009, CT-115 e achado #3 do RF-003 (229 testes) |
| [execucoes/2026-10-02-qa-rf-009.md](execucoes/2026-10-02-qa-rf-009.md) | Execução JUnit | Verificação do QA do RF-009 e CT-115 (229 testes) |
| [execucoes/2026-10-02-rf-010.md](execucoes/2026-10-02-rf-010.md) | Execução JUnit | Implementação do RF-010, CT-090 a CT-093 (245 testes) |
| [execucoes/2026-10-05-rf-006-limite-5mb.md](execucoes/2026-10-05-rf-006-limite-5mb.md) | Execução JUnit | RF-006: limite de upload de 5 MB (CT-053) e correção proposta do achado #1 da revisão (245 testes) |
| [CT-003/2026-10-01.md](CT-003/2026-10-01.md) | Teste manual | CT-003 — ambiente local com `docker compose` |
| [CT-020/2026-10-02.md](CT-020/2026-10-02.md) | Teste manual (complemento) | CT-020 — cadastro de cliente pelo Swagger UI, feito pelo QA; anexo [`2026-10-02-swagger.png`](CT-020/2026-10-02-swagger.png) (apiKey coberta) |
| [CT-035/2026-10-05.md](CT-035/2026-10-05.md) | Teste opt-in (OpenAI real) | CT-035 — textos parecidos mais próximos (OK; primeiro por dedução — 12 testes, 11 passaram —, depois confirmado por nome na reexecução da classe `RagQualityOpenAiIT` pelo IntelliJ, 12 de 12); anexos [`intellij-ragquality`](CT-035/2026-10-05-intellij-ragquality.png) e [`reexecucao-12-de-12`](CT-075/2026-10-05-reexecucao-12-de-12.png) |
| [CT-075/2026-10-05.md](CT-075/2026-10-05.md) | Teste opt-in (OpenAI real) | CT-075 — perguntas da seção 10 do README (OK na reexecução, 10 de 10; a tentativa 1 teve uma falha isolada em "perda total A", sem "80%" literal); anexos [`reexecucao-12-de-12`](CT-075/2026-10-05-reexecucao-12-de-12.png) e, da tentativa 1, [`intellij-ragquality`](CT-035/2026-10-05-intellij-ragquality.png) |
| [CT-076/2026-10-05.md](CT-076/2026-10-05.md) | Teste opt-in (OpenAI real) | CT-076 — pergunta sem resposta nos documentos (OK; confirmado por nome na reexecução); anexos [`reexecucao-12-de-12`](CT-075/2026-10-05-reexecucao-12-de-12.png) e [`intellij-ragquality`](CT-035/2026-10-05-intellij-ragquality.png) |
| [CT-116/2026-10-05.md](CT-116/2026-10-05.md) | Teste manual | CT-116 — banco e aplicação sobem pelo console da IDE (OK); anexos [`compose-ps`](CT-116/2026-10-05-compose-ps.png), [`console-ide`](CT-116/2026-10-05-console-ide.png), [`swagger`](CT-116/2026-10-05-swagger.png), [`docker-logs`](CT-116/2026-10-05-docker-logs.png) e, complementar, [`coluna-vector`](CT-116/2026-10-05-coluna-vector.png) |
| [CT-117/2026-10-05.md](CT-117/2026-10-05.md) | Teste manual | CT-117 — tabelas, índice HNSW e `vector(768)` no banco local (OK); anexos [`passo-2`](CT-117/2026-10-05-passo-2.png), [`passo-3`](CT-117/2026-10-05-passo-3.png), [`passo-4`](CT-117/2026-10-05-passo-4.png) |
| [CT-118/2026-10-05.md](CT-118/2026-10-05.md) | Teste manual | CT-118 — FK composta rejeita chunk de outro cliente (OK, P1); anexo [`2026-10-05-erro-fk.png`](CT-118/2026-10-05-erro-fk.png) |
| [CT-119/2026-10-05.md](CT-119/2026-10-05.md) | Teste manual | CT-119 — administrador cadastra clientes pelo Swagger UI (OK; ids 3 e 4, banco não zerado); anexos [`swagger-post`](CT-119/2026-10-05-swagger-post.png), [`authorize`](CT-119/2026-10-05-authorize.png), [`cliente-a`](CT-119/2026-10-05-cliente-a.png), [`cliente-b`](CT-119/2026-10-05-cliente-b.png) e, complementar, [`nome-sql`](CT-119/2026-10-05-nome-sql.png) (apiKeys cobertas) |
| [CT-120/2026-10-05.md](CT-120/2026-10-05.md) | Teste manual | CT-120 — consulta de cliente e validação do nome (OK): 200 para o próprio cliente e o administrador, 400 para nome vazio e ausente, nenhum cliente novo; complementar: chave de outro cliente → 403; anexos [`passo-1-cliente`](CT-120/2026-10-05-passo-1-cliente.png), [`passo-2-admin`](CT-120/2026-10-05-passo-2-admin.png), [`passo-3-nome-vazio`](CT-120/2026-10-05-passo-3-nome-vazio.png), [`passo-4-sem-nome`](CT-120/2026-10-05-passo-4-sem-nome.png), [`outro-cliente-403`](CT-120/2026-10-05-outro-cliente-403.png) |
| [CT-121/2026-10-05.md](CT-121/2026-10-05.md) | Teste manual | CT-121 — embeddings reais da OpenAI com 768 dimensões nos chunks do cliente 6 (OK); anexo [`dimensoes`](CT-121/2026-10-05-dimensoes.png) |
| [CT-122/2026-10-05.md](CT-122/2026-10-05.md) | Teste manual | CT-122 — chunks do `contrato.pdf` (documento 9) sem lacuna e com até 1000 caracteres (OK); anexo [`chunks`](CT-122/2026-10-05-chunks.png) e a resposta do upload em [`CT-126/reenvio`](CT-126/2026-10-05-reenvio.png) |
| [CT-123/2026-10-05.md](CT-123/2026-10-05.md) | Teste manual | CT-123 — arquivo que não é PDF → 422 "Não foi possível ler o arquivo PDF.", nada gravado (OK); complementar: PDF sem texto → 422; anexos [`corrompido-422`](CT-123/2026-10-05-corrompido-422.png) e [`pdf-sem-texto-422`](CT-123/2026-10-05-pdf-sem-texto-422.png) |
| [CT-124/2026-10-05.md](CT-124/2026-10-05.md) | Teste manual | CT-124 — Cliente A (id 6) envia contrato, sinistro e vistoria pelo Swagger UI (OK, P1); anexos [`contrato`](CT-124/2026-10-05-contrato.png), [`sinistro`](CT-124/2026-10-05-sinistro.png), [`vistoria`](CT-124/2026-10-05-vistoria.png), [`banco`](CT-124/2026-10-05-banco.png) |
| [CT-125/2026-10-05.md](CT-125/2026-10-05.md) | Teste manual | CT-125 — envio para a conta de outro cliente, de cliente inexistente e pelo administrador → 403 (OK, P1); anexos [`contagem-antes`](CT-125/2026-10-05-contagem-antes.png), [`passo-2-outro-cliente`](CT-125/2026-10-05-passo-2-outro-cliente.png), [`passo-3-cliente-inexistente`](CT-125/2026-10-05-passo-3-cliente-inexistente.png), [`passo-4-admin`](CT-125/2026-10-05-passo-4-admin.png) |
| [CT-126/2026-10-05.md](CT-126/2026-10-05.md) | Teste manual | CT-126 — reenvio do `contrato.pdf` substitui o documento (OK; id 6 → 9, antigo sem chunks); anexos [`antes`](CT-126/2026-10-05-antes.png), [`reenvio`](CT-126/2026-10-05-reenvio.png), [`chunks-antigo`](CT-126/2026-10-05-chunks-antigo.png) |
| [CT-127/2026-10-05.md](CT-127/2026-10-05.md) | Teste manual | CT-127 — busca do Cliente A (id 6) pelo Postman com `topK` 3 e sem `topK`: franquia de R$ 4.000,00 do `contrato.pdf` em primeiro (OK); anexos [`topk-3`](CT-127/2026-10-05-topk-3.png), [`sem-topk`](CT-127/2026-10-05-sem-topk.png) |
| [CT-128/2026-10-05.md](CT-128/2026-10-05.md) | Teste manual | CT-128 — Cliente C (id 8, sem documentos): busca → `results: []` e `/ask` → resposta fixa com `sources: []` (OK, P1); complementos: administrador → 403, `/ask` do Cliente A com fontes só dele, pergunta sem relação (HU-014); anexos [`busca-sem-topk`](CT-128/2026-10-05-busca-sem-topk.png), [`busca-topk-20`](CT-128/2026-10-05-busca-topk-20.png), [`passo-3-ask`](CT-128/2026-10-05-passo-3-ask.png), [`admin-403`](CT-128/2026-10-05-admin-403.png), [`cliente-a-ask-franquia`](CT-128/2026-10-05-cliente-a-ask-franquia.png), [`cliente-a-pergunta-sem-relacao`](CT-128/2026-10-05-cliente-a-pergunta-sem-relacao.png) |
| [CT-129/2026-10-05.md](CT-129/2026-10-05.md) | Teste manual | CT-129 — pergunta de perda total pelo Swagger: Cliente A (id 6) "Sim" com 75% e 80%, Cliente B (id 7) "Não" com 70% e 53,7%, fontes só do próprio cliente (OK); anexos [`cliente-a`](CT-129/2026-10-05-cliente-a.png), [`cliente-b`](CT-129/2026-10-05-cliente-b.png) |
| [CT-130/2026-10-05.md](CT-130/2026-10-05.md) | Teste manual | CT-130 — autenticação e permissões por chave (OK, P1): sem chave e chave inexistente → 401; cliente cadastrando cliente, administrador na busca e Cliente A nos ids 7 e 999 → 403 com corpo idêntico exceto `instance`; próprio id → 200; anexos [`passo-1-sem-chave`](CT-130/2026-10-05-passo-1-sem-chave.png), [`passo-2-chave-inexistente`](CT-130/2026-10-05-passo-2-chave-inexistente.png), [`passo-3-cliente-cadastra`](CT-130/2026-10-05-passo-3-cliente-cadastra.png), [`passo-4-admin-busca`](CT-130/2026-10-05-passo-4-admin-busca.png), [`passo-5-outro-cliente`](CT-130/2026-10-05-passo-5-outro-cliente.png), [`passo-5-inexistente-999`](CT-130/2026-10-05-passo-5-inexistente-999.png), [`passo-6-proprio`](CT-130/2026-10-05-passo-6-proprio.png) |
| [CT-131/2026-10-05.md](CT-131/2026-10-05.md) | Teste manual | CT-131 — Swagger UI e `/v3/api-docs` públicos, operações só com Authorize (OK); anexos [`swagger-grupos`](CT-131/2026-10-05-swagger-grupos.png), [`api-docs`](CT-131/2026-10-05-api-docs.png), [`passo-2-401`](CT-131/2026-10-05-passo-2-401.png), [`authorize`](CT-131/2026-10-05-authorize.png), [`passo-4-proprio-200`](CT-131/2026-10-05-passo-4-proprio-200.png), [`passo-4-outro-403`](CT-131/2026-10-05-passo-4-outro-403.png), [`passo-5-logout-401`](CT-131/2026-10-05-passo-5-logout-401.png) |
| [CT-132/2026-10-05.md](CT-132/2026-10-05.md) | Teste manual (parcial) | CT-132 — erros em ProblemDetail, em português: 404 do cliente 999, 400 com `errors` na busca e 400 do `documentType` inválido OK; falta o passo 4 (arquivo que não é PDF); complementos: upload sem arquivo → 400 e upload com falha da IA → 503 sem detalhes técnicos (indício do CT-133); anexos [`passo-1-404`](CT-132/2026-10-05-passo-1-404.png), [`passo-2-400-validacao`](CT-132/2026-10-05-passo-2-400-validacao.png), [`passo-3-document-type`](CT-132/2026-10-05-passo-3-document-type.png), [`passo-4-arquivo-vazio`](CT-132/2026-10-05-passo-4-arquivo-vazio.png), [`upload-503-ia`](CT-132/2026-10-05-upload-503-ia.png) |
| [CT-134/2026-10-05.md](CT-134/2026-10-05.md) | Teste manual | CT-134 — documentos de exemplo conferem com o gabarito e foram ingeridos para os Clientes A (id 6) e B (id 7) (OK, informado pelo usuário; sem prints por decisão dele; conferência via `psql`) |
| [CT-137/2026-10-05.md](CT-137/2026-10-05.md) | Teste manual | CT-137 — upload acima de 5 MB recusado com 413 "Arquivo muito grande" (OK; `java.pdf` real em vez do arquivo de 6 MB; 413 chegou sem conexão fechada); anexo [`upload-413`](CT-137/2026-10-05-upload-413.png) |

## Modelo — execução JUnit (`execucoes/AAAA-MM-DD-<commit>.md`)

Gerado pelo script `qa/atualizar-status-testes.py` (T-108). Enquanto o script não existir, preencher à mão:

```markdown
# Execução JUnit — 2026-10-15 — commit a1b2c3d

- **Comando:** `./mvnw verify`
- **Motivo:** fechamento da Etapa 1
- **Executor:** <nome>
- **Resultado:** 18 testes, 18 aprovados, 0 falhas, 0 ignorados
- **Cobertura (JaCoCo):** linhas 82%, ramos 71%

| Cenário | Teste | Resultado |
|---|---|---|
| CT-001 | `SchemaMigrationIT.deveTerExtensaoVector` | Passou |
| CT-013 | `SchemaMigrationIT.deveRejeitarChunkDeOutroCliente` | Passou |

## Falhas

Nenhuma.
```

## Modelo — teste manual (`CT-xxx/AAAA-MM-DD.md`)

```markdown
# CT-003 — Ambiente local com docker compose

- **Data:** 2026-10-15
- **Executor:** <nome>
- **Commit:** a1b2c3d
- **Ambiente:** Windows 10, Docker Desktop 4.x, Java 21

## Passos executados

1. `docker compose up -d`
2. `mvnw.cmd spring-boot:run` com `OPENAI_API_KEY` e `ADMIN_API_KEY` definidas
3. Abrir `http://localhost:8080/swagger-ui.html`

## Resultado esperado

Contêiner `rag-postgres` em estado healthy e Swagger UI acessível.

## Resultado obtido

<o que aconteceu; colar a saída relevante>

## Anexos

- `2026-10-15-swagger.png`

## Veredito

OK  |  Bug (BUG-xxx)
```

## Modelo — teste opt-in com OpenAI real (`CT-xxx/AAAA-MM-DD.md`)

```markdown
# CT-075 — Perguntas da seção 10 do README (OpenAI real)

- **Data:** 2026-11-02
- **Executor:** <nome>
- **Commit:** a1b2c3d
- **Comando:** `./mvnw test -Dgroups=openai -Dtest.excluded.groups=nenhum -Dtest=RagQualityOpenAiIT`
- **Modelos:** text-embedding-3-small (768) · gpt-4o-mini

| Cliente | Pergunta | Termos esperados encontrados | Termos proibidos encontrados | Veredito |
|---|---|---|---|---|
| A | Qual é o valor da franquia da apólice? | R$ 3.500,00 | — | OK |

## Respostas completas

### A — Qual é o valor da franquia da apólice?

> <resposta do modelo, sem edição>

Fontes: [1] contrato.pdf (CONTRACT), chunk 7
```
