package io.github.michalmszczepanski.project22_card_game_1.e2e;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * README TODO "Ustawienia serwisu bez redeployu" — flips maintenance mode + an announcement
 * banner through the real /admin/settings page, then checks both show up on the real public
 * index page (same reasoning as AdminCardsE2ETest: a rendered-template check catches what a
 * pure AppSettingsServiceTest can't).
 */
@SpringBootTest(properties = {
        "operator.bootstrap-username=testop4",
        "operator.bootstrap-password=testpass123"
})
@AutoConfigureMockMvc
class AdminSettingsE2ETest {

    private static final Pattern CSRF_PATTERN = Pattern.compile("name=\"_csrf\" value=\"([^\"]+)\"");

    @Autowired
    private MockMvc mockMvc;

    private Cookie sessionCookie;

    @Test
    void enablingMaintenanceModeAndAnAnnouncementShowsOnThePublicIndexPage() throws Exception {
        login();

        MvcResult settingsPage = getWithCookie("/admin/settings");
        String csrfToken = extractCsrfToken(settingsPage.getResponse().getContentAsString());

        mockMvc.perform(postWithCookie("/admin/settings")
                        .param("_csrf", csrfToken)
                        .param("maintenanceMode", "true")
                        .param("announcementBanner", "Dziś wieczorem krótka przerwa techniczna")
                        .param("blankCardEnabled", "true")
                        .param("comingSoonBadgeEnabled", "true"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/settings"));

        MvcResult indexResult = mockMvc.perform(get("/")).andReturn();
        String indexBody = indexResult.getResponse().getContentAsString();
        assertThat(indexBody).contains("Przerwa techniczna");
        assertThat(indexBody).contains("Dziś wieczorem krótka przerwa techniczna");
        assertThat(indexBody).doesNotContain("Stwórz nową grę");

        // Creating a game is rejected server-side too, not just hidden in the UI — same visitor session
        // (SESSION cookie) carried from the GET so the CSRF token harvested from it actually validates.
        String csrfTokenIndex = extractCsrfToken(indexBody);
        Cookie visitorCookie = indexResult.getResponse().getCookie("SESSION");
        MvcResult createAttempt = mockMvc.perform(post("/games")
                        .cookie(visitorCookie)
                        .param("_csrf", csrfTokenIndex)
                        .param("nickname", "Ktos")
                        .param("totalRounds", "10"))
                .andReturn();
        assertThat(createAttempt.getFlashMap().get("error").toString()).contains("przerwy technicznej");
    }

    @Test
    void disablingTheComingSoonBadgeHidesItOnThePacksPage() throws Exception {
        login();
        MvcResult settingsPage = getWithCookie("/admin/settings");
        String csrfToken = extractCsrfToken(settingsPage.getResponse().getContentAsString());

        mockMvc.perform(postWithCookie("/admin/settings")
                        .param("_csrf", csrfToken)
                        .param("blankCardEnabled", "true")
                        .param("comingSoonBadgeEnabled", "false"))
                .andExpect(status().is3xxRedirection());

        String packsBody = mockMvc.perform(get("/packs")).andReturn().getResponse().getContentAsString();
        assertThat(packsBody).doesNotContain("wkrótce");
    }

    private void login() throws Exception {
        MvcResult loginGet = getWithCookie("/admin/login");
        String csrfToken = extractCsrfToken(loginGet.getResponse().getContentAsString());

        MvcResult loginPost = mockMvc.perform(postWithCookie("/admin/login")
                        .param("_csrf", csrfToken)
                        .param("username", "testop4")
                        .param("password", "testpass123"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin"))
                .andReturn();
        rememberSessionCookie(loginPost);
    }

    private MvcResult getWithCookie(String path) throws Exception {
        MockHttpServletRequestBuilder request = get(path);
        if (sessionCookie != null) {
            request.cookie(sessionCookie);
        }
        MvcResult result = mockMvc.perform(request).andReturn();
        rememberSessionCookie(result);
        return result;
    }

    private MockHttpServletRequestBuilder postWithCookie(String path) {
        MockHttpServletRequestBuilder request = post(path);
        if (sessionCookie != null) {
            request.cookie(sessionCookie);
        }
        return request;
    }

    private void rememberSessionCookie(MvcResult result) {
        Cookie newCookie = result.getResponse().getCookie("SESSION");
        if (newCookie != null) {
            sessionCookie = newCookie;
        }
    }

    private String extractCsrfToken(String html) {
        Matcher matcher = CSRF_PATTERN.matcher(html);
        if (!matcher.find()) {
            throw new IllegalStateException("No CSRF token found");
        }
        return matcher.group(1);
    }
}
