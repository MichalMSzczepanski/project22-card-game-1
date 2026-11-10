package io.github.michalmszczepanski.project22_card_game_1.game;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;

@Component
@RequiredArgsConstructor
public class GameCodeGenerator {

    // Deliberately excludes ambiguous characters (I/O/0/1) so a code read aloud or typed on a
    // phone isn't misheard/mistyped — not externalized, changing it needs the same care.
    private static final String ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";

    private final SecureRandom random = new SecureRandom();
    private final GameRepository gameRepository;
    private final GameProperties gameProperties;

    public String generate() {
        int length = gameProperties.codeLength();
        String code;
        do {
            StringBuilder sb = new StringBuilder(length);
            for (int i = 0; i < length; i++) {
                sb.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
            }
            code = sb.toString();
        } while (gameRepository.existsById(code));
        return code;
    }
}
