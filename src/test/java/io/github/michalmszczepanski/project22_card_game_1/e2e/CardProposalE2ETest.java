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

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * README TODO "Propozycje kart od graczy" — a logged-in player proposes a card on the real
 * {@code /me/proposals} page, the operator reviews it at {@code /admin/proposals}, and an
 * anonymous visit to {@code /me/proposals} is redirected to log in first (the one route that
 * actually requires an account — see SecurityConfig.appFilterChain).
 */
@SpringBootTest(properties = {
        "operator.bootstrap-username=testop5",
        "operator.bootstrap-password=testpass123"
})
@AutoConfigureMockMvc
class CardProposalE2ETest {

    private static final Pattern CSRF_PATTERN = Pattern.compile("name=\"_csrf\" value=\"([^\"]+)\"");

    @Autowired
    private MockMvc mockMvc;

    private Cookie playerCookie;
    private Cookie operatorCookie;

    @Test
    @Transactional
    void anonymousVisitToMyProposalsIsRedirectedToLogin() throws Exception {
        mockMvc.perform(get("/me/proposals"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
    }

    @Test
    @Transactional
    void loggedInPlayerCanProposeACardAndOperatorCanApproveIt() throws Exception {
        registerAndLogin("proposer1", "sekretne123");

        String panelBefore = getAsPlayer("/me/proposals").getResponse().getContentAsString();
        assertThat(panelBefore).contains("Nie masz jeszcze żadnych propozycji");
        String csrfToken = extractCsrfToken(panelBefore);

        mockMvc.perform(postAsPlayer("/me/proposals")
                        .param("_csrf", csrfToken)
                        .param("text", "Moja propozycja karty")
                        .param("type", "ANSWER"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/me/proposals"));

        String panelAfter = getAsPlayer("/me/proposals").getResponse().getContentAsString();
        assertThat(panelAfter).contains("Moja propozycja karty").contains("czeka");

        loginAsOperator();
        String queuePage = getAsOperator("/admin/proposals").getResponse().getContentAsString();
        assertThat(queuePage).contains("Moja propozycja karty").contains("proposer1");
        String operatorCsrf = extractCsrfToken(queuePage);
        Matcher approveMatcher = Pattern.compile("proposals/(\\d+)/approve").matcher(queuePage);
        assertThat(approveMatcher.find()).isTrue();
        String proposalId = approveMatcher.group(1);

        mockMvc.perform(postAsOperator("/admin/proposals/" + proposalId + "/approve")
                        .param("_csrf", operatorCsrf))
                .andExpect(status().is3xxRedirection());

        String queueAfterApproval = getAsOperator("/admin/proposals").getResponse().getContentAsString();
        assertThat(queueAfterApproval).doesNotContain("Moja propozycja karty");

        String panelAfterApproval = getAsPlayer("/me/proposals").getResponse().getContentAsString();
        assertThat(panelAfterApproval).contains("zaakceptowana");

        String packsPage = mockMvc.perform(get("/packs")).andReturn().getResponse().getContentAsString();
        assertThat(packsPage).contains("Od graczy");
    }

    @Test
    @Transactional
    void operatorCanRejectAProposalWithAReason() throws Exception {
        registerAndLogin("proposer2", "sekretne123");
        String panel = getAsPlayer("/me/proposals").getResponse().getContentAsString();
        String csrfToken = extractCsrfToken(panel);
        mockMvc.perform(postAsPlayer("/me/proposals")
                .param("_csrf", csrfToken)
                .param("text", "Słaba propozycja")
                .param("type", "ANSWER"));

        loginAsOperator();
        String queuePage = getAsOperator("/admin/proposals").getResponse().getContentAsString();
        String operatorCsrf = extractCsrfToken(queuePage);
        Matcher rejectMatcher = Pattern.compile("proposals/(\\d+)/reject").matcher(queuePage);
        assertThat(rejectMatcher.find()).isTrue();
        String proposalId = rejectMatcher.group(1);

        mockMvc.perform(postAsOperator("/admin/proposals/" + proposalId + "/reject")
                        .param("_csrf", operatorCsrf)
                        .param("note", "nie na temat"))
                .andExpect(status().is3xxRedirection());

        String panelAfter = getAsPlayer("/me/proposals").getResponse().getContentAsString();
        assertThat(panelAfter).contains("odrzucona").contains("nie na temat");
    }

    private void registerAndLogin(String username, String password) throws Exception {
        String registerPage = getAsPlayer("/register").getResponse().getContentAsString();
        String csrfToken = extractCsrfToken(registerPage);
        mockMvc.perform(postAsPlayer("/register")
                .param("_csrf", csrfToken)
                .param("username", username)
                .param("password", password));

        String loginPage = getAsPlayer("/login").getResponse().getContentAsString();
        String loginCsrf = extractCsrfToken(loginPage);
        MvcResult result = mockMvc.perform(postAsPlayer("/login")
                        .param("_csrf", loginCsrf)
                        .param("username", username)
                        .param("password", password))
                .andExpect(status().is3xxRedirection())
                .andReturn();
        rememberCookie(result, true);
    }

    private void loginAsOperator() throws Exception {
        String loginPage = getAsOperator("/admin/login").getResponse().getContentAsString();
        String csrfToken = extractCsrfToken(loginPage);
        MvcResult result = mockMvc.perform(postAsOperator("/admin/login")
                        .param("_csrf", csrfToken)
                        .param("username", "testop5")
                        .param("password", "testpass123"))
                .andExpect(status().is3xxRedirection())
                .andReturn();
        rememberCookie(result, false);
    }

    private MvcResult getAsPlayer(String path) throws Exception {
        MockHttpServletRequestBuilder request = get(path);
        if (playerCookie != null) {
            request.cookie(playerCookie);
        }
        MvcResult result = mockMvc.perform(request).andReturn();
        rememberCookie(result, true);
        return result;
    }

    private MockHttpServletRequestBuilder postAsPlayer(String path) {
        MockHttpServletRequestBuilder request = post(path);
        if (playerCookie != null) {
            request.cookie(playerCookie);
        }
        return request;
    }

    private MvcResult getAsOperator(String path) throws Exception {
        MockHttpServletRequestBuilder request = get(path);
        if (operatorCookie != null) {
            request.cookie(operatorCookie);
        }
        MvcResult result = mockMvc.perform(request).andReturn();
        rememberCookie(result, false);
        return result;
    }

    private MockHttpServletRequestBuilder postAsOperator(String path) {
        MockHttpServletRequestBuilder request = post(path);
        if (operatorCookie != null) {
            request.cookie(operatorCookie);
        }
        return request;
    }

    private void rememberCookie(MvcResult result, boolean player) {
        Cookie newCookie = result.getResponse().getCookie("SESSION");
        if (newCookie != null) {
            if (player) {
                playerCookie = newCookie;
            } else {
                operatorCookie = newCookie;
            }
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
