# ctf-platform · CTF 平台（Java / Spring Boot 3）

一个可直接运行、可从 GitHub 拉取并在服务器上一条命令启动的 CTF 平台。包含赛事（COMPETITION）与练习区（PRACTICE）双模式、动态积分、一血/二血/三血奖励、提示扣分、个人榜与战队榜、管理后台与提交审计。

> 默认数据为 H2 文件库（`./data/ctf.mv.db`）+ 数据卷，开箱即用，不依赖任何外部数据库；MySQL / PostgreSQL 为可选 profile。
> 前端页面全部为本地静态资源，随 JAR 发布，**不依赖任何外网 CDN**，内网/离线环境可直接使用。

## 目录

- [快速开始](#快速开始)
- [演示账号与默认 Flag](#演示账号与默认-flag)
- [GitHub 部署三步走](#github-部署三步走)
- [技术栈](#技术栈)
- [目录结构](#目录结构)
- [接口一览](#接口一览)
- [配置项](#配置项)
- [已知限制](#已知限制)
- [文档索引](#文档索引)

## 快速开始

### 方式一：本地运行（需要 JDK 17）

```bash
# Windows
mvnw.cmd spring-boot:run
# Linux / macOS
./mvnw spring-boot:run
```

浏览器打开 <http://localhost:8080/>（首次启动会自动执行 Flyway 迁移并写入演示数据）。

### 方式二：打包运行

```bash
mvnw.cmd -B clean package -DskipTests
java -jar target/ctf-platform-1.0.0.jar
```

### 方式三：Docker（推荐，无需本机 JDK/Maven）

```bash
docker compose up -d --build
# 查看健康状态
docker compose ps
curl http://localhost:8080/actuator/health
```

- 默认端口：`8080`（可用 `.env` 中 `APP_PORT` 修改）
- 健康检查：`GET /actuator/health`
- 数据持久化：named volume `ctf-platform-data` 挂载到容器 `/app/data`（H2 数据库 + 题目附件）
- 容器重启策略 `unless-stopped`，服务器重启后自动恢复
- 停止：`docker compose down`（**不会**删除数据卷）；彻底清理：`docker compose down -v`

首次启动约 30~60 秒（含 Flyway 迁移、演示数据与 BCrypt 初始化），可用 `docker compose logs -f ctf` 观察。

### 演示账号

| 用户名 | 密码 | 角色 | 战队 | 说明 |
|---|---|---|---|---|
| `admin` | `Admin@123` | ADMIN | — | 管理后台、建题建赛、封禁、审计 |
| `alice` | `Alice@123` | USER | `0x00sec`（队长） | 演示解题与排行榜 |
| `bob` | `Bob@123` | USER | `0x00sec`（成员） | 演示战队同题只计最高 |
| `carol` | `Carol@123` | USER | `Byte Raiders`（队长） | 演示另一支战队 |

> 密码仅用于演示，全部为 BCrypt 哈希后入库（见 `V2__seed_users.sql`），仓库中不存在明文密码配置项。
> 生产环境请通过管理端重建账号，或修改种子脚本后重新初始化数据库。

### 默认赛事与题目 Flag

| 赛事 | 类型 | 时间 | 题目 | 分类/难度 | 分值 | 默认 Flag |
|---|---|---|---|---|---|---|
| 秋季夺旗赛 2026 | COMPETITION | 首次启动时 `now-2d ~ now+5d`（进行中） | 欢迎来到 CTF | Misc / 简单 | 100 | `flag{welcome_to_ctf_2026}` |
| 秋季夺旗赛 2026 | COMPETITION | 同上 | 登录框的秘密 | Web / 中等 | 150 | `flag{sql_1nject10n_b4s1cs}` |
| 秋季夺旗赛 2026 | COMPETITION | 同上 | 小公钥的秘密 | Crypto / 中等 | 200 | `flag{rsa_sm4ll_e_attack}` |
| 常驻练习区 | PRACTICE | 长期开放 | 栈溢出入门 | Pwn / 困难 | 250 | `flag{r3t2w1n_4nd_r0p}` |
| 常驻练习区 | PRACTICE | 长期开放 | Baby Patch | Reverse / 简单 | 120 | `flag{rev3rs3_e4sy_p4tch}` |

> 演示用固定 flag 已写入种子数据；管理端新建题目时可自由设置 flag。数据库只保存 `flagHash + salt`，**不落明文**。

## GitHub 部署三步走

前置：一台已安装 Docker 与 Docker Compose v2 的 Linux 服务器（开放 8080 端口），以及一个 GitHub 仓库。

### 第 1 步：把仓库推上 GitHub

```bash
cd ctf-platform
git init
git add .
git commit -m "feat: CTF platform initial commit"
git branch -M main
git remote add origin https://github.com/<你的账号>/ctf-platform.git
git push -u origin main
```

推送后 Actions 会自动执行 `CI`：JDK 17 构建 + 全部测试 + Docker 镜像构建。首次运行可在仓库 **Actions** 页查看。

### 第 2 步：配置 Secrets / Variables

仓库 **Settings → Secrets and variables → Actions**：

| 类型 | 名称 | 必填 | 说明 |
|---|---|---|---|
| Secret | `SERVER_HOST` | SSH 部署必填 | 服务器 IP 或域名 |
| Secret | `SERVER_USER` | SSH 部署必填 | SSH 用户名（需在 docker 组） |
| Secret | `SERVER_SSH_KEY` | SSH 部署必填 | 私钥全文（对应公钥已加入服务器 `~/.ssh/authorized_keys`） |
| Secret | `SERVER_PORT` | 否 | SSH 端口，默认 22 |
| Secret | `GHCR_TOKEN` | 私有包必填 | PAT，权限至少 `read:packages`；公开包可留空 |
| Variable | `DEPLOY_ENABLED` | 自动部署必填 | 设为 `true` 才会执行 SSH 部署步骤 |
| Variable | `APP_PORT` | 否 | 服务器映射端口，默认 8080 |

### 第 3 步：服务器执行一条命令（或让 Actions 自动部署）

**A. 自动部署（推荐）**：打 tag 即触发构建、推送 GHCR、SSH 拉取重启：

```bash
git tag v1.0.0
git push origin v1.0.0
```

**B. 服务器手动一条命令**（把 `APP_IMAGE` 换成自己的 GHCR 地址）：

```bash
sudo mkdir -p /opt/ctf && cd /opt/ctf \
&& curl -fsSL https://raw.githubusercontent.com/<你的账号>/ctf-platform/main/deploy/docker-compose.server.yml -o docker-compose.yml \
&& echo "APP_IMAGE=ghcr.io/<你的账号>/ctf-platform:latest" > .env \
&& echo "JWT_SECRET=$(openssl rand -hex 32)" >> .env \
&& docker compose pull && docker compose up -d
```

验证：

```bash
curl http://<服务器IP>:8080/actuator/health   # {"status":"UP"}
```

浏览器打开 `http://<服务器IP>:8080/`，使用 `admin / Admin@123` 登录后台。

> 完整步骤、包可见性设置、端口/防火墙、私有仓库拉取、常见故障排查见 [docs/DEPLOY.md](docs/DEPLOY.md)。

## 技术栈

- Java 17、Spring Boot 3.2.12、Maven Wrapper（自带 Maven 3.9.9，无需本机 Maven）
- Spring Data JPA + Flyway（可移植 SQL，H2/MySQL/PostgreSQL）+ H2 文件库
- Spring Security 6 + JWT（jjwt 0.12.5）无状态认证 + BCrypt + RBAC（ADMIN / USER）
- 统一响应体 `{code, message, data}`、全局异常处理、Jakarta Validation
- springdoc-openapi（`/swagger-ui.html`）、Actuator（`/actuator/health`）
- 原生 HTML/CSS/JS 前端（深色 CTF 风格，零构建、零 CDN）

## 目录结构

```
ctf-platform/
├── .github/workflows/         # ci.yml（构建测试+镜像构建）、deploy.yml（GHCR+SSH）
├── .mvn/wrapper/              # Maven Wrapper
├── deploy/
│   └── docker-compose.server.yml  # 服务器端仅拉镜像的编排
├── docs/                      # PLAN / DEPLOY / DEMO / AI_USAGE / verification
├── prompts/                   # AI 提示词与实际使用记录
├── src/main/java/com/example/ctf/
│   ├── common/                # ApiResponse / ErrorCode / BusinessException / PageResult / 全局异常
│   ├── config/                # SecurityConfig / OpenApiConfig
│   ├── controller/            # Auth / Team / Event / Challenge / Scoreboard / Admin
│   ├── domain/                # User / Team / Event / Challenge / Hint / Submission / Solve / Announcement
│   ├── dto/                   # 各模块请求与响应 DTO
│   ├── repository/            # Spring Data JPA Repository
│   ├── security/              # JWT Service / Filter / UserPrincipal
│   └── service/               # 认证 / 战队 / 赛事 / 判分 / 排行榜 / 管理 / 演示数据
├── src/main/resources/
│   ├── db/migration/          # V1 建表、V2 演示账号
│   └── static/                # 登录、题目列表/详情、排行榜、我的战绩、管理后台
├── src/test/                  # 单元测试 + MockMvc 集成测试
├── Dockerfile
├── docker-compose.yml
├── .env.example
└── pom.xml
```

## 接口一览

统一响应体：`{"code":0,"message":"OK","data":...}`；业务错误 `code != 0`，HTTP 状态同步（400/401/403/404/409/429/500）。
认证：除登录、注册、静态页、Swagger、健康检查外均需 `Authorization: Bearer <JWT>`。

| 方法 | 路径 | 说明 | 权限 |
|---|---|---|---|
| POST | `/api/auth/register` | 注册（自动登录返回 token） | 公开 |
| POST | `/api/auth/login` | 登录 | 公开 |
| GET | `/api/auth/me` | 当前用户 | 登录 |
| GET | `/api/events` | 赛事/练习区列表（含派生状态） | 登录 |
| GET | `/api/events/{id}` | 赛事详情 | 登录 |
| GET | `/api/announcements` | 公告列表 | 登录 |
| GET | `/api/challenges?eventId=&category=&solved=&keyword=` | 题目列表（含我的解出状态、当前分值） | 登录 |
| GET | `/api/challenges/{id}` | 题目详情（描述/附件/提示/最近解题） | 登录 |
| GET | `/api/challenges/{id}/attachment` | 下载附件 | 登录 |
| POST | `/api/challenges/{id}/submit` | 提交 flag 判分 | 登录 |
| POST | `/api/challenges/{id}/hints/{hintId}/unlock` | 解锁提示（扣分） | 登录 |
| GET | `/api/scoreboard?eventId=&type=USER\|TEAM&limit=` | 排行榜（个人/战队，含我的名次） | 登录 |
| GET | `/api/scoreboard/recent?eventId=&limit=` | 最近解题动态 | 登录 |
| GET | `/api/me/profile` | 我的战绩（总分/解题/战队/赛事） | 登录 |
| POST | `/api/teams` | 创建战队 | USER |
| POST | `/api/teams/join` | 凭邀请码加入 | USER |
| POST | `/api/teams/leave` | 退出战队 | USER |
| GET | `/api/teams/my` | 我的战队信息 | 登录 |
| DELETE | `/api/teams/my/members/{userId}` | 队长移除成员 | USER（队长） |
| GET/POST | `/api/admin/challenges` | 题目列表 / 新建 | ADMIN |
| PUT/DELETE | `/api/admin/challenges/{id}` | 更新 / 删除 | ADMIN |
| POST/DELETE | `/api/admin/challenges/{id}/attachment` | 上传 / 删除附件 | ADMIN |
| POST/PUT/DELETE | `/api/admin/challenges/{id}/hints...` | 提示增删改 | ADMIN |
| GET/POST | `/api/admin/events` | 赛事列表 / 新建（COMPETITION / PRACTICE） | ADMIN |
| PUT/DELETE | `/api/admin/events/{id}` | 更新 / 删除 | ADMIN |
| GET | `/api/admin/users?keyword=&page=&size=` | 用户分页搜索 | ADMIN |
| PATCH | `/api/admin/users/{id}/ban` | 封禁 / 解封 | ADMIN |
| GET | `/api/admin/submissions?challengeId=&userId=&result=` | 提交审计（IP/UA/时间/结果） | ADMIN |
| GET | `/api/admin/anomalies` | 同 IP 多账号异常告警 | ADMIN |
| GET/POST/PUT/DELETE | `/api/admin/announcements...` | 公告管理 | ADMIN |

Swagger UI：<http://localhost:8080/swagger-ui.html>（点击 Authorize 填入登录返回的 token）。

## 配置项

| 环境变量 | 默认值 | 说明 |
|---|---|---|
| `SERVER_PORT` | `8080` | 应用端口 |
| `APP_PORT` | `8080` | Docker 宿主机映射端口 |
| `SPRING_PROFILES_ACTIVE` | `h2` | `h2` / `mysql` / `postgres` |
| `JWT_SECRET` | 空 | 空时启动生成临时随机密钥（重启后旧 token 失效）；生产务必设置 ≥32 字节随机串 |
| `JWT_EXPIRATION_HOURS` | `8` | token 有效期 |
| `DEMO_SEED_ENABLED` | `true` | 数据库为空时写入演示数据 |
| `UPLOAD_DIR` | `./data/uploads` | 附件存储目录（容器内为 `/app/data/uploads`） |
| `MAX_ATTACHMENT_SIZE` | `20971520` | 附件大小上限（字节） |
| `MAX_WRONG_ATTEMPTS` | `5` | 窗口内错误尝试上限 |
| `WRONG_WINDOW_MINUTES` | `5` | 限流统计窗口 |
| `SUBMIT_COOLDOWN_SECONDS` | `5` | 错误后的提交冷却 |
| `DB_URL` / `DB_USERNAME` / `DB_PASSWORD` | — | MySQL / PostgreSQL profile 使用 |

完整示例见 `.env.example`。

## 已知限制

- **不做动态靶机下发**：Pwn/Reverse 等题目通过"附件下载 + 外部地址（写在题目描述中）"承载，平台不负责启动/回收 Docker 靶机。
- **不做 CTFd 插件兼容**，不提供其 API/主题生态。
- **不做 OAuth 第三方登录**，仅账号密码 + JWT。
- 战队榜规则为"成员得分求和、同一题目只计最高分"，不做战队内多档计分。
- 防作弊仅为基础版：留存 IP/UA，并对"同题 + 同 IP 多账号正确提交"给出管理端告警，不做设备指纹或复杂风控。
- 赛事状态由起止时间实时派生，不支持手动暂停/加时（可编辑时间）。

## 文档索引

| 文档 | 内容 |
|---|---|
| [docs/PLAN.md](docs/PLAN.md) | 需求拆解、状态机、数据模型、接口/页面清单、需求-实现映射 |
| [docs/DEPLOY.md](docs/DEPLOY.md) | 本地/Docker/GitHub Actions + 服务器部署全流程、Secrets、故障排查 |
| [docs/DEMO.md](docs/DEMO.md) | 5-8 分钟演示脚本（赛事 + 练习区各走一遍） |
| [docs/AI_USAGE.md](docs/AI_USAGE.md) | AI 工具/模型使用说明、提示词模板、人工复核点与局限 |
| [docs/verification.md](docs/verification.md) | 本次实测证据（测试、启动、完整链路响应摘要） |
| [prompts/](prompts/) | 实际使用的提示词记录 |
