package com.example.ctf.service;

import com.example.ctf.common.BusinessException;
import com.example.ctf.common.ErrorCode;
import com.example.ctf.domain.*;
import com.example.ctf.dto.ChallengeDtos;
import com.example.ctf.dto.EventDtos;
import com.example.ctf.repository.*;
import com.example.ctf.security.UserPrincipal;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class ChallengeService {

    private final ChallengeRepository challengeRepository;
    private final HintRepository hintRepository;
    private final HintUnlockRepository hintUnlockRepository;
    private final SolveRepository solveRepository;
    private final SubmissionRepository submissionRepository;
    private final EventService eventService;
    private final FileStorageService fileStorageService;
    private final FlagHasher flagHasher;
    private final DtoMapper mapper;
    private final AuthService authService;
    private final ScoreboardService scoreboardService;
    private final AuditService auditService;

    @Value("${app.submission.max-wrong-attempts:5}")
    private int maxWrongAttempts;

    @Value("${app.submission.window-minutes:5}")
    private int wrongWindowMinutes;

    @Value("${app.submission.cooldown-seconds:5}")
    private int cooldownSeconds;

    public ChallengeService(ChallengeRepository challengeRepository, HintRepository hintRepository,
                            HintUnlockRepository hintUnlockRepository, SolveRepository solveRepository,
                            SubmissionRepository submissionRepository, EventService eventService,
                            FileStorageService fileStorageService, FlagHasher flagHasher, DtoMapper mapper,
                            AuthService authService, ScoreboardService scoreboardService,
                            AuditService auditService) {
        this.challengeRepository = challengeRepository;
        this.hintRepository = hintRepository;
        this.hintUnlockRepository = hintUnlockRepository;
        this.solveRepository = solveRepository;
        this.submissionRepository = submissionRepository;
        this.eventService = eventService;
        this.fileStorageService = fileStorageService;
        this.flagHasher = flagHasher;
        this.mapper = mapper;
        this.authService = authService;
        this.scoreboardService = scoreboardService;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public ChallengeDtos.ChallengeListResponse list(String eventId, Category category, Boolean solvedFilter,
                                                    String keyword, UserPrincipal principal) {
        Event event = eventService.resolveEvent(eventId);
        boolean admin = principal.getRole() == Role.ADMIN;
        eventService.assertViewable(event, admin);

        List<Challenge> challenges = admin
                ? challengeRepository.findByEventIdOrderByCategoryAscNameAsc(event.getId())
                : challengeRepository.findByEventIdAndVisibleTrueOrderByCategoryAscNameAsc(event.getId());
        Set<String> solvedIds = solveRepository.findSolvedChallengeIds(principal.getId(), event.getId())
                .stream().collect(Collectors.toSet());
        String kw = keyword == null ? "" : keyword.trim().toLowerCase();

        List<ChallengeDtos.ChallengeSummaryDto> items = challenges.stream()
                .filter(c -> category == null || c.getCategory() == category)
                .filter(c -> kw.isEmpty() || c.getName().toLowerCase().contains(kw)
                        || (c.getDescription() != null && c.getDescription().toLowerCase().contains(kw)))
                .filter(c -> solvedFilter == null || solvedFilter == solvedIds.contains(c.getId()))
                .map(c -> summary(c, solvedIds.contains(c.getId())))
                .toList();
        return new ChallengeDtos.ChallengeListResponse(admin
                ? mapper.eventDto(event, challengeRepository.countByEventId(event.getId()),
                challengeRepository.countByEventIdAndVisibleTrue(event.getId()))
                : mapper.eventDto(event, challengeRepository.countByEventIdAndVisibleTrue(event.getId()),
                challengeRepository.countByEventIdAndVisibleTrue(event.getId())), items);
    }

    @Transactional(readOnly = true)
    public ChallengeDtos.ChallengeDetailDto detail(String id, UserPrincipal principal) {
        Challenge challenge = requireAccessible(id, principal);
        Event event = challenge.getEvent();
        boolean solved = solveRepository.existsByUserIdAndChallengeId(principal.getId(), id);
        LocalDateTime solvedAt = solveRepository.findByUserIdAndChallengeId(principal.getId(), id)
                .map(Solve::getSolvedAt).orElse(null);
        List<Hint> hints = hintRepository.findByChallengeIdOrderByCostAsc(id);
        boolean adminView = principal.getRole() == Role.ADMIN;
        Set<String> unlockedHintIds = hintUnlockRepository.findByUserIdAndChallengeId(principal.getId(), id)
                .stream().map(u -> u.getHint().getId()).collect(Collectors.toSet());
        List<ChallengeDtos.HintDto> hintDtos = hints.stream()
                .map(h -> {
                    boolean unlocked = solved || adminView || unlockedHintIds.contains(h.getId());
                    return new ChallengeDtos.HintDto(h.getId(), unlocked ? h.getContent() : null, h.getCost(), unlocked);
                }).toList();
        List<ChallengeDtos.SolveBriefDto> recent = solveRepository
                .findTopSolvers(id, PageRequest.of(0, 5)).stream()
                .map(s -> new ChallengeDtos.SolveBriefDto(s.getUser().getUsername(), s.getUser().getDisplayName(),
                        s.getUser().getTeam() == null ? null : s.getUser().getTeam().getName(),
                        s.getFinalPoints(), s.getBloodRank().name(), s.getBloodRank().label(), s.getSolvedAt()))
                .toList();
        return new ChallengeDtos.ChallengeDetailDto(id,
                mapper.eventDto(event, challengeRepository.countByEventId(event.getId()),
                        challengeRepository.countByEventIdAndVisibleTrue(event.getId())),
                challenge.getName(), challenge.getDescription(), challenge.getCategory(),
                challenge.getCategory().label(), challenge.getDifficulty(), challenge.getDifficulty().label(),
                challenge.getInitialPoints(), currentPoints(challenge), challenge.getMinPoints(),
                challenge.getDecayPoints(), solveRepository.countByChallengeId(id), solved, solvedAt,
                challenge.isVisible(), challenge.getAttachmentOriginalName(), challenge.getAttachmentSize(),
                hintDtos, recent, challenge.getCreatedAt());
    }

    @Transactional(readOnly = true)
    public AttachmentDownload attachment(String id, UserPrincipal principal) {
        Challenge challenge = requireAccessible(id, principal);
        if (challenge.getAttachmentStoredName() == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "该题目没有附件");
        }
        Path path = fileStorageService.resolve(challenge.getAttachmentStoredName());
        return new AttachmentDownload(path, challenge.getAttachmentOriginalName(),
                challenge.getAttachmentContentType());
    }

    /**
     * 判分主流程。错误提交也会落审计记录，因此业务异常不回滚事务。
     */
    @Transactional(noRollbackFor = BusinessException.class)
    public ChallengeDtos.SubmitResponse submit(String challengeId, String flag, String ip, String userAgent,
                                               UserPrincipal principal) {
        if (principal.getRole() == Role.OBSERVER) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "观摩账号仅可浏览，不可参与答题");
        }
        Challenge challenge = challengeRepository.findForUpdate(challengeId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "题目不存在"));
        User user = authService.requireUser(principal.getId());
        boolean admin = principal.getRole() == Role.ADMIN;
        if (!challenge.isVisible() && !admin) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "题目不存在");
        }
        eventService.assertViewable(challenge.getEvent(), admin);
        eventService.assertSubmittable(challenge.getEvent());
        if (solveRepository.existsByUserIdAndChallengeId(user.getId(), challengeId)) {
            throw new BusinessException(ErrorCode.ALREADY_SOLVED);
        }

        LocalDateTime now = LocalDateTime.now();
        submissionRepository.findFirstByUserIdAndChallengeIdAndResultOrderByCreatedAtDesc(
                        user.getId(), challengeId, SubmissionResult.WRONG)
                .ifPresent(last -> {
                    if (cooldownSeconds > 0 && last.getCreatedAt().plusSeconds(cooldownSeconds).isAfter(now)) {
                        long wait = java.time.Duration.between(now,
                                last.getCreatedAt().plusSeconds(cooldownSeconds)).toSeconds() + 1;
                        throw new BusinessException(ErrorCode.COOLDOWN, "提交过于频繁，请 " + wait + " 秒后再试");
                    }
                });
        long wrongInWindow = submissionRepository.countByUserIdAndChallengeIdAndResultAndCreatedAtAfter(
                user.getId(), challengeId, SubmissionResult.WRONG, now.minusMinutes(wrongWindowMinutes));
        if (wrongInWindow >= maxWrongAttempts) {
            throw new BusinessException(ErrorCode.RATE_LIMITED,
                    "错误尝试过多，请 " + wrongWindowMinutes + " 分钟后再试");
        }

        String submittedHash = flagHasher.submissionHash(flag);
        boolean correct = flagHasher.matches(flag, challenge.getFlagHash(), challenge.getFlagSalt());
        Submission submission = new Submission();
        submission.setUser(user);
        submission.setChallenge(challenge);
        submission.setSubmittedHash(submittedHash);
        submission.setResult(correct ? SubmissionResult.CORRECT : SubmissionResult.WRONG);
        submission.setIp(ip);
        submission.setUserAgent(userAgent);
        submission.setCreatedAt(now);
        submissionRepository.save(submission);

        if (!correct) {
            throw new BusinessException(ErrorCode.WRONG_FLAG);
        }

        long solvedBefore = solveRepository.countByChallengeId(challengeId);
        BloodRank bloodRank = BloodRank.fromSolveOrder((int) solvedBefore + 1);
        int base = ScoringPolicy.basePoints(challenge.getInitialPoints(), challenge.getMinPoints(),
                challenge.getDecayPoints(), solvedBefore);
        int bonus = ScoringPolicy.bloodBonus(base, bloodRank);
        int hintCost = hintUnlockRepository.sumCostByUserAndChallenge(user.getId(), challengeId);
        int finalPoints = ScoringPolicy.finalPoints(base, bonus, hintCost);

        Solve solve = new Solve();
        solve.setUser(user);
        solve.setChallenge(challenge);
        solve.setSubmissionId(submission.getId());
        solve.setBasePoints(base);
        solve.setBloodBonus(bonus);
        solve.setHintCost(hintCost);
        solve.setFinalPoints(finalPoints);
        solve.setBloodRank(bloodRank);
        solve.setSolvedAt(now);
        solveRepository.save(solve);

        long total = solveRepository.sumPointsInEvent(user.getId(), challenge.getEvent().getId());
        int rank = scoreboardService.personalRank(challenge.getEvent().getId(), user.getId());
        String message = bloodRank == BloodRank.NONE ? "提交正确，得分已记录"
                : "提交正确，" + bloodRank.label() + "！";
        return new ChallengeDtos.SubmitResponse(true, message, base, bonus, hintCost, finalPoints,
                bloodRank.name(), bloodRank.label(), total, rank,
                solveRepository.countByChallengeId(challengeId), currentPoints(challenge));
    }

    @Transactional
    public ChallengeDtos.UnlockHintResponse unlockHint(String challengeId, String hintId, UserPrincipal principal) {
        if (principal.getRole() == Role.OBSERVER) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "观摩账号仅可浏览，不可解锁提示");
        }
        Challenge challenge = requireAccessible(challengeId, principal);
        Hint hint = hintRepository.findById(hintId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "提示不存在"));
        if (!hint.getChallenge().getId().equals(challengeId)) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "提示不属于该题目");
        }
        if (solveRepository.existsByUserIdAndChallengeId(principal.getId(), challengeId)) {
            return new ChallengeDtos.UnlockHintResponse(hintId, hint.getContent(), 0);
        }
        if (!hintUnlockRepository.existsByUserIdAndHintId(principal.getId(), hintId)) {
            HintUnlock unlock = new HintUnlock();
            unlock.setUser(authService.requireUser(principal.getId()));
            unlock.setChallenge(challenge);
            unlock.setHint(hint);
            unlock.setCost(hint.getCost());
            hintUnlockRepository.save(unlock);
        }
        return new ChallengeDtos.UnlockHintResponse(hintId, hint.getContent(), hint.getCost());
    }

    // ---------- 管理端 ----------

    @Transactional(readOnly = true)
    public List<ChallengeDtos.AdminChallengeDto> adminList() {
        return challengeRepository.findAll().stream()
                .map(this::adminDto).toList();
    }

    @Transactional
    public ChallengeDtos.AdminChallengeDto create(ChallengeDtos.AdminChallengeRequest request) {
        if (request.flag() == null || request.flag().isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_PARAM, "新建题目必须设置 flag");
        }
        Challenge challenge = new Challenge();
        challenge.setEvent(eventService.requireEvent(request.eventId()));
        applyBasics(challenge, request);
        String salt = flagHasher.newSalt();
        challenge.setFlagSalt(salt);
        challenge.setFlagHash(flagHasher.hash(request.flag(), salt));
        challengeRepository.save(challenge);
        auditService.record("CHALLENGE_CREATE", challenge.getName(),
                "赛事 " + challenge.getEvent().getName());
        return adminDto(challenge);
    }

    @Transactional
    public ChallengeDtos.AdminChallengeDto update(String id, ChallengeDtos.AdminChallengeRequest request) {
        Challenge challenge = requireChallenge(id);
        challenge.setEvent(eventService.requireEvent(request.eventId()));
        applyBasics(challenge, request);
        if (request.flag() != null && !request.flag().isBlank()) {
            String salt = flagHasher.newSalt();
            challenge.setFlagSalt(salt);
            challenge.setFlagHash(flagHasher.hash(request.flag(), salt));
        }
        challengeRepository.save(challenge);
        auditService.record("CHALLENGE_UPDATE", challenge.getName(),
                "赛事 " + challenge.getEvent().getName());
        return adminDto(challenge);
    }

    @Transactional
    public void delete(String id) {
        Challenge challenge = requireChallenge(id);
        fileStorageService.deleteQuietly(challenge.getAttachmentStoredName());
        challengeRepository.delete(challenge);
        auditService.record("CHALLENGE_DELETE", challenge.getName(), null);
    }

    @Transactional
    public ChallengeDtos.AdminChallengeDto uploadAttachment(String id, MultipartFile file) {
        Challenge challenge = requireChallenge(id);
        FileStorageService.StoredFile stored = fileStorageService.store(file);
        fileStorageService.deleteQuietly(challenge.getAttachmentStoredName());
        challenge.setAttachmentStoredName(stored.storedName());
        challenge.setAttachmentOriginalName(stored.originalName());
        challenge.setAttachmentContentType(stored.contentType());
        challenge.setAttachmentSize(stored.size());
        challengeRepository.save(challenge);
        auditService.record("CHALLENGE_ATTACHMENT_UPLOAD", challenge.getName(), stored.originalName());
        return adminDto(challenge);
    }

    @Transactional
    public ChallengeDtos.AdminChallengeDto deleteAttachment(String id) {
        Challenge challenge = requireChallenge(id);
        fileStorageService.deleteQuietly(challenge.getAttachmentStoredName());
        challenge.setAttachmentStoredName(null);
        challenge.setAttachmentOriginalName(null);
        challenge.setAttachmentContentType(null);
        challenge.setAttachmentSize(null);
        challengeRepository.save(challenge);
        auditService.record("CHALLENGE_ATTACHMENT_DELETE", challenge.getName(), null);
        return adminDto(challenge);
    }

    @Transactional
    public ChallengeDtos.HintDto addHint(String challengeId, ChallengeDtos.HintRequest request) {
        Challenge challenge = requireChallenge(challengeId);
        Hint hint = new Hint();
        hint.setChallenge(challenge);
        hint.setContent(request.content().trim());
        hint.setCost(request.cost());
        hintRepository.save(hint);
        auditService.record("HINT_CREATE", challenge.getName(), "扣分 " + hint.getCost());
        return new ChallengeDtos.HintDto(hint.getId(), hint.getContent(), hint.getCost(), false);
    }

    @Transactional
    public ChallengeDtos.HintDto updateHint(String challengeId, String hintId, ChallengeDtos.HintRequest request) {
        Hint hint = requireHint(challengeId, hintId);
        hint.setContent(request.content().trim());
        hint.setCost(request.cost());
        hintRepository.save(hint);
        auditService.record("HINT_UPDATE", hint.getChallenge().getName(), "扣分 " + hint.getCost());
        return new ChallengeDtos.HintDto(hint.getId(), hint.getContent(), hint.getCost(), false);
    }

    @Transactional
    public void deleteHint(String challengeId, String hintId) {
        Hint hint = requireHint(challengeId, hintId);
        String challengeName = hint.getChallenge().getName();
        hintRepository.delete(hint);
        auditService.record("HINT_DELETE", challengeName, null);
    }

    private Hint requireHint(String challengeId, String hintId) {
        Hint hint = hintRepository.findById(hintId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "提示不存在"));
        if (!hint.getChallenge().getId().equals(challengeId)) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "提示不属于该题目");
        }
        return hint;
    }

    private Challenge requireChallenge(String id) {
        return challengeRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "题目不存在"));
    }

    private Challenge requireAccessible(String id, UserPrincipal principal) {
        Challenge challenge = requireChallenge(id);
        boolean admin = principal.getRole() == Role.ADMIN;
        if (!challenge.isVisible() && !admin) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "题目不存在");
        }
        eventService.assertViewable(challenge.getEvent(), admin);
        return challenge;
    }

    private void applyBasics(Challenge challenge, ChallengeDtos.AdminChallengeRequest request) {
        int min = request.minPoints() == null ? Math.max(1, request.initialPoints() / 10)
                : request.minPoints();
        int decay = request.decayPoints() == null ? 5 : request.decayPoints();
        if (min > request.initialPoints()) {
            throw new BusinessException(ErrorCode.INVALID_PARAM, "最低分不能高于初始分值");
        }
        challenge.setName(request.name().trim());
        challenge.setDescription(request.description());
        challenge.setCategory(request.category());
        challenge.setDifficulty(request.difficulty());
        challenge.setInitialPoints(request.initialPoints());
        challenge.setMinPoints(min);
        challenge.setDecayPoints(decay);
        challenge.setVisible(request.visible() == null || request.visible());
    }

    private int currentPoints(Challenge challenge) {
        return ScoringPolicy.currentPoints(challenge.getInitialPoints(), challenge.getMinPoints(),
                challenge.getDecayPoints(), solveRepository.countByChallengeId(challenge.getId()));
    }

    private ChallengeDtos.ChallengeSummaryDto summary(Challenge c, boolean solved) {
        return new ChallengeDtos.ChallengeSummaryDto(c.getId(), c.getEvent().getId(), c.getName(),
                c.getDescription(), c.getCategory(), c.getCategory().label(), c.getDifficulty(),
                c.getDifficulty().label(), c.getInitialPoints(), currentPoints(c),
                solveRepository.countByChallengeId(c.getId()), c.isVisible(), solved,
                hintRepository.findByChallengeIdOrderByCostAsc(c.getId()).size(),
                c.getAttachmentStoredName() != null);
    }

    private ChallengeDtos.AdminChallengeDto adminDto(Challenge c) {
        return new ChallengeDtos.AdminChallengeDto(c.getId(), c.getEvent().getId(), c.getEvent().getName(),
                c.getName(), c.getDescription(), c.getCategory(), c.getCategory().label(), c.getDifficulty(),
                c.getDifficulty().label(), c.getInitialPoints(), c.getMinPoints(), c.getDecayPoints(),
                currentPoints(c), c.isVisible(), c.getFlagHash() != null, c.getAttachmentOriginalName(),
                c.getAttachmentSize(), solveRepository.countByChallengeId(c.getId()),
                c.getCreatedAt(), c.getUpdatedAt());
    }

    public record AttachmentDownload(Path path, String filename, String contentType) {
    }
}
