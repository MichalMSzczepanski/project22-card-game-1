package io.github.michalmszczepanski.project22_card_game_1.user;

public class InvalidRegistrationException extends RuntimeException {

    public InvalidRegistrationException(String message) {
        super(message);
    }
}
