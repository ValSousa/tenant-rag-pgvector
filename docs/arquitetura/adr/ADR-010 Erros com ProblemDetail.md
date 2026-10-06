# ADR-010 — Erros no formato ProblemDetail (RFC 9457)

- **Status:** Aprovada (decisão do responsável pelo projeto em 2026-10-01)
- **Data:** 2026-10-01
- **Requisitos:** RF-010 (resolve DP-01, DP-02, DP-03); fluxos alternativos de RF-003, RF-006, RF-007, RF-008, RF-009

## Contexto

O README prevê `ResourceNotFoundException` e `GlobalExceptionHandler`, sem definir códigos nem formato. Os erros saem de três lugares: controllers/services, Spring Security (401/403) e o próprio Spring MVC (validação, multipart).

## Decisão

- Corpo `application/problem+json` com `ProblemDetail` do Spring (`type`, `title`, `status`, `detail`, `instance`; `errors` em validação).
- `GlobalExceptionHandler` (`@RestControllerAdvice`, estende `ResponseEntityExceptionHandler`) e handlers do Spring Security produzem o mesmo formato.
- Mapeamento de códigos em [05 API REST.md](../05%20API%20REST.md), seção 3.
- Mensagens em português; sem stack trace, SQL ou dados de outro cliente.
- Erros de negócio e de entrada usam exceções próprias do pacote `exception`, cada uma com `@ExceptionHandler` explícito; o código não usa `ResponseStatusException` (complemento de 2026-10-05, ver [ADR-014](ADR-014%20Pacote%20validator%20e%20excecoes%20proprias.md)).

## Alternativas consideradas

- **Objeto de erro próprio (`ErrorResponse`):** funciona, mas reinventa um padrão que o Spring já suporta.

## Consequências

- Um formato único para o cliente da API e para os testes.
- `spring.mvc.problemdetails.enabled=true` cobre também os erros que o MVC gera sozinho.
