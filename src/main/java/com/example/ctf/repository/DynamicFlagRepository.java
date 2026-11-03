package com.example.ctf.repository;

import com.example.ctf.domain.DynamicFlag;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface DynamicFlagRepository extends JpaRepository<DynamicFlag, String> {
    Optional<DynamicFlag> findByInstanceId(String instanceId);

    Optional<DynamicFlag> findFirstByOwnerIdAndChallengeIdOrderByCreatedAtDesc(
            String ownerId, String challengeId);
}
