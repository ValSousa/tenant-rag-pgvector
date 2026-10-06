package br.com.rag_pgvector.support;

import java.util.ArrayList;
import java.util.List;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;

/**
 * {@link ChatModel} sem rede, no lugar do {@code OpenAiChatModel} nos testes (ADR-012): o {@code mvnw test} nunca
 * chama a OpenAI. O {@code RagAssistant} real ({@code AiServices}) roda sobre ele, então o teste vê o prompt exatamente
 * como iria para o modelo. Guarda cada requisição e devolve uma resposta fixa.
 */
public class FakeChatModel implements ChatModel {

	public static final String RESPOSTA = "Resposta do modelo falso [1].";

	private final List<ChatRequest> requisicoes = new ArrayList<>();
	private RuntimeException proximaFalha;

	@Override
	public synchronized ChatResponse doChat(ChatRequest chatRequest) {
		requisicoes.add(chatRequest);
		if (proximaFalha != null) {
			RuntimeException falha = proximaFalha;
			proximaFalha = null;
			throw falha;
		}
		return ChatResponse.builder().aiMessage(AiMessage.from(RESPOSTA)).build();
	}

	/** A próxima chamada lança a exceção. */
	public synchronized void falharNaProxima(RuntimeException falha) {
		proximaFalha = falha;
	}

	public synchronized List<ChatRequest> requisicoes() {
		return List.copyOf(requisicoes);
	}

	/** Texto da mensagem do usuário (trechos + pergunta) de cada chamada, na ordem. */
	public synchronized List<String> mensagensDoUsuario() {
		return requisicoes.stream()
				.flatMap(requisicao -> requisicao.messages().stream())
				.filter(UserMessage.class::isInstance)
				.map(mensagem -> ((UserMessage) mensagem).singleText())
				.toList();
	}

	public synchronized void reset() {
		requisicoes.clear();
		proximaFalha = null;
	}

}
