package io.github.michalmszczepanski.project22_card_game_1.game;

import io.github.michalmszczepanski.project22_card_game_1.card.Card;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

/**
 * One player's anonymous answer to the round's black card.
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Submission {

    @Id
    private String id;

    @ManyToOne
    @JoinColumn(name = "round_id")
    @Setter
    private Round round;

    @ManyToOne
    @JoinColumn(name = "author_id")
    private Player author;

    @ManyToOne
    @JoinColumn(name = "card_id")
    private Card card;

    /** Set instead of {@code card} when this is a "blank card" — the player's own typed answer. */
    private String customText;

    public Submission(Player author, Card card) {
        this.id = UUID.randomUUID().toString();
        this.author = author;
        this.card = card;
    }

    public Submission(Player author, String customText) {
        this.id = UUID.randomUUID().toString();
        this.author = author;
        this.customText = customText;
    }

    /** The text to show for this submission — the custom typed answer, or the card's own text. */
    public String text() {
        return customText != null ? customText : card.getText();
    }
}
