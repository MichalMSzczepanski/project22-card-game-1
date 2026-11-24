package io.github.michalmszczepanski.project22_card_game_1.game;

import io.github.michalmszczepanski.project22_card_game_1.card.Card;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Player {

    @Id
    @Getter
    private String id;

    @Getter
    private String nickname;
    @Getter
    private int points;

    /** Set when the joining session was authenticated, so results can feed /stats. Null for guests. */
    @Getter
    private Long appUserId;

    /** Join sequence within the game — drives deterministic leader rotation / turn order. */
    @Setter
    private int joinOrder;

    /**
     * Null if joined in the LOBBY (the normal case). Otherwise the number of the round that was
     * current when this player joined mid-game — they sit out exactly that one round (no hand yet,
     * excluded from {@link Game#answerersInOrder()}) and join properly from the next one. Never
     * cleared afterwards: once the round number moves past it, the comparison just stops matching.
     */
    @Getter
    @Setter
    private Integer joinedAtRound;

    /**
     * True once the player has left (explicitly, or pruned as disconnected). The row is kept
     * rather than deleted once the game has started, since past Submissions still reference it —
     * left players are just filtered out of everything "current" (scoreboard, turn order, leader rotation).
     */
    @Column(name = "has_left")
    @Getter
    private boolean left = false;

    /** True once this player has played their one "blank card" (own typed answer) for the whole game. */
    @Getter
    private boolean usedBlankCard = false;

    /** True once this player has swapped their whole hand for a fresh one, for the whole game. */
    @Getter
    private boolean usedHandSwap = false;

    /** Set once a rematch is created from this player's (finished) game — their own row in the new one. */
    @Getter
    @Setter
    private String nextPlayerId;

    /** Bumped on every request this player's own browser makes, so a stale value means a closed tab. */
    @Getter
    @Setter
    private Instant lastSeenAt = Instant.now();

    @ManyToOne
    @JoinColumn(name = "game_id")
    @Getter
    @Setter
    private Game game;

    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(name = "player_hand",
            joinColumns = @JoinColumn(name = "player_id"),
            inverseJoinColumns = @JoinColumn(name = "card_id"))
    @Getter
    private final List<Card> hand = new ArrayList<>();

    public Player(String nickname, Long appUserId) {
        this.id = UUID.randomUUID().toString();
        this.nickname = nickname;
        this.appUserId = appUserId;
    }

    public void addPoints(int amount) {
        this.points += amount;
    }

    public boolean removeFromHand(Card card) {
        return hand.remove(card);
    }

    public void markLeft() {
        this.left = true;
    }

    public void markBlankCardUsed() {
        this.usedBlankCard = true;
    }

    public void markHandSwapUsed() {
        this.usedHandSwap = true;
    }

    public void touch() {
        this.lastSeenAt = Instant.now();
    }
}
