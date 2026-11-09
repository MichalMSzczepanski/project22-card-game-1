package io.github.michalmszczepanski.project22_card_game_1.security;

import io.github.michalmszczepanski.project22_card_game_1.user.AppUser;
import io.github.michalmszczepanski.project22_card_game_1.user.AppUserRepository;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

@Component
public class CurrentUserResolver {

    private final AppUserRepository appUserRepository;

    public CurrentUserResolver(AppUserRepository appUserRepository) {
        this.appUserRepository = appUserRepository;
    }

    /** Returns the logged-in {@link AppUser}, or null for an anonymous guest. */
    public AppUser resolve(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()
                || "anonymousUser".equals(authentication.getPrincipal())) {
            return null;
        }
        return appUserRepository.findByUsername(authentication.getName()).orElse(null);
    }
}
