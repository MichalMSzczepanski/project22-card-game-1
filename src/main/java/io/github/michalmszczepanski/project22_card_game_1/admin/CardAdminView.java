package io.github.michalmszczepanski.project22_card_game_1.admin;

import io.github.michalmszczepanski.project22_card_game_1.card.CardType;
import io.github.michalmszczepanski.project22_card_game_1.card.PackAudience;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** Flattened, read-only snapshot for the "Karty i pakiety" admin screen — same spirit as AdminDashboardView. */
public record CardAdminView(List<PackRow> packs, PackRow selectedPack, List<CardRow> cards) {

    public record PackRow(Long id, String name, boolean free, BigDecimal pricePln, PackAudience audience,
                           LocalDate availableFrom, LocalDate availableUntil, int cardCount) {
    }

    public record CardRow(Long id, String text, CardType type, boolean enabled) {
    }
}
