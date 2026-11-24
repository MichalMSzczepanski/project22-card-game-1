package io.github.michalmszczepanski.project22_card_game_1.card;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Card {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Getter
    private Long id;

    @Column(length = 500)
    @Getter
    private String text;

    @Enumerated(EnumType.STRING)
    @Getter
    private CardType type;

    @ManyToOne(optional = false)
    @JoinColumn(name = "pack_id")
    @Getter
    private CardPack pack;

    /**
     * Admin "wyłącz kartę" (README TODO "Karty i pakiety") — a disabled card is never dealt to a
     * new hand ({@code CardRepository.findByTypeAndEnabledTrueAndPack_IdIn}), but existing hands
     * and Submissions keep referencing it, so disabling (not deleting) is the only safe removal.
     */
    @Getter
    private boolean enabled = true;

    public Card(String text, CardType type, CardPack pack) {
        this.text = text;
        this.type = type;
        this.pack = pack;
    }

    public void update(String text, CardType type, CardPack pack) {
        this.text = text;
        this.type = type;
        this.pack = pack;
    }

    public void enable() {
        this.enabled = true;
    }

    public void disable() {
        this.enabled = false;
    }
}
