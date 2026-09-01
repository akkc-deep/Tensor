# M04 Flyway Schema Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 通过固定 Flyway SQL 创建 49 张 Tushare 来源表、fixture 表、业务唯一键和查询索引，并验证实际 MySQL 8.4.6 schema 与 YAML 一致。

**Architecture:** DDL 完全由已冻结的 M03 YAML 驱动评审，但以可审阅的版本化 SQL 发布；运行时不自动建表或改表。每张表含全部业务列和三个来源字段，缺少自然键的表增加不对 API 暴露的 `business_key CHAR(64)`。

**Tech Stack:** MySQL 8.4.6 LTS、Flyway、SQL、Testcontainers MySQL 8.4.6。

**Spec:** `docs/superpowers/specs/2026-08-25-tensor-module-roadmap-design.md`

## Global Constraints

- InnoDB、`utf8mb4`、`utf8mb4_0900_as_cs`、UTC session timezone。
- M04-T02～T06 的实际数据库验证固定使用官方 `mysql:8.4.6`，不使用浮动 `mysql:8.4` 标签或其他数据库版本。
- 所有表追加 `source_plugin VARCHAR(64) NOT NULL`、`source_api VARCHAR(64) NOT NULL`、`ingested_at DATETIME(3) NOT NULL`。
- COMPOSITE 键直接建立主键；FINGERPRINT 表使用 `business_key CHAR(64)` 主键。
- 索引只覆盖实际 `ts_code`、`trade_date`、`ann_date` 筛选；已由主键最左前缀覆盖时不重复创建。
- SQL 任务只编辑迁移文件；总校验任务只编辑 Java 测试。

## Project Inputs

候选输入为对应 M03 YAML、迁移文件和 schema 测试。Flyway 任务不依赖 Java 或 Vue 实现。

---

### Task M04-T01: V1 基础与组织表（3.5h，SQL）

**Files:** Create `data-plane/tensor-app/src/main/resources/db/migration/V1__create_basic_and_organization_tables.sql`.

**Interfaces:** Creates the 11 tables corresponding to M03-T02; `stk_managers` has `business_key CHAR(64)` primary key.

- [ ] Confirm task design; compare every column type/nullability and key with the 11 frozen YAML files.
- [ ] Write CREATE TABLE statements for all 11 exact `tushare_pro__*` names, explicit business columns in YAML order, source columns and keys.
- [ ] Add only the query indexes declared by metadata and quote MySQL reserved words with backticks.
- [ ] Apply V1 to a clean MySQL 8.4 schema through Flyway; expect migration success and 11 base tables.
- [ ] Query `information_schema.columns` and `statistics`; confirm each table matches YAML plus source columns.
- [ ] Commit as `feat(db): create basic and organization tables` when Git exists.

### Task M04-T02: V2 行情、交易与资金表（3.5h，SQL）

**Files:** Create `data-plane/tensor-app/src/main/resources/db/migration/V2__create_market_and_trading_tables.sql`.

**Interfaces:** Creates 13 tables for M03-T03 and M03-T04, including `tushare_pro__daily` primary key `(ts_code,trade_date)`.

- [ ] Confirm task design; freeze all 13 DDL signatures from YAML before implementation.
- [ ] Write the 13 CREATE TABLE statements with DECIMAL precision preserved, exact composite keys and source columns.
- [ ] Add trade-date and ts-code/date indexes only when not covered by a primary-key prefix.
- [ ] Apply V1–V2 to clean MySQL 8.4.6 and verify total source-table count 24.
- [ ] Verify `tushare_pro__daily` has 14 visible columns and exact primary key order.
- [ ] Commit as `feat(db): create market and trading tables` when Git exists.

### Task M04-T03: V3 互联互通与转融通表（3.5h，SQL）

**Files:** Create `data-plane/tensor-app/src/main/resources/db/migration/V3__create_connect_and_slb_tables.sql`.

**Interfaces:** Creates six tables from M03-T05.

- [ ] Confirm task design; ensure empty template samples have complete frozen SQL types.
- [ ] Write six CREATE TABLE statements with exact key order, source columns and filters indexes.
- [ ] Apply V1–V3 to clean MySQL 8.4.6; expect 30 source tables.
- [ ] Verify `hk_hold` retains `code` rather than introducing `ts_code`.
- [ ] Commit as `feat(db): create connect and SLB tables` when Git exists.

### Task M04-T04: V4 财务与披露宽表（3.5h，SQL）

**Files:** Create `data-plane/tensor-app/src/main/resources/db/migration/V4__create_financial_tables.sql`.

**Interfaces:** Creates nine tables from M03-T06; `balancesheet` contains all 152 business fields plus three source fields.

- [ ] Confirm task design; require a deterministic YAML-to-DDL comparison artifact for all nine tables before editing SQL.
- [ ] Write nine CREATE TABLE statements, quoting reserved words and preserving DECIMAL/date/text types without narrowing.
- [ ] Add composite primary keys and only ts-code/announcement-date query indexes.
- [ ] Apply V1–V4 to clean MySQL 8.4.6; expect 39 source tables and no index-length error.
- [ ] Query `information_schema.columns`; assert business-field counts 85, 152, 97, 108, 7, 8, 15, 13 and 5.
- [ ] Commit as `feat(db): create financial disclosure tables` when Git exists.

### Task M04-T05: V5 公司行动、股东与治理表（3.5h，SQL）

**Files:** Create `data-plane/tensor-app/src/main/resources/db/migration/V5__create_corporate_and_governance_tables.sql`.

**Interfaces:** Creates ten tables from M03-T07 and M03-T08; `pledge_detail` has fingerprint primary key.

- [ ] Confirm task design; compare all ten YAML definitions and key modes.
- [ ] Write the ten CREATE TABLE statements with exact business columns, source columns, composite/fingerprint keys and query indexes.
- [ ] Apply V1–V5 to clean MySQL 8.4.6; expect exactly 49 `tushare_pro__*` tables.
- [ ] Verify `pledge_detail.business_key` is internal and all 14 template fields remain present.
- [ ] Commit as `feat(db): create corporate governance tables` when Git exists.

### Task M04-T06: Fixture 迁移与 49 表结构总校验（3.5h，Java/SQL test harness）

**Files:**
- Create: `data-plane/tensor-app/src/test/resources/db/migration/V6__create_fixture_tables.sql`
- Create: `data-plane/tensor-app/src/test/java/com/akkc/tensor/db/FlywaySchemaContractIT.java`

**Interfaces:** Fixture table fields are `ts_code`, `trade_date`, `amount`, `note` plus three source fields; primary key `(ts_code,trade_date)`.

- [ ] Confirm task design; keep V6 under test resources so production contains only 49 Tushare tables.
- [ ] Write the fixture DDL with exact fields and source columns.
- [ ] Write a Testcontainers MySQL 8.4.6 test that runs Flyway, loads 49 M03 YAML definitions and compares names, order, JDBC types, nullability, primary/unique keys and query indexes against `information_schema`.
- [ ] Add assertions for collation `utf8mb4_0900_as_cs`, InnoDB and exactly three source fields per table.
- [ ] Run `mvn -pl tensor-app -am -Dtest=FlywaySchemaContractIT test`; expect 49/49 plus fixture pass.
- [ ] Run Flyway validate a second time; expect no checksum or pending-migration error.
- [ ] Commit as `test(db): verify Flyway schema contracts` when Git exists.

## Module Gate

Run `mvn -f data-plane/pom.xml -pl tensor-app -am -Dtest=FlywaySchemaContractIT test`. M04 is complete only when a clean MySQL 8.4.6 instance reaches 49 production tables, every YAML/schema comparison passes and production resources exclude fixture DDL.
