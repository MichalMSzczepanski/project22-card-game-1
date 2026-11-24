package io.github.michalmszczepanski.project22_card_game_1.admin;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * The only way an {@link Operator} account is ever created — no form, no endpoint. Set
 * {@code OPERATOR_USERNAME}/{@code OPERATOR_PASSWORD} as env vars on the real deployment
 * (never in the repo); leave them unset locally and no account exists.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class OperatorSeeder implements ApplicationRunner {

    private final OperatorRepository operatorRepository;
    private final PasswordEncoder passwordEncoder;
    private final OperatorProperties operatorProperties;

    @Override
    public void run(ApplicationArguments args) {
        String username = operatorProperties.bootstrapUsername();
        String password = operatorProperties.bootstrapPassword();
        if (username == null || username.isBlank() || password == null || password.isBlank()) {
            return;
        }
        if (operatorRepository.existsByUsername(username)) {
            return;
        }
        operatorRepository.save(new Operator(username, passwordEncoder.encode(password)));
        log.info("Utworzono konto operatora: {}", username);
    }
}
