package com.example.ctf.repository;

import com.example.ctf.domain.AwdTeam;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AwdTeamRepository extends JpaRepository<AwdTeam, String> {

    List<AwdTeam> findByRoundIdOrderByNameAsc(String roundId);

    Optional<AwdTeam> findByRoundIdAndName(String roundId, String name);

    long countByRoundId(String roundId);
}
