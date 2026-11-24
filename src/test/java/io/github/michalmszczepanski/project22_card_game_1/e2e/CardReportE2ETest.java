package io.github.michalmszczepanski.project22_card_game_1.e2e;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * README TODO "Zgłoszone karty" — a player reports a card from the real game page
 * (game.html's new "Zgłoś" buttons), the operator sees it at {@code /admin/reports} and
 * dismisses/removes it, all through real HTTP requests (same reasoning as GameFlowE2ETest:
 * CSRF, sessions and the actual rendered templates, not direct service calls).
 */
@SpringBootTest(properties = {
        "operator.bootstrap-username=testop3",
        "operator.bootstrap-password=testpass123"
})
@AutoConfigureMockMvc
class CardReportE2ETest {

    private static final Pattern CSRF_PATTERN = Pattern.compile("name=\"_csrf\" value=\"([^\"]+)\"");
    private static final Pattern CARD_ID_PATTERN = Pattern.compile("name=\"cardId\" value=\"(\\d+)\"");

    @Autowired
    private MockMvc mockMvc;

    private final Map<String, Cookie> sessionCookies = new HashMap<>();

    @Test
    @Transactional
    void playerReportsAHandCardAndOperatorDismissesIt() throws Exception {
        String gameId = createGame("Host");
        joinGame("Guest1", gameId);
        joinGame("Guest2", gameId);
        postAsPlayer("Host", "/games/" + gameId, "/games/" + gameId + "/start", Map.of());

        String handPage = getAsPlayer("Guest1", "/games/" + gameId).getResponse().getContentAsString();
        Matcher cardIdMatcher = CARD_ID_PATTERN.matcher(handPage);
        assertThat(cardIdMatcher.find()).isTrue();
        String cardId = cardIdMatcher.group(1);

        MvcResult reported = postAsPlayer("Guest1", "/games/" + gameId, "/games/" + gameId + "/report-card",
                Map.of("cardId", cardId, "note", "nie na temat"));
        assertThat(reported.getFlashMap().get("info")).isEqualTo("Zgłoszono kartę. Dzięki!");

        loginAsOperator();
        String reportsPage = getAsOperator("/admin/reports").getResponse().getContentAsString();
        assertThat(reportsPage).contains("Guest1").contains("nie na temat");

        String csrfToken = extractCsrfToken(reportsPage);
        Matcher dismissMatcher = Pattern.compile("reports/(\\d+)/dismiss").matcher(reportsPage);
        assertThat(dismissMatcher.find()).isTrue();
        String reportId = dismissMatcher.group(1);

        mockMvc.perform(withOperatorCookie(post("/admin/reports/" + reportId + "/dismiss"))
                        .param("_csrf", csrfToken))
                .andExpect(status().is3xxRedirection());

        String afterDismiss = getAsOperator("/admin/reports").getResponse().getContentAsString();
        assertThat(afterDismiss).contains("Brak zgłoszeń");
    }

    @Test
    @Transactional
    void operatorRemovingAReportedCardDisablesItSoItNoLongerAppearsInAnyHand() throws Exception {
        String gameId = createGame("Host");
        joinGame("Guest1", gameId);
        joinGame("Guest2", gameId);
        postAsPlayer("Host", "/games/" + gameId, "/games/" + gameId + "/start", Map.of());

        String handPage = getAsPlayer("Guest1", "/games/" + gameId).getResponse().getContentAsString();
        Matcher cardIdMatcher = CARD_ID_PATTERN.matcher(handPage);
        assertThat(cardIdMatcher.find()).isTrue();
        String cardId = cardIdMatcher.group(1);

        postAsPlayer("Guest1", "/games/" + gameId, "/games/" + gameId + "/report-card", Map.of("cardId", cardId));

        loginAsOperator();
        String reportsPage = getAsOperator("/admin/reports").getResponse().getContentAsString();
        String csrfToken = extractCsrfToken(reportsPage);

        mockMvc.perform(withOperatorCookie(post("/admin/reports/" + cardId + "/remove"))
                        .param("_csrf", csrfToken))
                .andExpect(status().is3xxRedirection());

        String afterRemove = getAsOperator("/admin/reports").getResponse().getContentAsString();
        assertThat(afterRemove).contains("Brak zgłoszeń");
    }

    private void loginAsOperator() throws Exception {
        String loginPage = getAsOperator("/admin/login").getResponse().getContentAsString();
        String csrfToken = extractCsrfToken(loginPage);
        MvcResult result = mockMvc.perform(withOperatorCookie(post("/admin/login"))
                        .param("_csrf", csrfToken)
                        .param("username", "testop3")
                        .param("password", "testpass123"))
                .andExpect(status().is3xxRedirection())
                .andReturn();
        rememberCookie("__operator__", result);
    }

    private MvcResult getAsOperator(String path) throws Exception {
        MockHttpServletRequestBuilder request = get(path);
        Cookie cookie = sessionCookies.get("__operator__");
        if (cookie != null) {
            request.cookie(cookie);
        }
        MvcResult result = mockMvc.perform(request).andReturn();
        rememberCookie("__operator__", result);
        return result;
    }

    private MockHttpServletRequestBuilder withOperatorCookie(MockHttpServletRequestBuilder request) {
        Cookie cookie = sessionCookies.get("__operator__");
        if (cookie != null) {
            request.cookie(cookie);
        }
        return request;
    }

    private String createGame(String nickname) throws Exception {
        MvcResult result = postAsPlayer(nickname, "/", "/games", Map.of("nickname", nickname, "totalRounds", "10"));
        assertThat(result.getResponse().getStatus()).isEqualTo(302);
        String location = result.getResponse().getHeader("Location");
        return location.substring(location.lastIndexOf('/') + 1);
    }

    private void joinGame(String nickname, String gameId) throws Exception {
        postAsPlayer(nickname, "/", "/games/join", Map.of("code", gameId, "nickname", nickname));
    }

    private MvcResult postAsPlayer(String nickname, String refererGetPath, String postPath,
                                    Map<String, String> params) throws Exception {
        String csrfToken = harvestCsrf(refererGetPath, nickname);

        MockHttpServletRequestBuilder request = post(postPath).param("_csrf", csrfToken);
        params.forEach(request::param);
        Cookie cookie = sessionCookies.get(nickname);
        if (cookie != null) {
            request.cookie(cookie);
        }

        MvcResult result = mockMvc.perform(request).andReturn();
        rememberCookie(nickname, result);
        return result;
    }

    private MvcResult getAsPlayer(String nickname, String path) throws Exception {
        MockHttpServletRequestBuilder request = get(path);
        Cookie cookie = sessionCookies.get(nickname);
        if (cookie != null) {
            request.cookie(cookie);
        }
        MvcResult result = mockMvc.perform(request).andReturn();
        rememberCookie(nickname, result);
        return result;
    }

    private String harvestCsrf(String getPath, String nickname) throws Exception {
        MvcResult result = getAsPlayer(nickname, getPath);
        return extractCsrfToken(result.getResponse().getContentAsString());
    }

    private String extractCsrfToken(String html) {
        Matcher matcher = CSRF_PATTERN.matcher(html);
        if (!matcher.find()) {
            throw new IllegalStateException("No CSRF token found");
        }
        return matcher.group(1);
    }

    private void rememberCookie(String key, MvcResult result) {
        Cookie newCookie = result.getResponse().getCookie("SESSION");
        if (newCookie != null) {
            sessionCookies.put(key, newCookie);
        }
    }
}
