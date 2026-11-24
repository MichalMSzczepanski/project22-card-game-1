package io.github.michalmszczepanski.project22_card_game_1.security;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-IP rate limit on the two actions that matter most against abuse: creating games
 * (POST /games) and brute-forcing 5-character join codes (POST /games/join). Everything
 * else — polling GET requests, in-game actions on /games/{id}/** — is untouched, so the
 * ~4 req/s/game of normal polling (DEPLOY.md § 3) is never affected.
 *
 * In-memory, one {@link Bucket} per IP, not Redis/distributed — a single app instance on
 * one VPS doesn't need a shared counter (CLAUDE.md "żadnych usług pay-per-use"). The IP map
 * is never evicted, so it grows for as long as the process runs — a known, accepted
 * trade-off at this scale, not an oversight; revisit if it ever shows up in practice.
 */
@Component
@RequiredArgsConstructor
public class RateLimitFilter extends OncePerRequestFilter {

    private static final Set<String> LIMITED_PATHS = Set.of("/games", "/games/join");

    private final RateLimitProperties properties;
    private final ConcurrentHashMap<String, Bucket> buckets = new ConcurrentHashMap<>();

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        if ("POST".equals(request.getMethod()) && LIMITED_PATHS.contains(request.getRequestURI())) {
            Bucket bucket = buckets.computeIfAbsent(request.getRemoteAddr(), ip -> newBucket());
            if (!bucket.tryConsume(1)) {
                response.setStatus(429); // HttpServletResponse has no SC_TOO_MANY_REQUESTS constant
                response.setContentType("text/plain;charset=UTF-8");
                response.getWriter().write("Za dużo prób — spróbuj ponownie za chwilę.");
                return;
            }
        }
        filterChain.doFilter(request, response);
    }

    private Bucket newBucket() {
        Bandwidth limit = Bandwidth.builder()
                .capacity(properties.capacity())
                .refillGreedy(properties.refillTokens(), Duration.ofSeconds(properties.refillPeriodSeconds()))
                .build();
        return Bucket.builder().addLimit(limit).build();
    }
}
