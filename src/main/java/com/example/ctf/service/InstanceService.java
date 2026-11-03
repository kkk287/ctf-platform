package com.example.ctf.service;

import com.example.ctf.common.BusinessException;
import com.example.ctf.common.ErrorCode;
import com.example.ctf.domain.*;
import com.example.ctf.dto.ChallengeDtos;
import com.example.ctf.repository.ChallengeRepository;
import com.example.ctf.repository.DynamicFlagRepository;
import com.example.ctf.repository.InstanceRepository;
import com.example.ctf.repository.UserRepository;
import com.example.ctf.range.InstanceSpec;
import com.example.ctf.range.OrchestratorInstance;
import com.example.ctf.range.RangeOrchestrator;
import com.example.ctf.range.RangeProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** 靶机实例生命周期编排：申请 / 停止 / 重置 / 查询 / TTL 回收。 */
@Service
public class InstanceService {

    private static final Logger log = LoggerFactory.getLogger(InstanceService.class);

    private final InstanceRepository instanceRepository;
    private final DynamicFlagRepository dynamicFlagRepository;
    private final ChallengeRepository challengeRepository;
    private final UserRepository userRepository;
    private final RangeOrchestrator orchestrator;
    private final RangeProperties props;
    private final DynamicFlagService dynamicFlagService;
    private final AuditService auditService;

    public InstanceService(InstanceRepository instanceRepository,
                           DynamicFlagRepository dynamicFlagRepository,
                           ChallengeRepository challengeRepository,
                           UserRepository userRepository,
                           RangeOrchestrator orchestrator,
                           RangeProperties props,
                           DynamicFlagService dynamicFlagService,
                           AuditService auditService) {
        this.instanceRepository = instanceRepository;
        this.dynamicFlagRepository = dynamicFlagRepository;
        this.challengeRepository = challengeRepository;
        this.userRepository = userRepository;
        this.orchestrator = orchestrator;
        this.props = props;
        this.dynamicFlagService = dynamicFlagService;
        this.auditService = auditService;
    }

    /** 申请靶机：已存在活跃实例则直接延长 TTL 返回。 */
    @Transactional
    public ChallengeDtos.InstanceStatusDto start(String challengeId, String ownerId) {
        Challenge challenge = challengeRepository.findById(challengeId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "题目不存在"));
        if (challenge.getInstanceImage() == null || challenge.getInstanceImage().isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_PARAM, "该题目未配置靶机镜像");
        }

        // 1) 复用：同 owner 在同 challenge 下已存在活跃实例则延长 TTL
        Instance existing = instanceRepository
                .findFirstByOwnerIdAndChallengeIdAndStatusInOrderByCreatedAtDesc(
                        ownerId, challengeId, List.of(InstanceStatus.PENDING, InstanceStatus.RUNNING))
                .orElse(null);
        if (existing != null) {
            if (props.enabled() && !orchestrator.isRunning(existing.getContainerId())) {
                // 实例记录还在但容器已死，走 reset 路径
                return resetInternal(challenge, existing, ownerId);
            }
            existing.setExpiresAt(LocalDateTime.now().plusMinutes(ttlMinutesFor(challenge)));
            instanceRepository.save(existing);
            return toDto(existing, challenge);
        }

        // 2) 并发控制
        long perUser = instanceRepository.findByOwnerIdAndStatusIn(ownerId,
                List.of(InstanceStatus.PENDING, InstanceStatus.RUNNING)).size();
        if (perUser >= props.maxInstancesPerUser()) {
            throw new BusinessException(ErrorCode.MAX_INSTANCE_LIMIT,
                    "您的活跃靶机已达上限（每用户 " + props.maxInstancesPerUser() + " 台）");
        }
        long global = instanceRepository.countByStatusIn(
                List.of(InstanceStatus.PENDING, InstanceStatus.RUNNING));
        if (global >= props.maxInstancesGlobal()) {
            throw new BusinessException(ErrorCode.MAX_INSTANCE_LIMIT,
                    "平台活跃靶机已达上限（" + props.maxInstancesGlobal() + " 台）");
        }

        // 3) 分配宿主端口
        Integer hostPort = allocateHostPort();

        // 4) 动态 flag 生成（明文仅用于注入 env，不落库明文）
        String dynamicPlaintext = null;
        if (challenge.isInstanceDynamicFlag()) {
            dynamicPlaintext = dynamicFlagService.generatePlaintext(challengeId, ownerId);
        }

        // 5) 组装 InstanceSpec
        Map<String, String> env = new HashMap<>();
        env.put("CHALLENGE_ID", challengeId);
        env.put("OWNER_ID", ownerId);
        if (challenge.isInstanceDynamicFlag()) {
            env.put("FLAG", dynamicPlaintext);
            env.put("FLAG_DYNAMIC", "true");
        } else {
            env.put("FLAG_DYNAMIC", "false");
        }
        Map<String, String> labels = Map.of(
                "ctf.challenge-id", challengeId,
                "ctf.owner-id", ownerId,
                "ctf.created-at", String.valueOf(System.currentTimeMillis())
        );
        InstanceSpec spec = new InstanceSpec(
                "inst-" + java.util.UUID.randomUUID().toString().replace("-", "").substring(0, 16),
                challengeId, ownerId,
                challenge.getInstanceImage(),
                challenge.getInstancePort(),
                hostPort,
                challenge.getInstanceCpuLimit(),
                challenge.getInstanceMemoryMb(),
                env, labels);

        // 6) 调 orchestrator（生产 Docker / 测试 Noop）
        OrchestratorInstance orc;
        try {
            orc = orchestrator.start(spec);
        } catch (Exception ex) {
            log.error("orchestrator.start failed for challenge={}, owner={}: {}", challengeId, ownerId, ex.getMessage());
            auditService.record("INSTANCE_START_FAIL", challenge.getName(), ex.getMessage());
            throw new BusinessException(ErrorCode.RANGE_UNAVAILABLE,
                    "靶场调度器不可用（" + orchestrator.backendName() + "）：" + ex.getMessage());
        }

        // 7) 落库 Instance
        LocalDateTime now = LocalDateTime.now();
        Instance inst = new Instance();
        inst.setChallenge(challenge);
        inst.setOwnerId(ownerId);
        inst.setContainerId(orc.containerId());
        inst.setContainerName(orc.containerName());
        inst.setHost(orc.host());
        inst.setPort(orc.port() != null ? orc.port() : hostPort);
        inst.setStatus(InstanceStatus.RUNNING);
        inst.setExpiresAt(now.plusMinutes(ttlMinutesFor(challenge)));
        inst.setStartedAt(orc.startedAt() != null ? orc.startedAt() : now);
        instanceRepository.save(inst);

        // 8) 落库 DynamicFlag（如启用动态 flag）
        if (challenge.isInstanceDynamicFlag() && dynamicPlaintext != null) {
            DynamicFlagService.StoredFlag stored = dynamicFlagService.store(dynamicPlaintext);
            DynamicFlag df = new DynamicFlag();
            df.setInstance(inst);
            df.setChallenge(challenge);
            df.setOwnerId(ownerId);
            df.setFlagHash(stored.flagHash());
            df.setFlagSalt(stored.flagSalt());
            dynamicFlagRepository.save(df);
        }

        auditService.record("INSTANCE_START", challenge.getName(),
                "后端=" + orchestrator.backendName() + " host=" + inst.getHost() + ":" + inst.getPort());
        return toDto(inst, challenge);
    }

    /** 主动销毁。 */
    @Transactional
    public void stop(String challengeId, String ownerId) {
        Instance inst = requireActive(challengeId, ownerId);
        doStop(inst);
    }

    /** 重置（stop + start，同一 owner/challenge）。 */
    @Transactional
    public ChallengeDtos.InstanceStatusDto reset(String challengeId, String ownerId) {
        Instance existing = requireActive(challengeId, ownerId);
        Challenge challenge = existing.getChallenge();
        return resetInternal(challenge, existing, ownerId);
    }

    /** 查询某 owner 在某 challenge 下的活跃实例（无则返回 null）。 */
    @Transactional(readOnly = true)
    public ChallengeDtos.InstanceStatusDto status(String challengeId, String ownerId) {
        Instance inst = instanceRepository
                .findFirstByOwnerIdAndChallengeIdAndStatusInOrderByCreatedAtDesc(
                        ownerId, challengeId, List.of(InstanceStatus.PENDING, InstanceStatus.RUNNING))
                .orElse(null);
        if (inst == null) return null;
        return toDto(inst, inst.getChallenge());
    }

    /** 管理端：全量实例列表。 */
    @Transactional(readOnly = true)
    @PreAuthorize("hasAnyRole('ADMIN','JUDGE')")
    public List<ChallengeDtos.AdminInstanceDto> adminList() {
        return instanceRepository.findAll().stream().map(inst -> {
            User owner = userRepository.findById(inst.getOwnerId()).orElse(null);
            Challenge challenge = inst.getChallenge();
            return new ChallengeDtos.AdminInstanceDto(
                    inst.getId(),
                    challenge.getId(), challenge.getName(),
                    owner == null ? null : owner.getUsername(),
                    inst.getStatus().name(),
                    inst.getHost(), inst.getPort(),
                    inst.getContainerId(),
                    inst.getStartedAt(), inst.getExpiresAt());
        }).toList();
    }

    // ---------- TTL 调度器 ----------

    /** 每分钟扫一次（配置可调）：TTL 到期仍为 RUNNING/PENDING 的实例置 EXPIRED 并 stop。 */
    @Scheduled(fixedDelayString = "${app.range.sweep-interval-ms:60000}")
    @Transactional
    public void sweepExpired() {
        LocalDateTime now = LocalDateTime.now();
        List<Instance> expired = instanceRepository.findByStatusInAndExpiresAtBefore(
                List.of(InstanceStatus.RUNNING, InstanceStatus.PENDING), now);
        if (expired.isEmpty()) return;
        for (Instance inst : expired) {
            inst.setStatus(InstanceStatus.EXPIRED);
            inst.setStoppedAt(now);
            instanceRepository.save(inst);
            try { orchestrator.stop(inst.getContainerId()); } catch (Exception ex) {
                log.warn("sweep: stop container {} failed: {}", inst.getContainerId(), ex.getMessage());
            }
            auditService.record("INSTANCE_EXPIRED", inst.getChallenge().getName(),
                    "owner=" + inst.getOwnerId());
        }
        log.info("TTL sweep: {} instances expired", expired.size());
    }

    // ---------- 内部工具 ----------

    private ChallengeDtos.InstanceStatusDto resetInternal(Challenge challenge, Instance existing, String ownerId) {
        doStop(existing);
        // 直接走 start 复用逻辑
        return start(challenge.getId(), ownerId);
    }

    private void doStop(Instance inst) {
        try { orchestrator.stop(inst.getContainerId()); } catch (Exception ignored) {}
        inst.setStatus(InstanceStatus.STOPPED);
        inst.setStoppedAt(LocalDateTime.now());
        instanceRepository.save(inst);
        auditService.record("INSTANCE_STOP", inst.getChallenge().getName(),
                "owner=" + inst.getOwnerId());
    }

    private Instance requireActive(String challengeId, String ownerId) {
        return instanceRepository
                .findFirstByOwnerIdAndChallengeIdAndStatusInOrderByCreatedAtDesc(
                        ownerId, challengeId, List.of(InstanceStatus.PENDING, InstanceStatus.RUNNING))
                .orElseThrow(() -> new BusinessException(ErrorCode.INSTANCE_NOT_RUNNING,
                        "该题目当前没有您的活跃靶机"));
    }

    /** 从配置区间挑一个未被占用的宿主端口（遍历一次即可，规模小）。 */
    private Integer allocateHostPort() {
        int start = props.portBridgeStart();
        int end = props.portBridgeEnd();
        if (end < start) end = start + 999;
        List<Integer> used = instanceRepository.findAll().stream()
                .filter(i -> i.isActive() && i.getPort() != null)
                .map(Instance::getPort).toList();
        for (int p = start; p <= end; p++) {
            if (!used.contains(p)) return p;
        }
        throw new BusinessException(ErrorCode.MAX_INSTANCE_LIMIT,
                "宿主端口池已满（" + start + "-" + end + "），无法再分配");
    }

    private int ttlMinutesFor(Challenge challenge) {
        if (challenge.getInstanceTtlMinutes() != null && challenge.getInstanceTtlMinutes() > 0) {
            return challenge.getInstanceTtlMinutes();
        }
        return props.defaultTtlMinutes();
    }

    private ChallengeDtos.InstanceStatusDto toDto(Instance inst, Challenge challenge) {
        return new ChallengeDtos.InstanceStatusDto(
                inst.getId(),
                challenge.getId(), challenge.getName(),
                challenge.isInstanceDynamicFlag(),
                inst.getStatus().name(),
                inst.getHost(), inst.getPort(),
                inst.getStartedAt(), inst.getExpiresAt());
    }
}
