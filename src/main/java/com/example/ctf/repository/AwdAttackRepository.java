package com.example.ctf.repository;

import com.example.ctf.domain.AwdAttack;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AwdAttackRepository extends JpaRepository<AwdAttack, String> {

    boolean existsByRoundIdAndFlag(String roundId, String flag);

    List<AwdAttack> findByRoundIdOrderByCreatedAtDesc(String roundId);

    List<AwdAttack> findByRoundIdAndRoundNoOrderByCreatedAtDesc(String roundId, int roundNo);

    long countByRoundIdAndAttackerTeamId(String roundId, String attackerTeamId);
}
