package br.com.rag_pgvector.validator;

import java.util.Locale;

import br.com.rag_pgvector.exception.InvalidFileException;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

/**
 * Confere o arquivo do upload de documentos (regras que não cabem em Bean Validation) e devolve o nome limpo, sem
 * caminho. Sem estado; em erro lança {@link InvalidFileException}.
 */
@Component
public class DocumentFileValidator {

	/** Tamanho da coluna {@code document.file_name}. */
	private static final int MAX_FILE_NAME_LENGTH = 255;

	public String validate(MultipartFile file) {
		if (file.isEmpty()) {
			throw new InvalidFileException("O arquivo enviado está vazio.");
		}
		String fileName = StringUtils.getFilename(StringUtils.cleanPath(
				file.getOriginalFilename() == null ? "" : file.getOriginalFilename()));
		if (!StringUtils.hasText(fileName)) {
			throw new InvalidFileException("Informe o nome do arquivo.");
		}
		if (fileName.length() > MAX_FILE_NAME_LENGTH) {
			throw new InvalidFileException(
					"O nome do arquivo deve ter no máximo " + MAX_FILE_NAME_LENGTH + " caracteres.");
		}
		boolean pdfType = MediaType.APPLICATION_PDF_VALUE.equalsIgnoreCase(file.getContentType());
		boolean pdfExtension = fileName.toLowerCase(Locale.ROOT).endsWith(".pdf");
		if (!pdfType && !pdfExtension) {
			throw new InvalidFileException("O arquivo deve ser um PDF.");
		}
		return fileName;
	}

}
