-- RF-002: esquema inicial (docs/arquitetura/04 Modelo de dados.md, seção 3)

CREATE EXTENSION IF NOT EXISTS vector;

CREATE TABLE client (
    id            BIGSERIAL    PRIMARY KEY,
    name          VARCHAR(255) NOT NULL,
    api_key_hash  VARCHAR(64)  NOT NULL,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uk_client_api_key_hash UNIQUE (api_key_hash)
);

CREATE TABLE document (
    id             BIGSERIAL    PRIMARY KEY,
    client_id      BIGINT       NOT NULL,
    file_name      VARCHAR(255) NOT NULL,
    document_type  VARCHAR(100) NOT NULL,
    created_at     TIMESTAMPTZ  NOT NULL,
    CONSTRAINT fk_document_client
        FOREIGN KEY (client_id) REFERENCES client (id),
    CONSTRAINT ck_document_type
        CHECK (document_type IN ('CONTRACT', 'CLAIM', 'INSPECTION')),
    -- Alvo da FK composta de document_chunk
    CONSTRAINT uk_document_id_client UNIQUE (id, client_id)
);

CREATE INDEX document_client_id_idx ON document (client_id);

-- Tabela do PgVectorEmbeddingStore (LangChain4j), modo COLUMN_PER_KEY.
-- As colunas de metadados precisam bater com as columnDefinitions do AiConfig.
CREATE TABLE document_chunk (
    embedding_id   UUID         PRIMARY KEY,
    embedding      VECTOR(768)  NOT NULL,
    text           TEXT         NOT NULL,
    client_id      BIGINT       NOT NULL,
    document_id    BIGINT       NOT NULL,
    chunk_index    INTEGER      NOT NULL,
    document_type  VARCHAR(100) NOT NULL,
    file_name      VARCHAR(255) NOT NULL,
    CONSTRAINT fk_chunk_document_client
        FOREIGN KEY (document_id, client_id)
        REFERENCES document (id, client_id) ON DELETE CASCADE,
    CONSTRAINT uk_chunk_document_index UNIQUE (document_id, chunk_index)
);

CREATE INDEX document_chunk_client_id_idx ON document_chunk (client_id);

CREATE INDEX document_chunk_embedding_idx
    ON document_chunk
    USING hnsw (embedding vector_cosine_ops);
