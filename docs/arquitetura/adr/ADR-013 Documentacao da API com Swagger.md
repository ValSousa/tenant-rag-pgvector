# ADR-013 — Documentação da API com OpenAPI e Swagger UI

- **Status:** Aprovada (decisão do responsável pelo projeto em 2026-10-01)
- **Data:** 2026-10-01
- **Requisitos:** RF-003, RF-006, RF-007, RF-008 (forma de acesso aos endpoints)

## Contexto

A API só podia ser exercitada por curl, arquivo `.http` ou Postman. Para um projeto de estudo, ver e testar os endpoints no navegador acelera o desenvolvimento e a validação manual (perguntas da seção 10 do README). Todos os endpoints exigem o cabeçalho `X-API-Key` (ADR-008), e o `SecurityConfig` nega por padrão qualquer rota não listada.

## Decisão

- Adicionar **springdoc-openapi 3.1.x** (`org.springdoc:springdoc-openapi-starter-webmvc-ui`), a linha compatível com Spring Boot 4.1.
- Criar `config/OpenApiConfig` com:
  - título, descrição e versão da API;
  - esquema de segurança `apiKey` do tipo **API key no cabeçalho `X-API-Key`**, aplicado a todas as operações (botão **Authorize** no Swagger UI).
- Liberar sem autenticação, no `SecurityConfig`: `/swagger-ui.html`, `/swagger-ui/**` e `/v3/api-docs/**`. Só a documentação fica pública; as chamadas feitas por ela continuam exigindo a chave.
- Endpoints anotados com `@Tag`, `@Operation` e `@ApiResponse` (incluindo 400, 401, 403, 413, 422 e 503 com `ProblemDetail`).
- `DocumentController` declara `consumes = MULTIPART_FORM_DATA_VALUE` e recebe `@RequestPart("file") MultipartFile`, para o Swagger UI mostrar o seletor de arquivo.
- Documentação ligada por padrão e desligável por propriedade (`SWAGGER_ENABLED=false`).

URLs:

| Recurso | URL |
|---|---|
| Swagger UI | `http://localhost:8080/swagger-ui.html` |
| Especificação OpenAPI (JSON) | `http://localhost:8080/v3/api-docs` |

## Alternativas consideradas

- **Só arquivo `.http` / Postman:** sem dependência nova, mas sem documentação navegável dos contratos. Continua existindo como complemento (Etapa 6 do plano).
- **Spring REST Docs:** documentação gerada a partir dos testes, mais confiável, porém sem interface interativa e com mais trabalho.
- **Escrever o `openapi.yaml` à mão (contract-first):** contrato fica explícito, mas diverge do código se não houver geração.

## Consequências

- Uma dependência a mais; adicionada na Etapa 2 do plano, junto com o primeiro controller.
- A especificação pública revela a forma da API (não os dados). Aceitável em ambiente local; desligar com `SWAGGER_ENABLED=false` se a aplicação for exposta.
- Anotações de documentação aumentam um pouco os controllers; DTOs podem usar `@Schema` com exemplos.
- Teste de segurança passa a cobrir: documentação acessível sem chave; endpoints de dados chamados pelo Swagger continuam exigindo chave.
