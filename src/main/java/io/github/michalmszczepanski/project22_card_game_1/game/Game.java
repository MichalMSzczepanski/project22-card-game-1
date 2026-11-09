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
public class Game {

    public static final int MAX_PLAYERS = 5;
    public static final int MIN_PLAYERS_TO_START = 3;
    public static final int TOTAL_ROUNDS = 20;
    public static final int HAND_SIZE = 7;
    public static final int ANSWER_TIMEOUT_SECONDS = 60;
    public static final int RANKING_TIMEOUT_SECONDS = 45;
    /** A player who isn't currently expected to act and hasn't polled in this long is treated as gone (closed tab). */
    public static final int DISCONNECT_TIMEOUT_SECONDS = 15;
    /** How long the "X opuścił grę" banner stays visible to everyone else after a departure. */
    public static final int LEAVE_BANNER_SECONDS = 10;

    @Id
    private String id;

    @Enumerated(EnumType.STRING)
    private GameStatus status = GameStatus.LOBBY;

    private int leaderIndex = -1;
    private boolean familyMode = false;

    @OneToOne(cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @JoinColumn(name = "current_round_id")
    private Round currentRound;

    @OneToMany(mappedBy = "game", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @OrderBy("joinOrder ASC")
    private List<Player> players = new ArrayList<>();

    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(name = "game_active_packs",
            joinColumns = @JoinColumn(name = "game_id"),
            inverseJoinColumns = @JoinColumn(name = "pack_id"))
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
    private Set<Long> usedQuestionCardIds = new HashSet<>();

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "game_used_answer_cards", joinColumns = @JoinColumn(name = "game_id"))
    @Column(name = "card_id")
    private Set<Long> usedAnswerCardIds = new HashSet<>();

    /** Nickname/timestamp of the most recent departure, so everyone else can be shown a brief banner. */
    private String lastLeftNickname;
    private Instant lastLeftAt;

    @Version
    private Long version;

    protected Game() {
    }

    public Game(String id) {
        this.id = id;
    }

    public String getId() {
        return id;
    }

    public List<Player> getPlayers() {
        return players;
    }

    /** Adds a player and wires up the bidirectional FK + join-order used for turn rotation. */
    public void addPlayer(Player player) {
        player.setGame(this);
        player.setJoinOrder(players.size());
        players.add(player);
    }

    public GameStatus getStatus() {
        return status;
    }

    public void setStatus(GameStatus status) {
        this.status = status;
    }

    public Round getCurrentRound() {
        return currentRound;
    }

    public void setCurrentRound(Round currentRound) {
        this.currentRound = currentRound;
    }

    public List<CardPack> getActivePacks() {
        return activePacks;
    }

    public void setActivePacks(List<CardPack> activePacks) {
        this.activePacks = activePacks;
    }

    public boolean isFamilyMode() {
        return familyMode;
    }

    public void setFamilyMode(boolean familyMode) {
        this.familyMode = familyMode;
    }

    public Set<Long> getUsedQuestionCardIds() {
        return usedQuestionCardIds;
    }

    public Set<Long> getUsedAnswerCardIds() {
        return usedAnswerCardIds;
    }

    public boolean isFull() {
        return activePlayers().size() >= MAX_PLAYERS;
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

    public String getLastLeftNickname() {
        return lastLeftNickname;
    }

    public Instant getLastLeftAt() {
        return lastLeftAt;
    }

    public void recordDeparture(String nickname) {
        this.lastLeftNickname = nickname;
        this.lastLeftAt = Instant.now();
    }
}
