# RAG Multi-Tenant com PostgreSQL + pgvector

## 1. Objetivo

Construir uma aplicação de estudo que implemente um fluxo de **RAG
(Retrieval-Augmented Generation)** utilizando documentos de diferentes
clientes.

Cada cliente poderá possuir diferentes tipos de documentos, por exemplo:

-   Contrato de seguro
-   Aviso de sinistro
-   Relatório de vistoria

Os documentos serão processados, divididos em chunks e transformados em
embeddings. Os embeddings serão armazenados no PostgreSQL utilizando
**pgvector**.

O sistema deverá garantir que uma consulta de um cliente recupere
somente informações pertencentes àquele cliente.

### Exemplo

``` text
Cliente A
├── Contrato
├── Sinistro
└── Vistoria

Cliente B
├── Contrato
├── Sinistro
└── Vistoria
```

Uma pergunta feita pelo Cliente A não poderá recuperar chunks
pertencentes ao Cliente B.

------------------------------------------------------------------------

## 2. Tecnologias

### Backend

-   Java 21
-   Spring Boot
-   Maven
-   Spring Web
-   Spring Data JPA

### IA / RAG

-   LangChain4j (versão Java do LangChain)
-   OpenAI: `text-embedding-3-small` (embeddings, 768 dimensões) e
    `gpt-4o-mini` (respostas)
-   `PgVectorEmbeddingStore` do LangChain4j para gravar e buscar os
    chunks no pgvector
-   Busca por similaridade vetorial

### Banco de dados

-   PostgreSQL
-   pgvector

### Infraestrutura

-   Docker
-   Docker Compose

### Testes

-   JUnit
-   Mockito

> O projeto começa com foco em pgvector e RAG. Outras tecnologias de IA
> somente serão adicionadas quando forem necessárias para uma etapa do
> projeto.

------------------------------------------------------------------------

## 3. Estrutura do projeto

``` text
rag-pgvector/
│
├── src/
│   ├── main/
│   │   ├── java/
│   │   │   └── br/com/ragpgvector/
│   │   │       │
│   │   │       ├── RagPgVectorApplication.java
│   │   │       │
│   │   │       ├── controller/
│   │   │       │   ├── DocumentController.java
│   │   │       │   └── SearchController.java
│   │   │       │
│   │   │       ├── service/
│   │   │       │   ├── DocumentService.java
│   │   │       │   ├── EmbeddingService.java
│   │   │       │   └── SearchService.java
│   │   │       │
│   │   │       ├── repository/
│   │   │       │   ├── DocumentRepository.java
│   │   │       │   └── ChunkRepository.java
│   │   │       │
│   │   │       ├── entity/
│   │   │       │   ├── ClientEntity.java
│   │   │       │   └── DocumentEntity.java
│   │   │       │
│   │   │       ├── enums/
│   │   │       │   └── DocumentTypeEnum.java
│   │   │       │
│   │   │       ├── dto/
│   │   │       │   ├── DocumentResponseDTO.java
│   │   │       │   ├── SearchRequestDTO.java
│   │   │       │   └── SearchResponseDTO.java
│   │   │       │
│   │   │       ├── exception/
│   │   │       │   ├── ResourceNotFoundException.java
│   │   │       │   └── GlobalExceptionHandler.java
│   │   │       │
│   │   │       └── config/
│   │   │           ├── DatabaseConfig.java
│   │   │           └── AiConfig.java
│   │   │
│   │   └── resources/
│   │       ├── application.yml
│   │       └── db/
│   │           └── migration/
│   │               └── V1__create_tables.sql
│   │
│   └── test/
│       └── java/
│
├── documents/
│   ├── cliente-a/
│   │   ├── contrato.pdf
│   │   ├── sinistro.pdf
│   │   └── vistoria.pdf
│   │
│   └── cliente-b/
│       ├── contrato.pdf
│       ├── sinistro.pdf
│       └── vistoria.pdf
│
├── docker/
│   └── init.sql
│
├── docker-compose.yml
├── pom.xml
└── README.md
```

------------------------------------------------------------------------

## 4. Comando principal

### Subir PostgreSQL + pgvector

``` bash
docker compose up -d
```

### Executar a aplicação

A aplicação e o `docker compose` leem as configurações do arquivo `.env` na raiz do projeto (fora do git). Crie-o a partir do modelo e preencha `OPENAI_API_KEY` e `ADMIN_API_KEY`:

``` bash
cp .env.example .env                     # PowerShell: Copy-Item .env.example .env
```

Variáveis de ambiente do sistema também funcionam e têm prioridade sobre o `.env`.

Detalhes em [docs/guias/Como executar.md](docs/guias/Como%20executar.md).

``` bash
./mvnw spring-boot:run
```

No Windows:

``` bash
mvnw.cmd spring-boot:run
```

### Executar testes

``` bash
./mvnw test
```

Precisa do Docker no ar; não precisa de `OPENAI_API_KEY`. Detalhes em
[docs/guias/Como testar.md](docs/guias/Como%20testar.md).

### Gerar o projeto

``` bash
./mvnw clean package
```

------------------------------------------------------------------------

## 5. Estrutura do banco

A modelagem inicial terá três entidades principais:

``` text
CLIENT
   │
   │ 1:N
   ▼
DOCUMENT
   │
   │ 1:N
   ▼
DOCUMENT_CHUNK
```

### CLIENT

Representa o cliente/tenant.

``` sql
CREATE TABLE client (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL
);
```

### DOCUMENT

Representa o documento original.

``` sql
CREATE TABLE document (
    id BIGSERIAL PRIMARY KEY,
    client_id BIGINT NOT NULL,
    file_name VARCHAR(255) NOT NULL,
    document_type VARCHAR(100) NOT NULL,
    created_at TIMESTAMP NOT NULL,

    CONSTRAINT fk_document_client
        FOREIGN KEY (client_id)
        REFERENCES client(id)
);
```

Exemplos de `document_type`:

``` text
CONTRACT
CLAIM
INSPECTION
```

No código, esses valores são representados pelo enum `DocumentTypeEnum`
(pacote `enums`).

### DOCUMENT_CHUNK

Representa cada trecho do documento e seu embedding. É a tabela usada
pelo `PgVectorEmbeddingStore` do LangChain4j: as colunas `embedding_id`,
`embedding` e `text` são exigidas pela biblioteca, e as demais guardam os
metadados de cada chunk, uma coluna por chave (modo `COLUMN_PER_KEY`).

``` sql
CREATE TABLE document_chunk (
    embedding_id UUID PRIMARY KEY,
    embedding VECTOR(768) NOT NULL,
    text TEXT NOT NULL,
    client_id BIGINT NOT NULL,
    document_id BIGINT NOT NULL,
    chunk_index INTEGER NOT NULL,
    document_type VARCHAR(100) NOT NULL,
    file_name VARCHAR(255) NOT NULL,

    CONSTRAINT fk_chunk_document_client
        FOREIGN KEY (document_id, client_id)
        REFERENCES document(id, client_id)
);
```

> A dimensão `768` corresponde ao `text-embedding-3-small` da OpenAI
> configurado com `dimensions = 768`. Trocar de modelo exige ajustar a
> dimensão e reprocessar os documentos.
>
> O `client_id` fica também no chunk para o filtro por cliente ser feito
> direto nesta tabela. A chave estrangeira composta garante que ele é
> sempre igual ao `client_id` do documento. O DDL completo está em
> `docs/arquitetura/04 Modelo de dados.md`.

------------------------------------------------------------------------

## 6. Índice vetorial

Para busca por similaridade utilizando distância de cosseno:

``` sql
CREATE INDEX document_chunk_embedding_idx
ON document_chunk
USING hnsw (embedding vector_cosine_ops);
```

A busca deverá considerar também o cliente.

Com LangChain4j, a busca usa um filtro de metadado:

``` java
Filter filtro = metadataKey("client_id").isEqualTo(clientId);

EmbeddingSearchRequest request = EmbeddingSearchRequest.builder()
        .queryEmbedding(embeddingDaPergunta)
        .maxResults(5)
        .filter(filtro)
        .build();
```

Que o `PgVectorEmbeddingStore` transforma, conceitualmente, em:

``` sql
SELECT
    embedding_id,
    text,
    (2 - (embedding <=> :queryEmbedding)) / 2 AS score
FROM document_chunk
WHERE client_id = :clientId
ORDER BY embedding <=> :queryEmbedding
LIMIT 5;
```

O `clientId` é fundamental para impedir que a busca recupere informações
de outro cliente. O filtro é sempre aplicado no SQL, nunca depois da
busca.

------------------------------------------------------------------------

## 7. Classes principais

Convenção de nomes: entidades JPA terminam em `Entity` (`ClientEntity`)
e DTOs terminam em `DTO` (`SearchRequestDTO`, `DocumentResponseDTO`).
Enums ficam no pacote `enums` e terminam em `Enum` (`DocumentTypeEnum`).

### ClientEntity

Representa o cliente/tenant.

Responsabilidades:

-   Identificar o cliente
-   Relacionar documentos ao cliente

------------------------------------------------------------------------

### DocumentEntity

Representa o documento original.

Responsabilidades:

-   Nome do arquivo
-   Tipo do documento
-   Cliente proprietário
-   Data de criação

------------------------------------------------------------------------

### Chunk (TextSegment do LangChain4j)

Representa uma parte do documento. Não é uma entidade JPA: cada chunk é
um `TextSegment` do LangChain4j, gravado pelo `PgVectorEmbeddingStore`.

Responsabilidades:

-   Conteúdo do chunk (`text`)
-   Ordem do chunk (`chunk_index`)
-   Embedding
-   Documento e cliente de origem (`document_id`, `client_id`)

------------------------------------------------------------------------

### DocumentController

Responsável pelos endpoints relacionados à ingestão.

Exemplo:

``` text
POST /clients/{clientId}/documents
```

------------------------------------------------------------------------

### DocumentService

Responsável pelo fluxo de ingestão:

``` text
Documento
    ↓
Extração do texto
    ↓
Divisão em chunks
    ↓
Geração dos embeddings
    ↓
Persistência
```

------------------------------------------------------------------------

### EmbeddingService

Responsável por transformar texto em vetor.

``` text
Texto
  ↓
Modelo de embeddings
  ↓
Vector
```

------------------------------------------------------------------------

### SearchController

Responsável por receber perguntas.

Exemplo:

``` text
POST /clients/{clientId}/search
```

------------------------------------------------------------------------

### SearchService

Responsável por:

1.  Receber a pergunta
2.  Gerar o embedding da pergunta
3.  Aplicar o filtro do cliente
4.  Executar a busca vetorial
5.  Retornar os chunks mais relevantes

Fluxo:

``` text
Pergunta
   ↓
Embedding
   ↓
Filtro clientId
   ↓
pgvector
   ↓
Top-K chunks
```

------------------------------------------------------------------------

### DocumentRepository

Responsável pelas operações relacionadas aos documentos.

------------------------------------------------------------------------

### ChunkRepository

Responsável por gravar os chunks e fazer a consulta de similaridade
vetorial, usando o `PgVectorEmbeddingStore` do LangChain4j. Toda busca
exige o `clientId` e aplica o filtro `client_id`.

------------------------------------------------------------------------

## 8. Fluxo de ingestão

``` text
PDF
 ↓
Extração do texto
 ↓
Chunking
 ↓
Embedding
 ↓
document_chunk
 ↓
PostgreSQL + pgvector
```

Cada chunk mantém a relação:

``` text
Client
  ↓
Document
  ↓
Chunk (TextSegment)
  ↓
Embedding
```

------------------------------------------------------------------------

## 9. Fluxo de consulta

``` text
Cliente A
   ↓
Pergunta
   ↓
SearchController
   ↓
SearchService
   ↓
Embedding da pergunta
   ↓
Filtro client_id = A
   ↓
pgvector
   ↓
Top-K chunks
   ↓
Resposta
```

O ponto principal da arquitetura é:

``` text
Pergunta do Cliente A
        ↓
somente dados do Cliente A
        ↓
busca vetorial
        ↓
chunks relevantes
```

------------------------------------------------------------------------

## 10. Exemplo de perguntas

Com os documentos de contrato, sinistro e vistoria, o sistema poderá
responder perguntas como:

``` text
Qual é o valor da franquia da apólice?

Quais danos foram identificados na vistoria?

Quando ocorreu o sinistro?

O custo estimado do reparo representa qual percentual
do valor do veículo?

O sinistro pode ser considerado perda total segundo
as regras da apólice?
```

A última pergunta é especialmente interessante porque exige informações
de documentos diferentes:

``` text
Contrato
→ percentual necessário para perda total

Vistoria
→ custo do reparo
→ valor do veículo

Sistema
→ compara as informações
```

------------------------------------------------------------------------

## 11. Objetivos de aprendizado

Ao finalizar o projeto, o objetivo é compreender na prática:

-   Embeddings
-   Chunking
-   Armazenamento vetorial
-   pgvector
-   Busca por similaridade
-   Distância de cosseno
-   HNSW
-   Metadados
-   Filtros por cliente
-   RAG
-   Arquitetura multi-tenant
-   Integração de IA com uma aplicação Java
-   Testes de isolamento entre clientes

------------------------------------------------------------------------

## 12. Regra fundamental de segurança

O sistema nunca deve confiar somente na similaridade do embedding para
separar clientes.

A busca deve sempre considerar o contexto do cliente:

``` text
similaridade semântica
        +
filtro de tenant/client
        +
controle de acesso
```

O objetivo é garantir que um documento do Cliente B não seja retornado
durante uma consulta autorizada somente para o Cliente A.

------------------------------------------------------------------------

## 13. Documentação do projeto

| Pasta | Conteúdo |
|---|---|
| [`docs/requirements/`](docs/requirements/) | Requisitos funcionais RF-001 a RF-012, cada um com a seção "Cenários de teste (QA)" |
| [`docs/arquitetura/`](docs/arquitetura/README.md) | Arquitetura, componentes, modelo de dados, API e ADRs |
| [`docs/QA/`](docs/QA/README.md) | Estratégia de testes, testes unitários e de integração, DDT e catálogo de cenários `CT-xxx` |
| [`docs/backlog/`](docs/backlog/Backlog.md) | Backlog: funcionalidades, prioridades, entregas e tarefas `T-xxx` por etapa |
| [`docs/guias/`](docs/guias/README.md) | Guias de execução, testes e uso da API |
| [`docs/revisao/`](docs/revisao/) | Revisões de código por requisito |

Ao implementar um requisito, use os três: o RF diz **o que** fazer, a
arquitetura diz **como**, e os cenários de QA dizem **como provar** que
está pronto.

