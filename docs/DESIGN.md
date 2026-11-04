# 网络安全竞赛平台 · 总体设计（v2.0 重构）

> 依据《网络安全竞赛平台研发需求说明》《竞赛平台视觉规范》与《技术实现要求说明》重新设计。
> 本文档是后续分期实施的基准，标识每条需求的归属模块与落地方式。

## 1. 需求到架构的映射

| 需求域 | 归属模块 | 说明 |
| --- | --- | --- |
| 统一赛制引擎、题库、权限、虚拟化调度 | 基础底座 `platform-core` | CTF/AWD/AWDP/理论赛/场景实操/CFS 共享 |
| CTF 答题判分、动静 flag、writeup、反作弊 | `ctf-service` | 千人级并发 |
| AWD/AWDP 轮次攻防、check 脚本、靶场隔离 | `awd-service` | 百队级并发 |
| 排行榜、3D 态势大屏、时序回放、靶机监控 | `situation-service` | WebSocket 秒级推送 |
| 备案归档、报表导出、审计 | `platform-core` + 各子系统 | 一键备案报告 |
| SSO/LDAP/第三方登录、手机号开放查询 | `platform-core` 开放接口层 | Token 映射、脱敏、限流 |

## 2. 技术选型（对齐技术约束）

| 层 | 选型 | 约束来源 |
| --- | --- | --- |
| 后端语言 | Java 17 + Spring Boot 3.2 | 限定 Java/Python，沿用现有工程 |
| 前端 | Vue 3 + Vite + Element Plus | 限定 Vue2/Vue3；替换现有原生前端 |
| 关系库 | MySQL 8（每场比赛逻辑隔离，独立 Schema 命名空间） | 指定 |
| 缓存 | Redis 7（会话、排行榜 ZSet、限流、热点赛事） | 指定 |
| 非结构化 | MongoDB 6（writeup 附件元数据、答题记录、日志、提交代码、回放时序事件） | 指定 |
| 实时推送 | WebSocket（STOMP）+ Redis Pub/Sub 广播 | 秒级榜单/公告 |
| 靶场调度 | Docker（docker-java），接口抽象 `RangeOrchestrator`，LVP 预留 SPI | 需求要求 Docker+LVP |
| 时序回放 | MongoDB  capped/TTL 集合存事件流（规模可控；如需可换 InfluxDB） | 时序库要求 |
| 接口文档 | springdoc-openapi（Swagger UI） | 指定 |
| 部署 | docker-compose 起步，K8s 清单预留；单台 64GB 物理机可承载 | 指定 |

## 3. 总体架构

```
┌───────────────────────── 接入层 ─────────────────────────┐
│ Nginx(静态/反代/WSS)  ·  Vue3 选手端 · Vue3 管理端 · 大屏端 │
└───────────────┬───────────────────────────┬─────────────┘
                │ REST + WebSocket          │ 开放 API(签名+限流)
┌───────────────▼───────────────────────────▼─────────────┐
│ 业务层（Spring Boot 多模块单体起步，可按模块拆服务）        │
│  platform-core：用户/RBAC+ABAC/题库/赛制引擎/通知/审计/备案 │
│  ctf-service：题目/动静flag/判分/writeup/理论赛/场景实操    │
│  awd-service：轮次状态机/check脚本/攻防计分/靶场分配        │
│  situation-service：榜单/大屏聚合/回放/靶机监控            │
│  range-orchestrator：容器拉起/重置/销毁/VLAN(宿主机网络)    │
├─────────────────────────────────────────────────────────┤
│ 数据层：MySQL(结构化) · Redis(缓存/会话/限流/榜) · MongoDB  │
│         (非结构化/日志/回放) · 共享存储(附件/流量包/修补包)   │
└─────────────────────────────────────────────────────────┘
```

工程结构（Maven 多模块，保持单容器可部署）：

```
ctf-platform/
├── platform-core/        # 用户、权限、题库、赛制引擎、通知、审计、开放API
├── ctf-service/          # CTF/理论赛/场景实操
├── awd-service/          # AWD/AWDP 轮次与计分
├── situation-service/    # 榜单/大屏/回放/监控
├── range-orchestrator/   # 靶场调度（Docker 实现 + LVP SPI）
├── app/                  # 启动模块（聚合打包为单个可执行 jar）
└── web/                  # Vue3 前端（选手端/管理端/大屏端三入口）
```

## 4. 权限模型（RBAC + ABAC 混合）

- 角色（RBAC 静态授权）：`ADMIN` 管理员、`JUDGE` 裁判（暂停/封号/加分/强制结束/争议裁决）、`TEACHER` 教师（组卷/练习场运营）、`PLAYER` 参赛队员、`OBSERVER` 观摩用户（大屏/赛后数据）。
- ABAC 动态约束：以「比赛命名空间」为属性域——用户仅能访问其报名/被授权的比赛数据；裁判仅能操作其执裁场次；教师仅能管理其课程/练习场。
- 实现：`@PreAuthorize` + 自定义 `ContestPermissionEvaluator`，比赛级数据全部携带 `contest_id`（命名空间）字段，SQL 层强制过滤。

## 5. 数据模型设计

### 5.1 MySQL（结构化，核心表）

```
users(id, username, phone, email, password_hash, display_name, role,
      banned, totp_secret, third_party_bindings(json), created_at)
teams(id, contest_id, name, invite_code, captain_id, logo_url, status, created_at)
contests(id, name, type[CTF|AWD|AWDP|THEORY|SCENARIO|CFS], mode[PERSONAL|TEAM],
         namespace, status[DRAFT|REGISTERING|NOT_STARTED|RUNNING|SOFT_HALTED|
         HARD_HALTED|ENDED|ARCHIVED], start_at, end_at, max_participants,
         allow_late_join, template_id, switch_public_network, created_by)
registrations(id, contest_id, subject_type[USER|TEAM], subject_id,
              status[PENDING|APPROVED|REJECTED|QUIT], created_at)
question_banks(id, type[THEORY|CTF|AWD_RANGE|SCENARIO], name, owner, version)
questions(id, bank_id, kind[SINGLE|MULTI|JUDGE|FLAG|DYNAMIC_FLAG|SCENARIO],
          stem, options(json), answer_meta(json), knowledge_tags, difficulty,
          ext(json)            -- 赛制特有字段统一进 ext 扩展字段
          , writeup_required, created_at)
challenges(id, contest_id, question_id, initial_points, min_points, decay,
           first_blood_mode[ONCE|DECAY], visible, instance_image,
           instance_port, instance_ttl_minutes, sort)
flags(id, challenge_id, subject_id, flag_hash, flag_salt, expires_at)  -- 动态flag
submissions(id, contest_id, user_id, team_id, challenge_id, payload,
            result, points, ip, device_fp, risk_score, created_at)
solves(id, contest_id, user_id, team_id, challenge_id, points, blood_rank, solved_at)
writeups(id, contest_id, challenge_id, user_id, team_id, title, mongo_doc_id,
         submitted_at, accepted)
instances(id, contest_id, challenge_id, owner_type[USER|TEAM], owner_id,
          container_id, vlan_id, host, port, status, expires_at, created_at)
awd_rounds(id, contest_id, round_no, status, started_at, ended_at)
awd_scores(id, contest_id, round_no, team_id, attack_points, defense_points,
           check_passed, detail(json))
announcements(id, contest_id, title, content_md, target[ALL|TEAM|USER],
              target_ids(json), channels(json), created_by, created_at)
audit_logs(id, actor_id, actor_role, action, target, detail(json), ip, created_at)
contest_archives(id, contest_id, report_path, generated_at, generated_by)
```

### 5.2 Redis（键规约）

```
sess:{token}                    会话（TTL 滑动）
3rd:{thirdToken} -> sessToken   第三方 Token 映射（SSO）
rank:{contestId}                ZSet 总分榜（member=team/user）
rank:{contestId}:stage:{n}      阶段榜
ratelimit:{api}:{key}           令牌桶计数
flag:{challengeId}:{subjectId}  动态 flag 缓存（写穿 MySQL flags 表）
notify:{userId}                 待推送队列（Pub/Sub 广播到各节点）
```

### 5.3 MongoDB（非结构化）

```
writeup_docs    { _id, contestId, challengeId, userId, markdown, attachments[] }
answer_records  { contestId, userId, questionId, answer, correct, ts }   -- 理论赛
submit_payloads { submissionId, code/payload 原文 }
replay_events   { contestId, ts, type, payload }                          -- 大屏时序回放(TTL)
op_logs         管理/靶场/推送审计镜像
```

## 6. 关键子系统设计

### 6.1 CTF 子系统

- **动态 flag**：申请靶机时按 `flag{md5(secret+challengeId+subjectId)}` 生成，注入容器环境变量并落 `flags` 表；判分先查动态 flag，未命中再比静态哈希。
- **判分链路**：限流（Redis 令牌桶）→ 冷却 → 动态/静态比对 → 落审计（错误也落，`noRollbackFor`）→ 动态积分 + 首杀奖励（一次性/递减可配）→ Redis ZSet 更新 → WebSocket 广播。
- **理论赛**：单选/多选/判断；随机抽题（按知识点+难度权重）；多选部分得分（对选项按比例、错选清零）；赛后输出班级/个人能力分析报告（按知识点聚合正确率）。
- **场景实操**：题目配置镜像后，前端一键拉起实例（复用 range-orchestrator），提交关键证据 flag 判分。
- **黑灯模式**：比赛配置开关，开启后隐藏榜单/解题动态，仅个人可见自身得分。
- **反作弊（烽火台）**：动态 flag 外泄检测（A 队提交 B 队 flag 直接告警）、答题时序与相似度分析（提交间隔分布、多账号相似 IP/设备指纹）、风控分写入 submission.risk_score；裁判复核台支持一键封号，全部留审计日志；流量分析（识别爆破/数据外发/Web Payload）作为独立探针服务对接告警总线。
- **writeup**：赛后开放提交，Markdown + 附件存 MongoDB，可沉淀入题库。

### 6.2 AWD/AWDP 子系统

- **轮次状态机**：`ROUND_PREPARE → ROUND_RUNNING(刷新flag+开放提交) → ROUND_CHECK(check脚本) → ROUND_SETTLE(计分)`，由调度器驱动；支持维护窗口。
- **计分**：攻击分（提交他队动态 flag 成功）+ 防御分（check 脚本验证服务可用且漏洞已修）；check 脚本赛前由裁判预置，支持人工争议裁决改分（写审计）。
- **靶场**：每队独立 Namespace + VLAN（宿主机网桥/iptables 规则，orchestrator 调 Docker network）；支持拉起/重置/销毁/快照；容器资源限额 CPU/内存/磁盘。
- **AWDP 增强**：在 AWD 基础上开启流量采集（pcap 落共享存储）与攻击可视化事件流（推大屏）。
- **内网/公网一键切换**：比赛级开关，切换时 orchestrator 重绑端口映射与防火墙规则。

### 6.3 态势感知子系统

- **榜单**：Redis ZSet 秒级读取；总分榜/阶段榜/首杀榜；TopN 视图；导出 CSV/XLSX；首杀特殊标识。
- **大屏**：Vue3 独立入口；ECharts（趋势曲线/解题动态）+ 3D 地理飞线（ECharts-GL / three.js，地理底图投射靶机位置，攻击轨迹飞线动画）；1V1 攻击事件 WebSocket 实时推送；1080P/2K/4K 自适应（rem + 缩放容器）。
- **时序回放**：事件流写 `replay_events`，回放端按时间轴拉取，支持暂停/倍速（0.5x/1x/2x/4x），可导出素材。
- **靶机监控**：实例心跳 + cAdvisor/自采指标（CPU/内存/网络）、归属战队、check 通过率；大屏支持重启/重置/恢复初始，操作全部审计，异常自动告警。

### 6.4 靶场调度（range-orchestrator）

```
interface RangeOrchestrator {
  Instance start(InstanceSpec spec);   // 镜像/端口/资源限额/VLAN/TTL/env(动态flag)
  void stop(String instanceId);
  void reset(String instanceId);       // 销毁并按快照重建
  InstanceStatus status(String instanceId);
}
```

- 默认实现 `DockerOrchestrator`（docker-java 连宿主 docker.sock，独立 bridge 网络 + 标签管理）。
- `NoopOrchestrator` 用于无 Docker 环境与测试。
- 预留 `LvpOrchestrator` SPI（轻量虚拟机），配置切换。
- 并发护栏：全局/每用户/每队并发上限；TTL 到期由调度器扫描回收；拉起目标 ≤5–10 分钟、销毁 ≤5 分钟（镜像预热 + 并发池）。

### 6.5 通知与告警

- 两级订阅：用户订阅 + 赛事订阅；通道：站内信、WebSocket 弹窗、邮件、短信网关（可配置外部集成，默认空实现 + 日志）。
- 触发点：开赛提醒、首杀、战况、轮次切换、被攻击、得分变动、靶机异常。

## 7. 集成与开放接口

### 7.1 第三方登录 / SSO

```
POST /api/auth/third-party   { channel, thirdToken }
  1. 调第三方接口校验 thirdToken
  2. 拉取第三方用户信息 → 绑定/创建本地账号
  3. 生成本地会话 token，写入 Redis: 3rd:{thirdToken} -> sess:{token}
  4. 支持会话过期、Token 刷新（POST /api/auth/refresh）、登出销毁（DELETE /api/auth/logout）
```

- LDAP：`spring-ldap` 同步账号（定时任务 + 登录时回源校验），可配置开关。

### 7.2 开放查询接口（手机号主索引）

```
GET /open-api/v1/users/{phone}/contest-data
  Header: X-App-Key / X-Sign(HMAC-SHA256(appSecret, ts+nonce+body))
  - 权限：仅授权 appKey 可访问，字段白名单返回（脱敏：证件/手机号掩码）
  - 限流：Redis 令牌桶按 appKey 维度
  - 审计：每次调用落 op_logs
```

### 7.3 通用技术要求

- 全局请求日志拦截器：调用方、入参、返回、耗时、错误。
- 统一 `ApiResponse<T>` + `ErrorCode` + 全局异常处理（沿用 v1.0 规范）。
- Swagger/OpenAPI 输出；MQTT 接入作为可配置适配器（默认关闭）。

## 8. 视觉规范落地（前端 Design Tokens）

```css
:root {
  --color-primary: #295FFF; --color-primary-dark: #1b48e8; --color-primary-light: #e8eeff;
  --color-success: #00b42a; --color-warning: #ff7d00; --color-error: #f53f3f;
  --color-disabled: #c9cdd4;
  --text-title: #1d2129; --text-body: #4e5969; --text-secondary: #86909c; --text-placeholder: #c9cdd4;
  --bg-page: #ffffff; --bg-card: #f7f8fa; --border: #e5e6eb;
  --font-family: "PingFang SC","Microsoft YaHei","Helvetica Neue",Helvetica,Arial,sans-serif;
  /* 字号：24/20/18/16/14/13/12，对应行高 32/28/26/24/22/20/18 */
}
```

- 主色仅用于重点按钮、导航高亮、题号选中、进度条、关键标识；大面积背景禁用主色。
- 状态色严格语义化；标题字重 600、正文 400。

## 9. 部署拓扑

- 最小（单机 64GB）：`nginx + app(单jar多模块) + mysql + redis + mongo + 宿主 docker.sock`，docker-compose 一键拉起。
- 生产：主控节点（app/nginx）+ 计算集群（靶机容器节点，orchestrator 按标签调度）+ 共享存储（NFS：附件/流量包/修补包）+ 缓存分层（Redis 哨兵）。
- 高可用：应用无状态水平扩容；节点故障由编排健康检查剔除；SLA 99.95%、72h 连续运行依赖镜像预热与双节点冗余。
- 网络：比赛区/办公区 VLAN + 防火墙隔离；最小端口暴露；跨域访问审计。

## 10. 与现有 v1.0 的差距分析与分期路线图

现有 v1.0 已具备：用户/战队、赛事(CTF/练习)、动态积分、一二三四血、hint 扣分、双榜、公告、附件、JWT/BCrypt、Flyway、Docker 化。

| 期 | 内容 | 说明 |
| --- | --- | --- |
| P1 基础底座升级 | 角色扩展(JUDGE/TEACHER/OBSERVER) + 比赛命名空间隔离 + Redis 接入(会话/限流/榜) + MySQL 默认化 + MongoDB 接入 + 审计日志 | 后续一切的地基 |
| P2 CTF 增强 | 动态实例(range-orchestrator Docker 实现) + 动态 flag + 理论赛(单选/多选/判断/抽题/报告) + writeup + 黑灯模式 + WebSocket 公告/榜单推送 | 千人 CTF 完整闭环 |
| P3 前端重构 | Vue3 + Element Plus 三入口（选手/管理/大屏），按视觉规范落 tokens | 替换原生前端 |
| P4 AWD/AWDP | 轮次状态机、check 脚本、攻防计分、每队隔离靶场、VLAN、流量采集(AWDP) | 百队级 |
| P5 态势大屏 | ECharts 趋势、3D 飞线、1V1 推送、时序回放、靶机监控面板 | 需 P2/P4 数据流 |
| P6 集成与开放 | 第三方登录/SSO/LDAP、手机号开放查询接口、MQTT、短信/邮件网关适配 | 可配置外部项 |
| P7 备案与验收 | 备案报告生成、成绩报表导出、归档流程、压测与 SLA 验证 | 收尾验收 |

反作弊贯穿 P2–P5（动态 flag → 时序/相似度 → 流量分析逐步增强）。

## 11. P1 落地状态（2026-11-03）

P1「基础底座升级」已在现有单模块工程内落地（未拆 Maven 多模块，包结构先行），`mvnw clean test` 13/13 全绿。与上文目标架构的对应关系如下：

| 设计项 | 落地情况 |
| --- | --- |
| 角色体系（4.1 RBAC） | `Role` 扩展为 `USER / JUDGE / TEACHER / OBSERVER / ADMIN`；`/api/admin/**` 需 ADMIN 或 JUDGE，封禁与 `PATCH /api/admin/users/{id}/role` 仅 ADMIN；OBSERVER 在题目页隐藏提交区（只读） |
| 比赛命名空间（4.2 ABAC 属性域） | `events.namespace` 字段全链路落地（实体 / DTO / 管理端表单），`V3` 迁移加唯一索引，正则 `^[a-z0-9][a-z0-9-]{1,59}$`；比赛级数据隔离查询待 P2 引入 contest 模型后启用 |
| 审计日志 | `audit_logs` 表（V3）+ `AuditService`（`noRollbackFor` 保证业务失败也留痕），覆盖登录/登出/封禁/角色变更/赛事与题目管理；`GET /api/admin/audit-logs` 支持 action 过滤与分页 |
| MongoDB 接入 | `MongoAuditMirror` 监听 `AuditRecordedEvent`（`AFTER_COMMIT`），`app.mongo.enabled=true` 时镜像到 `op_logs` 集合；缺省关闭不影响启动 |
| Redis 接入 | `TokenRegistry` 抽象：默认 `InMemoryTokenRegistry`；`app.redis.enabled=true` 时切换 `RedisTokenRegistry`（键 `jwt:revoked:{jti}`，TTL 跟随 token 剩余有效期） |
| 会话登出销毁（7.1） | JWT 加入 `jti`；`POST /api/auth/logout` 吊销当前 token，`JwtAuthenticationFilter` 逐请求校验黑名单 |
| MySQL 默认化 | 根 `docker-compose.yml` 与 `deploy/docker-compose.server.yml` 均编排 MySQL 8 + Redis 7 + MongoDB 6（healthcheck + `depends_on: service_healthy`），默认 `mysql` profile；`.env` 可一键降级回 H2 轻量模式 |

P1 未做（按路线图留待后续）：Maven 多模块拆分（P2 起步时进行）、ABAC 数据级过滤（依赖 contest 模型）、Redis 会话/限流/榜单迁移（P2）、Vue3 前端（P3）。

## 12. P2 动态靶机下发落地状态（2026-11-03）

P2「动态靶机下发 MVP」已完成，`mvnw clean test` **17/17 全绿**（P1 13 + 新增 4）。范围聚焦在 range-orchestrator 核心 + 动态 flag 判分链路，理论赛 / writeup / 黑灯模式 / WebSocket 推送仍按路线图延后。

| 设计项 | 落地情况 |
| --- | --- |
| RangeOrchestrator SPI（6.4） | `com.example.ctf.range.RangeOrchestrator` 接口：`start / stop / reset / isRunning / backendName`；`NoopOrchestrator`（默认，内存模拟容器，端口从 18000 起递增）+ `DockerOrchestrator`（docker-java 3.4.0，支持 `unix:///var/run/docker.sock` 和 HTTP 直连，`@ConditionalOnProperty(app.range.enabled=true)` 按需装配）；`RangeProperties` 配置绑定（enabled / docker.host / 并发上限 / TTL / 端口池 / sweep 间隔） |
| 靶机生命周期编排 | `InstanceService`：申请（校验镜像配置 → 并发限制 → 端口分配 → orchestrator.start → Instance/DynamicFlag 落库）/ 重置 / 销毁 / 状态查询 / 管理端列表；同 owner + 同 challenge 已存在活跃实例时直接延长 TTL 复用，不重复拉容器 |
| TTL 自动回收 | `sweepExpired()` 调度器（`@Scheduled`），每分钟扫一次（可配 `RANGE_SWEEP_MS`）；过期实例置 `EXPIRED` 并异步 `orchestrator.stop` |
| 动态 flag（6.1） | `DynamicFlagService.generatePlaintext(challengeId, ownerId)` → `flag{md5("ctf-sec:" + challengeId + ":" + ownerId)}` 格式；明文仅用于注入容器 env，数据库只落加盐 SHA-256（复用 FlagHasher） |
| 判分链路改造（6.1） | `ChallengeService.submit`：先查 `DynamicFlagService.resolve(challengeId, userId)`，命中则 `dynamicFlagService.matches` 直接 correct；未命中或无动态 flag 实例则回退静态 `flagHasher.matches`。这样既能启用动态 flag（每 owner 独立 flag），也能保持无靶机题目行为不变 |
| 数据模型 | `V4__range_orchestrator.sql`：`challenges` 加 6 个靶机字段（instance_image/instance_port/instance_ttl_minutes/instance_cpu_limit/instance_memory_mb/instance_dynamic_flag）；新表 `instances`（实例生命周期 + 容器元数据）与 `dynamic_flags`（动态 flag 哈希 + salt + 唯一实例约束） |
| 端点 | `POST /api/challenges/{id}/instance/start`（选手申请）、`POST /reset`、`DELETE /instance`、`GET /instance`（状态查询）、`GET /api/admin/instances`（管理端列表，ADMIN/JUDGE） |
| 部署 | `docker-compose.yml` 与 `deploy/docker-compose.server.yml`：ctf 服务加 `volumes: /var/run/docker.sock:/var/run/docker.sock` + `user: "0:0"`；`.env.example` 加 P2 变量（`RANGE_ENABLED` 默认 false） |

**P2 集成测试（RangeOrchestratorIntegrationTest，4 用例）**：

| 用例 | 覆盖 |
| --- | --- |
| `noopOrchestrator_startStopReset` | NoopOrchestrator start 返回 RUNNING + host/port、复用延长 TTL、reset 新实例、stop 置 STOPPED |
| `dynamicFlag_generatedAndStoredOnStart` | DynamicFlag 落库 + matches 正确 / 错误提交返回 false |
| `dynamicFlagService_md5Format` | 明文 `flag{md5}` 格式 + 跨 challengeId 不同 flag |
| `ttlSweep_expiresRunningInstances` | 手动推 expiresAt 到过去 → sweep 置 EXPIRED 并 stop |

P2 未做（路线图后续）：WebSocket 推送、LVP SPI 实现、宿主多节点调度、流量采集（AWDP）、writeup 附件（当前 Markdown 仅 TEXT）。黑灯模式已落地，见第 15 节。

## 13. P2 理论赛落地状态（2026-11-03）

P2「理论赛 MVP」已完成，`mvnw clean test` **24/24 全绿**（P1 13 + 动态靶机 4 + 新增理论赛 7）。覆盖单选/多选/判断三种题型、随机抽题物化、判分（多选部分得分）、能力报告，理论赛得分并入个人榜。

| 设计项 | 落地情况 |
| --- | --- |
| 数据模型 | `V5__theory_exam.sql`：`theory_questions`（题干/选项 JSON/答案 JSON/知识点/难度，挂赛事维度）、`theory_configs`（每赛事一条：各题型抽题数与单题分值）、`theory_answers`（答卷物化，`uk(event_id,user_id,question_id)`） |
| 抽题（6.1 随机抽题） | 首次访问 `GET /api/events/{id}/theory/paper` 时按配置从该赛事题库分题型抽取：以 `Objects.hash(eventId, userId)` 为随机种子洗牌（同人同卷、不同人不同卷），抽中题目立即物化为 `theory_answers` 空卷；之后访问返回同一份试卷 |
| 判分（6.1 多选部分得分） | `POST /theory/submit`：SINGLE/JUDGE 全对满分；MULTI 有错选清零、无错选但漏选按正确比例得分（`full × 已选对 / 应选对`，向下取整）；比赛进行期间允许重复提交覆盖；提交结果只回传对错与得分，不回传正确答案（防暴力枚举） |
| 能力报告（6.1） | `GET /theory/report`：按 `knowledge_tag` 聚合（未标注归为"未标注"），输出题数/答对数/已得分/应得分/得分率；管理端 `GET /api/admin/theory/report` 输出全员知识点聚合 + 每用户得分总览 |
| 榜单并入 | `ScoreboardService.personalEntries` 聚合源扩展：`TheoryAnswerRepository.aggregatePointsByEvent` 按用户求和理论赛得分并入个人榜（无 CTF 解题记录的用户也凭理论赛上榜）；`personalRank` 同步并入；**战队榜与个人 Profile 总分暂未并入**（理论赛按个人计，待 AWD/AWDP 阶段统一设计多赛制计分） |
| 权限 | 题库 CRUD / 配置 / 总览：`ADMIN / JUDGE / TEACHER`；取卷与提交：登录用户（OBSERVER 只读排除）；个人报告：登录用户 |
| 审计 | 题目增删改 / 配置保存 / 提交判分均落 `audit_logs`（`THEORY_QUESTION_*` / `THEORY_CONFIG_SAVE` / `THEORY_SUBMIT`） |

**理论赛集成测试（TheoryIntegrationTest，7 用例）**：

| 用例 | 覆盖 |
| --- | --- |
| `paper_materializedAndDeterministic` | 抽题数量/题型构成正确、试卷不泄露答案字段、二次取卷同一试卷 |
| `submit_gradesAllKindsAndMultiPartial` | 单选答错 0 分、多选漏选部分得分、判断答对满分、结果不泄露答案 |
| `submit_multiWrongPickCleared` | 多选含错选直接清零 |
| `scoreboard_includesTheoryPoints` | 个人榜 `me.totalPoints` 含理论赛得分（纯理论赛用户上榜） |
| `report_aggregatesByKnowledgeTag` | 知识点聚合正确率（网络基础 10/30、Web安全 0/20） |
| `adminOverview_listsUserScores` | 管理端总览含每用户得分 |
| `paper_requiresConfig` | 未配置理论赛的赛事取卷返回 4040 |

理论赛未做（路线图后续）：班级维度报告导出、难度权重抽题（当前纯随机）、限时作答与防切屏、题目乱序选项、与统一 `question_banks` 模型合并（待多模块拆分）。

## 14. P2 Writeup 落地状态（2026-11-03）

P2「Writeup MVP」已完成，`mvnw clean test` **31/31 全绿**（P1 13 + 动态靶机 4 + 理论赛 7 + 新增 Writeup 7）。赛后开放的解题报告：Markdown 正文存 MySQL，赛后全员可见，管理端审核可采纳/驳回。

| 设计项 | 落地情况 |
| --- | --- |
| 数据模型 | `V6__writeups.sql`：`writeups(id,event_id,challenge_id,user_id,title,markdown,status,accepted,...)`，`uk(event_id,user_id,challenge_id)` 一人一题一报告，外键 CASCADE 删除 |
| 提交前置 | 仅已解出该题的用户可提交（`existsByUserIdAndChallengeId` 校验）；同一题只能提交一次（upsert → 提示"请直接编辑"） |
| 可见性控制 | **比赛进行中（ONGOING / NOT_STARTED）**：仅作者、`ADMIN / JUDGE / TEACHER` 可见；`SUBMITTED` 状态也视为非公开（防提前泄露）；**赛后（ENDED）**：`SUBMITTED / ACCEPTED` 全员可见，`REJECTED` 仍隐藏 |
| 审核 | `PUT /api/admin/writeups/{id}/moderate`：设为 `ACCEPTED`（accepted=true）或 `REJECTED`（accepted=false）；审核操作落 `audit_logs` |
| 权限 | 选手端：登录用户（OBSERVER 排除）仅可操作自己的；详情/列表按事件状态+角色动态可见；管理端：`ADMIN / JUDGE / TEACHER` 全量可见、可审核 |
| 审计 | 创建/更新/删除/审核均落 `audit_logs`（`WRITEUP_CREATE / WRITEUP_UPDATE / WRITEUP_DELETE / WRITEUP_MODERATE`） |

**Writeup 集成测试（WriteupIntegrationTest，7 用例）**：

| 用例 | 覆盖 |
| --- | --- |
| `create_requiresSolve` | 未解出的用户提交 → 403 FORBIDDEN |
| `create_solverCanSubmit` | 已解出的用户提交成功、状态 SUBMITTED |
| `create_duplicateRejected` | 同一题重复提交 → 409 CONFLICT + 提示编辑 |
| `list_duringEvent_onlyOwnVisible` | 非赛后 solver 看到自己的、viewer 看不到他人的 |
| `detail_duringEvent_forbiddenToOthers` | 非赛后 viewer 访问他人 Writeup → 403；作者与管理员可访问 |
| `afterEvent_allCanSee` | 赛后 viewer 可看到 SUBMITTED Writeup 的列表与详情 |
| `moderate_setsAcceptedStatus` | 管理端审核设 ACCEPTED/REJECTED、REJECTED 不出现在 findPublicByEvent 结果中 |

Writeup 未做（路线图后续）：Markdown 附件（`mongo_doc_id` 字段预留，MVP 仅存 TEXT）、writeup 沉淀入题库（accepted 自动同步到 `question_banks`）、writeup 点赞/收藏、writeup 导出 PDF。

### 附：测试隔离踩坑

全量 31 测试首次一起跑时出现跨测试污染——`events.get(0)` 从 Demo 数据的 practice event 变成了 CtfFlow/Security 测试临时创建的新赛事（DESC 排序新赛事在前），且临时赛事无 challenge 或已结束，导致 Theory/Writeup seed NPE 或 403。

修复：给每个创建额外 event/challenge 的测试加 `@AfterEach cleanup()`，track 本测试创建的 id 并删除。涉及：`CtfFlowIntegrationTest`（3 event + 3 challenge）、`SecurityUpgradeIntegrationTest`（1 event）。Rule：**任何测试如果创建了 DemoDataInitializer 之外的数据，必须在自己的 cleanup 里清理**。

## 15. P2 黑灯模式落地状态（2026-11-03）

P2「黑灯模式」已完成，`mvnw clean test` **35/35 全绿**（P1 13 + 动态靶机 4 + 理论赛 7 + Writeup 7 + 新增黑灯 4）。比赛级开关，开启后普通选手榜单仅可见自身、解题动态隐藏，管理员/裁判不受限。

| 设计项 | 落地情况 |
| --- | --- |
| 数据模型 | `V7__blackout_mode.sql`：`events` 表加 `blackout_mode BOOLEAN NOT NULL DEFAULT FALSE` |
| 配置入口 | `EventRequest` 加 `blackoutMode`（可空，缺省 false），创建/更新赛事时生效；`EventDto` 返回 `blackoutMode` 供前端切换展示 |
| 榜单过滤 | `ScoreboardService.scoreboard`：黑灯生效时个人榜 entries 只保留自身条目（`me` 不受影响，仍返回真实排名/得分）；战队榜只保留本队条目 |
| 解题动态 | `GET /api/scoreboard/recent` 黑灯生效时对普通选手返回空列表（接口新增 principal 入参） |
| 权限豁免 | `blackoutFor(event, principal)`：仅 `ADMIN / JUDGE` 不受限；TEACHER/OBSERVER 视为普通用户受限 |

**黑灯模式集成测试（BlackoutIntegrationTest，4 用例）**：

| 用例 | 覆盖 |
| --- | --- |
| `blackout_playerSeesOnlySelfAndNoRecent` | 选手个人榜仅 1 条自身记录、me 正常、recent 为空、EventDto 携带开关 |
| `blackout_adminAndJudgeUnaffected` | 管理员/裁判（角色变更后重新登录）榜单与动态全量可见 |
| `blackout_teamBoardShowsOnlyOwnTeam` | 战队榜仅显示本队 |
| `blackout_disabledRestoresFullVisibility` | PUT 关闭开关后选手恢复全量可见 |

黑灯未做（路线图后续）：前端榜单/动态组件的黑灯态展示（P3 Vue3 重构时落地）、黑灯期间大屏端脱敏（当前大屏端未独立实现）、黑灯开关变更的专项审计动作（当前复用 `EVENT_UPDATE`）。

## 16. P4 AWD 大屏匿名只读榜单（2026-11-04）

大屏端通常无人登录，为让 `screen.html` 直接展示 AWD 比分，新增 `/api/public/awd/**` 匿名只读接口；选手端与管理端接口保持原样。

| 设计项 | 落地情况 |
| --- | --- |
| 公开接口 | `GET /api/public/awd/rounds/current?eventId=`：按 `RUNNING > PAUSED > SCORING > PREP` 选当前轮，返回 `PublicScoreboardDto`；无轮次时 `data=null`。`GET /api/public/awd/rounds/{id}/scoreboard`：指定轮次同一结构 |
| 返回字段 | `roundId / roundName / status / statusLabel / currentRound / totalRounds / remainingSeconds / roundEndsAt` + 每队 `teamId / teamName / attackGain / defenseLoss / downtimeLoss / totalScore / rank` |
| 鉴权边界 | **不返回** flag（含本队 myFlag）、攻击记录明细（attacks）、靶机 `host/port/containerId`、队员名单；敏感信息仍只走 `GET /api/awd/rounds/{id}/scoreboard`（JWT，仅本队 flag）与 `/api/admin/awd/**`（ADMIN） |
| SecurityConfig | `"/api/public/**"` permitAll，位于 `/api/**` authenticated 之前 |
| 大屏降级 | `web/src/screen/App.vue` 的 `loadAwd()`：无 token 时调用公开接口并**不建立 WebSocket 订阅**（握手需 JWT），靠 30s 轮询刷新；已登录时仍走原 `/api/awd` 接口与 `awd:{id}` 主题 |

**验证记录（本机 H2 内存库、`RANGE_ENABLED=false`、`AWD_SIMULATE_NOOP=true`）**：

- 匿名 `GET /api/public/awd/rounds/current?eventId=...` → 200；载荷无 `flag/myFlag/host/port/containerId/attacks/members` 字段；红队 50 分排名 1、蓝队防守扣 20 分排名 2。
- 匿名 `GET /api/public/awd/rounds/{id}/scoreboard` → 200，STOP/FINISH 后仍可按 id 读取最终状态。
- 匿名 `GET /api/awd/rounds/{id}/scoreboard` → 401；匿名 `GET /api/admin/awd/rounds/{id}` → 401。
- `npm run build` 通过（三入口产物）。

### 真实 Docker 靶场现状（未完成实测）

本轮尝试把 `RANGE_ENABLED=true` 的实例下发转成实测，卡在应用装配层：`DockerOrchestrator`（`@ConditionalOnProperty(app.range.enabled=true)`）与 `NoopOrchestrator`（无条件 `@Component`）同时注册，`RangeOrchestrator` 注入点出现两个候选 Bean，应用无法启动。该问题未在轮次内修复，因此：

- 相关未验证改动（镜像本地复用、轮内 flag 写入容器、`RANGE_DOCKER_ADDRESS`）**已回退，不随本次提交入库**；
- 真实实例下发、真实 TCP/HTTP 探活、STOP 回收容器**均未验证**，不作为交付能力；
- 后续需先给 `NoopOrchestrator` 加 `@ConditionalOnProperty(name="app.range.enabled", havingValue="false", matchIfMissing=true)`，再按 `RANGE_DOCKER_ADDRESS=host.docker.internal` + 挂载 `docker.sock` 的方式补测。
