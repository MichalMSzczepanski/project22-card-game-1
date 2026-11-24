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
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * README TODO "Panel admina aplikacji" (minimalna wersja) — a completely separate identity from
 * players: the account here exists only because the test overrides {@code operator.bootstrap-*}
 * (normally blank, see OperatorSeeder); nothing in the public app ever links to {@code /admin}.
 *
 * <p>Session continuity is cookie-based (the "SESSION" cookie), same as {@code GameFlowE2ETest} —
 * this app's session mechanism is Spring Session JDBC ({@code @EnableJdbcHttpSession}), not the
 * plain servlet session, so a {@code MockHttpSession} object carried directly between requests
 * doesn't round-trip through it the way a real browser's session cookie does.
 */
@SpringBootTest(properties = {
        "operator.bootstrap-username=testop",
        "operator.bootstrap-password=testpass123"
})
@AutoConfigureMockMvc
class AdminPanelE2ETest {

    private static final Pattern CSRF_PATTERN = Pattern.compile("name=\"_csrf\" value=\"([^\"]+)\"");

    @Autowired
    private MockMvc mockMvc;

    private Cookie sessionCookie;

    @Test
    void anonymousRequestToAdminIsRedirectedToAdminLogin() throws Exception {
        mockMvc.perform(get("/admin"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/login"));
    }

    @Test
    void wrongPasswordIsRejected() throws Exception {
        mockMvc.perform(post("/admin/login").with(csrf())
                        .param("username", "testop")
                        .param("password", "not-the-password"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/login?error"));
    }

    @Test
    void correctLoginReachesTheDashboardAndCanForceEndALiveGame() throws Exception {
        login();

        MvcResult dashboard = getWithCookie("/admin");
        assertThat(dashboard.getResponse().getContentAsString()).contains("aktywne gry");

        String gameId = createGameAsPlayer("Host");

        MvcResult dashboardWithGame = getWithCookie("/admin");
        assertThat(dashboardWithGame.getResponse().getContentAsString()).contains(gameId);

        String csrfToken = extractCsrfToken(dashboardWithGame.getResponse().getContentAsString());
        mockMvc.perform(postWithCookie("/admin/games/" + gameId + "/end").param("_csrf", csrfToken))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin"));

        MvcResult afterEnd = getWithCookie("/admin");
        // The game no longer shows up as LIVE — it still appears once, in the flash success message.
        assertThat(afterEnd.getResponse().getContentAsString()).contains("Brak aktywnych gier.");
    }

    private void login() throws Exception {
        MvcResult loginGet = getWithCookie("/admin/login");
        String csrfToken = extractCsrfToken(loginGet.getResponse().getContentAsString());

        MvcResult loginPost = mockMvc.perform(postWithCookie("/admin/login")
                        .param("_csrf", csrfToken)
                        .param("username", "testop")
                        .param("password", "testpass123"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin"))
                .andReturn();
        rememberSessionCookie(loginPost);
    }

    private String createGameAsPlayer(String nickname) throws Exception {
        MvcResult homeGet = getWithCookie("/");
        String csrfToken = extractCsrfToken(homeGet.getResponse().getContentAsString());

        MvcResult created = mockMvc.perform(postWithCookie("/games")
                        .param("_csrf", csrfToken)
                        .param("nickname", nickname)
                        .param("totalRounds", "10"))
                .andExpect(status().is3xxRedirection())
                .andReturn();
        String location = created.getResponse().getRedirectedUrl();
        return location.substring(location.lastIndexOf('/') + 1);
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
