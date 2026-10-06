# Uso da API

Fluxo ponta a ponta com `curl` e Swagger UI. Atualizado em 2026-10-05 (limite de upload de 5 MB): a API tem o cadastro e a consulta de clientes (RF-003), o upload de documentos PDF (RF-005, RF-006), a busca semântica nos documentos do cliente (RF-007) e a resposta em linguagem natural com as fontes citadas (RF-008, `POST /clients/{clientId}/ask`).

Antes de começar, suba o banco e a aplicação ([Como executar](Como%20executar.md)). Nos exemplos, a aplicação foi iniciada com `ADMIN_API_KEY=minha-chave-local` e uma `OPENAI_API_KEY` válida (o upload e a busca geram embeddings na OpenAI; a resposta do `/ask` usa também o `gpt-4o-mini`).

## 1. Convenções

- Base: `http://localhost:8080`.
- Autenticação: cabeçalho `X-API-Key` com a chave do administrador (`ADMIN_API_KEY`) ou a chave de um cliente.
- Corpo e respostas em JSON (UTF-8); o upload usa `multipart/form-data`. Datas em ISO-8601 UTC.
- Erros no formato ProblemDetail (`application/problem+json`), com `title` em português (seção 9).
- Também dá para testar tudo pelo Swagger UI: `http://localhost:8080/swagger-ui.html` (botão **Authorize**).

Os exemplos usam a sintaxe do bash, rodando da pasta do projeto (ao lado do `pom.xml`). No Windows, use o Git Bash ou chame `curl.exe` no PowerShell (no PowerShell, `curl` sem `.exe` é outro comando).

## 2. Endpoints disponíveis

| Método | Caminho | Quem pode | Sucesso | RF |
|---|---|---|---|---|
| POST | `/clients` | Administrador | 201 | RF-003 |
| GET | `/clients/{clientId}` | Administrador ou o próprio cliente | 200 | RF-003 |
| POST | `/clients/{clientId}/documents` | Só o próprio cliente (o administrador recebe 403) | 201 | RF-005, RF-006 |
| POST | `/clients/{clientId}/search` | Só o próprio cliente (o administrador recebe 403) | 200 | RF-007 |
| POST | `/clients/{clientId}/ask` | Só o próprio cliente (o administrador recebe 403) | 200 | RF-008 |

Rotas fora desta lista não estão liberadas: sem chave respondem 401 e, com chave, em geral 403.

## 3. Cadastrar um cliente (administrador)

```bash
curl -i -X POST http://localhost:8080/clients \
  -H "X-API-Key: minha-chave-local" \
  -H "Content-Type: application/json" \
  -d '{"name": "Cliente A"}'
```

Resposta `201 Created`, com o cabeçalho `Location: /clients/1`:

```json
{
  "id": 1,
  "name": "Cliente A",
  "apiKey": "q7Zc3...k9"
}
```

- `name` é obrigatório, não pode ser vazio e tem no máximo 255 caracteres.
- **A `apiKey` aparece só nesta resposta.** O banco guarda apenas o hash dela. Se perder a chave, cadastre o cliente de novo (rotação de chave está fora do escopo).

Guarde a chave numa variável para os próximos passos. Cadastre também o "Cliente B" (id 2) do mesmo jeito:

```bash
CHAVE_CLIENTE_A="q7Zc3...k9"   # cole aqui a apiKey devolvida para o Cliente A
CHAVE_CLIENTE_B="m2Xp8...w4"   # e aqui a do Cliente B
```

## 4. Consultar um cliente

Pelo próprio cliente:

```bash
curl -i http://localhost:8080/clients/1 \
  -H "X-API-Key: $CHAVE_CLIENTE_A"
```

Pelo administrador:

```bash
curl -i http://localhost:8080/clients/1 \
  -H "X-API-Key: minha-chave-local"
```

Resposta `200 OK`:

```json
{ "id": 1, "name": "Cliente A", "createdAt": "2026-10-01T13:45:00Z" }
```

A resposta nunca traz a chave.

## 5. Enviar um documento (cliente)

`POST /clients/{clientId}/documents`, em `multipart/form-data`, com duas partes:

| Parte | Obrigatória | Valor |
|---|---|---|
| `file` | Sim | Arquivo PDF com texto (não escaneado), até 5 MB |
| `documentType` | Sim | `CONTRACT`, `CLAIM` ou `INSPECTION` |

O que acontece: o texto do PDF é extraído, dividido em chunks de até 1000 caracteres com sobreposição de 200 (`app.rag.chunk-size` e `app.rag.chunk-overlap` em `application.properties`), cada chunk vira um embedding na OpenAI e tudo é gravado numa única transação. O dono do documento é sempre o `clientId` do caminho.

```bash
curl -i -X POST http://localhost:8080/clients/1/documents \
  -H "X-API-Key: $CHAVE_CLIENTE_A" \
  -F "file=@documents/cliente-a/contrato.pdf;type=application/pdf" \
  -F "documentType=CONTRACT"
```

Resposta `201 Created`, com o cabeçalho `Location: /clients/1/documents/10`:

```json
{
  "id": 10,
  "clientId": 1,
  "fileName": "contrato.pdf",
  "documentType": "CONTRACT",
  "createdAt": "2026-10-02T14:05:12.345678Z",
  "totalChunks": 4
}
```

- `totalChunks` depende do tamanho do texto; o valor acima é só um exemplo.
- `fileName` é o nome original do arquivo, sem a pasta.
- Ainda não existe rota `GET` para a URL do `Location`.
- **Reenvio substitui:** mandar de novo um arquivo com o mesmo `fileName` pelo mesmo cliente apaga o documento anterior (e os chunks dele) e grava o novo, com outro `id`. O mesmo nome enviado por outro cliente não interfere.

### Enviar os documentos de exemplo

A pasta `documents/` tem três PDFs fictícios por cliente (RF-011). As respostas esperadas para as perguntas de teste estão em [`documents/gabarito.md`](../../documents/gabarito.md).

| Arquivo | `documentType` |
|---|---|
| `contrato.pdf` | `CONTRACT` |
| `sinistro.pdf` | `CLAIM` |
| `vistoria.pdf` | `INSPECTION` |

Com os dois clientes cadastrados (ids 1 e 2) e as chaves nas variáveis da seção 3:

```bash
enviar() {  # uso: enviar <clientId> <chave> <pasta>
  for par in contrato:CONTRACT sinistro:CLAIM vistoria:INSPECTION; do
    curl -s -X POST "http://localhost:8080/clients/$1/documents" \
      -H "X-API-Key: $2" \
      -F "file=@documents/$3/${par%%:*}.pdf;type=application/pdf" \
      -F "documentType=${par##*:}"
    echo
  done
}

enviar 1 "$CHAVE_CLIENTE_A" cliente-a
enviar 2 "$CHAVE_CLIENTE_B" cliente-b
```

PowerShell (um arquivo por vez; repita trocando arquivo e tipo):

```powershell
$chaveA = "q7Zc3...k9"   # apiKey do Cliente A
curl.exe -i -X POST http://localhost:8080/clients/1/documents `
  -H "X-API-Key: $chaveA" `
  -F "file=@documents/cliente-a/contrato.pdf;type=application/pdf" `
  -F "documentType=CONTRACT"
```

### Pelo Swagger UI

1. Abra `http://localhost:8080/swagger-ui.html` e clique em **Authorize**.
2. Informe a **chave do cliente** (a do administrador recebe 403 nesta rota) e confirme.
3. Em **Documentos**, abra `POST /clients/{clientId}/documents` e clique em **Try it out**.
4. Preencha `clientId` (o id do dono da chave), escolha o `documentType` e selecione o PDF em `file`.
5. Clique em **Execute**: a resposta esperada é `201` com o JSON acima.

## 6. Buscar nos documentos (cliente)

Devolve os trechos (chunks) dos documentos **do próprio cliente** mais parecidos com a pergunta, do mais parecido para o menos parecido. A busca nunca olha documentos de outro cliente: o filtro pelo cliente do caminho fica no próprio SQL.

```bash
curl -i -X POST http://localhost:8080/clients/1/search   -H "X-API-Key: $CHAVE_CLIENTE_A"   -H "Content-Type: application/json"   -d '{"question": "Qual é o valor da franquia da apólice?", "topK": 3}'
```

| Campo | Regra |
|---|---|
| `question` | obrigatório, não vazio, até 2000 caracteres |
| `topK` | opcional; padrão 5; de 1 a 20 |

Resposta `200 OK`:

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

- `score` vai de 0 a 1 (1 = idêntico à pergunta); os resultados vêm em ordem decrescente de `score`.
- Cliente sem documentos recebe `200` com `results` vazio, mesmo que outros clientes tenham documentos.
- Não há limiar mínimo: com documentos, a busca sempre devolve até `topK` trechos, mesmo pouco parecidos. Quem decide se o trecho responde à pergunta é você (ou a resposta gerada, RF-008).

### Pelo Swagger UI

1. Clique em **Authorize** e informe a **chave do cliente**.
2. Em **Busca**, abra `POST /clients/{clientId}/search` e clique em **Try it out**.
3. Preencha `clientId` (o id do dono da chave) e o corpo com `question` (e, se quiser, `topK`).
4. Clique em **Execute**: a resposta esperada é `200` com o JSON acima.

## 7. Fazer uma pergunta e receber a resposta (cliente)

`POST /clients/{clientId}/ask` responde em linguagem natural usando **só os documentos do próprio cliente**. O que acontece:

1. A pergunta passa pela mesma busca da seção 6 (filtro pelo cliente do caminho no SQL), com `topK` padrão 8: perguntas que cruzam documentos, como a de perda total, precisam de trechos do contrato e da vistoria juntos.
2. Os trechos encontrados são numerados (`[1]`, `[2]`, ...) e enviados ao modelo `gpt-4o-mini` (`app.openai.chat-model`, temperatura `app.openai.temperature=0.1`), instruído a responder só com base neles, não inventar valores, mostrar os números dos cálculos e citar os trechos como `[n]`.
3. A resposta volta com `answer` e `sources`: cada fonte tem o número `ref` citado no texto, o documento e o chunk.

Se o cliente não tem documentos, a resposta vem na hora, sem chamar o modelo. Se os documentos não têm a informação, o modelo responde que ela não foi encontrada nos documentos do cliente.

| Campo | Regra |
|---|---|
| `question` | obrigatório, não vazio, até 2000 caracteres |
| `topK` | opcional; padrão 8; de 1 a 20 |

Com os documentos de exemplo enviados (seção 5), pergunte como o Cliente A:

```bash
curl -i -X POST http://localhost:8080/clients/1/ask \
  -H "X-API-Key: $CHAVE_CLIENTE_A" \
  -H "Content-Type: application/json" \
  -d '{"question": "O sinistro pode ser considerado perda total segundo as regras da apólice?"}'
```

Resposta `200 OK` (ilustrativa: o texto exato muda de uma execução para outra, mas os valores vêm dos documentos do Cliente A):

```json
{
  "answer": "Sim. A apólice considera perda total quando o custo do reparo ultrapassa 75% do valor de referência do veículo [1]. A vistoria estima o reparo em R$ 96.000,00 para um valor de referência de R$ 120.000,00, ou seja, 96.000 / 120.000 = 80% [2], acima do limite de 75%.",
  "sources": [
    { "ref": 1, "documentId": 1, "fileName": "contrato.pdf", "documentType": "CONTRACT", "chunkIndex": 2 },
    { "ref": 2, "documentId": 3, "fileName": "vistoria.pdf", "documentType": "INSPECTION", "chunkIndex": 1 }
  ]
}
```

- `sources` traz todos os trechos enviados ao modelo, na ordem de relevância; `ref` é o número que o modelo cita em `answer`. Os ids e `chunkIndex` acima são só exemplo.
- Cliente sem documentos: `200` com `answer` = `"Não há documentos deste cliente para responder a esta pergunta."` e `sources` vazio.

### As perguntas da seção 10 do README

Faça as cinco perguntas para cada cliente e compare com o [gabarito](../../documents/gabarito.md):

```bash
perguntar() {  # uso: perguntar <clientId> <chave> "<pergunta>"
  curl -s -X POST "http://localhost:8080/clients/$1/ask" \
    -H "X-API-Key: $2" \
    -H "Content-Type: application/json" \
    -d "{\"question\": \"$3\"}"
  echo
}

for pergunta in \
  "Qual é o valor da franquia da apólice?" \
  "Quais danos foram identificados na vistoria?" \
  "Quando ocorreu o sinistro?" \
  "O custo estimado do reparo representa qual percentual do valor do veículo?" \
  "O sinistro pode ser considerado perda total segundo as regras da apólice?"; do
  perguntar 1 "$CHAVE_CLIENTE_A" "$pergunta"
  perguntar 2 "$CHAVE_CLIENTE_B" "$pergunta"
done
```

| Pergunta | Cliente A (esperado) | Cliente B (esperado) |
|---|---|---|
| Qual é o valor da franquia da apólice? | R$ 4.000,00 | R$ 5.500,00 |
| Quais danos foram identificados na vistoria? | Lateral esquerda: portas, para-lama, roda, suspensão, painel lateral, longarina e pintura | Frente: para-choque, capô, farol direito, radiador, condensador e travessa |
| Quando ocorreu o sinistro? | 18/09/2026 | 22/09/2026 |
| O custo estimado do reparo representa qual percentual do valor do veículo? | 80% (R$ 96.000,00 / R$ 120.000,00) | cerca de 53,7% (R$ 51.000,00 / R$ 95.000,00) |
| O sinistro pode ser considerado perda total segundo as regras da apólice? | Sim (80% > 75%) | Não (53,7% < 70%) |

A resposta de um cliente nunca deve trazer valores do outro (por exemplo, `R$ 5.500,00` numa resposta ao Cliente A).

Uma pergunta que os documentos não respondem:

```bash
perguntar 1 "$CHAVE_CLIENTE_A" "Qual a cor do carro do vizinho?"
```

A `answer` diz que a informação não foi encontrada nos documentos do cliente, sem inventar valores.

PowerShell:

```powershell
$chaveA = "q7Zc3...k9"   # apiKey do Cliente A
$corpo = '{"question": "Qual é o valor da franquia da apólice?"}'
Invoke-RestMethod -Method Post -Uri http://localhost:8080/clients/1/ask `
  -Headers @{ "X-API-Key" = $chaveA } -ContentType "application/json; charset=utf-8" `
  -Body ([System.Text.Encoding]::UTF8.GetBytes($corpo))
```

### Pelo Swagger UI

1. Clique em **Authorize** e informe a **chave do cliente**.
2. Em **Resposta**, abra `POST /clients/{clientId}/ask` e clique em **Try it out**.
3. Preencha `clientId` (o id do dono da chave) e o corpo com `question` (e, se quiser, `topK`).
4. Clique em **Execute**: a resposta esperada é `200` com `answer` e `sources`.

## 8. Isolamento entre clientes

Um cliente só acessa o próprio `clientId`. Consultar outro cliente, enviar documento para ele, buscar nos documentos dele ou perguntar sobre eles devolve sempre 403, exista ou não o outro cliente. Assim ninguém descobre quais clientes existem.

```bash
# 401: sem chave
curl -i http://localhost:8080/clients/1

# 403: cliente tentando cadastrar cliente
curl -i -X POST http://localhost:8080/clients \
  -H "X-API-Key: $CHAVE_CLIENTE_A" \
  -H "Content-Type: application/json" \
  -d '{"name": "Cliente C"}'

# 403: cliente A consultando o cliente 2
curl -i http://localhost:8080/clients/2 \
  -H "X-API-Key: $CHAVE_CLIENTE_A"

# 403: cliente A enviando documento para o cliente 2
curl -i -X POST http://localhost:8080/clients/2/documents \
  -H "X-API-Key: $CHAVE_CLIENTE_A" \
  -F "file=@documents/cliente-a/contrato.pdf;type=application/pdf" \
  -F "documentType=CONTRACT"

# 403: cliente A buscando nos documentos do cliente 2
curl -i -X POST http://localhost:8080/clients/2/search   -H "X-API-Key: $CHAVE_CLIENTE_A"   -H "Content-Type: application/json"   -d '{"question": "Qual é o valor da franquia?"}'

# 403: cliente A perguntando sobre os documentos do cliente 2
curl -i -X POST http://localhost:8080/clients/2/ask \
  -H "X-API-Key: $CHAVE_CLIENTE_A" \
  -H "Content-Type: application/json" \
  -d '{"question": "Qual é o valor da franquia?"}'
```

Mesmo com a chave certa, a resposta do `/ask` só usa trechos do próprio cliente: os trechos enviados ao modelo vêm da busca filtrada pelo cliente no SQL, então o modelo nunca recebe dados de outro cliente.

## 9. Erros

Exemplo (`GET /clients/999` com a chave do administrador):

```json
{
  "type": "about:blank",
  "title": "Recurso não encontrado",
  "status": 404,
  "detail": "Cliente 999 não encontrado.",
  "instance": "/clients/999"
}
```

| HTTP | Quando | `title` |
|---|---|---|
| 400 | Clientes: `name` ausente, vazio ou com mais de 255 caracteres; JSON malformado. Upload: parte `file` ou `documentType` ausente, arquivo vazio, arquivo que não é PDF, `documentType` fora de `CONTRACT`/`CLAIM`/`INSPECTION`, nome do arquivo com mais de 255 caracteres. Busca e `/ask`: `question` ausente, vazia ou com mais de 2000 caracteres; `topK` fora de 1 a 20 ou não numérico | Requisição inválida |
| 401 | Sem `X-API-Key` ou chave desconhecida | Não autenticado |
| 403 | Cliente chamando `POST /clients`; cliente acessando outro `clientId` (exista ou não); administrador enviando documento, buscando ou perguntando | Acesso negado |
| 404 | Administrador consultando um `clientId` que não existe | Recurso não encontrado |
| 413 | Arquivo acima de 5 MB | Arquivo muito grande |
| 422 | PDF ilegível (corrompido) ou sem texto extraível (em branco, imagem escaneada) | Documento não processável |
| 500 | Falha inesperada (detalhes só no log) | Erro interno |
| 503 | OpenAI indisponível ao gerar os embeddings do upload (nada é gravado) ou da pergunta (busca e `/ask`), ou ao gerar a resposta do `/ask` | Serviço de IA indisponível |

No erro 400 de validação do cadastro, da busca e do `/ask`, a lista `errors` diz o campo (na busca e no `/ask`, `question` ou `topK`):

```json
{
  "title": "Requisição inválida",
  "status": 400,
  "detail": "Um ou mais campos são inválidos.",
  "errors": [ { "field": "name", "message": "não deve estar em branco" } ]
}
```

Mensagens (`detail`) do upload:

| Situação | `detail` |
|---|---|
| Sem a parte `file` | `A parte 'file' é obrigatória.` |
| Sem `documentType` | `O parâmetro 'documentType' é obrigatório.` |
| `documentType` inválido | `O valor informado em 'documentType' é inválido.` |
| Arquivo vazio | `O arquivo enviado está vazio.` |
| Não é PDF (nem tipo `application/pdf` nem extensão `.pdf`) | `O arquivo deve ser um PDF.` |
| Acima de 5 MB | `O arquivo excede o tamanho máximo permitido de 5 MB.` |
| PDF corrompido | `Não foi possível ler o arquivo PDF.` |
| PDF sem texto | `O documento não contém texto extraível.` |

Experimente:

```bash
# 400: tipo inválido
curl -i -X POST http://localhost:8080/clients/1/documents \
  -H "X-API-Key: $CHAVE_CLIENTE_A" \
  -F "file=@documents/cliente-a/contrato.pdf;type=application/pdf" \
  -F "documentType=RECEIPT"

# 400: arquivo que não é PDF
curl -i -X POST http://localhost:8080/clients/1/documents \
  -H "X-API-Key: $CHAVE_CLIENTE_A" \
  -F "file=@pom.xml;type=application/xml" \
  -F "documentType=CONTRACT"
```

## Veja também

- [Como executar](Como%20executar.md)
- [Arquitetura 05 — API REST](../arquitetura/05%20API%20REST.md)
- [Arquitetura 07 — Segurança e isolamento](../arquitetura/07%20Seguranca%20e%20isolamento.md)
- [Gabarito dos documentos de exemplo](../../documents/gabarito.md)
