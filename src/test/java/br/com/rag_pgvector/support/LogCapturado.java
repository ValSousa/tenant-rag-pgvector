package br.com.rag_pgvector.support;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.boot.test.system.CapturedOutput;

/**
 * Leitura das linhas do {@code RequestLoggingFilter} no texto capturado pelo {@code OutputCaptureExtension}
 * (docs/QA/05, regras do RF-013): sem depender do prefixo do Logback (data, PID, thread).
 */
public final class LogCapturado {

	private static final Pattern NIVEL = Pattern.compile("\\b(TRACE|DEBUG|INFO|WARN|ERROR)\\b");
	private static final Pattern CAMPO = Pattern.compile("(\\w+)=(\\S*)");

	private LogCapturado() {
	}

	public static List<String> linhasDeRequisicao(CapturedOutput output) {
		return output.getAll().lines()
				.filter(linha -> linha.contains("RequestLoggingFilter") && linha.contains(" service="))
				.toList();
	}

	public static String mensagem(String linha) {
		return linha.substring(linha.indexOf("service="));
	}

	public static String nivel(String linha) {
		Matcher matcher = NIVEL.matcher(linha.substring(0, linha.indexOf("service=")));
		return matcher.find() ? matcher.group(1) : null;
	}

	public static String campo(String linha, String nome) {
		Matcher matcher = CAMPO.matcher(mensagem(linha));
		while (matcher.find()) {
			if (matcher.group(1).equals(nome)) {
				return matcher.group(2);
			}
		}
		return null;
	}

	public static List<String> nomesDosCampos(String linha) {
		return CAMPO.matcher(mensagem(linha)).results().map(resultado -> resultado.group(1)).toList();
	}

}
