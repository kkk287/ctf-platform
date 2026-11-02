package com.example.ctf.repository;

import com.example.ctf.domain.Hint;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface HintRepository extends JpaRepository<Hint, String> {

    List<Hint> findByChallengeIdOrderByCostAsc(String challengeId);

    void deleteByChallengeId(String challengeId);
}
