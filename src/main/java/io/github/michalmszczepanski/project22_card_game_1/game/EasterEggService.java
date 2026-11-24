package io.github.michalmszczepanski.project22_card_game_1.game;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Detects the text-only "fun fact" events from MECHANICS.md ("Easter eggi") and posts the
 * matching message onto {@link Game#showEasterEgg} for game.html/tv.html to show as a brief
 * banner. Flavor text only — never touches scoring (GameService.scoreRound does that).
 */
@Service
@Slf4j
class EasterEggService {

    private static final int HAT_TRICK_STREAK = 3;
    private static final int DRY_SPELL_STREAK = 5;

    /** Called right after a round's points are awarded — updates streaks and checks hat trick / dry spell. */
    void afterRoundScored(Game game, Round round, List<Integer> podiumPoints) {
        Map<String, Integer> rankBySubmissionId = round.getRankings().stream()
                .collect(Collectors.toMap(r -> r.getSubmission().getId(), Ranking::getRank));

        for (Submission submission : round.getSubmissions()) {
            Player author = submission.getAuthor();
            Integer rank = rankBySubmissionId.get(submission.getId());
            boolean wonFirstPlace = rank != null && rank == 1;
            boolean scoredPoints = rank != null && Round.pointsForRank(rank, podiumPoints) > 0;
            author.registerRoundResult(wonFirstPlace, scoredPoints);

            if (author.getWinStreak() == HAT_TRICK_STREAK) {
                fire(game, "🔥 " + author.getNickname()
                        + " jest nie do zatrzymania! Ktoś niech go/ją sprawdzi pod kątem oszustwa.");
            } else if (author.getScorelessStreak() == DRY_SPELL_STREAK) {
                fire(game, author.getNickname() + " zbiera materiał na stand-up o porażkach.");
            }
        }
    }

    /** Called when a new round starts — at the game's halfway point, snapshots who's in last place. */
    void captureMidpointStandings(Game game, int startingRoundNumber) {
        int midpoint = game.getTotalRounds() / 2;
        if (midpoint == 0 || startingRoundNumber != midpoint) {
            return;
        }
        List<Player> active = game.activePlayers();
        int min = active.stream().mapToInt(Player::getPoints).min().orElse(0);
        active.stream().filter(p -> p.getPoints() == min).forEach(Player::markLastAtMidpoint);
    }

    /** Called once a game finishes — checks tie for first, comeback winner, and "zero points" finishers. */
    void onGameFinished(Game game) {
        List<Player> active = game.activePlayers();
        if (active.isEmpty()) {
            return;
        }
        int max = active.stream().mapToInt(Player::getPoints).max().orElse(0);
        List<Player> winners = active.stream().filter(p -> p.getPoints() == max).toList();

        if (winners.size() >= 2) {
            String names = winners.stream().map(Player::getNickname).collect(Collectors.joining(" i "));
            fire(game, names + ": tytuł do podziału. Albo do rewanżu.");
            return;
        }
        if (max > 0 && winners.get(0).isLastAtMidpoint()) {
            fire(game, "Niespodzianka! " + winners.get(0).getNickname()
                    + " wraca z martwych i bierze wszystko.");
            return;
        }
        List<Player> zeroScorers = active.stream().filter(p -> p.getPoints() == 0).toList();
        if (!zeroScorers.isEmpty()) {
            String names = zeroScorers.stream().map(Player::getNickname).collect(Collectors.joining(", "));
            fire(game, names + ": liczy się udział. Serio. No prawie serio.");
        }
    }

    /** Called from GameService.pruneDisconnectedPlayers right before a quiet player is dropped. */
    void onPlayerDisconnected(Game game, Player disconnected) {
        List<Player> active = game.activePlayers();
        if (active.size() < 2) {
            return;
        }
        int max = active.stream().mapToInt(Player::getPoints).max().orElse(0);
        if (max > 0 && disconnected.getPoints() == max) {
            fire(game, disconnected.getNickname() + " zniknął w (dosłownie) najlepszym momencie.");
        }
    }

    private void fire(Game game, String text) {
        game.showEasterEgg(text);
        log.debug("Gra {}: easter egg — {}", game.getId(), text);
    }
}
