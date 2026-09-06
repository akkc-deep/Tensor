# Pause Handoff

## Handoff Type

pause

## Task Link

- **Task board:** `docs/task-handoffs/tensor-v1-task-board.md`
- **Task ID:** `M14-T05`
- **Transition:** `IN_PROGRESS -> BLOCKED`（原暂停快照；恢复状态以看板为准）
- **Design document:** `docs/task-designs/M14-T05-design.md`

## Current State

当前权威看板为IN_PROGRESS。用户授权的2000档阶段已完成本地修订：提交83ce0f4，固定40接口/48原样例/80查询、28ok/12empty；top_inst/broker_recommend及7项权限待确认接口明确排除，原49/58 manifest与原JAR不变。范围探针、既有反例、语法/40项发现、缺环境失败摘要和独立任务审查通过。

新独占MySQL8.4.6已准备，最近独立检查仍为0表，字符集/最小权限/实际来源host通过；Java21/8080空闲及冻结哈希通过。一次性直接启动器已修正并通过10项合成控制流程检查，整体独立审查Approved for local launch readiness。真实JVM、40项页面矩阵、迁移后及末态DB观察均尚未运行。

本轮控制目录为 `/private/tmp/tensor-m14-t05-control.j9045eey`。`launch.py` 是已审查一次性操作材料，`run-config.json` 固定审查内容与40/9集合；DB连接材料只存0600私有文件，禁止输出/提交。Token只能从用户已有终端的TENSOR_TUSHARE_TOKEN环境进入；工具进程不能继承该环境，禁止读取其他进程或绕过先前Terminal访问限制。旧c0f2ywas目录及ready文件不可复用。

## Changed Files

- `control-plane/e2e/tushare-live.spec.js`：2000档40项修订83ce0f4，SHA为`6e31e4d9e567feebdb3f22ee421b43d00202cc56832d0accfeab1dbe87f61ac3`。
- `docs/verification/M14-T05-tushare-live.md`：新增当前范围、本地检查/独立审查/新DB与启动器准备事实；保留原49历史未执行记录。
- 同一任务设计/任务卡已由d573bed修订；看板由96f9604单独记录本轮IN_PROGRESS。
- 本交接和看板补充当前执行入口；没有生产文件、依赖、manifest或JAR改动。

## Verification

- 当前spec语法/范围RED→GREEN/既有同函数反例/40项发现/diff检查通过；独立任务审查Spec通过、Quality Approved，无发现。
- 缺环境轮外层exit0，npx1/最终1保留，1failed/39didnotrun；registered40/unexecuted40、manifest58/selected48/排除9、业务POST0、扫描/清理通过。它不证明真实接口可用。
- 一次性启动器10项合成探针通过：成功与原exit37、坏JSON、启动失败、泄漏/自动上下文、worker未退出、终检异常、缺Token先拒绝；整体审查Approved for local launch readiness。
- 本轮独立MySQL8.4.6/0表、权限/来源host及Java21/8080/冻结哈希检查通过，安全记录位于控制目录的database-preflight.json和current-preflight.json。
- 当前证据内shell/Python语法通过，固定终检函数与已提交版本字节相同；真实Token扫描、40/48/80、fixture2/3、迁移及末态匹配尚未验证。

## Remaining Work

1. 用户在之前设置Token的同一终端执行 `python3 /private/tmp/tensor-m14-t05-control.j9045eey/launch.py`。它直接开始已授权验收，每15秒提示运行状态，不再等待确认文件；不需要再次粘贴Token。
2. 读取本轮安全run-started/run-finished标记，等待CLI和全部自有工作进程退出、终检及精确DB清理。禁止直接输出runner.log、application.log或私有连接JSON。
3. 验证启动器追加的安全报告与run-finished记录的文档SHA一致，独立核对40/48/80和fixture2/3、6迁移/50表/40页面计数匹配/9表0行、所有扫描及清理。追加报告在持有Token环境中完成整篇秘密扫描；控制器之后不要改已扫描证据内容而失去该哈希证明。
4. 全部通过只记录本轮阶段完成并写pause交接后IN_PROGRESS→PAUSED；真实失败则记录实际失败、写pause交接后BLOCKED。原全49未完成，不准备后继。

## Resume Task

恢复M14-T05的2000积分档子集页面验收阶段。原全49目标仍有9项不覆盖，用户未来要求后再处理。

## Start Here

先完整读取权威看板链接的 `docs/task-designs/M14-T05-design.md`、本交接及当前证据的“当前2000积分档阶段”。实现已完成，不重新派发或重复旧本地套件。

首个动作：只读检查 `/private/tmp/tensor-m14-t05-control.j9045eey/run-finished.json` 是否存在。存在则消费安全报告、核对文档哈希和清理后更新状态；仅有run-started则继续观察安全标记；尚未开始则交用户执行上述新命令。没有run-config时先完成控制器封存，不能运行旧启动器。

## Blocker

- **Reason:** 原全49权限阻塞已由用户缩减范围解决；当前真实执行尚待用户在保有Token的终端启动一次。看板状态仍IN_PROGRESS，不把这一说明当作新BLOCKED转换。
- **Resolution condition:** 新启动器直接消费该终端环境，固定审查哈希/新空库/端口检查通过后运行；任何实际页面或环境失败保留证据，不改范围、样例或自动重试。

## Risks

- 公开规则不能保证账户现时权限或未耗尽用量；实际错误停止该轮并保留失败，不自动更改范围或参数。
- 原日期与ok/empty状态可能漂移，不改manifest、生产文件、旧测试或分发JAR。
- 40项成功也不代表原49、PRD全覆盖或发布准入通过。
- 用户Token只从规定环境进入执行进程，终检必须在CLI/全部worker退出后运行；不发布原行/日志、截图或trace。
