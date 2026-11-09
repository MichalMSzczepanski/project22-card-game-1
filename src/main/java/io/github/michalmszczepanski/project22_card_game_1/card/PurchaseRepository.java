package io.github.michalmszczepanski.project22_card_game_1.card;

import io.github.michalmszczepanski.project22_card_game_1.user.AppUser;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PurchaseRepository extends JpaRepository<Purchase, Long> {

    List<Purchase> findByUser(AppUser user);

    boolean existsByUserAndPack(AppUser user, CardPack pack);
}
