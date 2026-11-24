package io.github.michalmszczepanski.project22_card_game_1.admin;

import io.github.michalmszczepanski.project22_card_game_1.game.GameStatus;

import java.util.List;

/** Flattened, read-only snapshot for the operator dashboard — same spirit as GameViewModel. */
public record AdminDashboardView(long activeGames, long finishedGames, List<GameRow> liveGames) {

    public record GameRow(String id, GameStatus status, int roundNumber, int totalRounds,
                           int playerCount, boolean hasAdmin) {
    }
}
