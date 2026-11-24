package io.github.michalmszczepanski.project22_card_game_1.game;

import io.github.michalmszczepanski.project22_card_game_1.card.Card;
import io.github.michalmszczepanski.project22_card_game_1.card.CardPack;
import io.github.michalmszczepanski.project22_card_game_1.card.CardRepository;
import io.github.michalmszczepanski.project22_card_game_1.card.CardType;
import io.github.michalmszczepanski.project22_card_game_1.card.PackAudience;
import io.github.michalmszczepanski.project22_card_game_1.settings.AppSettingsService;
import io.github.michalmszczepanski.project22_card_game_1.stats.GameScoreRecord;
import io.github.michalmszczepanski.project22_card_game_1.stats.GameScoreRecordRepository;
import io.github.michalmszczepanski.project22_card_game_1.user.AppUserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@Transactional
@RequiredArgsConstructor
@Slf4j
public class GameService {

    private final Random random = new Random();

    private final GameRepository gameRepository;
    private final GameCodeGenerator gameCodeGenerator;
    private final CardRepository cardRepository;
    private final EasterEggService easterEggService;
    private final GameScoreRecordRepository gameScoreRecordRepository;
    private final AppUserRepository appUserRepository;
    private final GameProperties gameProperties;
    private final AppSettingsService appSettingsService;

    @Transactional(readOnly = true)
    public Game getGame(String gameId) {
        return requireGame(gameId);
    }

    public Game createGame(List<CardPack> activePacks, int totalRounds) {
        return createGame(activePacks, totalRounds, null, null, null);
    }

    public Game createGame(List<CardPack> activePacks, int totalRounds, Integer targetScore,
                            Integer answerSecondsOverride, Integer rankingSecondsOverride) {
        if (appSettingsService.isMaintenanceMode()) {
            throw new InvalidGameActionException(
                    "Serwis jest tymczasowo w trybie przerwy technicznej — nowe gry są wstrzymane.");
        }
        if (totalRounds < 1 || totalRounds > gameProperties.maxTotalRounds()) {
            throw new InvalidGameActionException("Liczba rund musi być między 1 i " + gameProperties.maxTotalRounds() + ".");
        }
        if (targetScore != null && (targetScore < 1 || targetScore > gameProperties.maxTargetScore())) {
            throw new InvalidGameActionException("Próg punktowy musi być między 1 i " + gameProperties.maxTargetScore() + ".");
        }
        validateCustomTimeout(answerSecondsOverride, "odpowiedzi");
        validateCustomTimeout(rankingSecondsOverride, "rankingu");
        int maxActiveGames = appSettingsService.effectiveMaxActiveGames(gameProperties.maxActiveGames());
        if (gameRepository.countByStatusNot(GameStatus.FINISHED) >= maxActiveGames) {
            throw new InvalidGameActionException("Za dużo aktywnych gier w tej chwili — spróbuj ponownie za chwilę.");
        }
        Game game = new Game(gameCodeGenerator.generate(), totalRounds);
        game.setActivePacks(activePacks);
        game.setTargetScore(targetScore);
        game.setAnswerSecondsOverride(answerSecondsOverride);
        game.setRankingSecondsOverride(rankingSecondsOverride);
        game = gameRepository.save(game);
        log.info("Nowa gra {} (pakiety: {}, rund: {}, próg: {})", game.getId(),
                activePacks.stream().map(CardPack::getName).toList(), totalRounds, targetScore);
        return game;
    }

    private void validateCustomTimeout(Integer seconds, String label) {
        if (seconds == null) {
            return;
        }
        int min = gameProperties.timeouts().minCustomSeconds();
        int max = gameProperties.timeouts().maxCustomSeconds();
        if (seconds < min || seconds > max) {
            throw new InvalidGameActionException("Czas na " + label + " musi być między " + min + " i " + max + " s.");
        }
    }

    public Player join(String gameId, String nickname) {
        return join(gameId, nickname, null);
    }

    public Player join(String gameId, String nickname, Long appUserId) {
        Game game = requireGame(gameId);
        if (game.getStatus() == GameStatus.FINISHED) {
            throw new InvalidGameActionException("Gra już się zakończyła, nie można dołączyć.");
        }
        if (game.isFull(gameProperties.players().max())) {
            throw new InvalidGameActionException("Pokój jest pełny (max " + gameProperties.players().max() + " graczy).");
        }

        String trimmed = nickname == null ? "" : nickname.trim();
        if (trimmed.isEmpty() || trimmed.length() > gameProperties.maxNicknameLength()) {
            throw new InvalidGameActionException("Nick musi mieć 1-" + gameProperties.maxNicknameLength() + " znaków.");
        }

        Player player = new Player(trimmed, appUserId);
        // Mid-game join (README TODO): sits out the round in progress — no hand, excluded from
        // Game.answerersInOrder() — and joins properly (dealt a hand) from the next one.
        if (game.getStatus() != GameStatus.LOBBY && game.getCurrentRound() != null) {
            player.setJoinedAtRound(game.getCurrentRound().getNumber());
        }
        game.addPlayer(player);
        log.debug("Gracz {} dołączył do gry {}", trimmed, gameId);
        return player;
    }

    /**
     * Creates a game and immediately joins its creator as the first player, as one transaction —
     * if the nickname is invalid, the whole thing rolls back (no orphaned empty game left in the DB).
     */
    public Player createGameAndJoin(List<CardPack> activePacks, String nickname, Long appUserId, int totalRounds,
                                     Integer targetScore, Integer answerSecondsOverride, Integer rankingSecondsOverride) {
        Game game = createGame(activePacks, totalRounds, targetScore, answerSecondsOverride, rankingSecondsOverride);
        return join(game.getId(), nickname, appUserId);
    }

    public void startGame(String gameId, String playerId) {
        Game game = requireGame(gameId);
        if (game.getStatus() != GameStatus.LOBBY) {
            throw new InvalidGameActionException("Gra już wystartowała.");
        }
        requireAdmin(game, playerId);
        if (game.activePlayers().size() < gameProperties.players().min()) {
            throw new InvalidGameActionException("Potrzeba minimum " + gameProperties.players().min() + " graczy.");
        }

        for (Player player : game.activePlayers()) {
            dealUpToHandSize(game, player);
        }

        startRound(game);
        log.info("Gra {} wystartowała ({} graczy)", gameId, game.activePlayers().size());
    }

    public void submitAnswer(String gameId, String playerId, Long cardId) {
        Game game = requireGame(gameId);
        requireStatus(game, GameStatus.ANSWERING);
        Round round = game.getCurrentRound();
        Player player = requirePlayer(game, playerId);

        if (round.getLeader().getId().equals(playerId)) {
            throw new InvalidGameActionException("Prowadzący nie odpowiada w tej rundzie.");
        }
        if (round.hasSubmitted(player)) {
            throw new InvalidGameActionException("Już wysłałeś odpowiedź w tej rundzie.");
        }

        Card card = player.getHand().stream()
                .filter(c -> c.getId().equals(cardId))
                .findFirst()
                .orElseThrow(() -> new InvalidGameActionException("Tej karty nie masz na ręce."));

        player.removeFromHand(card);
        round.addSubmission(new Submission(player, card));
        log.debug("Gra {}: {} wysłał odpowiedź w rundzie {}", gameId, playerId, round.getNumber());

        advanceToRankingIfComplete(game, round);
    }

    /** "Pusta karta" (README TODO) — raz na całą grę, gracz wpisuje własną odpowiedź zamiast karty z ręki. */
    public void submitBlankAnswer(String gameId, String playerId, String text) {
        if (!appSettingsService.isBlankCardEnabled()) {
            throw new InvalidGameActionException("Pusta karta jest tymczasowo wyłączona przez administratora.");
        }
        Game game = requireGame(gameId);
        requireStatus(game, GameStatus.ANSWERING);
        Round round = game.getCurrentRound();
        Player player = requirePlayer(game, playerId);

        if (round.getLeader().getId().equals(playerId)) {
            throw new InvalidGameActionException("Prowadzący nie odpowiada w tej rundzie.");
        }
        if (round.hasSubmitted(player)) {
            throw new InvalidGameActionException("Już wysłałeś odpowiedź w tej rundzie.");
        }
        if (player.isUsedBlankCard()) {
            throw new InvalidGameActionException("Pustą kartę możesz zagrać tylko raz na grę.");
        }
        String trimmed = text == null ? "" : text.trim();
        if (trimmed.isEmpty() || trimmed.length() > gameProperties.maxBlankCardLength()) {
            throw new InvalidGameActionException(
                    "Odpowiedź musi mieć 1-" + gameProperties.maxBlankCardLength() + " znaków.");
        }

        player.markBlankCardUsed();
        round.addSubmission(new Submission(player, trimmed));
        log.debug("Gra {}: {} wysłał pustą kartę w rundzie {}", gameId, playerId, round.getNumber());

        advanceToRankingIfComplete(game, round);
    }

    /** "Wymiana ręki" (README TODO) — raz na całą grę, gracz zrzuca całą rękę i dobiera nową. */
    public void swapHand(String gameId, String playerId) {
        Game game = requireGame(gameId);
        requireStatus(game, GameStatus.ANSWERING);
        Round round = game.getCurrentRound();
        Player player = requirePlayer(game, playerId);

        if (round.getLeader().getId().equals(playerId)) {
            throw new InvalidGameActionException("Prowadzący nie odpowiada w tej rundzie.");
        }
        if (round.hasSubmitted(player)) {
            throw new InvalidGameActionException("Już wysłałeś odpowiedź w tej rundzie.");
        }
        if (player.isUsedHandSwap()) {
            throw new InvalidGameActionException("Wymianę ręki możesz wykorzystać tylko raz na grę.");
        }

        player.markHandSwapUsed();
        List.copyOf(player.getHand()).forEach(player::removeFromHand);
        dealUpToHandSize(game, player);
        log.info("Gra {}: {} wymienił rękę", gameId, playerId);
    }

    private void advanceToRankingIfComplete(Game game, Round round) {
        List<Player> answerers = game.answerersInOrder();
        if (round.getSubmissions().size() == answerers.size()) {
            Collections.shuffle(round.getSubmissions());
            game.setStatus(GameStatus.RANKING);
            round.restartPhaseTimer();
            log.debug("Gra {}: runda {} przechodzi do ustalania kolejności", game.getId(), round.getNumber());
        }
    }

    /**
     * Only the round's leader ranks the submissions, from best (rank 1) to worst
     * (rank N, N being the number of submissions). Points are awarded per rank
     * in {@link #scoreRound}, not here.
     */
    public void submitRanking(String gameId, String playerId, String submissionId, int rank) {
        Game game = requireGame(gameId);
        requireStatus(game, GameStatus.RANKING);
        Round round = game.getCurrentRound();

        Player player = requirePlayer(game, playerId);
        if (!round.getLeader().getId().equals(player.getId())) {
            throw new InvalidGameActionException("Tylko prowadzący ustala kolejność w tej rundzie.");
        }

        Submission submission = round.findSubmission(submissionId)
                .orElseThrow(() -> new InvalidGameActionException("Nieznana odpowiedź."));

        if (round.hasRanked(submission)) {
            throw new InvalidGameActionException("Ta odpowiedź już ma przyznaną pozycję.");
        }
        if (rank < 1 || rank > round.podiumSize(gameProperties.scoring().podiumSize())) {
            throw new InvalidGameActionException("Nieprawidłowa pozycja.");
        }
        if (round.usedRanks().contains(rank)) {
            throw new InvalidGameActionException("Ta pozycja została już przyznana innej karcie.");
        }

        round.addRanking(new Ranking(submission, rank));
        log.debug("Gra {}: prowadzący przyznał pozycję {} odpowiedzi {}", gameId, rank, submissionId);

        if (round.hasFinishedRanking(gameProperties.scoring().podiumSize())) {
            reveal(game, round);
        }
    }

    private void reveal(Game game, Round round) {
        scoreRound(game, round);
        game.setStatus(GameStatus.REVEALED);
        round.restartPhaseTimer();
        log.info("Gra {}: runda {} zakończona, wyniki odkryte", game.getId(), round.getNumber());
    }

    /**
     * Admin-only (README TODO "Prowadzący znika w trakcie rundy"): reveals the round right now
     * with whatever submissions/rankings exist, instead of waiting out the answer/ranking
     * timeout — same end state as a timeout firing, just on the admin's own call. Covers a
     * leader who's gone quiet without making everyone else wait ~45s+ for the normal timeout.
     */
    public void forceRevealRound(String gameId, String playerId) {
        Game game = requireGame(gameId);
        requireAdmin(game, playerId);
        Round round = game.getCurrentRound();
        if (round == null || (game.getStatus() != GameStatus.ANSWERING && game.getStatus() != GameStatus.RANKING)) {
            throw new InvalidGameActionException("Nie ma teraz rundy do pominięcia.");
        }
        reveal(game, round);
        log.info("Gra {}: admin pominął rundę {}", gameId, round.getNumber());
    }

    /**
     * Keeps a stuck round from blocking the party forever. Called opportunistically
     * whenever anyone's browser polls the game (no background scheduler needed):
     * past the answering deadline, missing players get a random card auto-played;
     * past the guessing deadline, the round reveals with whatever guesses exist.
     */
    public void checkTimeout(String gameId) {
        Game game = requireGame(gameId);
        Round round = game.getCurrentRound();
        if (round == null) {
            return;
        }

        if (game.getStatus() == GameStatus.ANSWERING
                && Instant.now().isAfter(round.getPhaseStartedAt().plusSeconds(
                        game.effectiveAnswerSeconds(gameProperties.timeouts().answerSeconds())))) {
            for (Player player : game.answerersInOrder()) {
                if (!round.hasSubmitted(player) && !player.getHand().isEmpty()) {
                    Card card = player.getHand().get(random.nextInt(player.getHand().size()));
                    player.removeFromHand(card);
                    round.addSubmission(new Submission(player, card));
                    log.info("Gra {}: czas minął, {} dostał losową kartę za odpowiedź", gameId, player.getId());
                }
            }
            advanceToRankingIfComplete(game, round);
        } else if (game.getStatus() == GameStatus.RANKING
                && Instant.now().isAfter(round.getPhaseStartedAt().plusSeconds(
                        game.effectiveRankingSeconds(gameProperties.timeouts().rankingSeconds())))) {
            log.info("Gra {}: czas na ustalenie kolejności minął, odkrywam wyniki z tym co jest", gameId);
            reveal(game, round);
        }
    }

    /**
     * For {@code game.timeouts.reveal-seconds} after a round reveals, only that round's leader may
     * advance to the next one — after that window, anyone can (covers the leader going quiet).
     */
    public void nextRound(String gameId, String playerId) {
        Game game = requireGame(gameId);
        requireStatus(game, GameStatus.REVEALED);
        Round round = game.getCurrentRound();

        boolean isLeader = round.getLeader().getId().equals(playerId);
        boolean leaderWindowOpen = Instant.now().isBefore(
                round.getPhaseStartedAt().plusSeconds(gameProperties.timeouts().revealSeconds()));
        if (leaderWindowOpen) {
            if (!isLeader) {
                throw new InvalidGameActionException("Jeszcze chwila — najpierw może kliknąć prowadzący tej rundy.");
            }
        } else if (!isLeader && !game.isAdmin(playerId)) {
            // Leader priority always applies; once the window closes, admin takes over from "anyone" (README § 5).
            throw new InvalidGameActionException("Tylko admin może to zrobić.");
        }

        boolean reachedRoundCeiling = round.getNumber() >= game.getTotalRounds();
        boolean reachedTargetScore = game.getTargetScore() != null
                && game.activePlayers().stream().anyMatch(p -> p.getPoints() >= game.getTargetScore());
        if (reachedRoundCeiling || reachedTargetScore) {
            game.setStatus(GameStatus.FINISHED);
            recordFinalScores(game);
            easterEggService.onGameFinished(game);
            log.info("Gra {} zakończona po {} rundach{}", gameId, round.getNumber(),
                    reachedTargetScore ? " (osiągnięto próg " + game.getTargetScore() + " pkt)" : "");
            return;
        }

        for (Player player : game.activePlayers()) {
            dealUpToHandSize(game, player);
        }
        startRound(game);
    }

    private void startRound(Game game) {
        Card questionCard = drawCard(game, CardType.QUESTION);
        Player leader = game.nextLeader();
        int number = game.getCurrentRound() == null ? 1 : game.getCurrentRound().getNumber() + 1;
        easterEggService.captureMidpointStandings(game, number);
        game.setCurrentRound(new Round(number, leader, questionCard));
        game.setStatus(GameStatus.ANSWERING);
    }

    private void recordFinalScores(Game game) {
        int max = game.activePlayers().stream().mapToInt(Player::getPoints).max().orElse(0);
        for (Player player : game.activePlayers()) {
            if (player.getAppUserId() == null) {
                continue;
            }
            appUserRepository.findById(player.getAppUserId()).ifPresent(user ->
                    gameScoreRecordRepository.save(
                            new GameScoreRecord(user, game.getId(), player.getPoints(), player.getPoints() == max)));
        }
    }

    /**
     * Only the podium (top N, or fewer if there aren't N submissions, N = {@code game.scoring.podium-size})
     * scores — points per place from {@code game.scoring.podium-points}. An unranked submission (outside
     * the podium, or a round that timed out before the leader placed it) simply scores 0.
     */
    private void scoreRound(Game game, Round round) {
        for (Ranking ranking : round.getRankings()) {
            ranking.getSubmission().getAuthor().addPoints(Round.pointsForRank(ranking.getRank(), gameProperties.scoring().podiumPoints()));
        }
        easterEggService.afterRoundScored(game, round, gameProperties.scoring().podiumPoints());
    }

    private void dealUpToHandSize(Game game, Player player) {
        int needed = gameProperties.handSize() - player.getHand().size();
        player.getHand().addAll(drawCards(game, CardType.ANSWER, needed));
    }

    private Card drawCard(Game game, CardType type) {
        return drawCards(game, type, 1).get(0);
    }

    /**
     * Draws {@code count} cards the game hasn't already used, recycling (allowing repeats)
     * once the pool is exhausted. Queries the candidate pool once up front rather than once
     * per card — same result either way, just fewer round trips for a multi-card deal.
     */
    private List<Card> drawCards(Game game, CardType type, int count) {
        if (count <= 0) {
            return List.of();
        }
        List<Long> packIds = effectivePacks(game).stream().map(CardPack::getId).toList();
        List<Card> candidates = cardRepository.findByTypeAndEnabledTrueAndPack_IdIn(type, packIds);
        if (candidates.isEmpty()) {
            throw new InvalidGameActionException("Brak kart tego typu w wybranych pakietach.");
        }

        Set<Long> used = type == CardType.QUESTION ? game.getUsedQuestionCardIds() : game.getUsedAnswerCardIds();
        List<Card> drawn = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            List<Card> fresh = candidates.stream().filter(c -> !used.contains(c.getId())).toList();
            if (fresh.isEmpty()) {
                used.clear();
                fresh = candidates;
            }
            Card chosen = fresh.get(random.nextInt(fresh.size()));
            used.add(chosen.getId());
            drawn.add(chosen);
        }
        return drawn;
    }

    private List<CardPack> effectivePacks(Game game) {
        if (!game.isFamilyMode()) {
            return game.getActivePacks();
        }
        return game.getActivePacks().stream().filter(p -> p.getAudience() != PackAudience.ADULT).toList();
    }

    /**
     * One-click "babcia przyszła" switch: from now on, only family-safe packs are
     * drawn. Doesn't yank the card currently on the table, but immediately strips
     * any non-family cards already in hand and tops players back up.
     */
    public void setFamilyMode(String gameId, String playerId, boolean enabled) {
        Game game = requireGame(gameId);
        requireAdmin(game, playerId);
        game.setFamilyMode(enabled);
        log.info("Gra {}: tryb rodzinny {}", gameId, enabled ? "włączony" : "wyłączony");

        if (!enabled) {
            return;
        }

        for (Player player : game.activePlayers()) {
            List<Card> toDiscard = player.getHand().stream()
                    .filter(c -> c.getPack().getAudience() == PackAudience.ADULT)
                    .toList();
            toDiscard.forEach(player::removeFromHand);
            dealUpToHandSize(game, player);
        }
    }

    /** Ends the game early (any point — lobby, mid-round, whenever). Admin-only (README § 5). */
    public void endGame(String gameId, String playerId) {
        Game game = requireGame(gameId);
        requireAdmin(game, playerId);
        endGameInternal(game);
    }

    /** Operator override (panel admina aplikacji) — no in-game admin check, for /admin use only. */
    public void forceEndGame(String gameId) {
        Game game = requireGame(gameId);
        endGameInternal(game);
        log.info("Gra {} zakończona przez operatora", gameId);
    }

    private void endGameInternal(Game game) {
        if (game.getStatus() == GameStatus.FINISHED) {
            return;
        }
        boolean hasStarted = game.getCurrentRound() != null;
        game.setStatus(GameStatus.FINISHED);
        if (hasStarted) {
            recordFinalScores(game);
            easterEggService.onGameFinished(game);
        }
        log.info("Gra {} zakończona przedwcześnie", game.getId());
    }

    /**
     * "Zagraj jeszcze raz" (README TODO) — admin-only, after FINISHED. Creates a fresh game with the
     * same (still-active) players and packs, no re-typing the code. Returns the admin's own row in
     * the new game, so the controller can drop them straight into it. Idempotent: a repeated click
     * (e.g. a slow double-tap) reuses the already-created rematch instead of spawning another one —
     * other players get there the same way, via {@link Player#getNextPlayerId()} once their own
     * browser polls the finished game again.
     */
    public Player rematch(String gameId, String adminPlayerId) {
        Game game = requireGame(gameId);
        requireAdmin(game, adminPlayerId);
        if (game.getStatus() != GameStatus.FINISHED) {
            throw new InvalidGameActionException("Grę można powtórzyć tylko po jej zakończeniu.");
        }

        if (game.getNextGameId() == null) {
            Game newGame = createGame(game.getActivePacks(), game.getTotalRounds(), game.getTargetScore(),
                    game.getAnswerSecondsOverride(), game.getRankingSecondsOverride());
            for (Player oldPlayer : game.activePlayers()) {
                Player newPlayer = join(newGame.getId(), oldPlayer.getNickname(), oldPlayer.getAppUserId());
                oldPlayer.setNextPlayerId(newPlayer.getId());
            }
            newGame.setAdminPlayerId(game.findPlayer(adminPlayerId).orElseThrow().getNextPlayerId());
            game.setNextGameId(newGame.getId());
            log.info("Gra {}: zagrano jeszcze raz jako {}", gameId, newGame.getId());
        }

        String newPlayerId = game.findPlayer(adminPlayerId).orElseThrow().getNextPlayerId();
        return requireGame(game.getNextGameId()).findPlayer(newPlayerId).orElseThrow();
    }

    /** Explicit "Opuść grę" — the player clicked the button. */
    public void leave(String gameId, String playerId) {
        Game game = requireGame(gameId);
        Player player = requirePlayer(game, playerId);
        removePlayer(game, player, false);
    }

    /** Admin-only (README § 5): removes someone else, same end state as them leaving voluntarily. */
    public void kickPlayer(String gameId, String adminPlayerId, String targetPlayerId) {
        Game game = requireGame(gameId);
        requireAdmin(game, adminPlayerId);
        if (adminPlayerId.equals(targetPlayerId)) {
            throw new InvalidGameActionException("Nie możesz wyrzucić samego siebie.");
        }
        Player target = requirePlayer(game, targetPlayerId);
        game.setLastKickedPlayerId(targetPlayerId);
        removePlayer(game, target, true);
        log.info("Gra {}: admin wyrzucił {}", gameId, target.getNickname());
    }

    /** Admin voluntarily hands the role to someone else, without leaving the game (README § 5). */
    public void transferAdmin(String gameId, String adminPlayerId, String targetPlayerId) {
        Game game = requireGame(gameId);
        requireAdmin(game, adminPlayerId);
        if (adminPlayerId.equals(targetPlayerId)) {
            throw new InvalidGameActionException("Już jesteś adminem.");
        }
        Player target = requirePlayer(game, targetPlayerId);
        game.setAdminPlayerId(target.getId());
        log.info("Gra {}: admin przekazał rolę {}", gameId, target.getNickname());
    }

    /**
     * "Przejmij admina" (README § 5) — only works while the game has none; whoever claims it
     * first wins. A genuine dead-heat is caught by Game's optimistic lock instead (the generic
     * "ktoś inny coś zmienił" message in GameController), not this explicit check.
     */
    public void takeOverAdmin(String gameId, String playerId) {
        Game game = requireGame(gameId);
        requirePlayer(game, playerId);
        if (game.hasAdmin()) {
            throw new InvalidGameActionException("Ktoś właśnie przejął admina.");
        }
        game.setAdminPlayerId(playerId);
        log.info("Gra {}: {} przejął rolę admina", gameId, playerId);
    }

    /** Bumps this player's own "last seen" clock — called on every request their browser makes. */
    public void touch(String gameId, String playerId) {
        if (playerId == null) {
            return;
        }
        Game game = requireGame(gameId);
        game.findPlayer(playerId).filter(p -> !p.isLeft()).ifPresent(Player::touch);
    }

    /**
     * Removes anyone who's gone quiet for too long — the stand-in for "closed the browser tab",
     * since there's no WebSocket/JS to tell us directly. A player currently expected to act
     * (answering, or the leader still ranking) is exempt: their screen doesn't auto-refresh while
     * waiting on them, so silence from them is normal, not a disconnect — the existing answer/ranking
     * timeouts already cover that case. Called opportunistically on every page load, like checkTimeout.
     */
    public void pruneDisconnectedPlayers(String gameId) {
        Game game = requireGame(gameId);
        Set<String> exempt = currentlyExpectedToAct(game);
        Instant cutoff = Instant.now().minusSeconds(gameProperties.timeouts().disconnectSeconds());

        for (Player player : List.copyOf(game.activePlayers())) {
            if (exempt.contains(player.getId())) {
                continue;
            }
            if (player.getLastSeenAt().isBefore(cutoff)) {
                easterEggService.onPlayerDisconnected(game, player);
                if (removePlayer(game, player, false)) {
                    return; // game got deleted — nothing left to prune
                }
            }
        }
    }

    private Set<String> currentlyExpectedToAct(Game game) {
        Round round = game.getCurrentRound();
        if (round == null) {
            return Set.of();
        }
        if (game.getStatus() == GameStatus.ANSWERING) {
            return game.answerersInOrder().stream()
                    .filter(p -> !round.hasSubmitted(p))
                    .map(Player::getId)
                    .collect(Collectors.toSet());
        }
        if (game.getStatus() == GameStatus.RANKING && !round.hasFinishedRanking(gameProperties.scoring().podiumSize())) {
            return Set.of(round.getLeader().getId());
        }
        return Set.of();
    }

    /**
     * Removes a player from an active game. In the lobby the row is dropped outright; once the game
     * has started it's only flagged left (past Submissions still point at it), and filtered out of
     * everything "current" via {@link Game#activePlayers()}. Deletes the whole game once nobody's left.
     *
     * @return true if the game was deleted as a result (nobody left to play with)
     */
    private boolean removePlayer(Game game, Player player, boolean kicked) {
        if (player.isLeft()) {
            return false;
        }

        String nickname = player.getNickname();
        if (game.getStatus() == GameStatus.LOBBY) {
            game.getPlayers().remove(player);
        } else {
            player.markLeft();
        }
        log.info("Gra {}: {} opuścił grę", game.getId(), nickname);

        // Admin "ucieka" (README § 5) — no auto-transfer, stays null until someone takes it over.
        if (game.isAdmin(player.getId())) {
            game.setAdminPlayerId(null);
        }

        if (game.activePlayers().isEmpty()) {
            log.info("Gra {}: wszyscy opuścili grę, usuwam", game.getId());
            gameRepository.delete(game);
            return true;
        }

        game.recordDeparture(nickname, kicked);

        // The departed player might have been the last answerer the round was waiting on.
        Round round = game.getCurrentRound();
        if (round != null && game.getStatus() == GameStatus.ANSWERING) {
            advanceToRankingIfComplete(game, round);
        }
        return false;
    }

    private Game requireGame(String gameId) {
        return gameRepository.findById(gameId).orElseThrow(() -> new GameNotFoundException(gameId));
    }

    private Player requirePlayer(Game game, String playerId) {
        return game.findPlayer(playerId)
                .filter(p -> !p.isLeft())
                .orElseThrow(() -> new InvalidGameActionException("Nieznany gracz w tej grze."));
    }

    private void requireAdmin(Game game, String playerId) {
        if (!game.isAdmin(playerId)) {
            throw new InvalidGameActionException("Tylko admin może to zrobić.");
        }
    }

    private void requireStatus(Game game, GameStatus expected) {
        if (game.getStatus() != expected) {
            throw new InvalidGameActionException("Nieprawidłowa faza gry.");
        }
    }
}
