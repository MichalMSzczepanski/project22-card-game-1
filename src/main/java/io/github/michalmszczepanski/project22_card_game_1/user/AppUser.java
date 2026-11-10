package io.github.michalmszczepanski.project22_card_game_1.user;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AppUser {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Getter
    private Long id;

    @Getter
    private String username;

    /** Null for accounts created via an OAuth2 provider (no local password). */
    @Getter
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Getter
    private AuthProvider authProvider;

    /** The provider's subject/user id. Null for LOCAL accounts. */
    private String providerId;

    private Instant createdAt;

    /** Local username+password account. */
    public AppUser(String username, String passwordHash) {
        this.username = username;
        this.passwordHash = passwordHash;
        this.authProvider = AuthProvider.LOCAL;
        this.createdAt = Instant.now();
    }

    /** Account created via an OAuth2/OIDC provider (Google, later Facebook, ...). */
    public AppUser(String username, AuthProvider authProvider, String providerId) {
        this.username = username;
        this.authProvider = authProvider;
        this.providerId = providerId;
        this.createdAt = Instant.now();
    }
}
