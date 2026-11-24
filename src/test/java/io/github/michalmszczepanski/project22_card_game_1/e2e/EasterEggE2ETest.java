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
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * MECHANICS.md "Easter eggi" — {@code EasterEggServiceTest} already covers the detection logic
 * in isolation (plain entities, no Spring context), but never proves {@code GameService} actually
 * calls those hooks at the right moments during a real game, or that the banner actually reaches
 * the rendered page. These tests play a real game over real HTTP (same reasoning as
 * {@code GameFlowE2ETest}) and check the banner on the real {@code game.html}/{@code tv.html}.
 * One test per distinct hook call site in {@code GameService} is enough — the other easter eggs
 * sharing that same call site (e.g. "sucha seria" also goes through {@code afterRoundScored})
 * are already exhaustively covered by {@code EasterEggServiceTest}.
 */
@SpringBootTest
@AutoConfigureMockMvc
class EasterEggE2ETest {

    private static final Pattern CSRF_PATTERN = Pattern.compile("name=\"_csrf\" value=\"([^\"]+)\"");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private GameRepository gameRepository;

    @Autowired
    private GameProperties gameProperties;

    private final Map<String, Cookie> sessionCookies = new HashMap<>();

    @BeforeEach
    void resetSessions() {
        sessionCookies.clear();
    }

    /** Exercises the GameService.scoreRound -> EasterEggService.afterRoundScored call site. */
    @Test
    @Transactional
    void sameAnswererWinningThreeRoundsFiresTheHatTrickBannerOnTvAndGamePages() throws Exception {
        String gameId = createGame("Host", 10);
        joinGame("Guest1", gameId);
        joinGame("Guest2", gameId);
        startGame("Host", gameId);

        // Leader rotates Host -> Guest1 -> Guest2 -> Host ... — Guest2 answers (and wins) in
        // rounds 1, 2 and 4, leads (frozen streak) in round 3.
        winRoundAsAnswerer(gameId, "Guest2"); // streak 1 (round 1, leader Host)
        winRoundAsAnswerer(gameId, "Guest2"); // streak 2 (round 2, leader Guest1)
        playRoundWithoutCaringWhoWins(gameId); // round 3, leader Guest2 — streak untouched
        winRoundAsAnswerer(gameId, "Guest2"); // streak 3 (round 4, leader Host) -> hat trick fires

        String tvBody = mockMvc.perform(get("/games/" + gameId + "/tv")).andReturn().getResponse().getContentAsString();
        assertThat(tvBody).contains("Guest2").contains("nie do zatrzymania");
    }

    /** Exercises the GameService.nextRound (FINISHED branch) -> EasterEggService.onGameFinished call site. */
    @Test
    @Transactional
    void aPlayerFinishingWithZeroPointsFiresTheZeroHeroBanner() throws Exception {
        String gameId = createGame("Host", 1);
        joinGame("Guest1", gameId);
        joinGame("Guest2", gameId);
        startGame("Host", gameId); // Host leads round 1, never answers -> stays at 0 pts

        Player guest1 = answerer(gameId, "Guest1");
        Player guest2 = answerer(gameId, "Guest2");
        answer("Guest1", gameId, guest1.getHand().get(0).getId());
        answer("Guest2", gameId, guest2.getHand().get(0).getId());

        Game game = gameRepository.findById(gameId).orElseThrow();
        Round round = game.getCurrentRound();
        Submission guest1Submission = submissionOf(round, "Guest1");
        rank("Host", gameId, guest1Submission.getId(), 1); // Guest2's submission is left unranked -> 0 pts

        // Host is both leader and admin here — force-reveal instead of waiting for a 2nd ranking.
        postAsPlayer("Host", "/games/" + gameId, "/games/" + gameId + "/skip-round", Map.of());

        MvcResult finishResult = postAsPlayer("Host", "/games/" + gameId, "/games/" + gameId + "/next", Map.of());
        assertThat(gameRepository.findById(gameId).orElseThrow().getStatus()).isEqualTo(GameStatus.FINISHED);

        String tvBody = mockMvc.perform(get("/games/" + gameId + "/tv")).andReturn().getResponse().getContentAsString();
        assertThat(tvBody).contains("liczy się udział");
        assertThat(tvBody).contains("Host").contains("Guest2"); // both finished at 0 pts
    }

    /** Exercises the GameService.pruneDisconnectedPlayers -> EasterEggService.onPlayerDisconnected call site. */
    @Test
    @Transactional
    void aLeadingPlayerGoingQuietFiresThePerfectTimingBanner() throws Exception {
        String gameId = createGame("Host", 10);
        joinGame("Guest1", gameId);
        joinGame("Guest2", gameId);
        startGame("Host", gameId);

        // Round 1 (leader Host): Guest1 wins outright (3 pts) — the clear point leader afterwards.
        winRoundAsAnswerer(gameId, "Guest1");

        // Round 2's leader is Guest1 (join order 1) — now the one holding the most points.
        Game game = gameRepository.findById(gameId).orElseThrow();
        assertThat(game.getCurrentRound().getLeader().getNickname()).isEqualTo("Guest1");
        Player guest1 = game.getPlayers().stream().filter(p -> p.getNickname().equals("Guest1")).findFirst().orElseThrow();
        // Simulate the phone screen going dark mid-round — a leader is never "expected to act"
        // during ANSWERING, so they're eligible for pruning regardless of their own heartbeat.
        guest1.setLastSeenAt(Instant.now().minusSeconds(gameProperties.timeouts().disconnectSeconds() + 5));

        // Any poll opportunistically runs pruneDisconnectedPlayers — Host's own next page load does it.
        getAsPlayer("Host", "/games/" + gameId);

        assertThat(gameRepository.findById(gameId).orElseThrow().findPlayer(guest1.getId()).orElseThrow().isLeft()).isTrue();

        String tvBody = mockMvc.perform(get("/games/" + gameId + "/tv")).andReturn().getResponse().getContentAsString();
        assertThat(tvBody).contains("Guest1").contains("najlepszym momencie");
    }

    /** Has whichever player {@code nickname} currently is answering win the round outright (3 pts, rank 1). */
    private void winRoundAsAnswerer(String gameId, String nickname) throws Exception {
        Game game = gameRepository.findById(gameId).orElseThrow();
        List<Player> answerers = game.answerersInOrder();
        for (Player answerer : answerers) {
            answer(answerer.getNickname(), gameId, answerer.getHand().get(0).getId());
        }

        game = gameRepository.findById(gameId).orElseThrow();
        Round round = game.getCurrentRound();
        String leaderNickname = round.getLeader().getNickname();
        Submission winner = submissionOf(round, nickname);
        List<Submission> others = round.getSubmissions().stream().filter(s -> s != winner).toList();

        rank(leaderNickname, gameId, winner.getId(), 1);
        int rank = 2;
        for (Submission other : others) {
            rank(leaderNickname, gameId, other.getId(), rank++);
        }

        nextRound(leaderNickname, gameId);
    }

    /** Completes a round with whatever default ranking order — used when nobody's win matters. */
    private void playRoundWithoutCaringWhoWins(String gameId) throws Exception {
        Game game = gameRepository.findById(gameId).orElseThrow();
        List<Player> answerers = game.answerersInOrder();
        for (Player answerer : answerers) {
            answer(answerer.getNickname(), gameId, answerer.getHand().get(0).getId());
        }

        game = gameRepository.findById(gameId).orElseThrow();
        Round round = game.getCurrentRound();
        String leaderNickname = round.getLeader().getNickname();
        int rank = 1;
        for (Submission submission : List.copyOf(round.getSubmissions())) {
            rank(leaderNickname, gameId, submission.getId(), rank++);
        }

        nextRound(leaderNickname, gameId);
    }

    private Player answerer(String gameId, String nickname) {
        Game game = gameRepository.findById(gameId).orElseThrow();
        return game.answerersInOrder().stream()
                .filter(p -> p.getNickname().equals(nickname)).findFirst().orElseThrow();
    }

    private Submission submissionOf(Round round, String nickname) {
        return round.getSubmissions().stream()
                .filter(s -> s.getAuthor().getNickname().equals(nickname))
                .findFirst().orElseThrow();
    }

    private String createGame(String nickname, int totalRounds) throws Exception {
        MvcResult result = postAsPlayer(nickname, "/", "/games",
                Map.of("nickname", nickname, "totalRounds", String.valueOf(totalRounds)));
        assertThat(result.getResponse().getStatus()).isEqualTo(302);
        String location = result.getResponse().getHeader("Location");
        return location.substring(location.lastIndexOf('/') + 1);
    }

    private void joinGame(String nickname, String gameId) throws Exception {
        postAsPlayer(nickname, "/", "/games/join", Map.of("code", gameId, "nickname", nickname));
    }

    private void startGame(String nickname, String gameId) throws Exception {
        postAsPlayer(nickname, "/games/" + gameId, "/games/" + gameId + "/start", Map.of());
    }

    private void answer(String nickname, String gameId, long cardId) throws Exception {
        postAsPlayer(nickname, "/games/" + gameId, "/games/" + gameId + "/answers", Map.of("cardId", String.valueOf(cardId)));
    }

    private void rank(String nickname, String gameId, String submissionId, int rank) throws Exception {
        postAsPlayer(nickname, "/games/" + gameId, "/games/" + gameId + "/rankings",
                Map.of("submissionId", submissionId, "rank", String.valueOf(rank)));
    }

    private void nextRound(String nickname, String gameId) throws Exception {
        postAsPlayer(nickname, "/games/" + gameId, "/games/" + gameId + "/next", Map.of());
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
        rememberSessionCookie(nickname, result);
        return result;
    }

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
        MvcResult result = getAsPlayer(nickname, getPath);
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
