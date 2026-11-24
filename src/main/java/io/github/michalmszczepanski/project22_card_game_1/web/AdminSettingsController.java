package io.github.michalmszczepanski.project22_card_game_1.web;

import io.github.michalmszczepanski.project22_card_game_1.settings.AppSettingsService;
import io.github.michalmszczepanski.project22_card_game_1.settings.AppSettingsView;
import io.github.michalmszczepanski.project22_card_game_1.settings.InvalidSettingsException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * "Ustawienia serwisu bez redeployu" (README TODO) — admin-only, {@code /admin/**}: tryb
 * serwisowy, baner ogłoszeń, limit aktywnych gier, włącz/wyłącz pustą kartę i baner „wkrótce”.
 */
@Controller
@RequiredArgsConstructor
@Slf4j
public class AdminSettingsController {

    private final AppSettingsService appSettingsService;

    @GetMapping("/admin/settings")
    public String settings(Model model) {
        model.addAttribute("settings", AppSettingsView.of(appSettingsService.current()));
        return "admin-settings";
    }

    @PostMapping("/admin/settings")
    public String update(@RequestParam(defaultValue = "false") boolean maintenanceMode,
                          @RequestParam(required = false) String announcementBanner,
                          @RequestParam(required = false) Integer maxActiveGamesOverride,
                          @RequestParam(defaultValue = "false") boolean blankCardEnabled,
                          @RequestParam(defaultValue = "false") boolean comingSoonBadgeEnabled,
                          RedirectAttributes redirectAttributes) {
        try {
            appSettingsService.update(maintenanceMode, announcementBanner, maxActiveGamesOverride,
                    blankCardEnabled, comingSoonBadgeEnabled);
            redirectAttributes.addFlashAttribute("info", "Ustawienia zapisane.");
        } catch (InvalidSettingsException e) {
            log.debug("Admin: zapis ustawień odrzucony — {}", e.getMessage());
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/admin/settings";
    }
}
