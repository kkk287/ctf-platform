package com.example.ctf.repository;

import com.example.ctf.domain.Submission;
import com.example.ctf.domain.SubmissionResult;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface SubmissionRepository extends JpaRepository<Submission, String>,
        JpaSpecificationExecutor<Submission> {

    long countByUserIdAndChallengeIdAndResultAndCreatedAtAfter(
            String userId, String challengeId, SubmissionResult result, LocalDateTime after);

    Optional<Submission> findFirstByUserIdAndChallengeIdAndResultOrderByCreatedAtDesc(
            String userId, String challengeId, SubmissionResult result);

    @Query("select s.challenge, s.ip, count(distinct s.user.id) from Submission s "
            + "where s.result = :result group by s.challenge, s.ip "
            + "having count(distinct s.user.id) >= 2 order by count(distinct s.user.id) desc")
    List<Object[]> findSharedIpGroups(@Param("result") SubmissionResult result);

    List<Submission> findByChallengeIdAndIpAndResultOrderByCreatedAtAsc(
            String challengeId, String ip, SubmissionResult result);
}
