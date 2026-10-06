package br.com.rag_pgvector.support;

/**
 * Constantes compartilhadas pelos testes (docs/QA/02, seção 2).
 */
public final class Fixtures {

	public static final long CLIENTE_A = 1L;
	public static final long CLIENTE_B = 2L;

	/** Igual a {@code app.security.admin-api-key} do application-test.properties. */
	public static final String CHAVE_ADMIN = "test-admin-key";
	public static final String CHAVE_A = "chave-do-cliente-a";
	public static final String CHAVE_B = "chave-do-cliente-b";

	public static final int DIMENSAO_EMBEDDING = 768;

	private Fixtures() {
	}

}
