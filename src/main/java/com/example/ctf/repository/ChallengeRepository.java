package com.example.ctf.repository;

import com.example.ctf.domain.Challenge;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ChallengeRepository extends JpaRepository<Challenge, String> {

    List<Challenge> findByEventIdAndVisibleTrueOrderByCategoryAscNameAsc(String eventId);

    List<Challenge> findByEventIdOrderByCategoryAscNameAsc(String eventId);

    long countByEventId(String eventId);

    long countByEventIdAndVisibleTrue(String eventId);

    boolean existsByEventId(String eventId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Challenge c where c.id = :id")
    Optional<Challenge> findForUpdate(@Param("id") String id);
}
