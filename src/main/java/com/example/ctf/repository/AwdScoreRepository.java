package com.example.ctf.repository;

import com.example.ctf.domain.AwdScore;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AwdScoreRepository extends JpaRepository<AwdScore, String> {

    List<AwdScore> findByRoundIdOrderByRoundNoAsc(String roundId);

    List<AwdScore> findByRoundIdAndRoundNo(String roundId, int roundNo);

    Optional<AwdScore> findByRoundIdAndRoundNoAndTeamId(String roundId, int roundNo, String teamId);
}
