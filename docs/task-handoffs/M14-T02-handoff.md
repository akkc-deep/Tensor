# Next Task Handoff

## Handoff Type

next-task

## Task Link

- **Task board:** `docs/task-handoffs/tensor-v1-task-board.md`
- **Completed task:** `M14-T01`
- **Next task:** `M14-T02`
- **Design document:** `docs/task-designs/M14-T02-design.md`
- **Expected next status:** `READY`; transition from `NOT_STARTED` only after this handoff is written and linked.

## Next Task

- **ID:** `M14-T02`
- **Title:** 下载失败、空结果、幂等和回滚矩阵
- **Goal:** 从真实验收 JAR 页面验证客户端拒绝、成功/空、重复下载、七类来源错误、适配错误及真实数据库回滚，保存响应/页面/数据/完成事件的一致证据。
- **Scope:** 只新增 `control-plane/e2e/download-outcomes.spec.js` 与 `docs/verification/M14-T02-download-outcomes.md`。同文件 Node 标准库本地上游、JAR 管理和 MySQL CLI 临时触发器故障准备；15 个串行 Chromium 用例、retries=0。不得改生产 Java/YAML/SQL/Vue、JAR、配置/依赖、runbook 或既有 E2E，不使用真实 Token/上游，不以直接业务 API 或 SQL 种数代替页面操作。
- **Acceptance criteria:** 规定命令恰15 passed、0失败/跳过/重试，14次页面下载POST、8次本机上游调用；客户端两项0 POST；fixture连续SUCCESS为1/1/0→1/0/1且入库时间更新，EMPTY/失败均保留完整基线；AFTER UPDATE/SIGNAL实际失败后页面整行含时间不变并有500/PERSISTENCE_FAILED及persistence完成事件；七类来源错误、精确摘要/HTTP/retryable、真实120秒读取超时和控件锁定分别通过。每个页面下载/查询请求恰一个完成事件；前端120/120、语法、范围、格式、秘密扫描和自有JVM/stub/trigger清理通过。两个实施文件以 `test(e2e): verify download outcome matrix` 提交。

最终设计已由 `d4880ab` 提交并链接看板，完整读取后通过独立就绪复审：`Ready for implementation: Yes`，无遗留项。设计明确15项具体输入、选择器、错误结果、临时触发器、数据库连接隔离、时限、日志扫描边界和清理。此交接只准备后继；新增15项矩阵尚未实施或运行，不把设计预期当作测试结果。

## Dependencies

### `M14-T01`

- **Artifact:** `docs/task-designs/M14-T01-design.md`、`control-plane/e2e/fixture-flow.spec.js`、`docs/verification/M14-T01-fixture-flow.md`；通过其固定运行合同消费 `docs/runbook/acceptance.md`、`docs/runbook/configuration.md` 和验收分发物 `data-plane/tensor-app/target/acceptance/tensor-app-1.0-SNAPSHOT-acceptance.jar`。
- **Decision:** 原样验收JAR以acceptance+fixture enabled=true运行，真实MySQL新空schema、schema级应用账号、同源页面、公开role/label选择器、页面发起下载与查询、根health就绪及自有JVM的SIGTERM/close/端口清理。fixture固定业务行与七列投影、完整精度/null/来源和Asia/Shanghai显示合同稳定。下拉框默认值从展开后的selected option验证，不读取内部readonly input.value。
- **Rationale:** 已通过的页面主闭环给后继提供真实运行基线，失败矩阵可在稳定入口上增加测试专用故障条件，无需改产品、重新装配包或伪造浏览器响应。
- **Constraint:** 每次完整运行使用另一新空schema，不复用前序库/记录或删除history。fixture `PERSISTENCE_FAILURE`只产生合法note标记，M14-T01未执行故障注入或重复SUCCESS矩阵；不能把标记存在当作已验证回滚。只读接口和秘密/正常停机边界不变；70秒为每阶段停机上限，不强杀未知进程。既有spec包含注册用例，不import它来共享helper。截图/JSON/原日志仅本地保存，不随Git分发。
- **Usage:** 复用已验证的角色/label、行值/时间的独立断言和局部生命周期模式，按后继完整设计创建自己的15项测试。先以页面SUCCESS建立fixture/daily基线，再测试结果与不变性；为既有合法note标记创建本次专用AFTER UPDATE触发器，用本机Node stub和生成的假Token覆盖上游，保留原120秒读取超时。默认文件严格语法、`--no-login-paths`、显式主机/端口/协议和受控CLI环境必须共同保证管理员只连本次schema。
- **Readiness evidence:** 实现 `23addbe`、完成记录 `afb3b85`，权威看板状态COMPLETED。真实Java21.0.11/MySQL8.4.6/Node24.15.0/Playwright1.62.1/Chromium151.0.7922.34上最终3/3，SUCCESS1/1/0与七列查询、EMPTY0/0/0且原行含时间不变、同库禁用重启摘要/页面验证通过；最终前端120/120、构建Surefire368/Failsafe7、语法/范围/格式/Git/脱敏门禁通过，两轮独立实现审查无遗留项。验收JAR SHA-256为 `a69874afa6ce783d4ef4e16a678ddb0ff457f2948b68f509a8e4a2c00440bcac`。两个最终JVM正常退出，只读库复核六条成功迁移/50业务表/fixture一行；随后本任务自有临时MySQL及私有凭证状态已清理。后继须重新准备其运行环境，不能连接已停机的前序实例。

只有一个直接任务输入，未发现依赖决策冲突。后继的临时触发器属于任务卡允许的故障准备，不改变fixture生产行为；本机stub消费既有Tushare协议。日志检查继续按M09-T06分表面执行：所有秘密和原始SQL不得泄漏，正常私有启动日志中的非秘密JDBC信息不冒充产品失败，公开响应和共享证据不暴露连接配置。

## Start Here

按顺序读取：

1. `docs/task-designs/M14-T02-design.md`，完整读取。
2. 本交接及 `docs/task-handoffs/tensor-v1-task-board.md` 的M14-T02行/详情。
3. `docs/superpowers/plans/tensor-modules/M14-integration-release.md` 的Global Constraints和Task M14-T02。
4. M14-T01测试/验收证据，以及验收包和配置运行说明。
5. 设计列出的OpenAPI/错误码、M09公共错误与完成事件、M08 fixture场景、M03 daily/new_share元数据、M07传输分类及M11可访问表单合同；仅消费公开设计和既有测试，不读后端生产实现。

首个实施动作：确认两个目标实施文件无重叠修改，按完成设计写出 `control-plane/e2e/download-outcomes.spec.js` 的完整15项矩阵及最小同文件生命周期/stub/临时触发器准备，准备本任务新空schema、私有管理员默认文件和原样验收JAR后执行设计中的语法、15用例发现、真实矩阵与前端回归命令。不要先补设计、修改JAR/产品、导入已有spec或直接种业务数据。

## Risks

- 本机需要MySQL 8.4客户端和能够在本次独立schema创建/移除触发器的管理员连接。严格默认文件或trigger/binlog权限不满足时，应记录具体环境阻塞，不能扩大应用账号权限、修改服务器全局策略或伪造500。
- 真实120秒读取超时使矩阵较慢；保留300秒启动钩子、180秒用例/最终钩子以及135秒该项响应等待，不缩短产品超时。
- 任何未解决产品缺陷都阻止任务完成，应保持失败断言与安全证据并走独立修复任务流程；当前交接不证明任何新增错误场景已通过。
- 新鲜schema、生成的假Token、触发器所有权和清理必须可核对；只清理本任务拥有的对象和进程，截图/日志/凭证不提交Git。
