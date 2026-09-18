package com.finance.ai.chat.service;

import com.finance.ai.agent.AgentRegistry;
import com.finance.ai.agent.FinanceAgent;
import com.finance.ai.agent.IntentClassifierService;
import com.finance.ai.agent.model.QueryIntent;
import com.finance.ai.agent.model.QueryIntentResult;
import com.finance.ai.chat.dto.ChatRequest;
import com.finance.ai.chat.dto.ChatResponse;
import com.finance.ai.exception.LlmUnavailableException;
import com.finance.ai.exception.PromptGuardException;
import com.finance.ai.guardrail.OutputGuardService;
import com.finance.ai.guardrail.PromptGuardService;
import com.finance.ai.memory.service.ConversationAuditService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatService {

    private static final int HISTORY_MESSAGE_LIMIT = 5;

    private final AgentRegistry agentRegistry;
    private final ConversationAuditService auditService;
    private final PromptGuardService promptGuardService;
    private final OutputGuardService outputGuardService;
    private final IntentClassifierService intentClassifierService;


    public ChatResponse handleChat(ChatRequest request) {
        UUID conversationId = auditService.resolveConversation(request);
        return process(request, conversationId, false);
    }

    public ChatResponse handleRagChat(ChatRequest request) {
        UUID conversationId = auditService.resolveConversation(request);
        return process(request, conversationId, true);
    }

    public ChatResponse handleAgentChat(ChatRequest request) {
        UUID conversationId = auditService.resolveConversation(request);
        return process(request, conversationId, false);
    }

    private ChatResponse process(ChatRequest request, UUID conversationId, boolean forceRag) {
        var inputCheck = promptGuardService.screenUserInput(request.getMessage());
        if (inputCheck.flagged()) {
            log.warn("Flagged input for conversation {}: {}", conversationId, inputCheck.reason());
            throw new PromptGuardException("Your message could not be processed. Please rephrase.");
        }

        String history = auditService.getRecentHistory(conversationId, HISTORY_MESSAGE_LIMIT);
        QueryIntentResult intentResult = intentClassifierService.classify(request.getMessage(), history);

        // Deterministic, non-agent paths — safety-critical enough that a scripted
        // response is preferable to an LLM-generated one, every time.
        if (intentResult.intent() == QueryIntent.OUT_OF_SCOPE) {
            return shortCircuit(request, conversationId,
                    "I'm focused on personal finance topics — I'm not able to help with that here.");
        }
        if (intentResult.intent() == QueryIntent.ADVICE_REQUEST) {
            return shortCircuit(request, conversationId,
                    "I can explain the concepts involved, but personalized investment, tax, or legal advice needs a licensed professional — I'd recommend speaking with one for your specific situation.");
        }

        QueryIntent effectiveIntent = forceRag ? QueryIntent.DOCUMENT_QA : intentResult.intent();
        FinanceAgent agent = agentRegistry.find(effectiveIntent)
                .orElseGet(() -> agentRegistry.find(QueryIntent.GENERAL_FINANCE_EDUCATION)
                        .orElseThrow(() -> new IllegalStateException("No fallback agent registered")));

        String queryForModel = intentResult.enrichedQuery();
        String reply;
        try {
            reply = agent.respond(queryForModel, conversationId.toString());
        } catch (LlmUnavailableException e) {
            throw e;
        } catch (Exception e) {
            log.error("Agent {} failed for conversation {}", agent.getClass().getSimpleName(), conversationId, e);
            throw new LlmUnavailableException("The assistant is temporarily unavailable. Please try again.");
        }

        var outputCheck = outputGuardService.screen(reply);
        if (outputCheck.flagged()) {
            log.warn("Flagged output for conversation {}: {}", conversationId, outputCheck.reason());
            reply = outputGuardService.sanitize(reply);
        }

        auditService.recordExchange(conversationId, request.getMessage(), reply);
        boolean usedRag = effectiveIntent == QueryIntent.DOCUMENT_QA;
        return new ChatResponse(reply, conversationId.toString(), usedRag);
    }

    private ChatResponse shortCircuit(ChatRequest request, UUID conversationId, String reply) {
        auditService.recordExchange(conversationId, request.getMessage(), reply);
        return new ChatResponse(reply, conversationId.toString(), false);
    }
}