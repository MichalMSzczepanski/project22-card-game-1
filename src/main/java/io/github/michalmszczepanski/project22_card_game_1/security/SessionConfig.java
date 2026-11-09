package io.github.michalmszczepanski.project22_card_game_1.security;

import org.springframework.context.annotation.Configuration;
import org.springframework.session.jdbc.config.annotation.web.http.EnableJdbcHttpSession;

/**
 * Spring Boot 4.x removed the old {@code spring.session.store-type=jdbc}
 * auto-configuration (verified: no "session" package at all in
 * spring-boot-autoconfigure-4.1.2.jar), so this has to be wired explicitly.
 * The actual table schema is applied via spring.sql.init.schema-locations
 * (see application.properties + session-schema-h2.sql) rather than Spring
 * Session's own non-idempotent scripts, so repeated restarts don't fail on
 * "table already exists".
 */
@Configuration
@EnableJdbcHttpSession
public class SessionConfig {
}
