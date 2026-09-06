# Pause Handoff

## Handoff Type

pause

## Task Link

- **Task board:** `docs/task-handoffs/tensor-v1-task-board.md`
- **Task ID:** `M14-T05`
- **Transition:** `IN_PROGRESS -> BLOCKED`（原暂停快照；恢复状态以看板为准）
- **Design document:** `docs/task-designs/M14-T05-design.md`

## Current State

原49接口spec的本地实现、反例与独立审查已完成（最终a9bf981），真实矩阵尚未运行。用户随后说明账户2000+积分，并明确要求“先把这两个排除，验证可以满足2000档积分的即可”。本次修订已将当前阶段冻结为40接口/48原样例/80查询、28ok/12empty，明确排除top_inst/broker_recommend及7项权限待确认接口；原manifest49/58及分发JAR、生产代码均保持不变。

旧终端启动器已因Ctrl+C退出，安全结果报告ownedContainerRemoved=true；私有DB连接材料已清理，没有JVM或真实业务请求。旧terminal-ready文件不可复用。当前spec还需要按新设计修订、检查和审查，新的Token执行进程/空库也尚未准备。

用户新请求使原“必须先确认全49权限”的阻塞条件不再适用于当前子集实施；修订设计以公开积分规则选出40项并采用2秒间隔，实际账户权限/额度差异由正式页面验收结果保留，不能先探测、假定授权或失败后删项。恢复本地实施以该明确范围变更和已核对的固定集合为依据，真实运行前仍必须检查本轮Token/DB/JAR/Java/端口。

## Changed Files

- `control-plane/e2e/tushare-live.spec.js`：原49项实现已提交且审查通过；40项修订待实施。
- `docs/verification/M14-T05-tushare-live.md`：原本地证据、公开权限差异及旧等待进程清理；新40项本地/真实结果待追加。
- `docs/task-designs/M14-T05-design.md`：用户新授权的40项范围、排除原因、2秒节流、计数及执行合同。
- `docs/superpowers/plans/tensor-modules/M14-integration-release.md`：仅更新M14-T05当前阶段，保留原全49目标的未覆盖事实。
- 本交接和权威看板：记录范围修订与独立的恢复/启动转换。

## Verification

以下为已有已记录结果，不将历史本地检查当作新版本或真实验收：

- 原spec a9bf981 Node24语法、49项发现、同函数VM反例均通过；最终SHA为`f7f3c315913bc19b8e2d59ab7ca07e82e4d3bdcd58d7ed86ea0545fbbb47fb90`。
- 原终检10反例、Chromium合成失败产物清理通过；d378ad2缺环境轮npx1/最终1，仅Token前置失败、无JVM或业务调用；独立审查问题均闭环。
- 官方文档审计49地址，45含对应API说明、4文档不存在；2000档规则及已知冲突已写实际证据。
- 旧启动器曾确认Token存在/新库0表，随后KeyboardInterrupt并报告精确清理成功；它不再是活动运行环境。
- 新集合已从原manifest机械核对为40/48/28ok/12empty；新spec、真实矩阵和新终端启动器尚未验证。

## Remaining Work

1. 按修订设计实现固定范围选择、安全排除证据与40/48/80成功计数，运行范围RED/GREEN、Node24语法/40项发现、既有同函数探针和缺环境摘要检查，并完成独立审查。
2. 准备本轮独占空MySQL8.4.6和最小权限账号、新私有启动器及独立表计数；本地准备全部完成后，用户在保有Token的终端启动一次直接验收。
3. 运行40接口、48原样例、80查询及独立fixture2POST/3查询；按CLI退出后终检、6迁移/50表/40页面计数匹配/9表0行和清理合同记录实际结果。
4. 成功只报告本轮2000档子集完成并将原任务PAUSED；真实失败则BLOCKED。不将原49目标标为完成或自动准备后继。

## Resume Task

恢复M14-T05的2000积分档子集页面验收阶段。原全49目标仍有9项不覆盖，用户未来要求后再处理。

## Start Here

1. 完整读取权威看板链接的 `docs/task-designs/M14-T05-design.md`。
2. 本交接、权威看板M14-T05详情与任务卡当前阶段。
3. `docs/verification/M14-T05-tushare-live.md`、已审查spec及原manifest（不读模板data）。
4. 原JAR公开页面合同和验收runbook；既有本地探针见本任务忽略工作目录。

首个动作：核对用户明确的子集授权与40/48/28/12集合，按修订设计记录BLOCKED→READY，再按同一请求单独记录READY→IN_PROGRESS，开始本地范围修订。不得把这些实施就绪转换写成Token/DB已准备或真实验收已通过。

## Blocker

- **Reason:** 原全49权限阻塞已由用户范围修订替代；旧运行环境已清理，新阶段尚未实施/启动。
- **Resolution condition:** 明确用户子集授权、原manifest上固定40/48/28/12范围及修订设计/任务卡已核对，即可恢复本地实施；实际运行环境必须在正式CLI前单独检查，缺失或失败如实记录，不伪造旧ready证据。

## Risks

- 公开规则不能保证账户现时权限或未耗尽用量；实际错误停止该轮并保留失败，不自动更改范围或参数。
- 原日期与ok/empty状态可能漂移，不改manifest、生产文件、旧测试或分发JAR。
- 40项成功也不代表原49、PRD全覆盖或发布准入通过。
- 用户Token只从规定环境进入执行进程，终检必须在CLI/全部worker退出后运行；不发布原行/日志、截图或trace。
