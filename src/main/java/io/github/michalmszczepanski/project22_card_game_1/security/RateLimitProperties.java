package io.github.michalmszczepanski.project22_card_game_1.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** See RateLimitFilter — application.yaml's "rate-limit" section. */
@ConfigurationProperties(prefix = "rate-limit")
public record RateLimitProperties(int capacity, int refillTokens, int refillPeriodSeconds) {
}
