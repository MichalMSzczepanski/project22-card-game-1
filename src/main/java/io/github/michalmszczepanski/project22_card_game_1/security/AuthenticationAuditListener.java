package io.github.michalmszczepanski.project22_card_game_1.security;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.security.authentication.event.AbstractAuthenticationFailureEvent;
import org.springframework.security.authentication.event.AuthenticationSuccessEvent;
import org.springframework.stereotype.Component;

/** Logs every login attempt (form login and OAuth2 alike — Spring publishes these events for both). */
@Component
@Slf4j
public class AuthenticationAuditListener {

    @EventListener
    public void onSuccess(AuthenticationSuccessEvent event) {
        log.info("Logowanie OK: {}", event.getAuthentication().getName());
    }

    @EventListener
    public void onFailure(AbstractAuthenticationFailureEvent event) {
        log.warn("Nieudane logowanie: {} ({})", event.getAuthentication().getName(), event.getException().getMessage());
    }
}
