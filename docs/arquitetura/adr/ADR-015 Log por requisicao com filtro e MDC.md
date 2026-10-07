# ADR-015 — Log por requisição com filtro servlet e MDC

- **Status:** Aprovada (2026-10-06) — o responsável pelo projeto aprovou esta ADR e todas as recomendações para as DP-01 a DP-07 do RF-013, sem mudanças (seção "Decisões do responsável")
- **Data:** 2026-10-05 (proposta); 2026-10-06 (aprovação)
- **Requisitos:** RF-013 (log de requisições para rastreamento); relaciona-se com RF-009 (401/403 da segurança) e RF-010 (log de erro do `GlobalExceptionHandler`); complementa a ADR-008 e a ADR-010

## Contexto

O RF-013 pede uma linha de log por requisição com `timestamp`, `level`, `service`, `environment`, `method`, `endpoint`, `status`, `clientId`, `traceId` e `duration`, inclusive para as requisições recusadas pela segurança (401/403) e para as que terminam em erro, e que o log de erro já existente possa ser relacionado à requisição pelo mesmo `traceId`. Nenhum log pode trazer chaves de API, segredos nem o hash das chaves.

Hoje:

- Só o `GlobalExceptionHandler` registra log (`log.error` para falha da IA e erro inesperado). Não há trace ID nem medição de duração.
- Os 401/403 são produzidos dentro da cadeia do Spring Security (`ApiKeyAuthenticationFilter` e `ProblemDetailSecurityHandler`), que no Spring Boot 4.1.1 é registrada como filtro servlet na ordem `-100` (`spring.security.filter.order`, valor padrão conferido no `spring-boot-security-4.1.1.jar`). Um `HandlerInterceptor` do Spring MVC roda depois da segurança e não veria essas respostas.
- O Spring Boot 4.1.1 já traz, sem dependência nova (conferido no `spring-boot-4.1.1.jar`): o padrão de console com `${LOG_CORRELATION_PATTERN}` (propriedade `logging.pattern.correlation`), o log estruturado em JSON (`logging.structured.format.console` = `ecs`, `logstash` ou `gelf`) e, no `spring-boot-test-4.1.1.jar`, o `OutputCaptureExtension` para ler o log nos testes.
- A visão geral ([01](../01%20Visao%20geral.md), seção 8) deixava "observabilidade avançada" fora do escopo.

## Decisão

1. **Escopo.** O log básico por requisição (uma linha por chamada + trace ID no log da aplicação) entra no escopo. Métricas, tracing distribuído, propagação de contexto entre serviços, exportação para ferramentas externas e painéis continuam fora ("observabilidade avançada").
2. **Um filtro servlet antes do Spring Security.** Novo pacote `br.com.rag_pgvector.logging` com `RequestLoggingFilter extends OncePerRequestFilter`, `@Component` com `@Order(Ordered.HIGHEST_PRECEDENCE + 10)`, isto é, antes da cadeia do Spring Security (`-100`). Por envolver a cadeia inteira, ele vê o `status` final de toda resposta: 2xx, 401/403 da segurança, 4xx/5xx do `GlobalExceptionHandler` e 413 do multipart. Se uma exceção escapar da cadeia, registra `status=500` e relança.
3. **Trace ID no MDC.** No início da requisição o filtro gera o trace ID (`UUID` aleatório sem hífens, 32 caracteres hexadecimais), coloca em `MDC.put("traceId", ...)` e remove no `finally`. A propriedade `logging.pattern.correlation=[%X{traceId:-}] ` faz o padrão de console do Spring Boot mostrar o trace ID em **todas** as linhas de log da requisição, inclusive o `log.error` do `GlobalExceptionHandler`, sem alterar essa classe. O nome `traceId` é o mesmo que o Micrometer Tracing usa no MDC, então uma adoção futura não muda o padrão. O trace ID é sempre gerado pela aplicação (trace ID vindo de fora não é aceito) e devolvido no cabeçalho de resposta `X-Trace-Id` (DP-03).
4. **Duração** medida com `System.nanoTime()` no filtro, registrada em milissegundos (`duration=42ms`).
5. **Linha de log** escrita pelo logger do filtro, só com os campos fixos, montados a partir de valores que o filtro controla:
   `service={} environment={} method={} endpoint={} status={} clientId={} traceId={} duration={}ms`. `timestamp` e `level` vêm do padrão do Logback do Spring Boot. Os dez campos estão sempre presentes (CA-01); valor ausente vira `-` (DP-05).
   - `method`: `request.getMethod()`.
   - `endpoint`: `request.getRequestURI()` — o caminho **sem** query string (DP-06). O URI não é decodificado pelo servlet, então quebras de linha chegam codificadas (`%0A`) e não forjam linhas novas no log.
   - `clientId`: o número de `/clients/{clientId}` no caminho, lido com uma expressão regular (`^/clients/(\d+)(/.*)?$`), ou `-` (DP-05). Não depende do `SecurityContext`, que o `SecurityContextHolderFilter` já limpou quando o filtro externo registra a linha, e fica disponível também nos 401/403.
   - `service`: `spring.application.name`; `environment`: nova propriedade `app.logging.environment` (DP-04).
   - `level` conforme o `status` (DP-07).
6. **Formato texto** (chave=valor, como no exemplo do pedido), com o padrão de console padrão do Spring Boot mais a correlação (DP-02). Se um dia o formato passar a JSON, basta `logging.structured.format.console=ecs` (ou `logstash`): o MDC (`traceId`) vira campo do JSON automaticamente, sem dependência nova e sem mudar o filtro.
7. **O que nunca entra no log** (RN-05/RN-06): o filtro não lê nem registra cabeçalhos (`X-API-Key` incluída), corpo de requisição ou resposta, query string, IP ou `User-Agent`. Regras de configuração que acompanham a decisão:
   - não usar `CommonsRequestLoggingFilter` nem `logRequests`/`logResponses` nos builders do LangChain4j (registrariam cabeçalhos ou o conteúdo enviado à OpenAI);
   - não deixar em arquivo versionado níveis `DEBUG`/`TRACE` de `org.springframework.web`, `org.springframework.security`, `org.hibernate.orm.jdbc.bind` (este registraria o `api_key_hash` como parâmetro do SQL) nem de clientes HTTP;
   - `spring.jpa.show-sql` continua desligado.
8. **Rotas de apoio** (`/swagger-ui.html`, `/swagger-ui/**`, `/v3/api-docs/**`) ficam fora do log por `shouldNotFilter` (DP-07). O despacho `ERROR` não é registrado de novo (padrão do `OncePerRequestFilter`).
9. **Sem dependência nova** no `pom.xml`: SLF4J, Logback, MDC e `OutputCaptureExtension` já vêm com o Spring Boot 4.1.1.

Esboço (sem IDs de requisito ou cenário no código):

```java
package br.com.rag_pgvector.logging;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10) // antes da cadeia do Spring Security (-100), para ver também 401/403
public class RequestLoggingFilter extends OncePerRequestFilter {

    public static final String TRACE_ID_HEADER = "X-Trace-Id";
    static final String TRACE_ID_MDC_KEY = "traceId";

    private static final Logger log = LoggerFactory.getLogger(RequestLoggingFilter.class);
    private static final Pattern CLIENT_PATH = Pattern.compile("^/clients/(\\d+)(/.*)?$");

    private final String service;
    private final String environment;

    public RequestLoggingFilter(@Value("${spring.application.name}") String service,
            @Value("${app.logging.environment}") String environment) {
        this.service = service;
        this.environment = environment;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path.startsWith("/swagger-ui") || path.startsWith("/v3/api-docs");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        long start = System.nanoTime();
        String traceId = UUID.randomUUID().toString().replace("-", "");
        MDC.put(TRACE_ID_MDC_KEY, traceId);
        response.setHeader(TRACE_ID_HEADER, traceId); // antes da cadeia: depois a resposta pode estar enviada
        int status = HttpServletResponse.SC_INTERNAL_SERVER_ERROR; // vale se uma exceção escapar da cadeia
        try {
            chain.doFilter(request, response);
            status = response.getStatus();
        }
        finally {
            long durationMs = (System.nanoTime() - start) / 1_000_000;
            log.atLevel(levelFor(status)).log(
                    "service={} environment={} method={} endpoint={} status={} clientId={} traceId={} duration={}ms",
                    service, environment, request.getMethod(), request.getRequestURI(), status,
                    clientIdFrom(request.getRequestURI()), traceId, durationMs);
            MDC.remove(TRACE_ID_MDC_KEY);
        }
    }

    private static Level levelFor(int status) {
        if (status >= 500) return Level.ERROR;
        if (status >= 400) return Level.WARN;
        return Level.INFO;
    }

    private static String clientIdFrom(String path) {
        Matcher matcher = CLIENT_PATH.matcher(path);
        return matcher.matches() ? matcher.group(1) : "-";
    }
}
```

Linha esperada (texto, padrão do Spring Boot + correlação; trechos fixos do padrão abreviados com `...`):

```text
2026-10-05T17:35:21.123-03:00  INFO 1234 --- [tenant-rag-pgvector] [nio-8080-exec-1] [3f2a...c9] b.c.r.logging.RequestLoggingFilter : service=tenant-rag-pgvector environment=local method=GET endpoint=/clients/123 status=200 clientId=123 traceId=3f2a...c9 duration=42ms
```

O `traceId` aparece duas vezes na linha de requisição (prefixo da correlação e campo da mensagem). É de propósito: o campo na mensagem mantém os dez campos mesmo que o padrão mude, e o prefixo leva o mesmo valor às outras linhas da requisição. A linha de 401/403 vem do mesmo filtro; a de erro do `GlobalExceptionHandler` mostra o mesmo `[traceId]` no prefixo.

`Level` é `org.slf4j.event.Level` e `log.atLevel(...)` é a API fluente do SLF4J 2 (existe desde a 2.0; o Boot 4.1.1 gerencia o `slf4j-api` 2.0.18, conferido no `spring-boot-dependencies-4.1.1.pom`).

## Decisões do responsável (2026-10-06)

O responsável pelo projeto aprovou em 2026-10-06 todas as recomendações abaixo, sem mudanças. Elas valem como decisão das DP-01 a DP-07 do RF-013 (o registro no requisito fica a cargo do `analista-de-requisitos`).

| DP do RF-013 | Decisão (aprovada em 2026-10-06) | Por quê |
|---|---|---|
| DP-01 Prioridade e entrega | Manter **Média** e desenvolver agora, para entrar numa próxima release (depois da v0.1.0), como etapa própria (Etapa 8 do [plano](../09%20Plano%20de%20implementacao.md)) | Não muda o valor do README; o código é pequeno e independente das outras etapas |
| DP-02 Formato | **Texto chave=valor** (como o exemplo do pedido) | Legível no console da IDE e nos testes manuais; o projeto só roda localmente. JSON fica a uma propriedade de distância (`logging.structured.format.console=ecs`), sem dependência nova |
| DP-03 Trace ID | **Gerado pela aplicação** a cada requisição (UUID sem hífens); **devolvido** no cabeçalho de resposta `X-Trace-Id`; **presente em todas as linhas** da requisição via MDC + `logging.pattern.correlation`; **não aceitar** trace ID de entrada por enquanto | Gerar sempre é o mais simples e evita validar valor externo (injeção no log). Devolver no cabeçalho permite a quem chamou citar o ID numa investigação. Aceitar `X-Trace-Id`/`traceparent` de entrada só faz sentido com outros serviços na frente (fora do escopo) |
| DP-04 `service` e `environment` | `service` = `spring.application.name` (`tenant-rag-pgvector`); `environment` = `app.logging.environment=${APP_ENVIRONMENT:local}` | Sem valor fixo no código; o `client-api`/`prod` do pedido é exemplo. Valor padrão `local` porque o projeto só roda na máquina do desenvolvedor |
| DP-05 `clientId` | Número do caminho `/clients/{clientId}` em **qualquer** resultado (inclusive 401/403 e chamadas do administrador em `GET /clients/{id}`); `-` quando a rota não tem cliente (`POST /clients`, rotas inexistentes). Não registrar quem chamou (cliente ou administrador) | Uma regra só, sem depender da autenticação (o contexto de segurança já foi limpo quando a linha é escrita). Num 403 o valor é o cliente **alvo**, o que basta para investigar tentativas de acesso cruzado. Registrar o papel de quem chamou exigiria o `ApiKeyAuthenticationFilter` escrever no MDC; fica como evolução |
| DP-06 O que fica fora | Fora: corpo da requisição e da resposta, pergunta do `/ask` e do `/search`, texto e nome do arquivo, nome do cliente, **query string**, todos os cabeçalhos, IP e `User-Agent` | O filtro registra só os campos fixos; nada vem de entrada livre, exceto o caminho (sem query string) |
| DP-07 Rotas de apoio e nível | Swagger UI e `/v3/api-docs` **fora** do log; `INFO` para status < 400, `WARN` para 4xx, `ERROR` para 5xx | A página do Swagger carrega vários arquivos estáticos e encheria o log de linhas sem valor. Os níveis separam uso normal, erro de quem chamou e falha da aplicação |

Se alguma dessas decisões mudar no futuro, o esboço muda pouco: formato (uma propriedade), trace ID de entrada (ler e validar `X-Trace-Id` com `[A-Za-z0-9-]{1,64}` antes de gerar), `clientId` do principal (o `ApiKeyAuthenticationFilter` grava `MDC.put("clientId", ...)`), níveis (um `switch`).

## Alternativas consideradas

- **`HandlerInterceptor` do Spring MVC:** roda depois do Spring Security; não registraria os 401/403 (FA-01 do RF-013) nem os 413 do multipart antes do controller.
- **`CommonsRequestLoggingFilter` do Spring:** registra antes e depois da requisição, mas não mede duração nem registra status, e pode incluir query string, cabeçalhos e corpo — o contrário do que o RF-013 pede.
- **Access log do Tomcat (`server.tomcat.accesslog.*`):** sem código e com duração (`%D`), mas grava em arquivo separado, em outro formato, sem o MDC da aplicação (o trace ID não liga a linha ao log de erro sem código extra) e não roda no MockMvc, o que dificulta testar sem servidor.
- **Micrometer Tracing (bridge Brave ou OpenTelemetry) + Actuator:** trace ID e propagação W3C (`traceparent`) automáticos, mas traz dependências novas e é a "observabilidade avançada" que segue fora do escopo. O nome `traceId` no MDC foi escolhido para ser compatível com ele, se um dia entrar.
- **Actuator `httpexchanges`:** guarda as requisições em memória para consulta por endpoint, não gera linha de log; dependência nova.
- **Encoder JSON de terceiros (`logstash-logback-encoder`):** desnecessário; o Spring Boot 4.1.1 já tem log estruturado embutido.
- **Registrar o log dentro do `ApiKeyAuthenticationFilter`:** misturaria autenticação com log, e o filtro só roda dentro da cadeia de segurança.

## Consequências

- Uma classe nova (`logging/RequestLoggingFilter`), um pacote novo, duas propriedades (`app.logging.environment`, `logging.pattern.correlation`), nenhuma dependência nova.
- Toda linha de log de uma requisição carrega o trace ID, inclusive o erro do `GlobalExceptionHandler`, sem mudar essa classe.
- A resposta ganha o cabeçalho `X-Trace-Id` (DP-03); status, corpo e os cabeçalhos que já existem não mudam (CA-08).
- Como `@Component` do tipo `Filter`, o filtro entra também nos testes `@WebMvcTest` (o `WebSliceTest` passa a gerar as linhas de log e a precisar da propriedade `app.logging.environment`, que vem do `application.properties`).
- A garantia de "nenhum segredo no log" depende também da configuração (item 7 da decisão); a revisão de código deve recusar `DEBUG`/`TRACE` desses pacotes em arquivo versionado e `logRequests(true)` no LangChain4j.
- Custo por requisição desprezível (um UUID, uma expressão regular e uma linha de log).
