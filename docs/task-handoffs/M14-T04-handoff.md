# Next Task Handoff

## Handoff Type

next-task

## Task Link

- **Task board:** `docs/task-handoffs/tensor-v1-task-board.md`
- **Completed task:** `M14-T03`，完成记录 `139c2c0`。
- **Next task:** `M14-T04`，按预定义 Order 74 选中。
- **Design document:** `docs/task-designs/M14-T04-design.md`
- **Expected next status:** `READY`; transition from `NOT_STARTED` only after this handoff is written and linked.

## Next Task

- **ID:** `M14-T04`
- **Title:** 49 数据集自动契约与页面回归驱动
- **Goal:** 运行既有元数据、MySQL schema、生产归档契约，并从原验收 JAR 页面验证 manifest 全部49接口与数据集的名称、中文说明、分类、参数控件/必填状态和精确筛选。
- **Scope:** 只新增 `scripts/verify-49-contracts.sh`、`control-plane/e2e/tushare-metadata.spec.js`、`docs/verification/M14-T04-49-contracts.md`。脚本在新临时HEAD源码快照运行原Maven生产构建与契约，避免工作区target/验收包被覆盖；spec包含49个接口/数据集配对用例、独立期望和最小同文件生命周期。禁止后端生产实现读取、生产/配置/依赖/既有测试修改、真实Token/真实上游、合法下载提交、records查询、API/SQL种数及mock响应。
- **Acceptance criteria:** `scripts/verify-49-contracts.sh`退出0，实际新XML证明M03 50、M04 52、生产JAR 4项全部通过；manifest、源YAML、49生产表、打包YAML全集一致，fixture附加表单独说明。`npx playwright test e2e/tushare-metadata.spec.js`恰49 passed、0失败/跳过/重试，下载API与dataset各49/49，43个必填拦截与6个无参数界面正确、5组filters完整，无自动查询；下载POST/recordsGET/上游调用全部0。前端120回归、语法、同函数报告/ZIP合成拒绝探针、安全、13截图人工审阅、只读库与自有资源清理、范围和格式门禁均通过。三个实施文件提交为 `test(release): verify all 49 dataset contracts`。

详细设计已由 `8526b97` 提交并回填同一Design document路径，之后完整读取。独立就绪审查指出上游环境变量名及XML属性层级/空failureMessage规则两处问题，已明确 `TENSOR_TUSHARE_BASE_URL`、`testsuite@name`/`testcase@classname` 和允许空/无非空文本的nil失败消息；定点复审两项Addressed、无新Critical/Important，`Ready for implementation: Yes`。49表与PRD/manifest、851字段合计、43/6参数分布、七组分类和五组filters无重复全覆盖、文档结构/引用已核对。新增脚本与49项E2E尚未创建或运行，设计预期不是验收结果。

## Dependencies

### `M03-T09`

- **Artifact:** `docs/task-designs/M03-T09-design.md`；`data-plane/tensor-plugin-tushare/src/test/java/com/akkc/tensor/plugin/tushare/metadata/TushareMetadataContractTest.java`；`docs/data-template/manifest.json`。通过其公开合同消费PRD附录A及M03-T02～T08批准参数/分类/filters，无需读生产实现。
- **Decision:** manifest定义唯一49 API/filename全集，模板字段为独立851列顺序基线；测试经公开loader校验参数、TRD业务键、filters、表名及默认batchSize，参数/键/filters期望不能来自实际YAML。当前全部参数必填、无description/defaultValue/pattern；filters按批准五组。
- **Rationale:** 用独立产品/模板基线发现缺项、多项与漂移，使页面回归不会以服务端实际响应自证正确。
- **Constraint:** 模板data必须由原测试流式跳过，不读取/复制样例行；不修改测试、YAML、manifest、模板或loader。manifest当前SHA-256为 `37a317f6a2bc3e5113be5f127976d16d8349414c6476c7f6a194b084a5b0f7c2`；params是样例对象数组，本任务只取参数key并集，不把样例值/状态当运行结果。`range`映射DTO `date_range`。
- **Usage:** shell显式运行原测试并从新报告核对49参数化+1全局，共50项；spec从manifest注册49稳定标题，参数/说明/category/mode由完成设计显式表给出，filters采用独立五组，与页面响应和实际控件分别比较。
- **Readiness evidence:** 看板已记录COMPLETED，实现 `36230d8` 精确新增唯一测试；缺类RED后定向50/50，提交后reactor test/verify均137/137、0 failure/error/skipped，49API/851字段、三层Enforcer及生产JAR排除通过，独立审查Ready to merge: Yes，无Critical/Important/Minor。此为既有输入可用性证据，后继仍须运行其新门禁。

### `M04-T06`

- **Artifact:** `docs/task-designs/M04-T06-design.md`；`data-plane/tensor-app/src/test/java/com/akkc/tensor/db/FlywaySchemaContractIT.java`；`data-plane/tensor-app/src/test/resources/db/migration/V6__create_fixture_tables.sql`；`data-plane/tensor-app/pom.xml` 中已批准两项Testcontainers测试依赖与现有构建合同。
- **Decision:** 固定官方 `mysql:8.4.6`，首次V1～V6六迁移/validate成功/二次零迁移；49生产表由公开loader定义逐表校验列/物理类型/空值/键/最小索引/InnoDB/排序规则。V6与fixture为测试专用，生产包仅V1～V5。
- **Rationale:** 真实MySQL契约证明元数据能对应实际schema；严格分离生产49与附加fixture，避免把验收包的50表误当生产覆盖。
- **Constraint:** 显式 `-Dtest=FlywaySchemaContractIT` 才执行该IT；不能依赖默认Surefire发现、无Docker时skip或替换数据库。生产为49表/851业务列/1000总列/49主键/40二级索引；加fixture为50表/1007列/50主键。既有V1～V5不可原地更改，不编辑POM或SQL，不停未知容器。
- **Usage:** shell通过设计规定的同一次reactor verify执行49动态+3固定共52项，从本轮Surefire XML与成功原测试的结果级断言取得schema证据。另消费既有 `PackagedJarContractTest` 的4项Failsafe报告与生产JAR资源核对；该公开测试/POM是直接构建输入，不新增任务依赖。
- **Readiness evidence:** 看板已记录COMPLETED，实现 `e78bd98` 精确三文件；缺类和缺V6两次可归因RED后真实MySQL52/52，主控新鲜复跑确认6/validate/0与全部schema totals，非定向test/verify150/150、六层Enforcer、依赖scope、生产JAR无V6/测试类及容器清理通过。任务审查Approved、最终Ready to merge: Yes，无Critical/Important；已记录Minor不影响逐表/总量/fixture三重键门禁。

### `M14-T01`

- **Artifact:** `docs/task-designs/M14-T01-design.md`；`control-plane/e2e/fixture-flow.spec.js`；`docs/verification/M14-T01-fixture-flow.md`；通过其分发合同消费 `docs/runbook/acceptance.md`、`docs/runbook/configuration.md`、`data-plane/tensor-app/target/acceptance/tensor-app-1.0-SNAPSHOT-acceptance.jar`。
- **Decision:** 原样验收JAR、acceptance+fixture enabled=true、全新独立MySQL schema与schema级应用权限、根health200/UP、公开role/label和真实页面入口；测试拥有JVM，SIGTERM后等待实际close及8080关闭，70秒是每阶段停机上限。
- **Rationale:** 已验证的分发/页面/生命周期合同让后继直接追加49描述符检查，避免重新装配应用或模拟成功响应。
- **Constraint:** 新完整运行使用新空schema，不复用前序进程/数据；应用只CREATE/SELECT/INSERT/UPDATE，凭证/原日志/截图不提交。不能import已注册测试的spec。M14-T01未配置Token时下载接口409是既定行为，因此后继用新生成假Token并显式 `TENSOR_TUSHARE_BASE_URL` 指向本机零调用哨兵；不使用真实Token。保留150秒正常停机观察，不强杀未知进程。
- **Usage:** 复制最小公开导航/生命周期模式到新spec，固定49配对用例，逐项从下载页进入数据查看页；只做metadata与客户端必填验证。原验收JAR独立于脚本新生产JAR，分别记录hash；准备后继独立库与私有环境，不连接已结束的前序环境。
- **Readiness evidence:** 实现 `23addbe`、完成记录 `afb3b85`，看板COMPLETED。原生产包负向仅因Fixture缺席失败；原验收包真实3/3、前端120/120，SUCCESS1/1/0、EMPTY0/0/0且行不变、同库关闭fixture后两页缺席与Tushare摘要一致。Java21.0.11/MySQL8.4.6/Node24.15.0/Playwright1.62.1/Chromium151.0.7922.34；原验收JAR SHA-256 `a69874afa6ce783d4ef4e16a678ddb0ff457f2948b68f509a8e4a2c00440bcac`。生产者构建Surefire368/Failsafe生产4+验收3、独立只读6迁移/50表/fixture1行、安全/范围/正常停机和独立审查通过。前序环境已清理，分发物与已提交证据继续可消费。

三个输入的49身份、schema表名和来源列、fixture隔离与页面合同一致。M11-T01于2026-09-04记录项目所有者批准暂不拆分类：当前七组（basic_organization及合并的互联互通与转融通），后继严格保留。M03-T09独立filters与下载参数职责不同，例如fina_mainbz有下载ann_date但只有ts_code筛选，不从页面实际列推断更多filter。无未解决的输入冲突。M14-T03只是Order前驱，不新增为直接依赖。

## Start Here

按顺序读取：

1. `docs/task-designs/M14-T04-design.md`，完整读取。
2. 本交接及 `docs/task-handoffs/tensor-v1-task-board.md` 的M14-T04行/详情。
3. `docs/superpowers/plans/tensor-modules/M14-integration-release.md` 的Global Constraints与Task M14-T04。
4. M03-T09设计/公开测试、manifest、PRD附录A；M04-T06设计/公开测试、POM和PackagedJarContractTest；M14-T01设计/测试/既有证据及验收/配置runbook。
5. `docs/contracts/openapi-v1.yaml`、M11-T01/T02与M12-T01设计/既有组件测试。只读元数据投影与公开合同，不读后端生产实现或模板data。

首个实施动作：确认三个实施目标无重叠修改，按已完成设计创建 `scripts/verify-49-contracts.sh` 的隔离HEAD快照/精确报告与归档门禁，以及 `control-plane/e2e/tushare-metadata.spec.js` 的完整49配对用例和同文件独立期望；先完成语法及同函数合成拒绝探针，再运行脚本。另准备后继专用新空schema、私有DB输入和原验收JAR，执行49用例发现及完整页面矩阵，最后写真实证据文档。不要先补设计、改产品或复用前序环境。

## Risks

- Maven快照构建仍需网络、磁盘、Java21与Docker固定镜像；报告必须属于本轮快照，前置失败不能以旧XML或skip掩盖。
- XML suite与case属性层级不同；成功Failsafe允许空failureMessage。精确遵循完成设计，避免把格式差异误报产品故障。
- 假Token只解锁元数据；哨兵HTTP500不提供数据，任何上游调用都使本任务失败，不能把未提交的表单说成真实49接口验收。
- 页面日期/月浮层定位用公开DOM和真实键盘；差异可按证据最小修正测试定位，不读取组件私有状态或删断言。
- 工作区可能有其他会话暂存文档或target图片；只提交三个实施路径，不运行工作区clean。每轮自有数据库/进程/凭证独立清理，保留安全本地证据。
