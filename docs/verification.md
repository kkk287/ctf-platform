# 实测验证记录（verification）

> 验证时间：2026-11-02 ~ 2026-11-03（Asia/Shanghai）
> 验证环境：Windows 11（PowerShell 7.6），Temurin JDK 17.0.20.1（便携目录），Maven Wrapper 3.9.9。
> 结论：**`mvnw test` 全绿（10/10）；JAR 启动成功；端到端脚本 20/20 项通过；Docker 构建、健康检查、重启自恢复与数据卷持久化均已本机验证。**
> GitHub Actions 的 SSH 部署与公网服务器访问需用户在真实服务器核验（本机无可用的公网目标）。

## 1. 测试结果

命令：

```powershell
$env:JAVA_HOME='C:\Users\34266\.cache\jdk17\jdk-17.0.20.1+1'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
.\mvnw.cmd -B test
```

结果摘要（完整日志见构建输出 `target/test-full.log`）：

```
[INFO] Running com.example.ctf.integration.CtfFlowIntegrationTest
[INFO] Tests run: 4, Failures: 0, Errors: 0, Skipped: 0
[INFO] Running com.example.ctf.service.FlagHasherTest
[INFO] Tests run: 3, Failures: 0, Errors: 0, Skipped: 0
[INFO] Running com.example.ctf.service.ScoringPolicyTest
[INFO] Tests run: 3, Failures: 0, Errors: 0, Skipped: 0
[INFO] Results:
[INFO] Tests run: 10, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS (05:10 min)
```

测试覆盖：

| 测试类 | 用例数 | 覆盖内容 |
|---|---|---|
| `CtfFlowIntegrationTest` | 4 | 认证与校验（含已入队用户登录返回战队信息的懒加载回归）、选手完整解题链路（错误/正确/重复 flag、个人榜、赛练隔离、战队同题取最高、审计 IP/UA、战绩）、管理端建赛建题/附件上传下载/未开始与已结束赛事/公告/封禁解封/越权 4030、错误 flag 限流 4290 |
| `FlagHasherTest` | 3 | 加盐哈希稳定性、不同盐隔离、提交哈希不泄漏明文 |
| `ScoringPolicyTest` | 3 | 动态积分衰减与最低分、血奖比例、最终分不为负 |

## 2. 应用启动

```powershell
java -jar target\ctf-platform-1.0.0.jar --server.port=18080
```

> 本机 8080 已被其他进程占用（Burp Suite），故实测使用 18080；**默认端口仍为 8080**，容器与服务器部署均使用 8080。

启动关键日志：

```
The following 1 profile is active: "h2"
HikariPool-1 - Added connection conn0: url=jdbc:h2:file:./data/ctf user=SA
Successfully applied 2 migrations to schema "PUBLIC", now at version v2
Tomcat started on port 18080 (http)
演示数据初始化完成：2 个赛事/练习区、5 道题目、2 支战队、7 条解题记录
```

健康检查（HTTP 200）：

```json
{"status":"UP","groups":["liveness","readiness"]}
```

## 3. 端到端业务流程

脚本：[scripts/verify-flow.ps1](../scripts/verify-flow.ps1)（PowerShell 7，自动注册独立用户，避免污染演示账号）

```powershell
pwsh -NoProfile -File scripts\verify-flow.ps1 -BaseUrl http://127.0.0.1:18080
```

**最终结果：`全部验证通过（0 失败）`，20 项断言全 PASS。** 关键响应摘录：

| 步骤 | 请求 | HTTP | 响应要点 |
|---|---|---|---|
| 1 | `GET /actuator/health` | 200 | `{"status":"UP"}` |
| 2 | `POST /api/auth/register` | 200 | `code=0`，返回 JWT（8h） |
| 3 | `POST /api/auth/login` | 200 | `code=0`，登录成功 |
| 4 | `GET /api/events` | 200 | 含 `常驻练习区`（PRACTICE）与 `秋季夺旗赛 2026`（COMPETITION / ONGOING） |
| 5 | `GET /api/challenges?eventId=<练习区>` | 200 | 找到 `栈溢出入门`，`currentPoints=235`、`solveCount=1` |
| 6 | `POST /api/challenges/<id>/submit`（错误 flag） | 400 | `{"code":4004,"message":"flag 不正确"}` |
| 7 | 同一题正确 flag（冷却 6s 后） | 200 | `{"correct":true,"message":"提交正确，二血！","basePoints":235,"bloodBonus":24,"points":259,"rank":2}` |
| 8 | 重复提交 | 409 | `{"code":4009,"message":"该题目已解出"}` |
| 9 | `GET /api/scoreboard?eventId=<练习区>&type=USER` | 200 | `me.totalPoints=259`，`me.rank=2` |
| 10 | 赛事内提交 `欢迎来到 CTF` | 200 | `message=提交正确`，`basePoints=80`，`points=80`，`rank=6` |
| 11 | 赛事个人榜 | 200 | `me.totalPoints=80`，与本次赛事得分一致（练习区 259 分未串入） |
| 12 | 练习区战队榜 | 200 | `0x00sec`：`memberCount=2`、`totalPoints=425`（同题取最高） |
| 13 | `POST /api/admin/events`（PRACTICE） | 200 | 新建练习区成功 |
| 14 | `POST /api/admin/challenges`（flag 设置） | 200 | 新建题目成功，`flagConfigured=true` |
| 16 | 选手提交新题 flag | 200 | `{"correct":true,"message":"提交正确，一血！","basePoints":100,"bloodBonus":20,"points":120,"rank":1}` |
| 17 | `GET /api/admin/submissions` | 200 | `ip=127.0.0.1`、`userAgent=ctf-verify-script/1.0`、`result=CORRECT`、`submittedHashPrefix=0d5f3679387d` |
| 18 | `GET /api/admin/anomalies` | 200 | 同一题 `欢迎来到 CTF` 出现 `distinctUsers=3` 的告警（验证脚本多个账号同 IP） |
| 19 | `GET /api/me/profile` | 200 | 3 条解题记录，总分 459，赛事/练习区分组展示 |
| 20 | 选手 `GET /api/admin/users` | 403 | `{"code":4030,"message":"无权访问该资源"}` |

完整原始输出见 `target/verify-flow.out.txt`（本地构建产物，不入库；关键内容已摘录于本表）。

## 4. 页面与接口文档可访问性

实测（HTTP，`http://127.0.0.1:18080`）：

| 路径 | 结果 |
|---|---|
| `/` | 200（返回 `index.html`） |
| `/login.html` | 200 |
| `/index.html` | 200 |
| `/challenge.html` | 200 |
| `/scoreboard.html` | 200 |
| `/profile.html` | 200 |
| `/admin.html` | 200 |
| `/assets/style.css`、`/assets/common.js` | 200 |
| `/swagger-ui.html` | 302 → `/swagger-ui/index.html`（正常重定向） |
| `/v3/api-docs` | 200（OpenAPI JSON） |

静态资源外链检查：对 `src/main/resources/static` 执行 `https?://` 搜索 → **无任何匹配**，确认零外网 CDN。

## 5. Docker 构建与运行

环境：Docker 29.2.0、Docker Compose v5.0.2（Windows）。为避开本机被占用的 8080，实测通过 `APP_PORT=18081` 映射（默认仍为 8080）。

```powershell
$env:APP_PORT='18081'
$env:JWT_SECRET='local-verify-secret-key-0123456789abcdef'
docker compose up -d --build
```

实测结果：

| 检查项 | 结果 |
|---|---|
| 镜像构建 | `ctf-platform:local` 构建成功（多阶段构建，约 382 MB），`mvn -DskipTests package` 在容器内通过 |
| 容器状态 | `Up (healthy)`，端口 `0.0.0.0:18081->8080/tcp` |
| 健康检查 | `GET /actuator/health` → 200 `{"status":"UP"}` |
| 页面访问 | `/` 200、`/login.html` 200、`/assets/style.css` 200 |
| 重启策略 | `docker inspect` → `RestartPolicy=unless-stopped` |
| 数据卷 | `ctf-platform-data:/app/data`（H2 文件库 + 上传附件） |
| 重启自恢复 | `docker restart ctf-platform` 后约 42 秒恢复健康；重启前通过管理端创建的练习区在重启后仍存在（数据卷持久化），验证后已删除 |
| 登录回归 | 重建镜像后 `alice / Alice@123` 登录 200，返回 `teamName=0x00sec` |
| 浏览器 UI | 在容器页面实测登录：提交后跳转 `/index.html`，可见“秋季夺旗赛 2026”与“[练习] 常驻练习区 · 进行中”入口及题目卡片，无错误提示 |

> 首次容器实测发现：已加入战队的用户登录返回 500（`LazyInitializationException: Team proxy - no Session`）。已在 `AuthService.login` 增加 `@Transactional(readOnly = true)`，并在集成测试中补充“alice 登录返回战队名”的回归断言，重建镜像后复测通过。

## 6. 演示账号与种子数据（实测可用）

| 账号 | 密码 | 角色 | 结果 |
|---|---|---|---|
| `admin` | `Admin@123` | ADMIN | 登录成功，管理端接口可用 |
| `alice` | `Alice@123` | USER | 种子数据中已有赛事与练习区解题记录 |
| `bob` | `Bob@123` | USER | 0x00sec 成员，战队榜聚合验证 |
| `carol` | `Carol@123` | USER | Byte Raiders 队长 |

种子数据（首次启动、数据库为空时写入）：2 个赛事/练习区、5 道题、2 支战队、7 条解题记录、2 条公告、错误提交若干。

## 7. 未在本机验证 / 需服务器最终核验

| 项目 | 状态 | 原因 / 核验方式 |
|---|---|---|
| GitHub Actions CI（JDK17 + test + 镜像构建） | 配置完成，未在 GitHub 实际运行 | 需推送到 GitHub 仓库后查看 Actions；工作流与本地命令一致 |
| GHCR 镜像推送与包可见性 | 配置完成，未实际推送 | 需配置仓库后打 tag；按 `docs/DEPLOY.md` 设置 Public 包或 `GHCR_TOKEN` |
| SSH 自动部署 | 配置完成，未实际连接服务器 | 需提供 `SERVER_HOST / SERVER_USER / SERVER_SSH_KEY` 并在服务器安装 Docker |
| 公网域名 / 端口 / 防火墙 | 未验证 | 需用户在云安全组放行端口并用 `curl /actuator/health` 核验 |
| MySQL / PostgreSQL 实库 | 未验证 | 本机无对应服务；SQL 为可移植写法，建议上线前用目标库跑一次 Flyway |
| 容器重启自恢复 | 已本机验证（`unless-stopped` + 健康检查 + 数据卷持久化，见第 5 节） | 服务器 `reboot` 后复查 `docker compose ps` 与健康端点 |

## 8. 已知限制（与设计一致）

- 不做动态靶机下发；靶机类题目以附件与外部地址承载。
- 不做 CTFd 插件兼容与 OAuth。
- 防作弊为"IP/UA 留存 + 同题同 IP 多账号告警"的基础版。
- 前端面向桌面（≥1280px），未做移动端专项适配。
