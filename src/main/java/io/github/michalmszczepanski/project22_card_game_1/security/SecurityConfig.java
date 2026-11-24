package io.github.michalmszczepanski.project22_card_game_1.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.CsrfConfigurer;
import org.springframework.security.config.annotation.web.configurers.HeadersConfigurer;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Two filter chains on purpose: the H2 console needs relaxed framing/CSRF and is
 * only reachable at all when {@code spring.h2.console.enabled=true} (off by
 * default, see application.yaml) — isolating it here means the main app
 * chain keeps Spring Security's normal defaults (deny framing, CSRF everywhere).
 *
 * Etap 1 (README TODO, FORMAL.md): rejestracja/logowanie/OAuth są wyłączone —
 * cała appka jest dostępna anonimowo, stąd {@code permitAll()} na wszystko w
 * {@link #appFilterChain}. Encje i serwisy kont (pakiety {@code user}/
 * {@code security}: {@code UserService}, {@code DatabaseUserDetailsService},
 * {@code CustomOAuth2UserService}, {@code AuthenticationAuditListener}) zostają
 * w repo nieużywane — gotowe do podłączenia z powrotem w etapie 2 (ROADMAP.md),
 * zamiast pisania ich od nowa.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    @Order(1)
    public SecurityFilterChain h2ConsoleFilterChain(HttpSecurity http) throws Exception {
        http
                .securityMatcher("/h2-console/**")
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
                .csrf(CsrfConfigurer::disable)
                .headers(headers -> headers.frameOptions(HeadersConfigurer.FrameOptionsConfig::sameOrigin));
        return http.build();
    }

    @Bean
    @Order(2)
    public SecurityFilterChain appFilterChain(HttpSecurity http) throws Exception {
        http.authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
        return http.build();
    }
}
