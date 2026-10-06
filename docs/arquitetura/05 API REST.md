# 05 — API REST

## 1. Convenções

- Base: `http://localhost:8080`.
- JSON em UTF-8; campos em `camelCase`; datas em ISO-8601 UTC (`2026-10-01T13:45:00Z`).
- Autenticação: cabeçalho `X-API-Key` em todas as rotas (ADR-008).
- Erros: `application/problem+json` (ADR-010).
- `{clientId}` é sempre o número do cliente; o cliente dono de qualquer dado vem do caminho, nunca do corpo.
- Proposto, ainda não implementado (ADR-015, em revisão; RF-013 DP-03): toda resposta da API traria o cabeçalho `X-Trace-Id` com o trace ID gerado pela aplicação, o mesmo das linhas de log da requisição. Status, corpo e os demais cabeçalhos não mudam.

## 1.1 Como chamar a API

| Forma | Onde | Observação |
|---|---|---|
| Swagger UI | `http://localhost:8080/swagger-ui.html` | Clicar em **Authorize**, informar a chave (`ADMIN_API_KEY` ou a do cliente) e usar **Try it out** (ADR-013) |
| Especificação OpenAPI | `http://localhost:8080/v3/api-docs` | Importável no Postman ou Insomnia |
| curl / arquivo `.http` | [Uso da API](../guias/Uso%20da%20API.md); `http/requests.http` é planejado (T-602, RF-011) | Exemplos abaixo e no guia; o arquivo `.http` servirá para scripts de carga dos documentos |

Roteiro no Swagger UI:

1. **Authorize** com a chave de administrador → `POST /clients` → copiar `apiKey` da resposta.
2. **Logout** e **Authorize** de novo com a chave do cliente.
3. `POST /clients/{clientId}/documents` com o PDF e o tipo.
4. `POST /clients/{clientId}/search` ou `/ask` com a pergunta.

A documentação é pública; as operações continuam exigindo a chave.

## 2. Endpoints

| Método | Caminho | Quem pode | Sucesso | RF |
|---|---|---|---|---|
| POST | `/clients` | ADMIN | 201 | RF-003 |
| GET | `/clients/{clientId}` | ADMIN ou o próprio cliente | 200 | RF-003 |
| POST | `/clients/{clientId}/documents` | O próprio cliente | 201 | RF-006 |
| POST | `/clients/{clientId}/search` | O próprio cliente | 200 | RF-007 |
| POST | `/clients/{clientId}/ask` | O próprio cliente | 200 | RF-008 |

O administrador **não** envia documentos nem consulta em nome de clientes: as rotas de dados exigem a chave do próprio cliente. Isso mantém a regra de isolamento sem exceções.

### 2.1 POST /clients

Requisição:

```json
{ "name": "Cliente A" }
```

| Campo | Tipo | Regra |
|---|---|---|
| `name` | string | obrigatório, não vazio, até 255 caracteres |

Resposta `201 Created`, cabeçalho `Location: /clients/1`:

```json
{
  "id": 1,
  "name": "Cliente A",
  "apiKey": "q7Zc3...k9"
}
```

`apiKey` só é mostrada aqui. Perdeu, cadastre de novo (rotação de chave está fora do escopo).

### 2.2 GET /clients/{clientId}

Resposta `200 OK`:

```json
{ "id": 1, "name": "Cliente A", "createdAt": "2026-10-01T13:45:00Z" }
```

### 2.3 POST /clients/{clientId}/documents

`Content-Type: multipart/form-data`

| Parte | Tipo | Regra |
|---|---|---|
| `file` | arquivo | obrigatório, não vazio, `application/pdf` ou extensão `.pdf`, até 5 MB (RF-006 DP-02, revisto em 2026-10-05; antes 10 MB) |
| `documentType` | texto | obrigatório: `CONTRACT`, `CLAIM` ou `INSPECTION` |

Exemplo:

```bash
curl -X POST http://localhost:8080/clients/1/documents \
  -H "X-API-Key: $CLIENTE_A_KEY" \
  -F "file=@documents/cliente-a/contrato.pdf" \
  -F "documentType=CONTRACT"
```

Resposta `201 Created`, `Location: /clients/1/documents/10`:

```json
{
  "id": 10,
  "clientId": 1,
  "fileName": "contrato.pdf",
  "documentType": "CONTRACT",
  "createdAt": "2026-10-01T13:50:12Z",
  "totalChunks": 14
}
```

### 2.4 POST /clients/{clientId}/search

```json
{ "question": "Qual é o valor da franquia da apólice?", "topK": 5 }
```

| Campo | Tipo | Regra |
|---|---|---|
| `question` | string | obrigatório, não vazio, até 2000 caracteres |
| `topK` | inteiro | opcional; padrão 5; entre 1 e 20 |

Resposta `200 OK` (lista vazia se o cliente não tiver documentos):

```json
{
  "clientId": 1,
  "question": "Qual é o valor da franquia da apólice?",
  "results": [
    {
      "chunkId": "6f1c2a9e-1b7d-4d0e-9a51-3c0f2b8e7d41",
      "documentId": 10,
      "fileName": "contrato.pdf",
      "documentType": "CONTRACT",
      "chunkIndex": 3,
      "content": "A franquia aplicável a sinistros de colisão é de R$ 3.500,00...",
      "score": 0.9083
    }
  ]
}
```

Ordenado por `score` decrescente. `score` vem do `PgVectorEmbeddingStore`: `(2 - distância de cosseno) / 2`, de 0 a 1, em que 1 é idêntico (04, seção 5.2). `chunkId` é o `embedding_id` (UUID) do chunk.

### 2.5 POST /clients/{clientId}/ask

```json
{ "question": "O sinistro pode ser considerado perda total segundo as regras da apólice?", "topK": 8 }
```

Mesmas regras de `SearchRequestDTO`; padrão de `topK` aqui é 8 (perguntas que cruzam documentos precisam de mais contexto — RF-008 DP-04).

Resposta `200 OK`:

```json
{
  "answer": "Sim. A apólice considera perda total quando o reparo passa de 75% do valor do veículo [1]. A vistoria estima o reparo em R$ 68.000,00 para um veículo avaliado em R$ 82.000,00, ou 83% [2][3].",
  "sources": [
    { "ref": 1, "documentId": 10, "fileName": "contrato.pdf", "documentType": "CONTRACT", "chunkIndex": 7 },
    { "ref": 2, "documentId": 12, "fileName": "vistoria.pdf", "documentType": "INSPECTION", "chunkIndex": 2 },
    { "ref": 3, "documentId": 12, "fileName": "vistoria.pdf", "documentType": "INSPECTION", "chunkIndex": 4 }
  ]
}
```

Sem documentos: `200 OK` com `answer` = "Não há documentos deste cliente para responder a esta pergunta." e `sources` vazio.

## 3. Erros

Formato (RFC 9457):

```json
{
  "type": "about:blank",
  "title": "Requisição inválida",
  "status": 400,
  "detail": "Um ou mais campos são inválidos.",
  "instance": "/clients/1/search",
  "errors": [ { "field": "question", "message": "não deve estar em branco" } ]
}
```

| HTTP | Quando | `title` |
|---|---|---|
| 400 | Campo obrigatório ausente, formato inválido, `documentType` desconhecido, arquivo vazio ou não-PDF | Requisição inválida |
| 401 | Sem `X-API-Key` ou chave desconhecida | Não autenticado |
| 403 | Chave válida acessando outro cliente, ou cliente chamando rota de ADMIN | Acesso negado |
| 404 | `GET /clients/{id}` por ADMIN para id inexistente | Recurso não encontrado |
| 413 | Arquivo acima de 5 MB | Arquivo muito grande |
| 422 | PDF ilegível ou sem texto extraível | Documento não processável |
| 503 | OpenAI indisponível, chave inválida ou limite de uso atingido | Serviço de IA indisponível |
| 500 | Erro inesperado (detalhe só no log) | Erro interno |

Origem dos 400 do upload: arquivo vazio, sem nome, com nome acima de 255 caracteres ou que não é PDF → `InvalidFileException`, lançada pelo `DocumentFileValidator` e tratada explicitamente no `GlobalExceptionHandler` (ADR-014); `documentType` ausente ou desconhecido → erros do próprio Spring MVC. O código não usa `ResponseStatusException`: todo erro de negócio ou de entrada é uma exceção própria do pacote `exception` com handler no `GlobalExceptionHandler`. Status, `title` e `detail` continuam os mesmos.

Por que 403 e não 404 para "cliente de outro tenant": o cliente nunca descobre se outro `clientId` existe (RF-009 RN-03). Para um cliente, qualquer `clientId` diferente do seu dá 403, exista ou não.
