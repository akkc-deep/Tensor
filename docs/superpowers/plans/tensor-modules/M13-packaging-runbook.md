# M13 Packaging and Runbook Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将已完成的前后端模块构建为一个可执行 JAR，并提供全新环境可复现的配置、启动和健康检查说明。

**Architecture:** Maven 在构建阶段执行确定性前端安装/测试/构建并复制哈希资源到 app 生成资源目录；Spring Boot 负责 API、静态资源和 SPA fallback。生产只运行一个应用进程。

**Tech Stack:** Maven、npm、Vite、Spring Boot Maven Plugin、YAML、Markdown。

**Spec:** `docs/superpowers/specs/2026-08-25-tensor-module-roadmap-design.md`

## Global Constraints

- 构建顺序：`npm ci` → 前端单测 → Vite build → 复制资源 → 后端测试 → Boot repackage → JAR 内容检查。
- 构建不得读取 Git 分支、提交或仓库状态，版本来自 Maven 和显式参数。
- 生产同源服务 Vue 和 `/api/v1`，CORS 默认关闭，`index.html` 不长期缓存。
- 运行只要求 Java 21、MySQL 8.4、数据库变量和可选下载 Token。

## Project Inputs

候选输入为构建、配置、运行说明和已稳定公开产物接口。打包任务不依赖业务模块内部实现。

---

### Task M13-T01: 前端确定性构建与资源复制（2.5h，Maven/XML）

**Files:**
- Modify: `data-plane/tensor-app/pom.xml`
- Create: `data-plane/tensor-app/src/test/java/com/akkc/tensor/build/FrontendResourceBuildTest.java`

**Interfaces:** Maven `generate-resources` produces `tensor-app/target/generated-resources/static/index.html` and hashed assets from `control-plane/dist`.

- [ ] Confirm task design; freeze Node/npm invocation and relative paths without Git calls.
- [ ] Write a build test asserting generated `index.html` and at least one hashed JS/CSS asset.
- [ ] Run `mvn -pl tensor-app -am -Dtest=FrontendResourceBuildTest test`; expect missing resources failure.
- [ ] Configure frontend-maven-plugin or exec plugin to run `npm ci`, unit tests and build, then resources plugin to copy `../../control-plane/dist`.
- [ ] Re-run targeted test and frontend build; expect deterministic install using lockfile.
- [ ] Commit as `build: integrate frontend assets into app` when Git exists.

### Task M13-T02: 单 JAR 与内容检查（2.5h，Maven/Java test）

**Files:**
- Modify: `data-plane/tensor-app/pom.xml`
- Create: `data-plane/tensor-app/src/test/java/com/akkc/tensor/build/PackagedJarContractTest.java`

**Interfaces:** One executable artifact `tensor-app-1.0-SNAPSHOT.jar` contains Boot launcher, static index/assets, 49 YAML and V1–V5 migrations; excludes fixture production DDL and credentials.

- [ ] Confirm task design; freeze required and forbidden JAR entries.
- [ ] Write a JAR ZIP-entry test for Boot classes, static resources, exactly 49 Tushare YAML, five production migrations and no fixture/test resources.
- [ ] Run package contract and confirm it fails before repackage/resource settings are complete.
- [ ] Configure Spring Boot repackage and resource inclusion across plugin modules.
- [ ] Run `mvn -f data-plane/pom.xml clean verify`; inspect JAR with `jar tf` and run the contract test.
- [ ] Commit as `build: package Tensor as one executable jar` when Git exists.

### Task M13-T03: 生产 Web、CORS 与停机配置（2.0h，Java/YAML）

**Files:**
- Modify: `data-plane/tensor-app/src/main/resources/application.yml`
- Create: `data-plane/tensor-app/src/main/java/com/akkc/tensor/config/SpaWebConfiguration.java`
- Create: `data-plane/tensor-app/src/test/java/com/akkc/tensor/config/ProductionWebConfigurationTest.java`

**Interfaces:** `/downloads` and `/datasets` return SPA index; `/api/v1` and `/actuator` never fall back; static hashes cache long, index no-cache; graceful shutdown enabled.

- [ ] Confirm task design; freeze dev-only origin property and proxy/app/upstream timeout ordering.
- [ ] MockMvc-test SPA routes, unknown UI route, API 404, cache headers, CORS off by default/dev allowed origin and health exposure.
- [ ] Run tests and confirm missing SPA config fails.
- [ ] Implement explicit SPA fallback, cache policy, env-driven dev CORS and shutdown/timeout settings.
- [ ] Run app tests and commit as `feat(app): configure production web delivery` when Git exists.

### Task M13-T04: 全新环境运行说明与 smoke test（2.0h，Markdown/Shell）

**Files:**
- Create: `docs/runbook/first-run.md`
- Create: `docs/runbook/configuration.md`
- Create: `scripts/smoke-test.sh`
- Test: `scripts/smoke-test.sh`

**Interfaces:** Runbook requires only Java 21, MySQL 8.4, `TENSOR_DB_URL`, `TENSOR_DB_USERNAME`, `TENSOR_DB_PASSWORD`; `TENSOR_TUSHARE_TOKEN` required only for downloads.

- [ ] Confirm task design; use non-secret examples such as `jdbc:mysql://127.0.0.1:3306/tensor` and environment-variable names, never credential-like literal values or source editing.
- [ ] Write smoke script with `set -eu`, configurable base URL, health check, `/downloads`, `/datasets`, data-sources JSON and no-secret assertions.
- [ ] Write schema/account creation, environment variables, JAR start, health readiness, browser URLs, shutdown, backup and forward-only rollback guidance.
- [ ] Run shell syntax check `sh -n scripts/smoke-test.sh` and execute against a packaged test instance; expect all probes pass.
- [ ] Follow the runbook from a clean temporary environment without reading source; record any missing step and fix it within this task.
- [ ] Commit as `docs: add reproducible Tensor first-run guide` when Git exists.

### Task M13-T05: 独立 acceptance JAR 打包及启停验收（Maven/XML、Java test）

本任务由项目所有者于 2026-09-05 批准增补，用于补齐 M14-T01 的验收包输入；M13-T01～T04 的生产交付与完成证据保持有效。

**Design:** `docs/task-designs/M13-T05-design.md`。

**Context boundary:** Read the app packaging POM/contract, M13 runbooks, fixture public descriptor/configuration, existing V6 and the application's component-discovery/adapter-extension seams. Do not change business implementation.

**Files:**
- Modify: `data-plane/tensor-app/pom.xml`
- Create: `data-plane/tensor-app/src/test/java/com/akkc/tensor/build/AcceptancePackagedJarContractTest.java`
- Create: `docs/runbook/acceptance.md`

**Interfaces:** `mvn -f data-plane/pom.xml -Pacceptance clean verify` additionally produces `data-plane/tensor-app/target/acceptance/tensor-app-1.0-SNAPSHOT-acceptance.jar`. Only the explicit runtime combination `--spring.profiles.active=acceptance --tensor.plugins.fixture.enabled=true` exposes fixture. The original top-level production JAR remains the only production artifact and excludes fixture/V6.

- [ ] Read the completed design and confirm its three-file implementation boundary.
- [ ] Write the three acceptance archive contract tests and only the Surefire/Failsafe test wiring; get RED caused by the missing acceptance JAR.
- [ ] Add the opt-in AntRun archive assembly after Boot repackage, retaining fixture test scope and copying only the exact V6 test resource.
- [ ] Verify default and acceptance builds, byte-level archive preservation, nested JAR storage and output isolation.
- [ ] From an isolated distribution directory and fresh MySQL schema, verify health/pages/metadata, both activation conditions, disabled-fixture restart and unchanged Tushare summary; write reproducible acceptance instructions.
- [ ] Record results and commit the exact three implementation files as `build: add isolated acceptance jar` when Git exists.

## Module Gate

Run `mvn -f data-plane/pom.xml clean verify` and the smoke script against the packaged JAR. M13 is complete only when one JAR serves both pages/API, contains all required resources, excludes fixture/secrets and a clean environment follows the runbook successfully.

M13-T05 的增补门禁另要求显式 acceptance 构建与验收包真实启停矩阵通过；它不能放宽上述生产门禁，也不回滚 M13-T01～T04 的既有完成状态。
