package io.github.michalmszczepanski.project22_card_game_1.user;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Account/registration rules, externalized so they can change without a recompile — see application.yaml's "user" section. */
@ConfigurationProperties(prefix = "user")
public record UserProperties(
        int minUsernameLength,
        int maxUsernameLength,
        int minPasswordLength
) {
}
