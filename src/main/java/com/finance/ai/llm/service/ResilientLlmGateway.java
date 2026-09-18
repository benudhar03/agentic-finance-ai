package com.finance.ai.llm.service;

import com.finance.ai.exception.LlmUnavailableException;
import com.finance.ai.rag.service.RagChatService;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.timelimiter.annotation.TimeLimiter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;

@Slf4j
@Service
public class ResilientLlmGateway {

    private final LlmService llmService;
    private final RagChatService ragChatService;
    private final ExecutorService llmExecutor;

    public ResilientLlmGateway(LlmService llmService,
                               RagChatService ragChatService,
                               @Qualifier("llmExecutor") ExecutorService llmExecutor) {
        this.llmService = llmService;
        this.ragChatService = ragChatService;
        this.llmExecutor = llmExecutor;
    }

    @CircuitBreaker(name = "llmService", fallbackMethod = "llmFallback")
    @TimeLimiter(name = "llmService")
    public CompletableFuture<String> generateReplyAsync(String message, String conversationId) {
        return CompletableFuture.supplyAsync(
                () -> llmService.generateReply(message, conversationId), llmExecutor);
    }

    @CircuitBreaker(name = "ragChatService", fallbackMethod = "ragFallback")
    @TimeLimiter(name = "ragChatService")
    public CompletableFuture<String> generateGroundedReplyAsync(String message, String conversationId) {
        return CompletableFuture.supplyAsync(
                () -> ragChatService.generateGroundedReply(message, conversationId), llmExecutor);
    }

    /** Blocking convenience wrapper — used by FinanceAgent implementations. */
    public String generateReplyBlocking(String message, String conversationId) {
        return joinOrTranslate(generateReplyAsync(message, conversationId), conversationId, "LLM");
    }

    /** Blocking convenience wrapper — used by FinanceAgent implementations. */
    public String generateGroundedReplyBlocking(String message, String conversationId) {
        return joinOrTranslate(generateGroundedReplyAsync(message, conversationId), conversationId, "RAG");
    }

    private String joinOrTranslate(CompletableFuture<String> future, String conversationId, String label) {
        try {
            return future.join();
        } catch (Exception e) {
            log.error("{} call failed for conversation {}", label, conversationId, e);
            throw new LlmUnavailableException("The assistant is temporarily unavailable. Please try again.");
        }
    }

    private CompletableFuture<String> llmFallback(String message, String conversationId, Throwable t) {
        log.warn("llmService circuit breaker fallback for conversation {}: {}", conversationId, t.toString());
        return CompletableFuture.failedFuture(t);
    }

    private CompletableFuture<String> ragFallback(String message, String conversationId, Throwable t) {
        log.warn("ragChatService circuit breaker fallback for conversation {}: {}", conversationId, t.toString());
        return CompletableFuture.failedFuture(t);
    }
}