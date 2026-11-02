# 提示词与实际使用记录（Prompts Usage Log）

> 平台：Trae IDE Agent；模型标识：`DeepSeek-V4.1-Flash`。
> 下表按阶段记录实际下发的关键指令（已压缩为要点，保留约束原意）与产出。

## 阶段 1：初始工程（工单方向，后已被 CTF 方向替换）

**提示词要点**

```
在 e:\trae\sec 从零构建可直接运行、可 GitHub 部署的 Java 业务应用（企业工单流转系统）。
硬性门槛：可启动、完整链路、前端可访问且零 CDN、Docker + compose + Actions、全套文档。
技术规范：Java 17 + Spring Boot 3.2.x + Maven Wrapper、JPA + Flyway + 种子、H2 默认 + MySQL/PG、
JWT + BCrypt + RBAC、统一响应/异常/校验/分页/OpenAPI/Actuator、mvn test 全绿、实测证据。
```

**产出**：工程骨架、基础设施与部分领域代码。
**后续处置**：业务方向变更后，按用户要求删除工单领域代码、页面和文档，重建为 CTF 平台，未保留两套实现。

## 阶段 2：部署优先级调整

**提示词要点**

```
先聚焦"能放到 GitHub、服务器能从 GitHub 拉起来用"主线：
1) 仓库自成闭环，服务器 clone 后 docker compose up -d 可起（或从 GHCR 拉镜像），不依赖本地构建；
2) GitHub 最短路径：Actions 构建测试 + 推送 GHCR + 可选 SSH 部署；README/DEPLOY 写成三步走；
3) 上线后可打开页面完成演示，端口/健康检查/演示账号写清楚，容器可重启自恢复；
4) 其余按原口径继续，但不要在本地环境细节过度投入。
```

**产出**：`Dockerfile`、`docker-compose.yml`、`deploy/docker-compose.server.yml`、`ci.yml`、`deploy.yml`、DEPLOY 三步走设计。
**复核**：本地 Docker 构建与启动实测；Actions 为配置层面完成，SSH 部署待服务器核验。

## 阶段 3：业务场景替换为 CTF 平台

**提示词要点**

```
重要变更：业务不是工单流转，而是 CTF 平台。停止工单方向建模，替换全部代码/页面/文档，删除废弃文件。
范围：注册/登录/JWT/BCrypt；USER/ADMIN；战队；赛事 Event（未开始/进行中/已结束、题目可见性）；
题目（Web/Pwn/Crypto/Misc/Reverse、难度、初始分、附件、hint、可见性、已解出）；
判分（哈希比对、限流防爆破、重复/已解出错误码、动态积分+最低分、一血/二血/三血、hint 扣分）；
排行榜（个人/战队、top N、我的名次、最近动态）；管理后台（题目/赛事/用户/公告/审计）；IP+UA 防作弊；
不做动态靶机、CTFd 插件、OAuth。技术栈与部署要求保持不变。
验证：mvn test 全绿、启动、脚本走完注册→登录→看题→错误 flag→正确 flag→排行榜→管理端建题。
```

**产出**：完整 CTF 领域模型、服务、接口、前端 7 类页面、测试、文档。
**人工复核**：删除旧领域文件（工单实体/服务/页面/文档不再存在）；`grep` 确认无残留工单命名。

## 阶段 4：赛事/练习双模式与双榜确认

**提示词要点**

```
补充确认（都要）：
1) Event 增加 COMPETITION（限时、到点截止、按时间放出、关闭后不可提交）与 PRACTICE（长期开放）；
   练习区也计分上榜，但榜单与赛事分开统计，避免串分。
2) 个人榜与战队榜都要，同一赛事可切换；战队榜采用"成员得分求和、同题只计最高"，写入文档。
3) 前端赛事/练习入口区分、题目按赛事筛选、排行个人/战队切换；管理端可建两种 Event。
4) 数据模型/接口文档写明区分；演示脚本各走一遍。
```

**产出**：`EventType`、派生状态、按 eventId 隔离的榜单、战队去重聚合、前端双 tab、DEMO 双模式脚本。

## 阶段 5：工具授权与验证主线

**提示词要点**

```
已授权按需安装插件/CLI/依赖，无需每次征求同意。
要求：只装必要项、便携优先、先探测已有版本、失败记录原因并给替代方案；
继续 CTF 平台开发与验证主线，拿到"测试通过 + 应用启动 + 业务流程跑通"的证据。
```

**执行记录**：

- 探测：本机有 JDK 24 / JDK 8，无 JDK 17、无 Maven、无 Docker CLI。
- 处理：下载 Temurin JDK 17 到 `C:\Users\34266\.cache\jdk17\...`（便携目录，未改系统环境变量）；使用仓库自带 Maven Wrapper 3.9.9。
- 测试命令：
  `$env:JAVA_HOME='C:\Users\34266\.cache\jdk17\jdk-17.0.20.1+1'; .\mvnw.cmd -B test`
- 失败与修复：
  1. MockMvc 响应默认按 ISO-8859-1 读取导致中文赛事名乱码 → 显式 `getContentAsString(UTF_8)`，请求加 UTF-8 字符集。
  2. 附件下载 `LazyInitializationException`（`attachment()` 未加事务）→ 添加 `@Transactional(readOnly = true)`。
- 结果：见 `docs/verification.md`。

## 阶段 6：文档收口

**提示词要点**

```
交付 README、PLAN、DEPLOY、DEMO、AI_USAGE、verification、prompts；
git init 初始提交，工作区无多余临时文件；
回复先给结论（是否跑通、启动命令、部署三步、演示顺序），再证据与限制，并注明待服务器核验项。
```

**产出**：本目录与 `docs/` 下全部文档；初始提交与 `.gitignore` 清理。
