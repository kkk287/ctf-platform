package com.example.ctf.service;

import com.example.ctf.common.BusinessException;
import com.example.ctf.common.ErrorCode;
import com.example.ctf.domain.Event;
import com.example.ctf.domain.Role;
import com.example.ctf.domain.Solve;
import com.example.ctf.domain.User;
import com.example.ctf.dto.ScoreboardDtos;
import com.example.ctf.dto.TeamDtos;
import com.example.ctf.repository.SolveRepository;
import com.example.ctf.repository.TheoryAnswerRepository;
import com.example.ctf.repository.UserRepository;
import com.example.ctf.security.UserPrincipal;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class ScoreboardService {

    private final SolveRepository solveRepository;
    private final UserRepository userRepository;
    private final EventService eventService;
    private final TeamService teamService;
    private final AuthService authService;
    private final DtoMapper mapper;
    private final TheoryAnswerRepository theoryAnswerRepository;

    public ScoreboardService(SolveRepository solveRepository, UserRepository userRepository,
                             EventService eventService, TeamService teamService, AuthService authService,
                             DtoMapper mapper, TheoryAnswerRepository theoryAnswerRepository) {
        this.solveRepository = solveRepository;
        this.userRepository = userRepository;
        this.eventService = eventService;
        this.teamService = teamService;
        this.authService = authService;
        this.mapper = mapper;
        this.theoryAnswerRepository = theoryAnswerRepository;
    }

    @Transactional(readOnly = true)
    public ScoreboardDtos.ScoreboardResponse scoreboard(String eventId, String type, int limit,
                                                        UserPrincipal principal) {
        Event event = eventService.resolveEvent(eventId);
        boolean admin = principal.getRole() == Role.ADMIN;
        eventService.assertViewable(event, admin);
        int safeLimit = Math.max(1, Math.min(limit, 200));
        String boardType = "TEAM".equalsIgnoreCase(type) ? "TEAM" : "USER";

        List<Solve> solves = solveRepository.findAllForEvent(event.getId());
        if ("TEAM".equals(boardType)) {
            return teamScoreboard(event, solves, safeLimit, principal);
        }
        List<ScoreboardDtos.ScoreEntryDto> all = personalEntries(solves, theoryAggregate(event.getId()));
        ScoreboardDtos.ScoreEntryDto me = all.stream()
                .filter(e -> e.id().equals(principal.getId())).findFirst().orElse(null);
        // 黑灯模式：普通选手仅可见自身条目
        List<ScoreboardDtos.ScoreEntryDto> entries = blackoutFor(event, principal)
                ? (me == null ? List.of() : List.of(me))
                : all.stream().limit(safeLimit).toList();
        return new ScoreboardDtos.ScoreboardResponse("USER", eventService.getEventDto(event.getId()),
                entries, me, LocalDateTime.now());
    }

    @Transactional(readOnly = true)
    public int personalRank(String eventId, String userId) {
        List<ScoreboardDtos.ScoreEntryDto> entries = personalEntries(
                solveRepository.findAllForEvent(eventId), theoryAggregate(eventId));
        return entries.stream().filter(e -> e.id().equals(userId)).map(ScoreboardDtos.ScoreEntryDto::rank)
                .findFirst().orElse(0);
    }

    @Transactional(readOnly = true)
    public List<ScoreboardDtos.RecentSolveDto> recent(String eventId, int limit, UserPrincipal principal) {
        Event event = eventService.resolveEvent(eventId);
        // 黑灯模式：普通选手不可见解题动态
        if (blackoutFor(event, principal)) {
            return List.of();
        }
        int safeLimit = Math.max(1, Math.min(limit, 50));
        return solveRepository.findRecentForEvent(event.getId(), PageRequest.of(0, safeLimit)).stream()
                .map(s -> new ScoreboardDtos.RecentSolveDto(s.getUser().getUsername(),
                        s.getUser().getDisplayName(),
                        s.getUser().getTeam() == null ? null : s.getUser().getTeam().getName(),
                        s.getChallenge().getName(), s.getChallenge().getCategory().name(),
                        s.getChallenge().getCategory().label(), s.getFinalPoints(),
                        s.getBloodRank().name(), s.getBloodRank().label(), s.getSolvedAt()))
                .toList();
    }

    @Transactional(readOnly = true)
    public ScoreboardDtos.ProfileResponse profile(UserPrincipal principal) {
        User user = authService.requireUser(principal.getId());
        List<Solve> solves = solveRepository.findAllByUser(user.getId());
        List<ScoreboardDtos.MySolveDto> solveDtos = solves.stream().map(s -> {
            Event e = s.getChallenge().getEvent();
            return new ScoreboardDtos.MySolveDto(s.getChallenge().getId(), s.getChallenge().getName(),
                    s.getChallenge().getCategory().name(), s.getChallenge().getCategory().label(),
                    e.getId(), e.getName(), e.getType().name(), e.getType().label(), s.getFinalPoints(),
                    s.getBasePoints(), s.getBloodBonus(), s.getHintCost(), s.getBloodRank().name(),
                    s.getBloodRank().label(), s.getSolvedAt());
        }).toList();

        Map<String, List<Solve>> byEvent = new LinkedHashMap<>();
        for (Solve solve : solves) {
            byEvent.computeIfAbsent(solve.getChallenge().getEvent().getId(), k -> new ArrayList<>()).add(solve);
        }
        List<ScoreboardDtos.EventStatDto> eventStats = byEvent.values().stream().map(list -> {
            Event e = list.get(0).getChallenge().getEvent();
            long points = list.stream().mapToLong(Solve::getFinalPoints).sum();
            return new ScoreboardDtos.EventStatDto(e.getId(), e.getName(), e.getType().name(),
                    e.getType().label(), points, personalRank(e.getId(), user.getId()), list.size());
        }).toList();

        TeamDtos.TeamInfoDto teamInfo = teamService.myTeam(user.getId());
        long total = solves.stream().mapToLong(Solve::getFinalPoints).sum();
        int hintCost = solves.stream().mapToInt(Solve::getHintCost).sum();
        return new ScoreboardDtos.ProfileResponse(mapper.userDto(user), teamInfo, total, eventStats,
                solveDtos, hintCost);
    }

    private List<ScoreboardDtos.ScoreEntryDto> personalEntries(List<Solve> solves,
                                                               Map<String, TheoryAgg> theory) {
        Map<String, PersonalAggregate> aggregates = new HashMap<>();
        for (Solve solve : solves) {
            User user = solve.getUser();
            if (user.isBanned()) {
                continue;
            }
            PersonalAggregate aggregate = aggregates.computeIfAbsent(user.getId(), k -> new PersonalAggregate(user));
            aggregate.total += solve.getFinalPoints();
            aggregate.solveCount++;
            if (aggregate.lastSolveAt == null || solve.getSolvedAt().isAfter(aggregate.lastSolveAt)) {
                aggregate.lastSolveAt = solve.getSolvedAt();
            }
        }
        // 并入理论赛得分：无 CTF 解题记录的用户也凭理论赛上榜
        Set<String> missing = new HashSet<>();
        for (Map.Entry<String, TheoryAgg> entry : theory.entrySet()) {
            if (!aggregates.containsKey(entry.getKey())) {
                missing.add(entry.getKey());
            }
        }
        Map<String, User> usersById = missing.isEmpty() ? Map.of()
                : userRepository.findAllById(missing).stream()
                        .collect(Collectors.toMap(User::getId, u -> u));
        for (Map.Entry<String, TheoryAgg> entry : theory.entrySet()) {
            PersonalAggregate aggregate = aggregates.get(entry.getKey());
            if (aggregate == null) {
                User user = usersById.get(entry.getKey());
                if (user == null || user.isBanned()) {
                    continue;
                }
                aggregate = aggregates.computeIfAbsent(user.getId(), k -> new PersonalAggregate(user));
            }
            aggregate.total += entry.getValue().points();
            LocalDateTime lastAt = entry.getValue().lastAt();
            if (lastAt != null && (aggregate.lastSolveAt == null || lastAt.isAfter(aggregate.lastSolveAt))) {
                aggregate.lastSolveAt = lastAt;
            }
        }
        List<PersonalAggregate> sorted = aggregates.values().stream()
                .sorted(Comparator.comparingLong(PersonalAggregate::total).reversed()
                        .thenComparing(a -> a.lastSolveAt, Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(a -> a.user.getUsername()))
                .toList();
        List<ScoreboardDtos.ScoreEntryDto> entries = new ArrayList<>();
        for (int i = 0; i < sorted.size(); i++) {
            PersonalAggregate a = sorted.get(i);
            entries.add(new ScoreboardDtos.ScoreEntryDto(i + 1, a.user.getId(), a.user.getDisplayName(),
                    a.user.getUsername(), a.user.getTeam() == null ? null : a.user.getTeam().getName(),
                    0, a.total, a.solveCount, a.lastSolveAt));
        }
        return entries;
    }

    /** 理论赛聚合：每用户总得分与最后作答时间（无理论赛的赛事返回空表）。 */
    private Map<String, TheoryAgg> theoryAggregate(String eventId) {
        Map<String, TheoryAgg> result = new HashMap<>();
        for (Object[] row : theoryAnswerRepository.aggregatePointsByEvent(eventId)) {
            String userId = (String) row[0];
            long points = ((Number) row[1]).longValue();
            LocalDateTime lastAt = (LocalDateTime) row[2];
            result.put(userId, new TheoryAgg(points, lastAt));
        }
        return result;
    }

    /** 理论赛聚合值 */
    private record TheoryAgg(long points, LocalDateTime lastAt) {
    }

    private ScoreboardDtos.ScoreboardResponse teamScoreboard(Event event, List<Solve> solves, int limit,
                                                              UserPrincipal principal) {
        Map<String, TeamAggregate> aggregates = new HashMap<>();
        for (Solve solve : solves) {
            User user = solve.getUser();
            if (user.isBanned() || user.getTeam() == null) {
                continue;
            }
            String teamId = user.getTeam().getId();
            TeamAggregate aggregate = aggregates.computeIfAbsent(teamId,
                    k -> new TeamAggregate(teamId, user.getTeam().getName()));
            // 战队规则：成员得分求和，同一题只计最高分
            Solve previous = aggregate.bestByChallenge.get(solve.getChallenge().getId());
            if (previous == null || solve.getFinalPoints() > previous.getFinalPoints()
                    || (solve.getFinalPoints() == previous.getFinalPoints()
                    && solve.getSolvedAt().isBefore(previous.getSolvedAt()))) {
                aggregate.bestByChallenge.put(solve.getChallenge().getId(), solve);
            }
        }
        for (TeamAggregate aggregate : aggregates.values()) {
            aggregate.recompute();
            aggregate.memberCount = userRepository.countByTeamId(aggregate.teamId);
        }
        List<TeamAggregate> sorted = aggregates.values().stream()
                .sorted(Comparator.comparingLong(TeamAggregate::total).reversed()
                        .thenComparing(a -> a.lastSolveAt)
                        .thenComparing(a -> a.name))
                .toList();
        List<ScoreboardDtos.ScoreEntryDto> entries = new ArrayList<>();
        for (int i = 0; i < sorted.size(); i++) {
            TeamAggregate a = sorted.get(i);
            entries.add(new ScoreboardDtos.ScoreEntryDto(i + 1, a.teamId, a.name,
                    a.memberCount + " 名成员", null, a.memberCount, a.total, a.solveCount, a.lastSolveAt));
        }
        User current = authService.requireUser(principal.getId());
        ScoreboardDtos.ScoreEntryDto me = current.getTeam() == null ? null : entries.stream()
                .filter(e -> e.id().equals(current.getTeam().getId())).findFirst().orElse(null);
        // 黑灯模式：普通选手仅可见本队条目
        List<ScoreboardDtos.ScoreEntryDto> visibleEntries = blackoutFor(event, principal)
                ? (me == null ? List.of() : List.of(me))
                : entries.stream().limit(limit).toList();
        return new ScoreboardDtos.ScoreboardResponse("TEAM", eventService.getEventDto(event.getId()),
                visibleEntries, me, LocalDateTime.now());
    }

    /** 黑灯模式是否对当前用户生效：赛事开启开关且用户不是管理员/裁判 */
    private static boolean blackoutFor(Event event, UserPrincipal principal) {
        return event.isBlackoutMode()
                && principal.getRole() != Role.ADMIN && principal.getRole() != Role.JUDGE;
    }

    private static final class PersonalAggregate {
        private final User user;
        private long total;
        private long solveCount;
        private LocalDateTime lastSolveAt;

        private PersonalAggregate(User user) {
            this.user = user;
        }

        long total() {
            return total;
        }
    }

    private static final class TeamAggregate {
        private final String teamId;
        private final String name;
        private final Map<String, Solve> bestByChallenge = new HashMap<>();
        private long total;
        private long solveCount;
        private long memberCount;
        private LocalDateTime lastSolveAt;

        private TeamAggregate(String teamId, String name) {
            this.teamId = teamId;
            this.name = name;
        }

        void recompute() {
            total = bestByChallenge.values().stream().mapToLong(Solve::getFinalPoints).sum();
            solveCount = bestByChallenge.size();
            lastSolveAt = bestByChallenge.values().stream().map(Solve::getSolvedAt)
                    .max(LocalDateTime::compareTo).orElse(null);
        }

        long total() {
            return total;
        }
    }
}
