package io.github.michalmszczepanski.project22_card_game_1.proposal;

import io.github.michalmszczepanski.project22_card_game_1.user.AppUser;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;

public interface CardProposalRepository extends JpaRepository<CardProposal, Long> {

    List<CardProposal> findByProposerOrderByCreatedAtDesc(AppUser proposer);

    List<CardProposal> findByStatusOrderByCreatedAtAsc(CardProposalStatus status);

    /** Lifetime anti-spam cap — counts every status, not just accepted ones. */
    long countByProposer(AppUser proposer);

    /** Daily anti-spam cap. */
    long countByProposerAndCreatedAtAfter(AppUser proposer, Instant after);
}
