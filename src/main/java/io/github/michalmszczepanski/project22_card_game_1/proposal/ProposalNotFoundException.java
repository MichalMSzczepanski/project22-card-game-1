package io.github.michalmszczepanski.project22_card_game_1.proposal;

public class ProposalNotFoundException extends RuntimeException {

    public ProposalNotFoundException(Long proposalId) {
        super("Nie znaleziono propozycji: " + proposalId);
    }
}
