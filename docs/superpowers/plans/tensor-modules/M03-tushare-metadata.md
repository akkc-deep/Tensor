# M03 Tushare Dataset Metadata Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 以 49 份 YAML 建立 Tushare Pro 数据集的单一运行时元数据来源，并与 JSON 模板、PRD 参数和 TRD 业务键保持严格一致。

**Architecture:** Java 加载器负责 schema 与语义验证；每个 YAML 任务只编辑本业务分类的资源文件。字段顺序来自 JSON 模板，参数来自 PRD 附录 A，业务键来自 TRD 9.4；无法唯一确定的列类型必须先在任务设计中形成唯一决策，不得在实现中猜测。

**Tech Stack:** YAML、Jackson YAML、JSON Schema contract、JUnit 5。

**Spec:** `docs/superpowers/specs/2026-08-25-tensor-module-roadmap-design.md`

## Global Constraints

- 运行时文件位于 `data-plane/tensor-plugin-tushare/src/main/resources/datasets/tushare_pro/<api_name>.yaml`。
- 运行时不得读取 `docs/data-template/`；模板只在构建测试中使用。
- `apiName` 和 49 文件集合必须与 `manifest.json` 完全一致。
- `columns` 名称和顺序必须与各 JSON `fields` 完全一致。
- 参数集合必须与 PRD 附录 A 一致；业务键必须与 TRD 9.4 一致。
- 日期严格使用 `DATE`；数值使用 `DECIMAL(38,18)` 或明确的 `LONG`；空值不得转为 0 或空字符串。
- YAML 任务不得修改 Java 生产实现；Java 验证任务不得修改 YAML 业务定义。
- 不得把模板中的完整 `data` 数组载入任务上下文；只通过 `jq '{api_name,query_mode,params,fields,sample:(.data[0] // null)}'` 提取字段和一个样例行。

## Project Inputs

候选输入为对应 API 模板投影、M00 schema、PRD 附录和 TRD 9.4。每个字段的类型、长度、可空性和业务键必须先在任务设计中唯一确定。

---

### Task M03-T01: 元数据加载与 schema 验证框架（2.0h，Java）

**Context boundary:** Read M00-T02 schema, M02 `DatasetDefinition` and one `daily.json`; do not read all 49 templates.

**Files:**
- Create: `data-plane/tensor-plugin-tushare/src/main/java/com/akkc/tensor/plugin/tushare/metadata/DatasetDefinitionLoader.java`
- Create: `data-plane/tensor-plugin-tushare/src/test/java/com/akkc/tensor/plugin/tushare/metadata/DatasetDefinitionLoaderTest.java`
- Create: `data-plane/tensor-plugin-tushare/src/test/resources/datasets/valid-daily.yaml`
- Create: `data-plane/tensor-plugin-tushare/src/test/resources/datasets/invalid-duplicate-column.yaml`

**Interfaces:** `List<DatasetDefinition> loadAll(ResourcePatternResolver resolver, String pattern)`; invalid resources return deterministic `DATASET_MISCONFIGURED` diagnostics with resource name.

- [ ] Confirm task design; freeze YAML-to-record mapping and deterministic resource sort by `apiName`.
- [ ] Write tests for valid daily, duplicate column, table mismatch, missing business key field and invalid filter field.
- [ ] Run `mvn -pl tensor-plugin-tushare -am -Dtest=DatasetDefinitionLoaderTest test`; expect failure because loader is absent.
- [ ] Implement Jackson YAML binding, M02 record construction and aggregated validation errors without network access.
- [ ] Re-run targeted and module tests; expect valid resource loads once and all invalid resources fail with exact file names.
- [ ] Commit as `feat(tushare): load validated dataset metadata` when Git exists.

### Task M03-T02: 基础与组织 11 数据集（3.0h，YAML）

**Context boundary:** Read only the 11 named JSON templates, PRD appendix A.1, TRD 9.4 rows for the same APIs, M00 schema and loader tests.

**Files:** Create YAML for `stock_basic`, `stock_company`, `hs_const`, `trade_cal`, `new_share`, `namechange`, `stk_managers`, `broker_recommend`, `index_classify`, `index_member`, `index_member_all` under the runtime metadata directory.

**Interfaces:** Each file supplies one `DatasetDefinition`; `stk_managers` uses `FINGERPRINT`, the remaining ten use `COMPOSITE` keys exactly from TRD 9.4.

- [ ] Confirm task design; if any field type/length/nullability lacks one decision, record the full 11-file type map in the M03-T02 task design and stop implementation until it is resolved.
- [ ] For each file, declare plugin/API/table/category/display/query mode and the exact PRD parameter descriptors, including enum values for `list_status`, `exchange` and `hs_type`.
- [ ] Copy every JSON `fields` name in order into `columns`; declare types and nullability from the approved type map; append no source columns because persistence adds them.
- [ ] Declare TRD business key fields, filters only when `ts_code`, `trade_date` or `ann_date` actually exists, and a deterministic fixed column.
- [ ] Run the loader contract test filtered to these 11 APIs; expect 11 files, exact field order and no unresolved key/filter reference.
- [ ] Commit the 11 YAML files as `feat(metadata): define basic and organization datasets` when Git exists.

### Task M03-T03: 行情与估值 7 数据集（2.5h，YAML）

**Files:** Create YAML for `daily`, `weekly`, `monthly`, `adj_factor`, `suspend_d`, `daily_basic`, `stk_limit`.

**Interfaces:** All use required `trade_date`; business keys are `(ts_code,trade_date)` except `stk_limit` order `(trade_date,ts_code)`.

- [ ] Confirm task design using the seven templates, PRD A.2 and TRD 9.4; if the per-column type map is incomplete, record the missing decisions in the M03-T03 task design and stop implementation until they are resolved.
- [ ] Declare the seven descriptors with category `行情与估值`, query mode `trade_date`, required `DATE` parameter and filters `[ts_code,trade_date]` when present.
- [ ] Copy template fields in exact order and use `DECIMAL(38,18)` for market numeric values without converting through double.
- [ ] Run metadata tests for these APIs and assert `daily` has 11 fields and table `tushare_pro__daily`.
- [ ] Commit as `feat(metadata): define market datasets` when Git exists.

### Task M03-T04: 交易与资金 6 数据集（2.5h，YAML）

**Files:** Create YAML for `moneyflow`, `margin`, `margin_detail`, `top_list`, `top_inst`, `block_trade`.

**Interfaces:** `margin` requires enum `exchange_id` plus `trade_date`; other APIs require `trade_date`; keys match TRD 9.4 including multi-field `top_inst` and `block_trade`.

- [ ] Confirm task design with the six templates, PRD A.3 and TRD 9.4; if identity-field nullability is not fixed, record the missing decision in the M03-T04 task design and stop implementation until it is resolved.
- [ ] Declare exact parameters, columns, business keys and `[ts_code,trade_date]`/date-only filters according to actual fields.
- [ ] Verify buyer/seller/reason/exalter remain strings and price/volume/net-buy fields preserve decimal precision.
- [ ] Run the six-file metadata contract and assert the `margin` enum is `SSE|SZSE|BSE`.
- [ ] Commit as `feat(metadata): define trading and funding datasets` when Git exists.

### Task M03-T05: 互联互通与转融通 6 数据集（2.5h，YAML）

**Files:** Create YAML for `moneyflow_hsgt`, `hsgt_top10`, `hk_hold`, `slb_len`, `slb_sec`, `slb_sec_detail`.

**Interfaces:** All require `trade_date`; keys match TRD 9.4 and empty sample files remain fully typed.

- [ ] Confirm task design; because three SLB templates are empty, require a complete external-field type map in the M03-T05 task design before implementation.
- [ ] Declare the six descriptors, exact template columns and TRD keys; do not infer absence of support from empty samples.
- [ ] Add filters only for actual core fields and preserve `code` versus `ts_code` naming for `hk_hold`.
- [ ] Run the six-file contract plus an empty-fixture parse test; expect all definitions load without sample data.
- [ ] Commit as `feat(metadata): define connect and SLB datasets` when Git exists.

### Task M03-T06: 财务与披露 9 数据集（4.0h，YAML）

**Context boundary:** Candidate inputs are the nine financial templates, PRD A.6, TRD 9.4 and the approved M03-T06 type map in the task design. Do not read other module code.

**Files:** Create YAML for `income`, `balancesheet`, `cashflow`, `fina_indicator`, `fina_audit`, `fina_mainbz`, `express`, `forecast`, `disclosure_date`.

**Interfaces:** All use announcement-date mode; first six accept `ts_code,ann_date`, final three accept `ann_date`; keys match TRD 9.4.

- [ ] Confirm task design; if a complete type/nullability map for all 490 declared fields is absent, record the missing map in the M03-T06 task design and stop implementation until it is resolved.
- [ ] Create the nine descriptors with exact parameter requirements, full template column order and no silent field omission.
- [ ] Use strict dates for date fields, strings/enums for identity/status/report fields, `TEXT` for long narrative fields and `DECIMAL(38,18)` for financial numeric fields unless the approved map specifies `LONG`.
- [ ] Declare composite keys exactly as TRD 9.4 and filters from existing `ts_code`/`ann_date` fields.
- [ ] Run the nine-file contract; assert field counts 85, 152, 97, 108, 7, 8, 15, 13 and 5 respectively.
- [ ] Commit as `feat(metadata): define financial disclosure datasets` when Git exists.

### Task M03-T07: 公司行动 3 数据集（2.0h，YAML）

**Files:** Create YAML for `dividend`, `repurchase`, `share_float`.

- [ ] Confirm task design using the three templates, PRD A.7 and TRD 9.4; if field semantics remain ambiguous, record the missing decisions in the M03-T07 task design and stop implementation until they are resolved.
- [ ] Declare required `ann_date`, exact template columns, keys `(ts_code,end_date,ann_date)`, `(ts_code,ann_date,proc)`, `(ts_code,float_date,holder_name,share_type)` and available filters.
- [ ] Run the three-file contract and exact field-count checks 14, 9 and 7.
- [ ] Commit as `feat(metadata): define corporate action datasets` when Git exists.

### Task M03-T08: 股东与治理 7 数据集（2.5h，YAML）

**Files:** Create YAML for `stk_rewards`, `stk_holdernumber`, `stk_holdertrade`, `top10_holders`, `top10_floatholders`, `pledge_stat`, `pledge_detail`.

**Interfaces:** `pledge_detail` uses FINGERPRINT over all 14 template fields; remaining keys match TRD 9.4.

- [ ] Confirm task design; require unique decisions for empty top-10 samples and fingerprint canonicalization inputs in the M03-T08 task design.
- [ ] Declare snapshot/announcement parameters exactly as PRD A.8, all template columns and only valid filters.
- [ ] Declare `pledge_detail` identity fields in template order and `businessKey.mode: FINGERPRINT`.
- [ ] Run the seven-file contract and exact field-count checks 7, 4, 11, 9, 9, 7 and 14.
- [ ] Commit as `feat(metadata): define shareholder governance datasets` when Git exists.

### Task M03-T09: 49 数据集总契约（1.0h，Java）

**Files:**
- Create: `data-plane/tensor-plugin-tushare/src/test/java/com/akkc/tensor/plugin/tushare/metadata/TushareMetadataContractTest.java`

**Interfaces:** Produces build gate comparing manifest names, JSON field order, PRD parameter map encoded in the test and TRD business-key map encoded in the test.

- [ ] Confirm task design; ensure the test has explicit expected maps rather than copying values from YAML under test.
- [ ] Write parameterized tests over 49 API names and assert there are neither missing nor extra YAML resources.
- [ ] For each API, read its JSON template in test scope and compare `fields` exactly with YAML column names/order.
- [ ] Add explicit expected parameter and business-key maps for all 49 APIs; test filters reference existing columns and table names follow the fixed formula.
- [ ] Run `mvn -pl tensor-plugin-tushare -am -Dtest=TushareMetadataContractTest test`; expect 49/49 pass.
- [ ] Commit as `test(metadata): enforce all Tushare dataset contracts` when Git exists.

## Module Gate

Run `mvn -f data-plane/pom.xml -pl tensor-plugin-tushare -am test`. M03 is complete only when exactly 49 YAML files load, all field orders/parameters/keys match independent baselines, and no field type remains an implementation-time guess.
