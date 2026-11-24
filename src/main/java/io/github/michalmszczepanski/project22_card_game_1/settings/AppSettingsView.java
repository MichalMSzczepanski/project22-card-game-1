package io.github.michalmszczepanski.project22_card_game_1.settings;

/** Flattened, read-only snapshot for the "Ustawienia serwisu" admin screen. */
public record AppSettingsView(boolean maintenanceMode, String announcementBanner, Integer maxActiveGamesOverride,
                               boolean blankCardEnabled, boolean comingSoonBadgeEnabled) {

    public static AppSettingsView of(AppSettings settings) {
        return new AppSettingsView(settings.isMaintenanceMode(), settings.getAnnouncementBanner(),
                settings.getMaxActiveGamesOverride(), settings.isBlankCardEnabled(), settings.isComingSoonBadgeEnabled());
    }
}
