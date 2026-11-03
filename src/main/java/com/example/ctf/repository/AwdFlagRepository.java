package com.example.ctf.repository;

import com.example.ctf.domain.AwdFlag;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AwdFlagRepository extends JpaRepository<AwdFlag, String> {

    Optional<AwdFlag> findByRoundIdAndRoundNoAndFlag(String roundId, int roundNo, String flag);

    Optional<AwdFlag> findByRoundIdAndRoundNoAndTeamId(String roundId, int roundNo, String teamId);

    List<AwdFlag> findByRoundIdAndRoundNoOrderByTeamIdAsc(String roundId, int roundNo);

    boolean existsByFlag(String flag);
}
