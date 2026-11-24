package io.github.michalmszczepanski.project22_card_game_1.e2e;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Static info pages — zero game logic, so just "does it render, does it say the right
 * things, is it linked from home". FORMAL.md etap 1: "Krótka strona 'O grze'" (darmowa, bez
 * danych osobowych, 18+ poza trybem rodzinnym) + a short player-facing "Jak grać" tutorial.
 */
@SpringBootTest
@AutoConfigureMockMvc
class InfoPagesE2ETest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void aboutPageMentionsFreeNoPersonalDataAndAdultContent() throws Exception {
        mockMvc.perform(get("/o-grze"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("darmowa")))
                .andExpect(content().string(containsString("danych osobowych")))
                .andExpect(content().string(containsString("18+")));
    }

    @Test
    void howToPlayPageExplainsPodiumScoringAndRotatingLeader() throws Exception {
        mockMvc.perform(get("/jak-grac"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("podium")))
                .andExpect(content().string(containsString("rotuje")));
    }

    @Test
    void homePageLinksToBothInfoPages() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("/o-grze")))
                .andExpect(content().string(containsString("/jak-grac")));
    }

    @Test
    void privacyPolicyPageExplainsAccountDataAndTheRightToDeleteIt() throws Exception {
        mockMvc.perform(get("/polityka-prywatnosci"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("/me/account")))
                .andExpect(content().string(containsString("hasło")))
                .andExpect(content().string(containsString("usun")));
    }

    @Test
    void homePageLinksToThePrivacyPolicy() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("/polityka-prywatnosci")));
    }
}
