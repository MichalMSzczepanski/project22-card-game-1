package io.github.michalmszczepanski.project22_card_game_1.web;

import io.github.michalmszczepanski.project22_card_game_1.game.Game;
import io.github.michalmszczepanski.project22_card_game_1.game.GameStatus;
import io.github.michalmszczepanski.project22_card_game_1.game.Player;
import io.github.michalmszczepanski.project22_card_game_1.game.Ranking;
import io.github.michalmszczepanski.project22_card_game_1.game.Round;
import io.github.michalmszczepanski.project22_card_game_1.game.Submission;

import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;

/**
 * Flattens the live {@link Game} state into exactly what the Thymeleaf template
 * needs for the requesting player, so the view has no game-rule logic of its own.
 */
public record GameViewModel(
        String gameId,
        GameStatus status,
        int roundNumber,
        int totalRounds,
        String questionCardText,
        List<PlayerRow> players,
        boolean inGame,
        boolean isLeader,
        boolean hasSubmitted,
        List<HandCard> hand,
        List<RankTarget> rankTargets,
        List<RankOption> rankOptions,
        List<RevealRow> reveal,
        boolean canStart,
        boolean autoRefresh,
        List<String> winnerNicknames,
        boolean familyMode,
        List<PlayerRow> finalStandings,
        int secondsLeft,
        int answeredCount,
        int answerersTotal,
        int rankedCount,
        int ranksNeededTotal,
        String justLeftNickname
) {

    public record PlayerRow(String nickname, int points, boolean leader, boolean me) {
    }

    public record HandCard(Long id, String text) {
    }

    public record RankTarget(String submissionId, String cardText) {
    }

    public record RankOption(int position, int points) {
    }

    /** rank/points are 0 when a round timed out before the leader placed this submission. */
    public record RevealRow(String authorNickname, String cardText, int rank, int points) {
    }

    public static GameViewModel of(Game game, String currentPlayerId) {
        Round round = game.getCurrentRound();
        Optional<Player> me = Optional.ofNullable(currentPlayerId).flatMap(game::findPlayer).filter(p -> !p.isLeft());

        List<PlayerRow> players = game.activePlayers().stream()
                .map(p -> new PlayerRow(
                        p.getNickname(),
                        p.getPoints(),
                        round != null && round.getLeader().getId().equals(p.getId()),
                        me.isPresent() && me.get().getId().equals(p.getId())))
                .toList();

        boolean isLeader = me.isPresent() && round != null && round.getLeader().getId().equals(me.get().getId());
        boolean hasSubmitted = me.isPresent() && round != null && round.hasSubmitted(me.get());

        List<HandCard> hand = List.of();
        List<RankTarget> rankTargets = List.of();
        List<RankOption> rankOptions = List.of();
        List<RevealRow> reveal = List.of();

        if (me.isPresent() && round != null) {
            Player player = me.get();

            if (game.getStatus() == GameStatus.ANSWERING && !isLeader && !hasSubmitted) {
                hand = player.getHand().stream().map(c -> new HandCard(c.getId(), c.getText())).toList();
            }

            if (game.getStatus() == GameStatus.RANKING && isLeader) {
                int total = round.getSubmissions().size();
                rankTargets = round.unrankedSubmissions().stream()
                        .map(s -> new RankTarget(s.getId(), s.getCard().getText()))
                        .toList();
                rankOptions = IntStream.rangeClosed(1, total)
                        .filter(rank -> !round.usedRanks().contains(rank))
                        .mapToObj(rank -> new RankOption(rank, total - rank + 1))
                        .toList();
            }
        }

        if (round != null && game.getStatus() == GameStatus.REVEALED) {
            int total = round.getSubmissions().size();
            reveal = round.getSubmissions().stream()
                    .map(s -> rankingFor(round, s)
                            .map(r -> new RevealRow(s.getAuthor().getNickname(), s.getCard().getText(),
                                    r.getRank(), total - r.getRank() + 1))
                            .orElseGet(() -> new RevealRow(s.getAuthor().getNickname(), s.getCard().getText(), 0, 0)))
                    .toList();
        }

        boolean waitingForMyAction =
                (game.getStatus() == GameStatus.ANSWERING && !isLeader && !hasSubmitted)
                        || (game.getStatus() == GameStatus.RANKING && isLeader && !rankTargets.isEmpty());
        boolean autoRefresh = game.getStatus() != GameStatus.FINISHED && !waitingForMyAction;

        List<String> winners = List.of();
        List<PlayerRow> finalStandings = List.of();
        if (game.getStatus() == GameStatus.FINISHED) {
            int max = game.activePlayers().stream().mapToInt(Player::getPoints).max().orElse(0);
            winners = game.activePlayers().stream()
                    .filter(p -> p.getPoints() == max)
                    .sorted(Comparator.comparing(Player::getNickname))
                    .map(Player::getNickname)
                    .toList();
            finalStandings = game.activePlayers().stream()
                    .sorted(Comparator.comparingInt(Player::getPoints).reversed())
                    .map(p -> new PlayerRow(p.getNickname(), p.getPoints(), false, me.isPresent() && me.get().getId().equals(p.getId())))
                    .toList();
        }

        int answerersTotal = game.answerersInOrder().size();
        int answeredCount = round == null ? 0 : round.getSubmissions().size();
        // The leader places every submission (one rank each), so the target is the same as answerersTotal.
        int ranksNeededTotal = answerersTotal;
        int rankedCount = round == null ? 0 : round.getRankings().size();

        int secondsLeft = 0;
        if (round != null) {
            int timeoutSeconds = game.getStatus() == GameStatus.ANSWERING ? Game.ANSWER_TIMEOUT_SECONDS
                    : game.getStatus() == GameStatus.RANKING ? Game.RANKING_TIMEOUT_SECONDS : -1;
            if (timeoutSeconds >= 0) {
                long elapsed = Duration.between(round.getPhaseStartedAt(), Instant.now()).getSeconds();
                secondsLeft = (int) Math.max(0, timeoutSeconds - elapsed);
            }
        }

        String justLeftNickname = null;
        if (game.getLastLeftNickname() != null
                && Duration.between(game.getLastLeftAt(), Instant.now()).getSeconds() < Game.LEAVE_BANNER_SECONDS) {
            justLeftNickname = game.getLastLeftNickname();
        }

        return new GameViewModel(
                game.getId(),
                game.getStatus(),
                round == null ? 0 : round.getNumber(),
                Game.TOTAL_ROUNDS,
                round == null ? null : round.getQuestionCard().getText(),
                players,
                me.isPresent(),
                isLeader,
                hasSubmitted,
                hand,
                rankTargets,
                rankOptions,
                reveal,
                game.getStatus() == GameStatus.LOBBY && game.activePlayers().size() >= Game.MIN_PLAYERS_TO_START,
                autoRefresh,
                winners,
                game.isFamilyMode(),
                finalStandings,
                secondsLeft,
                answeredCount,
                answerersTotal,
                rankedCount,
                ranksNeededTotal,
                justLeftNickname
        );
    }

    private static Optional<Ranking> rankingFor(Round round, Submission submission) {
        return round.getRankings().stream()
                .filter(r -> r.getSubmission().getId().equals(submission.getId()))
                .findFirst();
    }
}
