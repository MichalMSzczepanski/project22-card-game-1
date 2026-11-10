package io.github.michalmszczepanski.project22_card_game_1.card;

import io.github.michalmszczepanski.project22_card_game_1.user.AppUser;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Records that a user unlocked a paid card pack. For the MVP the "payment" is a
 * stub (no real charge happens yet) so this is purely an ownership/entitlement
 * record to build the rest of the purchase flow against.
 */
@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Purchase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Getter
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "user_id")
    @Getter
    private AppUser user;

    @ManyToOne(optional = false)
    @JoinColumn(name = "pack_id")
    @Getter
    private CardPack pack;

    private BigDecimal pricePaidPln;

    private Instant purchasedAt;

    public Purchase(AppUser user, CardPack pack, BigDecimal pricePaidPln) {
        this.user = user;
        this.pack = pack;
        this.pricePaidPln = pricePaidPln;
        this.purchasedAt = Instant.now();
    }
}
