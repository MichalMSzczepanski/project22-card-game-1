package io.github.michalmszczepanski.project22_card_game_1.web;

import io.github.michalmszczepanski.project22_card_game_1.security.CurrentUserResolver;
import io.github.michalmszczepanski.project22_card_game_1.stats.GameScoreRecord;
import io.github.michalmszczepanski.project22_card_game_1.stats.GameScoreRecordRepository;
import io.github.michalmszczepanski.project22_card_game_1.user.AppUser;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.List;

@Controller
@RequiredArgsConstructor
public class StatsController {

    private final CurrentUserResolver currentUserResolver;
    private final GameScoreRecordRepository gameScoreRecordRepository;

    @GetMapping("/stats")
    public String stats(Authentication authentication, Model model) {
        AppUser user = currentUserResolver.resolve(authentication);
        if (user == null) {
            return "redirect:/login";
        }
        List<GameScoreRecord> records = gameScoreRecordRepository.findByUserOrderByPlayedAtDesc(user);

        int gamesPlayed = records.size();
        int totalPoints = records.stream().mapToInt(GameScoreRecord::getPoints).sum();
        long wins = records.stream().filter(GameScoreRecord::isWon).count();
        double avgPoints = gamesPlayed == 0 ? 0 : (double) totalPoints / gamesPlayed;

        model.addAttribute("gamesPlayed", gamesPlayed);
        model.addAttribute("wins", wins);
        model.addAttribute("avgPoints", avgPoints);
        model.addAttribute("records", records);
        return "stats";
    }
}
