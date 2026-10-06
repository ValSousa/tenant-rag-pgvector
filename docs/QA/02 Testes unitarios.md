# 02 — Testes unitários e de camada web

Testes rápidos, sem banco e sem OpenAI. Rodam em todo `mvnw test`.

## 1. Convenções

| Item | Regra |
|---|---|
| Local | `src/test/java/br/com/rag_pgvector/<mesmo pacote da classe testada>` |
| Nome da classe | `<ClasseTestada>Test` (ex.: `DocumentServiceTest`); web slice: `<Controller>Test`, `SecurityConfigTest` |
| Nome do método | Frase em português, camelCase, começando por `deve`/`naoDeve` (ex.: `naoDeveGravarNadaQuandoEmbeddingFalha`) |
| `@DisplayName` | Sempre com o ID do cenário: `"CT-055 — Falha no embedding não grava documento"` |
| Estrutura | Arrange / Act / Assert, separados por uma linha em branco |
| Asserções | AssertJ (`assertThat`, `assertThatThrownBy`) |
| Mocks | Mockito com `@ExtendWith(MockitoExtension.class)`; no web slice, `@MockitoBean` |
| Um comportamento por teste | Vários `assert` são permitidos se descrevem o mesmo comportamento |
| Proibido | `Thread.sleep`, acesso à rede, depender de ordem de execução, `@Disabled` sem `BUG-xxx` |

## 2. Classes de apoio (`src/test/java/.../support`)

| Classe | Para que serve |
|---|---|
| `TestRagProperties` | Fábrica de `RagProperties` com os valores padrão (768, 1000, 200, 5, 8, 20) e métodos `comDimensao(int)`, `comChunk(int, int)`. Evita depender da ordem do construtor. |
| `TestPdfFactory` | Gera PDFs em memória com PDFBox: `comTexto(String...)`, `semTexto()` (página em branco), `corrompido()` (bytes aleatórios), `vazio()` (0 bytes). |
| `FakeEmbeddingModel` | Implementa `EmbeddingModel` do LangChain4j de forma determinística ([03](03%20Testes%20de%20integracao.md), seção 3.2). Também usado em testes unitários quando um mock não basta. |
| `Fixtures` | Constantes: `CLIENTE_A = 1L`, `CLIENTE_B = 2L`, `PERGUNTA_FRANQUIA`, `TEXTO_CONTRATO`, `CHAVE_A`, `CHAVE_B`, `CHAVE_ADMIN`. |

## 3. Unitários por componente

### 3.1 `EmbeddingService` (RF-004)

| Cenário | Caso | Verificação |
|---|---|---|
| CT-030 | Texto válido | Devolve `Embedding` com 768 posições |
| CT-031 | Texto `null`, `""`, `"   "` (DDT) | `IllegalArgumentException`; `verifyNoInteractions(embeddingModel)` |
| CT-032 | Modelo devolve vetor de 1536 | `AiProviderException` com "dimensão" na mensagem |
| CT-033 | Modelo lança exceção de rede, autenticação ou limite (DDT) | `AiProviderException` com a causa original encadeada |
| CT-034 | `embedDocuments` com 12 segmentos | `embedAll` chamado **uma** vez com os 12 |

```java
@ExtendWith(MockitoExtension.class)
class EmbeddingServiceTest {

    @Mock EmbeddingModel embeddingModel;
    EmbeddingService service;

    @BeforeEach
    void setUp() {
        service = new EmbeddingService(embeddingModel, TestRagProperties.padrao());
    }

    @ParameterizedTest(name = "[{index}] texto=''{0}''")
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "\t", "\n"})
    @DisplayName("CT-031 — Texto vazio é rejeitado sem chamar o modelo")
    void deveRejeitarTextoVazioSemChamarOModelo(String texto) {
        assertThatThrownBy(() -> service.embedQuery(texto))
                .isInstanceOf(IllegalArgumentException.class);

        verifyNoInteractions(embeddingModel);
    }

    @Test
    @DisplayName("CT-032 — Dimensão diferente da configurada gera AiProviderException")
    void deveFalharQuandoDimensaoNaoConfere() {
        when(embeddingModel.embed(anyString()))
                .thenReturn(Response.from(Embedding.from(new float[1536])));

        assertThatThrownBy(() -> service.embedQuery("franquia"))
                .isInstanceOf(AiProviderException.class)
                .hasMessageContaining("dimensão");
    }
}
```

### 3.2 `PdfTextExtractor` e `TextChunker` (RF-005)

| Cenário | Caso | Verificação |
|---|---|---|
| CT-040 | `TestPdfFactory.comTexto("A franquia é de R$ 3.500,00.")` | Texto extraído contém a frase |
| CT-041 | `corrompido()` e `vazio()` | `InvalidDocumentException` com "Não foi possível ler o arquivo PDF." |
| CT-042 | `semTexto()` | `InvalidDocumentException` com "O documento não contém texto extraível." |
| CT-043 | Texto de 300 caracteres | 1 chunk, índice 0, conteúdo igual ao texto normalizado |
| CT-044 | Textos de vários tamanhos (DDT, [04](04%20Testes%20orientados%20a%20dados.md) seção 3.5) | Índices 0..n-1 sem lacuna; nenhum chunk vazio; nenhum acima de `chunk-size`; toda palavra do texto aparece em algum chunk |
| CT-045 | `TestRagProperties.comChunk(500, 50)` | Nenhum chunk acima de 500 caracteres |

O teste de `TextChunker` verifica o **contrato** do wrapper, não o algoritmo interno do `DocumentSplitters.recursive` (ADR-007).

### 3.3 `ClientService` e `ApiKeyHasher` (RF-003, RF-009)

| Cenário | Caso | Verificação |
|---|---|---|
| CT-026 | Criar dois clientes | Chaves diferentes; cada uma com ao menos 43 caracteres (32 bytes em Base64 URL); `apiKeyHash` gravado = SHA-256 hex da chave devolvida; a chave em texto **não** é passada ao repository |
| CT-083 (parte unitária) | `ApiKeyHasher.hash("abc")` | `ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad` (vetor conhecido do SHA-256) |

### 3.4 `DocumentService` (RF-006)

| Cenário | Caso | Verificação |
|---|---|---|
| CT-055 (parte unitária) | `embedDocuments` lança `AiProviderException` | Exceção propagada; `verifyNoInteractions(documentWriter)` |
| CT-054 (parte unitária) | Extrator lança `InvalidDocumentException` | `verifyNoInteractions(textChunker, embeddingService, documentWriter)` |
| CT-050 (parte unitária) | Fluxo feliz | `documentWriter.save` recebe o `clientId` do parâmetro, os chunks na ordem e um embedding por chunk |

```java
@Test
@DisplayName("CT-055 — Falha no embedding não grava documento")
void naoDeveGravarNadaQuandoEmbeddingFalha() {
    when(pdfTextExtractor.extract(any())).thenReturn(TEXTO_CONTRATO);
    when(textChunker.split(TEXTO_CONTRATO)).thenReturn(List.of(new Chunk(0, TEXTO_CONTRATO)));
    when(embeddingService.embedDocuments(anyList()))
            .thenThrow(new AiProviderException("OpenAI indisponível"));

    assertThatThrownBy(() -> service.ingest(CLIENTE_A, "contrato.pdf", DocumentTypeEnum.CONTRACT, PDF_BYTES))
            .isInstanceOf(AiProviderException.class);

    verifyNoInteractions(documentWriter);
}
```

### 3.5 `ChunkRepository` com `EmbeddingStore` mockado (RF-007)

Garante que **o filtro por cliente sempre é montado**, independentemente do banco.

| Cenário | Caso | Verificação |
|---|---|---|
| CT-065 | `searchByClient(42L, embedding, 5)` | `EmbeddingSearchRequest` capturado tem filtro `IsEqualTo` com chave `client_id` e valor `42L`, `maxResults` 5 |
| CT-051 (parte unitária) | `saveAll(...)` | Cada `TextSegment` tem metadados `client_id`, `document_id`, `chunk_index`, `document_type`, `file_name` |

```java
@Test
@DisplayName("CT-065 — Toda busca envia o filtro client_id ao EmbeddingStore")
void deveSempreFiltrarPorCliente() {
    var captor = ArgumentCaptor.forClass(EmbeddingSearchRequest.class);
    when(embeddingStore.search(captor.capture())).thenReturn(new EmbeddingSearchResult<>(List.of()));

    repository.searchByClient(42L, Embedding.from(new float[768]), 5);

    var request = captor.getValue();
    assertThat(request.maxResults()).isEqualTo(5);
    assertThat(request.filter()).isInstanceOfSatisfying(IsEqualTo.class, f -> {
        assertThat(f.key()).isEqualTo("client_id");
        assertThat(f.comparisonValue()).isEqualTo(42L);
    });
}
```

### 3.6 `SearchService` (RF-007)

| Cenário | Caso | Verificação |
|---|---|---|
| CT-061 (parte unitária) | `topK` `null` → 5; 1 → 1; 20 → 20; 50 → 20 (proteção defensiva; a API já rejeita com 400) | Valor repassado ao repository |
| CT-060 (parte unitária) | Repository devolve 2 `EmbeddingMatch` | `SearchResultDTO` com `chunkId` (UUID), `documentId`, `fileName`, `documentType`, `chunkIndex`, `content`, `score` na mesma ordem |

### 3.7 `AnswerService` (RF-008)

| Cenário | Caso | Verificação |
|---|---|---|
| CT-070 | Busca sem resultados | `answer` = "Não há documentos deste cliente para responder a esta pergunta."; `sources` vazio; `verifyNoInteractions(ragAssistant)` |
| CT-071 | Busca com 3 chunks | `context` enviado ao `RagAssistant` tem linhas `[1] (contrato.pdf, CONTRACT) ...` a `[3] ...`; `sources[i].ref` = i+1 |
| CT-072 | `RagAssistant` lança exceção | `AiProviderException` |
| CT-073 | `topK` não informado | `SearchService` chamado com 8 |

### 3.8 `FakeEmbeddingModel` (apoio)

| Cenário | Caso | Verificação |
|---|---|---|
| CT-036 | Mesmo texto duas vezes | Vetores iguais, 768 posições, norma 1 |
| CT-036 | Vetor fixo registrado | Devolve exatamente o vetor registrado |

## 4. Camada web (slice, sem banco)

`@WebMvcTest` sobe só controllers, `SecurityConfig`, `GlobalExceptionHandler` e o filtro de chave; services e repositories são `@MockitoBean`. No Spring Boot 4, a anotação vem do módulo `spring-boot-webmvc-test` (já presente no `pom.xml` via `spring-boot-starter-webmvc-test`).

Base comum:

```java
@WebMvcTest
@Import({SecurityConfig.class, GlobalExceptionHandler.class, OpenApiConfig.class})
@ActiveProfiles("test")
abstract class WebSliceTest {

    @Autowired protected MockMvc mvc;
    @MockitoBean protected ClientRepository clientRepository;   // usado pelo filtro de chave
    @MockitoBean protected ClientService clientService;
    @MockitoBean protected DocumentService documentService;
    @MockitoBean protected SearchService searchService;
    @MockitoBean protected AnswerService answerService;

    @BeforeEach
    void chavesConhecidas() {
        when(clientRepository.findByApiKeyHash(ApiKeyHasher.hash(CHAVE_A))).thenReturn(Optional.of(cliente(CLIENTE_A)));
        when(clientRepository.findByApiKeyHash(ApiKeyHasher.hash(CHAVE_B))).thenReturn(Optional.of(cliente(CLIENTE_B)));
    }
}
```

| Classe | Cenários |
|---|---|
| `ClientControllerTest` | CT-020 (201 + `Location` + `apiKey`), CT-021 (validação de nome, DDT), CT-024 (404 para admin), CT-027 (cliente em `POST /clients` → 403) |
| `DocumentControllerTest` | CT-052 (validação do upload, DDT), CT-058 (parte `clientId` no corpo é ignorada) |
| `SearchControllerTest` | CT-061 e CT-062 (validação de `topK` e `question`, DDT), CT-064 (503) |
| `AnswerControllerTest` | CT-074 (validação, DDT), CT-072 (503) |
| `SecurityConfigTest` | CT-025, CT-080 (matriz DDT), CT-081, CT-082, CT-084, CT-085 |
| `GlobalExceptionHandlerTest` | CT-090 a CT-093 |

Exemplo da matriz de segurança (dados em [04](04%20Testes%20orientados%20a%20dados.md), seção 3.1):

```java
class SecurityConfigTest extends WebSliceTest {

    @ParameterizedTest(name = "[{index}] {0}")
    @CsvFileSource(resources = "/ddt/security-matrix.csv", numLinesToSkip = 1, delimiter = ';')
    @DisplayName("CT-080 — Matriz de acesso por chave e rota")
    void deveAplicarMatrizDeAcesso(String cenario, String chave, String metodo, String rota, String esperado) {
        var request = request(HttpMethod.valueOf(metodo), rota)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"question\":\"teste\",\"name\":\"teste\"}");
        Chaves.resolver(chave).ifPresent(k -> request.header("X-API-Key", k));

        var status = mvc.perform(request).andReturn().getResponse().getStatus();

        switch (esperado) {
            case "PERMITIDO" -> assertThat(status).isNotIn(401, 403);
            default -> assertThat(status).isEqualTo(Integer.parseInt(esperado));
        }
    }
}
```

**Limite do MockMvc:** ele não passa pelo parser multipart do servidor, então o limite de 5 MB (413) **não** é testável aqui. Esse caso fica em `UploadLimitIT`, com servidor real ([03](03%20Testes%20de%20integracao.md), seção 6).
