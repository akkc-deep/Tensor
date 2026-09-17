# DATA-INTEGRITY-T13 真实 fixture 闭环、回归与文档验收

## Goal

用受控真实 MySQL、acceptance JAR 和浏览器证明完整本地检查流程、SQL 可复算、只读证券表、规则扩展和历史可追溯，完成共享设计十项结果的证据闭环。

## Scope

扩展 acceptance 专用 fixture，新增真实后端浏览器套件和自有资源启动器；重新执行已有完整性 IT、前端及下载/数据查看/打包回归，更新运维文档并检查公开合同一致性。现有 `e2e/integrity-checks.spec.js` 保持 API stub 套件，真实闭环使用独立 `integrity-fixture.spec.js`，两类结果分别记录。不调用生产上游、不引入生产可信基线、自动补数、调度、导出或报告清理，不改变 core/前端来源分支。

T12 已记录 COMPLETED，740 项前端单测、39 项 Chromium stub 与构建通过；这些是可用输入，不能代替本任务真实数据库结果。本设计只准备 T13；用户明确启动后才实施。

## Approach

### 1. 固定验收数据与规则版本

现有 `FixtureIntegrityRules` 仅对 `PROVEN` 且范围位于 2026-01-01..20 时承诺可靠全集。不能把范围扩大到21日后仍宣称可靠，也不能在20个完整日期键内构造第21个不同键。因此先扩展 acceptance fixture 的明确合成场景：

| symbol | 可证明范围 | 预期集合 | 初始实际集合 | 预期覆盖结果 |
| --- | --- | --- | --- | --- |
| PROVEN_EXTRA | 2026-01-01..21 内的请求 | 1..20日与请求范围的交集 | 1..19日及21日 | 请求1..21时actual20/expected20/matched19/missing1/extra1，rate0.950000，FAIL |
| PROVEN | 原有2026-01-01..20内的请求 | 原有1..20日与请求范围交集 | 无行 | actual0/expected20/missing20，rate0.000000，FAIL；完整保留本地不存在股票 |
| PROVEN_EMPTY | 2026-01-01..21内的请求 | 可靠空集合 | 无行 | expected0/actual0，rate=null，VERIFIED_EMPTY，不做0/0 |
| UNCONFIRMED | 沿用原实现 | 1..20日候选集合 | 无行 | expected/rate=null，UNKNOWN；候选缺口不能伪装确认缺失 |

范围超出各场景可靠窗口时继续 UNCONFIRMED。所有证据必须注明 synthetic/acceptance，不能称为 Tushare 基线。`PROVEN` 和 `UNCONFIRMED` 原行为保留。

覆盖规则 `fixture.coverage.fixture_daily` 与对应 descriptor 能力版本升为 `2`，因为新增可证明场景改变能力语义；相应合同测试更新预期。新增仅 acceptance 可见的配置 `tensor.plugins.fixture.integrity-version`，允许 `2`（默认）或 `3`，其他值启动失败。`FixtureConfiguration` 注入到 `FixturePlugin` 的小型构造器重载，再传给 `FixtureIntegrityRules`；旧构造器委托版本2。版本3覆盖集合不变，但 descriptor/rule/evidence 的版本统一为3，并注册一条独立 `fixture.acceptance.extension@1` FIELD 规则（无依赖、无必需列），返回 PASS、`FIXTURE_EXTENSION_VERIFIED` 和明确“验收扩展规则已执行”的合成证据。该规则只证明可选规则注册与通用报告链路，不声称验证新的生产业务约束。

版本3的 rule descriptor 必须进入 capability 与 ruleResults，哈希变化；每个 STOCK_DATE 接口仍恰好一条 COVERAGE。新增规则及注册只改 fixture 模块，core、HTTP、前端不新增规则ID/来源ID条件分支。生产包仍不包含 fixture 或 acceptance 配置。

证券行按现有 V6 表结构插入：`ts_code, trade_date, amount, note, source_plugin, source_api, ingested_at`，amount固定精确 `1.230000000000000000`，note可为null，source固定fixture/fixture_daily，ingested_at固定 `2026-01-22T00:00:00Z`。不新增迁移、不改变业务键。建数据、修改数据和清理属于验收准备，不算检查自身写证券表。

### 2. 真实 Java/HTTP 验收

新增 `data-plane/tensor-app/src/test/java/com/akkc/tensor/fixture/IntegrityFixtureFlowIT.java`，采用现有 `FixtureFlowIT` 的 MySQL8.4.6、acceptance配置与9次迁移，实际装配 HTTP 及完整性服务。每场景独立数据，关闭自己创建的应用上下文/容器；环境失败要失败，不使用 disabledWithoutDocker 或 skip 冒充通过。

首个行为 RED：合法 PROVEN_EXTRA 1..21请求在现有规则下不能得到可靠20/19/1。测试先断言真实完成报告rate=`0.950000`、overallStatus=FAIL、missing Jan20和extra Jan21；观察现有 UNKNOWN/null 的行为失败，再实现 fixture 扩展。不是以类导入或编译失败代替该RED。

通过真实六端点断言：无Token能力可读取；POST202及Location、同ID同载荷200原checkId、不同载荷409；完成但FAIL；保存scope/descriptor/version/evidence；results及issues的完整键与日期筛选；整只无数据股票仍存在，列出20个缺失日；PROVEN_EMPTY与UNCONFIRMED分别保持上述语义。字符串Long和null遵循现有DTO，不直接依赖JavaScript Number。

版本验收先以2生成报告A，记录其响应、数据库report_json及证券行摘要；停止自己的应用后用3连接同一库重新启动。GET A内容不变，旧submissionId原载荷仍找回A；旧capabilityHash的新ID请求409，刷新能力后的新请求生成B，B显示coverage@3及extension@1，A仍为@2且无extension。同一数据库证券行不变。若报告含可变读取requestId，只比较业务载荷，不把每次请求标识差异误判为历史重写。

故障、预算与快照不重复另造框架：显式运行既有 `IntegrityReadRepositoryIT`、`IntegrityComparisonIT`、`IntegrityCheckRepositoryIT`、`IntegrityCheckServiceIT`、`IntegrityCheckRunnerIT`、`IntegrityCheckCoordinatorTest`，逐项登记真实执行的测试名。必要缺口才补对应测试文件；不靠缓存旧报告或旧测试数量宣称本次通过。

### 3. SQL复算与只读核对

真实浏览器与Java测试均独立计算集合，不用报告计数互相证明。对PROVEN_EXTRA1..21，使用MySQL8递归CTE构造1..20预期日，LEFT JOIN完整键(ts_code,trade_date)：

```sql
WITH RECURSIVE expected(trade_date) AS (
  SELECT DATE('2026-01-01')
  UNION ALL SELECT DATE_ADD(trade_date, INTERVAL 1 DAY)
  FROM expected WHERE trade_date < DATE('2026-01-20')
)
SELECT COUNT(*) AS expected_count, COUNT(a.trade_date) AS matched_count,
       COUNT(*) - COUNT(a.trade_date) AS missing_count,
       ROUND(COUNT(a.trade_date) / COUNT(*), 6) AS coverage_rate
FROM expected e LEFT JOIN fixture__fixture_daily a
  ON a.ts_code='PROVEN_EXTRA' AND a.trade_date=e.trade_date;
```

单独查询actual为同symbol且日期1..21的COUNT，extra为该范围实际键对预期CTE反连接。列出缺失/额外完整键，预期分别Jan20/Jan21；不得用 `actual/expected` 得到假100%。本地PROVEN无行同样计算20个缺失键。

每次“点击开始→终态”前后，对fixture表全部业务/来源/入库字段按主键排序、显式列序输出UTF-8并做SHA256与逐行比较，须完全相同；Tushare证券/参考表由既有真实HTTP IT做同样核对。报告三表新增记录允许。报告与问题原子性使用既有真实MySQL触发器故障测试，不能仅看最终列表的空值。

### 4. 浏览器闭环及自有环境

新增 `control-plane/e2e/integrity-fixture.spec.js`，复用现有真实fixture套件的**行为约束**，不复制其全部无关测试：只启动自有acceptance JVM、要求8080空闲、绑定127.0.0.1、等待health UP、记录安全日志、正常SIGTERM并等待退出，不杀其他PID。任何缺少JAR/数据库、端口占用或启动失败均显式失败。

套件消费 `ACCEPTANCE_JAR`、`TENSOR_DB_URL/USERNAME/PASSWORD`、`M14_DB_SCHEMA`、`M14_MYSQL_DEFAULTS_FILE`。仅允许loopback JDBC和 `tensor_integrity_t13_[0-9a-f]+` schema；校验defaults文件属于当前用户、0600且非符号链接，mysql使用argv/stdin，不在命令行拼密码。沿用其他SQL套件的mysql输出精确字符串策略。记录JAR的SHA256、版本、schema的安全别名，不持久化凭据或完整连接配置。

新增 `scripts/verify-integrity-fixture.py` 负责可重复环境：

1. `--acceptance-jar`必须是实际绝对路径；仅启动自有 `mysql:8.4.6` 容器，loopback动态端口、随机schema后缀与随机凭据，env/defaults文件置唯一临时目录且0600。无外部数据库参数，不接入生产库。
2. 该容器创建五个全新schema：T13使用上述前缀；旧四套分别使用 `packaged-test-environment.js` 已定义的 download-outcomes、dataset-query、tushare-metadata、fixture-flow 前缀。同一临时应用账号只授权这些自有schema，应用权限沿用当前迁移要求，不提供全局权限。
3. 为旧四套生成现有格式的四项env JSON（字段/前缀/0600契约不变），设置 `ISSUE_017_ACCEPTANCE_JAR_SHA256` 为本次JAR实际SHA256、`PLAYWRIGHT_BASE_URL=http://127.0.0.1:8080`，T13使用自己的直传环境；不修改 `configurePackagedEnvironment` 使旧契约失效。
4. 串行运行T13真实套件及这四个已有打包套件，每次JVM自行退出后才开始下一套，任一失败汇总非零退出；保留脱敏报告/截图路径。异常时终止并等待自己的子进程，删除自己的容器/临时凭据；不清理全局Docker资源或用户工作区。

真实浏览器步骤：

- 版本2启动，建上述数据，页面选择fixture、PROVEN_EXTRA及Jan1..21，核对日期口径/默认全选，明确确认后仅一次POST。
- 报告终态显示“计算已完成”和“有问题”，rate95%；进入问题，主日期Jan20及完整键准确；SQL返回20/19/1/1，与报告和问题一致。刷新及历史入口仍可读取。
- 通过验收SQL将Jan21行的trade_date更新为Jan20（一次UPDATE，同时补齐缺失并移除额外键，无需DELETE权限；明确在检查间修改），点击“再次检查”，复制原范围、确认默认false；确认后得到新submissionId/checkId，SQL与新报告100%/PASS一致，旧报告仍95%/FAIL。两个检查各自前后证券内容不变。
- PROVEN空表、PROVEN_EMPTY、UNCONFIRMED按上表验证；未知不能显示完整。无Token，浏览器请求必须是本机应用；检查期间上游测试接收器计数为0。Java真实HTTP测试同样使用受控上游计数器，不能仅靠浏览器零外网来断言JVM没有网络调用。
- 正常停止版本2，版本3重启相同库：旧报告仍@2，新报告@3且通用“保存的检查依据”显示新extension名称/ID/证据；前端无新规则组件。产生新请求前重新获取hash/确认，保留原范围。
- 至少1440完成上述闭环；1024/390刷新同一真实报告并键盘定位问题、确认无document溢出。全部UNKNOWN/N/A、超长值、中断、提交丢失由现有39项stub及对应真实Java边界共同覆盖，不能把stub标成真实场景。

现有下载/查询打包用例仍有V8时代的schema断言：`download-outcomes.spec.js` 的迁移串/52表，以及 `dataset-query.spec.js` 的首尾迁移串、52表和证据对象。T13须基于实际V9迁移最小同步为1..9全部成功、55张业务表（Flyway history另计），保留原证券行数与数据查询断言；这属于真实回归所需的版本更新，不降低门禁。不要将152个财报业务列等无关数值替换。

实施实测补充：四套既有打包回归保留了Studio改版前的“数据下载”等标题/导航定位，而本次JAR静态资产与当前dist逐字节一致，实际标题为“下载工作台”。允许依据真实失败最小同步这四套的过时展示定位（包括 `tushare-metadata.spec.js`、`fixture-flow.spec.js`）；保留全部下载/HTTP/SQL/网络边界断言，不修改生产前端来迁就旧测试。

### 5. 十项结果与完整边界证据

`docs/verification/DATA-INTEGRITY-T13.md` 用下表逐项填写本次命令、测试名、结果与证据文件；没有实测值的位置保持“未验证”，不得将设计期预期写成通过。

| 共享“如何验证” | 本项必须记录的证据 |
| --- | --- |
| 1 原范围/清单/版本/读取时点 | 新真实HTTP IT、浏览器报告A、保存JSON字段核对 |
| 2 缺失具体键与可复算数量 | PROVEN_EXTRA的SQL集合、Jan20/21问题、95%实图 |
| 3 依据不足null且完成不代表完整 | UNCONFIRMED真实报告及Tushare真实HTTP UNKNOWN；COMPLETED+FAIL实图 |
| 4 无Token/无上游/证券不变 | Java上游计数0、浏览器本机请求、证券全字段前后摘要与行比较 |
| 5 不适用/不支持仍保留 | 既有Runner混合7单元/HTTP40接口、NON_STOCK/缺描述结果、stub N/A |
| 6 扩展只改来源可选能力/规则 | fixture@3新增规则的diff范围检查、真实新规则结果；core/UI无规则ID分支 |
| 7 前端全流程及刷新 | 真实浏览器从创建到具体问题/历史/再次检查，不stub完整性端点 |
| 8 新旧版本各自保存 | 版本2/3相同数据库重启的A/B响应、保存JSON和浏览器依据 |
| 9 40接口正确日期轴与未知限制 | TushareIntegrityPoliciesTest与manifest40成员一致；三行情规则及NON_STOCK测试 |
| 10 部分执行/统计/明细与FAIL共存 | Runner/Repository真实故障与问题限额IT、Clock预算测试、对应中断stub实图 |

还需逐行映射共享“如何测试”15条边界：可靠空/未知/局部依据、行情边界与参考不足、财报完整版本键与日期轴、nullable/必填区别、多页/并发一致快照、旧插件及重复/停用、前端原范围、整只无数据股票、股票/天数/单元/扫描/问题限额等于及超一、空主日期与relatedDates、规则异常与独立FAIL、幂等重放/冲突/升级、扩展规则。对应现有Integrity/Tushare测试不能删除或降低断言。逐条注明具体测试方法，执行后统计本次更新的Surefire报告，IT确实连接MySQL且skip=0。

### 6. 文档和集成门禁

更新 `docs/runbook/configuration.md`：删除“HTTP/页面由后续任务接入”的过时表述，写明入口、六端点、队列/预算/停机、报告长期保留和重启中断；保留现有54/55表与迁移事实，除非实测证据改变。更新 `docs/runbook/data-integrity-rules.md`：明确首版本地检查已实现，与在线对账、定时检查等后续规范分开；解释FAIL/UNKNOWN/N/A、95%集合口径及fixture边界。更新共享设计末尾“当前只交付设计”等过时实施状态，保留功能合同及十项验收定义，并链接T13证据。

公开OpenAPI/schema/examples/error-codes只在实际发现不一致时做最小同步；不扩API。最终合同脚本 `scripts/verify-contracts.sh` 须最小同步V9对应的Flyway测试方法名和证据统计（生产54表/8次迁移，验收55表/9次迁移/1110列/55主索引/56非主索引）；其main/已提交干净受保护输入要求保持原样：它归档HEAD，当前混合暂存隔离区不能通过。不得为本任务提交整个混合基线、伪造main分支或绕过检查。只有用户已授权的干净集成main包含全部待验收输入后，才在那里执行该门禁，并将提交SHA与T13被测代码一致性记入证据。

缺少该集成前提时，完成其他验证、记录具体缺口并按看板写pause/blocker；T13及项目不得标COMPLETED，亦不回滚T12已完成状态。此为已知最终验收前提，不妨碍在隔离区开始fixture实现。

## Files

- 修改 `data-plane/tensor-plugin-fixture/src/main/java/com/akkc/tensor/plugin/fixture/integrity/FixtureIntegrityRules.java`、`FixturePlugin.java`、`FixtureConfiguration.java`：场景、版本2/3及acceptance注册；新增同integrity目录 `FixtureIntegrityExtensionRule.java`。
- 扩展 fixture 的 `integrity/FixtureIntegrityComparisonTest.java`、`IntegrityPluginContractTest.java`、`FixturePluginTest.java`：场景可靠性、版本哈希、规则一致性、非法配置与原场景兼容。
- 新增 app `src/test/java/com/akkc/tensor/fixture/IntegrityFixtureFlowIT.java`：真实MySQL/HTTP及版本历史验证；只在有具体覆盖缺口时补现有Integrity专项测试。
- 新增 `control-plane/e2e/integrity-fixture.spec.js`、`integrity-fixture.helpers.js`：真实UI/SQL及自有JVM生命周期；`integrity-checks.spec.js`保留stub回归，不伪造真实端点。
- 修改 `control-plane/e2e/download-outcomes.spec.js`、`dataset-query.spec.js`：同步V9后9次迁移/55表；四套既有回归（另含 `tushare-metadata.spec.js`、`fixture-flow.spec.js`）仅按真实失败同步过时Studio展示定位，保留业务断言。
- 新增 `scripts/verify-integrity-fixture.py`：专用MySQL/五独立schema、临时凭据、串行真实浏览器回归与自有资源清理。
- 修改上述三份runbook/共享设计文档；必要时同步既有公开合同，不新增迁移。
- 新增 `docs/verification/DATA-INTEGRITY-T13.md` 与 `docs/verification/data-integrity-t13/` 脱敏JSON/SQL摘要/截图；回填看板。测试日志可临时存放，但最终数字和关键证据须持久保存。

## Tests

Java21、Node24.15.0、MySQL8.4命令行客户端与受控Docker/MySQL；现有Colima连接方式如下。工具路径为本机已知值，换机器应验证等价版本，不能悄悄降版本。

```sh
export PATH=/Users/qiangzhiwei/.nvm/versions/node/v24.15.0/bin:$PATH
export DOCKER_HOST=unix:///Users/qiangzhiwei/.colima/default/docker.sock
export TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=/var/run/docker.sock
java -version
node --version

mvn -o -f data-plane/pom.xml \
  '-Dtest=*Integrity*Test,*Integrity*IT,FixturePluginTest,FixtureFlowIT,ProductionApplicationContextIT' \
  '-DargLine=-javaagent:/Users/qiangzhiwei/.m2/repository/org/mockito/mockito-core/5.17.0/mockito-core-5.17.0.jar' \
  -Dsurefire.failIfNoSpecifiedTests=false test
npm --prefix control-plane test
npm --prefix control-plane run build
# 启动仅本机Vite后，保持API stub单独统计：
PLAYWRIGHT_BASE_URL=http://127.0.0.1:4178 npm --prefix control-plane run test:e2e -- e2e/integrity-checks.spec.js

mvn -o -f data-plane/pom.xml -Pacceptance \
  '-DargLine=-javaagent:/Users/qiangzhiwei/.m2/repository/org/mockito/mockito-core/5.17.0/mockito-core-5.17.0.jar' clean verify
python3 scripts/verify-integrity-fixture.py \
  --acceptance-jar "$PWD/data-plane/tensor-app/target/acceptance/tensor-app-1.0-SNAPSHOT-acceptance.jar"
# 生产profile构建/回归独立执行：
mvn -o -f data-plane/pom.xml \
  '-DargLine=-javaagent:/Users/qiangzhiwei/.m2/repository/org/mockito/mockito-core/5.17.0/mockito-core-5.17.0.jar' clean verify
# 仅在上节已授权、代码一致的clean committed main中：
sh scripts/verify-contracts.sh
```

JAR basename以 `data-plane/pom.xml` 的实际project.version及tensor-app finalName校验；若不是上例1.0-SNAPSHOT，启动器必须接收构建实际产物的绝对路径，不复制旧JAR冒充新构建。离线缺依赖为环境失败，记录后在允许的联网环境补齐再执行；退出0仍要检查IT测试方法、数量、失败/错误/跳过均符合要求，不能仅用默认Maven是否选中IT判断。

预期：上述unit/IT/browser/构建/生产打包/合同命令全部通过，无失败/错误/skip；真实95%及改数后的100%与SQL吻合、旧报告不变；全部自有进程/容器清理结果可核对。既有大chunk或故障注入日志按证据说明，不降低业务断言，也不扩大为无关重构。

## Acceptance

十项结果及全部测试边界有本次可追溯证据；真实浏览器完成建数据、创建、缺失键定位、SQL复算、再次检查和版本升级前后历史验证。只读证券边界、无上游、精确值/未知、原范围与规则扩展保持。既有下载、数据查看、打包、公开合同及三宽度页面回归通过；所有新增文件加入Git。任何真实IT跳过或clean-main门禁未执行/失败都不能标本任务和项目完成。

## Risks

Tushare生产全集依然无法证明，不要求本任务解决；fixture合成95%不能用于生产覆盖承诺。当前分支混合暂存基线不可直接提交或合并，最终合同门禁需独立已授权集成前提；没有该前提时按上节保留其他证据并阻塞最终验收。JAR、数据库、截图和SQL必须来自同一轮受控运行，不能混用旧产物。所有环境资源仅清理本次所有者，凭据不得进入Git或审查输出。上述依赖约束无冲突，实施结构和结果口径无待选的材料缺口。
