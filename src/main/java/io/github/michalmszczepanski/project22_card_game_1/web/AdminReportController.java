package io.github.michalmszczepanski.project22_card_game_1.web;

import io.github.michalmszczepanski.project22_card_game_1.admin.CardReportService;
import io.github.michalmszczepanski.project22_card_game_1.card.CardNotFoundException;
import io.github.michalmszczepanski.project22_card_game_1.card.InvalidCardActionException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * "Zgłoszone karty" (README TODO "Panel admina aplikacji") — admin-only, {@code /admin/**}.
 * The queue itself (one row per open {@code CardReport}) plus the three actions: usuń (disable),
 * popraw (edit in place), odrzuć (dismiss, card stays as-is).
 */
@Controller
@RequiredArgsConstructor
@Slf4j
public class AdminReportController {

    private final CardReportService cardReportService;

    @GetMapping("/admin/reports")
    public String reports(Model model) {
        model.addAttribute("reports", cardReportService.openReports());
        return "admin-reports";
    }

    @PostMapping("/admin/reports/{cardId}/remove")
    public String remove(@PathVariable Long cardId, RedirectAttributes redirectAttributes) {
        return withErrorHandling(redirectAttributes, () -> cardReportService.removeReportedCard(cardId));
    }

    @PostMapping("/admin/reports/{cardId}/fix")
    public String fix(@PathVariable Long cardId, @RequestParam String text, RedirectAttributes redirectAttributes) {
        return withErrorHandling(redirectAttributes, () -> cardReportService.fixReportedCard(cardId, text));
    }

    @PostMapping("/admin/reports/{reportId}/dismiss")
    public String dismiss(@PathVariable Long reportId, RedirectAttributes redirectAttributes) {
        cardReportService.dismissReport(reportId);
        return "redirect:/admin/reports";
    }

    private String withErrorHandling(RedirectAttributes redirectAttributes, Runnable action) {
        try {
            action.run();
        } catch (InvalidCardActionException | CardNotFoundException e) {
            log.debug("Admin: akcja na zgłoszeniu odrzucona — {}", e.getMessage());
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/admin/reports";
    }
}
