package br.com.rag_pgvector.support;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.SplittableRandom;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;

/**
 * Gera PDFs em memória com PDFBox, para não versionar binários de teste (docs/QA/03, seção 3.3). A fonte Helvetica
 * (WinAnsiEncoding) aceita acentos do português e "R$".
 */
public final class TestPdfFactory {

	public static final String FRASE = "A franquia da apólice para colisão é de R$ 3.500,00 por evento. ";

	private static final float FONT_SIZE = 11f;
	private static final float LEADING = 14f;
	private static final float MARGIN = 50f;

	private TestPdfFactory() {
	}

	public static byte[] comTexto(String... paragrafos) {
		try (PDDocument document = new PDDocument()) {
			PDType1Font font = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
			PDRectangle pageSize = PDRectangle.A4;
			float width = pageSize.getWidth() - 2 * MARGIN;
			List<String> linhas = new ArrayList<>();
			for (String paragrafo : paragrafos) {
				if (!linhas.isEmpty()) {
					linhas.add("");
				}
				linhas.addAll(quebrarEmLinhas(paragrafo, font, width));
			}
			int linhasPorPagina = (int) ((pageSize.getHeight() - 2 * MARGIN) / LEADING);
			for (int inicio = 0; inicio < linhas.size(); inicio += linhasPorPagina) {
				escreverPagina(document, font, pageSize,
						linhas.subList(inicio, Math.min(inicio + linhasPorPagina, linhas.size())));
			}
			return salvar(document);
		}
		catch (IOException ex) {
			throw new UncheckedIOException(ex);
		}
	}

	public static byte[] comTamanho(int caracteres) {
		return comTexto(textoComTamanho(caracteres));
	}

	public static String textoComTamanho(int caracteres) {
		return FRASE.repeat(caracteres / FRASE.length() + 1).substring(0, caracteres);
	}

	public static byte[] semTexto() {
		try (PDDocument document = new PDDocument()) {
			document.addPage(new PDPage(PDRectangle.A4));
			return salvar(document);
		}
		catch (IOException ex) {
			throw new UncheckedIOException(ex);
		}
	}

	public static byte[] corrompido() {
		byte[] lixo = new byte[2048];
		new SplittableRandom(42).nextBytes(lixo);
		byte[] cabecalho = "%PDF-1.7\n".getBytes(StandardCharsets.US_ASCII);
		System.arraycopy(cabecalho, 0, lixo, 0, cabecalho.length);
		return lixo;
	}

	public static byte[] vazio() {
		return new byte[0];
	}

	public static byte[] arquivoDeTamanho(int tamanho) {
		byte[] bytes = new byte[tamanho];
		Arrays.fill(bytes, (byte) 'a');
		byte[] cabecalho = "%PDF-1.7\n".getBytes(StandardCharsets.US_ASCII);
		System.arraycopy(cabecalho, 0, bytes, 0, Math.min(cabecalho.length, tamanho));
		return bytes;
	}

	private static List<String> quebrarEmLinhas(String paragrafo, PDType1Font font, float width) throws IOException {
		List<String> linhas = new ArrayList<>();
		StringBuilder linha = new StringBuilder();
		for (String palavra : paragrafo.strip().split("\\s+")) {
			String candidata = linha.isEmpty() ? palavra : linha + " " + palavra;
			if (largura(candidata, font) <= width) {
				linha.setLength(0);
				linha.append(candidata);
				continue;
			}
			if (!linha.isEmpty()) {
				linhas.add(linha.toString());
				linha.setLength(0);
			}
			String resto = palavra;
			while (largura(resto, font) > width) {
				int corte = resto.length() - 1;
				while (corte > 1 && largura(resto.substring(0, corte), font) > width) {
					corte--;
				}
				linhas.add(resto.substring(0, corte));
				resto = resto.substring(corte);
			}
			linha.append(resto);
		}
		if (!linha.isEmpty()) {
			linhas.add(linha.toString());
		}
		return linhas;
	}

	private static float largura(String texto, PDType1Font font) throws IOException {
		return font.getStringWidth(texto) / 1000 * FONT_SIZE;
	}

	private static void escreverPagina(PDDocument document, PDType1Font font, PDRectangle pageSize,
			List<String> linhas) throws IOException {
		PDPage page = new PDPage(pageSize);
		document.addPage(page);
		try (PDPageContentStream content = new PDPageContentStream(document, page)) {
			content.beginText();
			content.setFont(font, FONT_SIZE);
			content.setLeading(LEADING);
			content.newLineAtOffset(MARGIN, pageSize.getHeight() - MARGIN);
			for (String linha : linhas) {
				content.showText(linha);
				content.newLine();
			}
			content.endText();
		}
	}

	private static byte[] salvar(PDDocument document) throws IOException {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		document.save(out);
		return out.toByteArray();
	}

}
