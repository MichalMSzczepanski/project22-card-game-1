package io.github.michalmszczepanski.project22_card_game_1.web;

import io.github.michalmszczepanski.project22_card_game_1.card.CardPack;
import io.github.michalmszczepanski.project22_card_game_1.card.PackAccessService;
import io.github.michalmszczepanski.project22_card_game_1.game.GameNotFoundException;
import io.github.michalmszczepanski.project22_card_game_1.game.GameProperties;
import io.github.michalmszczepanski.project22_card_game_1.game.GameService;
import io.github.michalmszczepanski.project22_card_game_1.game.InvalidGameActionException;
import io.github.michalmszczepanski.project22_card_game_1.game.Player;
import io.github.michalmszczepanski.project22_card_game_1.security.CurrentUserResolver;
import io.github.michalmszczepanski.project22_card_game_1.settings.AppSettingsService;
import io.github.michalmszczepanski.project22_card_game_1.user.AppUser;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;

@Controller
@RequiredArgsConstructor
@Slf4j
public class JoinController {

    private final GameService gameService;
    private final PackAccessService packAccessService;
    private final GameProperties gameProperties;
    private final AppSettingsService appSettingsService;
    private final CurrentUserResolver currentUserResolver;

    @GetMapping("/")
    public String index(Authentication authentication, Model model) {
        AppUser user = currentUserResolver.resolve(authentication);
        model.addAttribute("loggedIn", user != null);
        model.addAttribute("username", user == null ? null : user.getUsername());
        model.addAttribute("maintenanceMode", appSettingsService.isMaintenanceMode());
        model.addAttribute("announcementBanner", appSettingsService.announcementBanner());
        model.addAttribute("maxPlayers", gameProperties.players().max());
        model.addAttribute("maxNicknameLength", gameProperties.maxNicknameLength());
        model.addAttribute("codeLength", gameProperties.codeLength());
        model.addAttribute("maxTotalRounds", gameProperties.maxTotalRounds());
        model.addAttribute("maxTargetScore", gameProperties.maxTargetScore());
        model.addAttribute("defaultAnswerSeconds", gameProperties.timeouts().answerSeconds());
        model.addAttribute("defaultRankingSeconds", gameProperties.timeouts().rankingSeconds());
        model.addAttribute("minCustomSeconds", gameProperties.timeouts().minCustomSeconds());
        model.addAttribute("maxCustomSeconds", gameProperties.timeouts().maxCustomSeconds());
        return "index";
    }

    @PostMapping("/games")
    public String createGame(@RequestParam String nickname,
                              @RequestParam(defaultValue = "10") int totalRounds,
                              @RequestParam(required = false) Integer targetScore,
                              @RequestParam(required = false) Integer answerSeconds,
                              @RequestParam(required = false) Integer rankingSeconds,
                              Authentication authentication,
                              HttpSession session,
                              RedirectAttributes redirectAttributes) {
        // Purchases are still disabled (Etap 1, FORMAL.md), so a logged-in user has no paid packs
        // to pick from yet — resolveSelection(user, null) is still just the free packs either way.
        AppUser user = currentUserResolver.resolve(authentication);
        List<CardPack> activePacks = packAccessService.resolveSelection(user, null);

        try {
            Player player = gameService.createGameAndJoin(activePacks, nickname, user == null ? null : user.getId(),
                    totalRounds, targetScore, answerSeconds, rankingSeconds);
            session.setAttribute("playerId", player.getId());
            session.setAttribute("gameId", player.getGame().getId());
            return "redirect:/games/" + player.getGame().getId();
        } catch (InvalidGameActionException e) {
            log.debug("Nie udało się stworzyć gry — {}", e.getMessage());
            redirectAttributes.addFlashAttribute("error", e.getMessage());
            return "redirect:/";
        }
    }

    @PostMapping("/games/join")
    public String joinGame(@RequestParam String code,
                            @RequestParam String nickname,
                            Authentication authentication,
                            HttpSession session,
                            RedirectAttributes redirectAttributes) {
        String gameId = code.trim().toUpperCase();
        AppUser user = currentUserResolver.resolve(authentication);
        try {
            Player player = gameService.join(gameId, nickname, user == null ? null : user.getId());
            session.setAttribute("playerId", player.getId());
            session.setAttribute("gameId", gameId);
            return "redirect:/games/" + gameId;
        } catch (InvalidGameActionException | GameNotFoundException e) {
            log.debug("Gra {}: nie udało się dołączyć — {}", gameId, e.getMessage());
            redirectAttributes.addFlashAttribute("error", e.getMessage());
            return "redirect:/";
        }
    }
}
