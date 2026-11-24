package io.github.michalmszczepanski.project22_card_game_1.settings;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Transactional so each test rolls back — same reasoning as GameServiceTest. */
@SpringBootTest
@Transactional
class AppSettingsServiceTest {

    @Autowired
    private AppSettingsService appSettingsService;

    @Test
    void defaultsAreSensibleOnFirstRead() {
        AppSettings settings = appSettingsService.current();

        assertThat(settings.isMaintenanceMode()).isFalse();
        assertThat(settings.getAnnouncementBanner()).isNull();
        assertThat(settings.getMaxActiveGamesOverride()).isNull();
        assertThat(settings.isBlankCardEnabled()).isTrue();
        assertThat(settings.isComingSoonBadgeEnabled()).isTrue();
    }

    @Test
    void currentIsIdempotentAcrossRepeatedCalls() {
        Long firstId = appSettingsService.current().getId();
        Long secondId = appSettingsService.current().getId();
        assertThat(firstId).isEqualTo(secondId);
    }

    @Test
    void updateChangesEveryField() {
        appSettingsService.update(true, "Serwis w przerwie", 7, false, false);

        AppSettings settings = appSettingsService.current();
        assertThat(settings.isMaintenanceMode()).isTrue();
        assertThat(settings.getAnnouncementBanner()).isEqualTo("Serwis w przerwie");
        assertThat(settings.getMaxActiveGamesOverride()).isEqualTo(7);
        assertThat(settings.isBlankCardEnabled()).isFalse();
        assertThat(settings.isComingSoonBadgeEnabled()).isFalse();

        assertThat(appSettingsService.isMaintenanceMode()).isTrue();
        assertThat(appSettingsService.announcementBanner()).isEqualTo("Serwis w przerwie");
        assertThat(appSettingsService.isBlankCardEnabled()).isFalse();
        assertThat(appSettingsService.isComingSoonBadgeEnabled()).isFalse();
        assertThat(appSettingsService.effectiveMaxActiveGames(50)).isEqualTo(7);
    }

    @Test
    void blankAnnouncementIsStoredAsNoBanner() {
        appSettingsService.update(false, "   ", null, true, true);
        assertThat(appSettingsService.announcementBanner()).isNull();
    }

    @Test
    void nullMaxActiveGamesOverrideFallsBackToTheStaticDefault() {
        appSettingsService.update(false, null, null, true, true);
        assertThat(appSettingsService.effectiveMaxActiveGames(50)).isEqualTo(50);
    }

    @Test
    void rejectsTooLongAnnouncementOrNonPositiveMaxActiveGames() {
        assertThatThrownBy(() -> appSettingsService.update(false, "x".repeat(501), null, true, true))
                .isInstanceOf(InvalidSettingsException.class)
                .hasMessageContaining("Baner ogłoszeń");
        assertThatThrownBy(() -> appSettingsService.update(false, null, 0, true, true))
                .isInstanceOf(InvalidSettingsException.class)
                .hasMessageContaining("Limit aktywnych gier");
    }
}
