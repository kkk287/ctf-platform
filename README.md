# ctf-platform · CTF 平台（Java / Spring Boot 3）

> 仓库：<https://github.com/kkk287/ctf-platform>  
> 镜像：`ghcr.io/kkk287/ctf-platform`

一个可直接从 GitHub 拉取、在服务器上用 **一条命令** 启动的 CTF 平台。同时支持**赛事（COMPETITION，限时、到点截止）**与**练习区（PRACTICE，长期开放、独立计分独立榜单）**两种模式；内置动态积分、一血/二血/三血奖励、提示扣分、个人榜与战队榜、管理后台与提交审计。

本机直跑默认 H2 文件库，开箱即用，不依赖任何外部数据库；Docker Compose 默认编排**单服务 H2 轻量模式**（`docker compose up -d --build` 即可，无需 MySQL/Redis/MongoDB）。需要 MySQL 8 + Redis 7 + MongoDB 6 全家桶时改用 `docker compose -f docker-compose.full.yml up -d --build`。MySQL / PostgreSQL profile 可自由切换。前端全部为本地静态资源，**零外网 CDN**，内网/离线可用。

---

## 目录

- [功能清单](#功能清单)
- [技术栈](#技术栈)
- [仓库结构](#仓库结构)
- [三种使用方式](#三种使用方式)
  - [方式 A：服务器从 GitHub clone + docker compose（推荐）](#方式-a服务器从-github-clone--docker-compose推荐)
  - [方式 B：从 GHCR 拉镜像部署](#方式-b从-ghcr-拉镜像部署)
  - [方式 C：本机构建运行](#方式-c本机构建运行)
- [演示账号与默认端口](#演示账号与默认端口)
- [GitHub Actions 自动部署](#github-actions-自动部署)
- [常见故障排查](#常见故障排查)
- [仍需在你服务器上核验的环节](#仍需在你服务器上核验的环节)
- [接口一览](#接口一览)
- [配置项](#配置项)
- [已知限制](#已知限制)
- [文档索引](#文档索引)

---

## 功能清单

**赛事与题目**

- 赛事 `COMPETITION`：设定起止时间，到点自动截止；未开始的题目对普通用户不可见、不可答。
- 练习区 `PRACTICE`：长期开放；与赛事**独立计分、独立榜单**，互不影响。
- 题目分类：Web / Pwn / Crypto / Misc / Reverse；难度、初始分、附件、提示（hint）、可见性、已解出状态。
- 附件上传/下载（默认 20MB），存储于数据卷 `/app/data/uploads`，容器重启不丢。

**判分与防作弊**

- Flag 服务端加盐 SHA-256 哈希存储，**数据库不落明文**。
- 动态积分：随解出人数衰减并设最低分；一血/二血/三血按比例奖励；解锁 hint 扣分。
- 提交限流 + 冷却：窗口内错误次数超限返回 429；错误后冷却期内不可再提交。
- 重复提交/已解出返回明确业务错误码（4009）；封禁用户登录返回 4033。
- 提交审计：记录 IP / UA / 时间 / 结果；管理端提供「同题同 IP 多账号正确提交」异常告警。

**排行榜**

- 个人榜 / 战队榜切换；按赛事隔离（含练习区独立榜）。
- 排序：总分降序，同分按最后得分时间升序。
- 战队榜规则：成员得分求和，**同一题目只计最高分**。

**用户与战队**

- 注册 / 登录 / 登出（服务端吊销 token，Redis 或内存黑名单）/ JWT 无状态认证 / BCrypt。
- 角色体系：`USER` 选手 / `JUDGE` 裁判 / `TEACHER` 教师 / `OBSERVER` 观摩（只读）/ `ADMIN` 管理员。
- 战队：创建（生成邀请码）、凭邀请码加入、退出、队长移除成员。

**管理后台**（`/vue/admin.html`）

- 题目、赛事（含 PRACTICE、namespace 命名空间唯一标识）、用户封禁/解封与角色变更、公告、提交审计、异常告警、操作审计日志（可镜像至 MongoDB `op_logs`）。

**工程化**

- 统一响应体 `{code, message, data}`、全局异常处理、Jakarta Validation、分页。
- 基础设施可插拔：Redis（JWT 吊销黑名单，缺省降级为内存实现）与 MongoDB（审计日志异步镜像，缺省关闭），均不影响启动。
- OpenAPI（`/swagger-ui.html`）、Actuator 健康检查（`/actuator/health`）。
- Docker 多阶段构建、非 root 运行、healthcheck、`restart: unless-stopped`、named volume 持久化。
- GitHub Actions：CI（构建 + 测试 + 镜像构建）、Deploy（GHCR 推送 + SSH 部署）。

## 技术栈

| 层 | 选型 |
|---|---|
| 语言/运行时 | Java 17 |
| 框架 | Spring Boot 3.2.12 |
| 构建 | Maven Wrapper（自带 Maven 3.9.9，无需本机安装） |
| 持久化 | Spring Data JPA + Hibernate（`ddl-auto=validate`）+ Flyway 迁移 |
| 数据库 | H2 文件库（本机与 compose 默认）/ MySQL 8（`docker-compose.full.yml`）/ PostgreSQL（可选 profile） |
| 缓存 | Redis 7（可选；JWT 吊销黑名单，缺省降级内存实现） |
| 文档库 | MongoDB 6（可选；审计日志镜像 `op_logs`，缺省关闭） |
| 安全 | Spring Security 6 + JWT（jjwt 0.12.5，含 jti 吊销）+ BCrypt + RBAC |
| 文档 | springdoc-openapi 2.3.0 |
| 前端 | Vue3 + Element Plus 三入口（选手/管理/大屏，构建产物随 jar 打包），根路径重定向到 Vue 选手端 |
| 部署 | Docker 多阶段构建 + Docker Compose v2 + GHCR + GitHub Actions |

## 仓库结构

```
ctf-platform/
├── .github/workflows/
│   ├── ci.yml                 # push/PR：JDK17 构建 + 全部测试 + Docker 镜像构建
│   └── deploy.yml             # tag v* 或手动：推送 GHCR + SSH 自动部署
├── .mvn/wrapper/              # Maven Wrapper
├── deploy/
│   └── docker-compose.server.yml   # 服务器端仅拉镜像的编排文件
├── docs/                      # PLAN / DEPLOY / DEMO / AI_USAGE / verification
├── prompts/                   # AI 提示词模板与实际使用记录
├── scripts/
│   └── verify-flow.ps1        # 端到端验证脚本（20 项检查）
├── src/main/java/com/example/ctf/
│   ├── common/                # ApiResponse / ErrorCode / BusinessException / PageResult / 全局异常
│   ├── config/                # SecurityConfig / OpenApiConfig
│   ├── controller/            # Auth / Team / Event / Challenge / Scoreboard / Admin
│   ├── domain/                # User / Team / Event / Challenge / Hint / Submission / Solve / Announcement
│   ├── dto/                   # 各模块请求与响应 DTO
│   ├── repository/            # Spring Data JPA Repository
│   ├── security/              # JWT Service / Filter / UserPrincipal / 401&403 Handler
│   └── service/               # 认证 / 战队 / 赛事 / 判分 / 排行榜 / 管理 / 演示数据
├── src/main/resources/
│   ├── application*.yml       # 默认 h2，另有 mysql / postgres profile
│   ├── db/migration/          # V1 建表、V2 演示账号
│   └── static/                # 登录、赛事/题目、排行榜、我的战绩、管理后台
├── src/test/                  # 单元测试 + MockMvc 集成测试
├── Dockerfile
├── docker-compose.yml         # 本地/服务器源码构建
├── .env.example
└── pom.xml
```

## 三种使用方式

### 前置条件

| 场景 | 需要 |
|---|---|
| A / B（服务器） | Linux 服务器 + Docker Engine 20+ + Docker Compose v2 + 可访问 `github.com` / `ghcr.io` |
| C（本机） | JDK 17（Maven 由 Wrapper 自带） |

---

### 方式 A：服务器从 GitHub clone + docker compose（推荐）

```bash
sudo mkdir -p /opt && cd /opt
git clone https://github.com/kkk287/ctf-platform.git ctf-platform
cd ctf-platform
cp .env.example .env
# 生产环境务必写入固定密钥（否则每次重启 token 失效）
sed -i "s|^JWT_SECRET=.*|JWT_SECRET=$(openssl rand -hex 32)|" .env
# 如需改端口：sed -i 's|^APP_PORT=.*|APP_PORT=8080|' .env
docker compose up -d --build
```

> 默认编排 **单服务 H2 轻量模式**（首次启动自动 Flyway 建表，数据落在 named volume `ctf-platform-data`）。需要 MySQL + Redis + MongoDB 全家桶：`docker compose -f docker-compose.full.yml up -d --build`（在 `.env` 设 `SPRING_PROFILES_ACTIVE=mysql` 等）。

验证：

```bash
docker compose ps                                  # ctf 应为 healthy
curl -fsS http://127.0.0.1:8080/actuator/health    # {"status":"UP"}
```

- 页面：`http://<服务器IP>:8080/`（302 重定向到 `/vue/player.html`）；管理端 `/vue/admin.html`，大屏 `/vue/screen.html`
- 数据持久化：named volume `ctf-platform-data` → 容器 `/app/data`（H2 库、附件与上传）；全家桶模式另有 `ctf-platform-mysql`、`ctf-platform-mongo`
- 停止：`docker compose down`（保留数据）；彻底清理：`docker compose down -v`

---

### 方式 B：从 GHCR 拉镜像部署

前提：镜像已由 Actions 推送到 GHCR。首次使用请先跑一次 Deploy 工作流（打 tag 或手动触发），然后：

```bash
sudo mkdir -p /opt/ctf && cd /opt/ctf
curl -fsSL https://raw.githubusercontent.com/kkk287/ctf-platform/main/deploy/docker-compose.server.yml -o docker-compose.yml
cat > .env <<'EOF'
APP_IMAGE=ghcr.io/kkk287/ctf-platform:latest
APP_PORT=8080
JWT_SECRET=请替换为至少32字节随机串
MYSQL_ROOT_PASSWORD=请替换为强密码
MYSQL_PASSWORD=请替换为强密码
DEMO_SEED_ENABLED=false
EOF
# 生成随机密钥与数据库密码（替换上面的占位值）
sed -i "s|^JWT_SECRET=.*|JWT_SECRET=$(openssl rand -hex 32)|" .env
sed -i "s|^MYSQL_ROOT_PASSWORD=.*|MYSQL_ROOT_PASSWORD=$(openssl rand -hex 16)|" .env
sed -i "s|^MYSQL_PASSWORD=.*|MYSQL_PASSWORD=$(openssl rand -hex 16)|" .env
docker compose pull
docker compose up -d
```

`.env` 字段说明：

| 变量 | 必填 | 说明 |
|---|---|---|
| `APP_IMAGE` | 是 | `ghcr.io/kkk287/ctf-platform:latest` 或 `:v1.0.0` |
| `APP_PORT` | 否 | 宿主机映射端口，默认 `8080` |
| `JWT_SECRET` | 是 | ≥32 字节随机串（compose 强制校验，缺失拒绝启动） |
| `MYSQL_ROOT_PASSWORD` / `MYSQL_PASSWORD` | 是 | MySQL 初始化密码（compose 强制校验） |
| `MYSQL_DATABASE` / `MYSQL_USER` | 否 | 默认 `ctf` / `ctf` |
| `DEMO_SEED_ENABLED` | 否 | 服务器端默认 `false`；数据库为空时是否写入演示数据 |
| `REDIS_ENABLED` / `MONGO_ENABLED` | 否 | 默认 `true`；JWT 吊销黑名单 / 审计日志镜像 |

> 服务器端编排固定使用 `mysql` profile 并编排 MySQL + Redis + MongoDB；如需 H2 轻量模式请使用方式 A（源码构建）或自行调整 compose 文件。

镜像默认公开（Public）。若为私有包，需先登录：

```bash
echo "<你的PAT>" | docker login ghcr.io -u kkk287 --password-stdin
```

---

### 方式 C：本机构建运行

```bash
# 运行全部测试
mvnw.cmd -B clean test            # Linux/macOS: ./mvnw -B clean test

# 直接启动（默认 8080）
mvnw.cmd spring-boot:run

# 或打包运行
mvnw.cmd -B clean package -DskipTests
java -jar target/ctf-platform-1.0.0.jar
```

本机 Docker 构建（与方式 A 相同，只是镜像是本地构建）：

```bash
cp .env.example .env
docker compose up -d --build
```

- 页面：<http://localhost:8080/>（302 重定向到 `/vue/player.html`）；管理端 <http://localhost:8080/vue/admin.html>，大屏 <http://localhost:8080/vue/screen.html>
- Swagger：<http://localhost:8080/swagger-ui.html>
- 数据：`./data/ctf.mv.db`、附件 `./data/uploads`；删除 `data/` 可重置演示数据

## 演示账号与默认端口

**默认端口**：`8080`（Docker 可用 `.env` 的 `APP_PORT` 修改）。

| 用户名 | 密码 | 角色 | 战队 | 说明 |
|---|---|---|---|---|
| `admin` | `Admin@123` | ADMIN | — | 管理后台、建题建赛、封禁、审计 |
| `alice` | `Alice@123` | USER | `0x00sec`（队长） | 演示解题与排行榜 |
| `bob` | `Bob@123` | USER | `0x00sec`（成员） | 演示战队同题只计最高 |
| `carol` | `Carol@123` | USER | `Byte Raiders`（队长） | 演示另一支战队 |

> 密码仅用于演示，均以 BCrypt 哈希入库（见 `V2__seed_users.sql`），仓库中不存在明文密码配置项。生产环境请通过管理端重建账号，或修改种子脚本后重新初始化数据库。

| 赛事 | 类型 | 时间 | 题目 | 分类/难度 | 分值 | 默认 Flag |
|---|---|---|---|---|---|---|
| 秋季夺旗赛 2026 | COMPETITION | 首次启动时 `now-2d ~ now+5d` | 欢迎来到 CTF | Misc / 简单 | 100 | `flag{welcome_to_ctf_2026}` |
| 秋季夺旗赛 2026 | COMPETITION | 同上 | 登录框的秘密 | Web / 中等 | 150 | `flag{sql_1nject10n_b4s1cs}` |
| 秋季夺旗赛 2026 | COMPETITION | 同上 | 小公钥的秘密 | Crypto / 中等 | 200 | `flag{rsa_sm4ll_e_attack}` |
| 常驻练习区 | PRACTICE | 长期开放 | 栈溢出入门 | Pwn / 困难 | 250 | `flag{r3t2w1n_4nd_r0p}` |
| 常驻练习区 | PRACTICE | 长期开放 | Baby Patch | Reverse / 简单 | 120 | `flag{rev3rs3_e4sy_p4tch}` |

> 数据库只保存 `flagHash + salt`，不落明文。管理端新建题目时可自由设置 flag。

## GitHub Actions 自动部署

### 工作流与触发方式

| 工作流 | 文件 | 触发 | 行为 |
|---|---|---|---|
| **CI** | `.github/workflows/ci.yml` | push 到 `main/master`、任意 PR、手动 | JDK 17 → `./mvnw -B clean test` → 构建 Docker 镜像（不推送）→ 上传 surefire 报告 |
| **Deploy** | `.github/workflows/deploy.yml` | push tag `v*`、手动 Run workflow | 构建并推送 GHCR（`vX.Y.Z` / `latest` / `sha-<短哈希>`）→（`DEPLOY_ENABLED=true` 时）scp compose 文件并 SSH 拉取重启 |

触发自动部署：

```bash
git tag v1.0.0
git push origin v1.0.0
```

手动触发：仓库 **Actions → Deploy → Run workflow**（此时只推 `sha-<短哈希>`，不更新 `latest`）。

### 需要配置的 Secrets（Settings → Secrets and variables → Actions）

| 类型 | 名称 | 必填 | 说明 |
|---|---|---|---|
| Secret | `SERVER_HOST` | SSH 部署必填 | 服务器公网 IP 或域名 |
| Secret | `SERVER_USER` | SSH 部署必填 | SSH 用户（需属于 `docker` 组） |
| Secret | `SERVER_SSH_KEY` | SSH 部署必填 | **私钥全文**（含首尾行）；公钥需写入服务器 `~/.ssh/authorized_keys` |
| Secret | `SERVER_PORT` | 否 | SSH 端口，默认 `22` |
| Secret | `GHCR_TOKEN` | 私有包必填 | PAT，至少 `read:packages`；公开包可留空（使用 `GITHUB_TOKEN` 推送镜像） |
| Variable | `DEPLOY_ENABLED` | 自动部署必填 | 设为 `true` 才执行 SSH 部署；不设则只推镜像 |
| Variable | `APP_PORT` | 否 | 服务器映射端口，默认 `8080` |

生成部署密钥：

```bash
ssh-keygen -t ed25519 -C "github-actions-ctf" -f ./ctf_deploy_key -N ""
ssh-copy-id -i ./ctf_deploy_key.pub <user>@<server>
# 将 ctf_deploy_key 私钥全文填入 SERVER_SSH_KEY
```

服务端目录约定：compose 文件落到 `/opt/ctf/docker-compose.yml`，同目录 `.env` 保存 `APP_IMAGE` / `APP_PORT` / `JWT_SECRET`（工作流只更新 `APP_IMAGE`、`APP_PORT`，保留已有 `JWT_SECRET`）。

### GHCR 包可见性

首次推送后：仓库 → **Packages** → `ctf-platform` → *Package settings* → **Change visibility → Public**。若保持私有，服务器需 `docker login ghcr.io`（见方式 B）。

## 常见故障排查

### 1. 端口占用

```bash
ss -lntp | grep 8080                 # 查看占用进程
docker compose ps                    # 容器端口映射
```

处理：改 `.env` 中 `APP_PORT=8081` 后 `docker compose up -d`；或停止占用进程；或前置 Nginx 反代到 8080。

### 2. 包可见性 / 拉取失败

| 现象 | 处理 |
|---|---|
| `denied` / `unauthorized` 拉取 GHCR | 将 Package 设为 Public，或配置 `GHCR_TOKEN` 并 `docker login ghcr.io` |
| `manifest unknown` | 镜像 tag 不存在；先跑一次 Deploy，或改用 `:sha-xxxx` |
| 组织仓库读包失败 | 确认执行部署的账号有包读取权限 |

### 3. 防火墙 / 安全组

- 云服务器安全组放行业务端口（默认 8080）；主机防火墙 `ufw allow 8080` 或 `firewall-cmd --add-port=8080/tcp`。
- 前置 Nginx 时传递 `X-Forwarded-For`（应用已启用 `forward-headers-strategy: framework`，审计可拿到真实 IP）。

### 4. 数据持久化

```bash
docker volume ls | grep ctf-platform-data
docker inspect ctf-platform --format '{{json .Mounts}}'
```

- 数据卷 `ctf-platform-data` 挂载到 `/app/data`，`docker compose down` **不会**删除；只有 `down -v` 才会。
- 备份：

```bash
docker run --rm -v ctf-platform-data:/data -v $(pwd):/backup alpine \
  tar czf /backup/ctf-data-$(date +%Y%m%d).tar.gz -C /data .
```

- 重启后数据仍在但登录 401：`JWT_SECRET` 未固定，每次启动换密钥；在 `.env` 写入固定值后重启。

### 5. 容器不健康

```bash
docker compose logs -f ctf
docker inspect --format '{{json .State.Health}}' ctf-platform
```

| 现象 | 原因 / 处理 |
|---|---|
| 启动即退出，Flyway 报错 | 数据卷内 H2 与迁移不兼容；备份后重建（演示环境可 `down -v`） |
| 一直 `starting` | 首次启动含 BCrypt/种子数据，等待 45~60s |
| `/app/data` 权限错误 | 数据卷属主异常；`docker compose down` 后重建卷 |
| 页面能开但接口 401 | token 过期或密钥变更，重新登录 |

### 6. Actions 未触发 / 部署被跳过

- `deploy.yml` 只在 **push tag `v*`** 或手动触发；普通 push 只跑 CI。
- `DEPLOY_ENABLED` 必须放在 **Variables**（不是 Secrets）且为 `true`，否则 SSH job 跳过。
- Fork 仓库默认不运行上游 Actions，在自己的 fork 推送即可。

### 7. SSH 部署失败

- `SERVER_SSH_KEY` 必须是私钥全文（含 `BEGIN/END` 行），无多余空格。
- 服务器 `~/.ssh` 权限 700、`authorized_keys` 600。
- SSH 用户需在 `docker` 组：`sudo usermod -aG docker <user>`（重新登录生效）。
- 内网服务器需 GitHub 可达（公网 IP 或反向隧道）。

## 仍需在你服务器上核验的环节

以下环节无法在当前开发环境完成，需要你在真实服务器上执行并反馈：

| # | 环节 | 命令 / 判定 |
|---|---|---|
| 1 | 服务器能克隆仓库 | `git clone https://github.com/kkk287/ctf-platform.git` 成功 |
| 2 | 服务器能构建并启动 | `docker compose up -d --build` 后 `docker compose ps` 为 `healthy` |
| 3 | 健康检查 | `curl -fsS http://<IP>:<端口>/actuator/health` 返回 `{"status":"UP"}` |
| 4 | 页面可访问 | 浏览器打开 `http://<IP>:<端口>/`，登录 `alice / Alice@123` |
| 5 | GHCR 拉取 | Actions 至少成功跑一次 Deploy；`docker compose pull` 不报权限错误 |
| 6 | 自动部署链路 | 配好 Secrets/Variables 后打 `v*` tag，容器被自动更新 |
| 7 | 重启自恢复 | `sudo reboot` 后容器自动 `Up`，数据卷内容仍在 |
| 8 | 对外端口/域名 | 安全组已放行；如需 HTTPS，Nginx/Caddy 反代与证书 |
| 9 | MySQL/PostgreSQL（可选） | 切 profile 后 Flyway 建表成功 |

需要你提供的服务器信息：**公网 IP/域名、SSH 用户与端口、对外服务端口（或域名）、是否使用 MySQL/PostgreSQL**。

## 接口一览

统一响应体 `{"code":0,"message":"OK","data":...}`；业务错误 `code != 0` 且 HTTP 状态同步（400/401/403/404/409/429/500）。
除登录、注册、静态页、Swagger、健康检查外均需 `Authorization: Bearer <JWT>`。

| 方法 | 路径 | 说明 | 权限 |
|---|---|---|---|
| POST | `/api/auth/register` | 注册（返回 token） | 公开 |
| POST | `/api/auth/login` | 登录 | 公开 |
| POST | `/api/auth/logout` | 登出并吊销当前 token（jti 黑名单） | 登录 |
| GET | `/api/auth/me` | 当前用户 | 登录 |
| GET | `/api/events` | 赛事 / 练习区列表 | 登录 |
| GET | `/api/events/{id}` | 赛事详情 | 登录 |
| GET | `/api/announcements` | 公告 | 登录 |
| GET | `/api/challenges?eventId=&category=&solved=&keyword=` | 题目列表（含我的解出状态/当前分值） | 登录 |
| GET | `/api/challenges/{id}` | 题目详情 | 登录 |
| GET | `/api/challenges/{id}/attachment` | 下载附件 | 登录 |
| POST | `/api/challenges/{id}/submit` | 提交 flag | 登录 |
| POST | `/api/challenges/{id}/hints/{hintId}/unlock` | 解锁提示（扣分） | 登录 |
| GET | `/api/scoreboard?eventId=&type=USER\|TEAM` | 个人 / 战队榜 | 登录 |
| GET | `/api/scoreboard/recent?eventId=` | 最近解题动态 | 登录 |
| GET | `/api/me/profile` | 我的战绩 | 登录 |
| POST | `/api/teams` · `/api/teams/join` · `/api/teams/leave` | 战队创建/加入/退出 | USER |
| GET | `/api/teams/my` | 我的战队 | 登录 |
| DELETE | `/api/teams/my/members/{userId}` | 队长移除成员 | USER |
| GET/POST/PUT/DELETE | `/api/admin/challenges...` | 题目管理（含附件、提示） | ADMIN / JUDGE |
| GET/POST/PUT/DELETE | `/api/admin/events...` | 赛事管理（namespace 全局唯一） | ADMIN / JUDGE |
| GET | `/api/admin/users?keyword=&page=&size=` | 用户分页搜索 | ADMIN / JUDGE |
| PATCH | `/api/admin/users/{id}/ban` | 封禁 / 解封 | 仅 ADMIN |
| PATCH | `/api/admin/users/{id}/role` | 变更用户角色 | 仅 ADMIN |
| GET | `/api/admin/audit-logs?action=&page=&size=` | 操作审计日志 | ADMIN / JUDGE |
| GET | `/api/admin/submissions?...` | 提交审计 | ADMIN / JUDGE |
| GET | `/api/admin/anomalies` | 异常告警 | ADMIN / JUDGE |
| GET/POST/PUT/DELETE | `/api/admin/announcements...` | 公告管理 | ADMIN / JUDGE |
| GET/POST | `/api/awd/...` · `/api/admin/awd/...` | AWD 轮次、队伍、check、实时比分与 attack | 登录 / ADMIN |
| GET | `/api/public/awd/rounds/current?eventId=` | 大屏匿名当前轮次比分（不含 flag、攻击明细与实例地址） | 公开 |
| GET | `/api/public/awd/rounds/{id}/scoreboard` | 大屏匿名指定轮次比分 | 公开 |

> `/api/public/**` 仅用于大屏/匿名展示：返回队名、攻防得分、总分、排名、轮号与状态；**不返回 flag、攻击记录明细、靶机 host/port/containerId**。需要 flag 等敏感字段的接口仍走 JWT。

## 配置项

| 环境变量 | 默认值 | 说明 |
|---|---|---|
| `SERVER_PORT` | `8080` | 应用端口 |
| `APP_PORT` | `8080` | Docker 宿主机映射端口 |
| `SPRING_PROFILES_ACTIVE` | `h2` | `h2` / `mysql` / `postgres` |
| `JWT_SECRET` | 空 | 空时启动生成临时密钥（重启后旧 token 失效）；生产务必设置 ≥32 字节随机串 |
| `JWT_EXPIRATION_HOURS` | `8` | token 有效期 |
| `DEMO_SEED_ENABLED` | `true` | 数据库为空时写入演示数据 |
| `UPLOAD_DIR` | `./data/uploads` | 附件目录（容器内 `/app/data/uploads`） |
| `MAX_ATTACHMENT_SIZE` | `20971520` | 附件大小上限（字节） |
| `MAX_WRONG_ATTEMPTS` | `5` | 限流窗口内错误上限 |
| `WRONG_WINDOW_MINUTES` | `5` | 限流统计窗口 |
| `SUBMIT_COOLDOWN_SECONDS` | `5` | 错误后冷却时间 |
| `DB_URL` / `DB_USERNAME` / `DB_PASSWORD` | — | MySQL / PostgreSQL profile 使用 |
| `REDIS_ENABLED` | `false` | 开启后 JWT 吊销黑名单存 Redis（`jwt:revoked:{jti}`）；关闭时用内存实现，重启后吊销列表清空 |
| `REDIS_HOST` / `REDIS_PORT` / `REDIS_PASSWORD` | `127.0.0.1` / `6379` / 空 | Redis 连接参数 |
| `MONGO_ENABLED` | `false` | 开启后审计日志在事务提交后异步镜像到 MongoDB `op_logs` 集合 |
| `MONGO_URI` | `mongodb://127.0.0.1:27017/ctf` | MongoDB 连接串 |

完整示例见 `.env.example`。

## 已知限制

- 动态靶机与 AWD：真实 Docker 实例下发已在本机实测通过（应用挂载 `docker.sock` + `RANGE_DOCKER_ADDRESS=host.docker.internal`），含真实 HTTP 探活、轮内 flag 写回与 STOP 回收；默认仍以 noop 降级运行，启用真实下发需 `RANGE_ENABLED=true`。
- 不做 CTFd 插件/主题生态兼容。
- 不做 OAuth 第三方登录，仅账号密码 + JWT。
- 防作弊为基础版：留存 IP/UA 并给出同 IP 多账号告警，不做设备指纹或复杂风控。
- 赛事状态由起止时间实时派生，不支持手动暂停/加时（可编辑时间实现）。

## 文档索引

| 文档 | 内容 |
|---|---|
| [docs/DESIGN.md](docs/DESIGN.md) | v2.0 重构总体设计：需求映射、技术选型、数据模型、分期路线图与 P1 落地状态 |
| [docs/PLAN.md](docs/PLAN.md) | 需求拆解、状态机、数据模型、接口/页面清单、需求-实现映射 |
| [docs/DEPLOY.md](docs/DEPLOY.md) | 本地 / Docker / Actions + 服务器部署全流程、Secrets、故障排查 |
| [docs/DEMO.md](docs/DEMO.md) | 5-8 分钟演示脚本（赛事 + 练习区各走一遍） |
| [docs/AI_USAGE.md](docs/AI_USAGE.md) | AI 工具/模型使用说明、提示词模板、人工复核点与局限 |
| [docs/verification.md](docs/verification.md) | 实测证据（测试、启动、完整链路、Docker、浏览器） |
| [prompts/](prompts/) | 实际使用的提示词记录 |
