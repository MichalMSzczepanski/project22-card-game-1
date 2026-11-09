package io.github.michalmszczepanski.project22_card_game_1.user;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;

import java.time.Instant;

@Entity
public class AppUser {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String username;

    /** Null for accounts created via an OAuth2 provider (no local password). */
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    private AuthProvider authProvider;

    /** The provider's subject/user id. Null for LOCAL accounts. */
    private String providerId;

    private Instant createdAt;

    protected AppUser() {
    }

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

    public Long getId() {
        return id;
    }

    public String getUsername() {
        return username;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public AuthProvider getAuthProvider() {
        return authProvider;
    }

    public String getProviderId() {
        return providerId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
