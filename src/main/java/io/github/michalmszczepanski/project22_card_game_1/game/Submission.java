package io.github.michalmszczepanski.project22_card_game_1.game;

import io.github.michalmszczepanski.project22_card_game_1.card.Card;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;

import java.util.UUID;

/**
 * One player's anonymous answer to the round's black card.
 */
@Entity
public class Submission {

    @Id
    private String id;

    @ManyToOne
    @JoinColumn(name = "round_id")
    private Round round;

    @ManyToOne
    @JoinColumn(name = "author_id")
    private Player author;

    @ManyToOne
    @JoinColumn(name = "card_id")
    private Card card;

    protected Submission() {
    }

    public Submission(Player author, Card card) {
        this.id = UUID.randomUUID().toString();
        this.author = author;
        this.card = card;
    }

    public String getId() {
        return id;
    }

    public Round getRound() {
        return round;
    }

    public void setRound(Round round) {
        this.round = round;
    }

    public Player getAuthor() {
        return author;
    }

    public Card getCard() {
        return card;
    }
}
