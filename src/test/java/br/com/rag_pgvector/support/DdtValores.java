package br.com.rag_pgvector.support;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Converte os valores especiais das massas CSV (docs/QA/04, seção 2).
 */
public final class DdtValores {

	private static final Pattern REPETICAO = Pattern.compile("<(\\d+) (.+)>");

	private DdtValores() {
	}

	public static String resolver(String valor) {
		if (valor == null || "<null>".equals(valor)) {
			return null;
		}
		if ("<vazio>".equals(valor)) {
			return "";
		}
		if ("<espacos>".equals(valor)) {
			return "   ";
		}
		Matcher repeticao = REPETICAO.matcher(valor);
		if (repeticao.matches()) {
			return repeticao.group(2).repeat(Integer.parseInt(repeticao.group(1)));
		}
		return valor;
	}

}
