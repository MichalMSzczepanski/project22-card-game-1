package io.github.michalmszczepanski.project22_card_game_1.admin;

import io.github.michalmszczepanski.project22_card_game_1.card.Card;
import io.github.michalmszczepanski.project22_card_game_1.card.CardNotFoundException;
import io.github.michalmszczepanski.project22_card_game_1.card.CardPack;
import io.github.michalmszczepanski.project22_card_game_1.card.CardPackNotFoundException;
import io.github.michalmszczepanski.project22_card_game_1.card.CardPackRepository;
import io.github.michalmszczepanski.project22_card_game_1.card.CardRepository;
import io.github.michalmszczepanski.project22_card_game_1.card.CardType;
import io.github.michalmszczepanski.project22_card_game_1.card.InvalidCardActionException;
import io.github.michalmszczepanski.project22_card_game_1.card.PackAudience;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Transactional so each test rolls back — same reasoning as GameServiceTest. CardSeeder has
 * already run (ApplicationRunner, full Spring context) so {@code cardPackRepository.findAll()}
 * also contains the seeded packs — every test here creates its own pack and asserts on that.
 */
@SpringBootTest
@Transactional
class CardAdminServiceTest {

    @Autowired
    private CardAdminService cardAdminService;

    @Autowired
    private CardPackRepository cardPackRepository;

    @Autowired
    private CardRepository cardRepository;

    private CardPack newPack() {
        return cardAdminService.createPack("Testowy", true, BigDecimal.ZERO, PackAudience.FAMILY, null, null);
    }

    @Test
    void createsAndUpdatesAPack() {
        CardPack pack = newPack();
        assertThat(pack.isFree()).isTrue();
        assertThat(pack.getAudience()).isEqualTo(PackAudience.FAMILY);

        cardAdminService.updatePack(pack.getId(), "Testowy 2", false, new BigDecimal("9.00"), PackAudience.ADULT,
                LocalDate.of(2026, 12, 1), LocalDate.of(2027, 1, 1));

        CardPack updated = cardPackRepository.findById(pack.getId()).orElseThrow();
        assertThat(updated.getName()).isEqualTo("Testowy 2");
        assertThat(updated.isFree()).isFalse();
        assertThat(updated.getPricePln()).isEqualTo(new BigDecimal("9.00"));
        assertThat(updated.getAudience()).isEqualTo(PackAudience.ADULT);
        assertThat(updated.getAvailableFrom()).isEqualTo(LocalDate.of(2026, 12, 1));
    }

    @Test
    void rejectsBlankOrTooLongPackName() {
        assertThatThrownBy(() -> cardAdminService.createPack("  ", true, BigDecimal.ZERO, PackAudience.FAMILY, null, null))
                .isInstanceOf(InvalidCardActionException.class)
                .hasMessageContaining("Nazwa pakietu");
        assertThatThrownBy(() -> cardAdminService.createPack("x".repeat(101), true, BigDecimal.ZERO, PackAudience.FAMILY, null, null))
                .isInstanceOf(InvalidCardActionException.class);
    }

    @Test
    void updatingOrEnablingAMissingPackOrCardFails() {
        assertThatThrownBy(() -> cardAdminService.updatePack(999_999L, "X", true, null, PackAudience.FAMILY, null, null))
                .isInstanceOf(CardPackNotFoundException.class);
        assertThatThrownBy(() -> cardAdminService.setCardEnabled(999_999L, false))
                .isInstanceOf(CardNotFoundException.class);
    }

    @Test
    void createsAndUpdatesACard() {
        CardPack pack = newPack();
        Card card = cardAdminService.createCard("Pytanie testowe ___", CardType.QUESTION, pack.getId());
        assertThat(card.isEnabled()).isTrue();
        assertThat(card.getPack().getId()).isEqualTo(pack.getId());

        CardPack otherPack = cardAdminService.createPack("Testowy B", true, BigDecimal.ZERO, PackAudience.FAMILY, null, null);
        cardAdminService.updateCard(card.getId(), "Zmieniony tekst", CardType.ANSWER, otherPack.getId());

        Card updated = cardRepository.findById(card.getId()).orElseThrow();
        assertThat(updated.getText()).isEqualTo("Zmieniony tekst");
        assertThat(updated.getType()).isEqualTo(CardType.ANSWER);
        assertThat(updated.getPack().getId()).isEqualTo(otherPack.getId());
    }

    @Test
    void rejectsBlankOrTooLongCardText() {
        CardPack pack = newPack();
        assertThatThrownBy(() -> cardAdminService.createCard(" ", CardType.ANSWER, pack.getId()))
                .isInstanceOf(InvalidCardActionException.class)
                .hasMessageContaining("Tekst karty");
        assertThatThrownBy(() -> cardAdminService.createCard("x".repeat(501), CardType.ANSWER, pack.getId()))
                .isInstanceOf(InvalidCardActionException.class);
    }

    @Test
    void disablingACardExcludesItFromTheGameDrawPoolButKeepsItInTheRepository() {
        CardPack pack = newPack();
        Card card = cardAdminService.createCard("Jedyna karta", CardType.ANSWER, pack.getId());

        List<Card> beforeDisable = cardRepository.findByTypeAndEnabledTrueAndPack_IdIn(CardType.ANSWER, List.of(pack.getId()));
        assertThat(beforeDisable).contains(card);

        cardAdminService.setCardEnabled(card.getId(), false);

        List<Card> afterDisable = cardRepository.findByTypeAndEnabledTrueAndPack_IdIn(CardType.ANSWER, List.of(pack.getId()));
        assertThat(afterDisable).doesNotContain(card);
        assertThat(cardRepository.findById(card.getId())).isPresent(); // still there, just disabled

        cardAdminService.setCardEnabled(card.getId(), true);
        assertThat(cardRepository.findByTypeAndEnabledTrueAndPack_IdIn(CardType.ANSWER, List.of(pack.getId()))).contains(card);
    }

    @Test
    void importsOneCardPerLineSkippingBlankLines() {
        CardPack pack = newPack();
        MockMultipartFile file = new MockMultipartFile("file", "karty.csv", "text/csv",
                "Pierwsza karta\n\nDruga karta\n   \nTrzecia karta".getBytes(StandardCharsets.UTF_8));

        int imported = cardAdminService.importCards(pack.getId(), CardType.ANSWER, file);

        assertThat(imported).isEqualTo(3);
        List<Card> cards = cardRepository.findByPack_IdOrderByTypeAscIdAsc(pack.getId());
        assertThat(cards).extracting(Card::getText)
                .containsExactly("Pierwsza karta", "Druga karta", "Trzecia karta");
        assertThat(cards).allMatch(c -> c.getType() == CardType.ANSWER);
    }

    @Test
    void rejectsEmptyImportFile() {
        CardPack pack = newPack();
        MockMultipartFile empty = new MockMultipartFile("file", "karty.csv", "text/csv", new byte[0]);
        assertThatThrownBy(() -> cardAdminService.importCards(pack.getId(), CardType.ANSWER, empty))
                .isInstanceOf(InvalidCardActionException.class);

        MockMultipartFile blankOnly = new MockMultipartFile("file", "karty.csv", "text/csv",
                "   \n\n".getBytes(StandardCharsets.UTF_8));
        assertThatThrownBy(() -> cardAdminService.importCards(pack.getId(), CardType.ANSWER, blankOnly))
                .isInstanceOf(InvalidCardActionException.class)
                .hasMessageContaining("żadnych kart");
    }

    @Test
    void adminViewDefaultsToTheFirstPackAndListsOnlyItsCards() {
        CardPack pack = newPack();
        cardAdminService.createCard("A1", CardType.ANSWER, pack.getId());
        cardAdminService.createCard("A2", CardType.ANSWER, pack.getId());

        CardAdminView view = cardAdminService.adminView(pack.getId());

        assertThat(view.selectedPack().id()).isEqualTo(pack.getId());
        assertThat(view.selectedPack().cardCount()).isEqualTo(2);
        assertThat(view.cards()).extracting(CardAdminView.CardRow::text).containsExactlyInAnyOrder("A1", "A2");
        assertThat(view.packs()).extracting(CardAdminView.PackRow::id).contains(pack.getId());
    }
}
