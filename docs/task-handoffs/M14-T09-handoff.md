# Task Transfer Handoff

## Handoff Type

transfer

用户明确要求把尚未完成的工作交给新任务。本文件是未完成工作的承接快照，不是以前驱COMPLETED为前提的普通next-task交接；没有声明任何任务已完成或已启动。

## Task Link

- **Task board:** `docs/task-handoffs/tensor-v1-task-board.md`
- **Source task:** `M14-T05`，Order75，当前BLOCKED，原49目标未完成。
- **Receiving task:** `M14-T09`，新增Order76，当前NOT_STARTED。
- **Design document:** `docs/task-designs/M14-T09-design.md`，D-01待确认，尚未实施就绪。
- **State change:** 仅新增任务和转移当前剩余工作归属；没有启动、解阻或完成转换。原M14-T06/T07/T08只顺延Order，ID/状态/依赖不改。

## Next Task

**M14-T09：分红修复与2000档剩余验收。** 接手以下三项：

1. 确认dividend记录保存规则，按确认后的方案修复业务键及必要数据库迁移。
2. 完成适配、旧库升级/新库迁移、幂等、schema、打包、独立复审及合成health验证，冻结新验收包。
3. 新包完整复跑40接口/48原样例/80查询及fixture2POST/3查询；记录新轮实际结果，扫描/清理后提交新证据。

完成要求是上述修复验证和新轮40项全部通过，不能把此前28项与新轮剩余项拼成通过；只有诊断或设计完成也不能关闭任务。原9项排除仍不覆盖，原49目标不宣称完成，不自动准备M14-T06。

**未决D-01随任务移交：** 推荐保留各实施阶段并使用四字段指纹身份，但用户尚未确认。用户问过为何增加进度，随后要求新增任务移交；这两个请求不能当成采用方案的批准。新执行者先解决此已列明裁决，设计明确后再READY和单独启动，不直接修改业务键。

## Dependencies

### M14-T05

- **Artifact:** `docs/task-designs/M14-T05-design.md`、`control-plane/e2e/tushare-live.spec.js`、`docs/verification/M14-T05-tushare-live.md`、`docs/task-handoffs/M14-T05-handoff.md`。
- **Decision:** 当前范围40接口/48原样例/80查询，fixture另2/3；9项排除、原manifest/参数、Token环境隔离、单worker、零重试、失败停止和安全清理全部继承。
- **Rationale:** 现有spec已执行并给出真实部分结果，新任务应接续修复和完整复验，不能重复搭建另一套流程或重做已关闭问题。
- **Constraint:** 输入是已完成且可用的实现/历史证据，不是M14-T05原49目标已完成。旧证据不改写；spec可以沿用原文件和M14_T05技术环境名，但新控制器任务门禁和新证据归属必须是M14-T09，不能等待原任务IN_PROGRESS。
- **Usage:** 继承完整页面与安全合同，新包和批准后的dividend预期接入同一spec；新的真实证据写 `docs/verification/M14-T09-tushare-live.md` 并扫描该精确新文件。
- **Readiness evidence:** 1gpnb4ru正式轮139秒、28通过/1失败/11未运行；fixture2/3、99请求各一个完成事件、独立DB/扫描/全部自有资源清理通过。证据提交e3013b1，全文SHA `d4e7bf67b6a2b144662a987dec9aa812a8e39543c5134ed7cab8a12a4dbe34b5`。这些证据可用但不证明新包或全40通过。

### M14-T04

- **Artifact:** `docs/verification/M14-T04-49-contracts.md`、`scripts/verify-49-contracts.sh` 及生产/acceptance打包合同测试。
- **Decision:** 保持49个元数据资源、851业务字段、公开查询/下载合同和打包完整性；schema变化须有版本化迁移及真实合同验证。
- **Rationale:** 原任务已建立元数据/schema/归档基线，新键迁移必须明确修订受影响物理列/索引/迁移计数并重新验证，不能沿用旧计数证明新包。
- **Constraint:** 原完成记录80a9491及其历史1007列证据保留。若四字段方案获确认，当前契约脚本/测试按新1008列等精确合同更新；脚本要求含已提交变更、受保护输入干净的main Git检出，并自行从HEAD创建快照。
- **Usage:** 执行原合同门禁并结合新增迁移/业务测试，核对新包版本；不得以旧合同通过替代新增修复验证。
- **Readiness evidence:** 已提交的50元数据/52schema/4打包检查与49/49资源合同及既有页面结果；实际命令/结果见该证据。新迁移及新包未验证。

### ISSUE-007 已完成诊断与候选修复设计

- **Artifact:** `docs/issues/problems/ISSUE-007-dividend-adapter-diagnosis.md`、`docs/issues/proposals/ISSUE-007-dividend-business-key.md`，提交e9fedfa。
- **Decision:** 真实冲突位于旧三字段业务键；不同记录的div_proc/cash_div/cash_div_tax不同。四字段FINGERPRINT、V7迁移和仅dividend预期修订为待确认方案，不是已经批准的实现。
- **Rationale:** 实施进度可能区分应分别保留的记录；只保留最终方案则需另一组明确业务规则。当前证据不能代替业务裁决。
- **Constraint:** 不保存真实38行、不猜测其他冲突、不得把新诊断rowIndex21或sourceRowCount38补成此前页面失败位置/计数；金额不按未经批准规则用于身份或择一。
- **Usage:** 先完成D-01；确认候选方案后按已复审文件边界、SQL/Java编码、停写/原子主键切换、回归和包接入实施。不再运行旧单次诊断。
- **Readiness evidence:** 原参数单次诊断1.56秒、clientExecuteCalls1、sourceRowCount38、conflicting_key，适配计数未建立，扫描/Java退出通过，无DB或重试。设计2Important经修订复审关闭，最后Minor命令前提已补；业务确认、代码、迁移、回归、新包及复验均未完成。

以上输入在历史事实与安全约束上相容。D-01是已明确移交的未决需求，阻止实施就绪；本transfer不绕过该门禁，也不是一次正常next-task/READY交付。

## Start Here

1. 完整读取 `docs/task-designs/M14-T09-design.md` 和权威看板Order76。
2. 本交接、ISSUE-007诊断详情与候选修复设计。
3. 完整M14-T05设计及已扫描真实证据，再按需要读取M14-T04合同来源。

**第一个动作：核对D-01是否已有后续明确确认。** 当前没有，先确认分红数据应保留各阶段还是仅最终方案；确认写入设计后才进入实施就绪。确认现有方案后直接从合成RED开始，不重复已完成诊断、ISSUE-005/006修复或Token设置。

可用本机材料（先检查存在性和hash）：

- 当前基线JAR：`/private/tmp/tensor-issue-006-build.2rctzavi/data-plane/tensor-app/target/acceptance/tensor-app-1.0-SNAPSHOT-acceptance.jar`，SHA `f2fc35c933e69da5e85690fbabb13d691178538cd6ffb3b94284dfc95b10db89`。
- 原manifest SHA `37a317f6a2bc3e5113be5f127976d16d8349414c6476c7f6a194b084a5b0f7c2`；现有spec SHA `a81df4da7f92c6164062fa29a19902505dd5643c7c948a9aaa021f987220eee5`。
- 诊断安全结果：`/private/tmp/tensor-issue-007-diagnostic.shhiyk_p/safe-result.json`，SHA `1b1ee18c0fb667517312957eed85620e9fecc2d1c4e91597413932e0586e2d46`。该目录已使用，禁止删除used标志或重复执行diagnose.py。
- 1gpnb4ru正式环境已清理，其安全标记在 `/private/tmp/tensor-m14-t05-control.1gpnb4ru/run-finished.json`，允许保留产物根 `/private/tmp/tensor-m14-t05.91e69mlz`。不输出日志全文、真实行或私密材料。

目前28项通过，失败dividend，未运行11项为disclosure_date、repurchase、stk_holdertrade、top10_holders、top10_floatholders、new_share、stk_managers、pledge_stat、pledge_detail、index_classify、index_member_all。新包须完整40项复验。

## Risks

- D-01未确认，不能因任务创建而直接实施四字段指纹键；该未决条件已转交，不在本轮强迫再次确认。
- 所有旧启动器已使用，不应复用；临时材料缺失时按提交记录恢复可验证产物，不绕过hash。
- 工具不会继承用户终端Token；本地工作先完成，再给已有Token终端一次新命令。禁止读取其他进程环境、把Token存文件或打印哈希。
- 新真实结果可能继续暴露故障，必须失败停止；诊断、历史28项和当前非空源计数不能代替新轮验收。
- 原49中9项仍不覆盖，后续性能/安全/发布任务没有因此获得就绪或启动授权。
