package com.example.ctf.repository;

import com.example.ctf.domain.Writeup;
import com.example.ctf.domain.WriteupStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface WriteupRepository extends JpaRepository<Writeup, String> {

    Optional<Writeup> findByEventIdAndUserIdAndChallengeId(String eventId, String userId, String challengeId);

    @Query("select w from Writeup w join fetch w.user u join fetch w.challenge c "
            + "join fetch c.event e where w.event.id = :eventId and w.challenge.id = :challengeId "
            + "and (w.status = :publicStatus or w.user.id = :userId) "
            + "order by w.createdAt desc")
    List<Writeup> findVisibleForChallenge(@Param("eventId") String eventId,
                                          @Param("challengeId") String challengeId,
                                          @Param("userId") String userId,
                                          @Param("publicStatus") WriteupStatus publicStatus);

    /** 赛后列表：event 维度所有已公开 writeup（SUBMITTED / ACCEPTED） */
    @Query("select w from Writeup w join fetch w.user u join fetch w.challenge c "
            + "join fetch c.event e where w.event.id = :eventId "
            + "and w.status in (:publicStatuses) order by w.createdAt desc")
    List<Writeup> findPublicByEvent(@Param("eventId") String eventId,
                                    @Param("publicStatuses") List<WriteupStatus> publicStatuses);

    /** 某用户在赛事下的全部 writeup（含自己的草稿） */
    @Query("select w from Writeup w join fetch w.user u join fetch w.challenge c "
            + "where w.event.id = :eventId and w.user.id = :userId order by w.createdAt desc")
    List<Writeup> findByEventIdAndUserIdOrderByCreatedAtDesc(@Param("eventId") String eventId,
                                                              @Param("userId") String userId);

    @Query("select w from Writeup w join fetch w.user u join fetch w.challenge c "
            + "join fetch c.event e where w.event.id = :eventId order by w.createdAt desc")
    List<Writeup> findAllForEvent(@Param("eventId") String eventId);
}
