package com.example.ctf.service;

import com.example.ctf.common.BusinessException;
import com.example.ctf.common.ErrorCode;
import com.example.ctf.domain.*;
import com.example.ctf.dto.WriteupDtos;
import com.example.ctf.repository.ChallengeRepository;
import com.example.ctf.repository.SolveRepository;
import com.example.ctf.repository.WriteupRepository;
import com.example.ctf.security.UserPrincipal;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Writeup 服务：赛后开放的解题报告。
 * - 已解出该题的用户才能提交；
 * - 比赛进行中可写但仅作者/管理员可见；赛后全员可见；
 * - 管理端审核：可采纳/驳回。
 */
@Service
public class WriteupService {

    /** 赛后公开的 writeup 状态：SUBMITTED（赛后自动开放）、ACCEPTED（管理端采纳） */
    private static final Set<WriteupStatus> PUBLIC_STATUSES = Set.of(WriteupStatus.SUBMITTED, WriteupStatus.ACCEPTED);

    private final WriteupRepository writeupRepository;
    private final ChallengeRepository challengeRepository;
    private final SolveRepository solveRepository;
    private final EventService eventService;
    private final AuditService auditService;

    public WriteupService(WriteupRepository writeupRepository, ChallengeRepository challengeRepository,
                          SolveRepository solveRepository, EventService eventService, AuditService auditService) {
        this.writeupRepository = writeupRepository;
        this.challengeRepository = challengeRepository;
        this.solveRepository = solveRepository;
        this.eventService = eventService;
        this.auditService = auditService;
    }

    /** 创建 Writeup：必须已解出该题。若草稿已存在则报错（前端应先调列表让用户编辑）。 */
    @Transactional
    public WriteupDtos.WriteupDto create(String eventId, WriteupDtos.WriteupRequest request,
                                          UserPrincipal principal) {
        Event event = eventService.resolveEvent(eventId);
        Challenge challenge = challengeRepository.findById(request.challengeId())
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "题目不存在"));
        if (!challenge.getEvent().getId().equals(event.getId())) {
            throw new BusinessException(ErrorCode.INVALID_PARAM, "该题目不属于此赛事");
        }
        assertSolved(principal.getId(), challenge.getId());
        assertNotExists(event.getId(), principal.getId(), challenge.getId());

        Writeup writeup = new Writeup();
        writeup.setEvent(event);
        writeup.setChallenge(challenge);
        writeup.setUser(profileUser(principal));
        writeup.setTitle(trim(request.title(), 120));
        writeup.setMarkdown(trim(request.markdown(), 200_000));
        writeup.setStatus(WriteupStatus.SUBMITTED);
        writeupRepository.save(writeup);
        auditService.record("WRITEUP_CREATE", challenge.getName(), principal.getUsername() + " 提交 Writeup");
        return toDto(writeup);
    }

    /** 编辑 Writeup：仅作者可改；比赛结束后也可编辑（题目仍在），但状态保持。 */
    @Transactional
    public WriteupDtos.WriteupDto update(String id, WriteupDtos.WriteupRequest request,
                                          UserPrincipal principal) {
        Writeup writeup = requireOwned(id, principal);
        Challenge challenge = challengeRepository.findById(request.challengeId())
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "题目不存在"));
        if (!writeup.getChallenge().getId().equals(request.challengeId())) {
            // 换题需新建
            assertNotExists(writeup.getEvent().getId(), principal.getId(), challenge.getId());
            assertSolved(principal.getId(), challenge.getId());
            writeup.setChallenge(challenge);
        }
        assertSolved(principal.getId(), writeup.getChallenge().getId());
        writeup.setTitle(trim(request.title(), 120));
        writeup.setMarkdown(trim(request.markdown(), 200_000));
        writeupRepository.save(writeup);
        auditService.record("WRITEUP_UPDATE", writeup.getChallenge().getName(),
                principal.getUsername() + " 更新 Writeup");
        return toDto(writeup);
    }

    @Transactional
    public void delete(String id, UserPrincipal principal) {
        Writeup writeup = requireOwned(id, principal);
        writeupRepository.delete(writeup);
        auditService.record("WRITEUP_DELETE", writeup.getChallenge().getName(),
                principal.getUsername() + " 删除 Writeup");
    }

    /** 详情：赛后可见公开 writeup（SUBMITTED/ACCEPTED），非赛后仅作者/管理员可看。 */
    @Transactional(readOnly = true)
    public WriteupDtos.WriteupDto detail(String id, UserPrincipal principal) {
        Writeup writeup = writeupRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Writeup 不存在"));
        EventStatus eventStatus = EventService.statusOf(writeup.getEvent());
        boolean privileged = isPrivileged(principal);
        boolean isAuthor = principal.getId().equals(writeup.getUser().getId());
        boolean publiclyVisible = eventStatus == EventStatus.ENDED
                && PUBLIC_STATUSES.contains(writeup.getStatus());
        if (!isAuthor && !privileged && !publiclyVisible) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "Writeup 尚未公开，仅作者/管理员可见");
        }
        return toDto(writeup);
    }

    /** 列表：某赛事/题目下可见的 writeup。 */
    @Transactional(readOnly = true)
    public List<WriteupDtos.SummaryDto> list(String eventId, String challengeId, UserPrincipal principal) {
        Event event = eventService.resolveEvent(eventId);
        EventStatus eventStatus = EventService.statusOf(event);
        boolean privileged = isPrivileged(principal);
        boolean isEnded = eventStatus == EventStatus.ENDED;

        List<Writeup> all;
        if (isEnded || privileged) {
            // 赛后或管理员：公开状态 writeup（challengeId 过滤见下方）
            all = writeupRepository.findPublicByEvent(event.getId(),
                    List.of(WriteupStatus.SUBMITTED, WriteupStatus.ACCEPTED));
        } else {
            // 非赛后、非管理员：仅自己的全部 writeup（含草稿）
            all = writeupRepository.findByEventIdAndUserIdOrderByCreatedAtDesc(
                    event.getId(), principal.getId());
            // 防御性过滤：JPA session 缓存可能跨测试残留
            all = all.stream().filter(w -> principal.getId().equals(w.getUser().getId())).toList();
        }
        if (challengeId != null && !challengeId.isBlank()) {
            all = all.stream().filter(w -> w.getChallenge().getId().equals(challengeId)).toList();
        }
        return all.stream().map(this::toSummary).toList();
    }

    /** 管理端：全部 writeup（含非公开、自己草稿），支持审核。 */
    @Transactional(readOnly = true)
    public List<WriteupDtos.SummaryDto> adminList(String eventId) {
        Event event = eventService.resolveEvent(eventId);
        return writeupRepository.findAllForEvent(event.getId()).stream().map(this::toSummary).toList();
    }

    /** 管理端：审核 WRITEUP（采纳/驳回）。 */
    @Transactional
    public WriteupDtos.WriteupDto moderate(String id, WriteupDtos.ModerateRequest request) {
        Writeup writeup = writeupRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Writeup 不存在"));
        WriteupStatus newStatus;
        try {
            newStatus = WriteupStatus.valueOf(request.status().trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new BusinessException(ErrorCode.INVALID_PARAM, "状态仅支持 SUBMITTED / ACCEPTED / REJECTED");
        }
        writeup.setStatus(newStatus);
        writeup.setAccepted(newStatus == WriteupStatus.ACCEPTED);
        writeupRepository.save(writeup);
        auditService.record("WRITEUP_MODERATE", writeup.getChallenge().getName(),
                "设为 " + newStatus.label() + "：" + (request.note() == null ? "" : request.note()));
        return toDto(writeup);
    }

    // ---------- 内部 ----------

    private void assertSolved(String userId, String challengeId) {
        if (!solveRepository.existsByUserIdAndChallengeId(userId, challengeId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "仅已解出该题的用户可提交 Writeup");
        }
    }

    private void assertNotExists(String eventId, String userId, String challengeId) {
        Optional<Writeup> existing = writeupRepository
                .findByEventIdAndUserIdAndChallengeId(eventId, userId, challengeId);
        if (existing.isPresent()) {
            throw new BusinessException(ErrorCode.DUPLICATE, "您已为该题提交过 Writeup，请直接编辑");
        }
    }

    private Writeup requireOwned(String id, UserPrincipal principal) {
        Writeup writeup = writeupRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Writeup 不存在"));
        if (!writeup.getUser().getId().equals(principal.getId()) && !isPrivileged(principal)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "无权操作他人 Writeup");
        }
        return writeup;
    }

    private User profileUser(UserPrincipal principal) {
        User user = new User();
        user.setId(principal.getId());
        return user;
    }

    private boolean isPrivileged(UserPrincipal principal) {
        Role role = principal.getRole();
        return role == Role.ADMIN || role == Role.JUDGE || role == Role.TEACHER;
    }

    private String trim(String value, int max) {
        if (value == null || value.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_PARAM, "内容不能为空");
        }
        return value.length() <= max ? value : value.substring(0, max);
    }

    private WriteupDtos.WriteupDto toDto(Writeup w) {
        return new WriteupDtos.WriteupDto(w.getId(), w.getEvent().getId(), w.getChallenge().getId(),
                w.getChallenge().getName(), w.getUser().getId(), w.getUser().getUsername(),
                w.getUser().getDisplayName(), w.getTitle(), w.getStatus().name(), w.getStatus().label(),
                w.isAccepted(), w.getMarkdown(), w.getCreatedAt(), w.getUpdatedAt());
    }

    private WriteupDtos.SummaryDto toSummary(Writeup w) {
        return new WriteupDtos.SummaryDto(w.getId(), w.getTitle(), w.getStatus().name(),
                w.getStatus().label(), w.isAccepted(), w.getUser().getId(),
                w.getUser().getUsername(), w.getUser().getDisplayName(), w.getCreatedAt());
    }
}
