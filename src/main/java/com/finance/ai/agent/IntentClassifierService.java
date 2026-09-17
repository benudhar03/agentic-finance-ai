package com.finance.ai.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.finance.ai.agent.model.QueryIntent;
import com.finance.ai.agent.model.QueryIntentResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class IntentClassifierService {

    private static final String CLASSIFIER_SYSTEM_PROMPT = """
            You are a routing classifier for a finance assistant. Given the recent
            conversation history and the latest user message, do two things:

            1. Classify the latest message into exactly one intent:
               - GENERAL_FINANCE_EDUCATION: general concepts, definitions, how things work
               - DOCUMENT_QA: requires looking up specific figures, terms, transaction
                 details, or content that would only be found in an uploaded document
                 (receipts, filings, fact sheets, statements)
               - ACCOUNT_SPECIFIC_ACTION: requires real account data or a mutating action
               - ADVICE_REQUEST: asks for personalized investment, tax, or legal advice
               - SMALL_TALK: greetings, thanks, casual conversation
               - OUT_OF_SCOPE: unrelated to personal finance

            2. Rewrite the latest message into a fully standalone question that
               incorporates any necessary context from the conversation history
               (resolve pronouns like "it" / "that account", fill in an implied
               subject). If it is already standalone, return it unchanged.

            Respond ONLY with JSON in exactly this shape, no other text:
            {"intent": "...", "enrichedQuery": "..."}
            """;

    private final ChatClient classifierChatClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public IntentClassifierService(@Qualifier("classifierChatClient") ChatClient classifierChatClient) {
        this.classifierChatClient = classifierChatClient;
    }

    public QueryIntentResult classify(String userMessage, String conversationHistory) {
        String userPrompt = """
                Conversation history (most recent last):
                %s

                Latest message:
                %s
                """.formatted(
                conversationHistory == null || conversationHistory.isBlank() ? "(none)" : conversationHistory,
                userMessage
        );

        String raw;
        try {
            raw = classifierChatClient
                    .prompt()
                    .system(CLASSIFIER_SYSTEM_PROMPT)
                    .user(userPrompt)
                    .call()
                    .content();
        } catch (Exception e) {
            log.warn("Intent classification call failed for message=\"{}\", defaulting to GENERAL_FINANCE_EDUCATION", userMessage, e);
            return fallback(userMessage);
        }

        QueryIntentResult result = parse(raw, userMessage);
        log.info("INTENT CLASSIFIED: conversationTurn intent={}, requiresDocumentRetrieval={}, messageLength={}, enrichedLength={}",
                result.intent(), result.requiresDocumentRetrieval(),
                userMessage != null ? userMessage.length() : 0,
                result.enrichedQuery() != null ? result.enrichedQuery().length() : 0);
        return result;
    }

    // Retained for any existing callers that only need the boolean RAG decision.
    public boolean requiresDocumentRetrieval(String userMessage) {
        return classify(userMessage, null).requiresDocumentRetrieval();
    }

    private QueryIntentResult parse(String raw, String fallbackMessage) {
        try {
            JsonNode node = objectMapper.readTree(raw);
            QueryIntent intent = QueryIntent.valueOf(node.get("intent").asText());
            String enriched = node.hasNonNull("enrichedQuery") ? node.get("enrichedQuery").asText() : fallbackMessage;
            boolean needsRag = intent == QueryIntent.DOCUMENT_QA;
            boolean needsDisclaimer = intent == QueryIntent.ADVICE_REQUEST;
            return new QueryIntentResult(intent, enriched, needsRag, needsDisclaimer);
        } catch (Exception e) {
            log.warn("Could not parse intent classifier output, defaulting. Raw response: {}", raw);
            return fallback(fallbackMessage);
        }
    }

    private QueryIntentResult fallback(String message) {
        return new QueryIntentResult(QueryIntent.GENERAL_FINANCE_EDUCATION, message, false, false);
    }
}