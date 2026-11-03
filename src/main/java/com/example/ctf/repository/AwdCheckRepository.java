package com.example.ctf.repository;

import com.example.ctf.domain.AwdCheck;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AwdCheckRepository extends JpaRepository<AwdCheck, String> {

    List<AwdCheck> findByRoundIdOrderByCheckedAtDesc(String roundId);

    List<AwdCheck> findByRoundIdAndRoundNoOrderByCheckedAtDesc(String roundId, int roundNo);

    boolean existsByRoundIdAndRoundNoAndTeamIdAndOkFalse(String roundId, int roundNo, String teamId);
}
