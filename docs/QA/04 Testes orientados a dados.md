# 04 — Testes orientados a dados (DDT)

DDT (*Data-Driven Testing*): um mesmo teste roda várias vezes, uma por linha de uma massa de dados. A lógica fica no código; os casos ficam numa tabela fácil de ler e de estender. Para incluir um caso novo, adiciona-se uma linha — não um método.

## 1. Quando usar

| Use DDT quando | Não use quando |
|---|---|
| O mesmo comportamento é verificado com muitas entradas (validações, limites, matriz de permissões) | Cada caso precisa de montagem própria e diferente |
| Os casos são de interesse de quem não lê Java (QA, analista) | Há só 1 ou 2 casos |
| Novos casos surgem com frequência (novo endpoint → nova linha na matriz de segurança) | O dado é um objeto complexo que não cabe numa linha (use `@MethodSource`) |

## 2. Convenções

| Item | Regra |
|---|---|
| Local dos arquivos | `src/test/resources/ddt/<nome>.csv` |
| Formato | CSV UTF-8, separador `;`, primeira linha é cabeçalho (`numLinesToSkip = 1`) |
| Primeira coluna | Sempre `cenario`: descrição curta que aparece no nome do teste (`name = "[{index}] {0}"`) |
| Valores especiais | `<vazio>` = string vazia; `<null>` = ausente; `<espacos>` = `"   "`; `<n x>` = `x` repetido n vezes (ex.: `<256 a>`) — convertidos por `DdtValores.resolver(String)` |
| Fonte JUnit | `@CsvFileSource` para massas em arquivo; `@CsvSource` só para até 5 linhas; `@MethodSource` para objetos (exceções, PDFs); `@EnumSource` para enums |
| Nome do teste | `@DisplayName` com o ID do cenário (`CT-xxx`) |
| Revisão | Mudança em CSV passa por revisão como código |

Dependência: `junit-jupiter-params`, já incluída pelo starter de teste do Spring Boot.

## 3. Massas de dados

### 3.1 `security-matrix.csv` — CT-080 (RF-009, ADR-008)

Consumido por `SecurityConfigTest`. `PERMITIDO` significa "a segurança deixou passar e o controller atendeu": status 2xx ou 3xx, com os services mockados devolvendo uma resposta válida (o `/swagger-ui.html` redireciona para o `index.html`). Na massa, `{A}` = 1 e `{B}` = 2. Atualizada em 2026-10-02 (RF-009): todos os endpoints atuais com dono, outro cliente, admin, chave inválida e sem chave, mais as linhas de PUT, DELETE e PATCH em `/clients/{id}` (revisão do RF-003, achado #3).

```csv
cenario;chave;metodo;rota;esperado
sem chave em busca;<null>;POST;/clients/1/search;401
chave inválida em busca;CHAVE_INVALIDA;POST;/clients/1/search;401
cliente A na própria busca;CHAVE_A;POST;/clients/1/search;PERMITIDO
cliente A na busca de B;CHAVE_A;POST;/clients/2/search;403
cliente A na busca de cliente inexistente;CHAVE_A;POST;/clients/999/search;403
cliente B na busca de A;CHAVE_B;POST;/clients/1/search;403
sem chave no upload;<null>;POST;/clients/1/documents;401
chave inválida no upload;CHAVE_INVALIDA;POST;/clients/1/documents;401
cliente A no próprio upload;CHAVE_A;POST;/clients/1/documents;PERMITIDO
cliente A no upload de B;CHAVE_A;POST;/clients/2/documents;403
cliente B no upload de A;CHAVE_B;POST;/clients/1/documents;403
sem chave no ask;<null>;POST;/clients/1/ask;401
chave inválida no ask;CHAVE_INVALIDA;POST;/clients/1/ask;401
cliente A no próprio ask;CHAVE_A;POST;/clients/1/ask;PERMITIDO
cliente A no ask de B;CHAVE_A;POST;/clients/2/ask;403
cliente B no ask de A;CHAVE_B;POST;/clients/1/ask;403
admin na busca de A;CHAVE_ADMIN;POST;/clients/1/search;403
admin no upload de A;CHAVE_ADMIN;POST;/clients/1/documents;403
admin no ask de A;CHAVE_ADMIN;POST;/clients/1/ask;403
admin cria cliente;CHAVE_ADMIN;POST;/clients;PERMITIDO
cliente A cria cliente;CHAVE_A;POST;/clients;403
chave inválida cria cliente;CHAVE_INVALIDA;POST;/clients;401
sem chave cria cliente;<null>;POST;/clients;401
admin consulta cliente A;CHAVE_ADMIN;GET;/clients/1;PERMITIDO
cliente A consulta a si mesmo;CHAVE_A;GET;/clients/1;PERMITIDO
cliente A consulta B;CHAVE_A;GET;/clients/2;403
cliente B consulta A;CHAVE_B;GET;/clients/1;403
sem chave consulta cliente;<null>;GET;/clients/1;401
cliente A altera a si mesmo (PUT);CHAVE_A;PUT;/clients/1;403
cliente A apaga a si mesmo (DELETE);CHAVE_A;DELETE;/clients/1;403
cliente A altera a si mesmo (PATCH);CHAVE_A;PATCH;/clients/1;403
admin apaga cliente A (DELETE);CHAVE_ADMIN;DELETE;/clients/1;403
sem chave apaga cliente (DELETE);<null>;DELETE;/clients/1;401
clientId não numérico;CHAVE_A;POST;/clients/abc/search;403
rota desconhecida com chave;CHAVE_A;GET;/actuator/env;403
rota desconhecida com chave de admin;CHAVE_ADMIN;GET;/actuator/env;403
rota desconhecida sem chave;<null>;GET;/actuator/env;401
swagger ui sem chave;<null>;GET;/swagger-ui.html;PERMITIDO
swagger ui index sem chave;<null>;GET;/swagger-ui/index.html;PERMITIDO
api-docs sem chave;<null>;GET;/v3/api-docs;PERMITIDO
```

Regra de manutenção: **todo endpoint novo entra nesta matriz** com pelo menos: dono (PERMITIDO), outro cliente (403), admin (403 ou PERMITIDO) e sem chave (401).

### 3.2 `client-name-validation.csv` — CT-021 (RF-003)

```csv
cenario;name;esperado;campoComErro
nome válido;Cliente A;201;<null>
nome com 1 caractere;A;201;<null>
nome com 255 caracteres;<255 a>;201;<null>
nome com 256 caracteres;<256 a>;400;name
nome vazio;<vazio>;400;name
nome só com espaços;<espacos>;400;name
nome ausente;<null>;400;name
nome com acentos;Seguradora São João;201;<null>
```

### 3.3 `question-validation.csv` — CT-062 e CT-074 (RF-007, RF-008)

Usado por `SearchControllerTest` e `AnswerControllerTest`.

```csv
cenario;question;esperado;campoComErro
pergunta válida;Qual é o valor da franquia da apólice?;200;<null>
pergunta com 2000 caracteres;<2000 a>;200;<null>
pergunta com 2001 caracteres;<2001 a>;400;question
pergunta vazia;<vazio>;400;question
pergunta só com espaços;<espacos>;400;question
pergunta ausente;<null>;400;question
```

### 3.4 `topk-validation.csv` — CT-061 (RF-007) e CT-074 (RF-008)

```csv
cenario;topK;esperado;topKRepassado
topK ausente na busca;<null>;200;5
topK mínimo;1;200;1
topK máximo;20;200;20
topK zero;0;400;<null>
topK negativo;-1;400;<null>
topK acima do máximo;21;400;<null>
topK não numérico;abc;400;<null>
```

Para `/ask`, a mesma massa vale com `topKRepassado` = 8 na linha "ausente" (o teste troca o valor esperado pelo padrão do endpoint).

### 3.5 Chunking — CT-044 (RF-005), via `@MethodSource`

```java
static Stream<Arguments> textosParaChunking() {
    return Stream.of(
        arguments("texto de 1 caractere",               "a"),
        arguments("texto de 999 caracteres",            "a ".repeat(500).substring(0, 999)),
        arguments("texto de exatamente 1000",           "a ".repeat(500)),
        arguments("texto de 1001 caracteres",           "a ".repeat(501).substring(0, 1001)),
        arguments("texto longo com parágrafos",         paragrafos(12, 400)),
        arguments("palavra única de 2500 caracteres",   "x".repeat(2500)),
        arguments("texto com quebras repetidas",        "linha\n\n\n\nlinha\n\n\nlinha"),
        arguments("texto com acentos e R$",             "A franquia é de R$ 3.500,00 e a cobertura é de 75%.")
    );
}
```

Verificações para **cada** linha: índices 0..n-1 contínuos; nenhum `content` em branco; `content.length() <= chunkSize`; toda palavra do texto de entrada aparece em algum chunk.

### 3.6 `isolation-matrix.csv` — CT-113 (RF-012)

Consumido por `TenantIsolationIT`. Cada linha monta os chunks dos dois clientes com o vetor indicado e executa a busca como `consultante`.

```csv
cenario;consultante;vetorChunkA;vetorChunkB;vetorPergunta;resultadosEsperados
A pergunta e só B tem o vetor idêntico;A;vidros;franquia;franquia;A
B pergunta e só A tem o vetor idêntico;B;franquia;vidros;franquia;B
ambos têm o vetor idêntico, A pergunta;A;franquia;franquia;franquia;A
ambos têm o vetor idêntico, B pergunta;B;franquia;franquia;franquia;B
A sem chunks, B com o vetor idêntico;A;<null>;franquia;franquia;VAZIO
B sem chunks, A com o vetor idêntico;B;franquia;<null>;franquia;VAZIO
```

`resultadosEsperados`: `A` ou `B` = todos os resultados são daquele cliente (e há pelo menos um); `VAZIO` = lista vazia.

### 3.7 Upload — CT-052 (RF-006), via `@MethodSource`

Os arquivos vêm do `TestPdfFactory`, por isso a massa é Java:

| cenario | arquivo | nome | content-type | documentType | esperado |
|---|---|---|---|---|---|
| PDF válido CONTRACT | `comTexto(...)` | `contrato.pdf` | `application/pdf` | `CONTRACT` | 201 |
| PDF válido CLAIM | `comTexto(...)` | `sinistro.pdf` | `application/pdf` | `CLAIM` | 201 |
| PDF válido INSPECTION | `comTexto(...)` | `vistoria.pdf` | `application/pdf` | `INSPECTION` | 201 |
| sem arquivo | — | — | — | `CONTRACT` | 400 |
| arquivo vazio | `vazio()` | `contrato.pdf` | `application/pdf` | `CONTRACT` | 400 |
| arquivo .txt | bytes de texto | `contrato.txt` | `text/plain` | `CONTRACT` | 400 |
| sem documentType | `comTexto(...)` | `contrato.pdf` | `application/pdf` | — | 400 |
| documentType inválido | `comTexto(...)` | `contrato.pdf` | `application/pdf` | `POLICY` | 400 |
| documentType minúsculo | `comTexto(...)` | `contrato.pdf` | `application/pdf` | `contract` | 400 |

As três primeiras linhas usam `@EnumSource(DocumentTypeEnum.class)` num teste à parte, para que um valor novo no enum seja testado automaticamente.

No web slice, 201 significa "o `DocumentService` mockado foi chamado"; PDF corrompido e sem texto (422) ficam em `DocumentIngestionIT`, porque dependem do extrator real.

### 3.8 `gabarito-perguntas.csv` — CT-075 (RF-008, opt-in)

Consumido por `RagQualityOpenAiIT`. Os valores saem de `documents/gabarito.md` (RF-011, T-601). O arquivo está preenchido desde 2026-10-02; se um PDF mudar, atualize o gabarito e este CSV juntos.

```csv
cenario;cliente;pergunta;termosEsperados;tipoFonteEsperado;termosProibidos
franquia A;A;Qual é o valor da franquia da apólice?;<do gabarito>;CONTRACT;<valores de B>
danos A;A;Quais danos foram identificados na vistoria?;<do gabarito>;INSPECTION;<valores de B>
data sinistro A;A;Quando ocorreu o sinistro?;<do gabarito>;CLAIM;<valores de B>
percentual reparo A;A;O custo estimado do reparo representa qual percentual do valor do veículo?;<do gabarito>;INSPECTION;<valores de B>
perda total A;A;O sinistro pode ser considerado perda total segundo as regras da apólice?;<do gabarito>;CONTRACT|INSPECTION;<valores de B>
franquia B;B;Qual é o valor da franquia da apólice?;<do gabarito>;CONTRACT;<valores de A>
danos B;B;Quais danos foram identificados na vistoria?;<do gabarito>;INSPECTION;<valores de A>
data sinistro B;B;Quando ocorreu o sinistro?;<do gabarito>;CLAIM;<valores de A>
percentual reparo B;B;O custo estimado do reparo representa qual percentual do valor do veículo?;<do gabarito>;INSPECTION;<valores de A>
perda total B;B;O sinistro pode ser considerado perda total segundo as regras da apólice?;<do gabarito>;CONTRACT|INSPECTION;<valores de A>
```

- `termosEsperados` e `termosProibidos`: listas separadas por `|`; a resposta deve conter **todos** os esperados e **nenhum** proibido.
- `termosProibidos` com os valores do outro cliente é o teste de vazamento na resposta gerada (RF-008 CA-03).

### 3.9 Mapeamento de exceções — CT-090 (RF-010), via `@MethodSource`

```java
static Stream<Arguments> excecoes() {
    return Stream.of(
        arguments(new ResourceNotFoundException("Cliente 999 não encontrado"), 404, "Recurso não encontrado"),
        arguments(new InvalidDocumentException("O documento não contém texto extraível."), 422, "Documento não processável"),
        arguments(new AiProviderException("OpenAI indisponível"), 503, "Serviço de IA indisponível"),
        arguments(new MaxUploadSizeExceededException(10_485_760), 413, "Arquivo muito grande"),
        arguments(new IllegalStateException("erro inesperado"), 500, "Erro interno")
    );
}
```

O teste usa um controller de teste (`@RestController` só no escopo de teste) que lança a exceção recebida e verifica `status`, `title`, `Content-Type: application/problem+json` e que o corpo não contém o nome da classe da exceção nem `at br.com.`.

### 3.10 `document-type-check.csv` — CT-014 (RF-002)

```csv
cenario;documentType;aceito
contrato;CONTRACT;true
sinistro;CLAIM;true
vistoria;INSPECTION;true
valor fora da lista;POLICY;false
minúsculo;contract;false
vazio;<vazio>;false
```

Executado em `SchemaMigrationIT` com `INSERT` direto, para provar o `CHECK` do banco independentemente do enum Java.

### 3.11 Log por requisição — CT-139, CT-142 e CT-143 (RF-013, ADR-015)

Criada em 2026-10-05, ainda sem implementação (card HU-016 Aguardando). Os valores esperados seguem as DP do RF-013 decididas em 2026-10-06: `clientId` do caminho, também nas chamadas do administrador, e `-` nas rotas sem cliente (DP-05, RN-09), `endpoint` sem query string (DP-06), `nivel` pelo status e rotas de apoio sem linha (DP-07, RN-08, FA-04).

**`request-log-matrix.csv` — CT-143**, consumido por `RequestLoggingWebTest` (web slice). Na massa, `{A}` = 1 e `{B}` = 2, como na `security-matrix.csv`; os services são mockados como no `WebSliceTest` (o 404 vem do `ClientService` mockado lançando `ResourceNotFoundException`), e as linhas `POST` mandam um corpo válido. `naoContem` é um valor que não pode aparecer no texto capturado (`<null>` = sem verificação extra).

```csv
cenario;chave;metodo;rota;status;endpoint;clientId;nivel;naoContem
dono consulta a si mesmo;CHAVE_A;GET;/clients/1;200;/clients/1;1;INFO;<null>
admin consulta cliente A;CHAVE_ADMIN;GET;/clients/1;200;/clients/1;1;INFO;<null>
admin consulta cliente inexistente;CHAVE_ADMIN;GET;/clients/999;404;/clients/999;999;WARN;<null>
admin cria cliente;CHAVE_ADMIN;POST;/clients;201;/clients;-;INFO;<null>
dono busca;CHAVE_A;POST;/clients/1/search;200;/clients/1/search;1;INFO;<null>
dono pergunta;CHAVE_A;POST;/clients/1/ask;200;/clients/1/ask;1;INFO;<null>
sem chave consulta cliente;<null>;GET;/clients/1;401;/clients/1;1;WARN;<null>
chave inválida na busca;CHAVE_INVALIDA;POST;/clients/1/search;401;/clients/1/search;1;WARN;CHAVE_INVALIDA
sem chave cria cliente;<null>;POST;/clients;401;/clients;-;WARN;<null>
cliente A consulta B;CHAVE_A;GET;/clients/2;403;/clients/2;2;WARN;<null>
cliente A na busca de B;CHAVE_A;POST;/clients/2/search;403;/clients/2/search;2;WARN;<null>
admin na busca de A;CHAVE_ADMIN;POST;/clients/1/search;403;/clients/1/search;1;WARN;CHAVE_ADMIN
cliente A cria cliente;CHAVE_A;POST;/clients;403;/clients;-;WARN;CHAVE_A
rota desconhecida com chave;CHAVE_A;GET;/actuator/env;403;/actuator/env;-;WARN;<null>
query string fora do endpoint;CHAVE_A;GET;/clients/1?token=segredo-na-query;200;/clients/1;1;INFO;segredo-na-query
```

`CHAVE_A`, `CHAVE_B`, `CHAVE_ADMIN` e `CHAVE_INVALIDA` são resolvidas para os valores das constantes do teste, tanto na coluna `chave` quanto em `naoContem`. Regra de manutenção: endpoint novo entra com pelo menos dono (2xx) e sem chave (401).

**`@MethodSource` do `RequestLoggingFilterTest`** (unitário; mais de 5 linhas, mas valores curtos que só esse teste usa, por isso sem arquivo):

- CT-139 — `rota;endpointEsperado;clientIdEsperado`: `/clients/1;/clients/1;1` · `/clients/1/search;/clients/1/search;1` · `/clients/42/ask;/clients/42/ask;42` · `/clients;/clients;-` · `/clients/abc/search;/clients/abc/search;-` · `/actuator/env;/actuator/env;-` · `/clients/1?token=segredo-na-query;/clients/1;1` (no `MockHttpServletRequest`, a query vai em `setQueryString`, e o teste confere que `segredo-na-query` não aparece).
- CT-142 — `status;nivelEsperado`: `200;INFO` · `201;INFO` · `302;INFO` · `400;WARN` · `401;WARN` · `403;WARN` · `404;WARN` · `413;WARN` · `500;ERROR` · `503;ERROR`; e `rotaDeApoio`: `/swagger-ui.html` · `/swagger-ui/index.html` · `/v3/api-docs` → nenhuma linha do `RequestLoggingFilter`.
