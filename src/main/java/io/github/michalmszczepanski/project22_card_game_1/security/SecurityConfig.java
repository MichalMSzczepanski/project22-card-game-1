package io.github.michalmszczepanski.project22_card_game_1.security;

import io.github.michalmszczepanski.project22_card_game_1.admin.OperatorUserDetailsService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.CsrfConfigurer;
import org.springframework.security.config.annotation.web.configurers.HeadersConfigurer;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Three filter chains:
 * <ul>
 *   <li>{@link #h2ConsoleFilterChain} — relaxed framing/CSRF, only reachable at all when
 *       {@code spring.h2.console.enabled=true} (off by default, see application.yaml).</li>
 *   <li>{@link #adminFilterChain} — {@code /admin/**}, the operator panel (README TODO "Panel
 *       admina aplikacji"). A completely separate identity from players: its own
 *       {@link OperatorUserDetailsService}/{@code Operator} table, own {@link DaoAuthenticationProvider}
 *       scoped to just this chain — never mixed with the (disabled) player-account machinery below.</li>
 *   <li>{@link #appFilterChain} — everything else, {@code permitAll()}.</li>
 * </ul>
 *
 * Etap 1 (README TODO, FORMAL.md): player rejestracja/logowanie/OAuth są wyłączone — the public
 * app is anonymous, hence {@code permitAll()} in {@link #appFilterChain}. Entities/services for
 * player accounts (packages {@code user}/{@code security}: {@code UserService},
 * {@code DatabaseUserDetailsService}, {@code CustomOAuth2UserService}, {@code AuthenticationAuditListener})
 * stay in the repo unused — ready to reconnect in etap 2 (ROADMAP.md) instead of rewriting them.
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
    public SecurityFilterChain adminFilterChain(HttpSecurity http,
                                                 OperatorUserDetailsService operatorUserDetailsService,
                                                 PasswordEncoder passwordEncoder) throws Exception {
        DaoAuthenticationProvider operatorAuthProvider = new DaoAuthenticationProvider(operatorUserDetailsService);
        operatorAuthProvider.setPasswordEncoder(passwordEncoder);

        http
                .securityMatcher("/admin/**")
                .authenticationProvider(operatorAuthProvider)
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/admin/login").permitAll()
                        .anyRequest().hasRole("OPERATOR"))
                .formLogin(form -> form
                        .loginPage("/admin/login")
                        .defaultSuccessUrl("/admin", true)
                        .permitAll())
                .logout(logout -> logout
                        .logoutUrl("/admin/logout")
                        .logoutSuccessUrl("/admin/login")
                        .permitAll());
        return http.build();
    }

    @Bean
    @Order(3)
    public SecurityFilterChain appFilterChain(HttpSecurity http) throws Exception {
        http.authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
        return http.build();
    }
}
