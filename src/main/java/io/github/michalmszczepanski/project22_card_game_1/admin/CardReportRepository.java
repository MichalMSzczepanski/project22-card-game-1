package io.github.michalmszczepanski.project22_card_game_1.admin;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CardReportRepository extends JpaRepository<CardReport, Long> {

    List<CardReport> findAllByOrderByReportedAtAsc();

    void deleteByCard_Id(Long cardId);
}
