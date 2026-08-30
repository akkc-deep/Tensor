# M08 Fixture Plugin Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 提供只在验收配置启用的确定性插件，验证核心流程不依赖 Tushare 实现。

**Architecture:** Fixture 使用与生产插件完全相同的 SPI、注册、适配、持久化和查询路径。场景由验收参数选择，但 production profile 不注册 fixture Bean 或迁移。

**Tech Stack:** Java 21、Spring conditional configuration、JUnit 5、Testcontainers。

**Spec:** `docs/superpowers/specs/2026-08-25-tensor-module-roadmap-design.md`

## Global Constraints

- 仅 `tensor.plugins.fixture.enabled=true` 且验收 profile 激活时注册。
- 数据集业务字段固定为 `ts_code`, `trade_date`, `amount`, `note`。
- 支持 success、empty、source failure、type failure、persistence failure；不创建另一套核心流程。

## Project Inputs

候选输入为 fixture 文件及其消费的公共 SPI 和服务接口。Fixture 不依赖 Tushare 或 Vue 实现。

---

### Task M08-T01: Fixture 元数据、插件与适配器（2.5h）

**Files:**
- Create: `data-plane/tensor-plugin-fixture/src/main/java/com/akkc/tensor/plugin/fixture/FixturePlugin.java`
- Create: `data-plane/tensor-plugin-fixture/src/main/java/com/akkc/tensor/plugin/fixture/FixtureConfiguration.java`
- Create: `data-plane/tensor-plugin-fixture/src/main/resources/datasets/fixture/fixture_daily.yaml`
- Test: `data-plane/tensor-plugin-fixture/src/test/java/com/akkc/tensor/plugin/fixture/FixturePluginTest.java`

**Interfaces:** plugin ID `fixture`, API `fixture_daily`, parameter enum `scenario`.

- [ ] Confirm task design; freeze fixture descriptor and profile condition.
- [ ] Write tests for disabled-by-default, enabled registration, exact dataset fields/key/filter and no production activation.
- [ ] Run targeted test and confirm missing plugin failure.
- [ ] Implement plugin/configuration and reuse `GenericDatasetAdapter` rather than a fixture-specific adapter path.
- [ ] Run module tests.
- [ ] Commit as `feat(fixture): add acceptance data-source plugin` when Git exists.

### Task M08-T02: 确定性结果与故障场景（2.5h）

**Files:**
- Create: `data-plane/tensor-plugin-fixture/src/main/java/com/akkc/tensor/plugin/fixture/FixtureScenario.java`
- Create: `data-plane/tensor-plugin-fixture/src/main/java/com/akkc/tensor/plugin/fixture/FixtureEnvelopeFactory.java`
- Test: `data-plane/tensor-plugin-fixture/src/test/java/com/akkc/tensor/plugin/fixture/FixtureEnvelopeFactoryTest.java`

**Interfaces:** `SUCCESS` returns one valid row, `EMPTY` zero rows, `SOURCE_FAILURE` throws `SOURCE_UNAVAILABLE`, `TYPE_FAILURE` returns non-decimal amount, `PERSISTENCE_FAILURE` returns note marker consumed only by test fault injection.

- [ ] Confirm task design; ensure persistence failure injection is unavailable in production profiles.
- [ ] Test each exact scenario envelope/error and stable values `000001.SZ`, `2026-08-07`, `11.23`, nullable note.
- [ ] Run tests and confirm missing factory failure.
- [ ] Implement enum/factory and safe error output.
- [ ] Run module tests.
- [ ] Commit as `feat(fixture): provide deterministic acceptance scenarios` when Git exists.

### Task M08-T03: Fixture 全流程集成（2.0h）

**Files:**
- Create: `data-plane/tensor-app/src/test/java/com/akkc/tensor/fixture/FixtureFlowIT.java`

**Interfaces:** Uses production `PluginRegistry`, `DatasetAdapter`, `PersistenceService`, `DatasetQueryService` without fixture-specific branches.

- [ ] Confirm task design; verify M04 test migration and M05/M06 services are available.
- [ ] Write MySQL integration tests for register → success adapt/write/query, empty no transaction, type failure zero rows and database failure rollback.
- [ ] Run `mvn -pl tensor-app -am -Dtest=FixtureFlowIT test`; expect failure before test wiring is complete.
- [ ] Configure the acceptance profile and fault-injection datasource only in test scope.
- [ ] Re-run the integration test; expect all scenarios pass and production context contains no fixture plugin.
- [ ] Commit as `test(fixture): verify plugin through core data flow` when Git exists.

## Module Gate

Run `mvn -f data-plane/pom.xml -pl tensor-plugin-fixture,tensor-app -am verify`. M08 is complete only when fixture can be enabled without changing core/UI contracts and remains absent from production configuration.
