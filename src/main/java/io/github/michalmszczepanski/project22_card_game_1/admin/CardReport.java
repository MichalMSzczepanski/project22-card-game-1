package io.github.michalmszczepanski.project22_card_game_1.admin;

import io.github.michalmszczepanski.project22_card_game_1.card.Card;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * A player's "Zgłoś kartę" flag (README TODO "Zgłoszone karty") — queued for the operator at
 * {@code /admin/reports}. No link to {@link io.github.michalmszczepanski.project22_card_game_1.game.Player}
 * on purpose: the reporter's nickname is just a snapshot for the admin to read, not a tracked
 * identity, and the {@code game} package stays a one-way dependency of {@code card}/{@code admin},
 * never the other way round.
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CardReport {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "card_id")
    private Card card;

    private String reporterNickname;

    /** Optional free-text reason the reporting player gave — null/blank if they left it empty. */
    private String note;

    private Instant reportedAt;

    public CardReport(Card card, String reporterNickname, String note) {
        this.card = card;
        this.reporterNickname = reporterNickname;
        this.note = note;
        this.reportedAt = Instant.now();
    }
}
