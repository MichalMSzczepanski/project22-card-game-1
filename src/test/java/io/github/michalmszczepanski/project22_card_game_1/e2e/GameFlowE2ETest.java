package io.github.michalmszczepanski.project22_card_game_1.e2e;

import io.github.michalmszczepanski.project22_card_game_1.game.Game;
import io.github.michalmszczepanski.project22_card_game_1.game.GameProperties;
import io.github.michalmszczepanski.project22_card_game_1.game.GameRepository;
import io.github.michalmszczepanski.project22_card_game_1.game.GameStatus;
import io.github.michalmszczepanski.project22_card_game_1.game.Player;
import io.github.michalmszczepanski.project22_card_game_1.game.Round;
import io.github.michalmszczepanski.project22_card_game_1.game.Submission;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * True end-to-end tests: real HTTP requests through {@link MockMvc} — security filters, CSRF
 * validation and cookie-based Spring Session all included — not direct {@code GameService} calls
 * like {@code GameServiceTest}. Each virtual "player" is tracked by its own Spring Session cookie,
 * re-harvested together with a fresh CSRF token from whatever page that player last saw, exactly
 * like a real browser tab would. These run as part of the normal build ({@code ./mvnw test} / CI),
 * so they're replayed every time instead of being re-derived from scratch, and exist specifically
 * to catch HTTP-layer edge cases unit tests can't see (CSRF, cross-session isolation, redirect
 * targets, exact flash-message wording, orphaned-row regressions).
 *
 * <p>No class-level {@code @Transactional}: each test method gets its own plain {@code @Transactional}
 * (default propagation, no parameters) where a rollback-on-teardown is just convenient cleanup —
 * except {@link #creatingGameWithInvalidNicknameLeavesNoOrphanedGameInDatabase()}, which is
 * deliberately left with none at all (like {@code GameServiceMultiTransactionTest}) because that
 * test is specifically checking real commit/rollback behavior across requests; wrapping it in any
 * ambient transaction — even one tuned with a propagation parameter — would hide the one failure
 * mode it exists to catch.
 */
@SpringBootTest
@AutoConfigureMockMvc
class GameFlowE2ETest {

    private static final Pattern CSRF_PATTERN = Pattern.compile("name=\"_csrf\" value=\"([^\"]+)\"");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private GameRepository gameRepository;

    @Autowired
    private GameProperties gameProperties;

    /** One Spring Session cookie per virtual player ("browser tab"), keyed by nickname. */
    private final Map<String, Cookie> sessionCookies = new HashMap<>();

    @BeforeEach
    void resetSessions() {
        sessionCookies.clear();
    }

    @Test
    @Transactional
    void fullGameFlowEnforcesHostAndLeaderPriorityOverRealHttp() throws Exception {
        String gameId = createGame("Host", 2);
        joinGame("Guest1", gameId);
        joinGame("Guest2", gameId);

        // Edge case: a non-host can't start, even with a perfectly valid session + CSRF token.
        MvcResult rejected = startGame("Guest1", gameId);
        assertThat(rejected.getFlashMap().get("error")).isEqualTo("Tylko twórca gry może ją wystartować.");
        assertThat(gameRepository.findById(gameId).orElseThrow().getStatus()).isEqualTo(GameStatus.LOBBY);

        MvcResult started = startGame("Host", gameId);
        assertThat(started.getResponse().getStatus()).isEqualTo(302);
        assertThat(gameRepository.findById(gameId).orElseThrow().getStatus()).isEqualTo(GameStatus.ANSWERING);

        for (int roundNum = 1; roundNum <= 2; roundNum++) {
            Game game = gameRepository.findById(gameId).orElseThrow();
            List<Player> answerers = game.answerersInOrder();
            for (Player answerer : answerers) {
                answer(answerer.getNickname(), gameId, answerer.getHand().get(0).getId());
            }

            game = gameRepository.findById(gameId).orElseThrow();
            assertThat(game.getStatus()).isEqualTo(GameStatus.RANKING);
            Round round = game.getCurrentRound();
            String leaderNickname = round.getLeader().getNickname();

            int rank = 1;
            for (Submission submission : List.copyOf(round.getSubmissions())) {
                rank(leaderNickname, gameId, submission.getId(), rank++);
            }

            game = gameRepository.findById(gameId).orElseThrow();
            assertThat(game.getStatus()).isEqualTo(GameStatus.REVEALED);

            // Edge case: whoever ISN'T this round's leader can't jump the reveal-priority queue.
            String nonLeaderNickname = answerers.stream()
                    .map(Player::getNickname)
                    .filter(n -> !n.equals(leaderNickname))
                    .findFirst().orElseThrow();
            MvcResult jumpedQueue = nextRound(nonLeaderNickname, gameId);
            assertThat(jumpedQueue.getFlashMap().get("error"))
                    .isEqualTo("Jeszcze chwila — najpierw może kliknąć prowadzący tej rundy.");

            MvcResult advanced = nextRound(leaderNickname, gameId);
            assertThat(advanced.getResponse().getStatus()).isEqualTo(302);
        }

        assertThat(gameRepository.findById(gameId).orElseThrow().getStatus()).isEqualTo(GameStatus.FINISHED);
    }

    /** Deliberately no {@code @Transactional} here — see the class Javadoc for why. */
    @Test
    void creatingGameWithInvalidNicknameLeavesNoOrphanedGameInDatabase() throws Exception {
        long before = gameRepository.count();

        MvcResult result = postAsPlayer("NoNickname", "/", "/games", Map.of(
                "nickname", "",
                "totalRounds", "10"));

        assertThat(result.getFlashMap().get("error"))
                .isEqualTo("Nick musi mieć 1-" + gameProperties.maxNicknameLength() + " znaków.");
        assertThat(gameRepository.count()).isEqualTo(before);
    }

    @Test
    void creatingGameWithoutCsrfTokenIsRejected() throws Exception {
        mockMvc.perform(post("/games")
                        .param("nickname", "NoCsrf")
                        .param("totalRounds", "10"))
                .andExpect(status().isForbidden());
    }

    @Test
    @Transactional
    void joiningWithUnknownCodeRedirectsHomeWithFriendlyErrorInsteadOfServerError() throws Exception {
        MvcResult result = postAsPlayer("Lost", "/", "/games/join", Map.of(
                "code", "ZZZZZ",
                "nickname", "Lost"));

        assertThat(result.getResponse().getStatus()).isEqualTo(302);
        assertThat(result.getResponse().getRedirectedUrl()).isEqualTo("/");
        assertThat(result.getFlashMap().get("error")).isEqualTo("Nie znaleziono gry: ZZZZZ");
    }

    @Test
    @Transactional
    void joiningAFullRoomIsRejected() throws Exception {
        String gameId = createGame("P1", 10);
        int max = gameProperties.players().max();
        for (int i = 2; i <= max; i++) {
            joinGame("P" + i, gameId);
        }

        MvcResult rejected = postAsPlayer("Pn", "/", "/games/join", Map.of(
                "code", gameId,
                "nickname", "Pn"));

        assertThat(rejected.getFlashMap().get("error"))
                .isEqualTo("Pokój jest pełny (max " + max + " graczy).");
    }

    @Test
    @Transactional
    void creatingGameWithTooManyRoundsIsRejectedAndLeavesNoOrphan() throws Exception {
        long before = gameRepository.count();
        int tooMany = gameProperties.maxTotalRounds() + 1;

        MvcResult result = postAsPlayer("Greedy", "/", "/games", Map.of(
                "nickname", "Greedy",
                "totalRounds", String.valueOf(tooMany)));

        assertThat(result.getFlashMap().get("error"))
                .isEqualTo("Liczba rund musi być między 1 i " + gameProperties.maxTotalRounds() + ".");
        assertThat(gameRepository.count()).isEqualTo(before);
    }

    @Test
    @Transactional
    void joiningAGameThatAlreadyStartedIsRejected() throws Exception {
        String gameId = createGame("Host", 10);
        joinGame("Guest1", gameId);
        joinGame("Guest2", gameId);
        startGame("Host", gameId);

        MvcResult rejected = postAsPlayer("Latecomer", "/", "/games/join", Map.of(
                "code", gameId,
                "nickname", "Latecomer"));

        assertThat(rejected.getFlashMap().get("error")).isEqualTo("Gra już się zaczęła, nie można dołączyć.");
    }

    // --- HTTP/session plumbing — this is what makes these tests "real browser" realistic ---------

    private String createGame(String nickname, int totalRounds) throws Exception {
        MvcResult result = postAsPlayer(nickname, "/", "/games", Map.of(
                "nickname", nickname,
                "totalRounds", String.valueOf(totalRounds)));
        assertThat(result.getResponse().getStatus()).isEqualTo(302);
        String location = result.getResponse().getHeader("Location");
        return location.substring(location.lastIndexOf('/') + 1);
    }

    private void joinGame(String nickname, String gameId) throws Exception {
        MvcResult result = postAsPlayer(nickname, "/", "/games/join", Map.of(
                "code", gameId,
                "nickname", nickname));
        assertThat(result.getResponse().getStatus()).isEqualTo(302);
    }

    private MvcResult startGame(String nickname, String gameId) throws Exception {
        return postAsPlayer(nickname, "/games/" + gameId, "/games/" + gameId + "/start", Map.of());
    }

    private MvcResult answer(String nickname, String gameId, long cardId) throws Exception {
        return postAsPlayer(nickname, "/games/" + gameId, "/games/" + gameId + "/answers",
                Map.of("cardId", String.valueOf(cardId)));
    }

    private MvcResult rank(String nickname, String gameId, String submissionId, int rank) throws Exception {
        return postAsPlayer(nickname, "/games/" + gameId, "/games/" + gameId + "/rankings",
                Map.of("submissionId", submissionId, "rank", String.valueOf(rank)));
    }

    private MvcResult nextRound(String nickname, String gameId) throws Exception {
        return postAsPlayer(nickname, "/games/" + gameId, "/games/" + gameId + "/next", Map.of());
    }

    /**
     * Posts as {@code nickname}, first GET-ing {@code refererGetPath} to harvest that player's
     * current Spring Session cookie and a matching CSRF token — exactly what a real browser does
     * before submitting a form. Updates the stored session cookie from the response afterwards.
     */
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
        rememberSessionCookie(nickname, result);
        return result;
    }

    private String harvestCsrf(String getPath, String nickname) throws Exception {
        MockHttpServletRequestBuilder request = get(getPath);
        Cookie cookie = sessionCookies.get(nickname);
        if (cookie != null) {
            request.cookie(cookie);
        }

        MvcResult result = mockMvc.perform(request).andReturn();
        rememberSessionCookie(nickname, result);

        String html = result.getResponse().getContentAsString();
        Matcher matcher = CSRF_PATTERN.matcher(html);
        if (!matcher.find()) {
            throw new IllegalStateException("No CSRF token found on " + getPath + " for " + nickname);
        }
        return matcher.group(1);
    }

    private void rememberSessionCookie(String nickname, MvcResult result) {
        Cookie newCookie = result.getResponse().getCookie("SESSION");
        if (newCookie != null) {
            sessionCookies.put(nickname, newCookie);
        }
    }
}
