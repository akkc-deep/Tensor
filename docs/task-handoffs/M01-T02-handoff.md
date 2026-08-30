# Next Task Handoff

## Handoff Type

next-task

## Task Link

- **Task board:** `docs/task-handoffs/tensor-v1-task-board.md`
- **Completed task:** `M01-T01`
- **Next task:** `M01-T02`
- **Design document:** `docs/task-designs/M01-T02-designs.md`
- **Expected next status:** `READY`

## Next Task

- **Task ID:** `M01-T02`
- **Title:** Java 21、Boot 3.5.x 和测试依赖管理
- **Goal:** 在现有五模块 Maven reactor 上锁定 Java release 21、Spring Boot 3.5.16、编译/测试插件和测试库版本，并按模块职责声明内部、Spring、数据库和测试依赖。
- **Scope:** 只修改 `data-plane/pom.xml` 与五个现有子模块 POM；保留 M01-T01 的父子坐标、模块顺序和旧 `Main.java`。不创建源码、测试、资源或配置，不实现 M01-T03 的 Enforcer、ArchUnit 测试和禁止 Git 能力门禁，不修改前端或 docs 实施基线。
- **Acceptance:** 设计中的精确属性、Boot/Testcontainers BOM、内部与测试库 dependency management、Compiler/Surefire/Failsafe plugin management 和五个模块依赖清单全部成立；结构 RED/GREEN、父级管理契约、effective POM、六模块空测试、Java 21、范围与格式门禁得到设计注明的预期结果；仅六个 POM提交为 `build: lock backend runtime and test dependencies`。

## Dependencies

### `M01-T01`

- **Artifact:** 提交 `09a5c65302b203c967b6eeb7540cd47cfbd1a78c` 中的 `data-plane/pom.xml`、`data-plane/tensor-plugin-api/pom.xml`、`data-plane/tensor-core/pom.xml`、`data-plane/tensor-plugin-tushare/pom.xml`、`data-plane/tensor-plugin-fixture/pom.xml`、`data-plane/tensor-app/pom.xml`。
- **Decision:** 父工程坐标固定为 `com.akkc.tensor:data-plane:1.0-SNAPSHOT`、packaging 为 `pom`；模块顺序固定为 plugin-api、core、plugin-tushare、plugin-fixture、app；五个子 POM继承同一父坐标并只声明目录同名 artifactId。
- **Rationale:** TRD 3.3 用 Maven reactor 固定五模块边界和后续依赖方向，M01-T01 先提供不夹带版本、依赖或架构门禁的最小基线。
- **Constraint:** M01-T02 只能在六个 POM中增加设计明确的版本、BOM、插件和依赖管理；不得改变坐标、模块顺序、默认子模块 jar packaging、旧 Main 或 control-plane，也不得提前实现 M01-T03。
- **Usage:** 以现有六 POM为唯一编辑基线，按 `docs/task-designs/M01-T02-designs.md` 精确加入 11 个属性、两项 BOM import、内部/测试依赖管理、三个插件管理项和五模块依赖清单。
- **Readiness evidence:** M01-T01 在权威看板中为 `COMPLETED`；提交范围仅六个 POM，最终 `mvn validate` 的六项目 reactor 为 6/6 `SUCCESS`，结构、坐标、模块顺序、旧入口、范围与格式门禁均退出码 0。

## Start Here

1. `docs/task-designs/M01-T02-designs.md` 全文。
2. `docs/task-handoffs/tensor-v1-task-board.md` 的 M01-T02 行与任务详情。
3. `docs/superpowers/plans/tensor-modules/M01-backend-foundation.md` 的 Task M01-T02。
4. `docs/design/Tensor_多源证券数据平台_TRD_v1.0.md` 第 4 节。
5. `data-plane/pom.xml` 与五个子模块 `pom.xml`。
6. 设计依赖表记录的 Maven Central `spring-boot-dependencies`、WireMock 和 ArchUnit 版本裁决。
7. **First action:** 在修改任何 POM 前，运行设计“如何测试”的第一段无落盘 Python 结构契约，记录它因当前父 POM缺少目标 properties/dependency management/build 结构而退出 1；确认失败来自缺失交付物后再进入最小实现。

## Risks

- 首次解析 Boot 3.5.16、测试库和 Maven 插件可能需要 Maven Central 与本地仓库写权限；网络/权限错误必须作为环境问题处理，不能替代结构 RED 或最终 GREEN。
- 设计中的版本以 2026-08-31 读取的 Maven Central 稳定元数据为准；实施不得重新选择 4.x、milestone、beta 或其他补丁。
- 当前工作区仍有未提交 docs 变更；实施只能精确暂存六个 POM，不能使用会混入文档的宽泛暂存操作。
- 旧 `com.akkc.Main` 在父工程为 `pom` 后仍不参与 reactor 编译，这是延后到 M09 的既定边界，不得在 M01-T02 顺手删除或替换。
