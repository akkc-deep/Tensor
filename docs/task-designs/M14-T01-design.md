# M14-T01 fixture 页面端到端主闭环——任务设计

任务编号：`M14-T01`

权威任务：[任务看板](../task-handoffs/tensor-v1-task-board.md) 的 `M14-T01`（Order 71）。

对应任务：[M14-T01](../superpowers/plans/tensor-modules/M14-integration-release.md#task-m14-t01-fixture-页面主闭环40h)。

## Goal

从 M13-T05 已打包的真实 Servlet 验收应用完成 fixture SUCCESS 下载、页面查询、EMPTY 反馈和禁用重启，证明页面、同步 API、适配、真实 MySQL 入库与只读查询可以共同工作。留下可复现的 Playwright 测试和脱敏证据，不把本任务等同于真实 Tushare 或全部 AC 验收。

## Scope

只新增一个 JavaScript Playwright 文件和一份验收证据 Markdown。测试文件内包含本任务必要的 JAR 进程启动/停止、就绪等待、浏览器流程和只读证据采集；生产文件、Playwright 全局配置、package/lock、Maven、migration 和 runbook 均不修改。

覆盖首次空库、SUCCESS 插入一行及查询其七列、EMPTY 不增加记录、同包同库关闭 fixture 后两页无 fixture 且 Tushare 摘要不变。排除重复 SUCCESS 的幂等矩阵、来源/类型/写入失败、数据库故障注入、宽表/分页竞态、真实 Token、49 接口、性能和发布安全矩阵。这些留给既定后继任务。

发现产品缺陷只定位并记录在本任务证据中，按看板工作流提交独立单语言修复任务的事实与待决范围，不在本任务改 Java/YAML/SQL/Vue 或自行增加任务ID/Order。未解决的结果级缺陷不能靠降低断言或 skip 换取完成。

## Approach

### 输入和固定边界

- M13-T05：`docs/runbook/acceptance.md` 与其规定的 `tensor-app-1.0-SNAPSHOT-acceptance.jar`。验收包已提供原 fixture 和 V6，无需测试重新装配、解包、加 classpath 或编辑源码。Maven 构建开关不等于运行开关；运行必须为 `acceptance + tensor.plugins.fixture.enabled=true`。
- M13-T04：`docs/runbook/first-run.md`、`docs/runbook/configuration.md`、`scripts/smoke-test.sh`。复用 schema 级 CREATE/SELECT/INSERT/UPDATE、隐藏秘密注入、根 health、同源访问、70 秒每阶段正常停机及不破坏 history 的约束。
- 公共 REST 合同：`docs/contracts/openapi-v1.yaml` 的六个 `/api/v1` 操作及 DataSourceSummary、ApiDescriptor、DownloadRequest/Response、DatasetDefinitionResponse、PageResponse、ApiError。请求观察只能采集本次页面发出的请求；不通过 request.post、fetch POST、SQL 写入或 mock route 替代下载/查询操作。
- fixture 公开元数据与已冻结行为：`docs/task-designs/M08-T02-design.md` 的五场景表和 `data-plane/tensor-plugin-fixture/src/main/resources/datasets/fixture/fixture_daily.yaml`。本任务只消费 SUCCESS/EMPTY；唯一成功行的原始值为 `000001.SZ`、`20260807`、`11.23`、null。
- UI 只读取已有公开可访问标签和展示合同，不读后端内部实现。选择器与值见下表；不新增测试专用 DOM 标记。

### 运行前置条件与进程所有权

测试执行者按验收说明准备**全新空** `tensor_acceptance` schema 和应用账号，交互导出 `TENSOR_DB_URL`、`TENSOR_DB_USERNAME`、`TENSOR_DB_PASSWORD`，清除 Token 和开发 CORS。每次完整成功运行/重跑均使用新的独立空 schema；测试不 DROP/TRUNCATE、不删记录或 history，不把生产库用于本流程。

从 M13-T05 分发物取得验收包，在专用 shell 设置仅供测试定位文件的非秘密变量 `ACCEPTANCE_JAR` 为其绝对路径；不新增产品配置变量。若构建输出已被 clean 清除，先调用产物提供方的既定 `mvn -f data-plane/pom.xml -Pacceptance clean verify` 重新取得同合同分发物，再复制到独立目录；本任务不改变该打包过程。

测试文件使用现有 `@playwright/test` 和 Node 标准库，固定在一个 `test.describe` 中设置 `mode: 'serial'`、`retries: 0`，恰好三个普通 `test`，顺序为 SUCCESS→EMPTY→disabled。一次命令完成三个场景，不能要求执行者在两条测试之间手动抢时机重启。前两项测试及 beforeAll/afterAll 钩子的上限为 180 秒；第三项用例单独设置 360 秒，覆盖最多 150 秒停止、90 秒就绪及剩余 120 秒的元数据/页面检查，不压缩正常停机观察窗口。不改全局 Playwright 配置或其他文件的重试策略。

`beforeAll` 检查 JAR 是普通文件、三个 DB 变量非空以及 `PLAYWRIGHT_BASE_URL` 未设置或精确为 `http://127.0.0.1:8080`。若缺项或 8080 已有监听进程，安全失败，不回显变量值、不连接复用未知应用、不停止占用端口的进程。使用 `spawn('java', [...], {shell: false, cwd: 独立临时运行目录, env: 受控环境})`，参数固定为：

```sh
java -jar "$ACCEPTANCE_JAR" \
  --spring.profiles.active=acceptance --tensor.plugins.fixture.enabled=true \
  --server.address=127.0.0.1 --server.port=8080
```

测试使用绝对 JAR 路径，独立临时目录只承载运行日志，不需要把整个源码工作目录当分发物。保留 Java 工具链环境和三个 DB 变量，清除继承的 `TENSOR_*`、`SPRING_*`、`SERVER_*` 后只回填三个 DB 变量，使 Tushare enabled、显示时区等使用分发默认值；不注入 Token。凭证不进入 argv、测试标题、attachments 或 console。

只轮询 `GET /actuator/health`，每次请求上限 2 秒、总就绪观察上限 90 秒，等待 HTTP 200 且根 status=UP；进程提前退出或到期即安全失败。禁止用 JVM 存活、readiness 端点或固定 sleep 代替根就绪。进程 stdout/stderr 仅写权限受限的临时本地日志，不附原日志到报告。

保存本测试 `ChildProcess`；第三项在重启前及 `afterAll` 对它发 SIGTERM，等待实际 exit/close 事件并确认端口关闭。70 秒是每阶段上限；测试给正常退出 150 秒观察窗口，超时只报告未退出和非秘密 PID，不自动 SIGKILL。所有启动失败路径也必须进入本任务拥有的进程清理，不按名字批量 kill。由用户/执行器处理尚未退出的本任务进程后再恢复，不能继续占用端口进行后续断言。

### 页面操作与选择器

复用当前 Chromium 项目；若未安装浏览器，先运行 `cd control-plane && npx playwright install chromium`。测试不硬编码本机 Chrome 路径，不修改全局 launch 配置。统一 viewport 为 1440×1000，仅影响该测试文件。使用 Playwright 自动等待、`expect` 和在操作前注册的 `waitForResponse`，不用 `waitForTimeout`。

| 操作/断言 | 固定定位与期望 |
|---|---|
| 直接访问/刷新两路由 | `page.goto('/downloads')` / `page.goto('/datasets')` 与 `page.reload()` 均 HTTP 200；level 1 heading 为“数据下载”/“数据查看”。 |
| 切换页面 | 导航 link“数据下载”/“数据查看”；至少一次通过页面导航进入查询页。 |
| 数据源 | `getByRole('combobox', {name: '数据源', exact: true})`；聚焦后 Enter 打开，option“Fixture”选择。只读 input 的占位层可能拦截 click，不使用 force click 或 Element Plus CSS 内部结构。 |
| 下载接口 | combobox“数据接口”，打开后 option 名匹配 `/Fixture 日线.*fixture_daily/`。 |
| 场景 | 用关联 label/combobox 名“场景”（允许必填星号）定位，默认 SUCCESS；选择 EMPTY 时打开 option“EMPTY”。默认值从控件可见选中项/可访问展开选项确认，不查询 Vue 实例状态。 |
| 提交 | button“开始下载”，实际点击。 |
| SUCCESS 反馈 | status 内 heading“下载成功”，语义 term 列表为“上游返回数”“插入数”“更新数”，对应 definition 列表依次为 1、1、0。 |
| EMPTY 反馈 | heading“下载成功，0 条数据”，可见“本次请求没有可写入的数据。”，无“下载失败”。 |
| 查询数据集 | combobox“数据集”，option 名匹配 `/Fixture 日线.*fixture_daily/`。 |
| 筛选与查询 | `getByLabel('证券代码 (ts_code)')` 输入 `000001.SZ`，button“查询”点击。 |
| 首次空结果 | heading“未找到符合条件的数据”；真实页面触发的 PageResponse 为 totalElements=0、totalPages=0、items=[]。 |
| 查询行 | columnheader 依次含 ts_code、trade_date、amount、note、source_plugin、source_api、ingested_at；通过含 cell“000001.SZ”的 row 定位，七个 cell 必须逐项正确。 |

仅使用 role、关联 label、可见文本及语义 HTML 的 term/definition/cell 等结构。不引用 `.el-*`、组件类、内部ID、nth-child 或 Vue 方法。需要滚动查看来源列时，由目标 cell/columnheader 的 `scrollIntoViewIfNeeded()` 完成。

### 三项顺序测试

1. **`downloadsSuccessAndQueriesFixtureFromPages`**：确认两页直接打开和刷新；通过数据查看页选择 fixture/fixture_daily 并点击查询，证明全新库 fixture 查询为空，再从页面导航进入下载页选 fixture/fixture_daily。观察默认 SUCCESS，点击开始下载。操作前监听该页面的唯一 `POST /api/v1/downloads` 响应，断言 HTTP200、请求为 `{pluginId:'fixture',apiName:'fixture_daily',params:{scenario:'SUCCESS'}}`、响应 SUCCESS 和 counts 1/1/0、requestId非空且匹配响应头；页面三计数同样为1/1/0，不能只断言网络结果。再通过导航进入数据查看页，选择同一数据集、输入证券代码、点击查询；观察页面发出的 records GET含 `tsCode=000001.SZ`，page=1/pageSize=50（显式参数或合同默认），PageResponse精确 totalElements=1/totalPages=1/items.length=1。唯一行值为 `ts_code='000001.SZ'`、`trade_date='2026-08-07'`、`amount='11.230000000000000000'`、note=null、source_plugin='fixture'、source_api='fixture_daily'，ingested_at是可解析的带时区时间戳。页面依次显示前六项 `000001.SZ`、`2026-08-07`、`11.230000000000000000`、`--`、`fixture`、`fixture_daily`；ingested_at按既有展示合同为 Asia/Shanghai 的 `YYYY-MM-DD HH:mm:ss`，用 Node Intl 固定该时区从本次响应时间独立计算期望，不能调用产品 formatter。保存该行（含入库时间）供下一项不变性对照。
2. **`showsEmptyDownloadWithoutAddingRows`**：新 page从下载入口选同一 fixture/API，将场景改为EMPTY并点击开始下载。观察页面POST请求精确EMPTY、响应HTTP200/outcome=EMPTY/counts0/0/0；页面显示指定空下载反馈。再次从数据查看页用相同证券代码点击查询，仍恰有上一项的同一行，含ingested_at不变且无占位记录；这证明空结果没有伪写入，不扩展为第二次SUCCESS幂等验收。
3. **`hidesDisabledFixtureOnBothPagesAfterRestart`**：在初始启动时通过只读data-sources GET保存的Tushare完整摘要基础上，正常停止拥有的JVM，同一JAR/同一DB仅把 `--tensor.plugins.fixture.enabled=false` 改为false重启并等待根健康。GET data-sources精确仅Tushare且对象与原摘要一致。两页均直接打开/刷新200、预期heading可见；打开数据源combobox，option列表只有Tushare Pro，不存在Fixture。无Token时下载页可以显示既定配置不可用/接口列表409，不能要求Tushare下载成功。关闭fixture不撤销V6，不执行DDL/清理库。

三个测试不得使用mock、route.fulfill、伪造成功响应、浏览器外的业务POST或SQL写入。health和数据源摘要的额外GET仅用于生命周期/只读证据；下载和查询必须由可见页面控件触发。记录pageerror；预期之外的JS错误、业务HTTP失败、写请求或外部浏览器请求使对应测试失败。只允许两次由点击触发的fixture下载POST，以及本地页面/元数据/查询GET；fixture不调用真实上游。

### 证据、安全和失败行为

证据文档记录任务/设计/实现引用、环境版本（Java21、MySQL8.4.6、Node/npm、Playwright/Chromium）、非秘密构建/运行/测试命令、fresh-schema条件、三个测试各自结果、计数/行字段、禁用前后摘要及正常停机证据。真实密码/Token/完整环境/原始日志不写入文档。

为 SUCCESS计数、查询七列、EMPTY反馈和禁用后两页保存本地脱敏PNG。使用 `testInfo.outputPath(...)` 置于现有忽略的 `control-plane/node_modules/.cache/tensor-playwright` 产物树，文档记录实际相对路径及SHA-256，并说明这些是本地生成证据，重跑会变化，不声称已随Git分发。只提交下述两个文件；screenshots/trace/数据库/日志/凭证不提交。fixture数据本身为公开固定值，但分享截图前仍人工检查无秘密。失败trace继承现有配置，仅本地保留并脱敏审阅，不附到Git。

本任务失败时，区分工具链/数据库/端口/JAR前置失败、测试定位错误与产品缺陷。前置失败不能当成TDD RED，定位错误在该测试文件修正后重跑，产品缺陷则按既定任务流程记录阻塞/修复输入，绝不通过复制后端行为或放宽结果解决。

## Files

创建：

- `control-plane/e2e/fixture-flow.spec.js`：上述三个顺序真实浏览器测试及同文件内最小进程/就绪/页面辅助函数。
- `docs/verification/M14-T01-fixture-flow.md`：实际验收与失败定位证据。

实现提交精确为两个新文件，消息 `test(e2e): verify fixture user flow`。设计、看板和交接另行提交；不得修改生产实现、全局测试配置或其他任务文件。

## Tests

### 可归因负向对照

先写完整三个Playwright测试，不改产品。使用 M13-T04 的原生产JAR作为 `ACCEPTANCE_JAR`，连接另一全新隔离空schema，运行同一测试命令；应能真正启动、健康和两页200，但第一项在公开Fixture缺席处断言失败，串行后两项不执行。就绪失败/编译/浏览器缺失不是有效负向对照。该对照只证明浏览器流程依赖真实fixture，不能宣称发现生产缺陷；测试不得内置“预期失败”分支。

### 正向主门禁

按M13-T05说明取得验收包并准备新的空验收schema、三个DB变量及绝对 `ACCEPTANCE_JAR`；无需先手动启动Java，测试文件拥有应用生命周期。然后执行：

```sh
cd control-plane
npx playwright test e2e/fixture-flow.spec.js
```

预期恰3 tests passed，0 failed/skip/retry；SUCCESS1/1/0与精确查询行、EMPTY0/0/0且原行不变、disabled两页fixture缺席与Tushare摘要不变均成立，两个JVM实例正常停止。最后原smoke可在测试第三项重启后由执行器对同一实例只读复核；若已由afterAll停机，不对不存在的进程宣称smoke通过，使用测试本身已记录的health/页面/摘要结果。

再次运行该命令前必须按文档重新准备空schema，不能依赖上轮唯一行或篡改预期插入数；只在测试修订、失败调查或验收要求需要时重复，避免无目的全量重跑。

前端回归与静态门禁在测试实现完成后运行：

```sh
cd control-plane
npm run test:unit -- --run
node --check e2e/fixture-flow.spec.js
```

预期现有前端120项全部通过、JS语法通过；现有 `control-plane/vitest.config.js` 仅发现 `src/**/*.spec.js`，新e2e文件不进入Vitest；保留该边界。最后在仓库根执行 `git diff --check`、受保护生产/配置路径无差异检查，以及 `git diff --cached --name-status`/`git ls-files --stage`，实现暂存精确两新增且均100644。

## Acceptance

- 一条规定Playwright命令用真实验收JAR/真实空MySQL完成恰好三个测试，业务写入和查询全部由页面发起；无mock/直接API替代/SQL种数。
- 两页直接打开与刷新成功；SUCCESS的响应与页面1/1/0、查询唯一行七列及精度/空值/来源/显示时间、EMPTY零写入和禁用两页缺席均得到可观察证据。
- Tushare无Token摘要在禁用重启前后保持一致，fixture关闭不删除表/history；测试拥有的进程正常停止且不影响未知进程。
- 负向对照只因生产包没有fixture失败，正向3/3、前端120/120、语法/范围/格式/Git门禁通过；失败定位不越过M14只做集成的边界。
- 两文件证据与截图路径/摘要真实可追踪，无秘密和生成产物混入提交，不宣称后继场景或真实Tushare验收完成。

## Risks

- 现有fixture SUCCESS固定同一业务键，dirty schema或CI重试会改变插入计数；因此冻结fresh-schema前置及该describe retries=0，不自动删数据。
- 原生产包不含fixture/V6，不能把缺失的验收输入替换为生产包用于正向验收；负向对照独立schema，不能和验收库混用迁移历史。
- 浏览器安装和真实数据库属于外部验证条件；现有Vitest只发现src内测试，Playwright只发现e2e目录，保持两者边界。若出现范围外配置/产品问题，按任务事实记录并遵循缺陷任务流程，不默默修改生产或降级为mock。
- 临时PNG/trace/日志未进入Git，证据文档应明确保存位置/哈希及本地保管属性；安全分享由执行者脱敏检查。
- 停机观察超时不等于进程已结束；不强杀未知/尚在处理的进程，记录任务自有PID与恢复条件。
