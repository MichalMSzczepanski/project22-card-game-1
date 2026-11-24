package io.github.michalmszczepanski.project22_card_game_1.web;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/** Etap 1 (FORMAL.md): krótkie, statyczne strony informacyjne — nic do pobrania z bazy. */
@Controller
public class InfoController {

    @GetMapping("/o-grze")
    public String about() {
        return "o-grze";
    }

    @GetMapping("/jak-grac")
    public String howToPlay() {
        return "jak-grac";
    }

    @GetMapping("/polityka-prywatnosci")
    public String privacyPolicy() {
        return "polityka-prywatnosci";
    }
}
