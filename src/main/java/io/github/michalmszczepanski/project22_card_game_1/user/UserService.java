package io.github.michalmszczepanski.project22_card_game_1.user;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class UserService {

    private static final Logger log = LoggerFactory.getLogger(UserService.class);
    private static final int MIN_USERNAME_LENGTH = 3;
    private static final int MAX_USERNAME_LENGTH = 30;
    private static final int MIN_PASSWORD_LENGTH = 8;

    private final AppUserRepository appUserRepository;
    private final PasswordEncoder passwordEncoder;

    public UserService(AppUserRepository appUserRepository, PasswordEncoder passwordEncoder) {
        this.appUserRepository = appUserRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public AppUser register(String username, String rawPassword) {
        String trimmed = username == null ? "" : username.trim();

        if (trimmed.length() < MIN_USERNAME_LENGTH || trimmed.length() > MAX_USERNAME_LENGTH) {
            throw new InvalidRegistrationException(
                    "Login musi mieć od " + MIN_USERNAME_LENGTH + " do " + MAX_USERNAME_LENGTH + " znaków.");
        }
        if (rawPassword == null || rawPassword.length() < MIN_PASSWORD_LENGTH) {
            throw new InvalidRegistrationException("Hasło musi mieć minimum " + MIN_PASSWORD_LENGTH + " znaków.");
        }
        if (appUserRepository.existsByUsername(trimmed)) {
            log.debug("Rejestracja odrzucona — login zajęty: {}", trimmed);
            throw new UsernameTakenException(trimmed);
        }

        AppUser user = appUserRepository.save(new AppUser(trimmed, passwordEncoder.encode(rawPassword)));
        log.info("Zarejestrowano nowego użytkownika (LOCAL): {}", trimmed);
        return user;
    }
}
