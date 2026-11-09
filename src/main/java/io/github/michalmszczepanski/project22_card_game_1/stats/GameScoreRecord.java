package io.github.michalmszczepanski.project22_card_game_1.stats;

import io.github.michalmszczepanski.project22_card_game_1.user.AppUser;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;

import java.time.Instant;

/**
 * One logged-in player's result in one finished game. Guests (no account)
 * aren't recorded — this is the account perk that makes /stats possible.
 */
@Entity
public class GameScoreRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "user_id")
    private AppUser user;

    private String gameId;
    private int points;
    private boolean won;
    private Instant playedAt;

    protected GameScoreRecord() {
    }

    public GameScoreRecord(AppUser user, String gameId, int points, boolean won) {
        this.user = user;
        this.gameId = gameId;
        this.points = points;
        this.won = won;
        this.playedAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public AppUser getUser() {
        return user;
    }

    public String getGameId() {
        return gameId;
    }

    public int getPoints() {
        return points;
    }

    public boolean isWon() {
        return won;
    }

    public Instant getPlayedAt() {
        return playedAt;
    }
}
