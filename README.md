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

-   Modelo de embeddings
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
│   │   │       │   ├── Client.java
│   │   │       │   ├── Document.java
│   │   │       │   └── DocumentChunk.java
│   │   │       │
│   │   │       ├── dto/
│   │   │       │   ├── DocumentResponse.java
│   │   │       │   ├── SearchRequest.java
│   │   │       │   └── SearchResponse.java
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

### DOCUMENT_CHUNK

Representa cada trecho do documento e seu embedding.

``` sql
CREATE TABLE document_chunk (
    id BIGSERIAL PRIMARY KEY,
    document_id BIGINT NOT NULL,
    chunk_index INTEGER NOT NULL,
    content TEXT NOT NULL,
    embedding VECTOR(1536),

    CONSTRAINT fk_chunk_document
        FOREIGN KEY (document_id)
        REFERENCES document(id)
);
```

> A dimensão `1536` é um exemplo. Ela deverá corresponder à dimensão
> produzida pelo modelo de embeddings escolhido.

------------------------------------------------------------------------

## 6. Índice vetorial

Para busca por similaridade utilizando distância de cosseno:

``` sql
CREATE INDEX document_chunk_embedding_idx
ON document_chunk
USING hnsw (embedding vector_cosine_ops);
```

A busca deverá considerar também o cliente.

Conceitualmente:

``` sql
SELECT
    dc.id,
    dc.content,
    dc.embedding <=> :queryEmbedding AS distance
FROM document_chunk dc
JOIN document d
    ON d.id = dc.document_id
WHERE d.client_id = :clientId
ORDER BY dc.embedding <=> :queryEmbedding
LIMIT 5;
```

O `clientId` é fundamental para impedir que a busca recupere informações
de outro cliente.

------------------------------------------------------------------------

## 7. Classes principais

### Client

Representa o cliente/tenant.

Responsabilidades:

-   Identificar o cliente
-   Relacionar documentos ao cliente

------------------------------------------------------------------------

### Document

Representa o documento original.

Responsabilidades:

-   Nome do arquivo
-   Tipo do documento
-   Cliente proprietário
-   Data de criação

------------------------------------------------------------------------

### DocumentChunk

Representa uma parte do documento.

Responsabilidades:

-   Conteúdo do chunk
-   Ordem do chunk
-   Embedding
-   Documento de origem

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

Responsável pela busca dos chunks e pela consulta de similaridade
vetorial.

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
DocumentChunk
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