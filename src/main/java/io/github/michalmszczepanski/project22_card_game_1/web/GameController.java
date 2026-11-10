package io.github.michalmszczepanski.project22_card_game_1.web;

import io.github.michalmszczepanski.project22_card_game_1.game.Game;
import io.github.michalmszczepanski.project22_card_game_1.game.GameNotFoundException;
import io.github.michalmszczepanski.project22_card_game_1.game.GameProperties;
import io.github.michalmszczepanski.project22_card_game_1.game.GameService;
import io.github.michalmszczepanski.project22_card_game_1.game.InvalidGameActionException;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/games/{gameId}")
@RequiredArgsConstructor
@Slf4j
public class GameController {

    private final GameService gameService;
    private final GameProperties gameProperties;

    @GetMapping
    public String view(@PathVariable String gameId, HttpSession session, Model model,
                        RedirectAttributes redirectAttributes) {
        String playerId = (String) session.getAttribute("playerId");
        try {
            gameService.touch(gameId, playerId);
            gameService.checkTimeout(gameId);
            gameService.pruneDisconnectedPlayers(gameId);
            Game game = gameService.getGame(gameId);
            model.addAttribute("view", GameViewModel.of(game, playerId, gameProperties));
            return "game";
        } catch (GameNotFoundException e) {
            return goHome(session, redirectAttributes, "Gra zakończyła się — wszyscy wyszli.");
        }
    }

    /** Shared "big screen" display — no hand, no actions, meant for a TV/laptop everyone can see. */
    @GetMapping("/tv")
    public String tv(@PathVariable String gameId, Model model, RedirectAttributes redirectAttributes) {
        try {
            gameService.checkTimeout(gameId);
            gameService.pruneDisconnectedPlayers(gameId);
            Game game = gameService.getGame(gameId);
            model.addAttribute("view", GameViewModel.of(game, null, gameProperties));
            return "tv";
        } catch (GameNotFoundException e) {
            return goHome(null, redirectAttributes, "Gra zakończyła się — wszyscy wyszli.");
        }
    }

    @PostMapping("/leave")
    public String leave(@PathVariable String gameId, HttpSession session, RedirectAttributes redirectAttributes) {
        String playerId = requirePlayerId(session);
        try {
            gameService.leave(gameId, playerId);
        } catch (InvalidGameActionException e) {
            // already gone (e.g. pruned moments earlier) — fine, we're leaving anyway
            log.debug("Gra {}: {} próbował opuścić grę, ale już go nie było — {}", gameId, playerId, e.getMessage());
        }
        return goHome(session, redirectAttributes, "Opuściłeś grę.");
    }

    private String goHome(HttpSession session, RedirectAttributes redirectAttributes, String message) {
        if (session != null) {
            session.removeAttribute("playerId");
            session.removeAttribute("gameId");
        }
        redirectAttributes.addFlashAttribute("info", message);
        return "redirect:/";
    }

    @PostMapping("/start")
    public String start(@PathVariable String gameId, RedirectAttributes redirectAttributes) {
        return withErrorHandling(gameId, redirectAttributes, () -> gameService.startGame(gameId));
    }

    @PostMapping("/answers")
    public String submitAnswer(@PathVariable String gameId,
                                @RequestParam Long cardId,
                                HttpSession session,
                                RedirectAttributes redirectAttributes) {
        String playerId = requirePlayerId(session);
        return withErrorHandling(gameId, redirectAttributes, () -> gameService.submitAnswer(gameId, playerId, cardId));
    }

    @PostMapping("/rankings")
    public String submitRanking(@PathVariable String gameId,
                                 @RequestParam String submissionId,
                                 @RequestParam int rank,
                                 HttpSession session,
                                 RedirectAttributes redirectAttributes) {
        String playerId = requirePlayerId(session);
        return withErrorHandling(gameId, redirectAttributes,
                () -> gameService.submitRanking(gameId, playerId, submissionId, rank));
    }

    @PostMapping("/next")
    public String next(@PathVariable String gameId, RedirectAttributes redirectAttributes) {
        return withErrorHandling(gameId, redirectAttributes, () -> gameService.nextRound(gameId));
    }

    @PostMapping("/family-mode")
    public String toggleFamilyMode(@PathVariable String gameId,
                                    @RequestParam boolean enabled,
                                    RedirectAttributes redirectAttributes) {
        return withErrorHandling(gameId, redirectAttributes, () -> gameService.setFamilyMode(gameId, enabled));
    }

    @PostMapping("/end")
    public String end(@PathVariable String gameId, RedirectAttributes redirectAttributes) {
        return withErrorHandling(gameId, redirectAttributes, () -> gameService.endGame(gameId));
    }

    private String requirePlayerId(HttpSession session) {
        String playerId = (String) session.getAttribute("playerId");
        if (playerId == null) {
            throw new InvalidGameActionException("Dołącz do gry zanim zagrasz kartę.");
        }
        return playerId;
    }

    private String withErrorHandling(String gameId, RedirectAttributes redirectAttributes, Runnable action) {
        try {
            action.run();
        } catch (InvalidGameActionException e) {
            log.debug("Gra {}: akcja odrzucona — {}", gameId, e.getMessage());
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        } catch (ObjectOptimisticLockingFailureException e) {
            log.warn("Gra {}: konflikt współbieżnej edycji, gracz musi spróbować jeszcze raz", gameId);
            redirectAttributes.addFlashAttribute("error", "Ktoś inny właśnie coś zmienił w grze — spróbuj jeszcze raz.");
        }
        return "redirect:/games/" + gameId;
    }
}
