package com.finance.ai.agent.impl;

import com.finance.ai.agent.FinanceAgent;
import com.finance.ai.agent.model.QueryIntent;
import com.finance.ai.llm.service.ResilientLlmGateway;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class GeneralEducationAgent implements FinanceAgent {

    private final ResilientLlmGateway llmGateway;

    @Override
    public QueryIntent supportedIntent() {
        return QueryIntent.GENERAL_FINANCE_EDUCATION;
    }

    @Override
    public String respond(String enrichedQuery, String conversationId) {
        return llmGateway.generateReplyBlocking(enrichedQuery, conversationId);
    }
}