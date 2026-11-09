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

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
public class Player {

    @Id
    private String id;

    private String nickname;
    private int points;

    /** Set when the joining session was authenticated, so results can feed /stats. Null for guests. */
    private Long appUserId;

    /** Join sequence within the game — drives deterministic leader rotation / turn order. */
    private int joinOrder;

    /**
     * True once the player has left (explicitly, or pruned as disconnected). The row is kept
     * rather than deleted once the game has started, since past Submissions still reference it —
     * left players are just filtered out of everything "current" (scoreboard, turn order, leader rotation).
     */
    @Column(name = "has_left")
    private boolean left = false;

    /** Bumped on every request this player's own browser makes, so a stale value means a closed tab. */
    private Instant lastSeenAt = Instant.now();

    @ManyToOne
    @JoinColumn(name = "game_id")
    private Game game;

    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(name = "player_hand",
            joinColumns = @JoinColumn(name = "player_id"),
            inverseJoinColumns = @JoinColumn(name = "card_id"))
    private List<Card> hand = new ArrayList<>();

    protected Player() {
    }

    public Player(String nickname) {
        this(nickname, null);
    }

    public Player(String nickname, Long appUserId) {
        this.id = UUID.randomUUID().toString();
        this.nickname = nickname;
        this.appUserId = appUserId;
    }

    public String getId() {
        return id;
    }

    public String getNickname() {
        return nickname;
    }

    public Long getAppUserId() {
        return appUserId;
    }

    public int getJoinOrder() {
        return joinOrder;
    }

    public void setJoinOrder(int joinOrder) {
        this.joinOrder = joinOrder;
    }

    public Game getGame() {
        return game;
    }

    public void setGame(Game game) {
        this.game = game;
    }

    public List<Card> getHand() {
        return hand;
    }

    public int getPoints() {
        return points;
    }

    public void addPoints(int amount) {
        this.points += amount;
    }

    public boolean removeFromHand(Card card) {
        return hand.remove(card);
    }

    public boolean isLeft() {
        return left;
    }

    public void markLeft() {
        this.left = true;
    }

    public Instant getLastSeenAt() {
        return lastSeenAt;
    }

    public void touch() {
        this.lastSeenAt = Instant.now();
    }

    public void setLastSeenAt(Instant lastSeenAt) {
        this.lastSeenAt = lastSeenAt;
    }
}
