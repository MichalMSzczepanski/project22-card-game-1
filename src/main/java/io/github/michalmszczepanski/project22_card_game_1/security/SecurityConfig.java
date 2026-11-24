package io.github.michalmszczepanski.project22_card_game_1.security;

import io.github.michalmszczepanski.project22_card_game_1.admin.OperatorUserDetailsService;
import org.springframework.beans.factory.ObjectProvider;
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
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Three filter chains:
 * <ul>
 *   <li>{@link #h2ConsoleFilterChain} — relaxed framing/CSRF, only reachable at all when
 *       {@code spring.h2.console.enabled=true} (off by default, see application.yaml).</li>
 *   <li>{@link #adminFilterChain} — {@code /admin/**}, the operator panel (README TODO "Panel
 *       admina aplikacji"). A completely separate identity from players: its own
 *       {@link OperatorUserDetailsService}/{@code Operator} table, own {@link DaoAuthenticationProvider}
 *       scoped to just this chain — never mixed with the player-account machinery below.</li>
 *   <li>{@link #appFilterChain} — everything else. Gameplay stays guest-friendly (README
 *       invariant, {@code permitAll()}); only {@code /me/**} ("Propozycje kart od graczy") needs
 *       an actual player account.</li>
 * </ul>
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
    public SecurityFilterChain appFilterChain(HttpSecurity http,
                                               DatabaseUserDetailsService databaseUserDetailsService,
                                               PasswordEncoder passwordEncoder,
                                               CustomOAuth2UserService customOAuth2UserService,
                                               ObjectProvider<ClientRegistrationRepository> clientRegistrations) throws Exception {
        // Explicit provider, same reasoning as adminFilterChain: with two UserDetailsService beans
        // in context (this one + operatorUserDetailsService), Spring Boot won't auto-wire either
        // into the global AuthenticationManager.
        DaoAuthenticationProvider playerAuthProvider = new DaoAuthenticationProvider(databaseUserDetailsService);
        playerAuthProvider.setPasswordEncoder(passwordEncoder);

        http
                .authenticationProvider(playerAuthProvider)
                .authorizeHttpRequests(auth -> auth
                        // "Propozycje kart od graczy" (README TODO) — the only player-side route
                        // that actually requires login; everything else (gameplay) stays guest-friendly.
                        .requestMatchers("/me/**").authenticated()
                        .anyRequest().permitAll())
                .formLogin(form -> form
                        .loginPage("/login")
                        .defaultSuccessUrl("/", false)
                        .permitAll())
                .logout(logout -> logout
                        .logoutUrl("/logout")
                        .logoutSuccessUrl("/")
                        .permitAll());

        // Only wired up once a provider (google, later facebook, ...) is actually configured in
        // application.yaml — otherwise there is no ClientRegistrationRepository bean and enabling
        // oauth2Login() would fail application startup.
        if (clientRegistrations.getIfAvailable() != null) {
            http.oauth2Login(oauth2 -> oauth2
                    .loginPage("/login")
                    .userInfoEndpoint(info -> info
                            .oidcUserService(customOAuth2UserService::processOidcUser)
                            .userService(customOAuth2UserService::processOAuth2User)));
        }

        return http.build();
    }
}
