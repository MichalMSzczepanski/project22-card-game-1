package io.github.michalmszczepanski.project22_card_game_1.admin;

import io.github.michalmszczepanski.project22_card_game_1.card.Card;
import io.github.michalmszczepanski.project22_card_game_1.card.CardNotFoundException;
import io.github.michalmszczepanski.project22_card_game_1.card.CardPack;
import io.github.michalmszczepanski.project22_card_game_1.card.CardRepository;
import io.github.michalmszczepanski.project22_card_game_1.card.CardType;
import io.github.michalmszczepanski.project22_card_game_1.card.InvalidCardActionException;
import io.github.michalmszczepanski.project22_card_game_1.card.PackAudience;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Transactional so each test rolls back — same reasoning as GameServiceTest/CardAdminServiceTest. */
@SpringBootTest
@Transactional
class CardReportServiceTest {

    @Autowired
    private CardReportService cardReportService;

    @Autowired
    private CardAdminService cardAdminService;

    @Autowired
    private CardRepository cardRepository;

    private Card newCard() {
        CardPack pack = cardAdminService.createPack("Zgłoszenia-testowy", true, BigDecimal.ZERO, PackAudience.FAMILY, null, null);
        return cardAdminService.createCard("Karta do zgłoszenia", CardType.ANSWER, pack.getId());
    }

    @Test
    void reportingACardAddsItToTheOpenQueue() {
        Card card = newCard();

        cardReportService.reportCard(card.getId(), "Ala", "nieśmieszna");

        CardReportRow row = cardReportService.openReports().stream()
                .filter(r -> r.cardId().equals(card.getId())).findFirst().orElseThrow();
        assertThat(row.reporterNickname()).isEqualTo("Ala");
        assertThat(row.note()).isEqualTo("nieśmieszna");
        assertThat(row.cardText()).isEqualTo("Karta do zgłoszenia");
    }

    @Test
    void blankNoteIsStoredAsNull() {
        Card card = newCard();
        cardReportService.reportCard(card.getId(), "Ala", "   ");

        CardReportRow row = cardReportService.openReports().stream()
                .filter(r -> r.cardId().equals(card.getId())).findFirst().orElseThrow();
        assertThat(row.note()).isNull();
    }

    @Test
    void reportingAMissingCardFails() {
        assertThatThrownBy(() -> cardReportService.reportCard(999_999L, "Ala", null))
                .isInstanceOf(CardNotFoundException.class);
    }

    @Test
    void rejectsTooLongNote() {
        Card card = newCard();
        assertThatThrownBy(() -> cardReportService.reportCard(card.getId(), "Ala", "x".repeat(301)))
                .isInstanceOf(InvalidCardActionException.class);
    }

    @Test
    void removingAReportedCardDisablesItAndClearsAllItsReports() {
        Card card = newCard();
        cardReportService.reportCard(card.getId(), "Ala", null);
        cardReportService.reportCard(card.getId(), "Bartek", "zgadzam się z Alą");

        cardReportService.removeReportedCard(card.getId());

        assertThat(cardReportService.openReports()).noneMatch(r -> r.cardId().equals(card.getId()));
        assertThat(cardRepository.findById(card.getId()).orElseThrow().isEnabled()).isFalse();
    }

    @Test
    void fixingAReportedCardChangesTextAndClearsAllItsReports() {
        Card card = newCard();
        cardReportService.reportCard(card.getId(), "Ala", null);
        cardReportService.reportCard(card.getId(), "Bartek", null);

        cardReportService.fixReportedCard(card.getId(), "Naprawiony tekst");

        assertThat(cardReportService.openReports()).noneMatch(r -> r.cardId().equals(card.getId()));
        assertThat(cardRepository.findById(card.getId()).orElseThrow().getText()).isEqualTo("Naprawiony tekst");
    }

    @Test
    void dismissingOneReportLeavesOthersForTheSameCardOpen() {
        Card card = newCard();
        cardReportService.reportCard(card.getId(), "Ala", null);
        cardReportService.reportCard(card.getId(), "Bartek", null);

        Long firstReportId = cardReportService.openReports().stream()
                .filter(r -> r.cardId().equals(card.getId())).findFirst().orElseThrow().reportId();
        cardReportService.dismissReport(firstReportId);

        assertThat(cardReportService.openReports()).filteredOn(r -> r.cardId().equals(card.getId())).hasSize(1);
    }

    @Test
    void dismissingAnAlreadyGoneReportIsANoop() {
        cardReportService.dismissReport(999_999L);
    }
}
