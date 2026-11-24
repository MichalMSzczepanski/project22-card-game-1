package io.github.michalmszczepanski.project22_card_game_1.game;

import io.github.michalmszczepanski.project22_card_game_1.card.CardPack;
import io.github.michalmszczepanski.project22_card_game_1.card.CardPackRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Own {@code game.max-active-games} override (separate Spring context from
 * {@link GameServiceTest}) so the cap can be hit with just 2 games instead of 50.
 */
@SpringBootTest(properties = "game.max-active-games=2")
@Transactional
class GameActiveLimitTest {

    @Autowired
    private GameService gameService;

    @Autowired
    private CardPackRepository cardPackRepository;

    private List<CardPack> freePacks() {
        return cardPackRepository.findAll().stream().filter(CardPack::isFree).toList();
    }

    @Test
    void refusesANewGameOnceTheActiveGameCapIsReached() {
        gameService.createGame(freePacks(), 10);
        gameService.createGame(freePacks(), 10);

        assertThatThrownBy(() -> gameService.createGame(freePacks(), 10))
                .isInstanceOf(InvalidGameActionException.class)
                .hasMessage("Za dużo aktywnych gier w tej chwili — spróbuj ponownie za chwilę.");
    }

    @Test
    void finishingAGameFreesUpARoomUnderTheCap() {
        Game first = gameService.createGame(freePacks(), 1);
        gameService.createGame(freePacks(), 10);

        // A FINISHED game doesn't count against the cap — directly flip the status rather than
        // play a full round, since this test only cares about the counting, not round mechanics.
        first.setStatus(GameStatus.FINISHED);

        assertThat(gameService.createGame(freePacks(), 10)).isNotNull();
    }
}
