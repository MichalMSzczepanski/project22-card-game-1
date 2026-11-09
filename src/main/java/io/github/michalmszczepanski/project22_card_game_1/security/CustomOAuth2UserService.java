package io.github.michalmszczepanski.project22_card_game_1.security;

import io.github.michalmszczepanski.project22_card_game_1.user.AppUser;
import io.github.michalmszczepanski.project22_card_game_1.user.AppUserRepository;
import io.github.michalmszczepanski.project22_card_game_1.user.AuthProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;

/**
 * Finds-or-creates the local {@link AppUser} behind an OAuth2/OIDC login.
 * Google uses OpenID Connect (processOidcUser); a plain-OAuth2 provider like
 * Facebook would go through processOAuth2User instead — both funnel into the
 * same upsertUser logic keyed by (provider, providerId), so adding a new
 * provider is just a new application.properties registration, no new code.
 */
@Service
public class CustomOAuth2UserService {

    private static final Logger log = LoggerFactory.getLogger(CustomOAuth2UserService.class);

    private final AppUserRepository appUserRepository;
    private final OidcUserService delegateOidc = new OidcUserService();
    private final DefaultOAuth2UserService delegateOAuth2 = new DefaultOAuth2UserService();

    public CustomOAuth2UserService(AppUserRepository appUserRepository) {
        this.appUserRepository = appUserRepository;
    }

    public OidcUser processOidcUser(org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest request) {
        OidcUser oidcUser = delegateOidc.loadUser(request);
        upsertUser(request.getClientRegistration().getRegistrationId(), oidcUser.getSubject(), oidcUser.getEmail());
        return oidcUser;
    }

    public OAuth2User processOAuth2User(OAuth2UserRequest request) {
        OAuth2User oAuth2User = delegateOAuth2.loadUser(request);
        String providerId = oAuth2User.getName();
        String email = oAuth2User.getAttribute("email");
        upsertUser(request.getClientRegistration().getRegistrationId(), providerId, email);
        return oAuth2User;
    }

    private void upsertUser(String registrationId, String providerId, String email) {
        AuthProvider provider = AuthProvider.valueOf(registrationId.toUpperCase());

        appUserRepository.findByAuthProviderAndProviderId(provider, providerId).orElseGet(() -> {
            String username = (email != null && !email.isBlank())
                    ? email
                    : provider.name().toLowerCase() + ":" + providerId;
            log.info("Nowa rejestracja użytkownika przez {}: {}", provider, username);
            return appUserRepository.save(new AppUser(username, provider, providerId));
        });
    }
}
