package io.github.michalmszczepanski.project22_card_game_1.game;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;

/**
 * The leader's placement of one submission: rank 1 is the best answer, rank N
 * (the number of submissions in the round) is the worst.
 */
@Entity
public class Ranking {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "round_id")
    private Round round;

    @ManyToOne
    @JoinColumn(name = "submission_id")
    private Submission submission;

    private int rank;

    protected Ranking() {
    }

    public Ranking(Submission submission, int rank) {
        this.submission = submission;
        this.rank = rank;
    }

    public Long getId() {
        return id;
    }

    public Round getRound() {
        return round;
    }

    public void setRound(Round round) {
        this.round = round;
    }

    public Submission getSubmission() {
        return submission;
    }

    public int getRank() {
        return rank;
    }
}
