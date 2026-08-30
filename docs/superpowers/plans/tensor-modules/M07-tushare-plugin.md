# M07 Tushare Plugin Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 实现 Tushare Pro 的同步 HTTP 客户端、严格响应校验、错误分类和 49 接口插件。

**Architecture:** `TushareProClient` 是唯一接触 Token 和上游协议的组件；`TushareProPlugin` 只用已验证的 M03 元数据构造描述符并把调用结果封装为 M02 包络。核心模块不依赖本模块。

**Tech Stack:** Java 21、Spring `RestClient`、Jackson、WireMock、JUnit 5。

**Spec:** `docs/superpowers/specs/2026-08-25-tensor-module-roadmap-design.md`

## Global Constraints

- POST 默认 `https://api.tushare.pro`；连接超时 5s、响应超时 120s、响应体上限 64MiB、自动重试 0。
- Token 只存在于出站请求构造，不进入公共 DTO、MDC、异常、日志或响应。
- 校验顺序固定为 HTTP/JSON、业务码、data 节点、字段唯一/集合、行宽、row count。

## Project Inputs

候选输入为插件文件、M02 SPI 和 M03 数据集定义。Tushare 插件不依赖 core 内部实现、数据库或 Vue。

---

### Task M07-T01: 配置与 RestClient（2.5h）

**Files:**
- Create: `data-plane/tensor-plugin-tushare/src/main/java/com/akkc/tensor/plugin/tushare/config/TushareProperties.java`
- Create: `data-plane/tensor-plugin-tushare/src/main/java/com/akkc/tensor/plugin/tushare/client/TushareRestClientFactory.java`
- Test: `data-plane/tensor-plugin-tushare/src/test/java/com/akkc/tensor/plugin/tushare/client/TushareRestClientFactoryTest.java`

**Interfaces:** Properties bind enabled/baseUrl/token/connectTimeout/readTimeout/maxResponseBytes; factory returns configured synchronous `RestClient`.

- [ ] Confirm task design; define a redacted credential value object whose `toString()` never exposes content.
- [ ] Test defaults, overrides, missing Token readiness and redacted string/log behavior.
- [ ] Run targeted test; expect missing classes fail.
- [ ] Implement validated configuration and client timeouts/User-Agent `Tensor/1.0` with no retry interceptor.
- [ ] Run tests and scan captured configuration output for the test Token; expect no match.
- [ ] Commit as `feat(tushare): configure secure upstream client` when Git exists.

### Task M07-T02: 上游 DTO、解析和返回校验（3.0h）

**Files:**
- Create: `data-plane/tensor-plugin-tushare/src/main/java/com/akkc/tensor/plugin/tushare/client/TushareRequest.java`, `data-plane/tensor-plugin-tushare/src/main/java/com/akkc/tensor/plugin/tushare/client/TushareResponse.java`, `data-plane/tensor-plugin-tushare/src/main/java/com/akkc/tensor/plugin/tushare/client/TushareData.java`, `data-plane/tensor-plugin-tushare/src/main/java/com/akkc/tensor/plugin/tushare/client/TushareResponseValidator.java`, `data-plane/tensor-plugin-tushare/src/main/java/com/akkc/tensor/plugin/tushare/client/TushareProClient.java`
- Test: `data-plane/tensor-plugin-tushare/src/test/java/com/akkc/tensor/plugin/tushare/client/TushareProClientTest.java`

**Interfaces:** `DownloadEnvelope execute(DatasetDefinition definition, Map<String,Object> params)`.

- [ ] Confirm task design; exact request fields are `api_name`, `token`, `params`, comma-separated `fields`.
- [ ] WireMock-test success, legal empty, malformed JSON, absent data, duplicate fields, wrong field set, wrong row width and oversized body.
- [ ] Run targeted test; expect missing client failure.
- [ ] Implement DTOs and ordered validation; create rowCount from `items.size()` and keep source field/data semantics.
- [ ] Run tests; confirm empty returns successful envelope with zero rows and no database concern.
- [ ] Commit as `feat(tushare): validate upstream response envelopes` when Git exists.

### Task M07-T03: 错误分类（2.5h）

**Files:**
- Create: `data-plane/tensor-plugin-tushare/src/main/java/com/akkc/tensor/plugin/tushare/client/TushareErrorClassifier.java`
- Test: `data-plane/tensor-plugin-tushare/src/test/java/com/akkc/tensor/plugin/tushare/client/TushareErrorClassifierTest.java`

**Interfaces:** Maps upstream/transport failures to M02 error codes and retryable flags.

- [ ] Confirm task design; freeze message/code recognition without returning raw upstream text.
- [ ] Test auth, permission/points, rate limit, 5xx unavailable, DNS/connect, read timeout and invalid payload.
- [ ] Run targeted test and confirm missing classifier fails.
- [ ] Implement ordered classification to `SOURCE_AUTH_FAILED`, `SOURCE_PERMISSION_DENIED`, `SOURCE_RATE_LIMITED`, `SOURCE_UNAVAILABLE`, `SOURCE_NETWORK_ERROR`, `SOURCE_TIMEOUT`, `SOURCE_PAYLOAD_INVALID`.
- [ ] Run tests and assert all exception messages exclude Token and full response body.
- [ ] Commit as `feat(tushare): classify upstream failures` when Git exists.

### Task M07-T04: TushareProPlugin（4.0h）

**Files:**
- Create: `data-plane/tensor-plugin-tushare/src/main/java/com/akkc/tensor/plugin/tushare/TushareProPlugin.java`
- Create: `data-plane/tensor-plugin-tushare/src/main/java/com/akkc/tensor/plugin/tushare/TusharePluginConfiguration.java`
- Test: `data-plane/tensor-plugin-tushare/src/test/java/com/akkc/tensor/plugin/tushare/TushareProPluginTest.java`

**Interfaces:** Implements M02 `DataSourcePlugin`; plugin ID `tushare_pro`; descriptors contain exactly 49 M03 APIs/datasets.

- [ ] Confirm task design; confirm readiness differences for disabled, missing Token and ready states.
- [ ] Test exact 49 descriptor names, no constructor network call, unavailable download rejection and one `daily` delegation.
- [ ] Run targeted test; expect missing plugin failure.
- [ ] Implement metadata-backed descriptor/readiness and `download` lookup/delegation without API-name branches.
- [ ] Run module tests and M03 49-contract tests.
- [ ] Commit as `feat(tushare): register 49 API plugin` when Git exists.

## Module Gate

Run `mvn -f data-plane/pom.xml -pl tensor-plugin-tushare -am verify`. M07 is complete only when WireMock covers all upstream outcomes, exactly 49 APIs are exposed and secret scans find no Token outside the one outbound request body.
