package com.finance.ai.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.ExponentialBackOffWithMaxRetries;

@Configuration
public class KafkaErrorHandlingConfig {

    @Bean
    public DefaultErrorHandler kafkaErrorHandler(
            KafkaTemplate<String, Object> kafkaTemplate,
            @Value("${app.ingestion.max-retry-attempts:4}") int maxAttempts,
            @Value("${app.ingestion.retry-initial-delay-ms:1000}") long initialDelayMs,
            @Value("${app.ingestion.retry-multiplier:2.0}") double multiplier) {

        var recoverer = new DeadLetterPublishingRecoverer(kafkaTemplate);

        // maxRetries excludes the original invocation — pass (maxAttempts - 1)
        // so "4 attempts" here matches the same total-attempts meaning as
        // @RetryableTopic's `attempts` attribute would have given you.
        var backOff = new ExponentialBackOffWithMaxRetries(maxAttempts - 1);
        backOff.setInitialInterval(initialDelayMs);
        backOff.setMultiplier(multiplier);

        return new DefaultErrorHandler(recoverer, backOff);
    }
}