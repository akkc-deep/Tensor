# Next Task Handoff

## Handoff Type

next-task

## Task Link

- **Task board:** `docs/task-handoffs/tensor-v1-task-board.md`
- **Completed task:** `M13-T01`
- **Next task:** `M13-T02`
- **Design document:** `docs/task-designs/M13-T02-design.md`
- **Expected next status:** `READY`; transition from `NOT_STARTED` only after this handoff is written and linked.

## Next Task

- **ID:** `M13-T02`
- **Title:** 单个可执行 JAR 打包和内容检查
- **Goal:** 把 M13-T01 生成的前端静态资源和当前生产后端模块打包为唯一主产物 `tensor-app-1.0-SNAPSHOT.jar`，通过标准 Spring Boot launcher 直接启动，并在 repackage 后自动验证前端、49 个 Tushare YAML、V1–V5 migration 及 fixture/测试/凭证排除边界。
- **Scope:** 只修改 `data-plane/tensor-app/pom.xml` 并创建 `data-plane/tensor-app/src/test/java/com/akkc/tensor/build/PackagedJarContractTest.java`；注册生成主资源、配置 Spring Boot 3.5.16 repackage、用 Surefire exclusion 与 Failsafe 3.5.6 固定内容合同的执行时机。保持 Tushare YAML 在内层模块 JAR，不修改根/其他模块 POM、生产 Java/YAML/SQL、前端或 M13-T03/T04 行为，也不提交生成物或读取 Git 元数据。
- **Acceptance criteria:** `mvn -f data-plane/pom.xml -pl tensor-app -am clean verify` 按前端构建、后端测试、Boot repackage、Failsafe 内容合同顺序退出 0；唯一 app 主 JAR包含 Boot launcher/入口、index 及其引用的哈希 JS/CSS、三个 Tensor 生产模块、恰好 49 个不重复 Tushare YAML 和精确 V1–V5，同时不含 fixture 模块/YAML/V6、Tushare 测试 YAML、测试 class/report 或凭证文件；打包配置仍只引用环境变量；实现提交精确为一新增、一修改且消息为 `build: package Tensor as one executable jar`。

## Dependencies

### `M09-T06`

- **Artifact:** `docs/task-designs/M09-T06-design.md`；当前 `data-plane/tensor-app/src/main/java/com/akkc/tensor/TensorApplication.java`、`data-plane/tensor-app/src/main/resources/application.yml`、`data-plane/tensor-app/src/main/resources/db/migration/V1__create_basic_and_organization_tables.sql` 至 `V5__create_corporate_and_governance_tables.sql`，以及 test-only `data-plane/tensor-app/src/test/resources/db/migration/V6__create_fixture_tables.sql`。
- **Decision:** 生产入口固定为根包 `TensorApplication`；数据库密码与 Tushare Token 只由 `TENSOR_DB_PASSWORD`、`TENSOR_TUSHARE_TOKEN` 环境变量注入；生产 migration 只有 V1–V5，fixture V6 只位于 test resources，fixture 模块只用于测试/验收。
- **Rationale:** 可执行 JAR必须启动既有完整 Servlet Bean 图并保留生产配置与 schema，同时不能把验收数据源、测试 DDL 或真实秘密带入生产 classpath。
- **Constraint:** 不修改入口、`application.yml`、V1–V6、生产 Bean 图或秘密边界；最终 JAR的 `Start-Class` 必须指向既有入口，配置必须原样保留环境占位符，V6 和 fixture 必须缺席。
- **Usage:** Boot repackage 把 app classes、配置和 V1–V5 放入 `BOOT-INF/classes`；打包合同核对 manifest、入口、精确 migration 集合、环境占位符和 fixture/测试排除项。
- **Readiness evidence:** 权威看板记录 M09-T06 为 `COMPLETED`；实现提交 `d7a47f3` 已通过普通 18/18、受影响回归 51/51、生产上下文 1/1、schema 联跑 53/53、默认 reactor 338/338 及秘密/JAR/范围门禁。当前入口、配置和 migration 相对该完成提交无差异，生产目录恰有 V1–V5，V6 仍只在 test resources。

### `M13-T01`

- **Artifact:** `docs/task-designs/M13-T01-design.md`；当前 `data-plane/tensor-app/pom.xml`、`data-plane/tensor-app/src/test/java/com/akkc/tensor/build/FrontendResourceBuildTest.java` 和 Maven 生成接口 `data-plane/tensor-app/target/generated-resources/static`。
- **Decision:** app 的 `generate-resources` 使用 frontend-maven-plugin 1.15.4、Node v24.15.0、npm 11.12.1，按 `npm ci → 20 files / 120 tests → Vite build → maven-resources-plugin 3.4.0 unfiltered copy` 产生 index 和哈希资源；生成目录是 M13-T02 的唯一前端输入。
- **Rationale:** 最终 JAR必须只消费经过固定工具链、lockfile 和完整单测验证的真实 Vue 生产输出，不能使用手工或陈旧 dist。
- **Constraint:** 不修改 M13-T01 的插件版本、working/install/output directory、execution 顺序、前端、lockfile 或既有资源合同；任一前端步骤失败时不得进入后端测试、repackage 或内容合同。
- **Usage:** 在 app `<build><resources>` 中把 `${project.build.directory}/generated-resources` 加为未过滤主资源；process-resources 将 `static` 放入 classes，打包合同再验证 JAR内 index 及其引用的哈希 JS/CSS。
- **Readiness evidence:** 权威看板记录 M13-T01 为 `COMPLETED`；实现提交 `3296877` 与修复 `aa7f55d` 已通过严格 RED、前端 120/120、Vite build、Java 2/2、完整 reactor 79/75/93/12/81、资源/范围/禁止 Git 门禁及最终范围化复审。当前 POM和资源合同相对修复提交无差异。

两个直接依赖没有未解决冲突：M09-T06 固定 Boot 入口、生产后端资源、test-only fixture 和环境凭证边界，M13-T01 固定前端生成时机与目录；M13-T02 只在二者之后用标准 Maven resources 与 Boot repackage 组装，不复制模块资源、不改变运行行为。M09-T06 的 CSP 还要求外部哈希资源，正与 M13-T01 的 index/哈希 JS/CSS 输出一致。

## Start Here

按以下顺序读取：

1. `docs/task-designs/M13-T02-design.md`
2. `docs/task-handoffs/M13-T02-handoff.md`
3. `docs/task-handoffs/tensor-v1-task-board.md` 的 M13-T02 行与详情
4. `docs/superpowers/plans/tensor-modules/M13-packaging-runbook.md` 的 Global Constraints、Task M13-T02 和 Module Gate
5. `data-plane/tensor-app/pom.xml`、`TensorApplication.java`、`application.yml`、主/测试 migration 及两个现有 build contract 测试
6. `data-plane/tensor-plugin-tushare/pom.xml`、49 个主 YAML、两个 test YAML 和 `data-plane/tensor-plugin-fixture/pom.xml`/fixture YAML
7. `docs/task-designs/M09-T06-design.md`、`docs/task-designs/M13-T01-design.md` 及 Dependencies 中列出的当前消费产物

首个实施动作：保留范围外 `.idea/misc.xml`，确认两个目标实现文件没有重叠改动；只创建设计规定的完整 `PackagedJarContractTest.java`，保持 POM 不变，运行设计 Tests 节的 `clean -Dtest=PackagedJarContractTest -Dsurefire.failIfNoSpecifiedTests=false test`，取得只因 `target/tensor-app-1.0-SNAPSHOT.jar` 尚未生成而失败的严格 RED。`clean` 只清理并重建 Maven `target` 生成物，任何生成文件都不得暂存或提交。

## Risks

- 最终 49 个 YAML位于 `BOOT-INF/lib/tensor-plugin-tushare-1.0-SNAPSHOT.jar` 内；外层 `jar tf` 不展开它，Java 合同必须读取内层流并验证总数、去重数和 app classes 无副本。
- Surefire exclusion 与 Failsafe include 决定合同只能在 repackage 后运行一次；最终日志和报告必须同时证明 Surefire 未运行、Failsafe 1/1。
- Boot 的“单个 JAR”指 app 唯一主 `.jar`；依赖模块各自的 reactor JAR和 Boot 的 `.jar.original` 是 Maven 中间产物，不得误判为第二个生产主 JAR。
- `clean verify` 会重建当前未跟踪的 Maven `target` 并运行完整前后端回归；不得暂存这些生成物。沙箱若仅阻止 Mockito/Byte Buddy self-attach，只能在正常 JVM 权限下原样重跑，不能 skip 或修改测试。
