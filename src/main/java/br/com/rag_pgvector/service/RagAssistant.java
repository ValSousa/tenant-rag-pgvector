package br.com.rag_pgvector.service;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

/**
 * Assistente de resposta do RAG, implementado pelo {@code AiServices} do LangChain4j sobre o {@code ChatModel} da
 * OpenAI (arquitetura 06, seção 3.4). Não tem {@code ContentRetriever}: nunca busca no banco sozinho e recebe
 * os trechos já filtrados por cliente pelo {@link AnswerService} (ADR-011).
 */
public interface RagAssistant {

	@SystemMessage("""
			Você é um assistente que responde perguntas sobre documentos de seguro de um único cliente.
			Use somente as informações dos trechos numerados fornecidos.
			Se a resposta não estiver nos trechos, diga que a informação não foi encontrada nos documentos do cliente.
			Não invente valores, datas ou percentuais.
			Quando fizer cálculos, mostre os números usados.
			Cite os trechos usados no formato [n].
			Responda em português do Brasil.
			""")
	@UserMessage("""
			Trechos:
			{{context}}

			Pergunta: {{question}}
			""")
	String answer(@V("context") String context, @V("question") String question);

}
