package io.github.michalmszczepanski.project22_card_game_1.game;

public class GameNotFoundException extends RuntimeException {

    public GameNotFoundException(String gameId) {
        super("Nie znaleziono gry: " + gameId);
    }
}
