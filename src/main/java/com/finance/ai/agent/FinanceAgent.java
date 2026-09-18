package com.finance.ai.agent;

import com.finance.ai.agent.model.QueryIntent;

public interface FinanceAgent {
    QueryIntent supportedIntent();
    String respond(String enrichedQuery, String conversationId);
}