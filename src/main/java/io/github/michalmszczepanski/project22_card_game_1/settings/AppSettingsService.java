package io.github.michalmszczepanski.project22_card_game_1.settings;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Backs "Ustawienia serwisu bez redeployu" (README TODO) — a single mutable row other packages
 * read to adjust their behaviour at runtime: {@code game} (maintenance mode blocking new games,
 * the max-active-games override, the blank-card toggle), {@code web} (the announcement banner,
 * the "wkrótce" badge on /packs). Package stands on its own on purpose — {@code game}/{@code web}
 * depend on it, never the other way round.
 */
@Service
@Transactional
@RequiredArgsConstructor
@Slf4j
public class AppSettingsService {

    private static final long SINGLETON_ID = 1L;
    private static final int MAX_ANNOUNCEMENT_LENGTH = 500;

    private final AppSettingsRepository appSettingsRepository;

    public AppSettings current() {
        return appSettingsRepository.findById(SINGLETON_ID)
                .orElseGet(() -> appSettingsRepository.save(new AppSettings()));
    }

    public void update(boolean maintenanceMode, String announcementBanner, Integer maxActiveGamesOverride,
                        boolean blankCardEnabled, boolean comingSoonBadgeEnabled) {
        String trimmedBanner = announcementBanner == null ? null : announcementBanner.trim();
        if (trimmedBanner != null && trimmedBanner.length() > MAX_ANNOUNCEMENT_LENGTH) {
            throw new InvalidSettingsException(
                    "Baner ogłoszeń może mieć max " + MAX_ANNOUNCEMENT_LENGTH + " znaków.");
        }
        if (maxActiveGamesOverride != null && maxActiveGamesOverride < 1) {
            throw new InvalidSettingsException("Limit aktywnych gier musi być co najmniej 1.");
        }

        current().update(maintenanceMode, trimmedBanner == null || trimmedBanner.isEmpty() ? null : trimmedBanner,
                maxActiveGamesOverride, blankCardEnabled, comingSoonBadgeEnabled);
        log.info("Admin: ustawienia serwisu zaktualizowane (tryb serwisowy: {}, limit aktywnych gier: {})",
                maintenanceMode, maxActiveGamesOverride);
    }

    public boolean isMaintenanceMode() {
        return current().isMaintenanceMode();
    }

    /** Null/blank when there's nothing to show. */
    public String announcementBanner() {
        return current().getAnnouncementBanner();
    }

    public boolean isBlankCardEnabled() {
        return current().isBlankCardEnabled();
    }

    public boolean isComingSoonBadgeEnabled() {
        return current().isComingSoonBadgeEnabled();
    }

    /** {@code staticDefault} is {@code GameProperties.maxActiveGames()} — used unless the admin set an override. */
    public int effectiveMaxActiveGames(int staticDefault) {
        Integer override = current().getMaxActiveGamesOverride();
        return override != null ? override : staticDefault;
    }
}
