# Next Task Handoff

## Handoff Type

next-task

## Task Link

- **Task board:** `docs/task-handoffs/tensor-v1-task-board.md`
- **Completed task:** `M13-T05`
- **Next task:** `M14-T01`
- **Design document:** `docs/task-designs/M14-T01-design.md`
- **Expected next status:** `READY`; transition from `NOT_STARTED` only after this handoff is written and linked.

## Next Task

- **ID:** `M14-T01`
- **Title:** fixture 页面端到端主闭环
- **Goal:** 消费现成验收JAR，从两页完成SUCCESS下载、七列查询、EMPTY零写入及fixture禁用重启，形成真实浏览器/数据库闭环证据。
- **Scope:** 只新增 `control-plane/e2e/fixture-flow.spec.js` 和 `docs/verification/M14-T01-fixture-flow.md`；测试文件内最小生命周期编排和恰三个顺序Playwright用例，证据文档记录实际结果与本地脱敏截图路径/哈希。不得改生产Java/Vue/YAML/SQL、打包、全局Playwright/Vitest配置、package/lock或runbook。不执行真实Token/49接口/失败注入/重复SUCCESS幂等矩阵。
- **Acceptance criteria:** 先以原生产包在另一全新schema取得能启动/两页正常但Fixture缺席的负向对照，再以验收包/新的空schema执行 `cd control-plane && npx playwright test e2e/fixture-flow.spec.js`，恰3通过、零失败/跳过/重试。页面SUCCESS与响应计数1/1/0，证券代码查询唯一行的日期/精度/null/来源/显示入库时间正确；EMPTY0/0/0且原行含时间不变；同包同库false重启后两页fixture缺席、Tushare摘要不变。真实业务写入/查询由页面触发，无mock、直接API写入或SQL种数。前端120项、JS语法、范围/格式/证据脱敏和Git两新增门禁通过，按 `test(e2e): verify fixture user flow` 提交。

设计已经完成并由 `07fc039` 链接到权威看板；独立只读设计审查及超时预算修订复审确认 `Ready for implementation: Yes`，无遗留项。本交接只准备后继；M14-T01测试实现、页面下载和新增端到端验收尚未执行。

## Dependencies

### `M13-T04`

- **Artifact:** `docs/runbook/first-run.md`、`docs/runbook/configuration.md`、`scripts/smoke-test.sh`；原生产构建产物 `data-plane/tensor-app/target/tensor-app-1.0-SNAPSHOT.jar`（生成物，不加入Git）。
- **Decision:** 同源单JAR，schema级CREATE/SELECT/INSERT/UPDATE应用账号，隐藏凭证输入，缺Token可启动但Tushare下载不可用，根health200/UP作为就绪；正常停机为SIGTERM/Ctrl-C并等JVM退出。
- **Rationale:** 为后继提供不依赖开发服务器或后端内部实现的稳定运行流程与配置/安全边界；原生产包也能用作fixture缺席的负向对照。
- **Constraint:** 原生产包只有V1～V5/49业务表、不含fixture/V6；不能连接已执行V6的验收库。保留 `120s < 130s <= proxy` 和70秒每阶段停机语义。原smoke只GET，不代替页面下载/查询，秘密/日志/截图/trace不得直接提交。
- **Usage:** 依据说明创建负向对照的独立空schema并注入三个DB变量，使用原生产包启动健康且两页可打开但Fixture缺席的对照；复用同一权限、秘密输入和正常停机方式运行正向验收。测试只停止自己的ChildProcess，不干预既有进程。
- **Readiness evidence:** 实现 `59acec3`、完成 `bb26660`。权威看板记录前端120/Surefire368/原Failsafe4、smoke临时矩阵107/107，真实Java21.0.11/MySQL8.4.6最小权限首跑/同库重启/页面刷新/正常停机通过。M13-T05进一步以同次原生产包在另一空schema加acceptance+true验证仍只含Tushare、V1～V5/49表且health/smoke/两页刷新正常；完整原生产合同始终保持。

### `M13-T05`

- **Artifact:** `docs/runbook/acceptance.md`；`data-plane/tensor-app/target/acceptance/tensor-app-1.0-SNAPSHOT-acceptance.jar`。构建入口 `mvn -f data-plane/pom.xml -Pacceptance clean verify`；打包合同由 `data-plane/tensor-app/src/test/java/com/akkc/tensor/build/AcceptancePackagedJarContractTest.java` 提供。后继只消费分发物，不读取/修改业务实现或重新选择装配方案。
- **Decision:** 验收包复用生产入口/页面/API/依赖，只附加原fixture模块与V6。仅运行时acceptance profile与 `tensor.plugins.fixture.enabled=true` 同时满足时注册fixture。SUCCESS固定一行，EMPTY固定零行；Tushare默认enabled、缺Token时credentialConfigured/downloadAvailable为false。
- **Rationale:** 提供可直接 `java -jar` 的真实fixture环境，后继能够只通过页面验证数据全链路而无需改变五模块、fixture scope或生产资源。
- **Constraint:** 独立验收schema；V6始终可见，fixture关闭不撤销迁移/删表/history。最终默认clean已清除target/acceptance；需要时用同一生产者命令重建并复制到新分发目录，不能把生产包更名为验收包。每次正向测试用空schema，因为fixture唯一业务键固定；测试describe retries=0，不自动清数据。`PERSISTENCE_FAILURE`只含note标记，没有完整数据库故障注入，不消费为本任务失败场景。
- **Usage:** 用非秘密测试定位变量 `ACCEPTANCE_JAR` 指向验收JAR绝对路径。测试文件拥有Java启停与根健康等待，三个普通用例SUCCESS→EMPTY→disabled串行执行；前两项/钩子180秒，第三项360秒覆盖停止150秒+就绪90秒+页面检查120秒。三个DB变量仅后端环境，清除Token/其他Tensor-Spring覆盖；固定本地8080，占用即失败，不复用未知应用。通过可访问角色/label选择Fixture/fixture_daily，SUCCESS显示1/1/0、查询 `000001.SZ` 七列，EMPTY显示0条且原行不变；正常停机后同库false重启，两页fixture缺席、Tushare摘要相等。
- **Readiness evidence:** 实现 `b500f3b`，完成 `e74feac`。严格RED：前端120/Surefire368/原Failsafe4通过，新3项仅缺验收JAR断言失败、零errors/skip。默认clean、显式clean、显式无clean、最终默认clean四轮均BUILD SUCCESS，分别120/368/4、120/368/7、120/368/7、120/368/4，双Failsafe报告和Boot→AntRun顺序确认；逐项SHA-256、STORED、manifest/输出隔离及10/10临时归档错误/重建检查通过。真实Java21.0.11/MySQL8.4.6四状态启停通过：验收true六迁移/50表且fixture可用；验收false和production profile仍六/50但fixture缺席；原生产包另一空schema五/49无fixture。四次health/smoke均通过；独立临时Chrome两页直接访问/刷新200、Vue实际渲染，启用时Fixture/API/五场景可选；浏览器21/24/24/24个本地GET，零JS错误、写请求、外部请求。四JVM正常SIGTERM自行退出，任务自有数据库和临时凭证状态已清理。完整实现审查无遗留项。这些是已完成的打包/只读证据，不证明M14-T01页面下载已经通过。

两项输入一致：生产包提供不含fixture的稳定对照，独立验收包提供fixture/V6正向输入；二者必须使用分离数据库迁移历史。后继保持双开关、schema权限、秘密边界和正常停机；无需修改打包或业务模块。浏览器应使用既有Playwright Chromium项目；如未安装，按设计安装，不依赖M13临时Chrome脚本或机器绝对路径。

## Start Here

按顺序读取：

1. `docs/task-designs/M14-T01-design.md`，完整读取。
2. 本交接，以及 `docs/task-handoffs/tensor-v1-task-board.md` 的M14-T01行/详情。
3. `docs/superpowers/plans/tensor-modules/M14-integration-release.md` 的Global Constraints和Task M14-T01。
4. `docs/runbook/acceptance.md`、`docs/runbook/first-run.md`、`docs/runbook/configuration.md` 与原smoke的公开使用说明。
5. `docs/contracts/openapi-v1.yaml` 的六个操作及相关DTO；`docs/task-designs/M08-T02-design.md` 的公开场景表和fixture YAML；只需设计列出的UI可访问标签/展示合同，不读后端内部实现。
6. `control-plane/playwright.config.js`、`control-plane/vitest.config.js`、`control-plane/package.json`。保留Chromium、现有产物目录和Vitest只发现src测试的边界。

首个实施动作：确认两个目标文件尚无重叠用户修改，按完整设计创建 `control-plane/e2e/fixture-flow.spec.js` 的三个真实Playwright用例及最小同文件Java进程生命周期，先用原生产JAR和隔离空schema运行规定命令，取得应用健康/页面正常而Fixture缺席的可归因负向对照；随后更换为验收JAR和另一新的空schema进行正向3/3验收，形成实际证据文档。不要先补写设计、修改打包、种数据或实现故障注入。

## Risks

- 验收包在最终默认clean后需要按生产者命令重新生成；没有当前target文件不等于设计/输入合同未完成，不能复用原生产包做正向验收。
- 固定SUCCESS业务键要求每次正向运行使用全新schema，不能把重复下载的更新计数冒充首次插入；CI重试在该describe禁用。
- 8080占用、浏览器未安装、Java/MySQL不可用是前置失败，不是可归因RED；不停止未知进程或以skip/伪造页面绕过。
- 插件关闭不删除V6或表；两个正常停机窗口不等于强杀期限，记录并保留尚未退出的任务进程事实。
- 本任务不得顺手修复跨模块产品缺陷，失败应定位并按权威看板流程交接独立修复范围。
- 截图/trace/日志仅在忽略的本地产物目录保管，文档记录真实路径/哈希和保管边界，不把真实秘密或生成产物提交Git。
