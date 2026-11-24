package io.github.michalmszczepanski.project22_card_game_1.admin;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * The operator who runs the service (panel admina aplikacji, README TODO) — a completely
 * separate identity from {@code AppUser} (player accounts, disabled in etap 1) and from the
 * in-game "admin" role (README § 5, a rotating-table permission, not a login). No public
 * registration: accounts exist only via {@link OperatorSeeder}, from env vars.
 */
@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Operator {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Getter
    private Long id;

    @Getter
    private String username;

    @Getter
    private String passwordHash;

    public Operator(String username, String passwordHash) {
        this.username = username;
        this.passwordHash = passwordHash;
    }
}
