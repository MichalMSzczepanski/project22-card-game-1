package io.github.michalmszczepanski.project22_card_game_1.user;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserService {

    private final AppUserRepository appUserRepository;
    private final PasswordEncoder passwordEncoder;
    private final UserProperties userProperties;

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
}
