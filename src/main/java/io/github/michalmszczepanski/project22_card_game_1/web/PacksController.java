package io.github.michalmszczepanski.project22_card_game_1.web;

import io.github.michalmszczepanski.project22_card_game_1.card.CardPackRepository;
import io.github.michalmszczepanski.project22_card_game_1.card.PackAudience;
import io.github.michalmszczepanski.project22_card_game_1.settings.AppSettingsService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.List;

/**
 * Etap 1 (FORMAL.md): płatne pakiety są wyłączone — ten widok tylko pokazuje
 * katalog, bez cen i bez przycisku zakupu ("Tematyczne zestawy — wkrótce").
 */
@Controller
@RequiredArgsConstructor
public class PacksController {

    private final CardPackRepository cardPackRepository;
    private final AppSettingsService appSettingsService;

    @GetMapping("/packs")
    public String list(Model model) {
        List<PackRow> rows = cardPackRepository.findAll().stream()
                .map(p -> new PackRow(p.getName(), p.isFree(), p.getAudience()))
                .toList();

        model.addAttribute("packs", rows);
        model.addAttribute("comingSoonBadgeEnabled", appSettingsService.isComingSoonBadgeEnabled());
        return "packs";
    }

    public record PackRow(String name, boolean free, PackAudience audience) {
    }
}
