package io.github.michalmszczepanski.project22_card_game_1.admin;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface OperatorRepository extends JpaRepository<Operator, Long> {

    Optional<Operator> findByUsername(String username);

    boolean existsByUsername(String username);
}
