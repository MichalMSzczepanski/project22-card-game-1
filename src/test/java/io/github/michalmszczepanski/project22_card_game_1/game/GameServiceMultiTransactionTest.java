package io.github.michalmszczepanski.project22_card_game_1.game;

import io.github.michalmszczepanski.project22_card_game_1.card.CardPack;
import io.github.michalmszczepanski.project22_card_game_1.card.CardPackRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Deliberately NOT @Transactional, unlike {@link GameServiceTest} — every GameService call
 * here commits for real, round-tripping through separate transactions exactly like real HTTP
 * requests do. This is the only way to catch a real regression that was hiding behind the
 * single-shared-session trick: committing a new Round (a @OneToOne(cascade=ALL,
 * orphanRemoval=true) replacement) in the same flush as additions to Game's
 * usedAnswerCardIds/usedQuestionCardIds (@ElementCollection) silently dropped all but one of
 * those additions. That corrupted the "don't redeal the same card" tracking without an obvious
 * symptom until, purely by chance, a later draw collided with an "untracked" already-dealt
 * card and crashed on the unique constraint — see GameService.dealAndCommit for the fix.
 */
@SpringBootTest
class GameServiceMultiTransactionTest {

    @Autowired
    private GameService gameService;

    @Autowired
    private CardPackRepository cardPackRepository;

    @Test
    void usedCardTrackingSurvivesAFullGameAcrossRealCommits() {
        Random random = new Random();
        List<CardPack> freePacks = cardPackRepository.findAll().stream().filter(CardPack::isFree).toList();

        Game game = gameService.createGame(freePacks);
        String gameId = game.getId();
        for (String nick : new String[] {"Ala", "Bartek", "Celina"}) {
            gameService.join(gameId, nick);
        }

        gameService.startGame(gameId);

        for (int round = 1; round <= Game.TOTAL_ROUNDS; round++) {
            game = gameService.getGame(gameId);
            Round currentRound = game.getCurrentRound();
            for (Player p : game.answerersInOrder()) {
                var card = p.getHand().get(random.nextInt(p.getHand().size()));
                gameService.submitAnswer(gameId, p.getId(), card.getId());
            }

            game = gameService.getGame(gameId);
            currentRound = game.getCurrentRound();
            int rank = 1;
            for (Submission s : currentRound.getSubmissions()) {
                gameService.submitRanking(gameId, currentRound.getLeader().getId(), s.getId(), rank++);
            }

            // Would throw (unique constraint on game_used_answer_cards, or an equivalent
            // DataIntegrityViolationException) if the used-card tracking lost entries as it did
            // before the fix — a "fresh" pick could collide with a card actually already dealt.
            gameService.nextRound(gameId);
        }

        game = gameService.getGame(gameId);
        assertThat(game.getStatus()).isEqualTo(GameStatus.FINISHED);
    }
}
