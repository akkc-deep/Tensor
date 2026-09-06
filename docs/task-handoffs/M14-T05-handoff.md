# Pause Handoff

## Handoff Type

pause

## Task Link

- **Task board:** `docs/task-handoffs/tensor-v1-task-board.md`
- **Task ID:** `M14-T05`
- **Transition:** `IN_PROGRESS -> BLOCKED`
- **Design document:** `docs/task-designs/M14-T05-design.md`

## Current State

2026-09-06 恢复准备：用户“继续修复”已由独立 ISSUE-005 执行，生产修复提交 `e8f37c2`。客户端现直接将 JSON 小数解析为 BigDecimal，合成 RED 已分别复现精度丢失和 stock_company/reg_capital 适配拒绝；GREEN 85 测试、独立复审通过。该合成字段不冒充上次真实请求的具体失败字段。

修复包在 `/private/tmp/tensor-issue-005-build.kibqgbn5/data-plane/tensor-app/target/acceptance/tensor-app-1.0-SNAPSHOT-acceptance.jar`，SHA `7f794f3494109c27f134c04846e486bda3fe18beec3a88246b58fbcea719cef9`，原包及历史真实证据保留。7 个唯一打包合同测试通过，展开后仅客户端类不同。新包合成 Token/health-only 诊断成功，6 迁移/50 表全空，扫描停机清理通过。设计已明确接入该唯一修复包；当前 spec SHA `0ab8f12d96fe622a257bdb08fc0f0882c4fc0d94758900af2dc6e2ab45b457a2`，语法/40 发现通过，实际前置函数已证明拒绝旧包。

新正式控制目录 `/private/tmp/tensor-m14-t05-control.6gn542ah` 已准备全新独占 MySQL8.4.6 空库、最小权限、实际来源 host 与回环绑定。直接启动命令是 `python3 /private/tmp/tensor-m14-t05-control.6gn542ah/launch.py`，只能由用户在已有 `TENSOR_TUSHARE_TOKEN` 的终端执行一次；不重新设置 Token、不复用历史启动器。启动器仍固定 40/48/2000ms、15 秒状态提示、单次运行、终检扫描及精确清理，仅固定 JAR 路径改变。真实新轮尚未开始；看板只在修复/接入检查全部成立后按 BLOCKED→READY、单独 READY→IN_PROGRESS 恢复，不将本地验证计为真实通过。

以下是上次真实运行的历史结果，保持原义：

用户已执行修复后的kybrot1f一次性启动器。本轮约26秒，npx1/最终1；启动日志检查通过，fixture SUCCESS/EMPTY的2POST/3查询闭环通过。首个真实接口stock_basic完成3组原样例，结果依次SUCCESS(5556插入)、EMPTY(0)、SUCCESS(339插入)，合计5895行；页面末查、行显示/来源/时间、日志关联与独立DB末态5895匹配。

第二个接口stock_company在首组样例返回公开错误ADAPTER_TYPE_INVALID，页面显示失败且唯一完成事件为failureStage=adapter。失败请求ID为e82ccf95-6182-4d73-9b98-10ae2bd7e13b，durationMs=304；失败的上游/插入/更新计数是unavailable，不能填0。具体不兼容字段或值没有保存，不能据错误码猜测原因。

本轮实际2项尝试、1通过、1失败、38未运行，真实下载POST4、records查询3；不是完整40项验收。stock_company失败后没有自动重试、删除接口、更换参数或继续其他项。原9项范围排除仍独立于这40项状态。

初始独立观察6成功迁移/50业务表全空；末态stock_basic5895、fixture_daily1、其余48表均0。stock_company表0仅说明失败后无写入，不是合法EMPTY。spec网络排空/JVM停止/日志扫描/不可变输入检查均true；全部worker退出、CLI后扫描/自动产物删除、整篇证据秘密扫描和精确DB/卷/私密连接材料清理通过，控制器另确认8080空闲。

## Changed Files

- `docs/verification/M14-T05-tushare-live.md`：本轮启动器追加实际安全报告，已独立提交14dd921；文档SHA为dc0c0329c2d5a4c60837c2d7bc5e927ec2419ceb0edfc32079c3c8cf167c5018，与run-finished的真实秘密扫描记录一致，控制器未改内容。
- 本交接和权威看板：保留上次实际部分通过、适配失败与 IN_PROGRESS→BLOCKED；补充 ISSUE-005 修复和新的恢复入口。
- 上次真实运行没有改生产代码。本次独立修复 `e8f37c2` 包含唯一 Java 配置行、两处回归测试和问题文档；验收接入只改 spec 固定 hash 和相应设计/状态文档。manifest、原 JAR 和既有真实证据未改。

## Verification

- 本地修复/构建/启动详细命令与结果见 ISSUE-005 问题文档。接入独立复审再次通过，四项文件 hash、40/48/9、归档唯一类变化、一次性启动器与安全流程均核对无发现。以下所有真实接口结果属于上次 kybrot1f 运行，新轮尚未执行。

- 实际用户命令：`python3 /private/tmp/tensor-m14-t05-control.kybrot1f/launch.py`，内部使用指定spec、1worker、0retries、2000ms间隔。运行Git4b46ec1；spec SHA为72b9763941e7ed82fbf1b207a79ee515d3d7343c9831ce2604f8ec9abd7ee973。
- 实际退出码1/1、26秒；runner1passed/1failed/38didnotrun与安全计数completed1/failed1/unexecuted38一致；fixture5项检查独立计数。
- stock_basic的3POST/2查询、fixture2POST/3查询以及stock_company的1POST/1初查共12个业务完成事件。控制器按已记录requestId独立核对日志中均恰一次；失败事件为adapter/ADAPTER_TYPE_INVALID且失败计数均unavailable，不输出原日志或参数摘要。
- stock_basic插入5556+339=5895，与页面totalElements及独立DB相同；fixture末态1，9排除表和其余未完成接口表均0。全40页面匹配门禁false是正确结果，不能由部分匹配改为true。
- CLI后终检scanPassed/cleanupPassed均true，扫描5文件、删除2自动产物。控制器确认仅4个允许保留文件且均0600、根目录0700、无playwright目录，私有DB连接文件已删除；run-finished记录ownedContainerRemoved和evidenceSecretScanPassed均true，文档哈希及计数一致性复核通过。

| 上次 kybrot1f 的40项API | 实际case状态 |
|---|---|
| `stock_basic` | 通过 |
| `stock_company` | 失败：ADAPTER_TYPE_INVALID |
| `income` | 未运行 |
| `balancesheet` | 未运行 |
| `cashflow` | 未运行 |
| `fina_indicator` | 未运行 |
| `fina_audit` | 未运行 |
| `fina_mainbz` | 未运行 |
| `stk_rewards` | 未运行 |
| `stk_holdernumber` | 未运行 |
| `trade_cal` | 未运行 |
| `margin` | 未运行 |
| `daily` | 未运行 |
| `weekly` | 未运行 |
| `monthly` | 未运行 |
| `adj_factor` | 未运行 |
| `suspend_d` | 未运行 |
| `daily_basic` | 未运行 |
| `moneyflow` | 未运行 |
| `stk_limit` | 未运行 |
| `top_list` | 未运行 |
| `margin_detail` | 未运行 |
| `block_trade` | 未运行 |
| `slb_len` | 未运行 |
| `slb_sec` | 未运行 |
| `slb_sec_detail` | 未运行 |
| `forecast` | 未运行 |
| `express` | 未运行 |
| `dividend` | 未运行 |
| `disclosure_date` | 未运行 |
| `repurchase` | 未运行 |
| `stk_holdertrade` | 未运行 |
| `top10_holders` | 未运行 |
| `top10_floatholders` | 未运行 |
| `new_share` | 未运行 |
| `stk_managers` | 未运行 |
| `pledge_stat` | 未运行 |
| `pledge_detail` | 未运行 |
| `index_classify` | 未运行 |
| `index_member_all` | 未运行 |

本轮范围排除（不计通过、失败或skip）：top_inst、broker_recommend（higher_points）；share_float、hs_const、moneyflow_hsgt、hk_hold、index_member、hsgt_top10、namechange（permission_unverified）。

## Remaining Work

1. 在新 6gn542ah 空环境和用户已有 Token 终端执行一次启动命令，真实复跑固定 40/48/80 与 fixture 2/3；不额外探测或自动重试。
2. 消费新 `run-finished.json` 与终检后的安全结果，核对新一轮证据文档 SHA、实际页面/DB/完成事件及清理状态；不能读取或输出日志全文、真实行、私密连接材料。
3. 全 40 及门禁通过才报告 2000 档阶段完成并 PAUSED；失败则 BLOCKED。原 49 目标未完成，不准备后继。ISSUE-005 的真实复验结果随本轮实际结果更新。

## Resume Task

用 ISSUE-005 的独立修复包恢复 M14-T05 的 2000 积分档真实页面验收。

## Start Here

1. 权威看板Order75与完整 `docs/task-designs/M14-T05-design.md`，特别是“错误、凭证与证据”对ADAPTER_*失败的范围约束。
2. 本交接、14dd921中的最新实际安全报告及 `docs/contracts/openapi-v1.yaml` 的公开错误合同。
3. 本轮安全标记 `/private/tmp/tensor-m14-t05-control.kybrot1f/run-finished.json`；已终检私有产物根 `/private/tmp/tensor-m14-t05.d0n8o20x`。禁止输出日志全文、真实业务行、Token或私有连接材料。

4. `docs/issues/problems/ISSUE-005-tushare-decimal-decoding.md` 与对应设计：已完成的本地修复/测试/复审/构建/启动证据，以及本交接顶部的新包和正式控制目录。

首个动作：核对看板恢复状态和 6gn542ah 的安全运行标记；用户尚未执行时给出上述单条命令，已执行时直接消费新安全结果。不要重复已完成的产品修复、旧诊断或 Token 设置；不要使用已清理的 kybrot1f / j0uo9psx 环境。

## Blocker

- **Reason:** 历史 stock_company 真实页面下载在 adapter 阶段返回 ADAPTER_TYPE_INVALID，导致串行验收 1 失败、38 未运行。独立 ISSUE-005 现已修复和验证已复现的客户端精度缺陷，真实历史失败不改判。
- **Resolution condition:** 本地 RED/GREEN、85 测试、独立复审、修复包合同/内容比较、新包启动及设计接入证据已成立，可恢复复验。新轮真实结果仍待观察；失败应保留新的具体证据并再次 BLOCKED。

## Risks

- Token已实际用于stock_basic成功调用；本轮错误不是鉴权/权限/限流错误码，但不能据此推断其余接口均获授权。
- 1个真实接口通过不等于40项或原49目标通过；38项未运行和9项范围排除必须区分。
- 失败响应计数unavailable与DB观察0行不同，不将失败写成EMPTY，也不改变原样例绕过错误。
- 原 JAR 保留；下一轮唯一冻结运行输入为本交接顶部明确的新修复包。独立 ISSUE-005 与页面验收边界分开，不暗换 JAR、放宽样例或扩大积分范围。
