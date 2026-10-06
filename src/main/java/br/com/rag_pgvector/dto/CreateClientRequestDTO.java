package br.com.rag_pgvector.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateClientRequestDTO(
		@NotBlank(message = "não deve estar em branco")
		@Size(max = 255, message = "deve ter no máximo 255 caracteres")
		String name) {
}
