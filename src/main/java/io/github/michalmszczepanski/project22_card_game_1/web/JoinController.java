package io.github.michalmszczepanski.project22_card_game_1.web;

import io.github.michalmszczepanski.project22_card_game_1.card.CardPack;
import io.github.michalmszczepanski.project22_card_game_1.card.PackAccessService;
import io.github.michalmszczepanski.project22_card_game_1.game.Game;
import io.github.michalmszczepanski.project22_card_game_1.game.GameService;
import io.github.michalmszczepanski.project22_card_game_1.game.Player;
import io.github.michalmszczepanski.project22_card_game_1.security.CurrentUserResolver;
import io.github.michalmszczepanski.project22_card_game_1.user.AppUser;
import jakarta.servlet.http.HttpSession;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;

@Controller
public class JoinController {

    private final GameService gameService;
    private final PackAccessService packAccessService;
    private final CurrentUserResolver currentUserResolver;

    public JoinController(GameService gameService, PackAccessService packAccessService, CurrentUserResolver currentUserResolver) {
        this.gameService = gameService;
        this.packAccessService = packAccessService;
        this.currentUserResolver = currentUserResolver;
    }

    @GetMapping("/")
    public String index(Authentication authentication, Model model) {
        AppUser user = currentUserResolver.resolve(authentication);
        model.addAttribute("maxPlayers", Game.MAX_PLAYERS);
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
                              HttpSession session) {
        AppUser user = currentUserResolver.resolve(authentication);
        List<CardPack> activePacks = packAccessService.resolveSelection(user, packIds);

        Game game = gameService.createGame(activePacks);
        Player player = gameService.join(game.getId(), nickname, user == null ? null : user.getId());
        session.setAttribute("playerId", player.getId());
        session.setAttribute("gameId", game.getId());
        return "redirect:/games/" + game.getId();
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
        } catch (RuntimeException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
            return "redirect:/";
        }
    }
}
