package io.github.michalmszczepanski.project22_card_game_1.admin;

import io.github.michalmszczepanski.project22_card_game_1.card.CardType;

import java.time.Instant;

/** Flattened, read-only row for the "Zgłoszone karty" admin queue — same spirit as AdminDashboardView. */
public record CardReportRow(Long reportId, Long cardId, String cardText, CardType cardType, String packName,
                             String reporterNickname, String note, Instant reportedAt) {
}
