package io.github.michalmszczepanski.project22_card_game_1.web;

import io.github.michalmszczepanski.project22_card_game_1.admin.AdminDashboardService;
import io.github.michalmszczepanski.project22_card_game_1.game.GameNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Panel admina aplikacji (operator serwisu) — minimalna wersja (README TODO). Not reachable by
 * players: {@code /admin/**} needs a separate {@code Operator} login (SecurityConfig), nothing
 * links here from the public app.
 */
@Controller
@RequiredArgsConstructor
public class AdminController {

    private final AdminDashboardService dashboardService;

    /** Spring Security's formLogin renders this page itself on an unauthenticated request. */
    @GetMapping("/admin/login")
    public String login() {
        return "admin-login";
    }

    @GetMapping("/admin")
    public String dashboard(Model model) {
        model.addAttribute("view", dashboardService.currentView());
        return "admin-dashboard";
    }

    @PostMapping("/admin/games/{gameId}/end")
    public String endGame(@PathVariable String gameId, RedirectAttributes redirectAttributes) {
        try {
            dashboardService.forceEndGame(gameId);
            redirectAttributes.addFlashAttribute("info", "Gra " + gameId + " zakończona.");
        } catch (GameNotFoundException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/admin";
    }
}
