package io.github.michalmszczepanski.project22_card_game_1.web;

import io.github.michalmszczepanski.project22_card_game_1.card.CardType;
import io.github.michalmszczepanski.project22_card_game_1.proposal.CardProposalService;
import io.github.michalmszczepanski.project22_card_game_1.proposal.InvalidProposalException;
import io.github.michalmszczepanski.project22_card_game_1.security.CurrentUserResolver;
import io.github.michalmszczepanski.project22_card_game_1.user.AppUser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * "Propozycje kart od graczy" (README TODO) — player-facing panel, outside any game, {@code /me/**}
 * (requires login, {@code SecurityConfig.appFilterChain}).
 */
@Controller
@RequiredArgsConstructor
@Slf4j
public class CardProposalController {

    private final CardProposalService cardProposalService;
    private final CurrentUserResolver currentUserResolver;

    @GetMapping("/me/proposals")
    public String myProposals(Authentication authentication, Model model) {
        AppUser user = currentUserResolver.resolve(authentication);
        model.addAttribute("proposals", cardProposalService.myProposals(user));
        return "my-proposals";
    }

    @PostMapping("/me/proposals")
    public String propose(@RequestParam String text,
                           @RequestParam CardType type,
                           Authentication authentication,
                           RedirectAttributes redirectAttributes) {
        AppUser user = currentUserResolver.resolve(authentication);
        try {
            cardProposalService.propose(user, text, type);
            redirectAttributes.addFlashAttribute("info", "Propozycja wysłana — czeka na akceptację.");
        } catch (InvalidProposalException e) {
            log.debug("Propozycja karty odrzucona — {}", e.getMessage());
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/me/proposals";
    }
}
