package com.example.ctf.repository;

import com.example.ctf.domain.Instance;
import com.example.ctf.domain.InstanceStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface InstanceRepository extends JpaRepository<Instance, String> {
    List<Instance> findByOwnerIdAndStatusIn(String ownerId, List<InstanceStatus> statuses);

    List<Instance> findByChallengeId(String challengeId);

    /** 指定 owner 在指定 challenge 下的活跃实例（可能多条，reset 时全部清理） */
    List<Instance> findByOwnerIdAndChallengeIdAndStatusIn(String ownerId, String challengeId,
                                                          List<InstanceStatus> statuses);

    /** 查找指定 owner 在指定 challenge 下最新一条活跃实例 */
    Optional<Instance> findFirstByOwnerIdAndChallengeIdAndStatusInOrderByCreatedAtDesc(
            String ownerId, String challengeId, List<InstanceStatus> statuses);

    /** TTL 调度器：查找已过期但仍为 RUNNING/PENDING 的实例 */
    List<Instance> findByStatusInAndExpiresAtBefore(List<InstanceStatus> statuses, LocalDateTime before);

    long countByStatusIn(List<InstanceStatus> statuses);
}
