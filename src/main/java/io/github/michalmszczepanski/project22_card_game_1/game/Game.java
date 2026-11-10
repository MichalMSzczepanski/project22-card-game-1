package io.github.michalmszczepanski.project22_card_game_1.game;

import io.github.michalmszczepanski.project22_card_game_1.card.CardPack;
import jakarta.persistence.CascadeType;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * The whole aggregate (players, current round, used-card tracking) is small and
 * bounded (max 5 players), so everything here is fetched eagerly on purpose —
 * this app runs with spring.jpa.open-in-view=false, so a lazy collection touched
 * after the request's transaction closes would blow up with
 * LazyInitializationException. Eager-loading this little graph in one go is
 * simpler and cheap enough to not matter.
 */
@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Game {

    @Id
    @Getter
    private String id;

    @Enumerated(EnumType.STRING)
    @Getter
    @Setter
    private GameStatus status = GameStatus.LOBBY;

    private int leaderIndex = -1;

    @Getter
    @Setter
    private boolean familyMode = false;

    @OneToOne(cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @JoinColumn(name = "current_round_id")
    @Getter
    @Setter
    private Round currentRound;

    @OneToMany(mappedBy = "game", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @OrderBy("joinOrder ASC")
    @Getter
    private final List<Player> players = new ArrayList<>();

    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(name = "game_active_packs",
            joinColumns = @JoinColumn(name = "game_id"),
            inverseJoinColumns = @JoinColumn(name = "pack_id"))
    @Getter
    @Setter
    private List<CardPack> activePacks = new ArrayList<>();

    /**
     * Card ids already drawn this game, so the same card isn't dealt twice before the pool is
     * exhausted. LAZY on purpose: with EAGER, committing a new {@code currentRound} (a
     * {@code @OneToOne(cascade=ALL, orphanRemoval=true)} replacement) in the same flush as
     * additions to this collection silently drops all but one of them — see
     * GameServiceMultiTransactionTest for the repro and GameService.dealUpToHandSize for where
     * it's read (always from inside an active transaction, so laziness is never an issue here).
     */
    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "game_used_question_cards", joinColumns = @JoinColumn(name = "game_id"))
    @Column(name = "card_id")
    @Getter
    private final Set<Long> usedQuestionCardIds = new HashSet<>();

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "game_used_answer_cards", joinColumns = @JoinColumn(name = "game_id"))
    @Column(name = "card_id")
    @Getter
    private final Set<Long> usedAnswerCardIds = new HashSet<>();

    /** Nickname/timestamp of the most recent departure, so everyone else can be shown a brief banner. */
    @Getter
    private String lastLeftNickname;
    @Getter
    private Instant lastLeftAt;

    @Version
    private Long version;

    public Game(String id) {
        this.id = id;
    }

    /** Adds a player and wires up the bidirectional FK + join-order used for turn rotation. */
    public void addPlayer(Player player) {
        player.setGame(this);
        player.setJoinOrder(players.size());
        players.add(player);
    }

    public boolean isFull(int maxPlayers) {
        return activePlayers().size() >= maxPlayers;
    }

    public Optional<Player> findPlayer(String playerId) {
        return players.stream().filter(p -> p.getId().equals(playerId)).findFirst();
    }

    /** Players still in the game — everyone but those who left (explicitly or by going quiet). */
    public List<Player> activePlayers() {
        return players.stream().filter(p -> !p.isLeft()).toList();
    }

    /** Active players other than the current round's leader, in join order. */
    public List<Player> answerersInOrder() {
        return activePlayers().stream()
                .filter(p -> currentRound == null || !p.getId().equals(currentRound.getLeader().getId()))
                .toList();
    }

    /** Rotates to the next active player, skipping anyone who has left. */
    public Player nextLeader() {
        int attempts = 0;
        do {
            leaderIndex = (leaderIndex + 1) % players.size();
            attempts++;
        } while (players.get(leaderIndex).isLeft() && attempts < players.size());
        return players.get(leaderIndex);
    }

    public void recordDeparture(String nickname) {
        this.lastLeftNickname = nickname;
        this.lastLeftAt = Instant.now();
    }
}
