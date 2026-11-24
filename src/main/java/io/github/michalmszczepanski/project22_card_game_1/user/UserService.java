package io.github.michalmszczepanski.project22_card_game_1.user;

import io.github.michalmszczepanski.project22_card_game_1.proposal.CardProposalRepository;
import io.github.michalmszczepanski.project22_card_game_1.stats.GameScoreRecordRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * {@code deleteAccount} pulls in repositories from {@code proposal}/{@code stats} — a deliberate,
 * narrow exception to this package's usual "depends on nothing" layering (polityka prywatności:
 * "prawo do usunięcia" needs one place that knows every table an account's personal data lives
 * in). Only repositories, never their services, to keep the edge as thin as possible.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class UserService {

    private final AppUserRepository appUserRepository;
    private final PasswordEncoder passwordEncoder;
    private final UserProperties userProperties;
    private final CardProposalRepository cardProposalRepository;
    private final GameScoreRecordRepository gameScoreRecordRepository;

    public void register(String username, String rawPassword) {
        String trimmed = username == null ? "" : username.trim();

        if (trimmed.length() < userProperties.minUsernameLength() || trimmed.length() > userProperties.maxUsernameLength()) {
            throw new InvalidRegistrationException(
                    "Login musi mieć od " + userProperties.minUsernameLength() + " do " + userProperties.maxUsernameLength() + " znaków.");
        }
        if (rawPassword == null || rawPassword.length() < userProperties.minPasswordLength()) {
            throw new InvalidRegistrationException("Hasło musi mieć minimum " + userProperties.minPasswordLength() + " znaków.");
        }
        if (appUserRepository.existsByUsername(trimmed)) {
            log.debug("Rejestracja odrzucona — login zajęty: {}", trimmed);
            throw new UsernameTakenException(trimmed);
        }

        appUserRepository.save(new AppUser(trimmed, passwordEncoder.encode(rawPassword)));
        log.info("Zarejestrowano nowego użytkownika (LOCAL): {}", trimmed);
    }

    /**
     * "Prawo do usunięcia" (polityka prywatności) — self-service, {@code /me/account}. Removes
     * everything personal: the account itself, its card proposals (the resulting Card, if any was
     * accepted, stays — it's anonymous game content, not personal data) and its score history.
     * {@code Player}/game rows are untouched: {@code Player.appUserId} is a plain column, not a
     * foreign key, so nothing there references this row.
     */
    @Transactional
    public void deleteAccount(AppUser user) {
        cardProposalRepository.deleteByProposer(user);
        gameScoreRecordRepository.deleteByUser(user);
        appUserRepository.delete(user);
        log.info("Konto usunięte (appUserId={})", user.getId());
    }
}
