# Pause Handoff

## Handoff Type

pause

## Task Link

- **Task board:** `docs/task-handoffs/tensor-v1-task-board.md`
- **Task ID:** `M14-T05`
- **Transition:** `IN_PROGRESS -> BLOCKED`
- **Design document:** `docs/task-designs/M14-T05-design.md`

## Current State

用户已执行1gpnb4ru一次性启动器，139秒结束，实际28 passed / 1 failed / 11 did not run；当前阻塞是dividend的ADAPTER_TYPE_INVALID。stock_company和stk_holdernumber真实通过，ISSUE-005与ISSUE-006均已关闭。40项验收及原49目标尚未完成，不准备后继。

本轮证据已先独立提交 `e3013b1`。整篇真实Token扫描SHA `d4e7bf67b6a2b144662a987dec9aa812a8e39543c5134ed7cab8a12a4dbe34b5`，控制器复核一致、旧前缀未变。1gpnb4ru已使用并完成清理，不能再次运行。

独立ISSUE-007原参数单次诊断已实际完成：1.56秒、1次客户端执行、38源行，first conflicting_key rowIndex21，差异字段div_proc/cash_div/cash_div_tax，适配计数未建立；秘密扫描与Java退出通过，无数据库或重试。根因已定位为旧三字段业务键未区分实施进度。四字段指纹键、保留现有行的V7迁移及仅dividend的历史EMPTY预期修订已写具体设计，待确认后实施；生产/元数据/数据库/spec尚未更改。诊断不计为页面通过，不将新位置/数量回填为历史失败字段/计数。

## Changed Files

- `docs/verification/M14-T05-tushare-live.md`：真实28/1/11安全报告，已提交e3013b1，保持已扫描全文不变。
- `docs/issues/problems/ISSUE-006-holdernumber-announcement-date.md` 与 `docs/issues/README.md`：记录stk_holdernumber真实150行通过并关闭ISSUE-006。
- `docs/issues/problems/ISSUE-007-dividend-adapter-diagnosis.md`：登记单次真实诊断、根因和安全结果身份。
- `docs/issues/proposals/ISSUE-007-dividend-business-key.md`：待确认的四字段指纹键、V7迁移、回归和验收修订设计。
- 本交接和权威看板：记录新真实阻塞及解除条件。

## Verification

- 用户实际命令：`python3 /private/tmp/tensor-m14-t05-control.1gpnb4ru/launch.py`；运行Git `70422239d1d2b5dae40e15dd987bad6869f83b14`，完整139秒，spec约135秒，最终exit1。
- 实际attempted29/completed28/failed1/unexecuted11；真实POST37/records57，fixturePOST2/records3。99个请求各有且仅有一个完成事件；fixture SUCCESS/EMPTY闭环通过。
- stock_company三原样例2457+3083+754=6294；stk_holdernumber SUCCESS、source150/insert150/update0，页面与DB150，121ms，requestId `ba63f8ea-1043-4891-b2ee-fd577a371752`。历史149和当前150来自不同快照，不是同轮计数不一致。
- dividend唯一原样例ADAPTER_TYPE_INVALID，failureStage=adapter，320ms，requestId `4c5a2c20-e8e9-426d-a0b0-a758f881779f`。失败计数unavailable；表0不代表EMPTY；具体真实字段/值未保存。
- 初始6成功迁移/50业务表全0。最终20个非零表：adj_factor5553、block_trade139、daily5535、daily_basic5535、express1、fina_mainbz150、fixture_daily1、forecast3、margin3、margin_detail4424、moneyflow5535、stk_holdernumber150、stk_limit7733、stk_rewards1428、stock_basic5895、stock_company6294、suspend_d5、top_list67、trade_cal2、weekly5613；其余30表0。block_trade来源159/插入139符合既有去重语义。
- spec全部清理标志、worker/JVM退出、终检scanPassed/cleanupPassed均通过；CLI扫描5文件并删除2个自动产物，自有容器/卷和DB私密材料已清理。控制器确认8080空闲；保留目录0700，仅4个允许文件0600。
- 当前冻结JAR：`/private/tmp/tensor-issue-006-build.2rctzavi/data-plane/tensor-app/target/acceptance/tensor-app-1.0-SNAPSHOT-acceptance.jar`，SHA `f2fc35c933e69da5e85690fbabb13d691178538cd6ffb3b94284dfc95b10db89`。spec SHA `a81df4da7f92c6164062fa29a19902505dd5643c7c948a9aaa021f987220eee5`；原manifest SHA `37a317f6a2bc3e5113be5f127976d16d8349414c6476c7f6a194b084a5b0f7c2`。

已通过的28项为冻结顺序从stock_basic至express的全部接口。失败dividend；尚未运行11项为disclosure_date、repurchase、stk_holdertrade、top10_holders、top10_floatholders、new_share、stk_managers、pledge_stat、pledge_detail、index_classify、index_member_all。

范围固定40接口/48样例/80查询，fixture另计2POST/3查询。排除不计通过/失败/skip：top_inst、broker_recommend（higher_points）；share_float、hs_const、moneyflow_hsgt、hk_hold、index_member、hsgt_top10、namechange（permission_unverified）。

## Remaining Work

1. 确认ISSUE-007已写具体修复设计：按ts_code/end_date/ann_date/div_proc区分身份，保留nullable进度与同阶段跨次更新，以新增V7保留现有行；同步仅dividend的当前非空验收预期。旧诊断已消费，无需再次运行。
2. 确认后按设计实施最小修复、合成RED/GREEN、迁移/回归/独立复审和新冻结包接入。原manifest与历史真实失败保持不变。
3. 解除条件成立后才BLOCKED→READY并单独恢复M14-T05。最终全40与全部安全/清理门禁通过仅报告2000档阶段完成并PAUSED；若真实失败再次BLOCKED。原49未覆盖事实保留，不准备后继。

## Resume Task

解除dividend真实适配失败，再恢复M14-T05的2000档页面验收。

## Start Here

1. 权威看板Order75及完整 `docs/task-designs/M14-T05-design.md`。
2. 本交接与已扫描实际证据 `docs/verification/M14-T05-tushare-live.md`（提交e3013b1）。
3. `docs/issues/problems/ISSUE-007-dividend-adapter-diagnosis.md` 与修复设计 `docs/issues/proposals/ISSUE-007-dividend-business-key.md`。
4. 本轮安全标记 `/private/tmp/tensor-m14-t05-control.1gpnb4ru/run-finished.json`，终检产物根 `/private/tmp/tensor-m14-t05.91e69mlz`。不输出日志全文或真实行。

首动作：消费已定位的ISSUE-007根因与待确认设计；尚未确认时交付具体方案，用户确认后直接实施设计中的合成RED，不再要求Token设置或原诊断重跑。

## Blocker

- **Reason:** 1gpnb4ru实测dividend在adapter阶段返回ADAPTER_TYPE_INVALID，28通过/1失败/11未运行；历史页面失败字段/值未保存；独立诊断已定位进度冲突，所需业务键/迁移与验收设计尚未确认或实施。
- **Resolution condition:** ISSUE-007根因已确认，需落实四字段指纹键设计并建立修复的回归/独立复审、新冻结包合同/启动验证及验收设计接入；仅诊断成功或本地适配通过不足以宣称真实验收通过。

## Risks

- 不能将诊断的新请求位置/数量冒充1gpnb4ru历史失败字段/计数，也不能将adapter错误改判EMPTY。
- 诊断已证实当前非空与历史EMPTY预期不同；拟仅在验收层显式覆盖dividend，不修改manifest或删除接口。
- 原包、ISSUE-005与ISSUE-006包均保留，不暗换输入；所有真实响应仅在诊断内存处理。
