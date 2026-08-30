# M05 Core Registry and Adapter Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 实现插件/适配器注册、数据集目录、参数校验和严格的元数据驱动适配。

**Architecture:** `tensor-core` 只依赖 `tensor-plugin-api`。注册表和目录在启动时构建不可变视图；通用适配器将来源二维数据转换为带业务键定义和统一批次时间的行，不执行数据库操作。

**Tech Stack:** Java 21、Spring Core、Jackson、JUnit 5、AssertJ。

**Spec:** `docs/superpowers/specs/2026-08-25-tensor-module-roadmap-design.md`

## Global Constraints

- 单个插件或数据集配置错误必须隔离，不阻止其他有效插件注册。
- Token 缺失只关闭下载，不关闭已入库数据查询。
- 参数在后端按与前端相同的元数据再次校验。
- 数值转换使用 `BigDecimal` 和 `RoundingMode.UNNECESSARY`；日期严格解析 `yyyyMMdd`。
- 适配任一行失败时整批失败，数据库零写入。

## Project Inputs

候选输入为本模块 Java 边界以及直接消费的 M02、M03、M04 稳定契约。Core 注册与适配不依赖具体插件或前端实现。

---

### Task M05-T01: 插件与适配器注册表（3.0h）

**Files:**
- Create: `data-plane/tensor-core/src/main/java/com/akkc/tensor/core/registry/PluginRegistry.java`
- Create: `data-plane/tensor-core/src/main/java/com/akkc/tensor/core/registry/AdapterRegistry.java`
- Test: `data-plane/tensor-core/src/test/java/com/akkc/tensor/core/registry/RegistryTest.java`

**Interfaces:** `Optional<DataSourcePlugin> find(PluginId)`, `List<PluginDescriptor> descriptors()`, `Optional<DatasetAdapter> find(DatasetKey)`.

- [ ] Confirm task design; freeze duplicate-ID isolation semantics and deterministic descriptor sort.
- [ ] Write tests for normal lookup, duplicate plugin ID, duplicate dataset key, disabled plugin, missing credential and one broken plugin beside one valid plugin.
- [ ] Run `mvn -pl tensor-core -am -Dtest=RegistryTest test`; expect missing-class failure.
- [ ] Implement constructor-built immutable maps; catch descriptor failures per plugin and expose safe unavailable reasons.
- [ ] Run targeted and module tests; confirm one bad plugin does not hide the valid plugin.
- [ ] Commit as `feat(core): add plugin and adapter registries` when Git exists.

### Task M05-T02: 数据集目录与启动校验（3.0h）

**Files:**
- Create: `data-plane/tensor-core/src/main/java/com/akkc/tensor/core/catalog/DatasetCatalog.java`
- Create: `data-plane/tensor-core/src/main/java/com/akkc/tensor/core/catalog/SchemaInspector.java`
- Create: `data-plane/tensor-core/src/main/java/com/akkc/tensor/core/catalog/DatasetStartupValidator.java`
- Test: `data-plane/tensor-core/src/test/java/com/akkc/tensor/core/catalog/DatasetStartupValidatorTest.java`

**Interfaces:** `DatasetCatalog.find(DatasetKey)`, `DatasetCatalog.list(PluginId)` expose only validated definitions; `SchemaInspector.inspect(TableName)` returns ordered columns, JDBC types, nullability and keys.

- [ ] Confirm task design; use M03/M04 contracts without reading Tushare implementation.
- [ ] Test valid schema, missing table, type mismatch, nullability mismatch, missing unique key and invalid index reference.
- [ ] Run targeted test and confirm failure due to absent validator.
- [ ] Implement validator comparing immutable metadata with inspected MySQL schema; exclude failing datasets from catalog and retain safe diagnostics.
- [ ] Run tests and assert valid datasets remain queryable when a sibling dataset is invalid.
- [ ] Commit as `feat(core): validate dataset catalog at startup` when Git exists.

### Task M05-T03: 元数据驱动参数校验（3.0h）

**Files:**
- Create: `data-plane/tensor-core/src/main/java/com/akkc/tensor/core/validation/ParameterValidator.java`
- Create: `data-plane/tensor-core/src/main/java/com/akkc/tensor/core/validation/ValidatedParameters.java`
- Test: `data-plane/tensor-core/src/test/java/com/akkc/tensor/core/validation/ParameterValidatorTest.java`

**Interfaces:** `ValidatedParameters validate(ApiDescriptor api, Map<String,Object> raw)` returns immutable normalized snake_case values or throws `PARAM_REQUIRED|PARAM_INVALID` with field errors.

- [ ] Confirm task design; freeze trim, uppercase TS_CODE, enum, date/month and range-order rules.
- [ ] Write tests for missing required, unknown parameter, `000001.SZ`, bad code, valid/invalid `yyyyMMdd`, valid/invalid `yyyyMM`, enum membership and reversed range.
- [ ] Run targeted test and confirm missing validator fails.
- [ ] Implement validation without API-name branches; use only `ParameterDescriptor` rules.
- [ ] Run tests and confirm returned maps are immutable and contain no Token key.
- [ ] Commit as `feat(core): validate plugin parameters from metadata` when Git exists.

### Task M05-T04: 严格字段类型转换（3.5h）

**Files:**
- Create: `data-plane/tensor-core/src/main/java/com/akkc/tensor/core/adapter/ValueConverter.java`
- Create: `data-plane/tensor-core/src/main/java/com/akkc/tensor/core/adapter/ConversionContext.java`
- Test: `data-plane/tensor-core/src/test/java/com/akkc/tensor/core/adapter/ValueConverterTest.java`

**Interfaces:** `Object convert(Object source, ColumnDefinition column, ConversionContext context)` returns `String`, `LocalDate`, `Long`, `BigDecimal` or null.

- [ ] Confirm task design; freeze blank-string/null semantics and error fields.
- [ ] Test strict dates, month, trimmed strings, open/closed enums, exact long, long overflow, BigDecimal precision/scale, required rounding failure and null preservation.
- [ ] Run targeted test and confirm missing converter fails.
- [ ] Implement conversions without `double`; throw `ADAPTER_TYPE_INVALID` with API, row index and field name but not raw sensitive values.
- [ ] Run module regression; confirm precision and null tests pass.
- [ ] Commit as `feat(core): add strict dataset value conversion` when Git exists.

### Task M05-T05: 通用适配器与指纹键（3.5h）

**Files:**
- Create: `data-plane/tensor-core/src/main/java/com/akkc/tensor/core/adapter/GenericDatasetAdapter.java`
- Create: `data-plane/tensor-core/src/main/java/com/akkc/tensor/core/adapter/FingerprintKeyCodec.java`
- Test: `data-plane/tensor-core/src/test/java/com/akkc/tensor/core/adapter/GenericDatasetAdapterTest.java`

**Interfaces:** Implements M02 `DatasetAdapter`; `FingerprintKeyCodec.sha256(List<String> fields, Map<String,Object> row)` uses length-prefixed UTF-8, fixed order and explicit null marker.

- [ ] Confirm task design; freeze duplicate-source-row semantics and fingerprint byte format.
- [ ] Test envelope identity/field mismatch, row conversion, missing required key, duplicate conflicting key, duplicate identical row, stable fingerprint and one ingested timestamp.
- [ ] Run targeted test and confirm missing adapter fails.
- [ ] Implement field-index mapping once per batch, adapt every row, deduplicate identical keys and reject conflicting duplicates.
- [ ] Run module tests and an empty-envelope test; empty data returns no write batch path.
- [ ] Commit as `feat(core): adapt datasets from fixed metadata` when Git exists.

## Module Gate

Run `mvn -f data-plane/pom.xml -pl tensor-core -am test`. M05 is complete only when registries isolate failures, catalog exposes only schema-valid datasets, parameter validation is metadata-only and all adapter failure cases produce zero persistence calls.
