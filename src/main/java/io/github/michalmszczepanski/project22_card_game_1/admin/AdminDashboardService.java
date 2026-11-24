package io.github.michalmszczepanski.project22_card_game_1.admin;

import io.github.michalmszczepanski.project22_card_game_1.game.Game;
import io.github.michalmszczepanski.project22_card_game_1.game.GameRepository;
import io.github.michalmszczepanski.project22_card_game_1.game.GameService;
import io.github.michalmszczepanski.project22_card_game_1.game.GameStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Read side (cross-game reporting) + the one write action (force-end) for the minimal operator
 * dashboard (README TODO "Panel admina aplikacji"). The actual per-game business rules stay in
 * {@link GameService} — this only aggregates across games and delegates the one mutation to it.
 */
@Service
@RequiredArgsConstructor
public class AdminDashboardService {

    private final GameRepository gameRepository;
    private final GameService gameService;

    @Transactional(readOnly = true)
    public AdminDashboardView currentView() {
        var games = gameRepository.findAll();
        long active = games.stream().filter(g -> g.getStatus() != GameStatus.FINISHED).count();
        long finished = games.size() - active;

        var liveGames = games.stream()
                .filter(g -> g.getStatus() != GameStatus.FINISHED)
                .map(this::toRow)
                .toList();

        return new AdminDashboardView(active, finished, liveGames);
    }

    public void forceEndGame(String gameId) {
        gameService.forceEndGame(gameId);
    }

    private AdminDashboardView.GameRow toRow(Game game) {
        return new AdminDashboardView.GameRow(
                game.getId(),
                game.getStatus(),
                game.getCurrentRound() == null ? 0 : game.getCurrentRound().getNumber(),
                game.getTotalRounds(),
                game.activePlayers().size(),
                game.hasAdmin());
    }
}
