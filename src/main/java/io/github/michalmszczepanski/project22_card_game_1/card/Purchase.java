package io.github.michalmszczepanski.project22_card_game_1.card;

import io.github.michalmszczepanski.project22_card_game_1.user.AppUser;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Records that a user unlocked a paid card pack. For the MVP the "payment" is a
 * stub (no real charge happens yet) so this is purely an ownership/entitlement
 * record to build the rest of the purchase flow against.
 */
@Entity
public class Purchase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "user_id")
    private AppUser user;

    @ManyToOne(optional = false)
    @JoinColumn(name = "pack_id")
    private CardPack pack;

    private BigDecimal pricePaidPln;

    private Instant purchasedAt;

    protected Purchase() {
    }

    public Purchase(AppUser user, CardPack pack, BigDecimal pricePaidPln) {
        this.user = user;
        this.pack = pack;
        this.pricePaidPln = pricePaidPln;
        this.purchasedAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public AppUser getUser() {
        return user;
    }

    public CardPack getPack() {
        return pack;
    }

    public BigDecimal getPricePaidPln() {
        return pricePaidPln;
    }

    public Instant getPurchasedAt() {
        return purchasedAt;
    }
}
