package io.github.michalmszczepanski.project22_card_game_1.admin;

import io.github.michalmszczepanski.project22_card_game_1.card.Card;
import io.github.michalmszczepanski.project22_card_game_1.card.CardNotFoundException;
import io.github.michalmszczepanski.project22_card_game_1.card.CardPack;
import io.github.michalmszczepanski.project22_card_game_1.card.CardPackNotFoundException;
import io.github.michalmszczepanski.project22_card_game_1.card.CardPackRepository;
import io.github.michalmszczepanski.project22_card_game_1.card.CardRepository;
import io.github.michalmszczepanski.project22_card_game_1.card.CardType;
import io.github.michalmszczepanski.project22_card_game_1.card.InvalidCardActionException;
import io.github.michalmszczepanski.project22_card_game_1.card.PackAudience;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;

/**
 * Backs the "Karty i pakiety" admin screen (README TODO) — replaces the {@code CardSeeder}
 * hardcode with CRUD + a plain-text/CSV bulk import, both admin-only ({@code /admin/**}).
 */
@Service
@Transactional
@RequiredArgsConstructor
@Slf4j
public class CardAdminService {

    /** Matches Card.text's column length — keeps the validation message in sync with the schema. */
    private static final int MAX_CARD_TEXT_LENGTH = 500;
    private static final int MAX_PACK_NAME_LENGTH = 100;
    private static final int MAX_IMPORT_LINES = 1000;

    private final CardPackRepository cardPackRepository;
    private final CardRepository cardRepository;

    /** Null {@code selectedPackId} picks the first pack (if any) — mirrors AdminDashboardService.currentView. */
    @Transactional(readOnly = true)
    public CardAdminView adminView(Long selectedPackId) {
        List<CardPack> allPacks = cardPackRepository.findAll();
        List<CardAdminView.PackRow> packRows = allPacks.stream()
                .map(pack -> toPackRow(pack, cardRepository.countByPack_Id(pack.getId())))
                .toList();

        CardAdminView.PackRow selected;
        if (selectedPackId != null) {
            selected = packRows.stream().filter(row -> row.id().equals(selectedPackId)).findFirst().orElse(null);
        } else {
            selected = packRows.isEmpty() ? null : packRows.get(0);
        }

        List<CardAdminView.CardRow> cardRows = selected == null ? List.of()
                : cardRepository.findByPack_IdOrderByTypeAscIdAsc(selected.id()).stream()
                        .map(c -> new CardAdminView.CardRow(c.getId(), c.getText(), c.getType(), c.isEnabled()))
                        .toList();

        return new CardAdminView(packRows, selected, cardRows);
    }

    private CardAdminView.PackRow toPackRow(CardPack pack, int cardCount) {
        return new CardAdminView.PackRow(pack.getId(), pack.getName(), pack.isFree(), pack.getPricePln(),
                pack.getAudience(), pack.getAvailableFrom(), pack.getAvailableUntil(), cardCount);
    }

    public CardPack createPack(String name, boolean free, BigDecimal pricePln, PackAudience audience,
                                LocalDate availableFrom, LocalDate availableUntil) {
        validatePackName(name);
        CardPack pack = cardPackRepository.save(
                new CardPack(name.trim(), free, pricePln, audience, availableFrom, availableUntil));
        log.info("Admin: nowy pakiet kart {} ({})", pack.getName(), audience);
        return pack;
    }

    public void updatePack(Long packId, String name, boolean free, BigDecimal pricePln, PackAudience audience,
                            LocalDate availableFrom, LocalDate availableUntil) {
        validatePackName(name);
        CardPack pack = requirePack(packId);
        pack.update(name.trim(), free, pricePln, audience, availableFrom, availableUntil);
        log.info("Admin: edycja pakietu {}", pack.getName());
    }

    public Card createCard(String text, CardType type, Long packId) {
        validateCardText(text);
        CardPack pack = requirePack(packId);
        Card card = cardRepository.save(new Card(text.trim(), type, pack));
        log.info("Admin: nowa karta w pakiecie {} ({})", pack.getName(), type);
        return card;
    }

    public void updateCard(Long cardId, String text, CardType type, Long packId) {
        validateCardText(text);
        Card card = requireCard(cardId);
        CardPack pack = requirePack(packId);
        card.update(text.trim(), type, pack);
        log.debug("Admin: edycja karty {}", cardId);
    }

    public void setCardEnabled(Long cardId, boolean enabled) {
        Card card = requireCard(cardId);
        if (enabled) {
            card.enable();
        } else {
            card.disable();
        }
        log.info("Admin: karta {} {}", cardId, enabled ? "włączona" : "wyłączona");
    }

    /**
     * Plain-text bulk import — one card's text per line, blank lines skipped. Keeps the project
     * free of a CSV-parsing dependency: card text often contains commas, so a real multi-column
     * CSV would need proper quoting anyway, and the admin only ever picks one pack + one type here.
     */
    public int importCards(Long packId, CardType type, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new InvalidCardActionException("Wybierz plik do zaimportowania.");
        }
        CardPack pack = requirePack(packId);

        List<String> lines;
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))) {
            lines = reader.lines().map(String::trim).filter(line -> !line.isEmpty()).toList();
        } catch (IOException e) {
            log.warn("Admin: nie udało się odczytać pliku importu — {}", e.getMessage());
            throw new InvalidCardActionException("Nie udało się odczytać pliku.");
        }

        if (lines.isEmpty()) {
            throw new InvalidCardActionException("Plik nie zawiera żadnych kart.");
        }
        if (lines.size() > MAX_IMPORT_LINES) {
            throw new InvalidCardActionException("Za dużo linii w pliku (max " + MAX_IMPORT_LINES + ").");
        }
        for (String line : lines) {
            validateCardText(line);
        }

        lines.forEach(line -> cardRepository.save(new Card(line, type, pack)));
        log.info("Admin: zaimportowano {} kart do pakietu {} ({})", lines.size(), pack.getName(), type);
        return lines.size();
    }

    private void validateCardText(String text) {
        String trimmed = text == null ? "" : text.trim();
        if (trimmed.isEmpty() || trimmed.length() > MAX_CARD_TEXT_LENGTH) {
            throw new InvalidCardActionException(
                    "Tekst karty musi mieć 1-" + MAX_CARD_TEXT_LENGTH + " znaków.");
        }
    }

    private void validatePackName(String name) {
        String trimmed = name == null ? "" : name.trim();
        if (trimmed.isEmpty() || trimmed.length() > MAX_PACK_NAME_LENGTH) {
            throw new InvalidCardActionException(
                    "Nazwa pakietu musi mieć 1-" + MAX_PACK_NAME_LENGTH + " znaków.");
        }
    }

    private CardPack requirePack(Long packId) {
        return cardPackRepository.findById(packId).orElseThrow(() -> new CardPackNotFoundException(packId));
    }

    private Card requireCard(Long cardId) {
        return cardRepository.findById(cardId).orElseThrow(() -> new CardNotFoundException(cardId));
    }
}
