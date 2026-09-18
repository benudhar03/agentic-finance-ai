package com.finance.ai.agent.impl;

import com.finance.ai.agent.FinanceAgent;
import com.finance.ai.agent.model.QueryIntent;
import org.springframework.stereotype.Component;

/**
 * STUB. Real implementation requires two things not yet in place:
 *   1. An authenticated user principal (userId currently comes from the
 *      request body, unverified — parked earlier this session).
 *   2. A tool/function that can actually fetch account data on the user's
 *      behalf, scoped to that authenticated identity.
 * Until both exist, this agent deliberately returns a fixed, honest
 * response rather than fabricating account details or guessing.
 */
@Component
public class AccountSpecificActionAgent implements FinanceAgent {

    private static final String NOT_YET_AVAILABLE =
            "I'm not yet able to access specific account details or perform account actions. " +
            "This capability is under development. Please use your account portal directly for now.";

    @Override
    public QueryIntent supportedIntent() {
        return QueryIntent.ACCOUNT_SPECIFIC_ACTION;
    }

    @Override
    public String respond(String enrichedQuery, String conversationId) {
        return NOT_YET_AVAILABLE;
    }
}