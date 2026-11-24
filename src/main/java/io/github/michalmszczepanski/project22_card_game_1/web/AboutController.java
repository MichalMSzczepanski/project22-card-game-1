package io.github.michalmszczepanski.project22_card_game_1.web;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/** Etap 1 (FORMAL.md): krótka, statyczna strona informacyjna — nic do pobrania z bazy. */
@Controller
public class AboutController {

    @GetMapping("/o-grze")
    public String about() {
        return "o-grze";
    }
}
