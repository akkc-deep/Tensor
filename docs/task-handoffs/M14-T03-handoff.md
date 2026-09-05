# Next Task Handoff

## Handoff Type

next-task

## Task Link

- **Task board:** `docs/task-handoffs/tensor-v1-task-board.md`
- **Completed task:** `M14-T02`
- **Next task:** `M14-T03`
- **Design document:** `docs/task-designs/M14-T03-design.md`
- **Expected next status:** `READY`; transition from `NOT_STARTED` only after this handoff is written and linked.

## Next Task

- **ID:** `M14-T03`
- **Title:** 查询、分页、宽表、竞态和无障碍 E2E
- **Goal:** 从原样验收JAR页面验证动态筛选、服务端分页、完整宽表、查询竞态、键盘可访问性与只读限制，形成AC-012～016的实际证据。
- **Scope:** 只新增 `control-plane/e2e/dataset-query.spec.js` 与 `docs/verification/M14-T03-dataset-query.md`。同文件本机Node上游、JAR生命周期、只读MySQL证据和窄范围请求门闩；恰11个串行Chromium用例、retries=0。五个现有数据集经7次页面下载建立/更新确定性数据，包含daily126行和balancesheet152业务列。禁止修改生产、配置/依赖、JAR、runbook或既有E2E，禁止直接业务API/SQL种数及伪造业务响应，不使用真实Token或真实上游。
- **Acceptance criteria:** 规定命令11 passed、0失败/跳过/重试，7页面下载与7stub调用一致；无自动查询、动态字段、代码与两类日期的闭区间/单边/AND、reset、20/50/100分页、完整行/页/totals一致。真实页面更新disclosure_date的非键ann_date后，旧页码3由真实服务端归一为1。balancesheet152+3列、来源/精度/固定列/横向滚动，company的TEXT空/null/零和tooltip通过；旧真实响应在切换/重置后不覆盖当前状态，网络失败重试和纯键盘闭环通过。页面/API/实际完成事件/截图/独立只读库证据、安全扫描及自有资源清理、前端120/120、语法/范围/格式/Git门禁全部通过。实施提交为 `test(e2e): verify read-only dataset UX`。

最终设计已由 `e8501c2` 提交并链接看板，随后完整读取。独立设计审查指出公告日期单边范围缺少具体查询，已补齐122行/1行场景；清理明确每阶段单次15秒CLI batch与150+15+15秒最终预算。定点复审 `Ready for implementation: Yes`，无未解决发现。设计已规定实际数据、请求、选择器、行/页/状态、门闩释放、安全与清理；本交接没有运行或宣称新增11项E2E通过。

## Dependencies

### `M14-T01`

- **Artifact:** `docs/task-designs/M14-T01-design.md`、`control-plane/e2e/fixture-flow.spec.js`、`docs/verification/M14-T01-fixture-flow.md`；通过其分发/运行合同消费 `docs/runbook/acceptance.md`、`docs/runbook/configuration.md` 与 `data-plane/tensor-app/target/acceptance/tensor-app-1.0-SNAPSHOT-acceptance.jar`。
- **Decision:** 使用原样验收JAR、acceptance+fixture enabled=true、真实MySQL新空schema、schema级应用权限、同源页面及公开role/label；业务写入/查询由页面触发。根health就绪，测试拥有JVM，SIGTERM后确认close/8080关闭。业务字段/来源列原序、完整精度、null与Asia/Shanghai时间显示通过独立断言验证。
- **Rationale:** 已验证的页面与分发基线使后继可以对现有产品追加查询UX验证，无需读取内部实现、重装配JAR或用mock响应冒充端到端结果。
- **Constraint:** 每次完整运行另建独立空schema，不复用前序数据/进程或删除history；应用只具CREATE/SELECT/INSERT/UPDATE，SQL只读验证，不替代页面业务操作。M14-T01的固定fixture只有一业务行，不能提供后继超100行/152列/公告日/空TEXT数据；其无Token与禁用矩阵是已完成场景，不要求后继重跑。70秒为每阶段停机上限，后继保留150秒正常观察，不强杀未知进程。已有spec含注册用例，不能import共享。截图、JSON、原日志和凭证不提交Git。
- **Usage:** 复用公开导航/选择器与最小局部生命周期模式，依后继完整设计用本机受控Tushare协议和新生成假Token建立五数据集；七次写入全部从下载页执行。从独立模板fields及公开M03/M05/M06/M12合同生成期望，页面查询后比较完整行/分页。请求门闩只暂停/放行原页面GET，网络故障只一次abort；保留真实后端数据，不改响应。重新准备专用schema、私有CLI默认文件和工具环境，不连接已结束的前序实例。
- **Readiness evidence:** 实现 `23addbe`、完成记录 `afb3b85`，看板状态COMPLETED。真实Java21.0.11/MySQL8.4.6/Node24.15.0/Playwright1.62.1/Chromium151.0.7922.34上最终3/3、前端120/120；SUCCESS1/1/0与七列查询、EMPTY0/0/0且含时间整行不变、同库禁用重启两页和Tushare摘要一致。原验收JAR SHA-256 `a69874afa6ce783d4ef4e16a678ddb0ff457f2948b68f509a8e4a2c00440bcac`，生产者构建Surefire368/Failsafe7通过；两个JVM正常退出，独立只读库6迁移/50业务表/fixture1行，安全与范围门禁及独立审查通过。前序临时运行环境已清理，JAR分发物与已提交公开证据是可消费输入。

只有M14-T01一个直接任务依赖；M14-T02是按Order的完成前驱，不新增为依赖。公开元数据与适配规则一致：当前没有同时带两类日期的注册定义，后继分别覆盖实际两类日期；balancesheet的STRING空串转null，真正空串用stock_company的TEXT证明。disclosure_date的ann_date不在业务键中，页面更新可合法触发末页归一化。未发现未解决的输入决策冲突。

## Start Here

按顺序读取：

1. `docs/task-designs/M14-T03-design.md`，完整读取。
2. 本交接及 `docs/task-handoffs/tensor-v1-task-board.md` 的M14-T03行/详情。
3. `docs/superpowers/plans/tensor-modules/M14-integration-release.md` 的Global Constraints和Task M14-T03。
4. M14-T01设计、测试、实际证据与验收/配置runbook。
5. 后继设计列出的OpenAPI、PRD6/AC-012～016、TRD13.5～13.7、M03元数据与五模板fields、M05适配、M06查询、M09事件、M10错误及M12设计/既有测试；不读后端生产实现。

首个实施动作：确认两个目标实施文件无重叠修改，按完成设计写出 `control-plane/e2e/dataset-query.spec.js` 的完整11项测试、固定数据和同文件生命周期/上游/请求门闩，准备全新 `tensor_m14_t03_<随机十六进制后缀>` 空schema、私有CLI输入和原验收JAR，再执行语法、11用例发现、真实矩阵与前端回归命令。不要先补设计、改产品/JAR、import已有spec或直接API/SQL种数。

## Risks

- 源/数据集选择器以公开标签与可访问文本为依据；实际展示差异须保存安全快照后最小修正定位，继续严格校验API身份和结果，不能弱化断言。
- 末页归一化必须保留原查询页的旧总数，由第二页面完成真实更新；提前刷新/重查会丢失超界触发条件。
- 旧请求必须在新状态建立后实际放行并完成，才能证明竞态；finally要释放/等待/unroute，任一失败不能跳过其他自有资源清理。
- 既有产品是否满足新增11项尚未验证；产品问题需保留精确失败并走独立单语言修复流程，不能假定通过、skip或改写安全证据。
- 本机需要标准Chromium、Java21、MySQL8.4与本任务新空schema。前序环境已经结束；禁止凭历史路径复用数据库或停未知服务。
