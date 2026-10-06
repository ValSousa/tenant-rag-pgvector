# ADR-014 — Pacote validator e exceções próprias no lugar de ResponseStatusException

- **Status:** Aprovada (decisão do responsável pelo projeto em 2026-10-05)
- **Data:** 2026-10-05
- **Requisitos:** RF-006 (validação do arquivo do upload), RF-010 (tratamento de erros); complementa a ADR-001 e a ADR-010

## Contexto

As regras do arquivo enviado em `POST /clients/{clientId}/documents` (arquivo vazio, nome ausente, nome acima de 255 caracteres, arquivo que não é PDF) ficavam num método privado do `DocumentController`, que lançava `ResponseStatusException(400)`. Essas regras não cabem em Bean Validation (o `MultipartFile` não é um DTO anotado), misturam validação com o papel do controller e o `ResponseStatusException` era o único erro do projeto fora do padrão "exceção própria + handler no `GlobalExceptionHandler`" da ADR-010.

## Decisão

- Novo pacote `br.com.rag_pgvector.validator` para validações de entrada que não cabem em Bean Validation. Os componentes são `@Component`, sem estado, injetados no controller; não acessam service, repository nem banco; em erro lançam exceção do pacote `exception`. Sufixo `Validator`.
- Primeiro componente: `DocumentFileValidator`, com `String validate(MultipartFile file)`, que recebe as regras do upload e devolve o nome limpo (`StringUtils.getFilename(StringUtils.cleanPath(...))`). Mensagens: "O arquivo enviado está vazio.", "Informe o nome do arquivo.", "O nome do arquivo deve ter no máximo 255 caracteres.", "O arquivo deve ser um PDF." (PDF = content-type `application/pdf` **ou** extensão `.pdf`).
- Nova `InvalidFileException` (pacote `exception`) → 400, tratada por um `@ExceptionHandler` explícito no `GlobalExceptionHandler`, com o mesmo `ProblemDetail` de hoje: `type` `about:blank`, `title` "Requisição inválida", `detail` = mensagem da exceção.
- `InvalidDocumentException` (422, PDF ilegível ou sem texto) continua separada e não é reaproveitada para erros de envio.
- Regra geral: o código não usa `ResponseStatusException`. Erros de negócio e de entrada são exceções próprias do pacote `exception`, mapeadas no `GlobalExceptionHandler`.
- O comportamento externo da API não muda (mesmos status, títulos e mensagens).

Esboço (sem IDs de requisito ou cenário no código):

```java
package br.com.rag_pgvector.validator;

@Component
public class DocumentFileValidator {

    /** Tamanho da coluna document.file_name. */
    private static final int MAX_FILE_NAME_LENGTH = 255;

    public String validate(MultipartFile file) {
        if (file.isEmpty()) {
            throw new InvalidFileException("O arquivo enviado está vazio.");
        }
        // nome limpo, tamanho e tipo PDF, cada um com InvalidFileException
        ...
    }
}
```

## Alternativas consideradas

- **Manter o método privado no controller com `ResponseStatusException`:** funciona e o handler atual já devolve o mesmo `ProblemDetail`, mas deixa um caminho de erro fora do padrão da ADR-010 e uma regra de entrada difícil de testar sem MockMvc.
- **Validador customizado de Bean Validation (`@ValidPdf` + `ConstraintValidator`):** encaixaria no `@Valid`, mas o erro sairia no formato de validação de campos (`errors[]`) com outra mensagem em `detail`, mudando o contrato; exige mais código para quatro regras simples.
- **Reaproveitar `InvalidDocumentException`:** mudaria o status de 400 para 422 e misturaria "envio inválido" com "PDF não processável".

## Consequências

- O `DocumentController` fica só com o papel de controller; as regras do arquivo ganham teste unitário simples, sem Spring.
- Todo erro do projeto passa por exceção própria + handler explícito; fica fácil achar onde cada status é produzido.
- Uma classe e um pacote a mais; nenhuma dependência nova.
- O teste do `GlobalExceptionHandler` e os testes atuais do upload garantem que status, títulos e mensagens não mudaram.
