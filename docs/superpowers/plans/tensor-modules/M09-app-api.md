# M09 Application and REST API Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 装配模块化单体，提供稳定 `/api/v1` REST、错误映射、请求关联、指标、健康和静态资源安全配置。

**Architecture:** Controller 只做 DTO/输入边界，应用服务编排插件、适配和持久化。所有成功下载响应在事务提交后生成；异常由统一 handler 映射，日志和指标使用固定低基数标签。

**Tech Stack:** Spring Boot 3.5.x、Spring MVC、Actuator、Micrometer、Bean Validation、Spring Boot Test。

**Spec:** `docs/superpowers/specs/2026-08-25-tensor-module-roadmap-design.md`

## Global Constraints

- REST 必须符合 `docs/contracts/openapi-v1.yaml`。
- 每个响应设置 `X-Request-Id`；错误体同时包含 `requestId`。
- DECIMAL/BIGINT 业务字段序列化为十进制字符串。
- 生产只开放必要健康端点，不开放配置/环境明文。
- 查询 API 只读，不提供 POST/PUT/PATCH/DELETE 数据接口。

## Project Inputs

候选输入为 OpenAPI、app/core 边界文件和对应测试。应用 API 不依赖 Vue 或具体插件内部实现。

---

### Task M09-T01: Boot 入口、请求标识与通用 DTO（2.5h）

**Files:**
- Create: `data-plane/tensor-app/src/main/java/com/akkc/tensor/TensorApplication.java`
- Create: `data-plane/tensor-app/src/main/java/com/akkc/tensor/web/RequestIdFilter.java`
- Create: `data-plane/tensor-app/src/main/java/com/akkc/tensor/web/dto/ApiErrorResponse.java`
- Create: `data-plane/tensor-app/src/main/java/com/akkc/tensor/web/dto/FieldErrorResponse.java`
- Delete: `data-plane/src/main/java/com/akkc/Main.java`
- Test: `data-plane/tensor-app/src/test/java/com/akkc/tensor/web/RequestIdFilterTest.java`

**Interfaces:** Header `X-Request-Id`; valid client ID uses length/character whitelist, invalid ID replaced by UUID and stored in MDC for request lifetime.

- [ ] Confirm task design; freeze request-ID regex and MDC cleanup semantics.
- [ ] Test absent, valid, invalid/log-injection IDs and cleanup after response.
- [ ] Run targeted test; expect missing filter/app failure.
- [ ] Implement Boot entry, once-per-request filter and immutable error DTOs; remove old example entry.
- [ ] Run `mvn -pl tensor-app -am test` and confirm application context starts.
- [ ] Commit as `feat(app): bootstrap Tensor and request correlation` when Git exists.

### Task M09-T02: 数据源与元数据 API（2.0h）

**Files:**
- Create: `data-plane/tensor-app/src/main/java/com/akkc/tensor/web/DataSourceController.java`
- Create: `data-plane/tensor-app/src/main/java/com/akkc/tensor/web/dto/DataSourceResponse.java`
- Create: `data-plane/tensor-app/src/main/java/com/akkc/tensor/web/dto/ApiDescriptorResponse.java`
- Create: `data-plane/tensor-app/src/main/java/com/akkc/tensor/web/dto/DatasetDefinitionResponse.java`
- Test: `data-plane/tensor-app/src/test/java/com/akkc/tensor/web/DataSourceControllerTest.java`

**Interfaces:** GET data sources, plugin APIs, plugin datasets and dataset definition paths from M00-T03.

- [ ] Confirm task design; align every DTO field with OpenAPI and exclude credential details.
- [ ] MockMvc-test ready/unavailable plugin, 49 APIs, dataset list, unknown plugin/dataset and missing Token with query still available.
- [ ] Run targeted test; expect 404/missing controller failure.
- [ ] Implement controller mapping from registries/catalog without plugin-specific branches.
- [ ] Run tests and snapshot JSON field names.
- [ ] Commit as `feat(api): expose data-source metadata` when Git exists.

### Task M09-T03: 同步下载 API（3.0h）

**Files:**
- Create: `data-plane/tensor-core/src/main/java/com/akkc/tensor/core/download/DownloadService.java`
- Create: `data-plane/tensor-app/src/main/java/com/akkc/tensor/web/DownloadController.java`
- Create: `data-plane/tensor-app/src/main/java/com/akkc/tensor/web/dto/DownloadRequest.java`
- Create: `data-plane/tensor-app/src/main/java/com/akkc/tensor/web/dto/DownloadResponse.java`
- Test: `data-plane/tensor-app/src/test/java/com/akkc/tensor/web/DownloadControllerIT.java`

**Interfaces:** POST `/api/v1/downloads`; results contain sourceRowCount, insertedRows, updatedRows and outcome `SUCCESS|EMPTY`.

- [ ] Confirm task design; freeze linear orchestration and ensure success response is created after `persist` returns.
- [ ] Test invalid params no upstream call, empty no write, success counts, adapter failure zero write, database rollback and disabled plugin.
- [ ] Run integration test; expect missing service/controller failure.
- [ ] Implement validate → plugin download → empty short-circuit → adapter → persistence; never open a transaction around upstream calls.
- [ ] Run integration/module tests and verify `sourceRowCount == insertedRows + updatedRows` for unique fixture rows.
- [ ] Commit as `feat(api): execute synchronous dataset downloads` when Git exists.

### Task M09-T04: 数据集只读分页 API（3.0h）

**Files:**
- Create: `data-plane/tensor-app/src/main/java/com/akkc/tensor/web/DatasetController.java`
- Create: `data-plane/tensor-app/src/main/java/com/akkc/tensor/web/dto/PageResponse.java`
- Create: `data-plane/tensor-app/src/main/java/com/akkc/tensor/web/JacksonPrecisionConfiguration.java`
- Test: `data-plane/tensor-app/src/test/java/com/akkc/tensor/web/DatasetControllerIT.java`

**Interfaces:** GET dataset records with `tsCode`, `tradeDateFrom/To`, `annDateFrom/To`, `page`, `pageSize`.

- [ ] Confirm task design; align query-name camelCase and response snake_case item keys with OpenAPI.
- [ ] Test empty, unfiltered, combined filters, unsupported filter, 20/50/100, bad page size, out-of-range page and high-precision JSON strings.
- [ ] Run integration test; expect missing controller failure.
- [ ] Implement criteria mapping and ordered map response; configure precision serialization without global conversion of control integers.
- [ ] Run tests and assert no mutating dataset route exists.
- [ ] Commit as `feat(api): expose read-only dataset paging` when Git exists.

### Task M09-T05: 全局异常与 HTTP 状态（2.0h）

**Files:**
- Create: `data-plane/tensor-app/src/main/java/com/akkc/tensor/web/GlobalExceptionHandler.java`
- Test: `data-plane/tensor-app/src/test/java/com/akkc/tensor/web/GlobalExceptionHandlerTest.java`

**Interfaces:** HTTP mapping exactly follows TRD 12.6; response is M09 `ApiErrorResponse`.

- [ ] Confirm task design; map every M00 error code to one status/retryable value.
- [ ] Parameterized-test 400, 404, 409, 422, 500, 502, 503 and 504 plus Bean Validation and unknown exception.
- [ ] Run targeted test and confirm missing handler fails.
- [ ] Implement `@RestControllerAdvice`; log stack only for controlled server logs and return safe summaries/request ID.
- [ ] Scan response JSON for SQL, stack, internal path and test Token; expect none.
- [ ] Commit as `feat(api): map domain errors safely` when Git exists.

### Task M09-T06: 配置、脱敏、指标与健康（2.5h）

**Files:**
- Create: `data-plane/tensor-app/src/main/resources/application.yml`
- Create: `data-plane/tensor-app/src/main/java/com/akkc/tensor/observability/TensorMetrics.java`
- Create: `data-plane/tensor-app/src/main/java/com/akkc/tensor/observability/OperationLogger.java`
- Create: `data-plane/tensor-app/src/main/java/com/akkc/tensor/config/WebSecurityHeadersConfiguration.java`
- Test: `data-plane/tensor-app/src/test/java/com/akkc/tensor/observability/ObservabilityTest.java`

**Interfaces:** Metrics names/labels exactly TRD 17.3; health reports MySQL but missing Tushare Token only as plugin unavailable.

- [ ] Confirm task design; freeze config names from TRD appendix B and exposed actuator paths.
- [ ] Test structured completion events, low-cardinality labels, secret removal, MySQL health failure, missing Token app health success and security headers.
- [ ] Run tests and confirm missing configuration fails.
- [ ] Implement environment-backed configuration, final-event metrics/logging and health/security endpoint policy.
- [ ] Run app tests; search captured logs and actuator JSON for test Token/password; expect no match.
- [ ] Commit as `feat(app): add safe configuration and observability` when Git exists.

## Module Gate

Run `mvn -f data-plane/pom.xml -pl tensor-app -am verify`. M09 is complete only when OpenAPI contract tests, fixture integration, error mapping, secret scans, metrics and health behavior pass.
