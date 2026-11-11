package io.github.michalmszczepanski.project22_card_game_1.game;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/** Game rules, externalized so they can change without a recompile — see application.yaml's "game" section. */
@ConfigurationProperties(prefix = "game")
public record GameProperties(
        Players players,
        int handSize,
        // Ceiling the host can pick at game creation (Game.totalRounds) — not a fixed round count anymore.
        int maxTotalRounds,
        Timeouts timeouts,
        int maxNicknameLength,
        int codeLength,
        int pollIntervalSeconds,
        Scoring scoring
) {

    public record Players(int min, int max) {
    }

    public record Timeouts(int answerSeconds, int rankingSeconds, int revealSeconds, int disconnectSeconds, int leaveBannerSeconds) {
    }

    /** podiumSize is the ceiling — the actual podium is also capped by how many submissions a round has. */
    public record Scoring(int podiumSize, List<Integer> podiumPoints) {
    }
}
