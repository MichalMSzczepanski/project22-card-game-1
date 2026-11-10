package io.github.michalmszczepanski.project22_card_game_1.web;

import io.github.michalmszczepanski.project22_card_game_1.card.CardPack;
import io.github.michalmszczepanski.project22_card_game_1.card.CardPackRepository;
import io.github.michalmszczepanski.project22_card_game_1.card.PackAudience;
import io.github.michalmszczepanski.project22_card_game_1.card.Purchase;
import io.github.michalmszczepanski.project22_card_game_1.card.PurchaseRepository;
import io.github.michalmszczepanski.project22_card_game_1.security.CurrentUserResolver;
import io.github.michalmszczepanski.project22_card_game_1.user.AppUser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;

@Controller
@RequiredArgsConstructor
@Slf4j
public class PacksController {

    private final CardPackRepository cardPackRepository;
    private final PurchaseRepository purchaseRepository;
    private final CurrentUserResolver currentUserResolver;

    @GetMapping("/packs")
    public String list(Authentication authentication, Model model) {
        AppUser user = currentUserResolver.resolve(authentication);
        List<CardPack> owned = user == null ? List.of()
                : purchaseRepository.findByUser(user).stream().map(Purchase::getPack).toList();
        java.time.LocalDate today = java.time.LocalDate.now();

        List<PackRow> rows = cardPackRepository.findAll().stream()
                .map(p -> new PackRow(p.getId(), p.getName(), p.isFree(), p.getPricePln(), p.getAudience(),
                        p.isFree() || owned.stream().anyMatch(o -> o.getId().equals(p.getId())),
                        p.isAvailableOn(today)))
                .toList();

        model.addAttribute("packs", rows);
        model.addAttribute("loggedIn", user != null);
        return "packs";
    }

    @PostMapping("/packs/{id}/purchase")
    public String purchase(@PathVariable Long id, Authentication authentication, RedirectAttributes redirectAttributes) {
        AppUser user = currentUserResolver.resolve(authentication);
        if (user == null) {
            // Shouldn't happen (this endpoint requires authentication), but the
            // session's user could in principle have been removed mid-session.
            return "redirect:/login";
        }

        CardPack pack = cardPackRepository.findById(id).orElseThrow();

        if (!pack.isFree() && !pack.isAvailableOn(java.time.LocalDate.now())) {
            redirectAttributes.addFlashAttribute("error", "Ten pakiet jest obecnie niedostępny do zakupu (oferta sezonowa).");
            return "redirect:/packs";
        }

        if (!pack.isFree() && !purchaseRepository.existsByUserAndPack(user, pack)) {
            // MVP stub: unlocks immediately, no real payment provider wired up yet.
            purchaseRepository.save(new Purchase(user, pack, pack.getPricePln()));
            log.info("Zakup (symulowany): {} kupił pakiet \"{}\" za {} zł", user.getUsername(), pack.getName(), pack.getPricePln());
            redirectAttributes.addFlashAttribute("info",
                    "Pakiet \"" + pack.getName() + "\" odblokowany (symulacja płatności na czas MVP).");
        }
        return "redirect:/packs";
    }

    public record PackRow(Long id, String name, boolean free, java.math.BigDecimal pricePln,
                           PackAudience audience, boolean owned, boolean availableNow) {
    }
}
