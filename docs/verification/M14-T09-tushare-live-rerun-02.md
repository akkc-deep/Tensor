# M14-T09 D-03完整复验（第2次复跑）

本轮归属M14-T09，执行[当前设计](../task-designs/M14-T09-design.md)已获用户明确“同意”的D-03：仅增加top10_floatholders当前ok覆盖，保留dividend/top10_holders与其他37项期望、历史分类和全部原参数。历史28ok/12empty，当前31ok/9empty；真实来源数量不是未来固定值。

## 冻结输入与历史证据

- 原manifest SHA `37a317f6a2bc3e5113be5f127976d16d8349414c6476c7f6a194b084a5b0f7c2`，全部模板与原参数不变。
- 继续使用冻结JAR `/private/tmp/tensor-m14-t09-green.MZ4kMkN9/data-plane/tensor-app/target/acceptance/tensor-app-1.0-SNAPSHOT-acceptance.jar`，SHA `81adba0dd6500f4aa43b4fa06b18c2c8e7b7454d9e6d4d6c734772cdaef1d002`，不改生产/schema/依赖或重建二进制。
- [D-02真实证据](M14-T09-tushare-live-rerun-01.md)：6ae877f，SHA `6f91d9cbe0f4469a1278ede8de65b56f5841b292141202b88e97f2238948f695`；33通过/1失败/6未运行。top10_floatholders下载/入库280但EMPTY断言失败、末次页面查询未执行；历史失败保持。
- [D-01真实证据及后端门禁](M14-T09-tushare-live.md)：471dfb0，SHA `9d5c283b31f586ee7a3b4fdbda84321fbf3c7e4f55274f9d69955d86353af170`。[M14-T05历史证据](M14-T05-tushare-live.md) SHA `d4e7bf67b6a2b144662a987dec9aa812a8e39543c5134ed7cab8a12a4dbe34b5`。三份已扫描历史文档均保持，不拼接任何旧轮通过数。

## 执行合同与本地准备

范围固定40接口/48组原样例/80页面records查询，fixture另计2POST/3查询；9项排除top_inst、broker_recommend、share_float、hs_const、moneyflow_hsgt、hk_hold、index_member、hsgt_top10、namechange不计skip或通过。原49目标尚未完成。单worker、零重试，上次真实下载响应完成后至少2秒再点击下一次；全部业务下载/查询来自页面，失败停止。

D-03同函数selector/outcome/finalDataset探针先RED（原spec缺第三项覆盖，exit1），两处最小修改后GREEN（exit0）；准确三项覆盖，其他状态/原参数不变，覆盖项拒绝全EMPTY与空末态，剩余empty拒绝SUCCESS，分类31/9与28/12、范围40/48/9正确。`node --check control-plane/e2e/tushare-live.spec.js` exit0；control-plane下 `npx playwright test e2e/tushare-live.spec.js --list` exit0，恰40个Chromium用例。当前spec SHA `05a6601f656dcfb5528c6ed832ccb17ef2416cfa84397fb93f11da1bfbd5901e`。

新私有控制目录 `/private/tmp/tensor-m14-t09-control.9mkzsd_0`；MySQL8.4.6、初始0表、回环绑定、真实来源host、utf8mb4/utf8mb4_0900_as_cs与CREATE/SELECT/INSERT/UPDATE/ALTER/INDEX精确六权限已实测通过。启动器仅更换证据路径为本文件，仍提取固定SHA的M14-T05扫描算法；Python语法、5前置拒绝/3范围拒绝/10生命周期终检合成探针全部通过。本地子进程净化环境，无真实业务调用。

独立D-03审查：spec符合性PASS，无Critical/Important；唯一Minor为交接将旧EMPTY合同写成当前阻塞，已改为历史D-02失败和D-03剩余门禁。运行6hash、证据路径隔离和既有生命周期安全逻辑均核对一致。

运行前状态：真实新轮尚未执行，须先完成独立接入审查及单独BLOCKED→READY→IN_PROGRESS。启动器检查当前任务状态与设计/spec/JAR/manifest/证据/自身六项hash；真实Token仅从已有TENSOR_TUSHARE_TOKEN输入受控后端。正式结果在CLI/worker退出、终检扫描、独立DB核对和自有容器/卷/私密状态清理后追加，并在真实秘密集合下扫描本文件。

## 2000档实际运行 2026-09-06T12:33:08.990446+00:00

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
      "index_classify": 359,
      "index_member": 0,
      "index_member_all": 3000,
      "margin": 3,
      "margin_detail": 4424,
      "moneyflow": 5535,
      "moneyflow_hsgt": 0,
      "monthly": 0,
      "namechange": 0,
      "new_share": 1,
      "pledge_detail": 1493,
      "pledge_stat": 3000,
      "repurchase": 27,
      "share_float": 0,
      "slb_len": 0,
      "slb_sec": 0,
      "slb_sec_detail": 0,
      "stk_holdernumber": 150,
      "stk_holdertrade": 44,
      "stk_limit": 7733,
      "stk_managers": 3999,
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
  "elapsedSeconds": 173,
  "finalExitCode": 0,
  "npxExitCode": 0,
  "ownedContainerRemoved": true,
  "ownedWorkersExited": true,
  "postCliScan": {
    "cleanupPassed": true,
    "deletedArtifacts": 1,
    "filesScanned": 4,
    "npxExitCode": 0,
    "scanPassed": true
  },
  "scopeId": "points-2000",
  "selectedPageCountsAndExcludedEmptyMatched": true,
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
        "durationMs": 2713,
        "insertedRows": 5556,
        "outcome": "SUCCESS",
        "requestId": "703cf84e-2cc1-4f64-8058-d9efe6996937",
        "sourceRowCount": 5556,
        "updatedRows": 0
      },
      {
        "apiName": "stock_basic",
        "durationMs": 29,
        "insertedRows": 0,
        "outcome": "EMPTY",
        "requestId": "1b0cc111-9d2b-496e-8ce2-3ecb2e589415",
        "sourceRowCount": 0,
        "updatedRows": 0
      },
      {
        "apiName": "stock_basic",
        "durationMs": 186,
        "insertedRows": 339,
        "outcome": "SUCCESS",
        "requestId": "2744b361-461c-4c81-8016-5d4961bac309",
        "sourceRowCount": 339,
        "updatedRows": 0
      },
      {
        "apiName": "stock_company",
        "durationMs": 1455,
        "insertedRows": 2457,
        "outcome": "SUCCESS",
        "requestId": "bbcd7ebb-7e3a-4e32-84ab-523ebdfd9eb6",
        "sourceRowCount": 2457,
        "updatedRows": 0
      },
      {
        "apiName": "stock_company",
        "durationMs": 1775,
        "insertedRows": 3083,
        "outcome": "SUCCESS",
        "requestId": "4362abca-f30a-48fc-b04d-a83346fb5bab",
        "sourceRowCount": 3083,
        "updatedRows": 0
      },
      {
        "apiName": "stock_company",
        "durationMs": 519,
        "insertedRows": 754,
        "outcome": "SUCCESS",
        "requestId": "d6c25258-cc97-46d7-9999-ec727f6c0345",
        "sourceRowCount": 754,
        "updatedRows": 0
      },
      {
        "apiName": "income",
        "durationMs": 34,
        "insertedRows": 0,
        "outcome": "EMPTY",
        "requestId": "0c8623b6-875c-49a2-b4a8-a20666bc146e",
        "sourceRowCount": 0,
        "updatedRows": 0
      },
      {
        "apiName": "balancesheet",
        "durationMs": 25,
        "insertedRows": 0,
        "outcome": "EMPTY",
        "requestId": "1176d7ba-273e-4b7a-b3a9-e40e29be3736",
        "sourceRowCount": 0,
        "updatedRows": 0
      },
      {
        "apiName": "cashflow",
        "durationMs": 27,
        "insertedRows": 0,
        "outcome": "EMPTY",
        "requestId": "9cc8423b-8f94-4f09-8921-70f10b329017",
        "sourceRowCount": 0,
        "updatedRows": 0
      },
      {
        "apiName": "fina_indicator",
        "durationMs": 85,
        "insertedRows": 0,
        "outcome": "EMPTY",
        "requestId": "e3226ea4-f7bc-4b5a-9dfa-f272575d9258",
        "sourceRowCount": 0,
        "updatedRows": 0
      },
      {
        "apiName": "fina_audit",
        "durationMs": 29,
        "insertedRows": 0,
        "outcome": "EMPTY",
        "requestId": "aa706bf1-fdd5-4f03-a487-a59591340159",
        "sourceRowCount": 0,
        "updatedRows": 0
      },
      {
        "apiName": "fina_mainbz",
        "durationMs": 99,
        "insertedRows": 150,
        "outcome": "SUCCESS",
        "requestId": "766b4076-e60d-47fa-a8ac-38c0633f275c",
        "sourceRowCount": 150,
        "updatedRows": 0
      },
      {
        "apiName": "stk_rewards",
        "durationMs": 652,
        "insertedRows": 1428,
        "outcome": "SUCCESS",
        "requestId": "55e7cade-383a-4b0b-88c5-48a27ee0973c",
        "sourceRowCount": 1428,
        "updatedRows": 0
      },
      {
        "apiName": "stk_holdernumber",
        "durationMs": 100,
        "insertedRows": 150,
        "outcome": "SUCCESS",
        "requestId": "6a7aba69-0cf0-4c49-8be2-8fb74b19bf03",
        "sourceRowCount": 150,
        "updatedRows": 0
      },
      {
        "apiName": "trade_cal",
        "durationMs": 52,
        "insertedRows": 1,
        "outcome": "SUCCESS",
        "requestId": "298eabbe-8ec7-4c6c-9150-aba1a772fce3",
        "sourceRowCount": 1,
        "updatedRows": 0
      },
      {
        "apiName": "trade_cal",
        "durationMs": 29,
        "insertedRows": 1,
        "outcome": "SUCCESS",
        "requestId": "67f6fe2c-28d4-48b7-920e-c8b7868c9f51",
        "sourceRowCount": 1,
        "updatedRows": 0
      },
      {
        "apiName": "trade_cal",
        "durationMs": 26,
        "insertedRows": 0,
        "outcome": "EMPTY",
        "requestId": "5b1097bf-4201-4d57-909d-df0dc16cc551",
        "sourceRowCount": 0,
        "updatedRows": 0
      },
      {
        "apiName": "margin",
        "durationMs": 33,
        "insertedRows": 1,
        "outcome": "SUCCESS",
        "requestId": "6791fa0e-5313-44d6-80cd-48c3bcd6d218",
        "sourceRowCount": 1,
        "updatedRows": 0
      },
      {
        "apiName": "margin",
        "durationMs": 35,
        "insertedRows": 1,
        "outcome": "SUCCESS",
        "requestId": "291faf07-1645-4893-89ef-0bebfafb6b09",
        "sourceRowCount": 1,
        "updatedRows": 0
      },
      {
        "apiName": "margin",
        "durationMs": 29,
        "insertedRows": 1,
        "outcome": "SUCCESS",
        "requestId": "c7afc74b-e8e3-4b17-b86c-10aa90d09b72",
        "sourceRowCount": 1,
        "updatedRows": 0
      },
      {
        "apiName": "daily",
        "durationMs": 2257,
        "insertedRows": 5535,
        "outcome": "SUCCESS",
        "requestId": "eb13572e-3aa5-474f-ad09-ec9c9f2d9845",
        "sourceRowCount": 5535,
        "updatedRows": 0
      },
      {
        "apiName": "weekly",
        "durationMs": 2271,
        "insertedRows": 5613,
        "outcome": "SUCCESS",
        "requestId": "2d1bf1fb-7069-43bc-a65d-04c76597293d",
        "sourceRowCount": 5613,
        "updatedRows": 0
      },
      {
        "apiName": "monthly",
        "durationMs": 27,
        "insertedRows": 0,
        "outcome": "EMPTY",
        "requestId": "da80dd31-a26c-48da-9d21-4899e5fdc456",
        "sourceRowCount": 0,
        "updatedRows": 0
      },
      {
        "apiName": "adj_factor",
        "durationMs": 2017,
        "insertedRows": 5553,
        "outcome": "SUCCESS",
        "requestId": "1ffe5002-bcdb-4a39-9b08-2e5e05f903d2",
        "sourceRowCount": 5553,
        "updatedRows": 0
      },
      {
        "apiName": "suspend_d",
        "durationMs": 39,
        "insertedRows": 5,
        "outcome": "SUCCESS",
        "requestId": "ee4bc30c-c973-4102-b00a-559154001523",
        "sourceRowCount": 5,
        "updatedRows": 0
      },
      {
        "apiName": "daily_basic",
        "durationMs": 2405,
        "insertedRows": 5535,
        "outcome": "SUCCESS",
        "requestId": "85e6c28a-ec15-4968-8170-dc9e83a175a9",
        "sourceRowCount": 5535,
        "updatedRows": 0
      },
      {
        "apiName": "moneyflow",
        "durationMs": 2557,
        "insertedRows": 5535,
        "outcome": "SUCCESS",
        "requestId": "90bb0f52-3afb-4830-917c-0c7216c0d590",
        "sourceRowCount": 5535,
        "updatedRows": 0
      },
      {
        "apiName": "stk_limit",
        "durationMs": 2812,
        "insertedRows": 7733,
        "outcome": "SUCCESS",
        "requestId": "03adc4dc-7a52-4f9e-8cf1-c13a77b76a1d",
        "sourceRowCount": 7733,
        "updatedRows": 0
      },
      {
        "apiName": "top_list",
        "durationMs": 70,
        "insertedRows": 67,
        "outcome": "SUCCESS",
        "requestId": "76c51521-fed7-4554-98c0-f5a2904ea42b",
        "sourceRowCount": 67,
        "updatedRows": 0
      },
      {
        "apiName": "margin_detail",
        "durationMs": 1766,
        "insertedRows": 4424,
        "outcome": "SUCCESS",
        "requestId": "bdae4415-d7b6-4e41-aa26-dffadd689c7f",
        "sourceRowCount": 4424,
        "updatedRows": 0
      },
      {
        "apiName": "block_trade",
        "durationMs": 136,
        "insertedRows": 139,
        "outcome": "SUCCESS",
        "requestId": "45b461fe-e5d4-4dc4-86eb-adb32156b32e",
        "sourceRowCount": 159,
        "updatedRows": 0
      },
      {
        "apiName": "slb_len",
        "durationMs": 31,
        "insertedRows": 0,
        "outcome": "EMPTY",
        "requestId": "0229cf06-6484-4496-9619-0cafe60aa69d",
        "sourceRowCount": 0,
        "updatedRows": 0
      },
      {
        "apiName": "slb_sec",
        "durationMs": 39,
        "insertedRows": 0,
        "outcome": "EMPTY",
        "requestId": "0709b1f2-cafc-4898-8d3e-ce1295d966b1",
        "sourceRowCount": 0,
        "updatedRows": 0
      },
      {
        "apiName": "slb_sec_detail",
        "durationMs": 37,
        "insertedRows": 0,
        "outcome": "EMPTY",
        "requestId": "1f448ab5-5aa5-4c25-80ee-c6933929a5ad",
        "sourceRowCount": 0,
        "updatedRows": 0
      },
      {
        "apiName": "forecast",
        "durationMs": 39,
        "insertedRows": 3,
        "outcome": "SUCCESS",
        "requestId": "f57c1a28-45d8-4127-9702-42438a77f3d9",
        "sourceRowCount": 3,
        "updatedRows": 0
      },
      {
        "apiName": "express",
        "durationMs": 33,
        "insertedRows": 1,
        "outcome": "SUCCESS",
        "requestId": "f44296f1-8326-4225-ba70-522bbad2711f",
        "sourceRowCount": 1,
        "updatedRows": 0
      },
      {
        "apiName": "dividend",
        "durationMs": 454,
        "insertedRows": 38,
        "outcome": "SUCCESS",
        "requestId": "d3f0e15e-58b7-4971-850d-2c09c8790083",
        "sourceRowCount": 38,
        "updatedRows": 0
      },
      {
        "apiName": "disclosure_date",
        "durationMs": 104,
        "insertedRows": 10,
        "outcome": "SUCCESS",
        "requestId": "64ac925c-879b-4dfc-989a-db282e5dabb6",
        "sourceRowCount": 10,
        "updatedRows": 0
      },
      {
        "apiName": "repurchase",
        "durationMs": 48,
        "insertedRows": 27,
        "outcome": "SUCCESS",
        "requestId": "39b74a9a-d0ff-48d3-acfe-3a59b77a2ad9",
        "sourceRowCount": 27,
        "updatedRows": 0
      },
      {
        "apiName": "stk_holdertrade",
        "durationMs": 66,
        "insertedRows": 44,
        "outcome": "SUCCESS",
        "requestId": "6ea99f78-dacb-4425-b5df-7eaf8c53a806",
        "sourceRowCount": 157,
        "updatedRows": 0
      },
      {
        "apiName": "top10_holders",
        "durationMs": 169,
        "insertedRows": 320,
        "outcome": "SUCCESS",
        "requestId": "e2f5c113-7ef5-4abb-b924-d90e999e2a5d",
        "sourceRowCount": 320,
        "updatedRows": 0
      },
      {
        "apiName": "top10_floatholders",
        "durationMs": 160,
        "insertedRows": 280,
        "outcome": "SUCCESS",
        "requestId": "ecc994ea-88ed-4533-9d3c-36e8d488cc3b",
        "sourceRowCount": 280,
        "updatedRows": 0
      },
      {
        "apiName": "new_share",
        "durationMs": 34,
        "insertedRows": 1,
        "outcome": "SUCCESS",
        "requestId": "eb06c08d-02a1-4f76-8194-11b9c7b486d3",
        "sourceRowCount": 1,
        "updatedRows": 0
      },
      {
        "apiName": "stk_managers",
        "durationMs": 1689,
        "insertedRows": 3999,
        "outcome": "SUCCESS",
        "requestId": "db40db70-ade9-4cd4-ad93-2c35fd742656",
        "sourceRowCount": 4000,
        "updatedRows": 0
      },
      {
        "apiName": "pledge_stat",
        "durationMs": 1196,
        "insertedRows": 3000,
        "outcome": "SUCCESS",
        "requestId": "552e3135-36a6-48de-9a09-ae9bfe27921c",
        "sourceRowCount": 3000,
        "updatedRows": 0
      },
      {
        "apiName": "pledge_detail",
        "durationMs": 746,
        "insertedRows": 1493,
        "outcome": "SUCCESS",
        "requestId": "6506dc50-fa7f-44c0-a283-4e42e62350ae",
        "sourceRowCount": 1500,
        "updatedRows": 0
      },
      {
        "apiName": "index_classify",
        "durationMs": 165,
        "insertedRows": 359,
        "outcome": "SUCCESS",
        "requestId": "0287e97a-174c-4bde-9784-64735a76a958",
        "sourceRowCount": 359,
        "updatedRows": 0
      },
      {
        "apiName": "index_member_all",
        "durationMs": 1241,
        "insertedRows": 3000,
        "outcome": "SUCCESS",
        "requestId": "ac6548a4-76b8-4da5-942a-0cc71a4e2b85",
        "sourceRowCount": 3000,
        "updatedRows": 0
      }
    ],
    "finishedAt": "2026-09-06T12:33:07.370Z",
    "fixture": [
      {
        "apiName": "fixture_daily",
        "durationMs": 4,
        "outcome": "SUCCESS",
        "requestId": "22a10536-563a-4c51-97b3-b046bef67afa",
        "resultCount": 0,
        "totalElements": 0
      },
      {
        "apiName": "fixture_daily",
        "durationMs": 21,
        "insertedRows": 1,
        "outcome": "SUCCESS",
        "requestId": "8b6320c0-0743-41a7-a9d1-151e9a578baf",
        "sourceRowCount": 1,
        "updatedRows": 0
      },
      {
        "apiName": "fixture_daily",
        "durationMs": 5,
        "outcome": "SUCCESS",
        "requestId": "64239bee-f76b-46db-b522-07382be12c89",
        "resultCount": 1,
        "totalElements": 1
      },
      {
        "apiName": "fixture_daily",
        "durationMs": 0,
        "insertedRows": 0,
        "outcome": "EMPTY",
        "requestId": "73bcf49f-08a8-46f1-b901-d8a73ad1b2a6",
        "sourceRowCount": 0,
        "updatedRows": 0
      },
      {
        "apiName": "fixture_daily",
        "durationMs": 4,
        "outcome": "SUCCESS",
        "requestId": "e22b694d-57b1-4f16-b20f-bf1be497bb02",
        "resultCount": 1,
        "totalElements": 1
      }
    ],
    "inputs": {
      "gitCommit": "07ec79724e63a08a1867e06d2fe75485719e0f7a",
      "jarSha256": "81adba0dd6500f4aa43b4fa06b18c2c8e7b7454d9e6d4d6c734772cdaef1d002",
      "manifestSha256": "37a317f6a2bc3e5113be5f127976d16d8349414c6476c7f6a194b084a5b0f7c2",
      "specSha256": "05a6601f656dcfb5528c6ed832ccb17ef2416cfa84397fb93f11da1bfbd5901e"
    },
    "queries": [
      {
        "apiName": "stock_basic",
        "durationMs": 2,
        "outcome": "SUCCESS",
        "requestId": "79430bbb-8876-433f-b7df-457495ba150b",
        "resultCount": 0,
        "totalElements": 0
      },
      {
        "apiName": "stock_basic",
        "durationMs": 6,
        "outcome": "SUCCESS",
        "requestId": "2ed1f630-62f3-4573-9ec8-91126c590096",
        "resultCount": 50,
        "totalElements": 5895
      },
      {
        "apiName": "stock_company",
        "durationMs": 2,
        "outcome": "SUCCESS",
        "requestId": "bf965680-1849-4215-bdb1-e79d7835b031",
        "resultCount": 0,
        "totalElements": 0
      },
      {
        "apiName": "stock_company",
        "durationMs": 9,
        "outcome": "SUCCESS",
        "requestId": "d7de7254-1d0a-4253-a766-ae979dec6f6d",
        "resultCount": 50,
        "totalElements": 6294
      },
      {
        "apiName": "income",
        "durationMs": 2,
        "outcome": "SUCCESS",
        "requestId": "d7ac23d4-8c4c-4013-8a91-7360d2c0b1ab",
        "resultCount": 0,
        "totalElements": 0
      },
      {
        "apiName": "income",
        "durationMs": 2,
        "outcome": "SUCCESS",
        "requestId": "b1b5dc89-cbfa-4c68-94bc-522c71bcbf8e",
        "resultCount": 0,
        "totalElements": 0
      },
      {
        "apiName": "balancesheet",
        "durationMs": 2,
        "outcome": "SUCCESS",
        "requestId": "81d963e7-5139-463d-ab93-2130d78ce876",
        "resultCount": 0,
        "totalElements": 0
      },
      {
        "apiName": "balancesheet",
        "durationMs": 2,
        "outcome": "SUCCESS",
        "requestId": "b1ba484a-b4ea-4ae4-abdd-29f65ed462fc",
        "resultCount": 0,
        "totalElements": 0
      },
      {
        "apiName": "cashflow",
        "durationMs": 2,
        "outcome": "SUCCESS",
        "requestId": "03eb0e39-88b0-48fd-a826-1bbc2d8d1d41",
        "resultCount": 0,
        "totalElements": 0
      },
      {
        "apiName": "cashflow",
        "durationMs": 2,
        "outcome": "SUCCESS",
        "requestId": "6ec2317a-87c3-4f4b-b543-a1bff6a6f654",
        "resultCount": 0,
        "totalElements": 0
      },
      {
        "apiName": "fina_indicator",
        "durationMs": 2,
        "outcome": "SUCCESS",
        "requestId": "bc4ccf4e-ef68-433d-86b9-eab2f8b8923a",
        "resultCount": 0,
        "totalElements": 0
      },
      {
        "apiName": "fina_indicator",
        "durationMs": 2,
        "outcome": "SUCCESS",
        "requestId": "331b7795-4763-4dc1-b19d-a2217b095d98",
        "resultCount": 0,
        "totalElements": 0
      },
      {
        "apiName": "fina_audit",
        "durationMs": 2,
        "outcome": "SUCCESS",
        "requestId": "352e094a-f8c5-48e3-ac2f-1a2bd9a21dd5",
        "resultCount": 0,
        "totalElements": 0
      },
      {
        "apiName": "fina_audit",
        "durationMs": 2,
        "outcome": "SUCCESS",
        "requestId": "9f7d86a1-a1bf-426d-be5b-d2c12a050e2e",
        "resultCount": 0,
        "totalElements": 0
      },
      {
        "apiName": "fina_mainbz",
        "durationMs": 2,
        "outcome": "SUCCESS",
        "requestId": "bed91844-1efc-4e63-8ff9-50729ad9c6ed",
        "resultCount": 0,
        "totalElements": 0
      },
      {
        "apiName": "fina_mainbz",
        "durationMs": 5,
        "outcome": "SUCCESS",
        "requestId": "f4816600-7d70-4b4f-aa43-4b2d7de9c137",
        "resultCount": 50,
        "totalElements": 150
      },
      {
        "apiName": "stk_rewards",
        "durationMs": 2,
        "outcome": "SUCCESS",
        "requestId": "1b3ce767-70fd-492e-9245-72f3eeaaa084",
        "resultCount": 0,
        "totalElements": 0
      },
      {
        "apiName": "stk_rewards",
        "durationMs": 4,
        "outcome": "SUCCESS",
        "requestId": "d41069e6-f357-453b-b499-a52909eeb492",
        "resultCount": 50,
        "totalElements": 1428
      },
      {
        "apiName": "stk_holdernumber",
        "durationMs": 2,
        "outcome": "SUCCESS",
        "requestId": "25849dbc-45c7-4cde-bfef-bf85f94316c7",
        "resultCount": 0,
        "totalElements": 0
      },
      {
        "apiName": "stk_holdernumber",
        "durationMs": 3,
        "outcome": "SUCCESS",
        "requestId": "069404f4-f601-4bb2-a599-588747db4e37",
        "resultCount": 50,
        "totalElements": 150
      },
      {
        "apiName": "trade_cal",
        "durationMs": 3,
        "outcome": "SUCCESS",
        "requestId": "06dff602-5e82-45a6-9004-b98e9a1b3615",
        "resultCount": 0,
        "totalElements": 0
      },
      {
        "apiName": "trade_cal",
        "durationMs": 6,
        "outcome": "SUCCESS",
        "requestId": "0a447f43-d5f7-4402-b39b-bbab20c91e94",
        "resultCount": 2,
        "totalElements": 2
      },
      {
        "apiName": "margin",
        "durationMs": 2,
        "outcome": "SUCCESS",
        "requestId": "c1d42e36-aebd-46e1-82c0-ae9dd3287984",
        "resultCount": 0,
        "totalElements": 0
      },
      {
        "apiName": "margin",
        "durationMs": 3,
        "outcome": "SUCCESS",
        "requestId": "5993e9dd-ca75-44ed-af05-89d847a4057e",
        "resultCount": 3,
        "totalElements": 3
      },
      {
        "apiName": "daily",
        "durationMs": 2,
        "outcome": "SUCCESS",
        "requestId": "d9aa9939-2f78-4757-b3f2-a9e0663d6ebb",
        "resultCount": 0,
        "totalElements": 0
      },
      {
        "apiName": "daily",
        "durationMs": 5,
        "outcome": "SUCCESS",
        "requestId": "6c6c54d4-8c6e-40d2-a9e5-c14adedc06d2",
        "resultCount": 50,
        "totalElements": 5535
      },
      {
        "apiName": "weekly",
        "durationMs": 2,
        "outcome": "SUCCESS",
        "requestId": "11ed66d3-a68f-4508-a663-6c66353ef293",
        "resultCount": 0,
        "totalElements": 0
      },
      {
        "apiName": "weekly",
        "durationMs": 4,
        "outcome": "SUCCESS",
        "requestId": "299b61d2-ad20-4836-bb2b-1f06d0a7e7a5",
        "resultCount": 50,
        "totalElements": 5613
      },
      {
        "apiName": "monthly",
        "durationMs": 2,
        "outcome": "SUCCESS",
        "requestId": "d1bdcf17-951b-432e-bd85-aaaf6992acfb",
        "resultCount": 0,
        "totalElements": 0
      },
      {
        "apiName": "monthly",
        "durationMs": 2,
        "outcome": "SUCCESS",
        "requestId": "8f9aa5c2-8343-4d20-b0e4-5707ee5fcca6",
        "resultCount": 0,
        "totalElements": 0
      },
      {
        "apiName": "adj_factor",
        "durationMs": 2,
        "outcome": "SUCCESS",
        "requestId": "d4247224-679c-4fbd-beb9-500dab483f24",
        "resultCount": 0,
        "totalElements": 0
      },
      {
        "apiName": "adj_factor",
        "durationMs": 3,
        "outcome": "SUCCESS",
        "requestId": "e932602e-10f7-496c-b417-d964e74a29f7",
        "resultCount": 50,
        "totalElements": 5553
      },
      {
        "apiName": "suspend_d",
        "durationMs": 2,
        "outcome": "SUCCESS",
        "requestId": "3c30a6a8-10f4-4c65-913f-af4c54af4942",
        "resultCount": 0,
        "totalElements": 0
      },
      {
        "apiName": "suspend_d",
        "durationMs": 3,
        "outcome": "SUCCESS",
        "requestId": "9a7e8525-9a7e-4d7f-9539-aad93f8962d0",
        "resultCount": 5,
        "totalElements": 5
      },
      {
        "apiName": "daily_basic",
        "durationMs": 1,
        "outcome": "SUCCESS",
        "requestId": "a48b8ac3-27a2-4473-a3e9-b7a4559e2a72",
        "resultCount": 0,
        "totalElements": 0
      },
      {
        "apiName": "daily_basic",
        "durationMs": 4,
        "outcome": "SUCCESS",
        "requestId": "4891eb0f-ded2-4101-b201-a34d4a99bd01",
        "resultCount": 50,
        "totalElements": 5535
      },
      {
        "apiName": "moneyflow",
        "durationMs": 2,
        "outcome": "SUCCESS",
        "requestId": "fa0afae6-1e45-471e-ab85-06cfde11f02f",
        "resultCount": 0,
        "totalElements": 0
      },
      {
        "apiName": "moneyflow",
        "durationMs": 4,
        "outcome": "SUCCESS",
        "requestId": "c4ad5daf-0689-4d10-934d-77db765201c5",
        "resultCount": 50,
        "totalElements": 5535
      },
      {
        "apiName": "stk_limit",
        "durationMs": 2,
        "outcome": "SUCCESS",
        "requestId": "02b6d35a-3131-48ea-872b-0400c0bfb85d",
        "resultCount": 0,
        "totalElements": 0
      },
      {
        "apiName": "stk_limit",
        "durationMs": 3,
        "outcome": "SUCCESS",
        "requestId": "fa97a3bb-e50c-4db4-838f-3ad4caa299b7",
        "resultCount": 50,
        "totalElements": 7733
      },
      {
        "apiName": "top_list",
        "durationMs": 2,
        "outcome": "SUCCESS",
        "requestId": "5d2807d0-1191-4716-b957-114f139a0f82",
        "resultCount": 0,
        "totalElements": 0
      },
      {
        "apiName": "top_list",
        "durationMs": 3,
        "outcome": "SUCCESS",
        "requestId": "479aa25d-2d39-4aac-94a9-f7e18b75c8a4",
        "resultCount": 50,
        "totalElements": 67
      },
      {
        "apiName": "margin_detail",
        "durationMs": 1,
        "outcome": "SUCCESS",
        "requestId": "d06b1603-783f-4884-8b7f-9625cac0c6d2",
        "resultCount": 0,
        "totalElements": 0
      },
      {
        "apiName": "margin_detail",
        "durationMs": 4,
        "outcome": "SUCCESS",
        "requestId": "772df917-2ad2-4330-a659-81cc9e1723c7",
        "resultCount": 50,
        "totalElements": 4424
      },
      {
        "apiName": "block_trade",
        "durationMs": 2,
        "outcome": "SUCCESS",
        "requestId": "edcadd33-bb42-4bbd-a454-31028e2f9c32",
        "resultCount": 0,
        "totalElements": 0
      },
      {
        "apiName": "block_trade",
        "durationMs": 3,
        "outcome": "SUCCESS",
        "requestId": "cfa01989-b5fe-4abf-8df1-5562ecf24d60",
        "resultCount": 50,
        "totalElements": 139
      },
      {
        "apiName": "slb_len",
        "durationMs": 2,
        "outcome": "SUCCESS",
        "requestId": "60655321-dc45-466a-b93a-dba5ae02f31f",
        "resultCount": 0,
        "totalElements": 0
      },
      {
        "apiName": "slb_len",
        "durationMs": 2,
        "outcome": "SUCCESS",
        "requestId": "a2f516f8-176b-40eb-9940-1baf2343c25b",
        "resultCount": 0,
        "totalElements": 0
      },
      {
        "apiName": "slb_sec",
        "durationMs": 1,
        "outcome": "SUCCESS",
        "requestId": "e4aad3a1-c7ae-4bf9-8b17-c83d87835831",
        "resultCount": 0,
        "totalElements": 0
      },
      {
        "apiName": "slb_sec",
        "durationMs": 1,
        "outcome": "SUCCESS",
        "requestId": "b5a0ca37-9edd-43cc-bc20-4ff8835f091e",
        "resultCount": 0,
        "totalElements": 0
      },
      {
        "apiName": "slb_sec_detail",
        "durationMs": 2,
        "outcome": "SUCCESS",
        "requestId": "8f175023-ab55-4c97-be41-07aadcb96517",
        "resultCount": 0,
        "totalElements": 0
      },
      {
        "apiName": "slb_sec_detail",
        "durationMs": 2,
        "outcome": "SUCCESS",
        "requestId": "629a3363-6d9e-4dfb-9b9b-39e3dd8adf7e",
        "resultCount": 0,
        "totalElements": 0
      },
      {
        "apiName": "forecast",
        "durationMs": 2,
        "outcome": "SUCCESS",
        "requestId": "a9a58217-d339-4752-b79f-f033a213fc92",
        "resultCount": 0,
        "totalElements": 0
      },
      {
        "apiName": "forecast",
        "durationMs": 3,
        "outcome": "SUCCESS",
        "requestId": "abb09e5e-2e0c-4f80-8087-684aea6ea62b",
        "resultCount": 3,
        "totalElements": 3
      },
      {
        "apiName": "express",
        "durationMs": 2,
        "outcome": "SUCCESS",
        "requestId": "848d8a01-c810-4fed-8323-d45296d4d9cb",
        "resultCount": 0,
        "totalElements": 0
      },
      {
        "apiName": "express",
        "durationMs": 3,
        "outcome": "SUCCESS",
        "requestId": "341e15fa-093d-4ef1-aab9-cb73cd1c6f1c",
        "resultCount": 1,
        "totalElements": 1
      },
      {
        "apiName": "dividend",
        "durationMs": 2,
        "outcome": "SUCCESS",
        "requestId": "dbc01be6-715f-4302-a02a-4a5b4b9541dd",
        "resultCount": 0,
        "totalElements": 0
      },
      {
        "apiName": "dividend",
        "durationMs": 3,
        "outcome": "SUCCESS",
        "requestId": "fc5eb2d9-17db-424f-a8fe-40c9b300d249",
        "resultCount": 38,
        "totalElements": 38
      },
      {
        "apiName": "disclosure_date",
        "durationMs": 2,
        "outcome": "SUCCESS",
        "requestId": "77dfc715-d420-4b1f-b105-0f2b42538365",
        "resultCount": 0,
        "totalElements": 0
      },
      {
        "apiName": "disclosure_date",
        "durationMs": 2,
        "outcome": "SUCCESS",
        "requestId": "89b5a057-3abe-48b8-a2ac-870996440749",
        "resultCount": 10,
        "totalElements": 10
      },
      {
        "apiName": "repurchase",
        "durationMs": 2,
        "outcome": "SUCCESS",
        "requestId": "2951fedd-3f96-4753-b781-0f880156aa71",
        "resultCount": 0,
        "totalElements": 0
      },
      {
        "apiName": "repurchase",
        "durationMs": 3,
        "outcome": "SUCCESS",
        "requestId": "629783d0-24af-49c0-9672-2c0b1f54044f",
        "resultCount": 27,
        "totalElements": 27
      },
      {
        "apiName": "stk_holdertrade",
        "durationMs": 2,
        "outcome": "SUCCESS",
        "requestId": "92c51bfe-2f9c-4ff2-824c-f2718c88f8be",
        "resultCount": 0,
        "totalElements": 0
      },
      {
        "apiName": "stk_holdertrade",
        "durationMs": 3,
        "outcome": "SUCCESS",
        "requestId": "38b3a06e-0572-4259-9625-e1386461a1db",
        "resultCount": 44,
        "totalElements": 44
      },
      {
        "apiName": "top10_holders",
        "durationMs": 2,
        "outcome": "SUCCESS",
        "requestId": "dbd6396f-584b-49a7-b732-88ba1b963c76",
        "resultCount": 0,
        "totalElements": 0
      },
      {
        "apiName": "top10_holders",
        "durationMs": 3,
        "outcome": "SUCCESS",
        "requestId": "90037920-42b3-4be8-acc4-3697e523b831",
        "resultCount": 50,
        "totalElements": 320
      },
      {
        "apiName": "top10_floatholders",
        "durationMs": 2,
        "outcome": "SUCCESS",
        "requestId": "6c25164b-9bf4-487d-bda9-9c7f0edd9c64",
        "resultCount": 0,
        "totalElements": 0
      },
      {
        "apiName": "top10_floatholders",
        "durationMs": 3,
        "outcome": "SUCCESS",
        "requestId": "b2711b46-1031-4eb9-bdd1-ae61ddb8a1db",
        "resultCount": 50,
        "totalElements": 280
      },
      {
        "apiName": "new_share",
        "durationMs": 1,
        "outcome": "SUCCESS",
        "requestId": "1837fd78-2b0b-405c-a188-3e841e07a16e",
        "resultCount": 0,
        "totalElements": 0
      },
      {
        "apiName": "new_share",
        "durationMs": 3,
        "outcome": "SUCCESS",
        "requestId": "f00c3898-8f8e-45e3-961a-af9e603ec590",
        "resultCount": 1,
        "totalElements": 1
      },
      {
        "apiName": "stk_managers",
        "durationMs": 2,
        "outcome": "SUCCESS",
        "requestId": "456cfecc-b704-46d4-9c2f-4e0a222397d4",
        "resultCount": 0,
        "totalElements": 0
      },
      {
        "apiName": "stk_managers",
        "durationMs": 9,
        "outcome": "SUCCESS",
        "requestId": "5d5e7647-c945-446a-b280-db0092f2dda4",
        "resultCount": 50,
        "totalElements": 3999
      },
      {
        "apiName": "pledge_stat",
        "durationMs": 2,
        "outcome": "SUCCESS",
        "requestId": "e795950a-e6c9-4661-874a-6bde9aa512c3",
        "resultCount": 0,
        "totalElements": 0
      },
      {
        "apiName": "pledge_stat",
        "durationMs": 4,
        "outcome": "SUCCESS",
        "requestId": "ec5d0861-6107-41de-b789-556c0abc66f3",
        "resultCount": 50,
        "totalElements": 3000
      },
      {
        "apiName": "pledge_detail",
        "durationMs": 2,
        "outcome": "SUCCESS",
        "requestId": "4291b4c2-76a9-40b0-b3e0-28b011fd64c5",
        "resultCount": 0,
        "totalElements": 0
      },
      {
        "apiName": "pledge_detail",
        "durationMs": 7,
        "outcome": "SUCCESS",
        "requestId": "125aeb56-581b-4d30-ba1f-cce8778d31a3",
        "resultCount": 50,
        "totalElements": 1493
      },
      {
        "apiName": "index_classify",
        "durationMs": 3,
        "outcome": "SUCCESS",
        "requestId": "f7577497-8f64-48c7-90ce-e17ff5bd2742",
        "resultCount": 0,
        "totalElements": 0
      },
      {
        "apiName": "index_classify",
        "durationMs": 4,
        "outcome": "SUCCESS",
        "requestId": "b1aebec5-7096-449a-8e8f-559fb7bc1929",
        "resultCount": 50,
        "totalElements": 359
      },
      {
        "apiName": "index_member_all",
        "durationMs": 1,
        "outcome": "SUCCESS",
        "requestId": "4d1b6a1c-b209-4299-be12-d10626aa4243",
        "resultCount": 0,
        "totalElements": 0
      },
      {
        "apiName": "index_member_all",
        "durationMs": 3,
        "outcome": "SUCCESS",
        "requestId": "a7675d64-1996-4bcf-974f-138676a1895f",
        "resultCount": 50,
        "totalElements": 3000
      }
    ],
    "scope": {
      "acceptanceStatuses": {
        "empty": 9,
        "ok": 31
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
          "acceptanceStatus": "ok",
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
    "startedAt": "2026-09-06T12:30:17.396Z",
    "task": "M14-T09",
    "totals": {
      "attemptedCases": 40,
      "callIntervalMs": 2000,
      "completedCases": 40,
      "failedCases": 0,
      "fixtureDownloadPostsObserved": 2,
      "fixtureRecordsGetsObserved": 3,
      "liveDownloadPostsObserved": 48,
      "liveDownloadResultsRecorded": 48,
      "liveQueryResultsRecorded": 80,
      "liveRecordsGetsObserved": 80,
      "manifestSamples": 58,
      "registeredCases": 40,
      "selectedSamples": 48,
      "unexecutedCases": 0
    },
    "version": 2
  },
  "task": "M14-T09"
}
```
