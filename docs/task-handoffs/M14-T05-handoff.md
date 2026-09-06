# Pause Handoff

## Handoff Type

pause

## Task Link

- **Task board:** `docs/task-handoffs/tensor-v1-task-board.md`
- **Task ID:** `M14-T05`
- **Transition:** `IN_PROGRESS -> BLOCKED`
- **Design document:** `docs/task-designs/M14-T05-design.md`

## Current State

用户已执行 `6gn542ah` 一次性启动器，完整运行 58 秒，spec 阶段约 55 秒，npx/最终 exit 1。当前实际 9 passed、1 failed、30 did not run。stock_company 三组原样例全部 SUCCESS，分别插入 2457/3083/754，合计 6294，页面/来源时间/独立 DB 匹配；ISSUE-005 小数解码修复已真实验证并关闭。

新失败发生于 stk_holdernumber 原样例，ADAPTER_TYPE_INVALID，唯一完成事件为 adapter 阶段，requestId 841ad417-262b-4f40-a6ae-4154c536aac2，durationMs 37，失败计数 unavailable；具体真实字段/值未保存。独立 ISSUE-006 已从历史模板定位 ann_date 混入合法日期时间格式，正在限定源字段兼容修复，不能把历史行号当作本次真实位置。

40 项验收尚未完成。初始 6 成功迁移/50 表全空，末态 stock_basic5895、stock_company6294、fina_mainbz150、stk_rewards1428、fixture1，其余45表0。实际真实POST14/records19，fixturePOST2/records3，38个完成事件逐requestId唯一。spec四项清理、worker退出、CLI终检/自动产物删除与DB/卷/私密材料清理全部通过，8080空闲。6gn542ah 已用完，不再运行旧命令。

## Changed Files

- `docs/verification/M14-T05-tushare-live.md`：本轮原样安全报告已提交 `241813c`，整篇真实秘密扫描 SHA `699132b0e4373d9d74300f6b5b64b22a0b9c593601dd54b66feca428d9136afd`，控制器复核一致，旧前缀未变。
- 本交接、权威看板：记录本轮真实结果并 BLOCKED。
- ISSUE-005 记录真实关闭；新增 ISSUE-006 详情、限定兼容设计和实施计划。新修复尚未声称验证完成。

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

1. 按 ISSUE-006 独立修复/测试/复审并验证新产物接入，保留通用严格转换规则与本轮实际失败。
2. 修复条件成立后单独记录 BLOCKED→READY、READY→IN_PROGRESS，用新空库与用户已有Token终端完整复跑40/48/80和fixture2/3。当前不准备新真实运行，不额外探测上游。
3. 全40和所有门禁通过仅报告2000档阶段完成并PAUSED；实际失败则BLOCKED。原49目标未完成，不准备后继。

## Resume Task

恢复 M14-T05 的2000档真实页面验收，先完成 ISSUE-006 的限定日期兼容修复。

## Start Here

1. 权威看板 Order75 与完整 `docs/task-designs/M14-T05-design.md`。
2. 本交接与实际证据 `241813c`。
3. `docs/issues/problems/ISSUE-006-holdernumber-announcement-date.md`。
4. 本轮安全标记 `/private/tmp/tensor-m14-t05-control.6gn542ah/run-finished.json`，已终检产物根 `/private/tmp/tensor-m14-t05.gzcihjou`；不输出日志全文、业务行或私密材料。

首动作：按 ISSUE-006 的限定源字段设计补合成 RED，再最小实现和 GREEN；不要再次修复已真实通过的 ISSUE-005，不复用任何已用完的启动器或要求重新配置Token。

## Blocker

- **Reason:** stk_holdernumber 返回真实 ADAPTER_TYPE_INVALID，9通过/1失败/30未运行；独立历史格式诊断和修复正在进行，真实具体字段未保留。
- **Resolution condition:** ISSUE-006 的限定兼容修复与针对性回归/独立复审通过，新冻结产物路径/hash与验收设计明确接入后恢复复验；不能靠删除接口、换参数或原样重试解阻。

## Risks

- 历史 ann_date 的日期时间差异是可复现输入事实，尚不是本次真实错误字段证据。
- 真实错误计数 unavailable 与失败表0不同，不将错误计EMPTY。
- 保留原包和 ISSUE-005 修复包，后续包通过独立修复接入，不暗换输入。
