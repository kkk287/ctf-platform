package com.example.ctf.service;

import com.example.ctf.domain.*;
import com.example.ctf.repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;

/**
 * 演示数据初始化：
 * - 用户由 Flyway V2 写入；
 * - 赛事/练习区、题目、战队、解题与审计记录使用相对当前时间生成，保证首次启动即可演示。
 * 已存在数据时跳过，容器重启不会重复写入。
 */
@Component
public class DemoDataInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoDataInitializer.class);

    private final EventRepository eventRepository;
    private final ChallengeRepository challengeRepository;
    private final HintRepository hintRepository;
    private final UserRepository userRepository;
    private final TeamRepository teamRepository;
    private final SolveRepository solveRepository;
    private final SubmissionRepository submissionRepository;
    private final AnnouncementRepository announcementRepository;
    private final FlagHasher flagHasher;
    private final FileStorageService fileStorageService;

    @Value("${app.demo.enabled:true}")
    private boolean demoEnabled;

    public DemoDataInitializer(EventRepository eventRepository, ChallengeRepository challengeRepository,
                              HintRepository hintRepository, UserRepository userRepository,
                              TeamRepository teamRepository, SolveRepository solveRepository,
                              SubmissionRepository submissionRepository,
                              AnnouncementRepository announcementRepository, FlagHasher flagHasher,
                              FileStorageService fileStorageService) {
        this.eventRepository = eventRepository;
        this.challengeRepository = challengeRepository;
        this.hintRepository = hintRepository;
        this.userRepository = userRepository;
        this.teamRepository = teamRepository;
        this.solveRepository = solveRepository;
        this.submissionRepository = submissionRepository;
        this.announcementRepository = announcementRepository;
        this.flagHasher = flagHasher;
        this.fileStorageService = fileStorageService;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (!demoEnabled || eventRepository.count() > 0) {
            return;
        }
        User admin = userRepository.findByUsername("admin").orElse(null);
        User alice = userRepository.findByUsername("alice").orElse(null);
        User bob = userRepository.findByUsername("bob").orElse(null);
        User carol = userRepository.findByUsername("carol").orElse(null);
        if (admin == null || alice == null || bob == null || carol == null) {
            log.warn("演示用户缺失，跳过演示数据初始化（请确认 Flyway 迁移已执行）");
            return;
        }

        LocalDateTime now = LocalDateTime.now();

        Team teamA = new Team();
        teamA.setName("0x00sec");
        teamA.setInviteCode("0X00SEC1");
        teamA.setCaptain(alice);
        teamRepository.save(teamA);
        Team teamB = new Team();
        teamB.setName("Byte Raiders");
        teamB.setInviteCode("BYTERAID");
        teamB.setCaptain(carol);
        teamRepository.save(teamB);
        alice.setTeam(teamA);
        bob.setTeam(teamA);
        carol.setTeam(teamB);
        userRepository.save(alice);
        userRepository.save(bob);
        userRepository.save(carol);

        Event competition = new Event();
        competition.setName("秋季夺旗赛 2026");
        competition.setDescription("限时赛事：2026 秋季 CTF 线上赛，题目按赛程开放，结束后停止计分。");
        competition.setType(EventType.COMPETITION);
        competition.setStartAt(now.minusDays(2));
        competition.setEndAt(now.plusDays(5));
        eventRepository.save(competition);

        Event practice = new Event();
        practice.setName("常驻练习区");
        practice.setDescription("长期开放练习区：随时刷题、独立计分与独立榜单，不与赛事分数混排。");
        practice.setType(EventType.PRACTICE);
        eventRepository.save(practice);

        Challenge misc = challenge(competition, "欢迎来到 CTF", Category.MISC, Difficulty.EASY, 100, 20, 5,
                "flag{welcome_to_ctf_2026}", "平台签到题。提交 flag 熟悉流程，注意 flag 需完整包含大括号。");
        hint(misc, "flag 格式为 flag{...}，区分大小写。", 10);

        Challenge web = challenge(competition, "登录框的秘密", Category.WEB, Difficulty.MEDIUM, 150, 30, 8,
                "flag{sql_1nject10n_b4s1cs}",
                "某内部系统的登录接口疑似存在 SQL 注入。请分析参数拼接方式，从数据库中取出隐藏 flag。\n\n附件包含接口说明与示例请求。");
        hint(web, "先判断是字符型还是数字型注入。", 10);
        hint(web, "尝试 UNION SELECT 读取隐藏表。", 25);

        Challenge crypto = challenge(competition, "小公钥的秘密", Category.CRYPTO, Difficulty.MEDIUM, 200, 40, 10,
                "flag{rsa_sm4ll_e_attack}",
                "服务端使用了过小的 RSA 公钥指数 e=3，且没有填充。已知模数 n 与密文 c，请恢复明文。");
        hint(crypto, "当 m^3 < n 时，直接对 c 开三次方即可。", 20);

        Challenge pwn = challenge(practice, "栈溢出入门", Category.PWN, Difficulty.HARD, 250, 50, 15,
                "flag{r3t2w1n_4nd_r0p}",
                "本题不提供动态靶机，附件为目标程序说明与外部靶机地址。请通过栈溢出覆盖返回地址获取 shell。\n\n注意：平台不负责靶机实例下发，靶机需自行启动或使用文档中的外部地址。");
        hint(pwn, "检查二进制保护：RELRO、Canary、NX、PIE。", 30);

        Challenge reverse = challenge(practice, "Baby Patch", Category.REVERSE, Difficulty.EASY, 120, 20, 6,
                "flag{rev3rs3_e4sy_p4tch}",
                "附件是一个校验逻辑的伪代码说明。找到被篡改的分支条件并还原出正确 flag。");
        hint(reverse, "关注比较前的常量与分支跳转。", 15);

        attach(pwn, "pwn-note.txt", "pwn-note.txt",
                "本题为外部靶机模式。\n靶机地址示例：nc 127.0.0.1 31337\n请使用 checksec 检查保护后构造 ROP 链。\n");
        attach(reverse, "baby-patch.txt", "baby-patch.txt",
                "int check(char *s) {\n    return strcmp(s, \"flag{rev3rs3_e4sy_p4tch}\") == 0;\n}\n");

        // 演示解题记录：赛事榜与练习榜分开统计
        seedSolve(alice, misc, 120, 100, 20, 0, BloodRank.FIRST, now.minusHours(26), "10.0.0.10", true);
        seedSolve(bob, misc, 105, 95, 10, 0, BloodRank.SECOND, now.minusHours(20), "10.0.0.10", true);
        seedSolve(carol, crypto, 240, 200, 40, 0, BloodRank.FIRST, now.minusHours(18), "10.0.0.30", true);
        seedSolve(alice, web, 180, 150, 30, 0, BloodRank.FIRST, now.minusHours(12), "10.0.0.10", true);
        seedSolve(bob, pwn, 300, 250, 50, 0, BloodRank.FIRST, now.minusDays(3), "10.0.0.20", true);
        seedSolve(carol, reverse, 144, 120, 24, 0, BloodRank.FIRST, now.minusDays(2), "10.0.0.30", true);
        seedSolve(alice, reverse, 125, 114, 11, 0, BloodRank.SECOND, now.minusDays(1), "10.0.0.10", true);

        Submission wrong = new Submission();
        wrong.setUser(alice);
        wrong.setChallenge(crypto);
        wrong.setSubmittedHash(flagHasher.submissionHash("flag{wrong_demo}"));
        wrong.setResult(SubmissionResult.WRONG);
        wrong.setIp("10.0.0.10");
        wrong.setUserAgent("Mozilla/5.0 Demo Browser");
        wrong.setCreatedAt(now.minusHours(30));
        submissionRepository.save(wrong);

        Announcement announcement = new Announcement();
        announcement.setTitle("赛事公告：公平竞赛");
        announcement.setContent("秋季夺旗赛进行中，禁止交换 flag；练习区长期开放且使用独立榜单。发现异常提交可在管理后台审计。");
        announcement.setCreatedBy(admin);
        announcementRepository.save(announcement);

        log.info("演示数据初始化完成：2 个赛事/练习区、5 道题目、2 支战队、7 条解题记录");
    }

    private Challenge challenge(Event event, String name, Category category, Difficulty difficulty,
                                int initial, int min, int decay, String flag, String description) {
        Challenge challenge = new Challenge();
        challenge.setEvent(event);
        challenge.setName(name);
        challenge.setDescription(description);
        challenge.setCategory(category);
        challenge.setDifficulty(difficulty);
        challenge.setInitialPoints(initial);
        challenge.setMinPoints(min);
        challenge.setDecayPoints(decay);
        String salt = flagHasher.newSalt();
        challenge.setFlagSalt(salt);
        challenge.setFlagHash(flagHasher.hash(flag, salt));
        challenge.setVisible(true);
        challengeRepository.save(challenge);
        return challenge;
    }

    private void hint(Challenge challenge, String content, int cost) {
        Hint hint = new Hint();
        hint.setChallenge(challenge);
        hint.setContent(content);
        hint.setCost(cost);
        hintRepository.save(hint);
    }

    private void attach(Challenge challenge, String filename, String contentType, String content) {
        FileStorageService.StoredFile stored = fileStorageService.storeBytes(filename,
                content.getBytes(StandardCharsets.UTF_8), contentType);
        challenge.setAttachmentStoredName(stored.storedName());
        challenge.setAttachmentOriginalName(filename);
        challenge.setAttachmentContentType(contentType);
        challenge.setAttachmentSize(stored.size());
        challengeRepository.save(challenge);
    }

    private void seedSolve(User user, Challenge challenge, int finalPoints, int base, int bonus, int hintCost,
                           BloodRank bloodRank, LocalDateTime solvedAt, String ip, boolean createSubmission) {
        Submission submission = null;
        if (createSubmission) {
            submission = new Submission();
            submission.setUser(user);
            submission.setChallenge(challenge);
            // 演示环境已知 flag 明文用于生成审计摘要，生产环境只存哈希
            submission.setSubmittedHash(flagHasher.submissionHash(demoFlagOf(challenge, user)));
            submission.setResult(SubmissionResult.CORRECT);
            submission.setIp(ip);
            submission.setUserAgent("Mozilla/5.0 Demo Browser");
            submission.setCreatedAt(solvedAt.minusMinutes(1));
            submissionRepository.save(submission);
        }
        Solve solve = new Solve();
        solve.setUser(user);
        solve.setChallenge(challenge);
        solve.setSubmissionId(submission == null ? null : submission.getId());
        solve.setBasePoints(base);
        solve.setBloodBonus(bonus);
        solve.setHintCost(hintCost);
        solve.setFinalPoints(finalPoints);
        solve.setBloodRank(bloodRank);
        solve.setSolvedAt(solvedAt);
        solveRepository.save(solve);
    }

    /**
     * 仅为演示数据的审计摘要生成；平台运行时不保留 flag 明文。
     */
    private String demoFlagOf(Challenge challenge, User user) {
        return switch (challenge.getName()) {
            case "欢迎来到 CTF" -> "flag{welcome_to_ctf_2026}";
            case "登录框的秘密" -> "flag{sql_1nject10n_b4s1cs}";
            case "小公钥的秘密" -> "flag{rsa_sm4ll_e_attack}";
            case "栈溢出入门" -> "flag{r3t2w1n_4nd_r0p}";
            case "Baby Patch" -> "flag{rev3rs3_e4sy_p4tch}";
            default -> "flag{demo_" + user.getUsername() + "}";
        };
    }
}
