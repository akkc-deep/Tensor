# M14-T09 分红修复与2000档页面验收证据

本任务依据权威看板 Order76、`docs/task-designs/M14-T09-design.md` 和已确认的 ISSUE-007 四字段指纹方案执行。用户2026-09-06回复“同意”，批准保留各实施阶段、同阶段跨批更新、V7保留现有数据及完整40项复验。批准记录c72a823，启动记录fbd594a。

## 输入与范围

历史M14-T05证据只读，全文SHA `d4e7bf67b6a2b144662a987dec9aa812a8e39543c5134ed7cab8a12a4dbe34b5`；其28通过/1失败/11未运行不拼入本轮。基线ISSUE-006包SHA `f2fc35c933e69da5e85690fbabb13d691178538cd6ffb3b94284dfc95b10db89`，原manifest SHA `37a317f6a2bc3e5113be5f127976d16d8349414c6476c7f6a194b084a5b0f7c2`；两者均在本轮准备时核对一致。

原40接口/48参数样例/80页面查询，fixture另2POST/3查询不变。仅dividend当前验收预期为ok，原manifestStatus=empty保持；历史分类28ok/12empty，当前29ok/11empty；每接口manifestStatus/acceptanceStatus与本轮实际outcome分别记录。38是旧诊断来源计数，不是本轮固定行数。

9项仍不覆盖：top_inst、broker_recommend为higher_points；share_float、hs_const、moneyflow_hsgt、hk_hold、index_member、hsgt_top10、namechange为permission_unverified。不计测试skip或通过；原49目标及M14-T05 BLOCKED事实保留，不自动准备M14-T06。

## 已执行的离线验收接入检查

所有本地验证子进程采用环境白名单，不继承真实Token。

- 新dividend预期同函数合成反例先RED：原spec的acceptanceStatus缺失导致断言失败、exit1；修改后同探针GREEN、exit0，验证仅dividend覆盖、非空/空结果与页面末态断言、其他empty仍拒绝SUCCESS、manifest及参数不变、40/48/9与两组分类。
- 原40项范围选择探针、原纯函数反例（末态参数名随实际接口改为acceptanceStatus）均exit0。
- `node --check control-plane/e2e/tushare-live.spec.js` exit0；`cd control-plane && npx playwright test e2e/tushare-live.spec.js --list` exit0、恰40个Chromium用例，归属M14-T09。
- 新私有启动器的10项合成回归exit0：成功/原非零码保留、损坏安全JSON、创建失败、秘密命中、自动上下文、worker未退出、终检失败及缺Token；5项新前置拒绝exit0：仅旧任务启动、设计hash错误、启动器hash错误、历史扫描源变化、目录已用。尚未凭这些离线检查宣称真实40通过。

独立接入审查发现3项Important（优化模式移除assert、准备中断不清理、排除列表未验证）和1项Minor（旧任务提示）。合成反例均先RED，改为无条件检查、受保护的中断清理和冻结manifest的精确40/9集合后，新增2项准备反例与3项范围反例GREEN；原10项生命周期和5项前置检查再次通过。定点复审及后端/新包/真实验证结果按实际完成补录。

## 新包内容与合成启动实测

新包位于 `/private/tmp/tensor-m14-t09-green.MZ4kMkN9/data-plane/tensor-app/target/acceptance/tensor-app-1.0-SNAPSHOT-acceptance.jar`，实际SHA `81adba0dd6500f4aa43b4fa06b18c2c8e7b7454d9e6d4d6c734772cdaef1d002`。所有嵌套JAR递归展开旧18017/新18018文件：仅GenericDatasetAdapter.class、dividend.yaml改变，新增V7__version_dividend_business_key.sql，无删除；原静态前端、依赖及其他所有文件逐字节一致。

独立接入定点复审已关闭3Important/1Minor，无剩余发现。随后以合成Token运行私有health探针，控制目录 `/private/tmp/tensor-m14-t09-control.kcznkmbm`：专用MySQL8.4.6空schema，实际来源host、回环绑定、字符集/排序规则与六项最小权限（CREATE/SELECT/INSERT/UPDATE/ALTER/INDEX）验证通过。Node exit0，health就绪；独立SQL测得7次成功迁移、50业务表全部0行；JVM正常退出，终检扫描2文件、无自动产物、scanPassed/cleanupPassed均true，自有容器/匿名卷及私密DB状态已清理。没有业务请求；这不计真实接口通过。

## 后端修复与契约验证

修复提交 `963ea17`。合成RED先分别复现可空指纹被拒绝及旧三字段下不同分红阶段冲突；随后按设计限定三处生产修改。12个后端改动文件与测试快照逐字节一致，已发布V1～V6未修改。

| 检查 | 实际结果 |
|---|---|
| 设计第一条Maven适配/元数据选择器 | 76 tests，0 failures/errors/skipped；core13、元数据50、dividend4、decimal1、ann_date8 |
| 设计第二条Maven显式IT选择器 | MySQL8.4.6，73 tests，0 failures/errors/skipped；迁移5、schema52、fixture5、production1、download10 |
| 设计第三条acceptance verify选择器 | BUILD SUCCESS；生产包4、验收包3个唯一归档合同全部通过；随后仅补测试，生产包内容/hash未变 |
| 独立修复复审 | PASS；追加必填FINGERPRINT空值拒绝与V6回填后实际upsert覆盖，两项缺口已复审关闭，无剩余发现 |
| `sh scripts/verify-49-contracts.sh` | 在提交963ea17的main根执行，隔离HEAD构建exit0；metadata50/schema52/package4、49/49源与包资源、50业务表/1008物理列/50主键，11合成拒绝门禁全部通过 |

升级IT逐列核对现存业务值、来源与入库时间不变，并比较Java/SQL的UTF-8长度前缀、中文、null与文本null、1000-01-01边界日期指纹；最终ALTER被强制制造失败时旧主键仍在。V6旧实施行经V7回填后，实际PersistenceService同阶段更新计数0/1、不同预案插入1/0并共存；完全重复去重、同身份异内容冲突继续失败。

契约脚本首次因本次启动命令的 `TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE` 漏写 `.sock`，Ryuk启动失败（metadata50通过，schema启动错误，exit1）；该环境错误未计通过。改为已通过IT使用的 `/var/run/docker.sock` 后，原脚本在新隔离目录完整重跑成功，无产品或测试断言改动。

成功总门禁安全JSON：`/var/folders/s5/h3vynqy544lc7vwtz0zjy39m0000gn/T/tensor-m14-t04.OGkWAfc6/verification.json`，SHA `01f895555b5c1865bb4995e03bd6fc7a8c0686d07887078990ff4d800f5704c9`，2026-09-06T10:41:53.463277Z～10:42:24.254344Z。该沿用脚本内部task=M14-T04表示合同来源，本轮结果归属M14-T09，不改写旧M14-T04历史报告；新真实执行仍使用前节冻结acceptance包。

## 正式运行约束

专用一次性控制目录 `/private/tmp/tensor-m14-t09-control.nuy4jdhx`；命令 `python3 /private/tmp/tensor-m14-t09-control.nuy4jdhx/launch.py`。新环境已确认MySQL8.4.6、初始0表、回环端口、实际来源host、字符集及精确六项schema权限。启动器固定M14-T09当前IN_PROGRESS、批准设计/脚本/包/manifest/本文件hash；选中40与排除9须与冻结manifest精确吻合。2秒间隔、1worker、零重试；只从当前TENSOR_TUSHARE_TOKEN环境读取真实Token，不经文件/argv，不交本地构建或浏览器环境。

运行前全部本地修复、回归、复审、打包、health与总门禁已通过。下文真实报告只有在CLI及worker退出、终检、独立DB核对和自有环境清理后生成，并使用运行时真实秘密集合扫描本文件全文；以实际结果判定，不能将本节准备工作计为40项通过。

## 2000档实际运行 2026-09-06T10:51:59.020027+00:00

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
      "top10_floatholders": 0,
      "top10_holders": 320,
      "top_inst": 0,
      "top_list": 67,
      "trade_cal": 2,
      "weekly": 5613
    },
    "successfulMigrations": 7
  },
  "elapsedSeconds": 149,
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
        "durationMs": 2699,
        "insertedRows": 5556,
        "outcome": "SUCCESS",
        "requestId": "e73c5070-1962-4eeb-82ee-8e15e078ebb9",
        "sourceRowCount": 5556,
        "updatedRows": 0
      },
      {
        "apiName": "stock_basic",
        "durationMs": 26,
        "insertedRows": 0,
        "outcome": "EMPTY",
        "requestId": "ee887fe2-c28e-4d7f-a372-d6f52240878e",
        "sourceRowCount": 0,
        "updatedRows": 0
      },
      {
        "apiName": "stock_basic",
        "durationMs": 190,
        "insertedRows": 339,
        "outcome": "SUCCESS",
        "requestId": "e59ee0bf-4cd5-49f2-a8b7-0f478dd03e98",
        "sourceRowCount": 339,
        "updatedRows": 0
      },
      {
        "apiName": "stock_company",
        "durationMs": 1481,
        "insertedRows": 2457,
        "outcome": "SUCCESS",
        "requestId": "36e70c74-0dd7-44f0-af29-f7d572986bf6",
        "sourceRowCount": 2457,
        "updatedRows": 0
      },
      {
        "apiName": "stock_company",
        "durationMs": 1804,
        "insertedRows": 3083,
        "outcome": "SUCCESS",
        "requestId": "1360b72d-7457-435a-b416-2b8ad3766531",
        "sourceRowCount": 3083,
        "updatedRows": 0
      },
      {
        "apiName": "stock_company",
        "durationMs": 474,
        "insertedRows": 754,
        "outcome": "SUCCESS",
        "requestId": "b05d713a-e3f9-4d54-b481-ad44ed7c2838",
        "sourceRowCount": 754,
        "updatedRows": 0
      },
      {
        "apiName": "income",
        "durationMs": 29,
        "insertedRows": 0,
        "outcome": "EMPTY",
        "requestId": "347508ce-d2b9-4d4e-ae18-d4bdc4c523ae",
        "sourceRowCount": 0,
        "updatedRows": 0
      },
      {
        "apiName": "balancesheet",
        "durationMs": 25,
        "insertedRows": 0,
        "outcome": "EMPTY",
        "requestId": "5e93b5bf-48fc-4c6c-86e6-4a8a92a967e7",
        "sourceRowCount": 0,
        "updatedRows": 0
      },
      {
        "apiName": "cashflow",
        "durationMs": 32,
        "insertedRows": 0,
        "outcome": "EMPTY",
        "requestId": "0494be30-961e-46ef-bc26-09a05f60be3e",
        "sourceRowCount": 0,
        "updatedRows": 0
      },
      {
        "apiName": "fina_indicator",
        "durationMs": 27,
        "insertedRows": 0,
        "outcome": "EMPTY",
        "requestId": "f3d2ba1f-970d-4c8f-8829-ccd944e9d053",
        "sourceRowCount": 0,
        "updatedRows": 0
      },
      {
        "apiName": "fina_audit",
        "durationMs": 21,
        "insertedRows": 0,
        "outcome": "EMPTY",
        "requestId": "dd10a48d-03ee-41b9-91e4-01612b596a71",
        "sourceRowCount": 0,
        "updatedRows": 0
      },
      {
        "apiName": "fina_mainbz",
        "durationMs": 196,
        "insertedRows": 150,
        "outcome": "SUCCESS",
        "requestId": "5222568e-5987-42b2-a6b4-fe0a45fe0bae",
        "sourceRowCount": 150,
        "updatedRows": 0
      },
      {
        "apiName": "stk_rewards",
        "durationMs": 633,
        "insertedRows": 1428,
        "outcome": "SUCCESS",
        "requestId": "df840ce5-806c-46d7-83e3-3d671482b1fe",
        "sourceRowCount": 1428,
        "updatedRows": 0
      },
      {
        "apiName": "stk_holdernumber",
        "durationMs": 99,
        "insertedRows": 150,
        "outcome": "SUCCESS",
        "requestId": "fe2ca93e-1593-40fb-9cd5-688398003397",
        "sourceRowCount": 150,
        "updatedRows": 0
      },
      {
        "apiName": "trade_cal",
        "durationMs": 559,
        "insertedRows": 1,
        "outcome": "SUCCESS",
        "requestId": "54d01b0b-7245-40e5-afde-3194514d706b",
        "sourceRowCount": 1,
        "updatedRows": 0
      },
      {
        "apiName": "trade_cal",
        "durationMs": 23,
        "insertedRows": 1,
        "outcome": "SUCCESS",
        "requestId": "4bd3c44c-8b0d-4086-b621-9853846e15dc",
        "sourceRowCount": 1,
        "updatedRows": 0
      },
      {
        "apiName": "trade_cal",
        "durationMs": 19,
        "insertedRows": 0,
        "outcome": "EMPTY",
        "requestId": "fd5c6747-5af1-475f-9ff0-2b506b429a32",
        "sourceRowCount": 0,
        "updatedRows": 0
      },
      {
        "apiName": "margin",
        "durationMs": 43,
        "insertedRows": 1,
        "outcome": "SUCCESS",
        "requestId": "4a64adb2-a05c-41ec-b4d6-50940eb4b5c6",
        "sourceRowCount": 1,
        "updatedRows": 0
      },
      {
        "apiName": "margin",
        "durationMs": 70,
        "insertedRows": 1,
        "outcome": "SUCCESS",
        "requestId": "c26ed741-722e-4191-ac4c-ab89c322ebe5",
        "sourceRowCount": 1,
        "updatedRows": 0
      },
      {
        "apiName": "margin",
        "durationMs": 29,
        "insertedRows": 1,
        "outcome": "SUCCESS",
        "requestId": "ad9ef62c-744d-4258-8047-179dea521ba9",
        "sourceRowCount": 1,
        "updatedRows": 0
      },
      {
        "apiName": "daily",
        "durationMs": 2231,
        "insertedRows": 5535,
        "outcome": "SUCCESS",
        "requestId": "52586705-9c23-4df6-87d7-ebf719282bde",
        "sourceRowCount": 5535,
        "updatedRows": 0
      },
      {
        "apiName": "weekly",
        "durationMs": 2255,
        "insertedRows": 5613,
        "outcome": "SUCCESS",
        "requestId": "5f33c222-7661-4530-b6a9-fa646ece8508",
        "sourceRowCount": 5613,
        "updatedRows": 0
      },
      {
        "apiName": "monthly",
        "durationMs": 21,
        "insertedRows": 0,
        "outcome": "EMPTY",
        "requestId": "66c2ffdd-7b66-410f-a32d-c718b6a92883",
        "sourceRowCount": 0,
        "updatedRows": 0
      },
      {
        "apiName": "adj_factor",
        "durationMs": 2023,
        "insertedRows": 5553,
        "outcome": "SUCCESS",
        "requestId": "8e8a4cec-dea6-41d4-9a2b-b6cf9ecfd4a7",
        "sourceRowCount": 5553,
        "updatedRows": 0
      },
      {
        "apiName": "suspend_d",
        "durationMs": 37,
        "insertedRows": 5,
        "outcome": "SUCCESS",
        "requestId": "1c9f357e-3bbc-4d15-95a2-3881a9efcfb6",
        "sourceRowCount": 5,
        "updatedRows": 0
      },
      {
        "apiName": "daily_basic",
        "durationMs": 2423,
        "insertedRows": 5535,
        "outcome": "SUCCESS",
        "requestId": "db3ba4e3-08a7-40a6-bf5c-d3701ea7aa56",
        "sourceRowCount": 5535,
        "updatedRows": 0
      },
      {
        "apiName": "moneyflow",
        "durationMs": 2540,
        "insertedRows": 5535,
        "outcome": "SUCCESS",
        "requestId": "be144016-ea2d-4504-a9af-dce128897c88",
        "sourceRowCount": 5535,
        "updatedRows": 0
      },
      {
        "apiName": "stk_limit",
        "durationMs": 2814,
        "insertedRows": 7733,
        "outcome": "SUCCESS",
        "requestId": "ebbb2138-db7b-4551-a827-f987f99f1d03",
        "sourceRowCount": 7733,
        "updatedRows": 0
      },
      {
        "apiName": "top_list",
        "durationMs": 65,
        "insertedRows": 67,
        "outcome": "SUCCESS",
        "requestId": "3bdbdadf-334b-4024-8895-44c9e2c8720a",
        "sourceRowCount": 67,
        "updatedRows": 0
      },
      {
        "apiName": "margin_detail",
        "durationMs": 1766,
        "insertedRows": 4424,
        "outcome": "SUCCESS",
        "requestId": "08b3f6e4-c6dc-4f33-816f-82ef19bab99b",
        "sourceRowCount": 4424,
        "updatedRows": 0
      },
      {
        "apiName": "block_trade",
        "durationMs": 105,
        "insertedRows": 139,
        "outcome": "SUCCESS",
        "requestId": "2d54c4f9-a4ce-4ceb-a9d0-b30663e81c69",
        "sourceRowCount": 159,
        "updatedRows": 0
      },
      {
        "apiName": "slb_len",
        "durationMs": 61,
        "insertedRows": 0,
        "outcome": "EMPTY",
        "requestId": "5fe0597c-bba0-4c09-ae7d-d56ba8eb57ec",
        "sourceRowCount": 0,
        "updatedRows": 0
      },
      {
        "apiName": "slb_sec",
        "durationMs": 26,
        "insertedRows": 0,
        "outcome": "EMPTY",
        "requestId": "9512f32d-058d-421f-ae7a-85c77edf1d8b",
        "sourceRowCount": 0,
        "updatedRows": 0
      },
      {
        "apiName": "slb_sec_detail",
        "durationMs": 26,
        "insertedRows": 0,
        "outcome": "EMPTY",
        "requestId": "5054ceeb-0813-48a0-a501-d5805266b615",
        "sourceRowCount": 0,
        "updatedRows": 0
      },
      {
        "apiName": "forecast",
        "durationMs": 30,
        "insertedRows": 3,
        "outcome": "SUCCESS",
        "requestId": "d956ff2b-ea48-4ff8-b825-3ef23f0824b7",
        "sourceRowCount": 3,
        "updatedRows": 0
      },
      {
        "apiName": "express",
        "durationMs": 42,
        "insertedRows": 1,
        "outcome": "SUCCESS",
        "requestId": "8ded80bd-a611-4887-818b-248422bec227",
        "sourceRowCount": 1,
        "updatedRows": 0
      },
      {
        "apiName": "dividend",
        "durationMs": 434,
        "insertedRows": 38,
        "outcome": "SUCCESS",
        "requestId": "c05dfddc-0ff5-4335-92d1-9b97c4d88722",
        "sourceRowCount": 38,
        "updatedRows": 0
      },
      {
        "apiName": "disclosure_date",
        "durationMs": 123,
        "insertedRows": 10,
        "outcome": "SUCCESS",
        "requestId": "f2792d88-aa42-4cd9-9240-2cb7954018a9",
        "sourceRowCount": 10,
        "updatedRows": 0
      },
      {
        "apiName": "repurchase",
        "durationMs": 61,
        "insertedRows": 27,
        "outcome": "SUCCESS",
        "requestId": "5703e8ff-9923-4857-8541-a0eb8044d103",
        "sourceRowCount": 27,
        "updatedRows": 0
      },
      {
        "apiName": "stk_holdertrade",
        "durationMs": 63,
        "insertedRows": 44,
        "outcome": "SUCCESS",
        "requestId": "8de0e531-796b-4930-af53-88e6149a6759",
        "sourceRowCount": 157,
        "updatedRows": 0
      },
      {
        "apiName": "top10_holders",
        "durationMs": 164,
        "insertedRows": 320,
        "outcome": "SUCCESS",
        "requestId": "01e1ba67-d26a-4e6d-918f-b42c70cb6e91",
        "sourceRowCount": 320,
        "updatedRows": 0
      }
    ],
    "finishedAt": "2026-09-06T10:51:56.189Z",
    "fixture": [
      {
        "apiName": "fixture_daily",
        "durationMs": 5,
        "outcome": "SUCCESS",
        "requestId": "ce2ee36e-edb6-4501-bae6-8ff367dc0fd5",
        "resultCount": 0,
        "totalElements": 0
      },
      {
        "apiName": "fixture_daily",
        "durationMs": 22,
        "insertedRows": 1,
        "outcome": "SUCCESS",
        "requestId": "b38e1cb7-fc9d-4604-8c73-b82ce7748087",
        "sourceRowCount": 1,
        "updatedRows": 0
      },
      {
        "apiName": "fixture_daily",
        "durationMs": 5,
        "outcome": "SUCCESS",
        "requestId": "1c1887cd-d16c-4048-b99f-aebcca47aa2d",
        "resultCount": 1,
        "totalElements": 1
      },
      {
        "apiName": "fixture_daily",
        "durationMs": 0,
        "insertedRows": 0,
        "outcome": "EMPTY",
        "requestId": "51a8ffad-7f5f-4c5a-8ec1-da34ad85c091",
        "sourceRowCount": 0,
        "updatedRows": 0
      },
      {
        "apiName": "fixture_daily",
        "durationMs": 4,
        "outcome": "SUCCESS",
        "requestId": "ca8ef83a-bedb-45ff-9e17-ba0fc9b784c2",
        "resultCount": 1,
        "totalElements": 1
      }
    ],
    "inputs": {
      "gitCommit": "36f3e7f66307c98c0f72279fb8559a81eac79da8",
      "jarSha256": "81adba0dd6500f4aa43b4fa06b18c2c8e7b7454d9e6d4d6c734772cdaef1d002",
      "manifestSha256": "37a317f6a2bc3e5113be5f127976d16d8349414c6476c7f6a194b084a5b0f7c2",
      "specSha256": "e3d6ada1324df1deb140c264a2b7f3f8fae8f1a1d864ca02d2240ce4bfd7f1cc"
    },
    "queries": [
      {
        "apiName": "stock_basic",
        "durationMs": 2,
        "outcome": "SUCCESS",
        "requestId": "2e52e5da-1a96-4397-a40e-2f3450915b5e",
        "resultCount": 0,
        "totalElements": 0
      },
      {
        "apiName": "stock_basic",
        "durationMs": 6,
        "outcome": "SUCCESS",
        "requestId": "e4b53938-2c94-4022-a030-42bd3734e110",
        "resultCount": 50,
        "totalElements": 5895
      },
      {
        "apiName": "stock_company",
        "durationMs": 2,
        "outcome": "SUCCESS",
        "requestId": "6b6baeda-0395-477b-b93f-d9bb51ed12e0",
        "resultCount": 0,
        "totalElements": 0
      },
      {
        "apiName": "stock_company",
        "durationMs": 11,
        "outcome": "SUCCESS",
        "requestId": "0bacdf48-8ed0-4ba3-b7b9-731773d8e643",
        "resultCount": 50,
        "totalElements": 6294
      },
      {
        "apiName": "income",
        "durationMs": 2,
        "outcome": "SUCCESS",
        "requestId": "fed20283-f70f-4ebe-a3a6-6d56308b00fc",
        "resultCount": 0,
        "totalElements": 0
      },
      {
        "apiName": "income",
        "durationMs": 2,
        "outcome": "SUCCESS",
        "requestId": "760116d9-433b-44ae-9caf-2d6729f35d28",
        "resultCount": 0,
        "totalElements": 0
      },
      {
        "apiName": "balancesheet",
        "durationMs": 2,
        "outcome": "SUCCESS",
        "requestId": "eb8b8689-5222-44c5-9351-ad8d1ebd8ca4",
        "resultCount": 0,
        "totalElements": 0
      },
      {
        "apiName": "balancesheet",
        "durationMs": 2,
        "outcome": "SUCCESS",
        "requestId": "c85be7e7-45cb-44d2-9e8d-8a8496559c20",
        "resultCount": 0,
        "totalElements": 0
      },
      {
        "apiName": "cashflow",
        "durationMs": 2,
        "outcome": "SUCCESS",
        "requestId": "e619fb8b-8383-4451-ba5b-e2d2145a6738",
        "resultCount": 0,
        "totalElements": 0
      },
      {
        "apiName": "cashflow",
        "durationMs": 2,
        "outcome": "SUCCESS",
        "requestId": "f405e2f9-ea7e-45ce-88a6-10e875fb4da2",
        "resultCount": 0,
        "totalElements": 0
      },
      {
        "apiName": "fina_indicator",
        "durationMs": 3,
        "outcome": "SUCCESS",
        "requestId": "e4ba1bbc-92fc-481a-b5fb-ddabb8119c67",
        "resultCount": 0,
        "totalElements": 0
      },
      {
        "apiName": "fina_indicator",
        "durationMs": 2,
        "outcome": "SUCCESS",
        "requestId": "5aaa5c89-4278-42e2-be1f-8a5f8ea7bb0a",
        "resultCount": 0,
        "totalElements": 0
      },
      {
        "apiName": "fina_audit",
        "durationMs": 2,
        "outcome": "SUCCESS",
        "requestId": "dd723134-d40e-40f1-8d7d-d2f8a9c5289a",
        "resultCount": 0,
        "totalElements": 0
      },
      {
        "apiName": "fina_audit",
        "durationMs": 2,
        "outcome": "SUCCESS",
        "requestId": "3e65084c-8dca-49c4-bf35-9550e0494379",
        "resultCount": 0,
        "totalElements": 0
      },
      {
        "apiName": "fina_mainbz",
        "durationMs": 2,
        "outcome": "SUCCESS",
        "requestId": "cda35c6c-72aa-47ff-9286-aefd53f4e51e",
        "resultCount": 0,
        "totalElements": 0
      },
      {
        "apiName": "fina_mainbz",
        "durationMs": 4,
        "outcome": "SUCCESS",
        "requestId": "b8d60ed1-8d5f-4e47-ab29-1523aba706b4",
        "resultCount": 50,
        "totalElements": 150
      },
      {
        "apiName": "stk_rewards",
        "durationMs": 2,
        "outcome": "SUCCESS",
        "requestId": "8c928db0-ee5e-4811-a6f5-6dcbbfdcd460",
        "resultCount": 0,
        "totalElements": 0
      },
      {
        "apiName": "stk_rewards",
        "durationMs": 4,
        "outcome": "SUCCESS",
        "requestId": "8fec313a-076b-47e2-8a76-5e57362c3839",
        "resultCount": 50,
        "totalElements": 1428
      },
      {
        "apiName": "stk_holdernumber",
        "durationMs": 2,
        "outcome": "SUCCESS",
        "requestId": "5855a9fd-3f8c-4935-80eb-caeaf6d776a4",
        "resultCount": 0,
        "totalElements": 0
      },
      {
        "apiName": "stk_holdernumber",
        "durationMs": 3,
        "outcome": "SUCCESS",
        "requestId": "f42e809e-4b9f-4212-b129-3472e679a13a",
        "resultCount": 50,
        "totalElements": 150
      },
      {
        "apiName": "trade_cal",
        "durationMs": 4,
        "outcome": "SUCCESS",
        "requestId": "181a3158-a706-42fc-ba94-bad210fe4d7b",
        "resultCount": 0,
        "totalElements": 0
      },
      {
        "apiName": "trade_cal",
        "durationMs": 5,
        "outcome": "SUCCESS",
        "requestId": "a01848f4-bc53-4795-a1fd-76b5a8c6a390",
        "resultCount": 2,
        "totalElements": 2
      },
      {
        "apiName": "margin",
        "durationMs": 2,
        "outcome": "SUCCESS",
        "requestId": "11373d54-75ef-47b1-9d7f-3b04ae3e984a",
        "resultCount": 0,
        "totalElements": 0
      },
      {
        "apiName": "margin",
        "durationMs": 3,
        "outcome": "SUCCESS",
        "requestId": "e05d54b3-9af6-4ef3-908f-4db863d654f0",
        "resultCount": 3,
        "totalElements": 3
      },
      {
        "apiName": "daily",
        "durationMs": 2,
        "outcome": "SUCCESS",
        "requestId": "e9fdb8c0-b3e1-401f-b00b-1126f1c5c0af",
        "resultCount": 0,
        "totalElements": 0
      },
      {
        "apiName": "daily",
        "durationMs": 5,
        "outcome": "SUCCESS",
        "requestId": "1c713d71-f317-4279-b5a4-6ffc01536f3f",
        "resultCount": 50,
        "totalElements": 5535
      },
      {
        "apiName": "weekly",
        "durationMs": 2,
        "outcome": "SUCCESS",
        "requestId": "77fa4971-6c1d-433f-8e89-c2392f4e814d",
        "resultCount": 0,
        "totalElements": 0
      },
      {
        "apiName": "weekly",
        "durationMs": 4,
        "outcome": "SUCCESS",
        "requestId": "88cbf45c-278e-496b-8a2c-0c3b8e6cd33c",
        "resultCount": 50,
        "totalElements": 5613
      },
      {
        "apiName": "monthly",
        "durationMs": 2,
        "outcome": "SUCCESS",
        "requestId": "9e2f0f30-9854-4eac-811f-a2bfbf0843af",
        "resultCount": 0,
        "totalElements": 0
      },
      {
        "apiName": "monthly",
        "durationMs": 2,
        "outcome": "SUCCESS",
        "requestId": "b9c4372f-429a-41b5-80c1-eb3210b049ac",
        "resultCount": 0,
        "totalElements": 0
      },
      {
        "apiName": "adj_factor",
        "durationMs": 2,
        "outcome": "SUCCESS",
        "requestId": "bd71cec3-7587-491a-abdd-b7acd80bdfa1",
        "resultCount": 0,
        "totalElements": 0
      },
      {
        "apiName": "adj_factor",
        "durationMs": 4,
        "outcome": "SUCCESS",
        "requestId": "477ceaea-ca49-4a05-a4d8-11b5562fe518",
        "resultCount": 50,
        "totalElements": 5553
      },
      {
        "apiName": "suspend_d",
        "durationMs": 2,
        "outcome": "SUCCESS",
        "requestId": "6b6ceaf6-1013-4ba4-9de4-1ad3965aba02",
        "resultCount": 0,
        "totalElements": 0
      },
      {
        "apiName": "suspend_d",
        "durationMs": 3,
        "outcome": "SUCCESS",
        "requestId": "ab59bb4a-2204-4db9-90e1-8e05b784d7fa",
        "resultCount": 5,
        "totalElements": 5
      },
      {
        "apiName": "daily_basic",
        "durationMs": 2,
        "outcome": "SUCCESS",
        "requestId": "a62a23bf-93ce-4c08-9ed8-a540c17dd1b3",
        "resultCount": 0,
        "totalElements": 0
      },
      {
        "apiName": "daily_basic",
        "durationMs": 4,
        "outcome": "SUCCESS",
        "requestId": "40e590f5-8bf2-4cff-a946-9d8a87eda469",
        "resultCount": 50,
        "totalElements": 5535
      },
      {
        "apiName": "moneyflow",
        "durationMs": 2,
        "outcome": "SUCCESS",
        "requestId": "36e720b0-a998-45c2-befb-b0c5b9b7a52c",
        "resultCount": 0,
        "totalElements": 0
      },
      {
        "apiName": "moneyflow",
        "durationMs": 4,
        "outcome": "SUCCESS",
        "requestId": "54c0ca53-a08e-48b8-80cb-eb80a8313d68",
        "resultCount": 50,
        "totalElements": 5535
      },
      {
        "apiName": "stk_limit",
        "durationMs": 2,
        "outcome": "SUCCESS",
        "requestId": "a890fa14-0d20-49a9-b7e2-359fb8e533e2",
        "resultCount": 0,
        "totalElements": 0
      },
      {
        "apiName": "stk_limit",
        "durationMs": 4,
        "outcome": "SUCCESS",
        "requestId": "c8f104ce-75af-49c2-9d99-7f6b6b365e10",
        "resultCount": 50,
        "totalElements": 7733
      },
      {
        "apiName": "top_list",
        "durationMs": 106,
        "outcome": "SUCCESS",
        "requestId": "c23c0b3b-7871-4066-8b5d-c8c5fb25f265",
        "resultCount": 0,
        "totalElements": 0
      },
      {
        "apiName": "top_list",
        "durationMs": 4,
        "outcome": "SUCCESS",
        "requestId": "5b29f325-3270-4251-abe0-f2c8a986dd02",
        "resultCount": 50,
        "totalElements": 67
      },
      {
        "apiName": "margin_detail",
        "durationMs": 2,
        "outcome": "SUCCESS",
        "requestId": "521a0579-27ee-4edd-89cf-1b9db225a273",
        "resultCount": 0,
        "totalElements": 0
      },
      {
        "apiName": "margin_detail",
        "durationMs": 3,
        "outcome": "SUCCESS",
        "requestId": "46662c81-60fb-4d5d-b568-409e137aea19",
        "resultCount": 50,
        "totalElements": 4424
      },
      {
        "apiName": "block_trade",
        "durationMs": 2,
        "outcome": "SUCCESS",
        "requestId": "ece0c836-d0d1-47a1-8631-a45c602d9bbb",
        "resultCount": 0,
        "totalElements": 0
      },
      {
        "apiName": "block_trade",
        "durationMs": 3,
        "outcome": "SUCCESS",
        "requestId": "72d521d8-7d78-4766-9950-639b55d99bd9",
        "resultCount": 50,
        "totalElements": 139
      },
      {
        "apiName": "slb_len",
        "durationMs": 2,
        "outcome": "SUCCESS",
        "requestId": "416f86fa-75d4-43e4-932d-fcf6b81640de",
        "resultCount": 0,
        "totalElements": 0
      },
      {
        "apiName": "slb_len",
        "durationMs": 2,
        "outcome": "SUCCESS",
        "requestId": "8c052f81-581a-48c4-83a5-67a8136f4731",
        "resultCount": 0,
        "totalElements": 0
      },
      {
        "apiName": "slb_sec",
        "durationMs": 2,
        "outcome": "SUCCESS",
        "requestId": "6191825e-9642-4754-bd1f-06b65a1cb008",
        "resultCount": 0,
        "totalElements": 0
      },
      {
        "apiName": "slb_sec",
        "durationMs": 1,
        "outcome": "SUCCESS",
        "requestId": "b3ecae38-2920-450e-bd2a-83d65b12c309",
        "resultCount": 0,
        "totalElements": 0
      },
      {
        "apiName": "slb_sec_detail",
        "durationMs": 2,
        "outcome": "SUCCESS",
        "requestId": "1a7b521d-b5e0-4ec1-849a-c362cd20df07",
        "resultCount": 0,
        "totalElements": 0
      },
      {
        "apiName": "slb_sec_detail",
        "durationMs": 1,
        "outcome": "SUCCESS",
        "requestId": "9cc484dc-db2e-45e3-a2ac-b5afd1d6d95e",
        "resultCount": 0,
        "totalElements": 0
      },
      {
        "apiName": "forecast",
        "durationMs": 2,
        "outcome": "SUCCESS",
        "requestId": "572e8631-fe4f-48b1-b95d-ecb905b02a42",
        "resultCount": 0,
        "totalElements": 0
      },
      {
        "apiName": "forecast",
        "durationMs": 2,
        "outcome": "SUCCESS",
        "requestId": "bf2a6cec-3126-45e6-ba48-562fc0d529da",
        "resultCount": 3,
        "totalElements": 3
      },
      {
        "apiName": "express",
        "durationMs": 2,
        "outcome": "SUCCESS",
        "requestId": "abdc88a9-d23a-472d-a461-7e0782767bcb",
        "resultCount": 0,
        "totalElements": 0
      },
      {
        "apiName": "express",
        "durationMs": 3,
        "outcome": "SUCCESS",
        "requestId": "2b0adade-6d9d-45e6-b525-6f0c9e236af5",
        "resultCount": 1,
        "totalElements": 1
      },
      {
        "apiName": "dividend",
        "durationMs": 2,
        "outcome": "SUCCESS",
        "requestId": "ae28f897-afbf-4718-8099-d6814888fa70",
        "resultCount": 0,
        "totalElements": 0
      },
      {
        "apiName": "dividend",
        "durationMs": 3,
        "outcome": "SUCCESS",
        "requestId": "79192c14-f4a5-4c41-bca7-9c3b444b7d98",
        "resultCount": 38,
        "totalElements": 38
      },
      {
        "apiName": "disclosure_date",
        "durationMs": 2,
        "outcome": "SUCCESS",
        "requestId": "30211d68-a889-466d-a7e5-9352ecb2011a",
        "resultCount": 0,
        "totalElements": 0
      },
      {
        "apiName": "disclosure_date",
        "durationMs": 3,
        "outcome": "SUCCESS",
        "requestId": "3e57451a-e73c-420b-b86b-3e8c53eebaea",
        "resultCount": 10,
        "totalElements": 10
      },
      {
        "apiName": "repurchase",
        "durationMs": 2,
        "outcome": "SUCCESS",
        "requestId": "adccd702-5e2a-477d-9c9d-70623bbf25ff",
        "resultCount": 0,
        "totalElements": 0
      },
      {
        "apiName": "repurchase",
        "durationMs": 3,
        "outcome": "SUCCESS",
        "requestId": "2df9238b-8fea-4c9f-90d9-aa4aecd30a59",
        "resultCount": 27,
        "totalElements": 27
      },
      {
        "apiName": "stk_holdertrade",
        "durationMs": 2,
        "outcome": "SUCCESS",
        "requestId": "8e1aa0e8-36e5-4a48-a845-236d9ec16677",
        "resultCount": 0,
        "totalElements": 0
      },
      {
        "apiName": "stk_holdertrade",
        "durationMs": 2,
        "outcome": "SUCCESS",
        "requestId": "8238280f-f08f-435e-9c3c-0f805230c39e",
        "resultCount": 44,
        "totalElements": 44
      },
      {
        "apiName": "top10_holders",
        "durationMs": 2,
        "outcome": "SUCCESS",
        "requestId": "b9569864-d7d9-43a2-83cc-1de09fd4d62c",
        "resultCount": 0,
        "totalElements": 0
      }
    ],
    "scope": {
      "acceptanceStatuses": {
        "empty": 11,
        "ok": 29
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
          "acceptanceStatus": "empty",
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
    "startedAt": "2026-09-06T10:49:31.082Z",
    "task": "M14-T09",
    "totals": {
      "attemptedCases": 33,
      "callIntervalMs": 2000,
      "completedCases": 32,
      "failedCases": 1,
      "fixtureDownloadPostsObserved": 2,
      "fixtureRecordsGetsObserved": 3,
      "liveDownloadPostsObserved": 41,
      "liveDownloadResultsRecorded": 41,
      "liveQueryResultsRecorded": 65,
      "liveRecordsGetsObserved": 65,
      "manifestSamples": 58,
      "registeredCases": 40,
      "selectedSamples": 48,
      "unexecutedCases": 7
    },
    "version": 2
  },
  "task": "M14-T09"
}
```
