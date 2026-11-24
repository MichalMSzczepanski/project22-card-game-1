package io.github.michalmszczepanski.project22_card_game_1.web;

import io.github.michalmszczepanski.project22_card_game_1.admin.CardAdminService;
import io.github.michalmszczepanski.project22_card_game_1.card.CardNotFoundException;
import io.github.michalmszczepanski.project22_card_game_1.card.CardPack;
import io.github.michalmszczepanski.project22_card_game_1.card.CardPackNotFoundException;
import io.github.michalmszczepanski.project22_card_game_1.card.CardType;
import io.github.michalmszczepanski.project22_card_game_1.card.InvalidCardActionException;
import io.github.michalmszczepanski.project22_card_game_1.card.PackAudience;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * "Karty i pakiety" (README TODO "Panel admina aplikacji") — replaces the CardSeeder hardcode
 * with admin CRUD + bulk import. Admin-only: {@code /admin/**} (SecurityConfig.adminFilterChain).
 */
@Controller
@RequiredArgsConstructor
@Slf4j
public class AdminCardController {

    private final CardAdminService cardAdminService;

    @GetMapping("/admin/cards")
    public String cards(@RequestParam(required = false) Long packId, Model model) {
        model.addAttribute("view", cardAdminService.adminView(packId));
        return "admin-cards";
    }

    @PostMapping("/admin/packs")
    public String createPack(@RequestParam String name,
                              @RequestParam(defaultValue = "false") boolean free,
                              @RequestParam(required = false) BigDecimal pricePln,
                              @RequestParam PackAudience audience,
                              @RequestParam(required = false) LocalDate availableFrom,
                              @RequestParam(required = false) LocalDate availableUntil,
                              RedirectAttributes redirectAttributes) {
        try {
            CardPack pack = cardAdminService.createPack(name, free, pricePln, audience, availableFrom, availableUntil);
            return "redirect:/admin/cards?packId=" + pack.getId();
        } catch (InvalidCardActionException e) {
            log.debug("Admin: nie udało się stworzyć pakietu — {}", e.getMessage());
            redirectAttributes.addFlashAttribute("error", e.getMessage());
            return "redirect:/admin/cards";
        }
    }

    @PostMapping("/admin/packs/{packId}")
    public String updatePack(@PathVariable Long packId,
                              @RequestParam String name,
                              @RequestParam(defaultValue = "false") boolean free,
                              @RequestParam(required = false) BigDecimal pricePln,
                              @RequestParam PackAudience audience,
                              @RequestParam(required = false) LocalDate availableFrom,
                              @RequestParam(required = false) LocalDate availableUntil,
                              RedirectAttributes redirectAttributes) {
        return withErrorHandling(redirectAttributes, packId, () ->
                cardAdminService.updatePack(packId, name, free, pricePln, audience, availableFrom, availableUntil));
    }

    @PostMapping("/admin/cards")
    public String createCard(@RequestParam String text,
                              @RequestParam CardType type,
                              @RequestParam Long packId,
                              RedirectAttributes redirectAttributes) {
        return withErrorHandling(redirectAttributes, packId, () -> cardAdminService.createCard(text, type, packId));
    }

    @PostMapping("/admin/cards/{cardId}")
    public String updateCard(@PathVariable Long cardId,
                              @RequestParam String text,
                              @RequestParam CardType type,
                              @RequestParam Long packId,
                              RedirectAttributes redirectAttributes) {
        return withErrorHandling(redirectAttributes, packId, () -> cardAdminService.updateCard(cardId, text, type, packId));
    }

    @PostMapping("/admin/cards/{cardId}/toggle")
    public String toggleCard(@PathVariable Long cardId,
                              @RequestParam boolean enabled,
                              @RequestParam Long packId,
                              RedirectAttributes redirectAttributes) {
        return withErrorHandling(redirectAttributes, packId, () -> cardAdminService.setCardEnabled(cardId, enabled));
    }

    @PostMapping("/admin/cards/import")
    public String importCards(@RequestParam Long packId,
                               @RequestParam CardType type,
                               @RequestParam MultipartFile file,
                               RedirectAttributes redirectAttributes) {
        try {
            int count = cardAdminService.importCards(packId, type, file);
            redirectAttributes.addFlashAttribute("info", "Zaimportowano " + count + " kart.");
        } catch (InvalidCardActionException | CardPackNotFoundException e) {
            log.debug("Admin: import kart odrzucony — {}", e.getMessage());
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/admin/cards?packId=" + packId;
    }

    private String withErrorHandling(RedirectAttributes redirectAttributes, Long packId, Runnable action) {
        try {
            action.run();
        } catch (InvalidCardActionException | CardNotFoundException | CardPackNotFoundException e) {
            log.debug("Admin: akcja na kartach odrzucona — {}", e.getMessage());
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/admin/cards?packId=" + packId;
    }
}
