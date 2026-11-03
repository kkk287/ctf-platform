package com.example.ctf.service;

import com.example.ctf.common.BusinessException;
import com.example.ctf.common.ErrorCode;
import com.example.ctf.domain.*;
import com.example.ctf.dto.AwdDtos;
import com.example.ctf.range.InstanceSpec;
import com.example.ctf.range.OrchestratorInstance;
import com.example.ctf.range.RangeOrchestrator;
import com.example.ctf.realtime.RealtimeHub;
import com.example.ctf.repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URL;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;

/**
 * P4 AWD/AWDP：轮次状态机、队伍与靶场实例、flag 轮换、攻防计分、check 探活。
 *
 * 状态机：PREP → RUNNING ⇄ PAUSED → SCORING → FINISHED。
 * 计分：攻击得 attackPoints；被攻破扣 defensePenalty；本轮 check 首次失败扣 downtimePenalty。
 */
@Service
public class AwdService {

    private static final Logger log = LoggerFactory.getLogger(AwdService.class);
    private static final SecureRandom RANDOM = new SecureRandom();

    private final AwdRoundRepository roundRepository;
    private final AwdTeamRepository teamRepository;
    private final AwdFlagRepository flagRepository;
    private final AwdAttackRepository attackRepository;
    private final AwdScoreRepository scoreRepository;
    private final AwdCheckRepository checkRepository;
    private final EventRepository eventRepository;
    private final UserRepository userRepository;
    private final RangeOrchestrator orchestrator;
    private final RealtimeHub realtimeHub;

    @Value("${app.awd.check.simulate-noop:true}")
    private boolean simulateNoopChecks;

    @Value("${app.awd.check.timeout-ms:3000}")
    private int checkTimeoutMs;

    @Value("${app.awd.check-interval-seconds:30}")
    private int checkIntervalSeconds;

    public AwdService(AwdRoundRepository roundRepository, AwdTeamRepository teamRepository,
                      AwdFlagRepository flagRepository, AwdAttackRepository attackRepository,
                      AwdScoreRepository scoreRepository, AwdCheckRepository checkRepository,
                      EventRepository eventRepository, UserRepository userRepository,
                      RangeOrchestrator orchestrator, RealtimeHub realtimeHub) {
        this.roundRepository = roundRepository;
        this.teamRepository = teamRepository;
        this.flagRepository = flagRepository;
        this.attackRepository = attackRepository;
        this.scoreRepository = scoreRepository;
        this.checkRepository = checkRepository;
        this.eventRepository = eventRepository;
        this.userRepository = userRepository;
        this.orchestrator = orchestrator;
        this.realtimeHub = realtimeHub;
    }

    // ---------------- 轮次管理（管理端） ----------------

    @Transactional
    public AwdDtos.RoundDto create(AwdDtos.CreateRoundRequest req) {
        Event event = eventRepository.findById(req.eventId())
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "赛事不存在"));
        if (req.name() == null || req.name().isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_PARAM, "轮次名称不能为空");
        }
        if (req.totalRounds() < 1 || req.totalRounds() > 50) {
            throw new BusinessException(ErrorCode.INVALID_PARAM, "总轮数需在 1-50 之间");
        }
        if (req.roundSeconds() < 30 || req.roundSeconds() > 7200) {
            throw new BusinessException(ErrorCode.INVALID_PARAM, "单轮时长需在 30-7200 秒之间");
        }
        String checkType = req.checkType() == null || req.checkType().isBlank()
                ? "TCP" : req.checkType().trim().toUpperCase(Locale.ROOT);
        if (!checkType.equals("TCP") && !checkType.equals("HTTP")) {
            throw new BusinessException(ErrorCode.INVALID_PARAM, "check 类型仅支持 TCP / HTTP");
        }
        AwdRound round = new AwdRound();
        round.setId(UUID.randomUUID().toString());
        round.setEventId(event.getId());
        round.setName(req.name().trim());
        round.setStatus(AwdStatus.PREP);
        round.setCurrentRound(0);
        round.setTotalRounds(req.totalRounds());
        round.setRoundSeconds(req.roundSeconds());
        round.setRemainingSeconds(req.roundSeconds());
        round.setAttackPoints(Math.max(0, req.attackPoints()));
        round.setDefensePenalty(Math.max(0, req.defensePenalty()));
        round.setDowntimePenalty(Math.max(0, req.downtimePenalty()));
        round.setCheckType(checkType);
        round.setCheckTarget(req.checkTarget());
        round.setImage(req.image());
        round.setContainerPort(req.containerPort());
        round.setCreatedAt(LocalDateTime.now());
        round.setUpdatedAt(round.getCreatedAt());
        roundRepository.save(round);
        return roundDto(round, event.getName());
    }

    public List<AwdDtos.RoundDto> listRounds(String eventId) {
        Collection<AwdRound> rounds = eventId == null || eventId.isBlank()
                ? roundRepository.findAllByOrderByCreatedAtDesc()
                : roundRepository.findByEventIdOrderByCreatedAtDesc(eventId);
        List<AwdDtos.RoundDto> result = new ArrayList<>();
        for (AwdRound round : rounds) {
            String eventName = eventRepository.findById(round.getEventId())
                    .map(Event::getName).orElse("未知赛事");
            result.add(roundDto(round, eventName));
        }
        return result;
    }

    public AwdDtos.AdminDetailDto adminDetail(String roundId) {
        AwdRound round = requireRound(roundId);
        return new AwdDtos.AdminDetailDto(
                roundDto(round),
                teams(roundId),
                checkDtos(roundId, 0),
                attackLogs(roundId),
                scoreboard(roundId),
                roundScores(roundId));
    }

    @Transactional
    public AwdDtos.RoundDto transition(String roundId, String action) {
        AwdRound round = requireRound(roundId);
        String act = action == null ? "" : action.trim().toUpperCase(Locale.ROOT);
        switch (act) {
            case "START" -> startRound(round);
            case "PAUSE" -> pauseRound(round);
            case "RESUME" -> resumeRound(round);
            case "NEXT" -> advanceRound(round);
            case "STOP" -> stopRound(round);
            case "FINISH" -> finishRound(round);
            default -> throw new BusinessException(ErrorCode.INVALID_PARAM,
                    "不支持的操作：" + action + "（可用 START/PAUSE/RESUME/NEXT/STOP/FINISH）");
        }
        round.setUpdatedAt(LocalDateTime.now());
        roundRepository.save(round);
        realtimeHub.broadcastAfterCommit("awd:" + round.getId(), "AWD_ROUND_CHANGED",
                Map.of("action", act, "status", round.getStatus().name(),
                        "currentRound", round.getCurrentRound()));
        return roundDto(round);
    }

    @Transactional
    public AwdDtos.TeamDto addTeam(String roundId, AwdDtos.TeamRequest req) {
        AwdRound round = requireRound(roundId);
        if (round.getStatus() != AwdStatus.PREP) {
            throw new BusinessException(ErrorCode.INVALID_STATE, "只有准备中的轮次可以分配队伍");
        }
        if (req.name() == null || req.name().isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_PARAM, "队伍名称不能为空");
        }
        if (req.members() == null || req.members().isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_PARAM, "队伍至少需要一名成员");
        }
        if (teamRepository.countByRoundId(roundId) >= 16) {
            throw new BusinessException(ErrorCode.INVALID_PARAM, "单轮最多 16 支队伍");
        }
        teamRepository.findByRoundIdAndName(roundId, req.name().trim()).ifPresent(t -> {
            throw new BusinessException(ErrorCode.DUPLICATE, "队伍名称已存在");
        });
        LinkedHashSet<String> members = new LinkedHashSet<>();
        for (String member : req.members()) {
            if (member == null || member.isBlank()) {
                continue;
            }
            String username = member.trim();
            if (!userRepository.existsByUsername(username)) {
                throw new BusinessException(ErrorCode.INVALID_PARAM, "用户不存在：" + username);
            }
            members.add(username);
        }
        if (members.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_PARAM, "队伍至少需要一名有效成员");
        }
        AwdTeam team = new AwdTeam();
        team.setId(UUID.randomUUID().toString());
        team.setRoundId(roundId);
        team.setName(req.name().trim());
        team.setMembers(String.join(",", members));
        team.setCreatedAt(LocalDateTime.now());
        teamRepository.save(team);
        return teamDto(team);
    }

    @Transactional
    public void removeTeam(String roundId, String teamId) {
        AwdRound round = requireRound(roundId);
        if (round.getStatus() != AwdStatus.PREP && round.getStatus() != AwdStatus.PAUSED) {
            throw new BusinessException(ErrorCode.INVALID_STATE, "开轮后仅暂停状态可移除队伍");
        }
        AwdTeam team = teamRepository.findById(teamId)
                .filter(t -> t.getRoundId().equals(roundId))
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "队伍不存在"));
        teamRepository.delete(team);
    }

    // ---------------- 状态机内部实现 ----------------

    private void startRound(AwdRound round) {
        if (round.getStatus() != AwdStatus.PREP) {
            throw new BusinessException(ErrorCode.INVALID_STATE,
                    "当前状态 " + round.getStatus().label() + " 不能开轮");
        }
        List<AwdTeam> teams = teamRepository.findByRoundIdOrderByNameAsc(round.getId());
        if (teams.size() < 2) {
            throw new BusinessException(ErrorCode.INVALID_STATE, "至少需要 2 支队伍才能开轮");
        }
        LocalDateTime now = LocalDateTime.now();
        round.setStatus(AwdStatus.RUNNING);
        round.setCurrentRound(1);
        round.setStartedAt(now);
        round.setRoundEndsAt(now.plusSeconds(round.getRoundSeconds()));
        round.setRemainingSeconds(round.getRoundSeconds());
        round.setLastCheckAt(null);
        rotateFlags(round, 1, teams);
        for (AwdTeam team : teams) {
            provision(round, team);
        }
    }

    private void pauseRound(AwdRound round) {
        if (round.getStatus() != AwdStatus.RUNNING) {
            throw new BusinessException(ErrorCode.INVALID_STATE,
                    "当前状态 " + round.getStatus().label() + " 不能暂停");
        }
        round.setRemainingSeconds(Math.max(0, secondsLeft(round)));
        round.setRoundEndsAt(null);
        round.setStatus(AwdStatus.PAUSED);
    }

    private void resumeRound(AwdRound round) {
        if (round.getStatus() != AwdStatus.PAUSED) {
            throw new BusinessException(ErrorCode.INVALID_STATE,
                    "当前状态 " + round.getStatus().label() + " 不能恢复");
        }
        int remaining = round.getRemainingSeconds() > 0 ? round.getRemainingSeconds() : round.getRoundSeconds();
        round.setRoundEndsAt(LocalDateTime.now().plusSeconds(remaining));
        round.setRemainingSeconds(remaining);
        round.setStatus(AwdStatus.RUNNING);
    }

    private void advanceRound(AwdRound round) {
        if (round.getStatus() != AwdStatus.RUNNING) {
            throw new BusinessException(ErrorCode.INVALID_STATE,
                    "只有进行中的轮次可以推进到下一轮");
        }
        if (round.getCurrentRound() >= round.getTotalRounds()) {
            stopRound(round);
            return;
        }
        int next = round.getCurrentRound() + 1;
        round.setCurrentRound(next);
        round.setRemainingSeconds(round.getRoundSeconds());
        round.setRoundEndsAt(LocalDateTime.now().plusSeconds(round.getRoundSeconds()));
        rotateFlags(round, next, teamRepository.findByRoundIdOrderByNameAsc(round.getId()));
    }

    private void stopRound(AwdRound round) {
        if (round.getStatus() != AwdStatus.RUNNING && round.getStatus() != AwdStatus.PAUSED) {
            throw new BusinessException(ErrorCode.INVALID_STATE,
                    "当前状态 " + round.getStatus().label() + " 不能结算");
        }
        round.setRemainingSeconds(Math.max(0, secondsLeft(round)));
        round.setRoundEndsAt(null);
        round.setStatus(AwdStatus.SCORING);
        // 结算后回收靶场实例
        for (AwdTeam team : teamRepository.findByRoundIdOrderByNameAsc(round.getId())) {
            if (team.getContainerId() != null) {
                try {
                    orchestrator.stop(team.getContainerId());
                } catch (RuntimeException ex) {
                    log.warn("AWD 回收实例失败 team={}: {}", team.getName(), ex.getMessage());
                }
            }
        }
    }

    private void finishRound(AwdRound round) {
        if (round.getStatus() != AwdStatus.SCORING) {
            throw new BusinessException(ErrorCode.INVALID_STATE,
                    "只有结算中的轮次可以结束");
        }
        round.setStatus(AwdStatus.FINISHED);
        round.setFinishedAt(LocalDateTime.now());
    }

    private void provision(AwdRound round, AwdTeam team) {
        if (round.getImage() == null || round.getImage().isBlank()) {
            return; // 未配置镜像：留空 host/port，check 将判为失败（降级点）
        }
        try {
            String instanceId = "awd-" + round.getId().substring(0, 8) + "-" + team.getId().substring(0, 8);
            int port = round.getContainerPort() == null ? 80 : round.getContainerPort();
            InstanceSpec spec = new InstanceSpec(instanceId, round.getId(), team.getId(),
                    round.getImage(), port, 0, "0.5", 256,
                    Map.of("AWD_FLAG", team.getFlag() == null ? "" : team.getFlag()),
                    Map.of("awd", "true"));
            OrchestratorInstance instance = orchestrator.start(spec);
            team.setHost(instance.host());
            team.setPort(instance.port());
            team.setContainerId(instance.containerId());
            teamRepository.save(team);
        } catch (RuntimeException ex) {
            log.warn("AWD 拉起靶机失败 team={}: {}", team.getName(), ex.getMessage());
        }
    }

    private void rotateFlags(AwdRound round, int roundNo, List<AwdTeam> teams) {
        for (AwdTeam team : teams) {
            String flag = generateFlag(roundNo, team);
            AwdFlag entity = new AwdFlag();
            entity.setId(UUID.randomUUID().toString());
            entity.setRoundId(round.getId());
            entity.setRoundNo(roundNo);
            entity.setTeamId(team.getId());
            entity.setFlag(flag);
            entity.setCreatedAt(LocalDateTime.now());
            flagRepository.save(entity);
            team.setFlag(flag);
            team.setFlagRound(roundNo);
            teamRepository.save(team);
        }
    }

    private String generateFlag(int roundNo, AwdTeam team) {
        for (int i = 0; i < 20; i++) {
            String candidate = "flag{awd_r" + roundNo + "_" + team.getId().substring(0, 8) + "_"
                    + Long.toString(RANDOM.nextLong() & Long.MAX_VALUE, 36) + "}";
            if (!flagRepository.existsByFlag(candidate)) {
                return candidate;
            }
        }
        throw new BusinessException(ErrorCode.INTERNAL_ERROR, "flag 生成失败，请重试");
    }

    // ---------------- 攻防计分 ----------------

    @Transactional
    public AwdDtos.AttackResultDto attack(String roundId, String username, String flag) {
        AwdRound round = requireRound(roundId);
        if (round.getStatus() != AwdStatus.RUNNING) {
            throw new BusinessException(ErrorCode.INVALID_STATE,
                    "当前轮次状态为 " + round.getStatus().label() + "，仅进行中可提交 flag");
        }
        if (flag == null || flag.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_PARAM, "flag 不能为空");
        }
        List<AwdTeam> teams = teamRepository.findByRoundIdOrderByNameAsc(roundId);
        AwdTeam attacker = teams.stream().filter(t -> t.hasMember(username)).findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCode.FORBIDDEN, "你不在该轮次的任何队伍中"));
        AwdFlag target = flagRepository.findByRoundIdAndRoundNoAndFlag(roundId, round.getCurrentRound(), flag.trim())
                .orElseThrow(() -> new BusinessException(ErrorCode.WRONG_FLAG,
                        "flag 无效或已过期（每轮轮换，仅当前轮有效）"));
        if (target.getTeamId().equals(attacker.getId())) {
            throw new BusinessException(ErrorCode.INVALID_PARAM, "不能提交本队 flag");
        }
        if (attackRepository.existsByRoundIdAndFlag(roundId, target.getFlag())) {
            throw new BusinessException(ErrorCode.DUPLICATE, "该 flag 已被攻破，禁止重复刷分");
        }
        AwdTeam victim = teamRepository.findById(target.getTeamId())
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "被攻击队伍不存在"));
        int points = round.getAttackPoints();
        AwdAttack attack = new AwdAttack();
        attack.setId(UUID.randomUUID().toString());
        attack.setRoundId(roundId);
        attack.setRoundNo(round.getCurrentRound());
        attack.setAttackerTeamId(attacker.getId());
        attack.setVictimTeamId(victim.getId());
        attack.setFlag(target.getFlag());
        attack.setPoints(points);
        attack.setCreatedAt(LocalDateTime.now());
        attackRepository.save(attack);

        AwdScore attackerScore = scoreRow(round, attacker);
        attackerScore.setAttackGain(attackerScore.getAttackGain() + points);
        attackerScore.recalculate();
        attackerScore.setUpdatedAt(LocalDateTime.now());
        scoreRepository.save(attackerScore);

        AwdScore victimScore = scoreRow(round, victim);
        victimScore.setDefenseLoss(victimScore.getDefenseLoss() + round.getDefensePenalty());
        victimScore.recalculate();
        victimScore.setUpdatedAt(LocalDateTime.now());
        scoreRepository.save(victimScore);

        int attackerTotal = totalOf(roundId, attacker.getId());
        realtimeHub.broadcastAfterCommit("awd:" + roundId, "AWD_ATTACK",
                Map.of("attacker", attacker.getName(), "victim", victim.getName(),
                        "points", points, "roundNo", round.getCurrentRound()));
        realtimeHub.broadcastAfterCommit("awd:" + roundId, "AWD_SCORE_CHANGED", Map.of());
        return new AwdDtos.AttackResultDto(true, "攻击成功", points, round.getCurrentRound(),
                victim.getName(), attackerTotal);
    }

    private AwdScore scoreRow(AwdRound round, AwdTeam team) {
        return scoreRepository.findByRoundIdAndRoundNoAndTeamId(round.getId(), round.getCurrentRound(), team.getId())
                .orElseGet(() -> {
                    AwdScore score = new AwdScore();
                    score.setId(UUID.randomUUID().toString());
                    score.setRoundId(round.getId());
                    score.setRoundNo(round.getCurrentRound());
                    score.setTeamId(team.getId());
                    score.setCreatedAt(LocalDateTime.now());
                    score.setUpdatedAt(score.getCreatedAt());
                    return score;
                });
    }

    // ---------------- check 探活 ----------------

    @Transactional
    public List<AwdDtos.CheckDto> runChecks(String roundId) {
        AwdRound round = requireRound(roundId);
        if (round.getStatus() != AwdStatus.RUNNING && round.getStatus() != AwdStatus.PAUSED) {
            throw new BusinessException(ErrorCode.INVALID_STATE,
                    "仅进行中/暂停的轮次可执行 check");
        }
        List<AwdDtos.CheckDto> result = new ArrayList<>();
        for (AwdTeam team : teamRepository.findByRoundIdOrderByNameAsc(roundId)) {
            boolean ok;
            int latency = 0;
            String detail;
            long begin = System.currentTimeMillis();
            if (simulateNoopChecks && orchestrator.backendName().equals("noop")) {
                ok = true;
                latency = (int) (System.currentTimeMillis() - begin);
                detail = "noop 模拟靶机探活通过（降级模式）";
            } else if (team.getHost() == null || team.getPort() == null) {
                ok = false;
                detail = "实例未启动";
            } else if (round.getCheckType().equals("HTTP")) {
                detail = httpCheck(round, team);
                latency = (int) (System.currentTimeMillis() - begin);
                ok = detail == null;
                if (detail == null) {
                    detail = "HTTP 探活正常";
                }
            } else {
                detail = tcpCheck(team);
                latency = (int) (System.currentTimeMillis() - begin);
                ok = detail == null;
                if (detail == null) {
                    detail = "TCP 探活正常";
                }
            }
            AwdCheck check = new AwdCheck();
            check.setId(UUID.randomUUID().toString());
            check.setRoundId(roundId);
            check.setRoundNo(round.getCurrentRound());
            check.setTeamId(team.getId());
            check.setOk(ok);
            check.setLatencyMs(latency);
            check.setDetail(detail);
            check.setCheckedAt(LocalDateTime.now());
            // 本轮首次失败才扣宕机分：先判断历史失败记录，再落库
            boolean firstFailure = !ok && !checkRepository.existsByRoundIdAndRoundNoAndTeamIdAndOkFalse(
                    roundId, round.getCurrentRound(), team.getId());
            checkRepository.save(check);
            if (firstFailure) {
                AwdScore score = scoreRow(round, team);
                score.setDowntimeLoss(score.getDowntimeLoss() + round.getDowntimePenalty());
                score.recalculate();
                score.setUpdatedAt(LocalDateTime.now());
                scoreRepository.save(score);
            }
            result.add(new AwdDtos.CheckDto(team.getId(), team.getName(), ok, latency, detail, check.getCheckedAt()));
        }
        round.setLastCheckAt(LocalDateTime.now());
        roundRepository.save(round);
        realtimeHub.broadcastAfterCommit("awd:" + roundId, "AWD_CHECK", Map.of("checks", result.size()));
        return result;
    }

    private String tcpCheck(AwdTeam team) {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(team.getHost(), team.getPort()), checkTimeoutMs);
            return null;
        } catch (Exception ex) {
            return "TCP 连接失败：" + ex.getMessage();
        }
    }

    private String httpCheck(AwdRound round, AwdTeam team) {
        String template = round.getCheckTarget() == null || round.getCheckTarget().isBlank()
                ? "http://{host}:{port}/" : round.getCheckTarget();
        String url = template.replace("{host}", team.getHost()).replace("{port}", String.valueOf(team.getPort()));
        try {
            HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
            connection.setConnectTimeout(checkTimeoutMs);
            connection.setReadTimeout(checkTimeoutMs);
            connection.setRequestMethod("GET");
            int code = connection.getResponseCode();
            connection.disconnect();
            if (code >= 200 && code < 400) {
                return null;
            }
            return "HTTP 状态码 " + code;
        } catch (Exception ex) {
            return "HTTP 探活失败：" + ex.getMessage();
        }
    }

    // ---------------- 查询与排行 ----------------

    public AwdDtos.ScoreboardDto scoreboard(String roundId, String username) {
        AwdRound round = requireRound(roundId);
        List<AwdTeam> teams = teamRepository.findByRoundIdOrderByNameAsc(roundId);
        AwdTeam mine = username == null ? null
                : teams.stream().filter(t -> t.hasMember(username)).findFirst().orElse(null);
        int remaining = round.getStatus() == AwdStatus.RUNNING ? secondsLeft(round) : round.getRemainingSeconds();
        return new AwdDtos.ScoreboardDto(round.getId(), round.getName(), round.getStatus().name(),
                round.getStatus().label(), round.getCurrentRound(), round.getTotalRounds(),
                Math.max(0, remaining), round.getRoundEndsAt(),
                mine == null ? null : mine.getId(), mine == null ? null : mine.getName(),
                mine == null ? null : mine.getFlag(), mine == null ? null : mine.getFlagRound(),
                scoreboard(roundId), roundScores(roundId),
                simulateNoopChecks && orchestrator.backendName().equals("noop"));
    }

    public List<AwdDtos.CheckDto> checkDtos(String roundId, int roundNo) {
        List<AwdCheck> checks = roundNo > 0
                ? checkRepository.findByRoundIdAndRoundNoOrderByCheckedAtDesc(roundId, roundNo)
                : checkRepository.findByRoundIdOrderByCheckedAtDesc(roundId);
        Map<String, String> teamNames = new HashMap<>();
        teamRepository.findByRoundIdOrderByNameAsc(roundId)
                .forEach(t -> teamNames.put(t.getId(), t.getName()));
        List<AwdDtos.CheckDto> result = new ArrayList<>();
        for (AwdCheck check : checks) {
            result.add(new AwdDtos.CheckDto(check.getTeamId(),
                    teamNames.getOrDefault(check.getTeamId(), "未知队伍"),
                    check.isOk(), check.getLatencyMs(), check.getDetail(), check.getCheckedAt()));
        }
        return result;
    }

    public List<AwdDtos.AttackLogDto> attackLogs(String roundId) {
        Map<String, String> teamNames = new HashMap<>();
        teamRepository.findByRoundIdOrderByNameAsc(roundId)
                .forEach(t -> teamNames.put(t.getId(), t.getName()));
        List<AwdDtos.AttackLogDto> result = new ArrayList<>();
        for (AwdAttack attack : attackRepository.findByRoundIdOrderByCreatedAtDesc(roundId)) {
            result.add(new AwdDtos.AttackLogDto(
                    teamNames.getOrDefault(attack.getAttackerTeamId(), "未知队伍"),
                    teamNames.getOrDefault(attack.getVictimTeamId(), "未知队伍"),
                    attack.getPoints(), attack.getRoundNo(), attack.getCreatedAt()));
        }
        return result;
    }

    public List<AwdDtos.TeamScoreDto> scoreboard(String roundId) {
        List<AwdTeam> teams = teamRepository.findByRoundIdOrderByNameAsc(roundId);
        Map<String, int[]> aggregates = new HashMap<>();
        for (AwdScore score : scoreRepository.findByRoundIdOrderByRoundNoAsc(roundId)) {
            int[] agg = aggregates.computeIfAbsent(score.getTeamId(), k -> new int[3]);
            agg[0] += score.getAttackGain();
            agg[1] += score.getDefenseLoss();
            agg[2] += score.getDowntimeLoss();
        }
        List<AwdDtos.TeamScoreDto> list = new ArrayList<>();
        for (AwdTeam team : teams) {
            int[] agg = aggregates.getOrDefault(team.getId(), new int[3]);
            int total = agg[0] - agg[1] - agg[2];
            list.add(new AwdDtos.TeamScoreDto(team.getId(), team.getName(), agg[0], agg[1], agg[2], total, 0));
        }
        list.sort(Comparator.comparingInt(AwdDtos.TeamScoreDto::totalScore).reversed()
                .thenComparing(Comparator.comparingInt(AwdDtos.TeamScoreDto::attackGain).reversed()));
        List<AwdDtos.TeamScoreDto> ranked = new ArrayList<>();
        for (int i = 0; i < list.size(); i++) {
            AwdDtos.TeamScoreDto item = list.get(i);
            ranked.add(new AwdDtos.TeamScoreDto(item.teamId(), item.teamName(), item.attackGain(),
                    item.defenseLoss(), item.downtimeLoss(), item.totalScore(), i + 1));
        }
        return ranked;
    }

    private List<AwdDtos.RoundScoreDto> roundScores(String roundId) {
        Map<String, String> teamNames = new HashMap<>();
        teamRepository.findByRoundIdOrderByNameAsc(roundId)
                .forEach(t -> teamNames.put(t.getId(), t.getName()));
        List<AwdDtos.RoundScoreDto> result = new ArrayList<>();
        for (AwdScore score : scoreRepository.findByRoundIdOrderByRoundNoAsc(roundId)) {
            result.add(new AwdDtos.RoundScoreDto(score.getRoundNo(), score.getTeamId(),
                    teamNames.getOrDefault(score.getTeamId(), "未知队伍"), score.getAttackGain(),
                    score.getDefenseLoss(), score.getDowntimeLoss(), score.getRoundScore()));
        }
        return result;
    }

    private int totalOf(String roundId, String teamId) {
        int total = 0;
        for (AwdScore score : scoreRepository.findByRoundIdOrderByRoundNoAsc(roundId)) {
            if (score.getTeamId().equals(teamId)) {
                total += score.getRoundScore();
            }
        }
        return total;
    }

    private List<AwdDtos.TeamDto> teams(String roundId) {
        List<AwdDtos.TeamDto> list = new ArrayList<>();
        for (AwdTeam team : teamRepository.findByRoundIdOrderByNameAsc(roundId)) {
            list.add(teamDto(team));
        }
        return list;
    }

    private AwdDtos.TeamDto teamDto(AwdTeam team) {
        List<String> members = team.getMembers() == null || team.getMembers().isBlank()
                ? List.of() : List.of(team.getMembers().split(","));
        return new AwdDtos.TeamDto(team.getId(), team.getName(), members, team.getHost(), team.getPort(),
                team.getContainerId(), team.getFlag(), team.getFlagRound());
    }

    private AwdDtos.RoundDto roundDto(AwdRound round) {
        String eventName = eventRepository.findById(round.getEventId()).map(Event::getName).orElse("未知赛事");
        return roundDto(round, eventName);
    }

    private AwdDtos.RoundDto roundDto(AwdRound round, String eventName) {
        int remaining = round.getStatus() == AwdStatus.RUNNING ? secondsLeft(round) : round.getRemainingSeconds();
        return new AwdDtos.RoundDto(round.getId(), round.getEventId(), eventName, round.getName(),
                round.getStatus().name(), round.getStatus().label(), round.getCurrentRound(),
                round.getTotalRounds(), round.getRoundSeconds(), Math.max(0, remaining),
                round.getAttackPoints(), round.getDefensePenalty(), round.getDowntimePenalty(),
                round.getCheckType(), round.getCheckTarget(), round.getImage(), round.getContainerPort(),
                teamRepository.countByRoundId(round.getId()), round.getStartedAt(), round.getRoundEndsAt(),
                round.getLastCheckAt(), round.getFinishedAt(), round.getCreatedAt());
    }

    private AwdRound requireRound(String roundId) {
        return roundRepository.findById(roundId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "AWD 轮次不存在"));
    }

    private int secondsLeft(AwdRound round) {
        if (round.getRoundEndsAt() == null) {
            return round.getRemainingSeconds();
        }
        return (int) Math.max(0, Duration.between(LocalDateTime.now(), round.getRoundEndsAt()).getSeconds());
    }

    // ---------------- 定时推进 ----------------

    @Transactional
    public void tick(LocalDateTime now) {
        for (AwdRound round : roundRepository.findByStatusInOrderByCreatedAtDesc(List.of(AwdStatus.RUNNING))) {
            if (round.getRoundEndsAt() != null && now.isAfter(round.getRoundEndsAt())) {
                if (round.getCurrentRound() < round.getTotalRounds()) {
                    advanceRound(round);
                } else {
                    stopRound(round);
                }
                round.setUpdatedAt(now);
                roundRepository.save(round);
                realtimeHub.broadcastAfterCommit("awd:" + round.getId(), "AWD_ROUND_CHANGED",
                        Map.of("action", "AUTO", "status", round.getStatus().name(),
                                "currentRound", round.getCurrentRound()));
            } else if (round.getLastCheckAt() == null
                    || Duration.between(round.getLastCheckAt(), now).getSeconds() >= checkIntervalSeconds) {
                runChecks(round.getId());
            }
        }
    }

    public List<AwdDtos.RoundDto> activeRounds(String eventId) {
        List<AwdDtos.RoundDto> result = new ArrayList<>();
        for (AwdDtos.RoundDto dto : listRounds(eventId)) {
            if (!dto.status().equals("FINISHED")) {
                result.add(dto);
            }
        }
        return result;
    }
}
