package io.github.michalmszczepanski.project22_card_game_1.card;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;

import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
public class CardPack {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;

    private boolean free;

    private BigDecimal pricePln;

    @Enumerated(EnumType.STRING)
    private PackAudience audience;

    /** Null on both = always available. Used for limited-time seasonal packs. */
    private LocalDate availableFrom;
    private LocalDate availableUntil;

    protected CardPack() {
    }

    public CardPack(String name, boolean free, BigDecimal pricePln, PackAudience audience) {
        this(name, free, pricePln, audience, null, null);
    }

    public CardPack(String name, boolean free, BigDecimal pricePln, PackAudience audience,
                     LocalDate availableFrom, LocalDate availableUntil) {
        this.name = name;
        this.free = free;
        this.pricePln = pricePln;
        this.audience = audience;
        this.availableFrom = availableFrom;
        this.availableUntil = availableUntil;
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public boolean isFree() {
        return free;
    }

    public BigDecimal getPricePln() {
        return pricePln;
    }

    public PackAudience getAudience() {
        return audience;
    }

    public LocalDate getAvailableFrom() {
        return availableFrom;
    }

    public LocalDate getAvailableUntil() {
        return availableUntil;
    }

    public boolean isAvailableOn(LocalDate date) {
        if (availableFrom != null && date.isBefore(availableFrom)) {
            return false;
        }
        return availableUntil == null || !date.isAfter(availableUntil);
    }
}
