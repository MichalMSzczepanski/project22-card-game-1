package io.github.michalmszczepanski.project22_card_game_1.admin;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Bootstrap credentials for the one operator account (see {@link OperatorSeeder}) — blank by
 * default (application.yaml), set only via env vars on a real deployment (DEPLOY.md), never in
 * the repo. Blank means "don't create an account" — there is deliberately no other way to get one.
 */
@ConfigurationProperties(prefix = "operator")
public record OperatorProperties(String bootstrapUsername, String bootstrapPassword) {
}
