package io.github.michalmszczepanski.project22_card_game_1.card;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface CardRepository extends JpaRepository<Card, Long> {

    /** Only enabled cards are ever dealt — a disabled one (admin "wyłącz kartę") stays out of the draw pool. */
    List<Card> findByTypeAndEnabledTrueAndPack_IdIn(CardType type, Collection<Long> packIds);

    List<Card> findByPack_IdOrderByTypeAscIdAsc(Long packId);

    int countByPack_Id(Long packId);
}
