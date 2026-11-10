package io.github.michalmszczepanski.project22_card_game_1.game;

import io.github.michalmszczepanski.project22_card_game_1.card.Card;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Round {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Getter
    private Long id;

    @Getter
    private int number;

    @ManyToOne
    @JoinColumn(name = "leader_id")
    @Getter
    private Player leader;

    @ManyToOne
    @JoinColumn(name = "question_card_id")
    @Getter
    private Card questionCard;

    /** When the CURRENT phase (answering or guessing) started — reset on each phase change, drives the round timer. */
    @Getter
    @Setter
    private Instant phaseStartedAt = Instant.now();

    // Small, bounded aggregate (max 5 players) — eager fetch avoids LazyInitializationException
    // once the request's transaction has closed (we deliberately run with open-in-view=false).
    @OneToMany(mappedBy = "round", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @Getter
    private final List<Submission> submissions = new ArrayList<>();

    @OneToMany(mappedBy = "round", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @Getter
    private final List<Ranking> rankings = new ArrayList<>();

    public Round(int number, Player leader, Card questionCard) {
        this.number = number;
        this.leader = leader;
        this.questionCard = questionCard;
    }

    public void restartPhaseTimer() {
        this.phaseStartedAt = Instant.now();
    }

    public void addSubmission(Submission submission) {
        submission.setRound(this);
        submissions.add(submission);
    }

    public void addRanking(Ranking ranking) {
        ranking.setRound(this);
        rankings.add(ranking);
    }

    public boolean hasSubmitted(Player player) {
        return submissions.stream().anyMatch(s -> s.getAuthor().getId().equals(player.getId()));
    }

    public Optional<Submission> findSubmission(String submissionId) {
        return submissions.stream().filter(s -> s.getId().equals(submissionId)).findFirst();
    }

    public boolean hasRanked(Submission submission) {
        return rankings.stream().anyMatch(r -> r.getSubmission().getId().equals(submission.getId()));
    }

    /** Ranks (1..N) already given to some submission this round, so the leader can't reuse one. */
    public Set<Integer> usedRanks() {
        return rankings.stream().map(Ranking::getRank).collect(Collectors.toSet());
    }

    /** Submissions still waiting for the leader to place them. */
    public List<Submission> unrankedSubmissions() {
        return submissions.stream().filter(s -> !hasRanked(s)).toList();
    }

    /** How many podium places exist this round — {@code configuredPodiumSize}, or fewer if there aren't that many submissions to rank. */
    public int podiumSize(int configuredPodiumSize) {
        return Math.min(configuredPodiumSize, submissions.size());
    }

    /** Points for podium place {@code rank} (1 = best). Only valid for {@code rank} in {@code 1..podiumSize()}. */
    public static int pointsForRank(int rank, List<Integer> podiumPoints) {
        return podiumPoints.get(rank - 1);
    }

    public boolean hasFinishedRanking(int configuredPodiumSize) {
        return rankings.size() == podiumSize(configuredPodiumSize);
    }
}
