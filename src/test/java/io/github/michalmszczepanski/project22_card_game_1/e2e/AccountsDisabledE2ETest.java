package io.github.michalmszczepanski.project22_card_game_1.e2e;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Etap 1 (README TODO, FORMAL.md): zakupy pakietów i historia wyników (/stats) są wyłączone —
 * sprawdza, że te trasy faktycznie nie istnieją (404), i że "/packs" nie proponuje kupowania.
 * Logowanie/rejestracja gracza WRÓCIŁY (README TODO "Propozycje kart od graczy") — te przypadki
 * są w {@link AuthE2ETest}, nie tutaj.
 */
@SpringBootTest
@AutoConfigureMockMvc
class AccountsDisabledE2ETest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void statsAndUnconfiguredOAuthRoutesStillDoNotExist() throws Exception {
        mockMvc.perform(get("/stats")).andExpect(status().isNotFound());
        // No Google client-id/secret configured in this environment -> no ClientRegistrationRepository
        // bean -> SecurityConfig skips oauth2Login() entirely, so the provider path is genuinely absent.
        mockMvc.perform(get("/oauth2/authorization/google")).andExpect(status().isNotFound());
    }

    @Test
    void purchasingAPackNoLongerExists() throws Exception {
        mockMvc.perform(post("/packs/1/purchase").with(csrf())).andExpect(status().isNotFound());
    }

    @Test
    void homePageOffersNoStatsLinkOrPurchaseFlow() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("/stats"))));
    }

    @Test
    void packsPageListsPacksWithoutPricesOrAPurchaseButton() throws Exception {
        mockMvc.perform(get("/packs"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("wkrótce")))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("/purchase"))))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString(" zł"))));
    }
}
