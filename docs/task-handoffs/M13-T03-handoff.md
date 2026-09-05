# Next Task Handoff

## Handoff Type

next-task

## Task Link

- **Task board:** `docs/task-handoffs/tensor-v1-task-board.md`
- **Completed task:** `M13-T02`
- **Next task:** `M13-T03`
- **Design document:** `docs/task-designs/M13-T03-design.md`
- **Expected next status:** `READY`; transition from `NOT_STARTED` only after this handoff is written and linked.

## Next Task

- **ID:** `M13-T03`
- **Title:** 生产配置、CORS、SPA fallback 和优雅停机
- **Goal:** 让 M13-T02 打入可执行 JAR 的 Vue 控制面在 `/downloads`、`/datasets` 和其他批准的无扩展名 UI 路径直接刷新时通过 `forward:/index.html` 恢复，同时让 API、Actuator、assets 和文件型路径保持真实资源/404；生产默认关闭 CORS，开发只按一个精确 origin 开放 `/api/v1/**`，并启用覆盖现有写事务上限的 graceful shutdown。
- **Scope:** 只修改 `data-plane/tensor-app/src/main/resources/application.yml`，创建 `data-plane/tensor-app/src/main/java/com/akkc/tensor/config/SpaWebConfiguration.java` 和 `data-plane/tensor-app/src/test/java/com/akkc/tensor/config/ProductionWebConfigurationTest.java`。增加 `${TENSOR_DEV_CORS_ALLOWED_ORIGIN:}`、`server.shutdown=graceful`、每阶段 `70s`，实现已验证 PathPattern 的 GET/HEAD SPA forward 和 fail-closed CORS；不修改安全 Filter、Controller、POM、前端、业务模块、migration、runbook 或代理配置，不提交生成物。
- **Acceptance criteria:** `/`、两个业务页面和批准的未知 UI 路径内部 forward 到入口且不重定向；真实入口/哈希资源继续可用，未知 `/api/v1/**`、Actuator、assets 和带扩展名路径为空 404；现有六安全头和 `no-store|no-cache|immutable` 缓存策略保持；CORS 默认关闭，显式单一非 wildcard origin 只允许 GET/POST/OPTIONS、`Content-Type|X-Request-Id`、暴露 `X-Request-Id` 且 credentials false；YAML 精确保留 `120s < 130s <= proxy` 边界和 health-only exposure；聚焦测试、完整 `clean verify`、既有 Failsafe 4/4、范围/格式/跟踪门禁通过；实现提交精确三文件且消息为 `feat(app): configure production web delivery`。

## Dependencies

### `M09-T06`

- **Artifact:** `docs/task-designs/M09-T06-design.md`；当前 `data-plane/tensor-app/src/main/resources/application.yml`、`data-plane/tensor-app/src/main/java/com/akkc/tensor/config/WebSecurityHeadersConfiguration.java`、`data-plane/tensor-app/src/main/java/com/akkc/tensor/web/RequestIdFilter.java` 和 `data-plane/tensor-app/src/main/java/com/akkc/tensor/web/GlobalExceptionHandler.java`。
- **Decision:** Servlet 生产应用只从既有七个环境变量读取数据库/Tushare配置；Actuator discovery 关闭且默认只公开 health/liveness/readiness；窄 `MissingResourceHandler` 把缺失 handler/resource 变为空 404；请求 Filter 固定六个安全头，并按原始 path 对入口/API/Actuator使用 `no-store`、其他 UI 使用 `no-cache`、assets 使用一年 public immutable。
- **Rationale:** M13-T03 必须在已稳定的安全生产 Web 基线上增加 SPA/CORS/停机，而不能复制 header、改变错误 DTO、扩大 Actuator 或让 fallback 把真实 404 伪装为 HTML 成功。
- **Constraint:** 不修改 `WebSecurityHeadersConfiguration`、`RequestIdFilter`、`GlobalExceptionHandler` 或现有七个环境变量及其值；新增 dev origin 是 M13-T03 明确批准的第八个环境入口，只能为空或单一精确非 wildcard origin，不得携带秘密或改变现有配置。forward 必须保留原始路径上的既有 Cache-Control。
- **Usage:** 在现有 YAML 上只追加 `tensor.web` 与 lifecycle/server 三项值；MockMvc fixture 使用真实安全 Filter验证缓存/安全头，未知资源继续消费现有 404 行为；CORS 只由新 MVC 配置提供。
- **Readiness evidence:** 权威看板记录 M09-T06 为 `COMPLETED`；实现提交 `d7a47f3` 最终通过普通 18/18、受影响回归 51/51、生产上下文 1/1、schema 联跑 53/53、默认 reactor 338/338、五项 mutation 及秘密/Actuator/JAR/范围门禁。当前直接消费文件没有工作树差异。

### `M13-T02`

- **Artifact:** `docs/task-designs/M13-T02-design.md`；当前 `data-plane/tensor-app/pom.xml`、`data-plane/tensor-app/src/test/java/com/akkc/tensor/build/PackagedJarContractTest.java`、`data-plane/tensor-app/target/tensor-app-1.0-SNAPSHOT.jar` 和生成后的 `target/classes/static/index.html`/哈希 assets。
- **Decision:** Spring Boot 3.5.16 以标准嵌套 JAR 形成唯一 app 主产物；M13-T01 的真实 Vue 生产输出进入 `BOOT-INF/classes/static`；打包合同在 repackage 后由 Failsafe 打开外层/内层 JAR验证入口、哈希资源、49 个 Tushare YAML、V1–V5 和 fixture/测试/凭证排除边界。
- **Rationale:** SPA forward 必须指向已经打包、测试并由单 JAR 在生产 classpath 服务的同一个 `index.html`，不能读取前端源码、手工文件或陈旧 `dist`；M13-T03 完整回归还必须保护打包结果。
- **Constraint:** 不修改 app POM、前端流水线、Boot repackage、资源位置或 `PackagedJarContractTest`；聚焦/完整生命周期仍先运行前端 120/120，最终 `clean verify` 必须保留 Failsafe 4/4。所有 `target` 只是未跟踪生成物，不能暂存。
- **Usage:** 生产 controller 返回 `forward:/index.html` 让 Boot 静态 handler读取同一 classpath 入口；测试从生成后的真实 index 动态解析一个被引用的 hash asset，完整验证后重新 repackage 并运行原打包合同。
- **Readiness evidence:** 权威看板记录 M13-T02 为 `COMPLETED`；实现 `c9ae5b1`、所有权修复 `9c34e6e`、合同强化 `0032094` 最终在正常 JVM 权限下通过前端 120/120、plugin-api 79、core 75、Tushare 93、fixture 12、app Surefire 81、Failsafe 4/4 和 reactor success。当前 POM无工作树差异，JAR与生成入口均可定位。

两个直接依赖没有未解决冲突：M09-T06 冻结安全 header、缓存、404、health-only 管理面和既有环境配置，M13-T02 冻结同一进程可读取的入口/assets 和完整打包回归；M13-T03 只增加显式路由、一个受限开发 origin 和生命周期值。新增第八个 dev-only 环境变量是后继任务批准的范围扩展，不改写 M09-T06 的七项凭证/运行配置；`no-store`/`no-cache`/immutable 直接复用原 Filter，不与 forward 或打包资源冲突。

## Start Here

按以下顺序读取：

1. `docs/task-designs/M13-T03-design.md`
2. `docs/superpowers/plans/2026-09-05-m13-t03-production-web.md`
3. `docs/task-handoffs/M13-T03-handoff.md`
4. `docs/task-handoffs/tensor-v1-task-board.md` 的 M13-T03 行与详情
5. `docs/superpowers/plans/tensor-modules/M13-packaging-runbook.md` 的 Global Constraints、Task M13-T03 和 Module Gate
6. 当前 `application.yml`、`WebSecurityHeadersConfiguration.java`、app POM、两个 build contract 测试及 `target/classes/static/index.html` 和其引用的哈希资源
7. `docs/task-designs/M09-T06-design.md`、`docs/task-designs/M13-T02-design.md` 及 Dependencies 中列出的当前消费产物

首个实施动作：保留范围外 `.idea/misc.xml` 和全部 Maven `target`，确认三个目标实现路径仍无重叠修改；只按计划 Step 2 创建完整 `ProductionWebConfigurationTest.java`，保持生产类不存在且 YAML 不变，然后运行聚焦命令，取得前端 120/120 和资源复制成功后、app 只因 `SpaWebConfiguration`/嵌套 controller 尚不存在而 testCompile 失败的严格 RED。不要提交 RED，不能把任何生成物加入 Git。

## Risks

- `SpaForwardController` 必须使用 Spring 6.2.19 已探针验证的单段负向正则和尾部 `{*rest}`；普通 catch-all 会抢占 `/assets/**` 静态资源，资源 resolver 直出 index 又不满足 `forward:/index.html`，两者均禁止。
- 多段路径中任一点号都会得到真实 404；这是批准边界而非缺陷。未来前端若新增带点号路由，必须先重新设计。
- `timeout-per-shutdown-phase=70s` 是每个阶段而非 JVM 总时限；M13-T04 仍需给外部进程管理器配置足够的强制终止窗口，并记录代理响应超时 `>=130s`。
- 配置非空错误 origin 会因精确匹配自然 fail-closed，精确 `*` 则启动失败；不得改为 origin pattern、多值列表或悄悄回退任意来源。
- 聚焦和完整 Maven 生命周期都会重建前端并产生大量未跟踪 `target`；必须精确暂存三个实现文件。沙箱若仅阻止 Mockito/Byte Buddy self-attach，只能在正常 JVM 权限下原样重跑，不能 skip 或改配置。
