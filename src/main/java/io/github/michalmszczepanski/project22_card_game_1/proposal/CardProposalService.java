package io.github.michalmszczepanski.project22_card_game_1.proposal;

import io.github.michalmszczepanski.project22_card_game_1.admin.CardAdminService;
import io.github.michalmszczepanski.project22_card_game_1.card.Card;
import io.github.michalmszczepanski.project22_card_game_1.card.CardPack;
import io.github.michalmszczepanski.project22_card_game_1.card.CardPackRepository;
import io.github.michalmszczepanski.project22_card_game_1.card.CardSeeder;
import io.github.michalmszczepanski.project22_card_game_1.card.CardType;
import io.github.michalmszczepanski.project22_card_game_1.user.AppUser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Backs "Propozycje kart od graczy" (README TODO): a logged-in player proposes a card at
 * {@code /me/proposals}, the operator accepts/rejects it at {@code /admin/proposals}. Accepted
 * proposals become real {@code Card} rows in the "Od graczy" pack ({@link CardSeeder#USER_PROPOSED_PACK_NAME})
 * via {@link CardAdminService} — never automatically.
 */
@Service
@Transactional
@RequiredArgsConstructor
@Slf4j
public class CardProposalService {

    private static final int MAX_TEXT_LENGTH = 500;

    /** Anti-spam caps from the feature request: 100 proposals total per player, 100 per day. */
    private static final long MAX_PROPOSALS_PER_PLAYER = 100;
    private static final long MAX_PROPOSALS_PER_DAY = 100;

    private final CardProposalRepository cardProposalRepository;
    private final CardPackRepository cardPackRepository;
    private final CardAdminService cardAdminService;

    public CardProposal propose(AppUser user, String text, CardType type) {
        String trimmed = text == null ? "" : text.trim();
        if (trimmed.isEmpty() || trimmed.length() > MAX_TEXT_LENGTH) {
            throw new InvalidProposalException("Tekst karty musi mieć 1-" + MAX_TEXT_LENGTH + " znaków.");
        }
        if (cardProposalRepository.countByProposer(user) >= MAX_PROPOSALS_PER_PLAYER) {
            throw new InvalidProposalException(
                    "Limit " + MAX_PROPOSALS_PER_PLAYER + " propozycji na konto został wykorzystany.");
        }
        Instant since = Instant.now().minus(1, ChronoUnit.DAYS);
        if (cardProposalRepository.countByProposerAndCreatedAtAfter(user, since) >= MAX_PROPOSALS_PER_DAY) {
            throw new InvalidProposalException(
                    "Limit " + MAX_PROPOSALS_PER_DAY + " propozycji dziennie został wykorzystany — wróć później.");
        }

        CardProposal proposal = cardProposalRepository.save(new CardProposal(user, trimmed, type));
        log.info("Gracz (appUserId={}) zaproponował kartę ({})", user.getId(), type);
        return proposal;
    }

    @Transactional(readOnly = true)
    public List<MyProposalRow> myProposals(AppUser user) {
        return cardProposalRepository.findByProposerOrderByCreatedAtDesc(user).stream()
                .map(p -> new MyProposalRow(p.getId(), p.getText(), p.getType(), p.getStatus(), p.getReviewNote()))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<PendingProposalRow> pendingProposals() {
        return cardProposalRepository.findByStatusOrderByCreatedAtAsc(CardProposalStatus.PENDING).stream()
                .map(p -> new PendingProposalRow(p.getId(), p.getProposer().getUsername(), p.getText(), p.getType(), p.getCreatedAt()))
                .toList();
    }

    public void approve(Long proposalId) {
        CardProposal proposal = requirePendingProposal(proposalId);
        CardPack pack = cardPackRepository.findByName(CardSeeder.USER_PROPOSED_PACK_NAME)
                .orElseThrow(() -> new IllegalStateException(
                        "Brak pakietu '" + CardSeeder.USER_PROPOSED_PACK_NAME + "' — sprawdź CardSeeder."));

        Card card = cardAdminService.createCard(proposal.getText(), proposal.getType(), pack.getId());
        proposal.approve(card.getId());
        log.info("Admin: zaakceptowano propozycję {} -> karta {}", proposalId, card.getId());
    }

    public void reject(Long proposalId, String note) {
        CardProposal proposal = requirePendingProposal(proposalId);
        proposal.reject(note == null || note.isBlank() ? null : note.trim());
        log.info("Admin: odrzucono propozycję {}", proposalId);
    }

    private CardProposal requirePendingProposal(Long proposalId) {
        CardProposal proposal = cardProposalRepository.findById(proposalId)
                .orElseThrow(() -> new ProposalNotFoundException(proposalId));
        if (proposal.getStatus() != CardProposalStatus.PENDING) {
            throw new InvalidProposalException("Ta propozycja już została rozpatrzona.");
        }
        return proposal;
    }
}
