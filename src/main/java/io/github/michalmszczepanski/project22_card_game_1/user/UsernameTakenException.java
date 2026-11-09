package io.github.michalmszczepanski.project22_card_game_1.user;

public class UsernameTakenException extends RuntimeException {

    public UsernameTakenException(String username) {
        super("Nazwa użytkownika '" + username + "' jest już zajęta.");
    }
}
