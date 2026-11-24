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
        assertThat(rejected.getFlashMap().get("error")).isEqualTo("Tylko admin może to zrobić.");
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
    void joiningAFinishedGameIsRejected() throws Exception {
        String gameId = createGame("Host", 1);
        joinGame("Guest1", gameId);
        joinGame("Guest2", gameId);
        startGame("Host", gameId);

        Game game = gameRepository.findById(gameId).orElseThrow();
        for (Player answerer : game.answerersInOrder()) {
            answer(answerer.getNickname(), gameId, answerer.getHand().get(0).getId());
        }
        game = gameRepository.findById(gameId).orElseThrow();
        int rankToAssign = 1;
        for (Submission submission : List.copyOf(game.getCurrentRound().getSubmissions())) {
            rank(game.getCurrentRound().getLeader().getNickname(), gameId, submission.getId(), rankToAssign++);
        }
        nextRound(game.getCurrentRound().getLeader().getNickname(), gameId);
        assertThat(gameRepository.findById(gameId).orElseThrow().getStatus()).isEqualTo(GameStatus.FINISHED);

        MvcResult rejected = postAsPlayer("Latecomer", "/", "/games/join", Map.of(
                "code", gameId,
                "nickname", "Latecomer"));

        assertThat(rejected.getFlashMap().get("error")).isEqualTo("Gra już się zakończyła, nie można dołączyć.");
    }

    @Test
    @Transactional
    void joiningMidGameSitsOutTheCurrentRoundThenJoinsProperlyNextRound() throws Exception {
        String gameId = createGame("Host", 10);
        joinGame("Guest1", gameId);
        joinGame("Guest2", gameId);
        startGame("Host", gameId);

        joinGame("Latecomer", gameId);
        Game game = gameRepository.findById(gameId).orElseThrow();
        Player latecomer = game.findPlayer(game.getPlayers().get(game.getPlayers().size() - 1).getId()).orElseThrow();
        assertThat(latecomer.getNickname()).isEqualTo("Latecomer");
        assertThat(latecomer.getHand()).isEmpty();
        assertThat(game.answerersInOrder()).noneMatch(p -> p.getNickname().equals("Latecomer"));

        String latecomerView = getAsPlayer("Latecomer", "/games/" + gameId).getResponse().getContentAsString();
        assertThat(latecomerView).contains("Dołączyłeś w trakcie tej rundy");

        for (Player answerer : game.answerersInOrder()) {
            answer(answerer.getNickname(), gameId, answerer.getHand().get(0).getId());
        }
        game = gameRepository.findById(gameId).orElseThrow();
        int rankToAssign = 1;
        for (Submission submission : List.copyOf(game.getCurrentRound().getSubmissions())) {
            rank(game.getCurrentRound().getLeader().getNickname(), gameId, submission.getId(), rankToAssign++);
        }
        nextRound(game.getCurrentRound().getLeader().getNickname(), gameId);

        game = gameRepository.findById(gameId).orElseThrow();
        assertThat(game.getCurrentRound().getNumber()).isEqualTo(2);
        assertThat(latecomer.getHand()).hasSize(7);
        assertThat(game.answerersInOrder()).anyMatch(p -> p.getNickname().equals("Latecomer"));
    }

    // --- Admin role (README § 5) — web-layer behavior GameServiceTest can't see: rendered HTML,
    //     and the kicked player's own next poll getting redirected home. ----------------------

    @Test
    @Transactional
    void adminOnlyButtonsAreHiddenFromNonAdminPlayers() throws Exception {
        String gameId = createGame("Host", 10);
        joinGame("Guest1", gameId);
        joinGame("Guest2", gameId);

        String adminView = getAsPlayer("Host", "/games/" + gameId).getResponse().getContentAsString();
        assertThat(adminView).contains("/games/" + gameId + "/end");
        assertThat(adminView).contains("/games/" + gameId + "/family-mode");
        assertThat(adminView).contains("/games/" + gameId + "/kick");
        assertThat(adminView).contains("/games/" + gameId + "/transfer-admin");

        String guestView = getAsPlayer("Guest1", "/games/" + gameId).getResponse().getContentAsString();
        assertThat(guestView).doesNotContain("/games/" + gameId + "/end");
        assertThat(guestView).doesNotContain("/games/" + gameId + "/family-mode");
        assertThat(guestView).doesNotContain("/games/" + gameId + "/kick");
        assertThat(guestView).doesNotContain("/games/" + gameId + "/transfer-admin");
    }

    @Test
    @Transactional
    void adminCanTransferTheRoleToAnotherPlayerWithoutLeaving() throws Exception {
        String gameId = createGame("Host", 10);
        joinGame("Guest1", gameId);
        joinGame("Guest2", gameId);

        MvcResult transferred = postAsPlayer("Host", "/games/" + gameId, "/games/" + gameId + "/transfer-admin",
                Map.of("targetPlayerId", playerIdOf("Guest1", gameId)));
        assertThat(transferred.getResponse().getStatus()).isEqualTo(302);

        String newAdminView = getAsPlayer("Guest1", "/games/" + gameId).getResponse().getContentAsString();
        assertThat(newAdminView).contains("/games/" + gameId + "/end");

        String formerAdminView = getAsPlayer("Host", "/games/" + gameId).getResponse().getContentAsString();
        assertThat(formerAdminView).doesNotContain("/games/" + gameId + "/end");
    }

    @Test
    @Transactional
    void kickedPlayerIsRedirectedHomeWithMessageOnNextPoll() throws Exception {
        String gameId = createGame("Host", 10);
        joinGame("Guest1", gameId);
        joinGame("Guest2", gameId);

        MvcResult kicked = postAsPlayer("Host", "/games/" + gameId, "/games/" + gameId + "/kick",
                Map.of("targetPlayerId", playerIdOf("Guest1", gameId)));
        assertThat(kicked.getResponse().getStatus()).isEqualTo(302);

        MvcResult guest1NextPoll = getAsPlayer("Guest1", "/games/" + gameId);
        assertThat(guest1NextPoll.getResponse().getStatus()).isEqualTo(302);
        assertThat(guest1NextPoll.getResponse().getRedirectedUrl()).isEqualTo("/");
        assertThat(guest1NextPoll.getFlashMap().get("info")).isEqualTo("Admin usunął Cię z gry.");

        String guest2View = getAsPlayer("Guest2", "/games/" + gameId).getResponse().getContentAsString();
        assertThat(guest2View).contains("został(a) usunięty(a) z gry");
    }

    @Test
    @Transactional
    void noAdminBannerAndTakeOverButtonAppearAfterAdminLeaves() throws Exception {
        String gameId = createGame("Host", 10);
        joinGame("Guest1", gameId);
        joinGame("Guest2", gameId);

        postAsPlayer("Host", "/games/" + gameId, "/games/" + gameId + "/leave", Map.of());

        String guest1View = getAsPlayer("Guest1", "/games/" + gameId).getResponse().getContentAsString();
        assertThat(guest1View).contains("Przejmij admina");
        assertThat(guest1View).contains("/games/" + gameId + "/take-over-admin");

        postAsPlayer("Guest1", "/games/" + gameId, "/games/" + gameId + "/take-over-admin", Map.of());
        String afterTakeOver = getAsPlayer("Guest1", "/games/" + gameId).getResponse().getContentAsString();
        assertThat(afterTakeOver).doesNotContain("Przejmij admina");
        assertThat(afterTakeOver).contains("/games/" + gameId + "/end"); // Guest1 now sees admin-only buttons
    }

    @Test
    @Transactional
    void adminCanSkipAStuckRoundOverRealHttp() throws Exception {
        String gameId = createGame("Host", 10);
        joinGame("Guest1", gameId);
        joinGame("Guest2", gameId);
        startGame("Host", gameId);

        String adminView = getAsPlayer("Host", "/games/" + gameId).getResponse().getContentAsString();
        assertThat(adminView).contains("/games/" + gameId + "/skip-round");

        MvcResult skipped = postAsPlayer("Host", "/games/" + gameId, "/games/" + gameId + "/skip-round", Map.of());
        assertThat(skipped.getResponse().getStatus()).isEqualTo(302);
        assertThat(gameRepository.findById(gameId).orElseThrow().getStatus()).isEqualTo(GameStatus.REVEALED);
    }

    @Test
    @Transactional
    void playerCanSubmitABlankCardOverRealHttp() throws Exception {
        String gameId = createGame("Host", 10);
        joinGame("Guest1", gameId);
        joinGame("Guest2", gameId);
        startGame("Host", gameId);

        Game game = gameRepository.findById(gameId).orElseThrow();
        String answererId = game.answerersInOrder().get(0).getId();
        String answererNickname = game.answerersInOrder().get(0).getNickname();

        MvcResult submitted = postAsPlayer(answererNickname, "/games/" + gameId,
                "/games/" + gameId + "/blank-answer", Map.of("text", "własna odpowiedź"));
        assertThat(submitted.getResponse().getStatus()).isEqualTo(302);

        Player answerer = game.findPlayer(answererId).orElseThrow();
        assertThat(answerer.isUsedBlankCard()).isTrue();
    }

    @Test
    @Transactional
    void playerCanSwapTheirHandOverRealHttp() throws Exception {
        String gameId = createGame("Host", 10);
        joinGame("Guest1", gameId);
        joinGame("Guest2", gameId);
        startGame("Host", gameId);

        Game game = gameRepository.findById(gameId).orElseThrow();
        String answererId = game.answerersInOrder().get(0).getId();
        String answererNickname = game.answerersInOrder().get(0).getNickname();

        MvcResult swapped = postAsPlayer(answererNickname, "/games/" + gameId,
                "/games/" + gameId + "/swap-hand", Map.of());
        assertThat(swapped.getResponse().getStatus()).isEqualTo(302);

        Player answerer = game.findPlayer(answererId).orElseThrow();
        assertThat(answerer.isUsedHandSwap()).isTrue();
    }

    @Test
    @Transactional
    void adminCanRematchAndEveryoneElsesNextPollLandsThemInTheNewGameOverRealHttp() throws Exception {
        String gameId = createGame("Host", 1);
        joinGame("Guest1", gameId);
        joinGame("Guest2", gameId);
        startGame("Host", gameId);
        postAsPlayer("Host", "/games/" + gameId, "/games/" + gameId + "/skip-round", Map.of());
        nextRound("Host", gameId); // totalRounds=1, so this is the one that finishes the game

        assertThat(gameRepository.findById(gameId).orElseThrow().getStatus()).isEqualTo(GameStatus.FINISHED);

        MvcResult rematch = postAsPlayer("Host", "/games/" + gameId, "/games/" + gameId + "/rematch", Map.of());
        assertThat(rematch.getResponse().getStatus()).isEqualTo(302);
        String newGameId = rematch.getResponse().getRedirectedUrl().replace("/games/", "");
        assertThat(newGameId).isNotEqualTo(gameId);

        // Guest1 never clicked anything — their own browser's next poll of the old (finished) game
        // picks up the rematch and redirects them straight into the new one.
        MvcResult guestFollowUp = getAsPlayer("Guest1", "/games/" + gameId);
        assertThat(guestFollowUp.getResponse().getStatus()).isEqualTo(302);
        assertThat(guestFollowUp.getResponse().getRedirectedUrl()).isEqualTo("/games/" + newGameId);

        Game newGame = gameRepository.findById(newGameId).orElseThrow();
        assertThat(newGame.getPlayers()).extracting(Player::getNickname)
                .containsExactlyInAnyOrder("Host", "Guest1", "Guest2");
    }

    @Test
    @Transactional
    void tvLobbyEmbedsAJoinQrCodeThatRendersAsAPng() throws Exception {
        String gameId = createGame("Host", 10);

        String tv = mockMvc.perform(get("/games/" + gameId + "/tv")).andReturn().getResponse().getContentAsString();
        assertThat(tv).contains("/games/" + gameId + "/qr.png");

        MvcResult qr = mockMvc.perform(get("/games/" + gameId + "/qr.png")).andReturn();
        assertThat(qr.getResponse().getStatus()).isEqualTo(200);
        assertThat(qr.getResponse().getContentType()).isEqualTo("image/png");
        byte[] png = qr.getResponse().getContentAsByteArray();
        assertThat(png[0]).isEqualTo((byte) 0x89);
        assertThat(png[1]).isEqualTo((byte) 'P');
        assertThat(png[2]).isEqualTo((byte) 'N');
        assertThat(png[3]).isEqualTo((byte) 'G');
    }

    /** Finds a player's id by nickname straight from the DB — the test only knows nicknames, not ids. */
    private String playerIdOf(String nickname, String gameId) {
        return gameRepository.findById(gameId).orElseThrow().getPlayers().stream()
                .filter(p -> p.getNickname().equals(nickname))
                .findFirst().orElseThrow().getId();
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

    /** GETs {@code path} carrying {@code nickname}'s stored session cookie, same as a browser poll. */
    private MvcResult getAsPlayer(String nickname, String path) throws Exception {
        MockHttpServletRequestBuilder request = get(path);
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
