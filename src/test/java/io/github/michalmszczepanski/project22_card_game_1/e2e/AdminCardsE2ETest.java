package io.github.michalmszczepanski.project22_card_game_1.e2e;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * README TODO "Karty i pakiety" — the Thymeleaf template (admin-cards.html) has enough
 * conditionals (empty pack list, no selected pack, card rows) that only rendering it for real
 * catches syntax mistakes a pure CardAdminServiceTest can't. Session handling copied from
 * AdminPanelE2ETest.
 */
@SpringBootTest(properties = {
        "operator.bootstrap-username=testop2",
        "operator.bootstrap-password=testpass123"
})
@AutoConfigureMockMvc
class AdminCardsE2ETest {

    private static final Pattern CSRF_PATTERN = Pattern.compile("name=\"_csrf\" value=\"([^\"]+)\"");

    @Autowired
    private MockMvc mockMvc;

    private Cookie sessionCookie;

    @Test
    void createsAPackThenAddsAndImportsCardsThroughTheRealPages() throws Exception {
        login();

        MvcResult cardsPage = getWithCookie("/admin/cards");
        assertThat(cardsPage.getResponse().getContentAsString()).contains("Karty i pakiety");
        String csrfToken = extractCsrfToken(cardsPage.getResponse().getContentAsString());

        MvcResult created = mockMvc.perform(postWithCookie("/admin/packs")
                        .param("_csrf", csrfToken)
                        .param("name", "E2E pakiet")
                        .param("audience", "FAMILY")
                        .param("free", "true"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("/admin/cards?packId=*"))
                .andReturn();
        rememberSessionCookie(created);
        String packId = created.getResponse().getRedirectedUrl().substring("/admin/cards?packId=".length());

        MvcResult packPage = getWithCookie("/admin/cards?packId=" + packId);
        String bodyAfterPackCreated = packPage.getResponse().getContentAsString();
        assertThat(bodyAfterPackCreated).contains("E2E pakiet");
        String csrfToken2 = extractCsrfToken(bodyAfterPackCreated);

        mockMvc.perform(postWithCookie("/admin/cards")
                        .param("_csrf", csrfToken2)
                        .param("text", "Testowa karta")
                        .param("type", "ANSWER")
                        .param("packId", packId))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/cards?packId=" + packId));

        MvcResult withCard = getWithCookie("/admin/cards?packId=" + packId);
        String bodyWithCard = withCard.getResponse().getContentAsString();
        assertThat(bodyWithCard).contains("Testowa karta");
        String csrfToken3 = extractCsrfToken(bodyWithCard);

        MockMultipartFile file = new MockMultipartFile("file", "karty.csv", "text/csv",
                "Z importu 1\nZ importu 2".getBytes(StandardCharsets.UTF_8));
        mockMvc.perform(multipartWithCookie("/admin/cards/import")
                        .file(file)
                        .param("_csrf", csrfToken3)
                        .param("packId", packId)
                        .param("type", "ANSWER"))
                .andExpect(status().is3xxRedirection());

        MvcResult finalPage = getWithCookie("/admin/cards?packId=" + packId);
        String finalBody = finalPage.getResponse().getContentAsString();
        assertThat(finalBody).contains("Zaimportowano 2 kart.");
        assertThat(finalBody).contains("Z importu 1").contains("Z importu 2");

        // Toggle one card off, then edit its text — both round-trip through the real pages too.
        Matcher cardIdMatcher = Pattern.compile("cards/(\\d+)/toggle").matcher(finalBody);
        assertThat(cardIdMatcher.find()).isTrue();
        String cardId = cardIdMatcher.group(1);
        String csrfToken4 = extractCsrfToken(finalBody);

        mockMvc.perform(postWithCookie("/admin/cards/" + cardId + "/toggle")
                        .param("_csrf", csrfToken4)
                        .param("packId", packId)
                        .param("enabled", "false"))
                .andExpect(status().is3xxRedirection());

        MvcResult afterToggle = getWithCookie("/admin/cards?packId=" + packId);
        String bodyAfterToggle = afterToggle.getResponse().getContentAsString();
        assertThat(bodyAfterToggle).contains("wyłączona");
        String csrfToken5 = extractCsrfToken(bodyAfterToggle);

        mockMvc.perform(postWithCookie("/admin/cards/" + cardId)
                        .param("_csrf", csrfToken5)
                        .param("text", "Po edycji")
                        .param("type", "ANSWER")
                        .param("packId", packId))
                .andExpect(status().is3xxRedirection());

        MvcResult afterEdit = getWithCookie("/admin/cards?packId=" + packId);
        assertThat(afterEdit.getResponse().getContentAsString()).contains("Po edycji");
    }

    private void login() throws Exception {
        MvcResult loginGet = getWithCookie("/admin/login");
        String csrfToken = extractCsrfToken(loginGet.getResponse().getContentAsString());

        MvcResult loginPost = mockMvc.perform(postWithCookie("/admin/login")
                        .param("_csrf", csrfToken)
                        .param("username", "testop2")
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

    private org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder multipartWithCookie(String path) {
        var request = multipart(path);
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
