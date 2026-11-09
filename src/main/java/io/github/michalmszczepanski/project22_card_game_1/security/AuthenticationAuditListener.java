package io.github.michalmszczepanski.project22_card_game_1.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.security.authentication.event.AbstractAuthenticationFailureEvent;
import org.springframework.security.authentication.event.AuthenticationSuccessEvent;
import org.springframework.stereotype.Component;

/** Logs every login attempt (form login and OAuth2 alike — Spring publishes these events for both). */
@Component
public class AuthenticationAuditListener {

    private static final Logger log = LoggerFactory.getLogger(AuthenticationAuditListener.class);

    @EventListener
    public void onSuccess(AuthenticationSuccessEvent event) {
        log.info("Logowanie OK: {}", event.getAuthentication().getName());
    }

    @EventListener
    public void onFailure(AbstractAuthenticationFailureEvent event) {
        log.warn("Nieudane logowanie: {} ({})", event.getAuthentication().getName(), event.getException().getMessage());
    }
}
