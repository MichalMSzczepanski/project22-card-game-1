package io.github.michalmszczepanski.project22_card_game_1.settings;

import org.springframework.data.jpa.repository.JpaRepository;

public interface AppSettingsRepository extends JpaRepository<AppSettings, Long> {
}
