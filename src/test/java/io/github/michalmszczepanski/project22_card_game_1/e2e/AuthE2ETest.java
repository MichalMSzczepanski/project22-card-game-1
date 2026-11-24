package io.github.michalmszczepanski.project22_card_game_1.e2e;

import io.github.michalmszczepanski.project22_card_game_1.game.GameRepository;
import io.github.michalmszczepanski.project22_card_game_1.user.AppUserRepository;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * README TODO "Propozycje kart od graczy" — player login/registration, reconnected from the
 * dormant {@code user}/{@code security} packages (README/CLAUDE.md: "gotowe do podłączenia").
 * Real HTTP, CSRF and cookie-based sessions throughout, same reasoning as GameFlowE2ETest.
 */
@SpringBootTest
@AutoConfigureMockMvc
class AuthE2ETest {

    private static final Pattern CSRF_PATTERN = Pattern.compile("name=\"_csrf\" value=\"([^\"]+)\"");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AppUserRepository appUserRepository;

    @Autowired
    private GameRepository gameRepository;

    private Cookie sessionCookie;

    @Test
    @Transactional
    void registeringThenLoggingInSucceedsAndShowsTheLoggedInStateOnTheHomePage() throws Exception {
        register("gracz1", "sekretne123");
        login("gracz1", "sekretne123");

        String homeBody = getWithCookie("/").getResponse().getContentAsString();
        assertThat(homeBody).contains("Zalogowany jako").contains("gracz1");
        assertThat(appUserRepository.existsByUsername("gracz1")).isTrue();
    }

    @Test
    @Transactional
    void passwordIsNeverStoredInPlainText() throws Exception {
        register("gracz2", "sekretne123");
        var user = appUserRepository.findByUsername("gracz2").orElseThrow();
        assertThat(user.getPasswordHash()).isNotEqualTo("sekretne123");
        assertThat(user.getPasswordHash()).startsWith("$2"); // BCrypt
    }

    @Test
    @Transactional
    void duplicateUsernameIsRejected() throws Exception {
        register("gracz3", "sekretne123");
        MvcResult secondAttempt = register("gracz3", "inneHaslo456");
        assertThat(secondAttempt.getFlashMap().get("error").toString()).contains("zajęta");
    }

    @Test
    @Transactional
    void tooShortUsernameOrPasswordIsRejected() throws Exception {
        MvcResult shortUsername = register("ab", "sekretne123");
        assertThat(shortUsername.getFlashMap().get("error").toString()).contains("Login musi mieć");

        MvcResult shortPassword = register("gracz4", "short");
        assertThat(shortPassword.getFlashMap().get("error").toString()).contains("Hasło musi mieć");

        assertThat(appUserRepository.existsByUsername("gracz4")).isFalse();
    }

    @Test
    @Transactional
    void wrongPasswordIsRejected() throws Exception {
        register("gracz5", "sekretne123");

        String loginPage = getWithCookie("/login").getResponse().getContentAsString();
        String csrfToken = extractCsrfToken(loginPage);
        MvcResult result = mockMvc.perform(postWithCookie("/login")
                        .param("_csrf", csrfToken)
                        .param("username", "gracz5")
                        .param("password", "zlehaslo"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login?error"))
                .andReturn();
        rememberSessionCookie(result);
    }

    @Test
    @Transactional
    void creatingAGameWhileLoggedInLinksThePlayerToTheAccount() throws Exception {
        register("gracz6", "sekretne123");
        login("gracz6", "sekretne123");

        String homeBody = getWithCookie("/").getResponse().getContentAsString();
        String csrfToken = extractCsrfToken(homeBody);
        MvcResult created = mockMvc.perform(postWithCookie("/games")
                        .param("_csrf", csrfToken)
                        .param("nickname", "Gracz6WGrze")
                        .param("totalRounds", "10"))
                .andExpect(status().is3xxRedirection())
                .andReturn();
        String location = created.getResponse().getHeader("Location");
        String gameId = location.substring(location.lastIndexOf('/') + 1);

        var game = gameRepository.findById(gameId).orElseThrow();
        var player = game.getPlayers().get(0);
        Long appUserId = appUserRepository.findByUsername("gracz6").orElseThrow().getId();
        assertThat(player.getAppUserId()).isEqualTo(appUserId);
    }

    @Test
    @Transactional
    void loggingOutReturnsToTheAnonymousHomePage() throws Exception {
        register("gracz7", "sekretne123");
        login("gracz7", "sekretne123");

        String homeBody = getWithCookie("/").getResponse().getContentAsString();
        String csrfToken = extractCsrfToken(homeBody);
        mockMvc.perform(postWithCookie("/logout").param("_csrf", csrfToken))
                .andExpect(status().is3xxRedirection());

        String afterLogout = getWithCookie("/").getResponse().getContentAsString();
        assertThat(afterLogout).doesNotContain("Zalogowany jako");
        assertThat(afterLogout).contains("Zaloguj");
    }

    private MvcResult register(String username, String password) throws Exception {
        String registerPage = getWithCookie("/register").getResponse().getContentAsString();
        String csrfToken = extractCsrfToken(registerPage);
        return mockMvc.perform(postWithCookie("/register")
                        .param("_csrf", csrfToken)
                        .param("username", username)
                        .param("password", password))
                .andReturn();
    }

    private void login(String username, String password) throws Exception {
        String loginPage = getWithCookie("/login").getResponse().getContentAsString();
        String csrfToken = extractCsrfToken(loginPage);
        MvcResult result = mockMvc.perform(postWithCookie("/login")
                        .param("_csrf", csrfToken)
                        .param("username", username)
                        .param("password", password))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/"))
                .andReturn();
        rememberSessionCookie(result);
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
