package br.com.rag_pgvector.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ApiKeyHasherTest {

	@Test
	@DisplayName("CT-083 — Chave guardada só como hash (SHA-256 hexadecimal)")
	void deveCalcularSha256Hexadecimal() {
		String hash = ApiKeyHasher.hash("abc");

		assertThat(hash).isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
	}

}
