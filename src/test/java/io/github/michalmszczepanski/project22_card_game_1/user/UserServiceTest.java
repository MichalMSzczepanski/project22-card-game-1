package io.github.michalmszczepanski.project22_card_game_1.user;

import io.github.michalmszczepanski.project22_card_game_1.card.CardType;
import io.github.michalmszczepanski.project22_card_game_1.proposal.CardProposalRepository;
import io.github.michalmszczepanski.project22_card_game_1.proposal.CardProposalService;
import io.github.michalmszczepanski.project22_card_game_1.stats.GameScoreRecord;
import io.github.michalmszczepanski.project22_card_game_1.stats.GameScoreRecordRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Transactional so each test rolls back — same reasoning as GameServiceTest. */
@SpringBootTest
@Transactional
class UserServiceTest {

    @Autowired
    private UserService userService;

    @Autowired
    private AppUserRepository appUserRepository;

    @Autowired
    private UserProperties userProperties;

    @Autowired
    private CardProposalRepository cardProposalRepository;

    @Autowired
    private CardProposalService cardProposalService;

    @Autowired
    private GameScoreRecordRepository gameScoreRecordRepository;

    @Test
    void registersALocalAccountWithAHashedPassword() {
        userService.register("nowyGracz", "poprawneHaslo123");

        AppUser user = appUserRepository.findByUsername("nowyGracz").orElseThrow();
        assertThat(user.getAuthProvider()).isEqualTo(AuthProvider.LOCAL);
        assertThat(user.getPasswordHash()).isNotEqualTo("poprawneHaslo123");
    }

    @Test
    void trimsWhitespaceFromTheUsername() {
        userService.register("  spacje  ", "poprawneHaslo123");
        assertThat(appUserRepository.existsByUsername("spacje")).isTrue();
    }

    @Test
    void rejectsADuplicateUsername() {
        userService.register("zajety", "poprawneHaslo123");
        assertThatThrownBy(() -> userService.register("zajety", "inneHaslo456"))
                .isInstanceOf(UsernameTakenException.class)
                .hasMessageContaining("zajety");
    }

    @Test
    void rejectsAUsernameOutsideTheConfiguredLengthRange() {
        assertThatThrownBy(() -> userService.register("x".repeat(userProperties.minUsernameLength() - 1), "poprawneHaslo123"))
                .isInstanceOf(InvalidRegistrationException.class)
                .hasMessageContaining("Login musi mieć");
        assertThatThrownBy(() -> userService.register("x".repeat(userProperties.maxUsernameLength() + 1), "poprawneHaslo123"))
                .isInstanceOf(InvalidRegistrationException.class);
    }

    @Test
    void rejectsAPasswordShorterThanConfiguredMinimum() {
        assertThatThrownBy(() -> userService.register("gracz", "x".repeat(userProperties.minPasswordLength() - 1)))
                .isInstanceOf(InvalidRegistrationException.class)
                .hasMessageContaining("Hasło musi mieć");
    }

    @Test
    void rejectsANullPassword() {
        assertThatThrownBy(() -> userService.register("gracz", null))
                .isInstanceOf(InvalidRegistrationException.class);
    }

    @Test
    void deletingAnAccountRemovesItAndItsOwnData() {
        userService.register("usuwany", "poprawneHaslo123");
        AppUser user = appUserRepository.findByUsername("usuwany").orElseThrow();

        cardProposalService.propose(user, "Propozycja do usunięcia", CardType.ANSWER);
        gameScoreRecordRepository.save(new GameScoreRecord(user, "ABCDE", 7, true));

        Long userId = user.getId();
        userService.deleteAccount(user);

        assertThat(appUserRepository.findById(userId)).isEmpty();
        assertThat(cardProposalRepository.findByProposerOrderByCreatedAtDesc(user)).isEmpty();
        assertThat(gameScoreRecordRepository.findByUserOrderByPlayedAtDesc(user)).isEmpty();
    }
}
