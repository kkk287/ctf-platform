package com.example.ctf.repository;

import com.example.ctf.domain.TheoryAnswer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface TheoryAnswerRepository extends JpaRepository<TheoryAnswer, String> {

    @Query("select a from TheoryAnswer a join fetch a.question "
            + "where a.event.id = :eventId and a.user.id = :userId order by a.createdAt asc")
    List<TheoryAnswer> findPaper(@Param("eventId") String eventId, @Param("userId") String userId);

    Optional<TheoryAnswer> findByEventIdAndUserIdAndQuestionId(String eventId, String userId, String questionId);

    @Query("select a from TheoryAnswer a join fetch a.question join fetch a.user "
            + "where a.event.id = :eventId")
    List<TheoryAnswer> findAllForEvent(@Param("eventId") String eventId);

    /** 榜单聚合：每用户理论赛总得分与最后作答时间。返回 Object[]{userId, points, lastAt}。 */
    @Query("select a.user.id, coalesce(sum(a.points), 0), max(a.updatedAt) "
            + "from TheoryAnswer a where a.event.id = :eventId group by a.user.id")
    List<Object[]> aggregatePointsByEvent(@Param("eventId") String eventId);
}
