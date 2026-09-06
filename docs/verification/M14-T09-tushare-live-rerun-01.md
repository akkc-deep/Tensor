# M14-T09 D-02完整复验（第1次复跑）

本轮归属M14-T09，权威设计为[当前设计](../task-designs/M14-T09-design.md)的D-02。用户明确同意仅将top10_holders当前验收预期调整为成功且有数据，保留原参数、历史结果和完整40项复验；D-01的dividend覆盖继续有效，其余38项期望不变。历史分类28ok/12empty，当前30ok/10empty。38和320仅是此前测量值，不是本轮固定行数。

## 冻结输入与历史证据

- 原manifest及全部模板/参数不变，SHA `37a317f6a2bc3e5113be5f127976d16d8349414c6476c7f6a194b084a5b0f7c2`。
- 沿用冻结验收JAR：`/private/tmp/tensor-m14-t09-green.MZ4kMkN9/data-plane/tensor-app/target/acceptance/tensor-app-1.0-SNAPSHOT-acceptance.jar`，SHA `81adba0dd6500f4aa43b4fa06b18c2c8e7b7454d9e6d4d6c734772cdaef1d002`。本轮不改生产代码、不重建二进制。
- [上一轮M14-T09实际证据](M14-T09-tushare-live.md)，提交471dfb0，整篇SHA `9d5c283b31f586ee7a3b4fdbda84321fbf3c7e4f55274f9d69955d86353af170`；32通过/1失败/7未运行，top10_holders的末次页面核对未执行，原失败保留。
- [M14-T05历史证据](M14-T05-tushare-live.md)，整篇SHA `d4e7bf67b6a2b144662a987dec9aa812a8e39543c5134ed7cab8a12a4dbe34b5`。既有修复回归/迁移/打包/health/49总合同结果见上一轮证据，本轮只修改验收预期，不重复后端构建测试。

## 执行合同与本地准备

完整范围仍为40接口/48组原样例/80次真实页面records查询，fixture另计2POST/3查询。9项排除仍为top_inst、broker_recommend、share_float、hs_const、moneyflow_hsgt、hk_hold、index_member、hsgt_top10、namechange；不计skip或通过，原49目标仍未完成。使用单worker、零重试、上次真实响应结束后至少2秒再点击下一次下载；所有业务下载/查询均来自页面。全轮失败停止，不将此前32项与本轮拼接。

新私有控制目录 `/private/tmp/tensor-m14-t09-control.yx5keenc`，准备脚本实际通过MySQL8.4.6、初始0表、回环绑定、真实来源host、utf8mb4/utf8mb4_0900_as_cs及CREATE/SELECT/INSERT/UPDATE/ALTER/INDEX六项权限检查。新启动器沿用已审查逻辑，仅将证据写入目标更换为本文件；扫描器仍从固定SHA的M14-T05历史证据提取，不修改扫描算法。

D-02同函数探针先RED（旧spec仅dividend覆盖，与预期双覆盖不符，exit1），仅修改两处spec后GREEN（exit0）。验证准确双覆盖、其余期望不变、非空/EMPTY与末次数据集断言、历史28/12与当前30/10及40/48/9，原manifest/参数不变。`node --check control-plane/e2e/tushare-live.spec.js` exit0；`npx playwright test e2e/tushare-live.spec.js --list` exit0，恰40个Chromium用例。

本地启动材料验证：Python语法通过；5项前置拒绝、3项精确范围拒绝及10项生命周期/终检合成探针全部通过。这些探针仅使用合成凭证，无真实业务调用；本地子进程使用净化环境。

独立D-02接入审查：spec符合性PASS；唯一Minor为交接仍把旧specSHA标作当前，已更正为 `20499ebe50010f07edcbfc6502ed13fe84aac06f557700f92f8cde7fd9757f94`。当前spec/config和6项运行hash、0700/0600权限及控制目录未使用均已核对。

运行前状态：真实复验尚未执行。正式命令由本轮私有启动器在M14-T09明确恢复IN_PROGRESS且设计/spec/JAR/manifest/证据/启动器hash均匹配后执行；真实Token仅从现有TENSOR_TUSHARE_TOKEN进入受控后端。实际结果由同一启动器在全部worker退出、终检扫描、独立DB核对与自有容器/卷/私密材料清理后追加，并对本文件执行真实秘密集合扫描。

## 2000档实际运行 2026-09-06T11:57:33.823213+00:00

以下为本次启动器在CLI退出、终检和清理后记录的实际结果；空值表示未测量，原全49目标仍不完整。

```json
{
  "controllerFailure": null,
  "databaseAfterMigration": {
    "businessTables": 50,
    "counts": {
      "adj_factor": 0,
      "balancesheet": 0,
      "block_trade": 0,
      "broker_recommend": 0,
      "cashflow": 0,
      "daily": 0,
      "daily_basic": 0,
      "disclosure_date": 0,
      "dividend": 0,
      "express": 0,
      "fina_audit": 0,
      "fina_indicator": 0,
      "fina_mainbz": 0,
      "fixture_daily": 0,
      "forecast": 0,
      "hk_hold": 0,
      "hs_const": 0,
      "hsgt_top10": 0,
      "income": 0,
      "index_classify": 0,
      "index_member": 0,
      "index_member_all": 0,
      "margin": 0,
      "margin_detail": 0,
      "moneyflow": 0,
      "moneyflow_hsgt": 0,
      "monthly": 0,
      "namechange": 0,
      "new_share": 0,
      "pledge_detail": 0,
      "pledge_stat": 0,
      "repurchase": 0,
      "share_float": 0,
      "slb_len": 0,
      "slb_sec": 0,
      "slb_sec_detail": 0,
      "stk_holdernumber": 0,
      "stk_holdertrade": 0,
      "stk_limit": 0,
      "stk_managers": 0,
      "stk_rewards": 0,
      "stock_basic": 0,
      "stock_company": 0,
      "suspend_d": 0,
      "top10_floatholders": 0,
      "top10_holders": 0,
      "top_inst": 0,
      "top_list": 0,
      "trade_cal": 0,
      "weekly": 0
    },
    "successfulMigrations": 7
  },
  "databaseFinal": {
    "businessTables": 50,
    "counts": {
      "adj_factor": 5553,
      "balancesheet": 0,
      "block_trade": 139,
      "broker_recommend": 0,
      "cashflow": 0,
      "daily": 5535,
      "daily_basic": 5535,
      "disclosure_date": 10,
      "dividend": 38,
      "express": 1,
      "fina_audit": 0,
      "fina_indicator": 0,
      "fina_mainbz": 150,
      "fixture_daily": 1,
      "forecast": 3,
      "hk_hold": 0,
      "hs_const": 0,
      "hsgt_top10": 0,
      "income": 0,
      "index_classify": 0,
      "index_member": 0,
      "index_member_all": 0,
      "margin": 3,
      "margin_detail": 4424,
      "moneyflow": 5535,
      "moneyflow_hsgt": 0,
      "monthly": 0,
      "namechange": 0,
      "new_share": 0,
      "pledge_detail": 0,
      "pledge_stat": 0,
      "repurchase": 27,
      "share_float": 0,
      "slb_len": 0,
      "slb_sec": 0,
      "slb_sec_detail": 0,
      "stk_holdernumber": 150,
      "stk_holdertrade": 44,
      "stk_limit": 7733,
      "stk_managers": 0,
      "stk_rewards": 1428,
      "stock_basic": 5895,
      "stock_company": 6294,
      "suspend_d": 5,
      "top10_floatholders": 280,
      "top10_holders": 320,
      "top_inst": 0,
      "top_list": 67,
      "trade_cal": 2,
      "weekly": 5613
    },
    "successfulMigrations": 7
  },
  "elapsedSeconds": 150,
  "finalExitCode": 1,
  "npxExitCode": 1,
  "ownedContainerRemoved": true,
  "ownedWorkersExited": true,
  "postCliScan": {
    "cleanupPassed": true,
    "deletedArtifacts": 2,
    "filesScanned": 5,
    "npxExitCode": 1,
    "scanPassed": true
  },
  "scopeId": "points-2000",
  "selectedPageCountsAndExcludedEmptyMatched": false,
  "sourceTask": "M14-T05",
  "specResults": {
    "cleanup": {
      "immutableInputs": true,
      "jvmStopped": true,
      "logScanned": true,
      "networkDrained": true
    },
    "command": "npx playwright test e2e/tushare-live.spec.js --workers=1",
    "downloads": [
      {
        "apiName": "stock_basic",
        "durationMs": 2736,
        "insertedRows": 5556,
        "outcome": "SUCCESS",
        "requestId": "c9a7c9e9-d3d2-46dd-84ba-3f779c3c20a9",
        "sourceRowCount": 5556,
        "updatedRows": 0
      },
      {
        "apiName": "stock_basic",
        "durationMs": 24,
        "insertedRows": 0,
        "outcome": "EMPTY",
        "requestId": "9643e9ec-be93-4544-aff8-3188fb95046b",
        "sourceRowCount": 0,
        "updatedRows": 0
      },
      {
        "apiName": "stock_basic",
        "durationMs": 201,
        "insertedRows": 339,
        "outcome": "SUCCESS",
        "requestId": "128197b8-84fa-4b92-8292-f5e6f5c5b9da",
        "sourceRowCount": 339,
        "updatedRows": 0
      },
      {
        "apiName": "stock_company",
        "durationMs": 1498,
        "insertedRows": 2457,
        "outcome": "SUCCESS",
        "requestId": "3f7773a4-6f84-468a-a84b-521ed2c88de9",
        "sourceRowCount": 2457,
        "updatedRows": 0
      },
      {
        "apiName": "stock_company",
        "durationMs": 1791,
        "insertedRows": 3083,
        "outcome": "SUCCESS",
        "requestId": "40ce0686-6ad6-4ac0-bf26-ec68e84b73ce",
        "sourceRowCount": 3083,
        "updatedRows": 0
      },
      {
        "apiName": "stock_company",
        "durationMs": 460,
        "insertedRows": 754,
        "outcome": "SUCCESS",
        "requestId": "7a9e66f1-4099-410f-86e7-bbac8acc6114",
        "sourceRowCount": 754,
        "updatedRows": 0
      },
      {
        "apiName": "income",
        "durationMs": 23,
        "insertedRows": 0,
        "outcome": "EMPTY",
        "requestId": "1f1b8787-94e3-4e5a-b37d-ebf809f801c5",
        "sourceRowCount": 0,
        "updatedRows": 0
      },
      {
        "apiName": "balancesheet",
        "durationMs": 26,
        "insertedRows": 0,
        "outcome": "EMPTY",
        "requestId": "f4388c2b-3493-4c23-8cd9-0f2517ca1c4f",
        "sourceRowCount": 0,
        "updatedRows": 0
      },
      {
        "apiName": "cashflow",
        "durationMs": 25,
        "insertedRows": 0,
        "outcome": "EMPTY",
        "requestId": "a98e3fb7-977f-4868-9b3f-db47c4517c53",
        "sourceRowCount": 0,
        "updatedRows": 0
      },
      {
        "apiName": "fina_indicator",
        "durationMs": 26,
        "insertedRows": 0,
        "outcome": "EMPTY",
        "requestId": "8523e526-504d-4a7b-9481-2181e2d354dd",
        "sourceRowCount": 0,
        "updatedRows": 0
      },
      {
        "apiName": "fina_audit",
        "durationMs": 20,
        "insertedRows": 0,
        "outcome": "EMPTY",
        "requestId": "244e585d-35e2-46de-971f-ad41480d9080",
        "sourceRowCount": 0,
        "updatedRows": 0
      },
      {
        "apiName": "fina_mainbz",
        "durationMs": 100,
        "insertedRows": 150,
        "outcome": "SUCCESS",
        "requestId": "5eac66b3-260b-4591-85d0-7de97f9ac9e7",
        "sourceRowCount": 150,
        "updatedRows": 0
      },
      {
        "apiName": "stk_rewards",
        "durationMs": 631,
        "insertedRows": 1428,
        "outcome": "SUCCESS",
        "requestId": "1a26c625-2e7f-4827-9512-c71b1cd3c987",
        "sourceRowCount": 1428,
        "updatedRows": 0
      },
      {
        "apiName": "stk_holdernumber",
        "durationMs": 98,
        "insertedRows": 150,
        "outcome": "SUCCESS",
        "requestId": "5d5b0854-0fc1-4bcd-a8d7-f2ce3eb9a848",
        "sourceRowCount": 150,
        "updatedRows": 0
      },
      {
        "apiName": "trade_cal",
        "durationMs": 28,
        "insertedRows": 1,
        "outcome": "SUCCESS",
        "requestId": "9c91bc43-98b3-4973-9601-dbc1ade3e006",
        "sourceRowCount": 1,
        "updatedRows": 0
      },
      {
        "apiName": "trade_cal",
        "durationMs": 27,
        "insertedRows": 1,
        "outcome": "SUCCESS",
        "requestId": "7b786638-8d3f-48d0-950b-2dd02eb7f393",
        "sourceRowCount": 1,
        "updatedRows": 0
      },
      {
        "apiName": "trade_cal",
        "durationMs": 21,
        "insertedRows": 0,
        "outcome": "EMPTY",
        "requestId": "4a173f3b-8e81-44ce-8e44-c5054f15b2c5",
        "sourceRowCount": 0,
        "updatedRows": 0
      },
      {
        "apiName": "margin",
        "durationMs": 33,
        "insertedRows": 1,
        "outcome": "SUCCESS",
        "requestId": "31aefcaf-1b8f-4964-8a28-7dd5d4662a98",
        "sourceRowCount": 1,
        "updatedRows": 0
      },
      {
        "apiName": "margin",
        "durationMs": 30,
        "insertedRows": 1,
        "outcome": "SUCCESS",
        "requestId": "e3c9e83c-5db0-4847-8c5c-08ef005f7e5f",
        "sourceRowCount": 1,
        "updatedRows": 0
      },
      {
        "apiName": "margin",
        "durationMs": 104,
        "insertedRows": 1,
        "outcome": "SUCCESS",
        "requestId": "012f5ce0-4fbb-4c28-947f-a4f733a953b7",
        "sourceRowCount": 1,
        "updatedRows": 0
      },
      {
        "apiName": "daily",
        "durationMs": 2264,
        "insertedRows": 5535,
        "outcome": "SUCCESS",
        "requestId": "2baa1539-d9a6-46e7-9ab1-aefe35ab0611",
        "sourceRowCount": 5535,
        "updatedRows": 0
      },
      {
        "apiName": "weekly",
        "durationMs": 2298,
        "insertedRows": 5613,
        "outcome": "SUCCESS",
        "requestId": "f75fd873-daba-49d9-9219-1d377a321851",
        "sourceRowCount": 5613,
        "updatedRows": 0
      },
      {
        "apiName": "monthly",
        "durationMs": 31,
        "insertedRows": 0,
        "outcome": "EMPTY",
        "requestId": "a16ab42c-8365-4b1f-98a2-b66a54aceae1",
        "sourceRowCount": 0,
        "updatedRows": 0
      },
      {
        "apiName": "adj_factor",
        "durationMs": 2105,
        "insertedRows": 5553,
        "outcome": "SUCCESS",
        "requestId": "42fe5ce8-2d6e-438e-8ae6-f33582ae6d48",
        "sourceRowCount": 5553,
        "updatedRows": 0
      },
      {
        "apiName": "suspend_d",
        "durationMs": 37,
        "insertedRows": 5,
        "outcome": "SUCCESS",
        "requestId": "a8de848a-def2-48f6-96e0-77554e0224c6",
        "sourceRowCount": 5,
        "updatedRows": 0
      },
      {
        "apiName": "daily_basic",
        "durationMs": 2885,
        "insertedRows": 5535,
        "outcome": "SUCCESS",
        "requestId": "60ebe366-2c32-4760-8fd1-867d7369ac36",
        "sourceRowCount": 5535,
        "updatedRows": 0
      },
      {
        "apiName": "moneyflow",
        "durationMs": 2568,
        "insertedRows": 5535,
        "outcome": "SUCCESS",
        "requestId": "9e3f9f6e-1bfa-42e4-a99f-a46942dc4ccc",
        "sourceRowCount": 5535,
        "updatedRows": 0
      },
      {
        "apiName": "stk_limit",
        "durationMs": 2817,
        "insertedRows": 7733,
        "outcome": "SUCCESS",
        "requestId": "db2d3ee6-b4e6-44b2-bb59-2b776bab3a01",
        "sourceRowCount": 7733,
        "updatedRows": 0
      },
      {
        "apiName": "top_list",
        "durationMs": 97,
        "insertedRows": 67,
        "outcome": "SUCCESS",
        "requestId": "a7709619-cd39-4805-b3d4-d02987138329",
        "sourceRowCount": 67,
        "updatedRows": 0
      },
      {
        "apiName": "margin_detail",
        "durationMs": 1758,
        "insertedRows": 4424,
        "outcome": "SUCCESS",
        "requestId": "3cc02d83-70de-47c1-b75c-2c323cd94171",
        "sourceRowCount": 4424,
        "updatedRows": 0
      },
      {
        "apiName": "block_trade",
        "durationMs": 98,
        "insertedRows": 139,
        "outcome": "SUCCESS",
        "requestId": "c67837b0-d188-429e-8216-fd37a2567e0c",
        "sourceRowCount": 159,
        "updatedRows": 0
      },
      {
        "apiName": "slb_len",
        "durationMs": 27,
        "insertedRows": 0,
        "outcome": "EMPTY",
        "requestId": "fa24bf34-4222-4703-87ae-bfc5b3d7bfe8",
        "sourceRowCount": 0,
        "updatedRows": 0
      },
      {
        "apiName": "slb_sec",
        "durationMs": 25,
        "insertedRows": 0,
        "outcome": "EMPTY",
        "requestId": "94d441fc-5aa0-4850-9b2d-b2d4a0954aaa",
        "sourceRowCount": 0,
        "updatedRows": 0
      },
      {
        "apiName": "slb_sec_detail",
        "durationMs": 24,
        "insertedRows": 0,
        "outcome": "EMPTY",
        "requestId": "97771926-d225-4a8f-b1d3-7782b01c1999",
        "sourceRowCount": 0,
        "updatedRows": 0
      },
      {
        "apiName": "forecast",
        "durationMs": 28,
        "insertedRows": 3,
        "outcome": "SUCCESS",
        "requestId": "2f47b5e6-043f-4599-99ae-573867dfa505",
        "sourceRowCount": 3,
        "updatedRows": 0
      },
      {
        "apiName": "express",
        "durationMs": 45,
        "insertedRows": 1,
        "outcome": "SUCCESS",
        "requestId": "0bd47300-7591-477e-ba77-876ab7e90bf7",
        "sourceRowCount": 1,
        "updatedRows": 0
      },
      {
        "apiName": "dividend",
        "durationMs": 392,
        "insertedRows": 38,
        "outcome": "SUCCESS",
        "requestId": "0df8e232-b1f2-4a48-95d5-0444245de5fb",
        "sourceRowCount": 38,
        "updatedRows": 0
      },
      {
        "apiName": "disclosure_date",
        "durationMs": 123,
        "insertedRows": 10,
        "outcome": "SUCCESS",
        "requestId": "3f8fde0a-e3be-4ee4-bf34-f654a37d382d",
        "sourceRowCount": 10,
        "updatedRows": 0
      },
      {
        "apiName": "repurchase",
        "durationMs": 40,
        "insertedRows": 27,
        "outcome": "SUCCESS",
        "requestId": "3a1b7df2-b927-480e-9e25-237aeac7a16e",
        "sourceRowCount": 27,
        "updatedRows": 0
      },
      {
        "apiName": "stk_holdertrade",
        "durationMs": 55,
        "insertedRows": 44,
        "outcome": "SUCCESS",
        "requestId": "4db11750-e778-4f72-82fb-e21d8306f207",
        "sourceRowCount": 157,
        "updatedRows": 0
      },
      {
        "apiName": "top10_holders",
        "durationMs": 174,
        "insertedRows": 320,
        "outcome": "SUCCESS",
        "requestId": "3bfd840f-b3f2-4fed-b022-c78b59c3d226",
        "sourceRowCount": 320,
        "updatedRows": 0
      },
      {
        "apiName": "top10_floatholders",
        "durationMs": 165,
        "insertedRows": 280,
        "outcome": "SUCCESS",
        "requestId": "7dda164d-82b7-435f-a866-55f83d0a90f4",
        "sourceRowCount": 280,
        "updatedRows": 0
      }
    ],
    "finishedAt": "2026-09-06T11:57:31.916Z",
    "fixture": [
      {
        "apiName": "fixture_daily",
        "durationMs": 5,
        "outcome": "SUCCESS",
        "requestId": "0678008a-bbf3-447a-b458-21cb99f65110",
        "resultCount": 0,
        "totalElements": 0
      },
      {
        "apiName": "fixture_daily",
        "durationMs": 22,
        "insertedRows": 1,
        "outcome": "SUCCESS",
        "requestId": "55146ca5-949a-4235-83f3-ad07615d6615",
        "sourceRowCount": 1,
        "updatedRows": 0
      },
      {
        "apiName": "fixture_daily",
        "durationMs": 5,
        "outcome": "SUCCESS",
        "requestId": "3250ad97-359e-4870-aef5-c906d53ce0d0",
        "resultCount": 1,
        "totalElements": 1
      },
      {
        "apiName": "fixture_daily",
        "durationMs": 0,
        "insertedRows": 0,
        "outcome": "EMPTY",
        "requestId": "f765e2f1-d89d-4c16-893d-4a8292b11225",
        "sourceRowCount": 0,
        "updatedRows": 0
      },
      {
        "apiName": "fixture_daily",
        "durationMs": 4,
        "outcome": "SUCCESS",
        "requestId": "6a8fbb0a-4805-4bfe-9294-35c2fb8d436c",
        "resultCount": 1,
        "totalElements": 1
      }
    ],
    "inputs": {
      "gitCommit": "f22c1f2c7a08e0e2a0651b4270a73903c8c1d32c",
      "jarSha256": "81adba0dd6500f4aa43b4fa06b18c2c8e7b7454d9e6d4d6c734772cdaef1d002",
      "manifestSha256": "37a317f6a2bc3e5113be5f127976d16d8349414c6476c7f6a194b084a5b0f7c2",
      "specSha256": "20499ebe50010f07edcbfc6502ed13fe84aac06f557700f92f8cde7fd9757f94"
    },
    "queries": [
      {
        "apiName": "stock_basic",
        "durationMs": 2,
        "outcome": "SUCCESS",
        "requestId": "b062f188-55b1-4cf6-bc27-bc6439002120",
        "resultCount": 0,
        "totalElements": 0
      },
      {
        "apiName": "stock_basic",
        "durationMs": 6,
        "outcome": "SUCCESS",
        "requestId": "6d12e20d-d336-41e5-a534-7272a9e2f689",
        "resultCount": 50,
        "totalElements": 5895
      },
      {
        "apiName": "stock_company",
        "durationMs": 2,
        "outcome": "SUCCESS",
        "requestId": "9377c619-ce09-4ef5-8be5-8ddd17789272",
        "resultCount": 0,
        "totalElements": 0
      },
      {
        "apiName": "stock_company",
        "durationMs": 10,
        "outcome": "SUCCESS",
        "requestId": "6f881964-82db-4acb-a684-e67d0bd30068",
        "resultCount": 50,
        "totalElements": 6294
      },
      {
        "apiName": "income",
        "durationMs": 3,
        "outcome": "SUCCESS",
        "requestId": "25e3ecdf-8040-489f-924a-49b692f93fce",
        "resultCount": 0,
        "totalElements": 0
      },
      {
        "apiName": "income",
        "durationMs": 2,
        "outcome": "SUCCESS",
        "requestId": "47223de8-7b12-4279-bca2-03b1feed65be",
        "resultCount": 0,
        "totalElements": 0
      },
      {
        "apiName": "balancesheet",
        "durationMs": 3,
        "outcome": "SUCCESS",
        "requestId": "5880905a-4e3e-4e1e-8ead-3e8b042eee3f",
        "resultCount": 0,
        "totalElements": 0
      },
      {
        "apiName": "balancesheet",
        "durationMs": 2,
        "outcome": "SUCCESS",
        "requestId": "d049fcf7-f39c-4c1d-a588-4ee2350170a0",
        "resultCount": 0,
        "totalElements": 0
      },
      {
        "apiName": "cashflow",
        "durationMs": 2,
        "outcome": "SUCCESS",
        "requestId": "292c871f-3461-4f73-a732-d29d3b89c92c",
        "resultCount": 0,
        "totalElements": 0
      },
      {
        "apiName": "cashflow",
        "durationMs": 2,
        "outcome": "SUCCESS",
        "requestId": "595d82a3-49ed-4901-99fc-89670615b425",
        "resultCount": 0,
        "totalElements": 0
      },
      {
        "apiName": "fina_indicator",
        "durationMs": 2,
        "outcome": "SUCCESS",
        "requestId": "c4cf7501-3960-40ab-8cba-38c037279ecf",
        "resultCount": 0,
        "totalElements": 0
      },
      {
        "apiName": "fina_indicator",
        "durationMs": 2,
        "outcome": "SUCCESS",
        "requestId": "1142a256-7329-4c14-8017-adcba549cfce",
        "resultCount": 0,
        "totalElements": 0
      },
      {
        "apiName": "fina_audit",
        "durationMs": 2,
        "outcome": "SUCCESS",
        "requestId": "849ab45b-2155-4947-b820-7bd6829b3110",
        "resultCount": 0,
        "totalElements": 0
      },
      {
        "apiName": "fina_audit",
        "durationMs": 1,
        "outcome": "SUCCESS",
        "requestId": "d0ca1a6c-efcb-4b50-a9da-95e6ff96ad79",
        "resultCount": 0,
        "totalElements": 0
      },
      {
        "apiName": "fina_mainbz",
        "durationMs": 2,
        "outcome": "SUCCESS",
        "requestId": "30d182de-bae7-4e34-99c9-9c1750f6b496",
        "resultCount": 0,
        "totalElements": 0
      },
      {
        "apiName": "fina_mainbz",
        "durationMs": 5,
        "outcome": "SUCCESS",
        "requestId": "6d48b08d-9750-4cce-b9bc-d411ef3e471f",
        "resultCount": 50,
        "totalElements": 150
      },
      {
        "apiName": "stk_rewards",
        "durationMs": 2,
        "outcome": "SUCCESS",
        "requestId": "fd82c0bc-5bc7-44d6-8cda-31f00482233d",
        "resultCount": 0,
        "totalElements": 0
      },
      {
        "apiName": "stk_rewards",
        "durationMs": 4,
        "outcome": "SUCCESS",
        "requestId": "b8a549dd-7407-40a8-a5bc-ec42da62e306",
        "resultCount": 50,
        "totalElements": 1428
      },
      {
        "apiName": "stk_holdernumber",
        "durationMs": 2,
        "outcome": "SUCCESS",
        "requestId": "4f717f46-f2c7-4ad2-b3e9-1eb8a704b897",
        "resultCount": 0,
        "totalElements": 0
      },
      {
        "apiName": "stk_holdernumber",
        "durationMs": 3,
        "outcome": "SUCCESS",
        "requestId": "6f9f56e5-cee0-4506-af4a-e2dfd3186327",
        "resultCount": 50,
        "totalElements": 150
      },
      {
        "apiName": "trade_cal",
        "durationMs": 3,
        "outcome": "SUCCESS",
        "requestId": "08f9f977-6deb-4d9d-917f-525f7ee19c2a",
        "resultCount": 0,
        "totalElements": 0
      },
      {
        "apiName": "trade_cal",
        "durationMs": 3,
        "outcome": "SUCCESS",
        "requestId": "7a155a44-ab54-4f90-af96-904a2da2fcdb",
        "resultCount": 2,
        "totalElements": 2
      },
      {
        "apiName": "margin",
        "durationMs": 1,
        "outcome": "SUCCESS",
        "requestId": "7c00c677-424b-4867-8af0-f4c22948caf2",
        "resultCount": 0,
        "totalElements": 0
      },
      {
        "apiName": "margin",
        "durationMs": 3,
        "outcome": "SUCCESS",
        "requestId": "5f98e9df-dd55-4ad2-b3d6-c2394dc77c00",
        "resultCount": 3,
        "totalElements": 3
      },
      {
        "apiName": "daily",
        "durationMs": 2,
        "outcome": "SUCCESS",
        "requestId": "69de6467-a09d-429c-bfd8-918acffb57ca",
        "resultCount": 0,
        "totalElements": 0
      },
      {
        "apiName": "daily",
        "durationMs": 5,
        "outcome": "SUCCESS",
        "requestId": "d6f8e46a-0482-49c4-8c31-e6c9494270d5",
        "resultCount": 50,
        "totalElements": 5535
      },
      {
        "apiName": "weekly",
        "durationMs": 2,
        "outcome": "SUCCESS",
        "requestId": "40282bc5-6dfa-4c7b-a563-c23ff62c28c8",
        "resultCount": 0,
        "totalElements": 0
      },
      {
        "apiName": "weekly",
        "durationMs": 4,
        "outcome": "SUCCESS",
        "requestId": "f6b47ead-15f2-4add-af45-171398c8d0a6",
        "resultCount": 50,
        "totalElements": 5613
      },
      {
        "apiName": "monthly",
        "durationMs": 2,
        "outcome": "SUCCESS",
        "requestId": "0c2c5cfa-cf9e-499e-8a37-bf44ec2bf3a6",
        "resultCount": 0,
        "totalElements": 0
      },
      {
        "apiName": "monthly",
        "durationMs": 1,
        "outcome": "SUCCESS",
        "requestId": "f7d0a4f1-cee2-476e-b66a-348f54d1dcaa",
        "resultCount": 0,
        "totalElements": 0
      },
      {
        "apiName": "adj_factor",
        "durationMs": 2,
        "outcome": "SUCCESS",
        "requestId": "cadcb3e6-e141-42a1-a4b5-5230290289cb",
        "resultCount": 0,
        "totalElements": 0
      },
      {
        "apiName": "adj_factor",
        "durationMs": 4,
        "outcome": "SUCCESS",
        "requestId": "fa9e6fa7-bd6a-4610-99ec-b6ec95fdb391",
        "resultCount": 50,
        "totalElements": 5553
      },
      {
        "apiName": "suspend_d",
        "durationMs": 2,
        "outcome": "SUCCESS",
        "requestId": "1eebdff7-5932-475c-ba20-c7d771529812",
        "resultCount": 0,
        "totalElements": 0
      },
      {
        "apiName": "suspend_d",
        "durationMs": 3,
        "outcome": "SUCCESS",
        "requestId": "df72f3ae-a8ed-47d3-9023-7951d2ed866d",
        "resultCount": 5,
        "totalElements": 5
      },
      {
        "apiName": "daily_basic",
        "durationMs": 2,
        "outcome": "SUCCESS",
        "requestId": "f0a266c7-420d-44ed-90dc-8abd1d227752",
        "resultCount": 0,
        "totalElements": 0
      },
      {
        "apiName": "daily_basic",
        "durationMs": 4,
        "outcome": "SUCCESS",
        "requestId": "ce41245b-8391-4ad4-b707-3ea673ca9bdd",
        "resultCount": 50,
        "totalElements": 5535
      },
      {
        "apiName": "moneyflow",
        "durationMs": 2,
        "outcome": "SUCCESS",
        "requestId": "bd470a78-d12c-4b33-85f4-a026bd00a655",
        "resultCount": 0,
        "totalElements": 0
      },
      {
        "apiName": "moneyflow",
        "durationMs": 4,
        "outcome": "SUCCESS",
        "requestId": "6984d68e-130c-4166-ac5f-a175e0b83538",
        "resultCount": 50,
        "totalElements": 5535
      },
      {
        "apiName": "stk_limit",
        "durationMs": 2,
        "outcome": "SUCCESS",
        "requestId": "6247ada6-5e17-4e9c-86f1-a157fc2e69ce",
        "resultCount": 0,
        "totalElements": 0
      },
      {
        "apiName": "stk_limit",
        "durationMs": 4,
        "outcome": "SUCCESS",
        "requestId": "e3fe0715-313f-47e4-b812-5cb90b1f2244",
        "resultCount": 50,
        "totalElements": 7733
      },
      {
        "apiName": "top_list",
        "durationMs": 2,
        "outcome": "SUCCESS",
        "requestId": "aacacd61-a52a-4f7c-8788-01373c3881e3",
        "resultCount": 0,
        "totalElements": 0
      },
      {
        "apiName": "top_list",
        "durationMs": 3,
        "outcome": "SUCCESS",
        "requestId": "34138406-32d1-4442-9d7a-91cd367d5c75",
        "resultCount": 50,
        "totalElements": 67
      },
      {
        "apiName": "margin_detail",
        "durationMs": 2,
        "outcome": "SUCCESS",
        "requestId": "5dab1de1-55ac-47ce-85b8-64c2c226073d",
        "resultCount": 0,
        "totalElements": 0
      },
      {
        "apiName": "margin_detail",
        "durationMs": 3,
        "outcome": "SUCCESS",
        "requestId": "278d98ea-93fb-40fc-b8b7-b581232839d4",
        "resultCount": 50,
        "totalElements": 4424
      },
      {
        "apiName": "block_trade",
        "durationMs": 2,
        "outcome": "SUCCESS",
        "requestId": "8d4bbb04-ec03-4a80-ab35-17b4729f4a14",
        "resultCount": 0,
        "totalElements": 0
      },
      {
        "apiName": "block_trade",
        "durationMs": 3,
        "outcome": "SUCCESS",
        "requestId": "e8d0fdb2-bdb6-4234-badd-6731fee35996",
        "resultCount": 50,
        "totalElements": 139
      },
      {
        "apiName": "slb_len",
        "durationMs": 2,
        "outcome": "SUCCESS",
        "requestId": "977f2682-4ab7-4d04-b4f6-f3f0138256f7",
        "resultCount": 0,
        "totalElements": 0
      },
      {
        "apiName": "slb_len",
        "durationMs": 2,
        "outcome": "SUCCESS",
        "requestId": "60119df0-8b1c-4887-9713-67de1b3bf3d5",
        "resultCount": 0,
        "totalElements": 0
      },
      {
        "apiName": "slb_sec",
        "durationMs": 2,
        "outcome": "SUCCESS",
        "requestId": "6c1ca688-30ea-4904-9c63-be6d0f89622c",
        "resultCount": 0,
        "totalElements": 0
      },
      {
        "apiName": "slb_sec",
        "durationMs": 2,
        "outcome": "SUCCESS",
        "requestId": "6bb8049f-8871-4a44-894d-9a8e4907491b",
        "resultCount": 0,
        "totalElements": 0
      },
      {
        "apiName": "slb_sec_detail",
        "durationMs": 2,
        "outcome": "SUCCESS",
        "requestId": "270d6f2f-78ef-4d76-bfe4-09d6c037e0c3",
        "resultCount": 0,
        "totalElements": 0
      },
      {
        "apiName": "slb_sec_detail",
        "durationMs": 1,
        "outcome": "SUCCESS",
        "requestId": "40aea2e8-5a5e-4775-8a04-947cc21601ab",
        "resultCount": 0,
        "totalElements": 0
      },
      {
        "apiName": "forecast",
        "durationMs": 2,
        "outcome": "SUCCESS",
        "requestId": "ef0afee3-a91d-4e53-a214-aca4906f340f",
        "resultCount": 0,
        "totalElements": 0
      },
      {
        "apiName": "forecast",
        "durationMs": 3,
        "outcome": "SUCCESS",
        "requestId": "b3d7c15b-cd5e-43f4-935b-ca67ff9f7c9e",
        "resultCount": 3,
        "totalElements": 3
      },
      {
        "apiName": "express",
        "durationMs": 2,
        "outcome": "SUCCESS",
        "requestId": "7f9e80c2-92f3-441b-a5b2-c99c81a94d69",
        "resultCount": 0,
        "totalElements": 0
      },
      {
        "apiName": "express",
        "durationMs": 2,
        "outcome": "SUCCESS",
        "requestId": "85fd66be-15bc-4905-a02b-59128b50b83b",
        "resultCount": 1,
        "totalElements": 1
      },
      {
        "apiName": "dividend",
        "durationMs": 2,
        "outcome": "SUCCESS",
        "requestId": "0cced0f7-507f-4e51-be08-851beb64f87a",
        "resultCount": 0,
        "totalElements": 0
      },
      {
        "apiName": "dividend",
        "durationMs": 3,
        "outcome": "SUCCESS",
        "requestId": "26572275-f19f-4cad-9b9c-f26dab0bae47",
        "resultCount": 38,
        "totalElements": 38
      },
      {
        "apiName": "disclosure_date",
        "durationMs": 2,
        "outcome": "SUCCESS",
        "requestId": "d736f868-e45d-42e0-a551-7dc93099415a",
        "resultCount": 0,
        "totalElements": 0
      },
      {
        "apiName": "disclosure_date",
        "durationMs": 3,
        "outcome": "SUCCESS",
        "requestId": "47db88a2-056b-4d95-96d9-d9ed6a6f1a92",
        "resultCount": 10,
        "totalElements": 10
      },
      {
        "apiName": "repurchase",
        "durationMs": 2,
        "outcome": "SUCCESS",
        "requestId": "5e539f39-1ac1-4900-860b-3dbdf20f10ee",
        "resultCount": 0,
        "totalElements": 0
      },
      {
        "apiName": "repurchase",
        "durationMs": 3,
        "outcome": "SUCCESS",
        "requestId": "fbd7cdaa-6d42-44e3-ac5f-239e48372952",
        "resultCount": 27,
        "totalElements": 27
      },
      {
        "apiName": "stk_holdertrade",
        "durationMs": 2,
        "outcome": "SUCCESS",
        "requestId": "08926ec3-af7d-4c91-8051-200f7a28ac5f",
        "resultCount": 0,
        "totalElements": 0
      },
      {
        "apiName": "stk_holdertrade",
        "durationMs": 3,
        "outcome": "SUCCESS",
        "requestId": "abd3fd39-c912-4ebb-bac3-ebb60d016254",
        "resultCount": 44,
        "totalElements": 44
      },
      {
        "apiName": "top10_holders",
        "durationMs": 2,
        "outcome": "SUCCESS",
        "requestId": "71e4b1de-38ff-4ed9-8101-139967443e16",
        "resultCount": 0,
        "totalElements": 0
      },
      {
        "apiName": "top10_holders",
        "durationMs": 3,
        "outcome": "SUCCESS",
        "requestId": "339a8907-ff95-4353-99e5-f436f55cd368",
        "resultCount": 50,
        "totalElements": 320
      },
      {
        "apiName": "top10_floatholders",
        "durationMs": 2,
        "outcome": "SUCCESS",
        "requestId": "23373c5b-0257-4f7d-bcef-9c4f3d49ee51",
        "resultCount": 0,
        "totalElements": 0
      }
    ],
    "scope": {
      "acceptanceStatuses": {
        "empty": 10,
        "ok": 30
      },
      "excludedInterfaces": [
        {
          "apiName": "top_inst",
          "reason": "higher_points"
        },
        {
          "apiName": "broker_recommend",
          "reason": "higher_points"
        },
        {
          "apiName": "share_float",
          "reason": "permission_unverified"
        },
        {
          "apiName": "hs_const",
          "reason": "permission_unverified"
        },
        {
          "apiName": "moneyflow_hsgt",
          "reason": "permission_unverified"
        },
        {
          "apiName": "hk_hold",
          "reason": "permission_unverified"
        },
        {
          "apiName": "index_member",
          "reason": "permission_unverified"
        },
        {
          "apiName": "hsgt_top10",
          "reason": "permission_unverified"
        },
        {
          "apiName": "namechange",
          "reason": "permission_unverified"
        }
      ],
      "id": "points-2000",
      "interfaceStatuses": [
        {
          "acceptanceStatus": "ok",
          "apiName": "stock_basic",
          "manifestStatus": "ok"
        },
        {
          "acceptanceStatus": "ok",
          "apiName": "stock_company",
          "manifestStatus": "ok"
        },
        {
          "acceptanceStatus": "empty",
          "apiName": "income",
          "manifestStatus": "empty"
        },
        {
          "acceptanceStatus": "empty",
          "apiName": "balancesheet",
          "manifestStatus": "empty"
        },
        {
          "acceptanceStatus": "empty",
          "apiName": "cashflow",
          "manifestStatus": "empty"
        },
        {
          "acceptanceStatus": "empty",
          "apiName": "fina_indicator",
          "manifestStatus": "empty"
        },
        {
          "acceptanceStatus": "empty",
          "apiName": "fina_audit",
          "manifestStatus": "empty"
        },
        {
          "acceptanceStatus": "ok",
          "apiName": "fina_mainbz",
          "manifestStatus": "ok"
        },
        {
          "acceptanceStatus": "ok",
          "apiName": "stk_rewards",
          "manifestStatus": "ok"
        },
        {
          "acceptanceStatus": "ok",
          "apiName": "stk_holdernumber",
          "manifestStatus": "ok"
        },
        {
          "acceptanceStatus": "ok",
          "apiName": "trade_cal",
          "manifestStatus": "ok"
        },
        {
          "acceptanceStatus": "ok",
          "apiName": "margin",
          "manifestStatus": "ok"
        },
        {
          "acceptanceStatus": "ok",
          "apiName": "daily",
          "manifestStatus": "ok"
        },
        {
          "acceptanceStatus": "ok",
          "apiName": "weekly",
          "manifestStatus": "ok"
        },
        {
          "acceptanceStatus": "empty",
          "apiName": "monthly",
          "manifestStatus": "empty"
        },
        {
          "acceptanceStatus": "ok",
          "apiName": "adj_factor",
          "manifestStatus": "ok"
        },
        {
          "acceptanceStatus": "ok",
          "apiName": "suspend_d",
          "manifestStatus": "ok"
        },
        {
          "acceptanceStatus": "ok",
          "apiName": "daily_basic",
          "manifestStatus": "ok"
        },
        {
          "acceptanceStatus": "ok",
          "apiName": "moneyflow",
          "manifestStatus": "ok"
        },
        {
          "acceptanceStatus": "ok",
          "apiName": "stk_limit",
          "manifestStatus": "ok"
        },
        {
          "acceptanceStatus": "ok",
          "apiName": "top_list",
          "manifestStatus": "ok"
        },
        {
          "acceptanceStatus": "ok",
          "apiName": "margin_detail",
          "manifestStatus": "ok"
        },
        {
          "acceptanceStatus": "ok",
          "apiName": "block_trade",
          "manifestStatus": "ok"
        },
        {
          "acceptanceStatus": "empty",
          "apiName": "slb_len",
          "manifestStatus": "empty"
        },
        {
          "acceptanceStatus": "empty",
          "apiName": "slb_sec",
          "manifestStatus": "empty"
        },
        {
          "acceptanceStatus": "empty",
          "apiName": "slb_sec_detail",
          "manifestStatus": "empty"
        },
        {
          "acceptanceStatus": "ok",
          "apiName": "forecast",
          "manifestStatus": "ok"
        },
        {
          "acceptanceStatus": "ok",
          "apiName": "express",
          "manifestStatus": "ok"
        },
        {
          "acceptanceStatus": "ok",
          "apiName": "dividend",
          "manifestStatus": "empty"
        },
        {
          "acceptanceStatus": "ok",
          "apiName": "disclosure_date",
          "manifestStatus": "ok"
        },
        {
          "acceptanceStatus": "ok",
          "apiName": "repurchase",
          "manifestStatus": "ok"
        },
        {
          "acceptanceStatus": "ok",
          "apiName": "stk_holdertrade",
          "manifestStatus": "ok"
        },
        {
          "acceptanceStatus": "ok",
          "apiName": "top10_holders",
          "manifestStatus": "empty"
        },
        {
          "acceptanceStatus": "empty",
          "apiName": "top10_floatholders",
          "manifestStatus": "empty"
        },
        {
          "acceptanceStatus": "ok",
          "apiName": "new_share",
          "manifestStatus": "ok"
        },
        {
          "acceptanceStatus": "ok",
          "apiName": "stk_managers",
          "manifestStatus": "ok"
        },
        {
          "acceptanceStatus": "ok",
          "apiName": "pledge_stat",
          "manifestStatus": "ok"
        },
        {
          "acceptanceStatus": "ok",
          "apiName": "pledge_detail",
          "manifestStatus": "ok"
        },
        {
          "acceptanceStatus": "ok",
          "apiName": "index_classify",
          "manifestStatus": "ok"
        },
        {
          "acceptanceStatus": "ok",
          "apiName": "index_member_all",
          "manifestStatus": "ok"
        }
      ],
      "manifestCases": 49,
      "manifestSamples": 58,
      "manifestStatuses": {
        "empty": 12,
        "ok": 28
      },
      "selectedCases": 40,
      "selectedSamples": 48
    },
    "sourceTask": "M14-T05",
    "startedAt": "2026-09-06T11:55:04.547Z",
    "task": "M14-T09",
    "totals": {
      "attemptedCases": 34,
      "callIntervalMs": 2000,
      "completedCases": 33,
      "failedCases": 1,
      "fixtureDownloadPostsObserved": 2,
      "fixtureRecordsGetsObserved": 3,
      "liveDownloadPostsObserved": 42,
      "liveDownloadResultsRecorded": 42,
      "liveQueryResultsRecorded": 67,
      "liveRecordsGetsObserved": 67,
      "manifestSamples": 58,
      "registeredCases": 40,
      "selectedSamples": 48,
      "unexecutedCases": 6
    },
    "version": 2
  },
  "task": "M14-T09"
}
```
