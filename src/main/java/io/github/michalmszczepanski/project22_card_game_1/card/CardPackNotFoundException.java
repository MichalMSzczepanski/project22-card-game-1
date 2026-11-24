package io.github.michalmszczepanski.project22_card_game_1.card;

public class CardPackNotFoundException extends RuntimeException {

    public CardPackNotFoundException(Long packId) {
        super("Nie znaleziono pakietu: " + packId);
    }
}
