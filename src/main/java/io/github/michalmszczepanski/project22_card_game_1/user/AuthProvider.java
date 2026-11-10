package io.github.michalmszczepanski.project22_card_game_1.user;

/**
 * Where a user's identity comes from. GOOGLE is wired up; FACEBOOK is listed so
 * the same OAuth2 code path (see CustomOAuth2UserService) picks it up the
 * moment it's registered in application.yaml — no new code needed.
 */
public enum AuthProvider {
    LOCAL,
    GOOGLE,
    FACEBOOK
}
