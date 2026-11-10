package io.github.michalmszczepanski.project22_card_game_1.card;

import io.github.michalmszczepanski.project22_card_game_1.user.AppUser;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Resolves which card packs a given user (or an anonymous guest) is allowed to
 * use: always the free packs, plus whatever paid packs they have purchased.
 */
@Service
@RequiredArgsConstructor
public class PackAccessService {

    private final CardPackRepository cardPackRepository;
    private final PurchaseRepository purchaseRepository;

    /** All packs the user (or guest, if null) may include in a game. */
    public List<CardPack> availablePacksFor(AppUser user) {
        List<CardPack> free = cardPackRepository.findAll().stream().filter(CardPack::isFree).toList();
        if (user == null) {
            return free;
        }
        List<CardPack> owned = purchaseRepository.findByUser(user).stream().map(Purchase::getPack).toList();
        return java.util.stream.Stream.concat(free.stream(), owned.stream()).distinct().toList();
    }

    /** Free packs plus whichever of the user's owned packs were explicitly requested. */
    public List<CardPack> resolveSelection(AppUser user, List<Long> requestedPackIds) {
        List<CardPack> available = availablePacksFor(user);
        if (requestedPackIds == null || requestedPackIds.isEmpty()) {
            return available.stream().filter(CardPack::isFree).toList();
        }
        return available.stream()
                .filter(p -> p.isFree() || requestedPackIds.contains(p.getId()))
                .toList();
    }
}
