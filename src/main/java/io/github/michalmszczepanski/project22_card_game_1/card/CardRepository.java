package io.github.michalmszczepanski.project22_card_game_1.card;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface CardRepository extends JpaRepository<Card, Long> {

    List<Card> findByTypeAndPack_IdIn(CardType type, Collection<Long> packIds);
}
