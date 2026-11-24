package io.github.michalmszczepanski.project22_card_game_1.settings;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Single-row runtime settings the operator can change without a redeploy (README TODO
 * "Ustawienia serwisu bez redeployu") — always the one row with {@code id=1}, created lazily by
 * {@link AppSettingsService#current()} on first read.
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AppSettings {

    @Id
    private Long id = 1L;

    /** Blocks creating new games (README: "blokada nowych gier") and shows a banner everywhere. */
    private boolean maintenanceMode = false;

    /** Shown to everyone (index + in-game) when non-blank — null/blank means no banner. */
    @Column(length = 500)
    private String announcementBanner;

    /** Null = use the static {@code game.max-active-games} default from application.yaml. */
    private Integer maxActiveGamesOverride;

    private boolean blankCardEnabled = true;

    /** The "Tematyczne zestawy — wkrótce" badge on /packs for non-free packs. */
    private boolean comingSoonBadgeEnabled = true;

    public void update(boolean maintenanceMode, String announcementBanner, Integer maxActiveGamesOverride,
                        boolean blankCardEnabled, boolean comingSoonBadgeEnabled) {
        this.maintenanceMode = maintenanceMode;
        this.announcementBanner = announcementBanner;
        this.maxActiveGamesOverride = maxActiveGamesOverride;
        this.blankCardEnabled = blankCardEnabled;
        this.comingSoonBadgeEnabled = comingSoonBadgeEnabled;
    }
}
