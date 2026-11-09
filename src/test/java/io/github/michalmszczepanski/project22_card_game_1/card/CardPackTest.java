package io.github.michalmszczepanski.project22_card_game_1.card;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class CardPackTest {

    @Test
    void alwaysAvailableWhenNoDatesSet() {
        CardPack pack = new CardPack("Generyczny", true, BigDecimal.ZERO, PackAudience.FAMILY);
        assertThat(pack.isAvailableOn(LocalDate.of(2026, 1, 1))).isTrue();
        assertThat(pack.isAvailableOn(LocalDate.of(2030, 6, 15))).isTrue();
    }

    @Test
    void seasonalPackOnlyAvailableInsideItsWindow() {
        CardPack pack = new CardPack("Sylwestrowa", false, new BigDecimal("9.00"), PackAudience.FAMILY,
                LocalDate.of(2026, 12, 15), LocalDate.of(2027, 1, 15));

        assertThat(pack.isAvailableOn(LocalDate.of(2026, 11, 8))).isFalse();
        assertThat(pack.isAvailableOn(LocalDate.of(2026, 12, 15))).isTrue();
        assertThat(pack.isAvailableOn(LocalDate.of(2026, 12, 31))).isTrue();
        assertThat(pack.isAvailableOn(LocalDate.of(2027, 1, 15))).isTrue();
        assertThat(pack.isAvailableOn(LocalDate.of(2027, 1, 16))).isFalse();
    }
}
