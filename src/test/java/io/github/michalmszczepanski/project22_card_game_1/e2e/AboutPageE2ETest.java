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

/** FORMAL.md etap 1: "Krótka strona 'O grze'" — darmowa, bez danych osobowych, 18+ poza trybem rodzinnym. */
@SpringBootTest
@AutoConfigureMockMvc
class AboutPageE2ETest {

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
    void homePageLinksToTheAboutPage() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("/o-grze")));
    }
}
