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

    public Submission(Player author, Card card) {
        this.id = UUID.randomUUID().toString();
        this.author = author;
        this.card = card;
    }
}
