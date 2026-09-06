# Pause Handoff

## Handoff Type

pause

## Task Link

- **Task board:** `docs/task-handoffs/tensor-v1-task-board.md`
- **Task ID:** `M14-T05`
- **Transition:** `IN_PROGRESS -> BLOCKED`
- **Design document:** `docs/task-designs/M14-T05-design.md`

## Current State

**最新用户安排：** 暂缓 M14-T05，将原 49 中尚未覆盖的 9 项交由 `docs/issues/problems/ISSUE-008-tushare-live-coverage-gap.md` 跟踪；随后 M14-T06 也按用户要求跳过、登记 ISSUE-009 并标记完成，性能尚未实测。本任务保持 BLOCKED，本轮无真实调用或状态转换。以下 M14-T09 入口及旧轮诊断文字仅为历史；M14-T09 已完成，不再恢复旧任务或运行已使用控制器。

**M14-T09最终结果回写（2026-09-06）：** D-01分红修复及D-02/D-03当前预期修订全部完成验证。新9mkzsd_0轮173秒、npx/最终exit0，40通过/0失败/0未运行，48真实下载/80页面查询、fixture2/3、133个请求逐ID完成事件、全40页面/独立DB匹配、秘密扫描和自有资源清理全部通过。最新证据 `docs/verification/M14-T09-tushare-live-rerun-02.md`（14e038e，SHA5dd888c38608b058b15c89d9f3a29ce82ab1aeddd4e197eaa7ee66cafd6bf770）。M14-T09已达到完成条件；本任务保留BLOCKED和原49目标中9项未覆盖事实，不直接改为PAUSED/COMPLETED，不自动准备后继。此前D-01的32/1/7、D-02的33/1/6及下述原任务28/1/11均作为历史保留，不拼接或改判。


2026-09-06用户已要求将剩余工作移交新任务。当前续接入口为 **Order76/M14-T09**，见 `docs/task-designs/M14-T09-design.md` 与 `docs/task-handoffs/M14-T09-handoff.md`。本任务保留BLOCKED和以下历史结果，不因移交改判完成；后续不在本任务重复发起诊断或完整复跑。尚未确认的分红规则一并转交。

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

1. 移交M14-T09的分红修复、本地门禁和2000档完整40项验收已全部通过，无需重复执行；详见该任务完成证据。
2. M14-T05保留原49目标及历史证据，最新40项结果已回写；其余9项由ISSUE-008跟踪，按最新用户指示暂缓，不把本阶段完成当作原49完成。

## Resume Task

原M14-T05的49接口目标保留，M14-T09的2000档工作已完成。此次不恢复M14-T05；用户随后也将M14-T06性能验证转入ISSUE-009，并要求看板直接标记完成。当前安排以权威看板Workflow为准。

## Start Here

1. `docs/issues/problems/ISSUE-008-tushare-live-coverage-gap.md`：9项缺口、历史依据和未来关闭条件。
2. 权威看板Workflow最新安排；M14-T06性能尚未验证，后续范围由ISSUE-009跟踪。
3. M14-T09完整设计和最终证据作为已完成输入；本任务旧真实证据仅作历史和技术输入。

首动作：按当前看板选择执行入口；ISSUE-008后续恢复时先核对权限和完整验收合同，不复用旧启动器。

## Blocker

- **Reason:** 原49接口目标仍有9项在用户确认的2000档范围之外；M14-T09已完成40项，原分红缺陷及该阶段预期阻塞均已解决。
- **Resolution condition:** 用户另行明确要求并具备其余9项验证条件后，再按已建立的看板和设计流程恢复原49范围；当前不把本任务从BLOCKED直接改为PAUSED或COMPLETED。

## Risks

- 不能将诊断的新请求位置/数量冒充1gpnb4ru历史失败字段/计数，也不能将adapter错误改判EMPTY。
- 诊断已证实当前非空与历史EMPTY预期不同；拟仅在验收层显式覆盖dividend，不修改manifest或删除接口。
- 原包、ISSUE-005与ISSUE-006包均保留，不暗换输入；所有真实响应仅在诊断内存处理。
