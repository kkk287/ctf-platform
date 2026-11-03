package com.example.ctf.repository;

import com.example.ctf.domain.AwdRound;
import com.example.ctf.domain.AwdStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface AwdRoundRepository extends JpaRepository<AwdRound, String> {

    List<AwdRound> findByEventIdOrderByCreatedAtDesc(String eventId);

    List<AwdRound> findByStatusInOrderByCreatedAtDesc(Collection<AwdStatus> statuses);

    List<AwdRound> findAllByOrderByCreatedAtDesc();
}
