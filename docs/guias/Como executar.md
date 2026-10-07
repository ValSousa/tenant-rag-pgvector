# Como executar

Guia para subir o banco e a aplicação na sua máquina. Vale para o que já foi entregue até o RF-006 (atualizado em 2026-10-02: configuração pelo `.env` e upload de documentos).

## 1. Pré-requisitos

| Item | Versão | Para que serve |
|---|---|---|
| JDK | 21 | Compilar e rodar a aplicação. O Maven Wrapper usa o `JAVA_HOME`. |
| Docker | Docker Desktop ou Docker Engine com `docker compose` | PostgreSQL + pgvector local e os testes (Testcontainers) |
| Chave da OpenAI | `sk-...` | A aplicação só sobe com `OPENAI_API_KEY` definida |

Não é preciso instalar o Maven: use o wrapper do projeto (`./mvnw` no Linux/macOS, `mvnw.cmd` no Windows).

### Conferir o Java

O `java` do `PATH` pode ser de outra versão (por exemplo, Java 8). O que vale para o wrapper é o `JAVA_HOME`:

```bash
echo $JAVA_HOME
./mvnw -v          # deve mostrar "Java version: 21..."
```

PowerShell:

```powershell
$env:JAVA_HOME
.\mvnw.cmd -v
```

Se não for o JDK 21, aponte o `JAVA_HOME` para ele antes de continuar (exemplo; ajuste o caminho):

```powershell
$env:JAVA_HOME = "C:\Program Files\Java\jdk-21"
```

## 2. Variáveis de ambiente e `.env`

O jeito mais simples é o arquivo `.env` na raiz do projeto (ao lado do `pom.xml`), fora do git. Crie-o a partir do modelo versionado e preencha as chaves:

```bash
cp .env.example .env          # PowerShell: Copy-Item .env.example .env
```

```properties
DB_USER=rag
DB_PASSWORD=rag
OPENAI_API_KEY=sk-...
ADMIN_API_KEY=minha-chave-local
```

- A aplicação lê o `.env` por `spring.config.import=optional:file:.env[.properties]` (em `application.properties`). O caminho é relativo à pasta de execução: rode o `mvnw` dessa pasta; na IDE, o *Working directory* deve ser a pasta do projeto.
- O `docker compose` lê o mesmo `.env` automaticamente e usa `DB_USER` e `DB_PASSWORD` para criar o usuário do banco.
- Variáveis de ambiente do sistema têm prioridade sobre o `.env`.
- Os testes não precisam do `.env`: o perfil `test` define seus próprios valores.

As variáveis (no `.env` ou no ambiente):

| Variável | Obrigatória | Padrão | Uso |
|---|---|---|---|
| `OPENAI_API_KEY` | Sim | nenhum | Chave da OpenAI para os embeddings (`text-embedding-3-small`, 768 dimensões). Sem ela a aplicação não sobe. |
| `ADMIN_API_KEY` | Sim | nenhum | Chave do administrador, enviada no cabeçalho `X-API-Key` para cadastrar clientes. Sem ela (ou vazia) a aplicação não sobe. |
| `DB_URL` | Não | `jdbc:postgresql://localhost:5432/ragdb` | URL JDBC do banco |
| `DB_USER` | Sim | nenhum | Usuário do banco (também cria o usuário no `docker compose`) |
| `DB_PASSWORD` | Sim | nenhum | Senha do banco (idem) |
| `SWAGGER_ENABLED` | Não | `true` | `false` desliga o Swagger UI e o `/v3/api-docs` |
| `APP_ENVIRONMENT` | Não | `local` | Valor do campo `environment` na linha de log de cada requisição (ver seção 4) |

O `src/main/resources/application.properties` é versionado e serve de exemplo: ele **não** tem senhas nem chaves, só referências a variáveis de ambiente. Alternativa ao `.env`: crie o `src/main/resources/application-dev.properties` (fora do git, ver seção de perfis) com o usuário e a senha do `docker-compose.yml` e rode com o perfil `dev`; assim só as duas chaves precisam estar no ambiente:

```properties
spring.datasource.username=rag
spring.datasource.password=rag
```

```bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
```

Nunca grave as chaves em arquivos versionados. Use o `.env`, o `application-dev.properties` (ambos no `.gitignore`), o terminal ou a configuração de execução da IDE.

### Propriedades do upload, do chunking e da busca

Já vêm com valor em `application.properties`; só mude se precisar (por exemplo, no `application-dev.properties`):

| Propriedade | Valor | Uso |
|---|---|---|
| `spring.servlet.multipart.max-file-size` | `5MB` | Arquivo maior responde 413 ("O arquivo excede o tamanho máximo permitido de 5 MB.") |
| `spring.servlet.multipart.max-request-size` | `6MB` | Tamanho máximo da requisição multipart inteira |
| `server.tomcat.max-swallow-size` | `10MB` | Quanto do corpo recusado o Tomcat ainda lê antes de responder; precisa ficar acima do `max-request-size` para o cliente receber o 413 em vez de ter a conexão fechada |
| `app.rag.chunk-size` | `1000` | Tamanho máximo de cada chunk, em caracteres |
| `app.rag.chunk-overlap` | `200` | Caracteres repetidos entre chunks vizinhos |
| `app.rag.embedding-dimension` | `768` | Dimensão dos embeddings; tem que bater com a coluna `VECTOR(768)` da migration |
| `app.rag.default-top-k` | `5` | Quantos trechos a busca devolve quando o `topK` não é informado |
| `app.rag.answer-top-k` | `8` | Quantos trechos o `/ask` usa como contexto quando o `topK` não é informado |
| `app.rag.max-top-k` | `20` | Maior `topK` aceito na busca e no `/ask` |

## 3. Subir o banco

```bash
docker compose up -d
```

O que sobe:

- contêiner `rag-postgres`, imagem `pgvector/pgvector:pg17`;
- banco `ragdb`, usuário e senha do `.env` (`DB_USER`/`DB_PASSWORD`; `rag`/`rag` no modelo), porta `5432`. Sem o `.env`, o compose para com "defina DB_USER no .env";
- o usuário e a senha só são aplicados na primeira subida (volume vazio). Para trocá-los depois, apague o volume (`docker compose down -v`, perde os dados);
- volume `pgdata` (os dados sobrevivem a `docker compose down`);
- `docker/init.sql` cria a extensão `vector` na primeira subida.

Conferir se está pronto (coluna `STATUS` com `healthy`):

```bash
docker compose ps
```

Se a porta 5432 já estiver ocupada por outro PostgreSQL, pare o outro serviço ou use outro banco com `DB_URL`.

As tabelas não são criadas pelo Docker: o Flyway aplica `src/main/resources/db/migration/V1__create_tables.sql` (`client`, `document`, `document_chunk` com `VECTOR(768)` e índice HNSW) quando a aplicação sobe.

## 4. Subir a aplicação

Com o `.env` preenchido:

```bash
./mvnw spring-boot:run        # Windows: .\mvnw.cmd spring-boot:run
```

Sem `.env`, defina as variáveis no terminal. Linux/macOS (bash):

```bash
export DB_USER=rag
export DB_PASSWORD=rag
export OPENAI_API_KEY=sk-...
export ADMIN_API_KEY=minha-chave-local
./mvnw spring-boot:run
```

Windows (PowerShell):

```powershell
$env:DB_USER = "rag"
$env:DB_PASSWORD = "rag"
$env:OPENAI_API_KEY = "sk-..."
$env:ADMIN_API_KEY = "minha-chave-local"
.\mvnw.cmd spring-boot:run
```

A aplicação sobe em `http://localhost:8080`.

### Erro comum na subida

Sem `OPENAI_API_KEY` ou `ADMIN_API_KEY`, a subida falha com uma mensagem parecida com:

```text
Could not resolve placeholder 'OPENAI_API_KEY' in value "${OPENAI_API_KEY}"
```

Preencha as duas no `.env` (ou defina-as no terminal) e rode de novo. Com `OPENAI_API_KEY=` vazio no `.env`, a falha é na validação de `app.openai.api-key` (não pode estar em branco).

Se o erro for `password authentication failed for user "${DB_USER}"`, faltam `DB_USER`/`DB_PASSWORD`: o Spring envia o texto do placeholder como usuário. Confira o `.env` e se a aplicação está rodando da pasta do projeto.

### Log das requisições

Cada requisição à API gera uma linha no console (ADR-015), escrita pelo `RequestLoggingFilter`:

```text
2026-10-06T11:42:24.932-03:00  INFO 52492 --- [tenant-rag-pgvector] [nio-8080-exec-1] [4575e7774b654d969014e2402930f20e] b.c.r.logging.RequestLoggingFilter       : service=tenant-rag-pgvector environment=local method=GET endpoint=/clients/1 status=200 clientId=1 traceId=4575e7774b654d969014e2402930f20e duration=12ms
```

- Nível pelo status: `INFO` abaixo de 400, `WARN` para 4xx (inclusive os 401/403 da segurança) e `ERROR` para 5xx.
- `endpoint` é o caminho sem a query string; `clientId` é o número de `/clients/{clientId}` ou `-` (ex.: `POST /clients`); `duration` em milissegundos.
- O trace ID (32 caracteres hexadecimais, novo a cada requisição) aparece entre colchetes em **todas** as linhas de log daquela requisição, inclusive no `ERROR` do `GlobalExceptionHandler`, e volta para quem chamou no cabeçalho de resposta `X-Trace-Id`. Para investigar uma chamada, copie o `X-Trace-Id` da resposta e procure-o no console (Ctrl+F).
- Swagger UI e `/v3/api-docs` não geram linha.
- O log nunca traz cabeçalhos (`X-API-Key`), corpo, pergunta, nome do arquivo, query string, IP nem User-Agent. Não ligue `DEBUG`/`TRACE` de `org.springframework.web`, `org.springframework.security` ou `org.hibernate.orm.jdbc.bind` em arquivo versionado: eles registrariam a chave ou o hash dela.

## 5. Perfis

| Perfil | Onde | Uso |
|---|---|---|
| (padrão) | `src/main/resources/application.properties` | Execução local |
| `dev` | `src/main/resources/application-dev.properties` | Arquivo **local**, fora do git (está no `.gitignore`): cada pessoa cria o seu, com só o que muda em relação ao padrão, e pode guardar chaves nele. Ativar com `-Dspring-boot.run.profiles=dev` |
| `test` | `src/test/resources/application-test.properties` | Só nos testes: chaves falsas e `FakeEmbeddingModel` (ver [Como testar](Como%20testar.md)) |

## 6. Swagger UI

Com a aplicação no ar:

- Swagger UI: `http://localhost:8080/swagger-ui.html`
- Especificação OpenAPI: `http://localhost:8080/v3/api-docs`

A documentação é pública; as operações exigem a chave. Clique em **Authorize**, informe a chave (`ADMIN_API_KEY` para cadastrar clientes; a chave do cliente para consultar e enviar documentos) e use **Try it out**. Exemplos com `curl` em [Uso da API](Uso%20da%20API.md).

## 7. Gerar o pacote

```bash
./mvnw clean package
```

O `package` também roda os testes, então precisa do Docker no ar. O jar fica em `target/tenant-rag-pgvector-0.1.0.jar`:

```bash
java -jar target/tenant-rag-pgvector-0.1.0.jar
```

(com `java` 21 e as mesmas variáveis de ambiente da seção 2).

## 8. Parar

```bash
docker compose down        # para o banco e mantém os dados
docker compose down -v     # para o banco e apaga o volume pgdata (banco zerado na próxima subida)
```

## Veja também

- [Como testar](Como%20testar.md)
- [Uso da API](Uso%20da%20API.md)
- [Arquitetura 06 — Integrações e configuração](../arquitetura/06%20Integracoes%20e%20configuracao.md)
