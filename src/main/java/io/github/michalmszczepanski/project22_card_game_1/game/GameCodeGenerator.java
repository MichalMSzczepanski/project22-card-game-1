package io.github.michalmszczepanski.project22_card_game_1.game;

import org.springframework.stereotype.Component;

import java.security.SecureRandom;

@Component
public class GameCodeGenerator {

    private static final String ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final int LENGTH = 5;

    private final SecureRandom random = new SecureRandom();
    private final GameRepository gameRepository;

    public GameCodeGenerator(GameRepository gameRepository) {
        this.gameRepository = gameRepository;
    }

    public String generate() {
        String code;
        do {
            StringBuilder sb = new StringBuilder(LENGTH);
            for (int i = 0; i < LENGTH; i++) {
                sb.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
            }
            code = sb.toString();
        } while (gameRepository.existsById(code));
        return code;
    }
}
