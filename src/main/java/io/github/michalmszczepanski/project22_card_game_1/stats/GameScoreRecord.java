package io.github.michalmszczepanski.project22_card_game_1.stats;

import io.github.michalmszczepanski.project22_card_game_1.user.AppUser;
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
 * One logged-in player's result in one finished game. Guests (no account)
 * aren't recorded — this is the account perk that makes /stats possible.
 */
@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class GameScoreRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Getter
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "user_id")
    @Getter
    private AppUser user;

    @Getter
    private String gameId;
    @Getter
    private int points;
    @Getter
    private boolean won;
    private Instant playedAt;

    public GameScoreRecord(AppUser user, String gameId, int points, boolean won) {
        this.user = user;
        this.gameId = gameId;
        this.points = points;
        this.won = won;
        this.playedAt = Instant.now();
    }
}
