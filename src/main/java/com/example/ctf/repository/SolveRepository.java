package com.example.ctf.repository;

import com.example.ctf.domain.Solve;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface SolveRepository extends JpaRepository<Solve, String> {

    boolean existsByUserIdAndChallengeId(String userId, String challengeId);

    long countByChallengeId(String challengeId);

    long countByUserId(String userId);

    @Query("select coalesce(sum(s.finalPoints), 0) from Solve s where s.user.id = :userId")
    long sumPointsByUser(@Param("userId") String userId);

    java.util.Optional<Solve> findByUserIdAndChallengeId(String userId, String challengeId);

    List<Solve> findByUserIdOrderBySolvedAtDesc(String userId);

    @Query("select s.challenge.id from Solve s where s.user.id = :userId and s.challenge.event.id = :eventId")
    List<String> findSolvedChallengeIds(@Param("userId") String userId, @Param("eventId") String eventId);

    @Query("select s from Solve s join fetch s.user u left join fetch u.team "
            + "where s.challenge.id = :challengeId order by s.solvedAt asc")
    List<Solve> findTopSolvers(@Param("challengeId") String challengeId, Pageable pageable);

    /** 某赛事/练习区内全部解题记录，供排行榜聚合 */
    @Query("select s from Solve s join fetch s.user u left join fetch u.team "
            + "join fetch s.challenge c join fetch c.event e "
            + "where e.id = :eventId order by s.solvedAt desc")
    List<Solve> findAllForEvent(@Param("eventId") String eventId);

    @Query("select s from Solve s join fetch s.user u left join fetch u.team "
            + "join fetch s.challenge c join fetch c.event e "
            + "where e.id = :eventId order by s.solvedAt desc")
    List<Solve> findRecentForEvent(@Param("eventId") String eventId, Pageable pageable);

    @Query("select s from Solve s join fetch s.challenge c join fetch c.event e "
            + "where s.user.id = :userId order by s.solvedAt desc")
    List<Solve> findAllByUser(@Param("userId") String userId);

    @Query("select coalesce(sum(s.finalPoints), 0) from Solve s "
            + "where s.user.id = :userId and s.challenge.event.id = :eventId")
    long sumPointsInEvent(@Param("userId") String userId, @Param("eventId") String eventId);
}
