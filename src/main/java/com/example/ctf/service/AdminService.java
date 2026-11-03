package com.example.ctf.service;

import com.example.ctf.common.BusinessException;
import com.example.ctf.common.ErrorCode;
import com.example.ctf.common.PageResult;
import com.example.ctf.domain.*;
import com.example.ctf.dto.AdminDtos;
import com.example.ctf.dto.ScoreboardDtos;
import com.example.ctf.repository.AnnouncementRepository;
import com.example.ctf.repository.AuditLogRepository;
import com.example.ctf.repository.SolveRepository;
import com.example.ctf.repository.SubmissionRepository;
import com.example.ctf.repository.UserRepository;
import com.example.ctf.security.UserPrincipal;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

@Service
public class AdminService {

    private final UserRepository userRepository;
    private final SubmissionRepository submissionRepository;
    private final SolveRepository solveRepository;
    private final AnnouncementRepository announcementRepository;
    private final AuditLogRepository auditLogRepository;
    private final AuthService authService;
    private final DtoMapper mapper;
    private final AuditService auditService;

    public AdminService(UserRepository userRepository, SubmissionRepository submissionRepository,
                        SolveRepository solveRepository, AnnouncementRepository announcementRepository,
                        AuditLogRepository auditLogRepository, AuthService authService, DtoMapper mapper,
                        AuditService auditService) {
        this.userRepository = userRepository;
        this.submissionRepository = submissionRepository;
        this.solveRepository = solveRepository;
        this.announcementRepository = announcementRepository;
        this.auditLogRepository = auditLogRepository;
        this.authService = authService;
        this.mapper = mapper;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public PageResult<AdminDtos.AdminUserDto> users(int page, int size, String keyword) {
        PageRequest pageable = PageRequest.of(Math.max(0, page), clamp(size),
                Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<User> result = keyword == null || keyword.isBlank()
                ? userRepository.findAll(pageable)
                : userRepository.findByUsernameContainingIgnoreCaseOrDisplayNameContainingIgnoreCase(
                keyword.trim(), keyword.trim(), pageable);
        return PageResult.from(result.map(this::adminUserDto));
    }

    @Transactional
    public AdminDtos.AdminUserDto ban(String operatorId, String userId, boolean banned) {
        if (operatorId.equals(userId)) {
            throw new BusinessException(ErrorCode.INVALID_STATE, "不能封禁自己");
        }
        User user = authService.requireUser(userId);
        if (user.getRole() == Role.ADMIN) {
            throw new BusinessException(ErrorCode.INVALID_STATE, "不能封禁管理员账号");
        }
        user.setBanned(banned);
        userRepository.save(user);
        auditService.record(banned ? "USER_BAN" : "USER_UNBAN", user.getUsername(), null);
        return adminUserDto(user);
    }

    /**
     * 变更用户角色（仅 ADMIN）。保护规则：不能改自己；不能把管理员降级，避免平台失去管理入口。
     */
    @Transactional
    public AdminDtos.AdminUserDto updateRole(String operatorId, String userId, Role role) {
        if (operatorId.equals(userId)) {
            throw new BusinessException(ErrorCode.INVALID_STATE, "不能修改自己的角色");
        }
        User user = authService.requireUser(userId);
        if (user.getRole() == Role.ADMIN && role != Role.ADMIN) {
            throw new BusinessException(ErrorCode.INVALID_STATE, "不能降级管理员账号");
        }
        Role old = user.getRole();
        if (old != role) {
            user.setRole(role);
            userRepository.save(user);
            auditService.record("USER_ROLE_CHANGE", user.getUsername(),
                    "角色由 " + old.name() + " 变更为 " + role.name());
        }
        return adminUserDto(user);
    }

    @Transactional(readOnly = true)
    public PageResult<AdminDtos.AuditLogDto> auditLogs(int page, int size, String action) {
        Specification<AuditLog> spec = (root, query, cb) -> action == null || action.isBlank()
                ? cb.conjunction()
                : cb.equal(root.get("action"), action.trim());
        PageRequest pageable = PageRequest.of(Math.max(0, page), clamp(size),
                Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<AuditLog> result = auditLogRepository.findAll(spec, pageable);
        return PageResult.from(result.map(log -> new AdminDtos.AuditLogDto(log.getId(), log.getActorName(),
                log.getActorRole(), log.getAction(), log.getTarget(), log.getDetail(), log.getIp(),
                log.getCreatedAt())));
    }

    @Transactional(readOnly = true)
    public PageResult<AdminDtos.SubmissionDto> submissions(int page, int size, String challengeId,
                                                            String userId, SubmissionResult result) {
        Specification<Submission> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (challengeId != null && !challengeId.isBlank()) {
                predicates.add(cb.equal(root.get("challenge").get("id"), challengeId));
            }
            if (userId != null && !userId.isBlank()) {
                predicates.add(cb.equal(root.get("user").get("id"), userId));
            }
            if (result != null) {
                predicates.add(cb.equal(root.get("result"), result));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        PageRequest pageable = PageRequest.of(Math.max(0, page), clamp(size),
                Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<Submission> submissions = submissionRepository.findAll(spec, pageable);
        return PageResult.from(submissions.map(s -> new AdminDtos.SubmissionDto(s.getId(),
                s.getUser().getUsername(), s.getChallenge().getName(), s.getResult().name(),
                s.getResult() == SubmissionResult.CORRECT ? "正确" : "错误", s.getIp(), s.getUserAgent(),
                s.getSubmittedHash().substring(0, Math.min(12, s.getSubmittedHash().length())),
                s.getCreatedAt())));
    }

    /**
     * 防作弊预警（基础版）：同一题目 + 同一 IP 下出现多个账号的正确提交，提示疑似共享 flag。
     */
    @Transactional(readOnly = true)
    public List<AdminDtos.AnomalyDto> anomalies() {
        List<Object[]> groups = submissionRepository.findSharedIpGroups(SubmissionResult.CORRECT);
        List<AdminDtos.AnomalyDto> result = new ArrayList<>();
        for (Object[] row : groups) {
            Challenge challenge = (Challenge) row[0];
            String ip = (String) row[1];
            Long count = (Long) row[2];
            List<Submission> submissions = submissionRepository
                    .findByChallengeIdAndIpAndResultOrderByCreatedAtAsc(challenge.getId(), ip,
                            SubmissionResult.CORRECT);
            LinkedHashSet<String> usernames = new LinkedHashSet<>();
            LocalDateTime first = null;
            LocalDateTime last = null;
            for (Submission submission : submissions) {
                usernames.add(submission.getUser().getUsername());
                if (first == null || submission.getCreatedAt().isBefore(first)) {
                    first = submission.getCreatedAt();
                }
                if (last == null || submission.getCreatedAt().isAfter(last)) {
                    last = submission.getCreatedAt();
                }
            }
            if (usernames.size() >= 2) {
                result.add(new AdminDtos.AnomalyDto(challenge.getId(), challenge.getName(), ip,
                        count, List.copyOf(usernames), first, last));
            }
        }
        return result;
    }

    @Transactional(readOnly = true)
    public List<ScoreboardDtos.AnnouncementDto> announcements() {
        return announcementRepository.findAllByOrderByCreatedAtDesc().stream()
                .map(mapper::announcementDto).toList();
    }

    @Transactional
    public ScoreboardDtos.AnnouncementDto createAnnouncement(String userId, AdminDtos.AnnouncementRequest request) {
        Announcement announcement = new Announcement();
        announcement.setTitle(request.title().trim());
        announcement.setContent(request.content().trim());
        announcement.setCreatedBy(authService.requireUser(userId));
        announcementRepository.save(announcement);
        auditService.record("ANNOUNCEMENT_CREATE", announcement.getTitle(), null);
        return mapper.announcementDto(announcement);
    }

    @Transactional
    public ScoreboardDtos.AnnouncementDto updateAnnouncement(String id, AdminDtos.AnnouncementRequest request) {
        Announcement announcement = announcementRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "公告不存在"));
        announcement.setTitle(request.title().trim());
        announcement.setContent(request.content().trim());
        announcementRepository.save(announcement);
        auditService.record("ANNOUNCEMENT_UPDATE", announcement.getTitle(), null);
        return mapper.announcementDto(announcement);
    }

    @Transactional
    public void deleteAnnouncement(String id) {
        Announcement announcement = announcementRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "公告不存在"));
        announcementRepository.delete(announcement);
        auditService.record("ANNOUNCEMENT_DELETE", announcement.getTitle(), null);
    }

    private AdminDtos.AdminUserDto adminUserDto(User user) {
        return new AdminDtos.AdminUserDto(user.getId(), user.getUsername(), user.getDisplayName(),
                user.getRole().name(), mapper.roleLabel(user.getRole()), user.isBanned(),
                user.getTeam() == null ? null : user.getTeam().getName(),
                solveRepository.countByUserId(user.getId()), solveRepository.sumPointsByUser(user.getId()),
                user.getCreatedAt());
    }

    private int clamp(int size) {
        return Math.max(1, Math.min(size, 100));
    }
}
