package io.github.michalmszczepanski.project22_card_game_1.security;

import io.github.michalmszczepanski.project22_card_game_1.user.AppUser;
import io.github.michalmszczepanski.project22_card_game_1.user.AppUserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class DatabaseUserDetailsService implements UserDetailsService {

    private final AppUserRepository appUserRepository;

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
