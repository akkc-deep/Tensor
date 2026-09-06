# Pause Handoff

## Handoff Type

pause

## Task Link

- **Task board:** `docs/task-handoffs/tensor-v1-task-board.md`
- **Task ID:** `M14-T05`
- **Transition:** `IN_PROGRESS -> BLOCKED`
- **Design document:** `docs/task-designs/M14-T05-design.md`

## Current State

恢复准备更新：ISSUE-006 已修复并提交 `c38dbac`。限定 `stk_holdernumber.ann_date` 的合法日期时间规范化，94项相关回归通过；复审1项Minor测试边界已补强并通过定点复审，无剩余发现。真实历史样本仅内存校验：旧包在ann_date拒绝，新包149行全部适配且源行未改。新包构建/7唯一打包合同与仅health启动验证通过，6迁移/50表全空，诊断环境0xdt5neo已清理。

下一轮唯一JAR：`/private/tmp/tensor-issue-006-build.2rctzavi/data-plane/tensor-app/target/acceptance/tensor-app-1.0-SNAPSHOT-acceptance.jar`，SHA `f2fc35c933e69da5e85690fbabb13d691178538cd6ffb3b94284dfc95b10db89`。与ISSUE-005包展开仅validator类变化，旧两包均保留。spec SHA `a81df4da7f92c6164062fa29a19902505dd5643c7c948a9aaa021f987220eee5`，语法/40发现通过；设计已明确新包接入。

新正式控制目录 `/private/tmp/tensor-m14-t05-control.1gpnb4ru`：全新独占MySQL8.4.6空库及最小权限已验证；启动器只改变固定JAR路径，四项hash和40/48/9范围封存。用户在已有Token终端运行一次：`python3 /private/tmp/tensor-m14-t05-control.1gpnb4ru/launch.py`。此命令仅在看板独立完成BLOCKED→READY和READY→IN_PROGRESS后交付，不重复设置Token。新轮尚未运行，以下仍是6gn542ah历史实际结果：

用户已执行 `6gn542ah` 一次性启动器，完整运行 58 秒，spec 阶段约 55 秒，npx/最终 exit 1。当前实际 9 passed、1 failed、30 did not run。stock_company 三组原样例全部 SUCCESS，分别插入 2457/3083/754，合计 6294，页面/来源时间/独立 DB 匹配；ISSUE-005 小数解码修复已真实验证并关闭。

新失败发生于 stk_holdernumber 原样例，ADAPTER_TYPE_INVALID，唯一完成事件为 adapter 阶段，requestId 841ad417-262b-4f40-a6ae-4154c536aac2，durationMs 37，失败计数 unavailable；具体真实字段/值未保存。独立 ISSUE-006 从历史模板定位 ann_date 混入合法日期时间格式并已修复，不能把历史行号当作本次真实位置。

40 项验收尚未完成。初始 6 成功迁移/50 表全空，末态 stock_basic5895、stock_company6294、fina_mainbz150、stk_rewards1428、fixture1，其余45表0。实际真实POST14/records19，fixturePOST2/records3，38个完成事件逐requestId唯一。spec四项清理、worker退出、CLI终检/自动产物删除与DB/卷/私密材料清理全部通过，8080空闲。6gn542ah 已用完，不再运行旧命令。

## Changed Files

- `docs/verification/M14-T05-tushare-live.md`：本轮原样安全报告已提交 `241813c`，整篇真实秘密扫描 SHA `699132b0e4373d9d74300f6b5b64b22a0b9c593601dd54b66feca428d9136afd`，控制器复核一致，旧前缀未变。
- 本交接、权威看板：保留本轮真实BLOCKED结果，记录修复证据及恢复入口。
- ISSUE-005 记录真实关闭；ISSUE-006 的代码/测试/文档已提交c38dbac，本地验证完成，真实复验未完成。验收接入只变固定JAR hash和设计/交接/状态，原证据未改写。

## Verification

- 用户实际命令：`python3 /private/tmp/tensor-m14-t05-control.6gn542ah/launch.py`，Git `ecbf035`，新JAR SHA `7f794f3494109c27f134c04846e486bda3fe18beec3a88246b58fbcea719cef9`，spec SHA `0ab8f12d96fe622a257bdb08fc0f0882c4fc0d94758900af2dc6e2ab45b457a2`，manifest不变。
- Fixture SUCCESS/EMPTY 闭环通过，stock_basic3样例、stock_company3样例、其后7项各1样例成功或合法EMPTY，第10项stk_holdernumber失败。页面与实际计数/显示检查仅对已完成9项成立。
- CLI终检扫描5文件、删除2自动产物，scanPassed/cleanupPassed true；仅4个允许文件保留且0600，根目录0700；自有worker/JVM/容器与卷已清理，私密DB材料已删除，当前8080空闲。

| 本轮40项API | 实际case状态 |
|---|---|
| `stock_basic` | 通过 |
| `stock_company` | 通过 |
| `income` | 通过 |
| `balancesheet` | 通过 |
| `cashflow` | 通过 |
| `fina_indicator` | 通过 |
| `fina_audit` | 通过 |
| `fina_mainbz` | 通过 |
| `stk_rewards` | 通过 |
| `stk_holdernumber` | 失败：ADAPTER_TYPE_INVALID |
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

范围排除不计通过/失败/skip：top_inst、broker_recommend（higher_points）；share_float、hs_const、moneyflow_hsgt、hk_hold、index_member、hsgt_top10、namechange（permission_unverified）。

## Remaining Work

1. 用户在已有Token终端执行新的1gpnb4ru单次命令，完整复跑40/48/80和fixture2/3；不额外探测或自动重试。
2. 消费新的run-finished和安全结果，核对整篇证据hash、实际完成/失败/未运行与独立DB和清理结论。stk_holdernumber真实通过后才关闭ISSUE-006。
3. 全40和所有门禁通过仅报告2000档阶段完成并PAUSED；实际失败则BLOCKED。原49目标未完成，不准备后继。

## Resume Task

以 ISSUE-006 的已验证新包恢复 M14-T05 的2000档真实页面验收。

## Start Here

1. 权威看板 Order75 与完整 `docs/task-designs/M14-T05-design.md`。
2. 本交接与实际证据 `241813c`。
3. `docs/issues/problems/ISSUE-006-holdernumber-announcement-date.md`。
4. 本轮安全标记 `/private/tmp/tensor-m14-t05-control.6gn542ah/run-finished.json`，已终检产物根 `/private/tmp/tensor-m14-t05.gzcihjou`；不输出日志全文、业务行或私密材料。

首动作：检查1gpnb4ru新运行标记；用户尚未运行时给顶部单条命令，已运行时直接消费安全结果。不要重复已完成的修复/本地验证，不复用已用完的6gn542ah/0xdt5neo启动器，不要求重新配置Token。

## Blocker

- **Reason:** 6gn542ah 的 stk_holdernumber 返回真实ADAPTER_TYPE_INVALID，9通过/1失败/30未运行；独立限定兼容修复现已通过本地验证，真实历史错误不改判。
- **Resolution condition:** ISSUE-006 的回归/独立复审、整批历史适配、新冻结包合同/启动及设计接入已成立，可恢复复验。新真实轮尚未验证，不能以本地通过代替真实通过；若失败应保留新证据并再次BLOCKED。

## Risks

- 历史 ann_date 的日期时间差异是可复现输入事实，尚不是本次真实错误字段证据。
- 真实错误计数 unavailable 与失败表0不同，不将错误计EMPTY。
- 保留原包和 ISSUE-005 修复包，后续包通过独立修复接入，不暗换输入。
