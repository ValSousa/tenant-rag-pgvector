package br.com.rag_pgvector.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * SHA-256 em hexadecimal da chave de API (ADR-008). Suficiente porque a chave é aleatória com 256 bits de entropia.
 */
public final class ApiKeyHasher {

	private ApiKeyHasher() {
	}

	public static String hash(String apiKey) {
		try {
			byte[] digest = MessageDigest.getInstance("SHA-256").digest(apiKey.getBytes(StandardCharsets.UTF_8));
			return HexFormat.of().formatHex(digest);
		}
		catch (NoSuchAlgorithmException ex) {
			throw new IllegalStateException("SHA-256 indisponível na JVM", ex);
		}
	}

}
