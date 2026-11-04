# CTF 平台 · 设计与实现计划（PLAN）

## 1. 目标与边界

构建一个 Java 实现的 CTF 平台，要求"本地可跑 + GitHub 可部署"，默认零外部依赖（H2 文件库），同时支持 MySQL / PostgreSQL。核心业务为赛事与练习双模式下的题目发布、flag 判分、动态积分与双榜排行。

### 明确不做（避免过度设计）

| 不做 | 替代方案 |
|---|---|
| Docker 动态靶机下发 / 回收 | 靶机类题目使用"附件 + 描述中的外部地址"，由出题人自行维护 |
| CTFd 插件 / 主题 / API 兼容 | 使用本项目自有 REST API 与静态前端 |
| OAuth / 第三方登录 | 账号密码 + BCrypt + JWT |
| 复杂风控（设备指纹、行为模型） | 留存 IP/UA + 同 IP 多账号正确提交告警 |
| 分布式缓存 / 消息队列 | 单实例部署；判分使用数据库悲观锁保证一致性 |

## 2. 角色与权限

| 角色 | 能力 |
|---|---|
| `USER` | 注册/登录、浏览赛事与题目、提交 flag、解锁提示、创建/加入/退出战队、查看个人榜/战队榜、我的战绩 |
| `ADMIN` | 上述全部 + 管理后台：题目 CRUD（flag/附件/提示）、赛事 CRUD、用户封禁、公告、提交审计、异常告警 |

- 接口级：`SecurityConfig` 放行登录/注册/静态资源/Swagger/健康检查，其余 `/api/**` 需认证；`/api/admin/**` 需 `ROLE_ADMIN`。
- 方法级：`AdminController` 类级 `@PreAuthorize("hasRole('ADMIN')")`；战队写操作 `hasRole('USER')`。
- 数据级：非管理员只能看到 `visible=true` 且所属赛事已开始的题目；被封禁账号登录返回 `4033`。

## 3. 业务流程与状态机

### 3.1 赛事状态（由起止时间实时派生，不落库）

```
PRACTICE（练习区）：任意时间均为 ONGOING，题目长期可见可答
COMPETITION：
  now < startAt            -> NOT_STARTED（未开始：题目对普通用户不可见、不可答）
  startAt <= now <= endAt  -> ONGOING（进行中：可见可答）
  now > endAt              -> ENDED（已结束：可查看题目，不可提交）
```

### 3.2 题目可见性与提交判定

```
visible=false                  -> 普通用户不可见（管理员后台仍可见）
COMPETITION 且 NOT_STARTED     -> 不可见（管理员可见）
COMPETITION 且 ENDED           -> 可见，但提交返回 EVENT_ENDED(4032)
PRACTICE                       -> 一直可见、可提交
```

### 3.3 判分流程（`ChallengeService.submit`）

```
1. 校验赛事可提交（未开始 4031 / 已结束 4032）
2. 加锁读取题目（PESSIMISTIC_WRITE），保证并发下积分与血奖计算一致
3. 已解出 -> ALREADY_SOLVED(4009)
4. 防爆破：窗口内错误次数 >= 上限 -> RATE_LIMITED(4290)；距上次错误不足冷却 -> COOLDOWN(4291)
5. 计算加盐哈希并与题目 flagHash 比对（不落明文）
6. 错误 -> 写 Submission(WRONG) 后抛出 WRONG_FLAG(4004)（事务不整体回滚，保证审计留存）
7. 正确 -> 统计当前解出人数 n：
     base      = max(minPoints, initialPoints - decayPoints * n)
     blood     = 第 1/2/3 个解出 => base * 20% / 10% / 5%
     hintCost  = 已解锁提示的分值之和
     final     = max(0, base + blood - hintCost)
   写 Submission(CORRECT) + Solve（含 basePoints/bloodBonus/hintCost/finalPoints/bloodRank）
8. 返回 {正确, 基础分, 血奖, 提示扣分, 最终得分, 当前总分, 当前名次}
```

### 3.4 排行榜规则

- 按 `eventId` 隔离：赛事榜与练习区榜分开统计，不串分。
- 个人榜：`sum(Solve.finalPoints)` 降序；同分按"最后一次得分时间"升序（先到者靠前）。
- 战队榜：**成员得分求和，同一题目只计最高分**（A、B 同解一题只记一次较高分）；无战队用户不计入战队榜。
- 实时性：每次请求从数据库聚合，解题后立即反映；前端 30 秒自动刷新。
- 提供 top N（默认 20，上限受控）与"我的名次"；另提供最近解题动态（谁在何时解出哪题、血奖）。

## 4. 数据模型

表名与关键字段（Flyway `V1__init_schema.sql`，UUID 字符串主键，兼容 H2/MySQL/PG）：

| 表 | 字段 | 说明 |
|---|---|---|
| `app_user` | id, username(uniq), password, display_name, role, team_id, banned, created_at | 账号；password 为 BCrypt |
| `teams` | id, name(uniq), invite_code(uniq), captain_id, created_at | 战队；队长为外键 |
| `events` | id, name, description, type(COMPETITION/PRACTICE), start_at, end_at, created_at | 赛事/练习区 |
| `challenges` | id, event_id, name, description, category, difficulty, initial_points, min_points, decay_points, flag_hash, flag_salt, visible, attachment_* , created_at, updated_at | 题目；flag 仅哈希 |
| `hints` | id, challenge_id, content, cost | 提示与扣分代价 |
| `hint_unlocks` | id, user_id, hint_id, challenge_id, cost, created_at (uniq user+hint) | 提示解锁记录 |
| `submissions` | id, user_id, challenge_id, submitted_hash, ip, user_agent, result, created_at | 全量提交审计 |
| `solves` | id, user_id, challenge_id, base_points, blood_bonus, hint_cost, final_points, blood_rank, solved_at (uniq user+challenge) | 解题与积分快照 |
| `announcements` | id, title, content, created_by, created_at | 公告 |

索引：`submissions(challenge_id, ip, result)`、`submissions(user_id, challenge_id, created_at)`、`solves(challenge_id, solved_at)`、`solves(user_id)`。

## 5. 接口清单

详见 [README 接口一览](../README.md#接口一览)，模块划分：

- 认证：`/api/auth/*`
- 赛事与公告：`/api/events`、`/api/announcements`
- 题目与判分：`/api/challenges/*`
- 排行与战绩：`/api/scoreboard`、`/api/scoreboard/recent`、`/api/me/profile`
- 战队：`/api/teams/*`
- 管理：`/api/admin/*`（题目/赛事/用户/审计/异常/公告）

统一响应：`{code, message, data}`。主要业务错误码：

| code | HTTP | 含义 |
|---|---|---|
| 4001 | 409 | 状态非法（如删除仍有题目的赛事） |
| 4002 | 400 | 参数校验失败 |
| 4004 | 400 | flag 错误 |
| 4009 | 409 | 题目已解出 / 重复提交 |
| 4010/4011 | 401 | 未登录 / 账号或密码错误 |
| 4030 | 403 | 无权限 |
| 4031/4032 | 403 | 赛事未开始 / 已结束不可提交 |
| 4033 | 403 | 账号被封禁 |
| 4040 | 404 | 资源不存在 |
| 4090 | 409 | 唯一性冲突（用户名/战队名等） |
| 4290/4291 | 429 | 错误次数超限 / 冷却中 |

## 6. 页面清单（Vue3 三入口）

构建产物位于 `src/main/resources/static/vue`，根路径与旧版原生入口统一重定向到 Vue 选手端。

| 入口 | 路径 | 功能 |
|---|---|---|
| 选手端 | `/vue/player.html` | 登录/注册、赛事与练习区切换、题目列表、题目详情与提交、提示解锁、排行榜、个人战绩与战队 |
| 管理端 | `/vue/admin.html` | 题目/赛事/用户/靶机实例/提交审计/异常告警/公告/AWD 轮次 |
| 大屏端 | `/vue/screen.html` | 排行榜与 AWD 态势大屏，未登录可看匿名只读数据 |

根路径 `/`、`/index.html`、`/login.html` 均 302 到 `/vue/player.html`；旧的原生页面（`challenge.html`、`scoreboard.html`、`profile.html`、`admin.html`）已下线，访问返回 404。

全部为本地静态资源，无外网 CDN；深色科技风 + 等宽字体点缀，中文文案。

## 7. 非功能设计

### 安全

- JWT 无状态（`Authorization: Bearer`），密钥来自 `JWT_SECRET`；未配置时启动生成临时随机密钥并 WARN（仅适合演示）。
- 密码 BCrypt；flag 加盐 SHA-256，数据库无明文 flag；提交内容仅存哈希前缀用于审计。
- 统一异常处理不泄漏堆栈；CORS 未开放；CSRF 因无 Cookie 会话而关闭。
- 附件上传：UUID 重命名、路径穿越防护、大小限制（默认 20MB）、原文件名仅存元数据。
- 防爆破：窗口错误计数 + 冷却；正确提交不受限流影响。
- 敏感配置全部走环境变量，`.env` 不入库，仓库仅提供 `.env.example`。

### 性能

- 排行榜聚合使用单条 `join fetch` 查询 + 内存聚合，适合中小规模赛事（万级解题记录内）。
- 判分采用悲观锁避免并发导致血奖/积分错算；H2 单机文件库足够演示与小型比赛。
- 静态资源随 JAR 发布，无外部请求；前端接口并发加载（`Promise.all`）。
- 数据库连接池默认 10，可用 `DB_POOL_SIZE` 调整。

### 可维护

- 分层：controller / service / repository / domain / dto / config / security / common。
- Flyway 管理 schema，`ddl-auto=validate` 防止实体与表漂移。
- 统一 `ApiResponse` 与 `ErrorCode`，前端 `common.js` 统一处理 401 与业务错误。
- 演示数据初始化幂等（仅当 `events` 为空时写入）。

## 8. 需求 - 实现映射

| 需求 | 实现位置 | 状态 |
|---|---|---|
| 注册/登录、JWT、BCrypt、USER/ADMIN | `AuthService`、`JwtService`、`JwtAuthenticationFilter`、`SecurityConfig`、`V2__seed_users.sql` | 已实现 |
| 战队创建/加入/退出、队长管理 | `TeamService`、`TeamController`、`teams` 表 | 已实现 |
| 赛事/练习双模式、时间控制 | `Event.type`、`EventService.status`、`canView/canSubmit` | 已实现 |
| 题目分类/难度/分值/附件/提示/可见性 | `Challenge`、`Hint`、`FileStorageService`、`AdminController` | 已实现 |
| 服务端哈希判分、错误审计 | `FlagHasher`、`ChallengeService.submit` | 已实现 |
| 动态积分 + 最低分 + 一二三血 | `ScoringPolicy`、`Solve` 快照 | 已实现 |
| 提示扣分 | `HintUnlock`、`unlockHint`、`finalPoints` | 已实现 |
| 限流与冷却 | `SubmissionRepository` 计数 + `ChallengeService` 校验 | 已实现 |
| 重复提交/已解出错误码 | `ErrorCode.ALREADY_SOLVED` | 已实现 |
| 个人榜/战队榜、赛事隔离 | `ScoreboardService` | 已实现 |
| top N、我的名次、最近动态 | `/api/scoreboard`、`/api/scoreboard/recent` | 已实现 |
| 管理后台（题目/赛事/用户/审计/公告） | `AdminController`、`AdminService`、`/vue/admin.html` | 已实现 |
| 防作弊（IP/UA + 同 IP 多账号告警） | `Submission`、`findSharedIpGroups`、`/api/admin/anomalies` | 已实现 |
| 统一响应/异常/校验/分页/OpenAPI/Actuator | `common/*`、`GlobalExceptionHandler`、springdoc、actuator | 已实现 |
| H2 默认 + MySQL/PG profile | `application-h2/mysql/postgres.yml` | 已实现 |
| Docker + compose + 健康检查 + 数据卷 | `Dockerfile`、`docker-compose.yml`、`deploy/docker-compose.server.yml` | 已实现 |
| GitHub Actions CI + GHCR + SSH 部署 | `.github/workflows/ci.yml`、`deploy.yml` | 已实现 |
| 前端三入口、零 CDN | `web/src`（构建产物 `static/vue/*`），根路径重定向 | 已实现 |
| 单元 + 集成测试 | `ScoringPolicyTest`、`FlagHasherTest`、`CtfFlowIntegrationTest` | 已实现，`mvnw test` 全绿（见 verification） |

## 9. 风险与后续可选增强

- 大规模并发下排行榜全量聚合有性能上限，可加缓存或物化统计表。
- 附件与数据库同卷（`ctf-platform-data`），需自行做服务器级备份（`docker run --rm -v ctf-platform-data:/data ...`）。
- JWT 未实现刷新/吊销；封禁通过每次请求加载用户信息即时生效，但已签发 token 仍可访问直到过期（当前实现每次请求查询用户，封禁即时生效）。
- 若需要 HTTPS，建议前置 Nginx/Caddy 反向代理（`forward-headers-strategy: framework` 已开启）。
