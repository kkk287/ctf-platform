package com.example.ctf.service;

import com.example.ctf.common.BusinessException;
import com.example.ctf.common.ErrorCode;
import com.example.ctf.domain.*;
import com.example.ctf.dto.TheoryDtos;
import com.example.ctf.repository.TheoryAnswerRepository;
import com.example.ctf.repository.TheoryConfigRepository;
import com.example.ctf.repository.TheoryQuestionRepository;
import com.example.ctf.security.UserPrincipal;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

/** 理论赛：题库管理、随机抽题物化、判分（多选部分得分）、能力报告。 */
@Service
public class TheoryService {

    private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() {};
    private static final TypeReference<List<Integer>> INT_LIST = new TypeReference<>() {};
    private static final List<String> JUDGE_OPTIONS = List.of("正确", "错误");
    private static final String UNTAGGED = "未标注";

    private final TheoryQuestionRepository questionRepository;
    private final TheoryConfigRepository configRepository;
    private final TheoryAnswerRepository answerRepository;
    private final EventService eventService;
    private final AuditService auditService;
    private final ObjectMapper mapper;

    public TheoryService(TheoryQuestionRepository questionRepository,
                         TheoryConfigRepository configRepository,
                         TheoryAnswerRepository answerRepository,
                         EventService eventService, AuditService auditService,
                         ObjectMapper mapper) {
        this.questionRepository = questionRepository;
        this.configRepository = configRepository;
        this.answerRepository = answerRepository;
        this.eventService = eventService;
        this.auditService = auditService;
        this.mapper = mapper;
    }

    // ---------- 管理端：题库 CRUD ----------

    @Transactional(readOnly = true)
    public List<TheoryDtos.AdminQuestionDto> listQuestions(String eventId) {
        Event event = eventService.resolveEvent(eventId);
        return questionRepository.findByEventIdOrderByCreatedAtAsc(event.getId()).stream()
                .map(this::toAdminDto).toList();
    }

    @Transactional
    public TheoryDtos.AdminQuestionDto createQuestion(TheoryDtos.QuestionRequest request) {
        Event event = eventService.requireEvent(request.eventId());
        TheoryQuestion question = new TheoryQuestion();
        question.setEvent(event);
        applyQuestion(question, request);
        questionRepository.save(question);
        auditService.record("THEORY_QUESTION_CREATE", event.getName(),
                question.getKind().label() + "：" + truncate(question.getStem()));
        return toAdminDto(question);
    }

    @Transactional
    public TheoryDtos.AdminQuestionDto updateQuestion(String id, TheoryDtos.QuestionRequest request) {
        TheoryQuestion question = questionRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "题目不存在"));
        applyQuestion(question, request);
        questionRepository.save(question);
        auditService.record("THEORY_QUESTION_UPDATE", question.getEvent().getName(),
                question.getKind().label() + "：" + truncate(question.getStem()));
        return toAdminDto(question);
    }

    @Transactional
    public void deleteQuestion(String id) {
        TheoryQuestion question = questionRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "题目不存在"));
        questionRepository.delete(question);
        auditService.record("THEORY_QUESTION_DELETE", question.getEvent().getName(),
                question.getKind().label() + "：" + truncate(question.getStem()));
    }

    // ---------- 管理端：抽题配置 ----------

    @Transactional(readOnly = true)
    public TheoryDtos.ConfigDto getConfig(String eventId) {
        Event event = eventService.resolveEvent(eventId);
        TheoryConfig config = configRepository.findById(event.getId()).orElse(null);
        long poolSize = questionRepository.countByEventId(event.getId());
        if (config == null) {
            return new TheoryDtos.ConfigDto(event.getId(), 0, 0, 0, 0, 0, 0, 0, 0, poolSize);
        }
        return toConfigDto(config, poolSize);
    }

    @Transactional
    public TheoryDtos.ConfigDto saveConfig(TheoryDtos.ConfigRequest request) {
        Event event = eventService.requireEvent(request.eventId());
        validateCounts(request.singleCount(), request.singlePoints(), "单选");
        validateCounts(request.multiCount(), request.multiPoints(), "多选");
        validateCounts(request.judgeCount(), request.judgePoints(), "判断");
        TheoryConfig config = configRepository.findById(event.getId()).orElseGet(TheoryConfig::new);
        config.setEventId(event.getId());
        config.setSingleCount(request.singleCount());
        config.setSinglePoints(request.singlePoints());
        config.setMultiCount(request.multiCount());
        config.setMultiPoints(request.multiPoints());
        config.setJudgeCount(request.judgeCount());
        config.setJudgePoints(request.judgePoints());
        configRepository.save(config);
        auditService.record("THEORY_CONFIG_SAVE", event.getName(),
                "单选" + request.singleCount() + "x" + request.singlePoints()
                        + " 多选" + request.multiCount() + "x" + request.multiPoints()
                        + " 判断" + request.judgeCount() + "x" + request.judgePoints());
        return toConfigDto(config, questionRepository.countByEventId(event.getId()));
    }

    // ---------- 选手端：取卷 ----------

    /** 取卷：首次访问按配置抽题并物化空卷，之后返回同一份试卷。 */
    @Transactional
    public TheoryDtos.PaperResponse paper(String eventId, UserPrincipal principal) {
        Event event = eventService.resolveEvent(eventId);
        eventService.assertViewable(event, isPrivileged(principal));
        TheoryConfig config = requireConfig(event.getId());

        List<TheoryAnswer> paper = answerRepository.findPaper(event.getId(), principal.getId());
        if (paper.isEmpty()) {
            materialize(event, config, principal);
            paper = answerRepository.findPaper(event.getId(), principal.getId());
        }

        int total = config.getSingleCount() * config.getSinglePoints()
                + config.getMultiCount() * config.getMultiPoints()
                + config.getJudgeCount() * config.getJudgePoints();
        List<TheoryDtos.PaperQuestionDto> questions = paper.stream()
                .map(a -> toPaperDto(a, config)).toList();
        return new TheoryDtos.PaperResponse(event.getId(), total, questions);
    }

    // ---------- 选手端：提交判分 ----------

    /**
     * 提交判分：SINGLE/JUDGE 全对满分；MULTI 无错选按正确比例得分、有错选清零。
     * 比赛进行期间允许重复提交覆盖；不返回正确答案，防止暴力枚举。
     */
    @Transactional
    public TheoryDtos.SubmitResultDto submit(String eventId, TheoryDtos.SubmitRequest request,
                                             UserPrincipal principal) {
        Event event = eventService.resolveEvent(eventId);
        eventService.assertSubmittable(event);
        TheoryConfig config = requireConfig(event.getId());
        List<TheoryAnswer> paper = answerRepository.findPaper(event.getId(), principal.getId());
        if (paper.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_STATE, "请先获取试卷再提交");
        }
        if (request.answers() == null || request.answers().isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_PARAM, "提交内容为空");
        }

        Map<String, TheoryAnswer> byQuestion = new LinkedHashMap<>();
        for (TheoryAnswer row : paper) {
            byQuestion.put(row.getQuestion().getId(), row);
        }
        List<TheoryDtos.AnswerResultDto> details = new ArrayList<>();
        for (TheoryDtos.AnswerItem item : request.answers()) {
            TheoryAnswer row = byQuestion.get(item.questionId());
            if (row == null) {
                throw new BusinessException(ErrorCode.INVALID_PARAM, "试卷中不存在题目 " + item.questionId());
            }
            grade(row, item.answer() == null ? List.of() : item.answer(), config);
            answerRepository.save(row);
            details.add(new TheoryDtos.AnswerResultDto(row.getQuestion().getId(),
                    row.isCorrect(), row.getPoints()));
        }

        int total = 0;
        int correctCount = 0;
        for (TheoryAnswer row : answerRepository.findPaper(event.getId(), principal.getId())) {
            total += row.getPoints();
            if (row.isCorrect()) {
                correctCount++;
            }
        }
        auditService.record("THEORY_SUBMIT", event.getName(),
                principal.getUsername() + " 得分 " + total + "（答对 " + correctCount + "/" + paper.size() + "）");
        return new TheoryDtos.SubmitResultDto(total, correctCount, paper.size(), details);
    }

    // ---------- 报告 ----------

    /** 个人能力报告：按知识点聚合得分率。 */
    @Transactional(readOnly = true)
    public TheoryDtos.ReportDto myReport(String eventId, UserPrincipal principal) {
        Event event = eventService.resolveEvent(eventId);
        TheoryConfig config = requireConfig(event.getId());
        List<TheoryAnswer> rows = answerRepository.findPaper(event.getId(), principal.getId());
        if (rows.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_STATE, "尚未生成试卷，暂无报告");
        }
        List<TheoryDtos.TagStatDto> tags = aggregateTags(rows, config);
        long earned = rows.stream().mapToLong(TheoryAnswer::getPoints).sum();
        long possible = rows.stream()
                .mapToLong(r -> config.pointsFor(r.getQuestion().getKind())).sum();
        return new TheoryDtos.ReportDto(event.getId(), earned, possible, ratio(earned, possible), tags);
    }

    /** 管理端总览：全员知识点聚合 + 每用户得分。 */
    @Transactional(readOnly = true)
    public TheoryDtos.OverviewDto overview(String eventId) {
        Event event = eventService.resolveEvent(eventId);
        TheoryConfig config = requireConfig(event.getId());
        List<TheoryAnswer> rows = answerRepository.findAllForEvent(event.getId());

        Map<String, List<TheoryAnswer>> byUser = new LinkedHashMap<>();
        for (TheoryAnswer row : rows) {
            byUser.computeIfAbsent(row.getUser().getId(), k -> new ArrayList<>()).add(row);
        }
        List<TheoryDtos.UserScoreDto> users = byUser.values().stream().map(list -> {
            long earned = list.stream().mapToLong(TheoryAnswer::getPoints).sum();
            long possible = list.stream()
                    .mapToLong(r -> config.pointsFor(r.getQuestion().getKind())).sum();
            User user = list.get(0).getUser();
            return new TheoryDtos.UserScoreDto(user.getId(), user.getUsername(),
                    user.getDisplayName(), earned, possible);
        }).sorted(Comparator.comparingLong(TheoryDtos.UserScoreDto::earned).reversed()).toList();

        return new TheoryDtos.OverviewDto(event.getId(), users.size(),
                aggregateTags(rows, config), users);
    }

    // ---------- 内部工具 ----------

    /** 抽题并物化空卷：以 (eventId, userId) 为随机种子，同一人抽取结果确定。 */
    private void materialize(Event event, TheoryConfig config, UserPrincipal principal) {
        long seed = Objects.hash(event.getId(), principal.getId());
        List<TheoryQuestion> picked = new ArrayList<>();
        for (QuestionKind kind : QuestionKind.values()) {
            int count = config.countFor(kind);
            if (count <= 0) {
                continue;
            }
            List<TheoryQuestion> pool = new ArrayList<>(
                    questionRepository.findByEventIdAndKind(event.getId(), kind));
            if (pool.size() < count) {
                throw new BusinessException(ErrorCode.INVALID_STATE,
                        kind.label() + "题库数量不足（需 " + count + " 道，现有 " + pool.size() + " 道）");
            }
            Collections.shuffle(pool, new Random(seed + kind.ordinal()));
            picked.addAll(pool.subList(0, count));
        }
        User userRef = new User();
        userRef.setId(principal.getId());
        for (TheoryQuestion question : picked) {
            TheoryAnswer row = new TheoryAnswer();
            row.setEvent(event);
            row.setUser(userRef);
            row.setQuestion(question);
            answerRepository.save(row);
        }
    }

    private void grade(TheoryAnswer row, List<Integer> given, TheoryConfig config) {
        TheoryQuestion question = row.getQuestion();
        List<Integer> answer = readInts(question.getAnswerJson());
        int full = config.pointsFor(question.getKind());
        Set<Integer> answerSet = new HashSet<>(answer);
        Set<Integer> givenSet = new HashSet<>(given);

        int earned;
        boolean correct;
        if (question.getKind() == QuestionKind.MULTI) {
            boolean anyWrong = givenSet.stream().anyMatch(i -> !answerSet.contains(i));
            if (anyWrong || givenSet.isEmpty()) {
                earned = 0;
                correct = false;
            } else if (givenSet.equals(answerSet)) {
                earned = full;
                correct = true;
            } else {
                // 无错选但漏选：按正确选项比例得分
                earned = (int) ((long) full * givenSet.size() / answerSet.size());
                correct = false;
            }
        } else {
            correct = givenSet.equals(answerSet);
            earned = correct ? full : 0;
        }
        row.setGivenJson(writeJson(new ArrayList<>(givenSet)));
        row.setPoints(earned);
        row.setCorrect(correct);
    }

    private List<TheoryDtos.TagStatDto> aggregateTags(List<TheoryAnswer> rows, TheoryConfig config) {
        Map<String, long[]> byTag = new LinkedHashMap<>();
        for (TheoryAnswer row : rows) {
            String tag = row.getQuestion().getKnowledgeTag();
            if (tag == null || tag.isBlank()) {
                tag = UNTAGGED;
            }
            long[] acc = byTag.computeIfAbsent(tag, k -> new long[4]);
            acc[0]++;                                              // 题数
            if (row.isCorrect()) {
                acc[1]++;                                          // 答对数
            }
            acc[2] += row.getPoints();                             // 已得分
            acc[3] += config.pointsFor(row.getQuestion().getKind()); // 应得分
        }
        return byTag.entrySet().stream()
                .map(e -> new TheoryDtos.TagStatDto(e.getKey(), e.getValue()[0], e.getValue()[1],
                        e.getValue()[2], e.getValue()[3], ratio(e.getValue()[2], e.getValue()[3])))
                .toList();
    }

    private void applyQuestion(TheoryQuestion question, TheoryDtos.QuestionRequest request) {
        QuestionKind kind = parseKind(request.kind());
        List<String> options = request.options() == null ? List.of() : request.options().stream()
                .filter(Objects::nonNull).map(String::trim).filter(s -> !s.isEmpty()).toList();
        List<Integer> answer = request.answer() == null ? List.of() : request.answer();

        if (request.stem() == null || request.stem().isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_PARAM, "题干不能为空");
        }
        if (kind == QuestionKind.JUDGE) {
            options = JUDGE_OPTIONS;
        }
        if (options.size() < 2) {
            throw new BusinessException(ErrorCode.INVALID_PARAM, "至少需要两个选项");
        }
        if (answer.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_PARAM, "请设置正确答案");
        }
        for (Integer index : answer) {
            if (index == null || index < 0 || index >= options.size()) {
                throw new BusinessException(ErrorCode.INVALID_PARAM, "答案下标超出选项范围");
            }
        }
        if (kind == QuestionKind.SINGLE && answer.size() != 1) {
            throw new BusinessException(ErrorCode.INVALID_PARAM, "单选题答案只能有一个");
        }
        if (kind == QuestionKind.JUDGE && answer.size() != 1) {
            throw new BusinessException(ErrorCode.INVALID_PARAM, "判断题答案只能有一个");
        }

        question.setKind(kind);
        question.setStem(request.stem().trim());
        question.setOptionsJson(writeJson(options));
        question.setAnswerJson(writeJson(answer.stream().sorted().distinct().toList()));
        question.setKnowledgeTag(request.knowledgeTag() == null || request.knowledgeTag().isBlank()
                ? null : request.knowledgeTag().trim());
        question.setDifficulty(parseDifficulty(request.difficulty()));
    }

    private TheoryDtos.AdminQuestionDto toAdminDto(TheoryQuestion q) {
        return new TheoryDtos.AdminQuestionDto(q.getId(), q.getEvent().getId(),
                q.getKind().name(), q.getKind().label(), q.getStem(),
                readStrings(q.getOptionsJson()), readInts(q.getAnswerJson()),
                q.getKnowledgeTag(), q.getDifficulty().name(), q.getDifficulty().label(),
                q.getCreatedAt());
    }

    private TheoryDtos.PaperQuestionDto toPaperDto(TheoryAnswer row, TheoryConfig config) {
        TheoryQuestion q = row.getQuestion();
        return new TheoryDtos.PaperQuestionDto(q.getId(), q.getKind().name(), q.getKind().label(),
                q.getStem(), readStrings(q.getOptionsJson()), q.getKnowledgeTag(),
                q.getDifficulty().name(), q.getDifficulty().label(),
                config.pointsFor(q.getKind()),
                row.getGivenJson() == null ? null : readInts(row.getGivenJson()));
    }

    private TheoryDtos.ConfigDto toConfigDto(TheoryConfig config, long poolSize) {
        int totalQuestions = config.totalQuestions();
        int totalPoints = config.getSingleCount() * config.getSinglePoints()
                + config.getMultiCount() * config.getMultiPoints()
                + config.getJudgeCount() * config.getJudgePoints();
        return new TheoryDtos.ConfigDto(config.getEventId(), config.getSingleCount(),
                config.getSinglePoints(), config.getMultiCount(), config.getMultiPoints(),
                config.getJudgeCount(), config.getJudgePoints(), totalQuestions, totalPoints, poolSize);
    }

    private TheoryConfig requireConfig(String eventId) {
        TheoryConfig config = configRepository.findById(eventId).orElse(null);
        if (config == null || config.totalQuestions() <= 0) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "该赛事未配置理论赛");
        }
        return config;
    }

    private boolean isPrivileged(UserPrincipal principal) {
        Role role = principal.getRole();
        return role == Role.ADMIN || role == Role.JUDGE || role == Role.TEACHER;
    }

    private void validateCounts(int count, int points, String label) {
        if (count < 0 || points < 0) {
            throw new BusinessException(ErrorCode.INVALID_PARAM, label + "数量与分值不能为负数");
        }
        if (count > 0 && points <= 0) {
            throw new BusinessException(ErrorCode.INVALID_PARAM, label + "抽题数量大于 0 时分值必须大于 0");
        }
    }

    private QuestionKind parseKind(String kind) {
        try {
            return QuestionKind.valueOf(kind == null ? "" : kind.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new BusinessException(ErrorCode.INVALID_PARAM, "题型仅支持 SINGLE / MULTI / JUDGE");
        }
    }

    private Difficulty parseDifficulty(String difficulty) {
        if (difficulty == null || difficulty.isBlank()) {
            return Difficulty.MEDIUM;
        }
        try {
            return Difficulty.valueOf(difficulty.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new BusinessException(ErrorCode.INVALID_PARAM, "难度仅支持 EASY / MEDIUM / HARD");
        }
    }

    private double ratio(long earned, long possible) {
        return possible <= 0 ? 0.0 : Math.round(earned * 10000.0 / possible) / 10000.0;
    }

    private String truncate(String value) {
        return value == null ? null : value.substring(0, Math.min(value.length(), 60));
    }

    private List<String> readStrings(String json) {
        try {
            return mapper.readValue(json, STRING_LIST);
        } catch (Exception ex) {
            return List.of();
        }
    }

    private List<Integer> readInts(String json) {
        try {
            return mapper.readValue(json, INT_LIST);
        } catch (Exception ex) {
            return List.of();
        }
    }

    private String writeJson(Object value) {
        try {
            return mapper.writeValueAsString(value);
        } catch (Exception ex) {
            throw new IllegalStateException("JSON 序列化失败", ex);
        }
    }
}
