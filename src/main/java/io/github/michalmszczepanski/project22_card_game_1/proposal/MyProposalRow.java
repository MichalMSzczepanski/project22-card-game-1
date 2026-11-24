package io.github.michalmszczepanski.project22_card_game_1.proposal;

import io.github.michalmszczepanski.project22_card_game_1.card.CardType;

/** Flattened row for the player's own "Moje propozycje kart" panel. */
public record MyProposalRow(Long id, String text, CardType type, CardProposalStatus status, String reviewNote) {
}
