package io.github.michalmszczepanski.project22_card_game_1.web;

import io.github.michalmszczepanski.project22_card_game_1.card.CardPack;
import io.github.michalmszczepanski.project22_card_game_1.card.PackAccessService;
import io.github.michalmszczepanski.project22_card_game_1.game.GameNotFoundException;
import io.github.michalmszczepanski.project22_card_game_1.game.GameProperties;
import io.github.michalmszczepanski.project22_card_game_1.game.GameService;
import io.github.michalmszczepanski.project22_card_game_1.game.InvalidGameActionException;
import io.github.michalmszczepanski.project22_card_game_1.game.Player;
import io.github.michalmszczepanski.project22_card_game_1.security.CurrentUserResolver;
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
    private final CurrentUserResolver currentUserResolver;
    private final GameProperties gameProperties;

    @GetMapping("/")
    public String index(Authentication authentication, Model model) {
        AppUser user = currentUserResolver.resolve(authentication);
        model.addAttribute("maxPlayers", gameProperties.players().max());
        model.addAttribute("maxNicknameLength", gameProperties.maxNicknameLength());
        model.addAttribute("codeLength", gameProperties.codeLength());
        model.addAttribute("loggedIn", user != null);
        model.addAttribute("username", user == null ? null : user.getUsername());
        if (user != null) {
            model.addAttribute("ownedPaidPacks", packAccessService.availablePacksFor(user).stream()
                    .filter(p -> !p.isFree()).toList());
        }
        return "index";
    }

    @PostMapping("/games")
    public String createGame(@RequestParam String nickname,
                              @RequestParam(required = false) List<Long> packIds,
                              Authentication authentication,
                              HttpSession session,
                              RedirectAttributes redirectAttributes) {
        AppUser user = currentUserResolver.resolve(authentication);
        List<CardPack> activePacks = packAccessService.resolveSelection(user, packIds);

        try {
            Player player = gameService.createGameAndJoin(activePacks, nickname, user == null ? null : user.getId());
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
