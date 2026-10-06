package br.com.rag_pgvector.service;

import java.util.ArrayList;
import java.util.List;

import br.com.rag_pgvector.config.RagProperties;
import br.com.rag_pgvector.dto.AnswerResponseDTO;
import br.com.rag_pgvector.dto.SearchResultDTO;
import br.com.rag_pgvector.dto.SourceResponseDTO;
import br.com.rag_pgvector.exception.AiProviderException;
import org.springframework.stereotype.Service;

/**
 * Resposta a perguntas: busca os chunks do cliente pelo {@link SearchService} (filtro {@code client_id} no
 * SQL), monta o contexto numerado e pede a resposta ao {@link RagAssistant}. Só chunks do cliente da requisição chegam
 * ao prompt (RN-01, ADR-011).
 */
@Service
public class AnswerService {

	/** Resposta quando a busca não traz chunks do cliente: o modelo não é chamado (FA-01). */
	public static final String NO_DOCUMENTS_ANSWER = "Não há documentos deste cliente para responder a esta pergunta.";

	private final SearchService searchService;
	private final RagAssistant ragAssistant;
	private final int answerTopK;

	public AnswerService(SearchService searchService, RagAssistant ragAssistant, RagProperties ragProperties) {
		this.searchService = searchService;
		this.ragAssistant = ragAssistant;
		this.answerTopK = ragProperties.answerTopK();
	}

	/**
	 * @param clientId cliente dono dos documentos, sempre o do caminho já autorizado
	 * @param topK quantidade de chunks no contexto; {@code null} usa o padrão do {@code /ask} (DP-04)
	 */
	public AnswerResponseDTO answer(long clientId, String question, Integer topK) {
		List<SearchResultDTO> chunks = searchService
				.search(clientId, question, topK == null ? answerTopK : topK)
				.results();
		if (chunks.isEmpty()) {
			return new AnswerResponseDTO(NO_DOCUMENTS_ANSWER, List.of());
		}
		List<String> lines = new ArrayList<>(chunks.size());
		List<SourceResponseDTO> sources = new ArrayList<>(chunks.size());
		for (int i = 0; i < chunks.size(); i++) {
			SearchResultDTO chunk = chunks.get(i);
			int ref = i + 1;
			lines.add("[%d] (%s, %s) %s".formatted(ref, chunk.fileName(), chunk.documentType(), chunk.content()));
			sources.add(new SourceResponseDTO(ref, chunk.documentId(), chunk.fileName(), chunk.documentType(),
					chunk.chunkIndex()));
		}
		String answer;
		try {
			answer = ragAssistant.answer(String.join("\n", lines), question);
		}
		catch (RuntimeException ex) {
			throw new AiProviderException("Falha ao gerar a resposta no provedor de IA: " + ex.getMessage(), ex);
		}
		return new AnswerResponseDTO(answer, sources);
	}

}
