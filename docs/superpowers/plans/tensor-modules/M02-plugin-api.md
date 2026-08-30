# M02 Plugin API Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 实现无 Spring 业务依赖的插件 SPI、描述符、数据集定义、下载包络和适配批次。

**Architecture:** 使用不可变 Java records 和受校验值对象表达跨模块契约。`plugin-api` 不执行网络、数据库或 Spring 注册，只定义稳定类型和约束。

**Tech Stack:** Java 21、Jakarta Validation annotations only where contract-safe、JUnit 5、AssertJ。

**Spec:** `docs/superpowers/specs/2026-08-25-tensor-module-roadmap-design.md`

## Global Constraints

- 包根为 `com.akkc.tensor.plugin.api`。
- 标识符正则为 `^[a-z][a-z0-9_]{1,63}$`；表名固定由 pluginId 与 apiName 推导。
- 动态参数值用 `Map<String,Object>`；Token 不属于任何公共 DTO。
- records 构造时复制集合并拒绝 null、重复名和不一致状态。

## Project Inputs

候选输入为 M00 契约、本模块接口文件和指定 TRD 契约。Plugin API 不依赖具体插件、数据库或 Vue 实现。

---

### Task M02-T01: 标识和值对象（1.5h）

**Design:** [M02-T01-designs.md](../../../task-designs/M02-T01-designs.md)

**Files:**
- Create: `data-plane/tensor-plugin-api/src/main/java/com/akkc/tensor/plugin/api/model/PluginId.java`, `data-plane/tensor-plugin-api/src/main/java/com/akkc/tensor/plugin/api/model/ApiName.java`, `data-plane/tensor-plugin-api/src/main/java/com/akkc/tensor/plugin/api/model/DatasetKey.java`, `data-plane/tensor-plugin-api/src/main/java/com/akkc/tensor/plugin/api/model/TableName.java`, `data-plane/tensor-plugin-api/src/main/java/com/akkc/tensor/plugin/api/model/RequestId.java`
- Test: `data-plane/tensor-plugin-api/src/test/java/com/akkc/tensor/plugin/api/model/IdentifierTest.java`

**Interfaces:** Produces `PluginId.of(String)`, `ApiName.of(String)`, `DatasetKey.of(PluginId,ApiName)`, `TableName.from(DatasetKey)`, `RequestId.newId()`.

- [ ] Confirm task design using only M00-T02 and TRD 5.1.
- [ ] Write tests accepting `tushare_pro/daily` and rejecting uppercase, hyphens, leading digits, one-character and over-64-character identifiers.
- [ ] Run `mvn -pl tensor-plugin-api -am -Dtest=IdentifierTest test`; expect compile failure because types do not exist.
- [ ] Implement final records with static factories, defensive validation and `TableName.from` returning `tushare_pro__daily`.
- [ ] Re-run the targeted test and then the module suite; expect all pass.
- [ ] Commit the five types and test as `feat(plugin-api): add validated identifiers` when Git exists.

### Task M02-T02: 参数、API 和插件描述符（2.0h）

**Files:**
- Create: `data-plane/tensor-plugin-api/src/main/java/com/akkc/tensor/plugin/api/descriptor/ParameterType.java`, `data-plane/tensor-plugin-api/src/main/java/com/akkc/tensor/plugin/api/descriptor/ParameterDescriptor.java`, `data-plane/tensor-plugin-api/src/main/java/com/akkc/tensor/plugin/api/descriptor/QueryMode.java`, `data-plane/tensor-plugin-api/src/main/java/com/akkc/tensor/plugin/api/descriptor/ApiDescriptor.java`, `data-plane/tensor-plugin-api/src/main/java/com/akkc/tensor/plugin/api/descriptor/PluginReadiness.java`, `data-plane/tensor-plugin-api/src/main/java/com/akkc/tensor/plugin/api/descriptor/PluginDescriptor.java`
- Test: `data-plane/tensor-plugin-api/src/test/java/com/akkc/tensor/plugin/api/descriptor/PluginDescriptorTest.java`

**Interfaces:** `PluginDescriptor(pluginId,displayName,description,enabled,credentialConfigured,downloadAvailable,unavailableReason,apis,datasets)`; parameter types exactly match M00-T02.

- [ ] Confirm task design; ensure readiness never stores credential values or paths.
- [ ] Write tests for immutable collections, duplicate API rejection, disabled/unconfigured readiness and required enum allowed values.
- [ ] Run the targeted test and confirm failure due to missing records.
- [ ] Implement enums/records; require unavailable reason when download is unavailable and forbid it when available.
- [ ] Run `mvn -pl tensor-plugin-api -am -Dtest=PluginDescriptorTest test` and module regression.
- [ ] Commit as `feat(plugin-api): define plugin descriptors` when Git exists.

### Task M02-T03: 数据集定义（2.5h）

**Files:**
- Create: `data-plane/tensor-plugin-api/src/main/java/com/akkc/tensor/plugin/api/dataset/LogicalType.java`, `data-plane/tensor-plugin-api/src/main/java/com/akkc/tensor/plugin/api/dataset/ColumnDefinition.java`, `data-plane/tensor-plugin-api/src/main/java/com/akkc/tensor/plugin/api/dataset/BusinessKeyMode.java`, `data-plane/tensor-plugin-api/src/main/java/com/akkc/tensor/plugin/api/dataset/BusinessKeyDefinition.java`, `data-plane/tensor-plugin-api/src/main/java/com/akkc/tensor/plugin/api/dataset/FilterDefinition.java`, `data-plane/tensor-plugin-api/src/main/java/com/akkc/tensor/plugin/api/dataset/DatasetDefinition.java`
- Test: `data-plane/tensor-plugin-api/src/test/java/com/akkc/tensor/plugin/api/dataset/DatasetDefinitionTest.java`

**Interfaces:** `DatasetDefinition(datasetKey,displayName,category,queryMode,parameters,tableName,columns,businessKey,filters,fixedColumn,batchSize)`.

- [ ] Confirm task design; map every Java field to M00-T02 JSON Schema.
- [ ] Write tests rejecting duplicate columns, missing key/filter references, invalid batch sizes, FINGERPRINT without identity fields and table-name mismatch.
- [ ] Run targeted tests and observe missing-type failure.
- [ ] Implement records with ordered immutable lists and constructor invariants; allow batch sizes 1–500 and default 500.
- [ ] Run module tests and confirm the complete `daily` definition succeeds.
- [ ] Commit as `feat(plugin-api): define dataset metadata model` when Git exists.

### Task M02-T04: 下载包络、适配批次和结果（2.0h）

**Files:**
- Create: `data-plane/tensor-plugin-api/src/main/java/com/akkc/tensor/plugin/api/download/DownloadStatus.java`, `data-plane/tensor-plugin-api/src/main/java/com/akkc/tensor/plugin/api/download/DownloadEnvelope.java`, `data-plane/tensor-plugin-api/src/main/java/com/akkc/tensor/plugin/api/download/AdaptedBatch.java`, `data-plane/tensor-plugin-api/src/main/java/com/akkc/tensor/plugin/api/download/DownloadOutcome.java`, `data-plane/tensor-plugin-api/src/main/java/com/akkc/tensor/plugin/api/download/DownloadResult.java`
- Test: `data-plane/tensor-plugin-api/src/test/java/com/akkc/tensor/plugin/api/download/DownloadEnvelopeTest.java`, `data-plane/tensor-plugin-api/src/test/java/com/akkc/tensor/plugin/api/download/AdaptedBatchTest.java`

**Interfaces:** `DownloadEnvelope(pluginId,apiName,params,fields,rowCount,data,status,error)` and `AdaptedBatch(datasetKey,tableName,columns,rows,businessKeyDefinition,ingestedAt)`.

- [ ] Confirm task design; freeze `SUCCESS` envelope rules and outcomes `SUCCESS|EMPTY`.
- [ ] Test `rowCount == data.size`, unique fields, row width, empty success, failure/error consistency and one batch timestamp.
- [ ] Run targeted tests and confirm missing types fail.
- [ ] Implement immutable records; copy nested row lists and reject a half-valid envelope.
- [ ] Run module regression; expect all envelope and batch tests pass.
- [ ] Commit as `feat(plugin-api): add download and adaptation contracts` when Git exists.

### Task M02-T05: SPI 和领域错误（2.0h）

**Files:**
- Create: `data-plane/tensor-plugin-api/src/main/java/com/akkc/tensor/plugin/api/DataSourcePlugin.java`, `data-plane/tensor-plugin-api/src/main/java/com/akkc/tensor/plugin/api/DatasetAdapter.java`
- Create: `data-plane/tensor-plugin-api/src/main/java/com/akkc/tensor/plugin/api/error/ErrorCode.java`, `data-plane/tensor-plugin-api/src/main/java/com/akkc/tensor/plugin/api/error/TensorException.java`, `data-plane/tensor-plugin-api/src/main/java/com/akkc/tensor/plugin/api/error/SourceException.java`, `data-plane/tensor-plugin-api/src/main/java/com/akkc/tensor/plugin/api/error/AdapterException.java`
- Test: `data-plane/tensor-plugin-api/src/test/java/com/akkc/tensor/plugin/api/PluginApiSurfaceTest.java`

**Interfaces:**
```java
public interface DataSourcePlugin {
    PluginDescriptor descriptor();
    PluginReadiness readiness();
    DownloadEnvelope download(ApiName apiName, Map<String, Object> params);
}
public interface DatasetAdapter {
    DatasetKey datasetKey();
    DatasetDefinition definition();
    AdaptedBatch adapt(DownloadEnvelope envelope, Instant ingestedAt);
}
```

- [ ] Confirm task design; compare error codes with `docs/contracts/error-codes.md`.
- [ ] Write a reflection test asserting the exact method names, parameter types and return types above and no Spring/JDBC types on the public surface.
- [ ] Run the test and confirm missing interfaces fail.
- [ ] Implement interfaces and immutable exceptions carrying `ErrorCode`, safe message and retryable flag without raw response or Token.
- [ ] Run `mvn -pl tensor-plugin-api -am test` and `jdeps`/dependency checks; expect no concrete plugin/core dependency.
- [ ] Commit as `feat(plugin-api): publish plugin and adapter SPI` when Git exists.

## Module Gate

Run `mvn -f data-plane/pom.xml -pl tensor-plugin-api -am verify`. M02 is complete only when public types are immutable, invariant tests pass and the exported SPI contains no Spring, JDBC, HTTP or concrete plugin types.
