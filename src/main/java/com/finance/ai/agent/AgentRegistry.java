package com.finance.ai.agent;

import com.finance.ai.agent.model.QueryIntent;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Component
public class AgentRegistry {

    private final Map<QueryIntent, FinanceAgent> agentsByIntent;

    public AgentRegistry(List<FinanceAgent> agents) {
        this.agentsByIntent = new EnumMap<>(QueryIntent.class);
        for (FinanceAgent agent : agents) {
            FinanceAgent existing = agentsByIntent.put(agent.supportedIntent(), agent);
            if (existing != null) {
                throw new IllegalStateException(
                        "Multiple agents registered for intent " + agent.supportedIntent() +
                        ": " + existing.getClass().getSimpleName() + " and " + agent.getClass().getSimpleName());
            }
        }
    }

    public Optional<FinanceAgent> find(QueryIntent intent) {
        return Optional.ofNullable(agentsByIntent.get(intent));
    }
}