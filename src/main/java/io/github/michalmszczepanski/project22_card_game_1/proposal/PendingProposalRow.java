package io.github.michalmszczepanski.project22_card_game_1.proposal;

import io.github.michalmszczepanski.project22_card_game_1.card.CardType;

import java.time.Instant;

/** Flattened row for the operator's "/admin/proposals" review queue. */
public record PendingProposalRow(Long id, String proposerUsername, String text, CardType type, Instant createdAt) {
}
