package io.github.michalmszczepanski.project22_card_game_1.e2e;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Own rate-limit override (separate Spring context from the other e2e tests, which run
 * with a deliberately huge limit — see test {@code application.properties}) so the cap
 * can be hit with just 3 requests instead of the real 10/minute.
 */
@SpringBootTest(properties = {
        "rate-limit.capacity=3",
        "rate-limit.refill-tokens=3",
        "rate-limit.refill-period-seconds=60"
})
@AutoConfigureMockMvc
class RateLimitFilterTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void repeatedJoinAttemptsFromTheSameIpAreEventuallyRateLimited() throws Exception {
        for (int i = 0; i < 3; i++) {
            mockMvc.perform(post("/games/join").with(csrf())
                            .param("code", "ZZZZZ")
                            .param("nickname", "Flood"))
                    .andExpect(status().is3xxRedirection());
        }

        mockMvc.perform(post("/games/join").with(csrf())
                        .param("code", "ZZZZZ")
                        .param("nickname", "Flood"))
                .andExpect(status().isTooManyRequests());
    }

    @Test
    void pollingTheHomePageIsNeverRateLimitedEvenWellPastTheCap() throws Exception {
        for (int i = 0; i < 10; i++) {
            mockMvc.perform(get("/")).andExpect(status().isOk());
        }
    }
}
