package io.github.michalmszczepanski.project22_card_game_1.admin;

import io.github.michalmszczepanski.project22_card_game_1.card.Card;
import io.github.michalmszczepanski.project22_card_game_1.card.CardNotFoundException;
import io.github.michalmszczepanski.project22_card_game_1.card.CardRepository;
import io.github.michalmszczepanski.project22_card_game_1.card.InvalidCardActionException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Backs the "Zgłoszone karty" admin queue (README TODO) — a player flags a card from the game
 * ({@code GameController.reportCard}), the operator then removes (disables), fixes, or dismisses
 * it at {@code /admin/reports}. Delegates the actual card mutation to {@link CardAdminService} —
 * this class only owns the report queue itself.
 */
@Service
@Transactional
@RequiredArgsConstructor
@Slf4j
public class CardReportService {

    private static final int MAX_NOTE_LENGTH = 300;

    private final CardReportRepository cardReportRepository;
    private final CardRepository cardRepository;
    private final CardAdminService cardAdminService;

    public void reportCard(Long cardId, String reporterNickname, String note) {
        Card card = cardRepository.findById(cardId).orElseThrow(() -> new CardNotFoundException(cardId));
        String trimmedNote = note == null ? null : note.trim();
        if (trimmedNote != null && trimmedNote.length() > MAX_NOTE_LENGTH) {
            throw new InvalidCardActionException("Komentarz do zgłoszenia może mieć max " + MAX_NOTE_LENGTH + " znaków.");
        }
        cardReportRepository.save(new CardReport(card, reporterNickname,
                trimmedNote == null || trimmedNote.isEmpty() ? null : trimmedNote));
        log.info("Zgłoszono kartę {} ({})", cardId, reporterNickname);
    }

    @Transactional(readOnly = true)
    public List<CardReportRow> openReports() {
        return cardReportRepository.findAllByOrderByReportedAtAsc().stream()
                .map(r -> new CardReportRow(r.getId(), r.getCard().getId(), r.getCard().getText(),
                        r.getCard().getType(), r.getCard().getPack().getName(), r.getReporterNickname(),
                        r.getNote(), r.getReportedAt()))
                .toList();
    }

    /** "usuń" (README TODO) — disables the card (never hard-deleted, see CardAdminService) and clears its reports. */
    public void removeReportedCard(Long cardId) {
        cardAdminService.setCardEnabled(cardId, false);
        cardReportRepository.deleteByCard_Id(cardId);
        log.info("Admin: usunięto (wyłączono) zgłoszoną kartę {}", cardId);
    }

    /** "popraw" (README TODO) — fixes the text in place, keeps type/pack, clears its reports. */
    public void fixReportedCard(Long cardId, String newText) {
        Card card = cardRepository.findById(cardId).orElseThrow(() -> new CardNotFoundException(cardId));
        cardAdminService.updateCard(cardId, newText, card.getType(), card.getPack().getId());
        cardReportRepository.deleteByCard_Id(cardId);
        log.info("Admin: poprawiono zgłoszoną kartę {}", cardId);
    }

    /** "odrzuć zgłoszenie" (README TODO) — the card's fine, just closes this one report. Idempotent. */
    public void dismissReport(Long reportId) {
        cardReportRepository.findById(reportId).ifPresent(cardReportRepository::delete);
        log.debug("Admin: odrzucono zgłoszenie {}", reportId);
    }
}
