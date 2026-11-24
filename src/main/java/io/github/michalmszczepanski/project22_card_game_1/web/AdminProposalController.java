package io.github.michalmszczepanski.project22_card_game_1.web;

import io.github.michalmszczepanski.project22_card_game_1.proposal.CardProposalService;
import io.github.michalmszczepanski.project22_card_game_1.proposal.InvalidProposalException;
import io.github.michalmszczepanski.project22_card_game_1.proposal.ProposalNotFoundException;
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
 * "Propozycje kart od graczy" (README TODO) — operator review queue, admin-only, {@code /admin/**}
 * (SecurityConfig.adminFilterChain).
 */
@Controller
@RequiredArgsConstructor
@Slf4j
public class AdminProposalController {

    private final CardProposalService cardProposalService;

    @GetMapping("/admin/proposals")
    public String proposals(Model model) {
        model.addAttribute("proposals", cardProposalService.pendingProposals());
        return "admin-proposals";
    }

    @PostMapping("/admin/proposals/{id}/approve")
    public String approve(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        return withErrorHandling(redirectAttributes, () -> cardProposalService.approve(id));
    }

    @PostMapping("/admin/proposals/{id}/reject")
    public String reject(@PathVariable Long id, @RequestParam(required = false) String note,
                          RedirectAttributes redirectAttributes) {
        return withErrorHandling(redirectAttributes, () -> cardProposalService.reject(id, note));
    }

    private String withErrorHandling(RedirectAttributes redirectAttributes, Runnable action) {
        try {
            action.run();
        } catch (InvalidProposalException | ProposalNotFoundException e) {
            log.debug("Admin: akcja na propozycji odrzucona — {}", e.getMessage());
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/admin/proposals";
    }
}
