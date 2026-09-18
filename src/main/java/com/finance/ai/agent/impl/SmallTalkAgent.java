package com.finance.ai.agent.impl;

import com.finance.ai.agent.FinanceAgent;
import com.finance.ai.agent.model.QueryIntent;
import com.finance.ai.llm.service.ResilientLlmGateway;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class SmallTalkAgent implements FinanceAgent {

    private final ResilientLlmGateway llmGateway;

    @Override
    public QueryIntent supportedIntent() {
        return QueryIntent.SMALL_TALK;
    }

    @Override
    public String respond(String enrichedQuery, String conversationId) {
        // Shares the same underlying LLM call as GeneralEducationAgent today.
        // Kept as a separate agent class deliberately, so this can later get
        // its own lighter-weight prompt/model without touching the education path.
        return llmGateway.generateReplyBlocking(enrichedQuery, conversationId);
    }
}