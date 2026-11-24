package io.github.michalmszczepanski.project22_card_game_1.game;

import io.github.michalmszczepanski.project22_card_game_1.card.Card;
import io.github.michalmszczepanski.project22_card_game_1.card.CardPack;
import io.github.michalmszczepanski.project22_card_game_1.card.CardPackRepository;
import io.github.michalmszczepanski.project22_card_game_1.card.PackAudience;
import io.github.michalmszczepanski.project22_card_game_1.stats.GameScoreRecordRepository;
import io.github.michalmszczepanski.project22_card_game_1.user.AppUser;
import io.github.michalmszczepanski.project22_card_game_1.user.AppUserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Transactional so the whole test body shares one Hibernate session — GameService's
 * own @Transactional methods just join it, which keeps every entity we touch
 * managed (no detached-entity surprises) and auto-rolls-back after each test.
 */
@SpringBootTest
@Transactional
class GameServiceTest {

    @Autowired
    private GameService gameService;

    @Autowired
    private GameProperties gameProperties;

    @Autowired
    private CardPackRepository cardPackRepository;

    @Autowired
    private AppUserRepository appUserRepository;

    @Autowired
    private GameScoreRecordRepository gameScoreRecordRepository;

    @Autowired
    private GameRepository gameRepository;

    private List<CardPack> freePacks() {
        return cardPackRepository.findAll().stream().filter(CardPack::isFree).toList();
    }

    private Game createGame(List<CardPack> packs) {
        return gameService.createGame(packs, 20);
    }

    /** Starts as the host (whoever joined first) — the only player allowed to. */
    private void startGame(Game game) {
        gameService.startGame(game.getId(), game.getPlayers().get(0).getId());
    }

    /** Advances as the round's leader — always allowed, regardless of the reveal-priority window. */
    private void nextRound(Game game) {
        gameService.nextRound(game.getId(), game.getCurrentRound().getLeader().getId());
    }

    /**
     * Plays through one ANSWERING+RANKING+REVEALED cycle with 2 answerers.
     * Only the leader ranks (submissions in whatever order they come back in) —
     * no assertions on scoring here, this is just a "get the round over with" helper.
     */
    private void playOneRound(Game game) {
        List<Player> answerers = game.answerersInOrder();
        Player a1 = answerers.get(0);
        Player a2 = answerers.get(1);

        gameService.submitAnswer(game.getId(), a1.getId(), a1.getHand().get(0).getId());
        gameService.submitAnswer(game.getId(), a2.getId(), a2.getHand().get(0).getId());

        Round round = game.getCurrentRound();
        Player leader = round.getLeader();
        int rank = 1;
        for (Submission submission : List.copyOf(round.getSubmissions())) {
            gameService.submitRanking(game.getId(), leader.getId(), submission.getId(), rank++);
        }
    }

    @Test
    void playsOneFullRoundWithCorrectScoring() {
        Game game = createGame(freePacks());
        Player leader = gameService.join(game.getId(), "Ala");
        gameService.join(game.getId(), "Bartek");
        gameService.join(game.getId(), "Celina");

        startGame(game);
        assertThat(game.getStatus()).isEqualTo(GameStatus.ANSWERING);
        assertThat(leader.getHand()).hasSize(gameProperties.handSize());

        Round round1 = game.getCurrentRound();
        Player roundLeader = round1.getLeader();
        List<Player> answerers = game.answerersInOrder();
        assertThat(answerers).hasSize(2);

        Player a1 = answerers.get(0);
        Player a2 = answerers.get(1);
        Card a1Card = a1.getHand().get(0);
        Card a2Card = a2.getHand().get(0);

        gameService.submitAnswer(game.getId(), a1.getId(), a1Card.getId());
        assertThat(game.getStatus()).isEqualTo(GameStatus.ANSWERING);
        gameService.submitAnswer(game.getId(), a2.getId(), a2Card.getId());
        assertThat(game.getStatus()).isEqualTo(GameStatus.RANKING);

        Submission submissionOfA2 = round1.getSubmissions().stream()
                .filter(s -> s.getAuthor().getId().equals(a2.getId()))
                .findFirst().orElseThrow();
        Submission submissionOfA1 = round1.getSubmissions().stream()
                .filter(s -> s.getAuthor().getId().equals(a1.getId()))
                .findFirst().orElseThrow();

        // leader puts a2's submission in 1st place (best) — podium 1st place is worth 3 points
        gameService.submitRanking(game.getId(), roundLeader.getId(), submissionOfA2.getId(), 1);
        assertThat(game.getStatus()).isEqualTo(GameStatus.RANKING);

        // a1's submission takes 2nd place (only 2 answerers, so no 3rd place exists) — worth 2 points
        gameService.submitRanking(game.getId(), roundLeader.getId(), submissionOfA1.getId(), 2);
        assertThat(game.getStatus()).isEqualTo(GameStatus.REVEALED);

        assertThat(roundLeader.getPoints()).isEqualTo(0);
        assertThat(a1.getPoints()).isEqualTo(2);
        assertThat(a2.getPoints()).isEqualTo(3);

        nextRound(game);
        assertThat(game.getStatus()).isEqualTo(GameStatus.ANSWERING);
        assertThat(game.getCurrentRound().getNumber()).isEqualTo(2);
        assertThat(a1.getHand()).hasSize(gameProperties.handSize());
        assertThat(a2.getHand()).hasSize(gameProperties.handSize());
    }

    @Test
    void playerCanSubmitABlankCardWithTheirOwnTypedAnswerOnce() {
        Game game = createGame(freePacks());
        gameService.join(game.getId(), "Ala");
        gameService.join(game.getId(), "Bartek");
        gameService.join(game.getId(), "Celina");
        startGame(game);

        List<Player> answerers = game.answerersInOrder();
        Player a1 = answerers.get(0);
        Player a2 = answerers.get(1);
        int handSizeBefore = a1.getHand().size();

        gameService.submitBlankAnswer(game.getId(), a1.getId(), "  moja własna odpowiedź  ");
        assertThat(a1.isUsedBlankCard()).isTrue();
        assertThat(a1.getHand()).hasSize(handSizeBefore); // blank card doesn't touch the hand

        Submission blank = game.getCurrentRound().getSubmissions().stream()
                .filter(s -> s.getAuthor().getId().equals(a1.getId()))
                .findFirst().orElseThrow();
        assertThat(blank.text()).isEqualTo("moja własna odpowiedź");

        gameService.submitAnswer(game.getId(), a2.getId(), a2.getHand().get(0).getId());
        assertThat(game.getStatus()).isEqualTo(GameStatus.RANKING);
    }

    @Test
    void blankCardCanOnlyBeUsedOncePerGame() {
        Game game = createGame(freePacks());
        gameService.join(game.getId(), "Ala");
        gameService.join(game.getId(), "Bartek");
        gameService.join(game.getId(), "Celina");
        startGame(game);

        // round 1: a1 plays the blank card, a2 answers normally
        List<Player> answerers = game.answerersInOrder();
        Player a1 = answerers.get(0);
        Player a2 = answerers.get(1);
        gameService.submitBlankAnswer(game.getId(), a1.getId(), "pierwsza pusta karta");
        gameService.submitAnswer(game.getId(), a2.getId(), a2.getHand().get(0).getId());
        int rank = 1;
        for (Submission submission : List.copyOf(game.getCurrentRound().getSubmissions())) {
            gameService.submitRanking(game.getId(), game.getCurrentRound().getLeader().getId(), submission.getId(), rank++);
        }
        nextRound(game);

        // round 2: with 3 players, a1 is now the leader — sits out, can't trip the check either way
        playOneRound(game);
        nextRound(game);

        // round 3: a1 is back to answering — reusing the blank card must fail
        assertThatThrownBy(() -> gameService.submitBlankAnswer(game.getId(), a1.getId(), "druga próba"))
                .isInstanceOf(InvalidGameActionException.class)
                .hasMessageContaining("raz na grę");
    }

    @Test
    void blankCardRejectsEmptyOrTooLongText() {
        Game game = createGame(freePacks());
        gameService.join(game.getId(), "Ala");
        gameService.join(game.getId(), "Bartek");
        gameService.join(game.getId(), "Celina");
        startGame(game);

        Player a1 = game.answerersInOrder().get(0);
        assertThatThrownBy(() -> gameService.submitBlankAnswer(game.getId(), a1.getId(), "   "))
                .isInstanceOf(InvalidGameActionException.class);
        assertThatThrownBy(() -> gameService.submitBlankAnswer(game.getId(), a1.getId(), "x".repeat(200)))
                .isInstanceOf(InvalidGameActionException.class);
        assertThat(a1.isUsedBlankCard()).isFalse();
    }

    @Test
    void podiumCapsScoringAtTopThreeWhenThereAreMoreAnswerers() {
        Game game = createGame(freePacks());
        gameService.join(game.getId(), "Ala");
        gameService.join(game.getId(), "Bartek");
        gameService.join(game.getId(), "Celina");
        gameService.join(game.getId(), "Dawid");
        gameService.join(game.getId(), "Erik");
        startGame(game);

        Round round = game.getCurrentRound();
        Player roundLeader = round.getLeader();
        List<Player> answerers = game.answerersInOrder();
        assertThat(answerers).hasSize(4);

        for (Player answerer : answerers) {
            gameService.submitAnswer(game.getId(), answerer.getId(), answerer.getHand().get(0).getId());
        }
        assertThat(game.getStatus()).isEqualTo(GameStatus.RANKING);

        List<Submission> submissions = round.getSubmissions();
        // Only 3 podium places exist even though there are 4 submissions — the leader ranks 1st..3rd.
        gameService.submitRanking(game.getId(), roundLeader.getId(), submissions.get(0).getId(), 1);
        gameService.submitRanking(game.getId(), roundLeader.getId(), submissions.get(1).getId(), 2);
        assertThat(game.getStatus()).isEqualTo(GameStatus.RANKING);
        gameService.submitRanking(game.getId(), roundLeader.getId(), submissions.get(2).getId(), 3);

        // Round reveals as soon as the podium (3 places) is filled — no 4th place to assign.
        assertThat(game.getStatus()).isEqualTo(GameStatus.REVEALED);

        assertThat(submissions.get(0).getAuthor().getPoints()).isEqualTo(3);
        assertThat(submissions.get(1).getAuthor().getPoints()).isEqualTo(2);
        assertThat(submissions.get(2).getAuthor().getPoints()).isEqualTo(1);
        assertThat(submissions.get(3).getAuthor().getPoints()).isEqualTo(0);
    }

    @Test
    void onlyLeaderCanRank() {
        Game game = createGame(freePacks());
        gameService.join(game.getId(), "Ala");
        gameService.join(game.getId(), "Bartek");
        gameService.join(game.getId(), "Celina");
        startGame(game);

        List<Player> answerers = game.answerersInOrder();
        Player a1 = answerers.get(0);
        Player a2 = answerers.get(1);
        gameService.submitAnswer(game.getId(), a1.getId(), a1.getHand().get(0).getId());
        gameService.submitAnswer(game.getId(), a2.getId(), a2.getHand().get(0).getId());

        Submission someSubmission = game.getCurrentRound().getSubmissions().get(0);

        assertThatThrownBy(() -> gameService.submitRanking(game.getId(), a1.getId(), someSubmission.getId(), 1))
                .isInstanceOf(InvalidGameActionException.class);
    }

    @Test
    void cannotAssignSameRankTwice() {
        Game game = createGame(freePacks());
        gameService.join(game.getId(), "Ala");
        gameService.join(game.getId(), "Bartek");
        gameService.join(game.getId(), "Celina");
        startGame(game);

        List<Player> answerers = game.answerersInOrder();
        gameService.submitAnswer(game.getId(), answerers.get(0).getId(), answerers.get(0).getHand().get(0).getId());
        gameService.submitAnswer(game.getId(), answerers.get(1).getId(), answerers.get(1).getHand().get(0).getId());

        Round round = game.getCurrentRound();
        Player leader = round.getLeader();
        List<Submission> submissions = round.getSubmissions();
        gameService.submitRanking(game.getId(), leader.getId(), submissions.get(0).getId(), 1);

        assertThatThrownBy(() -> gameService.submitRanking(game.getId(), leader.getId(), submissions.get(1).getId(), 1))
                .isInstanceOf(InvalidGameActionException.class);
    }

    @Test
    void cannotAssignRankOutsideValidRange() {
        Game game = createGame(freePacks());
        gameService.join(game.getId(), "Ala");
        gameService.join(game.getId(), "Bartek");
        gameService.join(game.getId(), "Celina");
        startGame(game);

        List<Player> answerers = game.answerersInOrder();
        gameService.submitAnswer(game.getId(), answerers.get(0).getId(), answerers.get(0).getHand().get(0).getId());
        gameService.submitAnswer(game.getId(), answerers.get(1).getId(), answerers.get(1).getHand().get(0).getId());

        Round round = game.getCurrentRound();
        Player leader = round.getLeader();
        Submission submission = round.getSubmissions().get(0);

        assertThatThrownBy(() -> gameService.submitRanking(game.getId(), leader.getId(), submission.getId(), 3))
                .isInstanceOf(InvalidGameActionException.class);
        assertThatThrownBy(() -> gameService.submitRanking(game.getId(), leader.getId(), submission.getId(), 0))
                .isInstanceOf(InvalidGameActionException.class);
    }

    @Test
    void onlyDealsCardsFromActivePacks() {
        CardPack pikantna = cardPackRepository.findAll().stream()
                .filter(p -> p.getName().equals("Pikantna"))
                .findFirst().orElseThrow();

        Game game = createGame(List.of(pikantna));
        Player p1 = gameService.join(game.getId(), "Ala");
        Player p2 = gameService.join(game.getId(), "Bartek");
        Player p3 = gameService.join(game.getId(), "Celina");
        startGame(game);

        for (Player p : List.of(p1, p2, p3)) {
            assertThat(p.getHand()).allSatisfy(card -> assertThat(card.getPack().getId()).isEqualTo(pikantna.getId()));
        }
        assertThat(game.getCurrentRound().getQuestionCard().getPack().getId()).isEqualTo(pikantna.getId());
    }

    @Test
    void familyModeStripsAdultCardsFromHandsAndFutureDraws() {
        CardPack generic = freePacks().stream().filter(p -> p.getName().equals("Generyczny")).findFirst().orElseThrow();
        CardPack pikantna = cardPackRepository.findAll().stream()
                .filter(p -> p.getName().equals("Pikantna")).findFirst().orElseThrow();

        Game game = createGame(List.of(generic, pikantna));
        Player p1 = gameService.join(game.getId(), "Ala");
        Player p2 = gameService.join(game.getId(), "Bartek");
        Player p3 = gameService.join(game.getId(), "Celina");
        startGame(game);

        gameService.setFamilyMode(game.getId(), p1.getId(), true);
        assertThat(game.isFamilyMode()).isTrue();

        for (Player p : List.of(p1, p2, p3)) {
            assertThat(p.getHand()).allSatisfy(
                    card -> assertThat(card.getPack().getAudience()).isNotEqualTo(PackAudience.ADULT));
        }

        // Play a round out so new cards get drawn too — must still be family-safe.
        playOneRound(game);
        nextRound(game);
        for (Player p : List.of(p1, p2, p3)) {
            assertThat(p.getHand()).allSatisfy(
                    card -> assertThat(card.getPack().getAudience()).isNotEqualTo(PackAudience.ADULT));
        }
    }

    @Test
    void finishedGameRecordsScoresOnlyForLoggedInPlayers() {
        AppUser loggedInUser = appUserRepository.save(new AppUser("stats-test-user-" + System.nanoTime(), "hash"));

        Game game = createGame(freePacks());
        Player loggedIn = gameService.join(game.getId(), "Ala", loggedInUser.getId());
        gameService.join(game.getId(), "Bartek");
        gameService.join(game.getId(), "Celina");
        startGame(game);

        for (int round = 1; round <= game.getTotalRounds(); round++) {
            playOneRound(game);
            nextRound(game);
        }

        assertThat(game.getStatus()).isEqualTo(GameStatus.FINISHED);
        assertThat(gameScoreRecordRepository.findByUserOrderByPlayedAtDesc(loggedInUser))
                .hasSize(1)
                .first()
                .satisfies(record -> {
                    assertThat(record.getGameId()).isEqualTo(game.getId());
                    assertThat(record.getPoints()).isEqualTo(loggedIn.getPoints());
                });
    }

    @Test
    void answerTimeoutAutoPlaysRandomCardsAndAdvances() {
        Game game = createGame(freePacks());
        gameService.join(game.getId(), "Ala");
        gameService.join(game.getId(), "Bartek");
        gameService.join(game.getId(), "Celina");
        startGame(game);

        List<Player> answerers = game.answerersInOrder();
        int handSizeBefore = answerers.get(0).getHand().size();

        game.getCurrentRound().setPhaseStartedAt(
                java.time.Instant.now().minusSeconds(gameProperties.timeouts().answerSeconds() + 5));
        gameService.checkTimeout(game.getId());

        assertThat(game.getStatus()).isEqualTo(GameStatus.RANKING);
        assertThat(game.getCurrentRound().getSubmissions()).hasSize(2);
        assertThat(answerers.get(0).getHand()).hasSize(handSizeBefore - 1);
    }

    @Test
    void rankingTimeoutRevealsWithWhateverRankingsExist() {
        Game game = createGame(freePacks());
        gameService.join(game.getId(), "Ala");
        gameService.join(game.getId(), "Bartek");
        gameService.join(game.getId(), "Celina");
        startGame(game);

        List<Player> answerers = game.answerersInOrder();
        gameService.submitAnswer(game.getId(), answerers.get(0).getId(), answerers.get(0).getHand().get(0).getId());
        gameService.submitAnswer(game.getId(), answerers.get(1).getId(), answerers.get(1).getHand().get(0).getId());
        assertThat(game.getStatus()).isEqualTo(GameStatus.RANKING);

        // nobody ranks in time
        game.getCurrentRound().setPhaseStartedAt(
                java.time.Instant.now().minusSeconds(gameProperties.timeouts().rankingSeconds() + 5));
        gameService.checkTimeout(game.getId());

        assertThat(game.getStatus()).isEqualTo(GameStatus.REVEALED);
    }

    @Test
    void cannotStartWithTooFewPlayers() {
        Game game = createGame(freePacks());
        gameService.join(game.getId(), "Ala");
        gameService.join(game.getId(), "Bartek");

        assertThatThrownBy(() -> startGame(game))
                .isInstanceOf(InvalidGameActionException.class);
    }

    @Test
    void roomHoldsUpToTenPlayersAndRejectsAnEleventh() {
        Game game = createGame(freePacks());
        for (int i = 1; i <= gameProperties.players().max(); i++) {
            gameService.join(game.getId(), "P" + i);
        }
        assertThat(game.activePlayers()).hasSize(gameProperties.players().max());

        assertThatThrownBy(() -> gameService.join(game.getId(), "OneTooMany"))
                .isInstanceOf(InvalidGameActionException.class)
                .hasMessage("Pokój jest pełny (max " + gameProperties.players().max() + " graczy).");

        startGame(game);
        assertThat(game.getStatus()).isEqualTo(GameStatus.ANSWERING);
    }

    @Test
    void joiningMidGameExcludesThePlayerFromTheCurrentRoundOnly() {
        Game game = createGame(freePacks());
        gameService.join(game.getId(), "Ala");
        gameService.join(game.getId(), "Bartek");
        gameService.join(game.getId(), "Celina");
        startGame(game);

        Player latecomer = gameService.join(game.getId(), "Dawid");
        assertThat(latecomer.getHand()).isEmpty();
        assertThat(latecomer.getJoinedAtRound()).isEqualTo(game.getCurrentRound().getNumber());
        assertThat(game.answerersInOrder()).noneMatch(p -> p.getId().equals(latecomer.getId()));

        playOneRound(game);
        nextRound(game);

        assertThat(game.getCurrentRound().getNumber()).isEqualTo(2);
        assertThat(latecomer.getHand()).hasSize(gameProperties.handSize());
        assertThat(game.answerersInOrder()).anyMatch(p -> p.getId().equals(latecomer.getId()));
    }

    @Test
    void cannotJoinAFinishedGame() {
        Game game = createGame(freePacks());
        gameService.join(game.getId(), "Ala");
        gameService.join(game.getId(), "Bartek");
        gameService.join(game.getId(), "Celina");
        gameService.endGame(game.getId(), game.getPlayers().get(0).getId());

        assertThatThrownBy(() -> gameService.join(game.getId(), "TooLate"))
                .isInstanceOf(InvalidGameActionException.class)
                .hasMessage("Gra już się zakończyła, nie można dołączyć.");
    }

    @Test
    void onlyAdminCanStartGame() {
        Game game = createGame(freePacks());
        Player admin = gameService.join(game.getId(), "Ala");
        Player other = gameService.join(game.getId(), "Bartek");
        gameService.join(game.getId(), "Celina");

        assertThatThrownBy(() -> gameService.startGame(game.getId(), other.getId()))
                .isInstanceOf(InvalidGameActionException.class)
                .hasMessage("Tylko admin może to zrobić.");

        gameService.startGame(game.getId(), admin.getId());
        assertThat(game.getStatus()).isEqualTo(GameStatus.ANSWERING);
    }

    @Test
    void adminRoleDoesNotAutoTransferWhenAdminLeaves_requiresTakeOverAdmin() {
        Game game = createGame(freePacks());
        Player admin = gameService.join(game.getId(), "Ala");
        Player other = gameService.join(game.getId(), "Bartek");
        gameService.join(game.getId(), "Celina");

        gameService.leave(game.getId(), admin.getId());
        gameService.join(game.getId(), "Dawid"); // back up to the 3-player minimum

        assertThat(game.hasAdmin()).isFalse();
        assertThatThrownBy(() -> gameService.startGame(game.getId(), other.getId()))
                .isInstanceOf(InvalidGameActionException.class);

        gameService.takeOverAdmin(game.getId(), other.getId());
        assertThat(game.isAdmin(other.getId())).isTrue();

        gameService.startGame(game.getId(), other.getId());
        assertThat(game.getStatus()).isEqualTo(GameStatus.ANSWERING);
    }

    @Test
    void takeOverAdminFailsWhileAnAdminIsStillActive() {
        Game game = createGame(freePacks());
        Player admin = gameService.join(game.getId(), "Ala");
        Player other = gameService.join(game.getId(), "Bartek");
        gameService.join(game.getId(), "Celina");

        assertThatThrownBy(() -> gameService.takeOverAdmin(game.getId(), other.getId()))
                .isInstanceOf(InvalidGameActionException.class)
                .hasMessage("Ktoś właśnie przejął admina.");
        assertThat(game.isAdmin(admin.getId())).isTrue();
    }

    @Test
    void adminCanVoluntarilyTransferTheRoleWithoutLeaving() {
        Game game = createGame(freePacks());
        Player admin = gameService.join(game.getId(), "Ala");
        Player other = gameService.join(game.getId(), "Bartek");
        gameService.join(game.getId(), "Celina");

        assertThatThrownBy(() -> gameService.transferAdmin(game.getId(), other.getId(), admin.getId()))
                .isInstanceOf(InvalidGameActionException.class)
                .hasMessage("Tylko admin może to zrobić.");

        assertThatThrownBy(() -> gameService.transferAdmin(game.getId(), admin.getId(), admin.getId()))
                .isInstanceOf(InvalidGameActionException.class)
                .hasMessage("Już jesteś adminem.");

        gameService.transferAdmin(game.getId(), admin.getId(), other.getId());
        assertThat(game.isAdmin(other.getId())).isTrue();
        assertThat(game.isAdmin(admin.getId())).isFalse();
        assertThat(game.activePlayers()).extracting(Player::getId).contains(admin.getId()); // still in the game
    }

    @Test
    void adminCanKickAnotherPlayerButNotThemselves() {
        Game game = createGame(freePacks());
        Player admin = gameService.join(game.getId(), "Ala");
        Player target = gameService.join(game.getId(), "Bartek");
        gameService.join(game.getId(), "Celina");

        assertThatThrownBy(() -> gameService.kickPlayer(game.getId(), admin.getId(), admin.getId()))
                .isInstanceOf(InvalidGameActionException.class)
                .hasMessage("Nie możesz wyrzucić samego siebie.");

        assertThatThrownBy(() -> gameService.kickPlayer(game.getId(), target.getId(), admin.getId()))
                .isInstanceOf(InvalidGameActionException.class)
                .hasMessage("Tylko admin może to zrobić.");

        gameService.kickPlayer(game.getId(), admin.getId(), target.getId());
        assertThat(game.findPlayer(target.getId())).isEmpty(); // still LOBBY — removal drops the row outright
        assertThat(game.getLastKickedPlayerId()).isEqualTo(target.getId());
        assertThat(game.getLastLeftNickname()).isEqualTo("Bartek");
        assertThat(game.isLastLeftWasKick()).isTrue();
    }

    @Test
    void onlyAdminCanToggleFamilyModeOrEndTheGame() {
        Game game = createGame(freePacks());
        Player admin = gameService.join(game.getId(), "Ala");
        Player other = gameService.join(game.getId(), "Bartek");
        gameService.join(game.getId(), "Celina");

        assertThatThrownBy(() -> gameService.setFamilyMode(game.getId(), other.getId(), true))
                .isInstanceOf(InvalidGameActionException.class)
                .hasMessage("Tylko admin może to zrobić.");

        assertThatThrownBy(() -> gameService.endGame(game.getId(), other.getId()))
                .isInstanceOf(InvalidGameActionException.class)
                .hasMessage("Tylko admin może to zrobić.");

        gameService.setFamilyMode(game.getId(), admin.getId(), true);
        assertThat(game.isFamilyMode()).isTrue();

        gameService.endGame(game.getId(), admin.getId());
        assertThat(game.getStatus()).isEqualTo(GameStatus.FINISHED);
    }

    @Test
    void adminCanSkipAStuckRoundAndRevealItImmediately() {
        Game game = createGame(freePacks());
        Player admin = gameService.join(game.getId(), "Ala");
        Player other = gameService.join(game.getId(), "Bartek");
        gameService.join(game.getId(), "Celina");
        startGame(game);

        assertThatThrownBy(() -> gameService.forceRevealRound(game.getId(), other.getId()))
                .isInstanceOf(InvalidGameActionException.class)
                .hasMessage("Tylko admin może to zrobić.");

        // Nobody has answered yet (leader included) — admin skips anyway, round reveals with 0 points.
        gameService.forceRevealRound(game.getId(), admin.getId());
        assertThat(game.getStatus()).isEqualTo(GameStatus.REVEALED);
        assertThat(game.getCurrentRound().getRankings()).isEmpty();
    }

    @Test
    void cannotSkipARoundWhenThereIsNoneInProgress() {
        Game game = createGame(freePacks());
        Player admin = gameService.join(game.getId(), "Ala");
        gameService.join(game.getId(), "Bartek");
        gameService.join(game.getId(), "Celina");
        // Still LOBBY — no current round at all.

        assertThatThrownBy(() -> gameService.forceRevealRound(game.getId(), admin.getId()))
                .isInstanceOf(InvalidGameActionException.class)
                .hasMessage("Nie ma teraz rundy do pominięcia.");
    }

    @Test
    void afterLeaderWindowClosesOnlyAdminOrTheLeaderCanAdvanceTheRound() {
        Game game = createGame(freePacks());
        Player admin = gameService.join(game.getId(), "Ala");
        gameService.join(game.getId(), "Bartek");
        gameService.join(game.getId(), "Celina");
        startGame(game);
        playOneRound(game);

        Player leader = game.getCurrentRound().getLeader();
        Player neitherAdminNorLeader = game.activePlayers().stream()
                .filter(p -> !p.getId().equals(leader.getId()) && !p.getId().equals(admin.getId()))
                .findFirst().orElseThrow();

        game.getCurrentRound().setPhaseStartedAt(
                java.time.Instant.now().minusSeconds(gameProperties.timeouts().revealSeconds() + 5));

        if (!leader.getId().equals(admin.getId())) {
            assertThatThrownBy(() -> gameService.nextRound(game.getId(), neitherAdminNorLeader.getId()))
                    .isInstanceOf(InvalidGameActionException.class)
                    .hasMessage("Tylko admin może to zrobić.");
        }

        gameService.nextRound(game.getId(), admin.getId());
        assertThat(game.getCurrentRound().getNumber()).isEqualTo(2);
    }

    @Test
    void createGameRejectsRoundCountOutsideAllowedRange() {
        assertThatThrownBy(() -> gameService.createGame(freePacks(), 0))
                .isInstanceOf(InvalidGameActionException.class);
        assertThatThrownBy(() -> gameService.createGame(freePacks(), gameProperties.maxTotalRounds() + 1))
                .isInstanceOf(InvalidGameActionException.class);
    }

    @Test
    void gameFinishesAfterTheHostChosenNumberOfRounds() {
        Game game = gameService.createGame(freePacks(), 1);
        gameService.join(game.getId(), "Ala");
        gameService.join(game.getId(), "Bartek");
        gameService.join(game.getId(), "Celina");
        startGame(game);

        playOneRound(game);
        nextRound(game);

        assertThat(game.getStatus()).isEqualTo(GameStatus.FINISHED);
    }

    @Test
    void onlyRoundLeaderCanAdvanceDuringRevealPriorityWindow() {
        Game game = createGame(freePacks());
        gameService.join(game.getId(), "Ala");
        gameService.join(game.getId(), "Bartek");
        gameService.join(game.getId(), "Celina");
        startGame(game);

        playOneRound(game);
        assertThat(game.getStatus()).isEqualTo(GameStatus.REVEALED);

        Round round = game.getCurrentRound();
        Player leader = round.getLeader();
        Player nonLeader = game.answerersInOrder().get(0);

        assertThatThrownBy(() -> gameService.nextRound(game.getId(), nonLeader.getId()))
                .isInstanceOf(InvalidGameActionException.class);

        gameService.nextRound(game.getId(), leader.getId());
        assertThat(game.getCurrentRound().getNumber()).isEqualTo(2);
    }

    // Replaced by afterLeaderWindowClosesOnlyAdminOrTheLeaderCanAdvanceTheRound — README § 5 narrowed
    // "opens to everyone after the window" down to "opens to admin" (leader keeps priority throughout).

    @Test
    void leavingLobbyRemovesPlayerOutright() {
        Game game = createGame(freePacks());
        gameService.join(game.getId(), "Ala");
        Player bartek = gameService.join(game.getId(), "Bartek");
        gameService.join(game.getId(), "Celina");

        gameService.leave(game.getId(), bartek.getId());

        assertThat(game.getPlayers()).hasSize(2);
        assertThat(game.findPlayer(bartek.getId())).isEmpty();
    }

    @Test
    void lastPlayerLeavingDeletesTheGame() {
        Game game = createGame(freePacks());
        Player ala = gameService.join(game.getId(), "Ala");
        String gameId = game.getId();

        gameService.leave(gameId, ala.getId());

        assertThat(gameRepository.findById(gameId)).isEmpty();
    }

    @Test
    void leavingMidGameHidesPlayerButKeepsHistoryAndDeletesGameOnceAllGone() {
        Game game = createGame(freePacks());
        Player leader = gameService.join(game.getId(), "Ala");
        gameService.join(game.getId(), "Bartek");
        gameService.join(game.getId(), "Celina");
        startGame(game);

        List<Player> answerers = game.answerersInOrder();
        Player a1 = answerers.get(0);
        Player a2 = answerers.get(1);
        gameService.submitAnswer(game.getId(), a1.getId(), a1.getHand().get(0).getId());

        // a2 leaves before submitting their answer
        gameService.leave(game.getId(), a2.getId());

        assertThat(game.activePlayers()).containsExactlyInAnyOrder(leader, a1);
        assertThat(game.getPlayers()).contains(a2); // row kept — past/ongoing Submissions still reference it
        assertThat(game.answerersInOrder()).containsExactly(a1);

        // round can now complete with just the remaining answerer
        assertThat(game.getStatus()).isEqualTo(GameStatus.RANKING);

        gameService.leave(game.getId(), a1.getId());
        gameService.leave(game.getId(), leader.getId());

        assertThat(gameRepository.findById(game.getId())).isEmpty();
    }

    @Test
    void pruneDisconnectedPlayersIgnoresSomeoneCurrentlyExpectedToAct() {
        Game game = createGame(freePacks());
        gameService.join(game.getId(), "Ala");
        gameService.join(game.getId(), "Bartek");
        gameService.join(game.getId(), "Celina");
        startGame(game);

        List<Player> answerers = game.answerersInOrder();
        Player a2 = answerers.get(1);

        // a2 hasn't answered yet — their screen isn't auto-refreshing, so staleness is expected, not a disconnect.
        a2.setLastSeenAt(java.time.Instant.now().minusSeconds(gameProperties.timeouts().disconnectSeconds() + 5));

        gameService.pruneDisconnectedPlayers(game.getId());

        assertThat(a2.isLeft()).isFalse();
        assertThat(game.activePlayers()).contains(a2);
    }

    @Test
    void pruneDisconnectedPlayersRemovesSomeoneWhoWentQuietWhileWaiting() {
        Game game = createGame(freePacks());
        gameService.join(game.getId(), "Ala");
        gameService.join(game.getId(), "Bartek");
        gameService.join(game.getId(), "Celina");
        startGame(game);

        List<Player> answerers = game.answerersInOrder();
        Player a1 = answerers.get(0);
        Player a2 = answerers.get(1);
        gameService.submitAnswer(game.getId(), a1.getId(), a1.getHand().get(0).getId());

        // a1 already answered — now just waiting/spectating, so their screen keeps auto-refreshing.
        // Going quiet for that long means their tab actually closed.
        a1.setLastSeenAt(java.time.Instant.now().minusSeconds(gameProperties.timeouts().disconnectSeconds() + 5));

        gameService.pruneDisconnectedPlayers(game.getId());

        assertThat(a1.isLeft()).isTrue();
        assertThat(game.activePlayers()).doesNotContain(a1);
        // a2 is still mid-turn (hasn't answered) — exempt from pruning regardless of their heartbeat.
        assertThat(a2.isLeft()).isFalse();
    }
}
