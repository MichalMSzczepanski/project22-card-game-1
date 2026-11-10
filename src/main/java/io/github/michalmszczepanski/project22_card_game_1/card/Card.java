package io.github.michalmszczepanski.project22_card_game_1.card;

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
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Card {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String text;

    @Enumerated(EnumType.STRING)
    private CardType type;

    @ManyToOne(optional = false)
    @JoinColumn(name = "pack_id")
    private CardPack pack;

    public Card(String text, CardType type, CardPack pack) {
        this.text = text;
        this.type = type;
        this.pack = pack;
    }
}
