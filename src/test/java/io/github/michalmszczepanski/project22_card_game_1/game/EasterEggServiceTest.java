package io.github.michalmszczepanski.project22_card_game_1.game;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pure unit tests for the "Easter eggi" detection (MECHANICS.md) — no Spring context needed,
 * {@link EasterEggService} only touches the entities passed to it.
 */
class EasterEggServiceTest {

    private static final List<Integer> PODIUM_POINTS = List.of(3, 2, 1);

    private final EasterEggService easterEggService = new EasterEggService();

    private Round roundWithRankedSubmission(int number, Player leader, Player author, int rank) {
        Round round = new Round(number, leader, null);
        Submission submission = new Submission(author, "odpowiedź");
        round.addSubmission(submission);
        round.addRanking(new Ranking(submission, rank));
        return round;
    }

    private Round roundWithUnrankedSubmission(int number, Player leader, Player author) {
        Round round = new Round(number, leader, null);
        round.addSubmission(new Submission(author, "odpowiedź"));
        return round;
    }

    @Test
    void hatTrickFiresAfterThreeConsecutiveFirstPlaceWins() {
        Game game = new Game("AAAAA", 20);
        Player leader = new Player("Prowadzący", null);
        Player ala = new Player("Ala", null);

        easterEggService.afterRoundScored(game, roundWithRankedSubmission(1, leader, ala, 1), PODIUM_POINTS);
        assertThat(game.getLastEasterEggText()).isNull();
        easterEggService.afterRoundScored(game, roundWithRankedSubmission(2, leader, ala, 1), PODIUM_POINTS);
        assertThat(game.getLastEasterEggText()).isNull();
        easterEggService.afterRoundScored(game, roundWithRankedSubmission(3, leader, ala, 1), PODIUM_POINTS);

        assertThat(ala.getWinStreak()).isEqualTo(3);
        assertThat(game.getLastEasterEggText()).contains("Ala").contains("nie do zatrzymania");
    }

    @Test
    void winStreakResetsOnceSomeoneDoesNotTakeFirstPlace() {
        Game game = new Game("AAAAA", 20);
        Player leader = new Player("Prowadzący", null);
        Player ala = new Player("Ala", null);

        easterEggService.afterRoundScored(game, roundWithRankedSubmission(1, leader, ala, 1), PODIUM_POINTS);
        easterEggService.afterRoundScored(game, roundWithRankedSubmission(2, leader, ala, 2), PODIUM_POINTS);
        easterEggService.afterRoundScored(game, roundWithRankedSubmission(3, leader, ala, 1), PODIUM_POINTS);

        assertThat(ala.getWinStreak()).isEqualTo(1);
        assertThat(game.getLastEasterEggText()).isNull();
    }

    @Test
    void drySpellFiresAfterFiveConsecutiveScorelessRoundsStillPlaying() {
        Game game = new Game("AAAAA", 20);
        Player leader = new Player("Prowadzący", null);
        Player bartek = new Player("Bartek", null);

        for (int round = 1; round <= 4; round++) {
            easterEggService.afterRoundScored(game, roundWithUnrankedSubmission(round, leader, bartek), PODIUM_POINTS);
        }
        assertThat(game.getLastEasterEggText()).isNull();

        easterEggService.afterRoundScored(game, roundWithUnrankedSubmission(5, leader, bartek), PODIUM_POINTS);

        assertThat(bartek.getScorelessStreak()).isEqualTo(5);
        assertThat(game.getLastEasterEggText()).contains("Bartek").contains("stand-up o porażkach");
    }

    @Test
    void midpointLastPlaceWinningTheGameFiresComebackMessage() {
        Game game = new Game("AAAAA", 10);
        Player ala = new Player("Ala", null);
        Player bartek = new Player("Bartek", null);
        game.addPlayer(ala);
        game.addPlayer(bartek);
        ala.addPoints(0);
        bartek.addPoints(5);

        easterEggService.captureMidpointStandings(game, 5); // totalRounds/2 == 5
        assertThat(ala.isLastAtMidpoint()).isTrue();
        assertThat(bartek.isLastAtMidpoint()).isFalse();

        ala.addPoints(20); // Ala storms back and wins outright
        easterEggService.onGameFinished(game);

        assertThat(game.getLastEasterEggText()).contains("Ala").contains("wraca z martwych");
    }

    @Test
    void captureMidpointStandingsIgnoresRoundsOtherThanTheHalfwayPoint() {
        Game game = new Game("AAAAA", 10);
        Player ala = new Player("Ala", null);
        game.addPlayer(ala);

        easterEggService.captureMidpointStandings(game, 4);
        easterEggService.captureMidpointStandings(game, 6);

        assertThat(ala.isLastAtMidpoint()).isFalse();
    }

    @Test
    void tieForFirstPlaceFiresSoulmatesMessageInsteadOfComebackOrZeroHero() {
        Game game = new Game("AAAAA", 20);
        Player ala = new Player("Ala", null);
        Player bartek = new Player("Bartek", null);
        game.addPlayer(ala);
        game.addPlayer(bartek);
        ala.addPoints(5);
        bartek.addPoints(5);

        easterEggService.onGameFinished(game);

        assertThat(game.getLastEasterEggText()).contains("Ala").contains("Bartek").contains("tytuł do podziału");
    }

    @Test
    void zeroPointFinisherFiresZeroHeroMessage() {
        Game game = new Game("AAAAA", 20);
        Player ala = new Player("Ala", null);
        Player celina = new Player("Celina", null);
        game.addPlayer(ala);
        game.addPlayer(celina);
        ala.addPoints(5);
        // celina stays at 0 points

        easterEggService.onGameFinished(game);

        assertThat(game.getLastEasterEggText()).contains("Celina").contains("liczy się udział");
    }

    @Test
    void disconnectingWhileInTheLeadFiresPerfectTimingMessage() {
        Game game = new Game("AAAAA", 20);
        Player ala = new Player("Ala", null);
        Player bartek = new Player("Bartek", null);
        game.addPlayer(ala);
        game.addPlayer(bartek);
        ala.addPoints(10);
        bartek.addPoints(3);

        easterEggService.onPlayerDisconnected(game, ala);

        assertThat(game.getLastEasterEggText()).contains("Ala").contains("najlepszym momencie");
    }

    @Test
    void disconnectingWhileNotInTheLeadFiresNoMessage() {
        Game game = new Game("AAAAA", 20);
        Player ala = new Player("Ala", null);
        Player bartek = new Player("Bartek", null);
        game.addPlayer(ala);
        game.addPlayer(bartek);
        ala.addPoints(10);
        bartek.addPoints(3);

        easterEggService.onPlayerDisconnected(game, bartek);

        assertThat(game.getLastEasterEggText()).isNull();
    }
}
