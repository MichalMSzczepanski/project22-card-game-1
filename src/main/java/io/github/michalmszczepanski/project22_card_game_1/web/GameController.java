package io.github.michalmszczepanski.project22_card_game_1.web;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.WriterException;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import io.github.michalmszczepanski.project22_card_game_1.admin.CardReportService;
import io.github.michalmszczepanski.project22_card_game_1.card.CardNotFoundException;
import io.github.michalmszczepanski.project22_card_game_1.card.InvalidCardActionException;
import io.github.michalmszczepanski.project22_card_game_1.game.Game;
import io.github.michalmszczepanski.project22_card_game_1.game.GameNotFoundException;
import io.github.michalmszczepanski.project22_card_game_1.game.GameProperties;
import io.github.michalmszczepanski.project22_card_game_1.game.GameService;
import io.github.michalmszczepanski.project22_card_game_1.game.InvalidGameActionException;
import io.github.michalmszczepanski.project22_card_game_1.game.Player;
import io.github.michalmszczepanski.project22_card_game_1.settings.AppSettingsService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

@Controller
@RequestMapping("/games/{gameId}")
@RequiredArgsConstructor
@Slf4j
public class GameController {

    private final GameService gameService;
    private final GameProperties gameProperties;
    private final CardReportService cardReportService;
    private final AppSettingsService appSettingsService;

    @GetMapping
    public String view(@PathVariable String gameId, HttpSession session, Model model,
                        RedirectAttributes redirectAttributes) {
        String playerId = (String) session.getAttribute("playerId");
        try {
            gameService.touch(gameId, playerId);
            gameService.checkTimeout(gameId);
            gameService.pruneDisconnectedPlayers(gameId);
            Game game = gameService.getGame(gameId);
            if (playerId != null && playerId.equals(game.getLastKickedPlayerId())) {
                return goHome(session, redirectAttributes, "Admin usunął Cię z gry.");
            }
            // Admin started a rematch (POST /rematch) — carry this still-open tab straight into it,
            // the same way the admin's own click already did, no re-typing the code.
            if (playerId != null && game.getNextGameId() != null) {
                String nextPlayerId = game.findPlayer(playerId).map(p -> p.getNextPlayerId()).orElse(null);
                if (nextPlayerId != null) {
                    session.setAttribute("playerId", nextPlayerId);
                    session.setAttribute("gameId", game.getNextGameId());
                    return "redirect:/games/" + game.getNextGameId();
                }
            }
            model.addAttribute("view", GameViewModel.of(game, playerId, gameProperties,
                    appSettingsService.isBlankCardEnabled(), appSettingsService.announcementBanner()));
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
            if (game.getNextGameId() != null) {
                return "redirect:/games/" + game.getNextGameId() + "/tv";
            }
            model.addAttribute("view", GameViewModel.of(game, null, gameProperties,
                    appSettingsService.isBlankCardEnabled(), appSettingsService.announcementBanner()));
            return "tv";
        } catch (GameNotFoundException e) {
            return goHome(null, redirectAttributes, "Gra zakończyła się — wszyscy wyszli.");
        }
    }

    /** QR kod kodujący link do dołączenia (`/?code=...`) — do wyświetlenia na ekranie TV w lobby. */
    @GetMapping(value = "/qr.png", produces = MediaType.IMAGE_PNG_VALUE)
    public ResponseEntity<byte[]> qrCode(@PathVariable String gameId, HttpServletRequest request)
            throws WriterException, IOException {
        String joinUrl = ServletUriComponentsBuilder.fromContextPath(request)
                .path("/")
                .queryParam("code", gameId)
                .build()
                .toUriString();
        BitMatrix matrix = new QRCodeWriter().encode(joinUrl, BarcodeFormat.QR_CODE, 300, 300);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        MatrixToImageWriter.writeToStream(matrix, "PNG", out);
        return ResponseEntity.ok().contentType(MediaType.IMAGE_PNG).body(out.toByteArray());
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
    public String start(@PathVariable String gameId, HttpSession session, RedirectAttributes redirectAttributes) {
        String playerId = requirePlayerId(session);
        return withErrorHandling(gameId, redirectAttributes, () -> gameService.startGame(gameId, playerId));
    }

    @PostMapping("/answers")
    public String submitAnswer(@PathVariable String gameId,
                                @RequestParam Long cardId,
                                HttpSession session,
                                RedirectAttributes redirectAttributes) {
        String playerId = requirePlayerId(session);
        return withErrorHandling(gameId, redirectAttributes, () -> gameService.submitAnswer(gameId, playerId, cardId));
    }

    @PostMapping("/blank-answer")
    public String submitBlankAnswer(@PathVariable String gameId,
                                     @RequestParam String text,
                                     HttpSession session,
                                     RedirectAttributes redirectAttributes) {
        String playerId = requirePlayerId(session);
        return withErrorHandling(gameId, redirectAttributes,
                () -> gameService.submitBlankAnswer(gameId, playerId, text));
    }

    @PostMapping("/swap-hand")
    public String swapHand(@PathVariable String gameId, HttpSession session, RedirectAttributes redirectAttributes) {
        String playerId = requirePlayerId(session);
        return withErrorHandling(gameId, redirectAttributes, () -> gameService.swapHand(gameId, playerId));
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
    public String next(@PathVariable String gameId, HttpSession session, RedirectAttributes redirectAttributes) {
        String playerId = requirePlayerId(session);
        return withErrorHandling(gameId, redirectAttributes, () -> gameService.nextRound(gameId, playerId));
    }

    @PostMapping("/family-mode")
    public String toggleFamilyMode(@PathVariable String gameId,
                                    @RequestParam boolean enabled,
                                    HttpSession session,
                                    RedirectAttributes redirectAttributes) {
        String playerId = requirePlayerId(session);
        return withErrorHandling(gameId, redirectAttributes, () -> gameService.setFamilyMode(gameId, playerId, enabled));
    }

    @PostMapping("/end")
    public String end(@PathVariable String gameId, HttpSession session, RedirectAttributes redirectAttributes) {
        String playerId = requirePlayerId(session);
        return withErrorHandling(gameId, redirectAttributes, () -> gameService.endGame(gameId, playerId));
    }

    /** "Zagraj jeszcze raz" — admin-only. Drops the clicking admin straight into the new game. */
    @PostMapping("/rematch")
    public String rematch(@PathVariable String gameId, HttpSession session, RedirectAttributes redirectAttributes) {
        String playerId = requirePlayerId(session);
        try {
            Player newPlayer = gameService.rematch(gameId, playerId);
            session.setAttribute("playerId", newPlayer.getId());
            session.setAttribute("gameId", newPlayer.getGame().getId());
            return "redirect:/games/" + newPlayer.getGame().getId();
        } catch (InvalidGameActionException e) {
            log.debug("Gra {}: nie udało się zagrać jeszcze raz — {}", gameId, e.getMessage());
            redirectAttributes.addFlashAttribute("error", e.getMessage());
            return "redirect:/games/" + gameId;
        }
    }

    @PostMapping("/kick")
    public String kick(@PathVariable String gameId,
                        @RequestParam String targetPlayerId,
                        HttpSession session,
                        RedirectAttributes redirectAttributes) {
        String adminPlayerId = requirePlayerId(session);
        return withErrorHandling(gameId, redirectAttributes,
                () -> gameService.kickPlayer(gameId, adminPlayerId, targetPlayerId));
    }

    @PostMapping("/skip-round")
    public String skipRound(@PathVariable String gameId, HttpSession session, RedirectAttributes redirectAttributes) {
        String playerId = requirePlayerId(session);
        return withErrorHandling(gameId, redirectAttributes, () -> gameService.forceRevealRound(gameId, playerId));
    }

    @PostMapping("/transfer-admin")
    public String transferAdmin(@PathVariable String gameId,
                                 @RequestParam String targetPlayerId,
                                 HttpSession session,
                                 RedirectAttributes redirectAttributes) {
        String adminPlayerId = requirePlayerId(session);
        return withErrorHandling(gameId, redirectAttributes,
                () -> gameService.transferAdmin(gameId, adminPlayerId, targetPlayerId));
    }

    @PostMapping("/take-over-admin")
    public String takeOverAdmin(@PathVariable String gameId, HttpSession session, RedirectAttributes redirectAttributes) {
        String playerId = requirePlayerId(session);
        return withErrorHandling(gameId, redirectAttributes, () -> gameService.takeOverAdmin(gameId, playerId));
    }

    /** "Zgłoś kartę" (README TODO "Zgłoszone karty") — any player, on any card they can currently see. */
    @PostMapping("/report-card")
    public String reportCard(@PathVariable String gameId,
                              @RequestParam Long cardId,
                              @RequestParam(required = false) String note,
                              HttpSession session,
                              RedirectAttributes redirectAttributes) {
        String playerId = requirePlayerId(session);
        try {
            Game game = gameService.getGame(gameId);
            String nickname = game.findPlayer(playerId).map(Player::getNickname).orElse("Nieznany gracz");
            cardReportService.reportCard(cardId, nickname, note);
            redirectAttributes.addFlashAttribute("info", "Zgłoszono kartę. Dzięki!");
        } catch (GameNotFoundException | CardNotFoundException | InvalidCardActionException e) {
            log.debug("Gra {}: zgłoszenie karty odrzucone — {}", gameId, e.getMessage());
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/games/" + gameId;
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
