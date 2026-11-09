package io.github.michalmszczepanski.project22_card_game_1.game;

public class InvalidGameActionException extends RuntimeException {

    public InvalidGameActionException(String message) {
        super(message);
    }
}
