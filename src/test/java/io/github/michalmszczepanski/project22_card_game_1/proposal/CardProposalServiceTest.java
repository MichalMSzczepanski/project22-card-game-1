package io.github.michalmszczepanski.project22_card_game_1.proposal;

import io.github.michalmszczepanski.project22_card_game_1.card.Card;
import io.github.michalmszczepanski.project22_card_game_1.card.CardPack;
import io.github.michalmszczepanski.project22_card_game_1.card.CardPackRepository;
import io.github.michalmszczepanski.project22_card_game_1.card.CardRepository;
import io.github.michalmszczepanski.project22_card_game_1.card.CardSeeder;
import io.github.michalmszczepanski.project22_card_game_1.card.CardType;
import io.github.michalmszczepanski.project22_card_game_1.user.AppUser;
import io.github.michalmszczepanski.project22_card_game_1.user.AppUserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Transactional so each test rolls back — same reasoning as GameServiceTest. */
@SpringBootTest
@Transactional
class CardProposalServiceTest {

    @Autowired
    private CardProposalService cardProposalService;

    @Autowired
    private AppUserRepository appUserRepository;

    @Autowired
    private CardPackRepository cardPackRepository;

    @Autowired
    private CardRepository cardRepository;

    private AppUser newUser(String username) {
        return appUserRepository.save(new AppUser(username, "hash"));
    }

    @Test
    void proposingACardAddsItToTheQueueAsPending() {
        AppUser user = newUser("gracz1");

        cardProposalService.propose(user, "Nowa karta", CardType.ANSWER);

        MyProposalRow row = cardProposalService.myProposals(user).get(0);
        assertThat(row.status()).isEqualTo(CardProposalStatus.PENDING);
        assertThat(row.text()).isEqualTo("Nowa karta");

        PendingProposalRow pending = cardProposalService.pendingProposals().stream()
                .filter(p -> p.proposerUsername().equals("gracz1")).findFirst().orElseThrow();
        assertThat(pending.text()).isEqualTo("Nowa karta");
    }

    @Test
    void rejectsBlankOrTooLongText() {
        AppUser user = newUser("gracz2");
        assertThatThrownBy(() -> cardProposalService.propose(user, "  ", CardType.ANSWER))
                .isInstanceOf(InvalidProposalException.class)
                .hasMessageContaining("Tekst karty");
        assertThatThrownBy(() -> cardProposalService.propose(user, "x".repeat(501), CardType.ANSWER))
                .isInstanceOf(InvalidProposalException.class);
    }

    @Test
    void rejectsProposalsPastTheLifetimeCap() {
        AppUser user = newUser("gracz3");
        for (int i = 0; i < 100; i++) {
            cardProposalService.propose(user, "Karta " + i, CardType.ANSWER);
        }
        assertThatThrownBy(() -> cardProposalService.propose(user, "Karta 101", CardType.ANSWER))
                .isInstanceOf(InvalidProposalException.class)
                .hasMessageContaining("Limit 100 propozycji na konto");
    }

    @Test
    void approvingCreatesARealCardInTheUserProposedPackAndMarksTheProposalApproved() {
        AppUser user = newUser("gracz4");
        cardProposalService.propose(user, "Super karta", CardType.QUESTION);
        Long proposalId = cardProposalService.pendingProposals().stream()
                .filter(p -> p.proposerUsername().equals("gracz4")).findFirst().orElseThrow().id();

        cardProposalService.approve(proposalId);

        MyProposalRow row = cardProposalService.myProposals(user).get(0);
        assertThat(row.status()).isEqualTo(CardProposalStatus.APPROVED);
        assertThat(cardProposalService.pendingProposals()).noneMatch(p -> p.id().equals(proposalId));

        CardPack pack = cardPackRepository.findByName(CardSeeder.USER_PROPOSED_PACK_NAME).orElseThrow();
        Card created = cardRepository.findByPack_IdOrderByTypeAscIdAsc(pack.getId()).stream()
                .filter(c -> c.getText().equals("Super karta")).findFirst().orElseThrow();
        assertThat(created.getType()).isEqualTo(CardType.QUESTION);
        assertThat(created.isEnabled()).isTrue();
    }

    @Test
    void rejectingStoresTheOptionalNoteAndClosesTheProposal() {
        AppUser user = newUser("gracz5");
        cardProposalService.propose(user, "Słaba karta", CardType.ANSWER);
        Long proposalId = cardProposalService.pendingProposals().stream()
                .filter(p -> p.proposerUsername().equals("gracz5")).findFirst().orElseThrow().id();

        cardProposalService.reject(proposalId, "nie na temat");

        MyProposalRow row = cardProposalService.myProposals(user).get(0);
        assertThat(row.status()).isEqualTo(CardProposalStatus.REJECTED);
        assertThat(row.reviewNote()).isEqualTo("nie na temat");
    }

    @Test
    void reviewingAnAlreadyReviewedProposalFails() {
        AppUser user = newUser("gracz6");
        cardProposalService.propose(user, "Karta", CardType.ANSWER);
        Long proposalId = cardProposalService.pendingProposals().stream()
                .filter(p -> p.proposerUsername().equals("gracz6")).findFirst().orElseThrow().id();
        cardProposalService.reject(proposalId, null);

        assertThatThrownBy(() -> cardProposalService.approve(proposalId))
                .isInstanceOf(InvalidProposalException.class)
                .hasMessageContaining("już została rozpatrzona");
    }

    @Test
    void reviewingAMissingProposalFails() {
        assertThatThrownBy(() -> cardProposalService.approve(999_999L))
                .isInstanceOf(ProposalNotFoundException.class);
    }
}
