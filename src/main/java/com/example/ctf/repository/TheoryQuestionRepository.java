package com.example.ctf.repository;

import com.example.ctf.domain.QuestionKind;
import com.example.ctf.domain.TheoryQuestion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TheoryQuestionRepository extends JpaRepository<TheoryQuestion, String> {

    List<TheoryQuestion> findByEventIdOrderByCreatedAtAsc(String eventId);

    List<TheoryQuestion> findByEventIdAndKind(String eventId, QuestionKind kind);

    long countByEventId(String eventId);
}
