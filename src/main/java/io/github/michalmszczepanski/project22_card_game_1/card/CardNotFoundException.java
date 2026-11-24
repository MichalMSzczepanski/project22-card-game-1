package io.github.michalmszczepanski.project22_card_game_1.card;

public class CardNotFoundException extends RuntimeException {

    public CardNotFoundException(Long cardId) {
        super("Nie znaleziono karty: " + cardId);
    }
}
