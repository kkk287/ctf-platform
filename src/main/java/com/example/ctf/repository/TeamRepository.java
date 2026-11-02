package com.example.ctf.repository;

import com.example.ctf.domain.Team;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface TeamRepository extends JpaRepository<Team, String> {

    Optional<Team> findByInviteCode(String inviteCode);

    boolean existsByName(String name);

    boolean existsByInviteCode(String inviteCode);
}
