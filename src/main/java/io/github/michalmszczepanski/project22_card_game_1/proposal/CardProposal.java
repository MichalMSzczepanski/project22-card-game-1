package io.github.michalmszczepanski.project22_card_game_1.proposal;

import io.github.michalmszczepanski.project22_card_game_1.card.CardType;
import io.github.michalmszczepanski.project22_card_game_1.user.AppUser;
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

import java.time.Instant;

/**
 * A player's "Zaproponuj kartę" submission (README TODO "Propozycje kart od graczy") — reviewed
 * by the operator at {@code /admin/proposals}; accepted ones become real {@code Card} rows in the
 * "Od graczy" pack via {@code CardAdminService.createCard}, never automatically.
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CardProposal {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "proposer_id")
    private AppUser proposer;

    @Column(length = 500)
    private String text;

    @Enumerated(EnumType.STRING)
    private CardType type;

    @Enumerated(EnumType.STRING)
    private CardProposalStatus status;

    private Instant createdAt;

    /** Admin's reason, set only on rejection. */
    private String reviewNote;

    /** Set once accepted — the real {@code Card} this proposal became. */
    private Long resultingCardId;

    public CardProposal(AppUser proposer, String text, CardType type) {
        this.proposer = proposer;
        this.text = text;
        this.type = type;
        this.status = CardProposalStatus.PENDING;
        this.createdAt = Instant.now();
    }

    public void approve(Long resultingCardId) {
        this.status = CardProposalStatus.APPROVED;
        this.resultingCardId = resultingCardId;
    }

    public void reject(String reviewNote) {
        this.status = CardProposalStatus.REJECTED;
        this.reviewNote = reviewNote;
    }
}
