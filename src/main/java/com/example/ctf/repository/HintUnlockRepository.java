package com.example.ctf.repository;

import com.example.ctf.domain.HintUnlock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface HintUnlockRepository extends JpaRepository<HintUnlock, String> {

    boolean existsByUserIdAndHintId(String userId, String hintId);

    List<HintUnlock> findByUserIdAndChallengeId(String userId, String challengeId);

    @Query("select coalesce(sum(h.cost), 0) from HintUnlock h where h.user.id = :userId and h.challenge.id = :challengeId")
    int sumCostByUserAndChallenge(@Param("userId") String userId, @Param("challengeId") String challengeId);
}
