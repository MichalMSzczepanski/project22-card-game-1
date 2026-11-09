package io.github.michalmszczepanski.project22_card_game_1.security;

import io.github.michalmszczepanski.project22_card_game_1.user.AppUser;
import io.github.michalmszczepanski.project22_card_game_1.user.AppUserRepository;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
public class DatabaseUserDetailsService implements UserDetailsService {

    private static final Logger log = LoggerFactory.getLogger(DatabaseUserDetailsService.class);

    private final AppUserRepository appUserRepository;

    public DatabaseUserDetailsService(AppUserRepository appUserRepository) {
        this.appUserRepository = appUserRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String username) {
        AppUser user = appUserRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException(username));

        if (user.getPasswordHash() == null) {
            // OAuth2-only account (Google/Facebook) — no local password to check against.
            log.debug("Próba logowania hasłem na konto bez hasła (provider={}): {}", user.getAuthProvider(), username);
            throw new UsernameNotFoundException(username);
        }

        return User.builder()
                .username(user.getUsername())
                .password(user.getPasswordHash())
                .roles("USER")
                .build();
    }
}
