package io.github.michalmszczepanski.project22_card_game_1.stats;

import io.github.michalmszczepanski.project22_card_game_1.user.AppUser;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface GameScoreRecordRepository extends JpaRepository<GameScoreRecord, Long> {

    List<GameScoreRecord> findByUserOrderByPlayedAtDesc(AppUser user);

    /** "Prawo do usunięcia" (UserService.deleteAccount) — this user's own score history. */
    void deleteByUser(AppUser user);
}
