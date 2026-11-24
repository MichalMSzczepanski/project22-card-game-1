package io.github.michalmszczepanski.project22_card_game_1.web;

import io.github.michalmszczepanski.project22_card_game_1.card.CardPack;
import io.github.michalmszczepanski.project22_card_game_1.card.PackAccessService;
import io.github.michalmszczepanski.project22_card_game_1.game.GameNotFoundException;
import io.github.michalmszczepanski.project22_card_game_1.game.GameProperties;
import io.github.michalmszczepanski.project22_card_game_1.game.GameService;
import io.github.michalmszczepanski.project22_card_game_1.game.InvalidGameActionException;
import io.github.michalmszczepanski.project22_card_game_1.game.Player;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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

    @GetMapping("/")
    public String index(Model model) {
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
                              HttpSession session,
                              RedirectAttributes redirectAttributes) {
        // Etap 1: bez kont, więc zawsze tylko darmowe pakiety (user=null w PackAccessService).
        List<CardPack> activePacks = packAccessService.resolveSelection(null, null);

        try {
            Player player = gameService.createGameAndJoin(activePacks, nickname, null, totalRounds,
                    targetScore, answerSeconds, rankingSeconds);
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
                            HttpSession session,
                            RedirectAttributes redirectAttributes) {
        String gameId = code.trim().toUpperCase();
        try {
            Player player = gameService.join(gameId, nickname, null);
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
