# Next Task Handoff

## Handoff Type

next-task

## Task Link

- **Task board:** `docs/task-handoffs/tensor-v1-task-board.md`
- **Completed task:** `M01-T02`
- **Next task:** `M01-T03`
- **Design document:** `docs/task-designs/M01-T03-designs.md`
- **Expected next status:** `READY`

## Next Task

- **Task ID:** `M01-T03`
- **Title:** Maven Enforcer、ArchUnit 和禁止 Git 能力门禁
- **Goal:** 在 M01-T02 的 Java 21 / Spring Boot 3.5.16 六项目 reactor 上建立 Enforcer 依赖禁令、ArchUnit 模块依赖规则和生产源码/文本资源 Git 能力扫描。
- **Scope:** 只修改 `data-plane/pom.xml`，并创建 `data-plane/tensor-app/src/test/java/com/akkc/tensor/architecture/ModuleDependencyTest.java` 与 `ForbiddenGitCapabilityTest.java`；不修改业务 Java、资源、配置、子模块 POM、旧 Main 或前端，不引入任何运行时 Git 能力。
- **Acceptance:** 父 POM 的 Enforcer 3.6.3 配置与七项 excludes 通过结构契约；四条 ArchUnit 规则和十个禁止能力反例/允许正例按设计运行；`validate`、聚焦 test、全 reactor test 与 `verify` 均退出 0，`tensor-app` 为 13/13 tests passed；提交仅含三个目标文件且消息为 `test: enforce backend architecture boundaries`。

## Dependencies

### `M01-T02`

- **Artifact:** 提交 `6e692b9229cba8bbe5e83307402bcc5d1bfad14c` 中的 `data-plane/pom.xml` 与五个子模块 POM。
- **Decision:** Java release 固定为 21，Spring Boot 固定为 3.5.16，Compiler 为 3.14.1，Surefire/Failsafe 为 3.5.6，ArchUnit 为 1.5.0；父 POM集中管理版本、BOM和三个构建插件，子 POM只声明本模块所需依赖。
- **Rationale:** M01-T02 先提供可解析、可测试的稳定六模块依赖基线，使 M01-T03 能只增加架构与 Git 禁止门禁，不重复选择或改写运行时/测试栈。
- **Constraint:** 保留 M01-T02 的 11 个既有属性、dependency management、plugin management、五模块依赖和固定顺序；本任务只在父 POM追加 Enforcer 版本/执行，并在 app 测试目录新增两个测试。不得修改其他五个 POM或把 docs 混入实施提交。
- **Usage:** 以该提交的父 POM作为唯一编辑基线，复用 app 已有的 JUnit Jupiter、AssertJ 与 ArchUnit test 依赖，按 `docs/task-designs/M01-T03-designs.md` 增加 Enforcer 和两个门禁测试。
- **Readiness evidence:** M01-T02 在权威看板中为 `COMPLETED`；提交仅含六个 POM；两项 XML 契约、effective POM、Java 21、旧入口/范围/格式检查均退出 0，最终 `mvn test` 六项目为 6/6 `SUCCESS`；任务级审查 `Approved`，最终整体审查 `Ready to merge: Yes`。

## Start Here

1. `docs/task-designs/M01-T03-designs.md` 全文。
2. `docs/task-handoffs/tensor-v1-task-board.md` 的 M01-T03 行与任务详情。
3. `docs/superpowers/plans/tensor-modules/M01-backend-foundation.md` 的 Global Constraints、Task M01-T03 和 Module Gate。
4. `docs/design/Tensor_多源证券数据平台_TRD_v1.0.md` 的 1.4、3.3、16.2、20.1。
5. 提交 `6e692b9229cba8bbe5e83307402bcc5d1bfad14c` 的 `data-plane/pom.xml` 与 `data-plane/tensor-app/pom.xml`。
6. 设计依赖表记录的 Maven Enforcer 3.6.3 官方规则语义与本地 ArchUnit 1.5.0 API 证据。
7. **First action:** 修改任何 POM或测试文件前，运行设计“如何测试”的无落盘 Python 结构契约，记录它因当前父 POM缺少 Enforcer 属性/插件且两个测试文件不存在而退出 1；确认失败来自缺失交付物后再进入最小实现。

## Risks

- 首次解析 Maven Enforcer Plugin 3.6.3 可能需要 Maven Central 与本地 Maven 缓存写权限；网络/权限失败是环境问题，不能替代 GREEN。
- 当前没有模块生产类，ArchUnit 规则必须逐条使用 `allowEmptyShould(true)`；不得全局关闭空规则失败，后续生产类出现后同一规则会实际检查依赖。
- 源码扫描只处理设计列出的生产文本后缀并排除测试与 target；实现若扩大到二进制资源或整个仓库会产生无关误报。
- 任务准备文档与实施提交必须保持分离；开始 M01-T03 前重新检查工作区，实施只能精确暂存父 POM与两个测试，不能使用宽泛暂存。
