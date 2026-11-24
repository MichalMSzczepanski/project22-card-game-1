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
 * Etap 1 (README TODO, FORMAL.md): rejestracja, logowanie, OAuth, historia wyników i zakupy
 * pakietów są wyłączone — sprawdza, że te trasy faktycznie nie istnieją (404, nie np. przekierowanie
 * na nieistniejący "/login") i że strony, które zostają ("/" i "/packs"), nie proponują logowania
 * ani kupowania.
 */
@SpringBootTest
@AutoConfigureMockMvc
class AccountsDisabledE2ETest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void registrationLoginStatsAndOAuthRoutesNoLongerExist() throws Exception {
        mockMvc.perform(get("/register")).andExpect(status().isNotFound());
        mockMvc.perform(post("/register").with(csrf())).andExpect(status().isNotFound());
        mockMvc.perform(get("/login")).andExpect(status().isNotFound());
        mockMvc.perform(get("/stats")).andExpect(status().isNotFound());
        mockMvc.perform(get("/oauth2/authorization/google")).andExpect(status().isNotFound());
    }

    @Test
    void purchasingAPackNoLongerExists() throws Exception {
        mockMvc.perform(post("/packs/1/purchase").with(csrf())).andExpect(status().isNotFound());
    }

    @Test
    void homePageOffersNoLoginOrRegistrationLinks() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("/login"))))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("/register"))))
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
