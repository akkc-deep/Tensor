# DATA-INTEGRITY-T05 报告表、原子持久化与分页查询

## Goal

将受理请求、固定计划、定义/规则快照以及每个单元的完整报告长期保存。每个单元的结果和问题原子可见；历史、结果和问题分页在相同读取快照中统计总数及取页，历史查询不依赖当前插件规则。

依据：`DATA-INTEGRITY-design.md` 第5–8节、任务看板T05、T01报告合同和现有下载任务仓库。T04已完成，但本项直接依赖仍只有T01；仓库输入使用plugin-api报告合同，不依赖T04执行器嵌套类型。2026-09-16迁移最大版本为V8，V9可用。本文为T05实施设计；实际执行证据记录在 `docs/verification/DATA-INTEGRITY-T05.md`。

## Scope

新增三张报告表、`IntegrityCheckRepository`、稳定报告JSON读写与专项测试，支持任务/计划原子创建、单元报告原子提交和三种分页查询。保存全部原请求和检查范围，不过滤本地不存在的股票。

不实现T06的参数规范化、准入/重放决策和队列，不实现T07的线程/任务状态机，不修改证券数据，不添加清理、删除、重跑或报告覆盖入口，不修改HTTP/前端。

## Approach

### 1. 表结构和数据库约束

新增 `V9__create_integrity_check_tables.sql`，InnoDB、utf8mb4、大小写敏感比较，沿用V8的中文字段注释和命名习惯。实施前再次确认版本；如V9已占用，使用下一编号并同步本设计和共享设计，不重命名已执行迁移。

| 表 | 列及约束 |
| --- | --- |
| `tensor_integrity_check_task` | `check_id CHAR(36)`主键、`submission_id CHAR(36)`唯一、`plugin_id VARCHAR(64)`、`request_hash/capability_hash CHAR(64)`；`original_request/normalized_scope/definition_snapshot JSON NOT NULL`；`status VARCHAR(24)`、`planned_units INT`；`created_at/updated_at DATETIME(3) NOT NULL`，`started_at/finished_at DATETIME(3) NULL`；`error_code VARCHAR(64)/error_message TEXT`同时为空或同时有值。 |
| `tensor_integrity_check_result` | `result_id CHAR(36)`主键、`check_id`外键、`unit_key CHAR(64)`、`plugin_id/api_name VARCHAR(64)`、`symbol VARCHAR(255) NULL`、`start_date/end_date DATE NOT NULL`、`date_field VARCHAR(64) NULL`、`definition_hash CHAR(64)`；`definition_snapshot JSON NOT NULL`、`report JSON NOT NULL`；`unit_status VARCHAR(24)`、`coverage_status/key_status/field_status/overall_status VARCHAR(24)`；`actual_count/expected_count/matched_count/missing_count/suspected_missing_count/extra_count/required_field_issue_count BIGINT NULL`；`snapshot_started_at/finished_at DATETIME(3) NULL`；`incomplete/issues_complete BOOLEAN NOT NULL`。唯一`(check_id,unit_key)`。 |
| `tensor_integrity_check_issue` | `issue_id BIGINT AUTO_INCREMENT`主键、`result_id`外键；`rule_id LONGTEXT`、`rule_version LONGTEXT`、`type/status VARCHAR(32)`、`date_field VARCHAR(64) NULL`、`issue_date DATE NULL`、`business_key JSON NOT NULL`、`field VARCHAR(64) NULL`、`related_dates JSON NOT NULL`、`reason_code LONGTEXT`、`message LONGTEXT`、`evidence JSON NOT NULL`、`incomplete BOOLEAN NOT NULL`。股票/接口通过结果表关联，不重复保存可产生不一致的归属列。 |

- task.status只允许T01的QUEUED/RUNNING/COMPLETED/FAILED/INTERRUPTED；unit_status只允许PENDING/RUNNING/COMPLETED/ERROR/NOT_RUN；四种结论列只允许PASS/FAIL/WARN/UNKNOWN/NOT_APPLICABLE；问题type取T01的完整枚举。
- planned_units非负；所有非空计数非负，matched不大于actual/expected，正式expected/matched/missing和actual/matched/extra同时存在时满足T01精确关系。start_date<=end_date。BOOLEAN只能0/1；ERROR必须incomplete；issues_complete=false必须incomplete；incomplete或非COMPLETED时expected/matched均为null。
- issue_date非空必须有date_field。business_key/related_dates及任务三份JSON、结果两份JSON为OBJECT，evidence为ARRAY。外键`ON DELETE RESTRICT ON UPDATE RESTRICT`，不级联删除。
- 索引：task `(created_at,check_id)`、`(plugin_id,status,created_at,check_id)`；result `(check_id,api_name,symbol,result_id)`及`(check_id,overall_status,result_id)`；issue `(result_id,issue_date,issue_id)`及`(result_id,type,issue_id)`。所有列表仍有最终ID排序，无动态用户排序SQL。
- symbol保留原规范值；VARCHAR(255)覆盖当前Tushare九位代码及fixture的64字符输入（含规范化后的大写值），不按数据库列截断或重写。单元去重不用symbol拼接。`unit_key`固定为UTF-8规范JSON有序数组`[apiName.value(),symbol]`的SHA-256；symbol=null写真实JSON null，不能写字符串"null"或空串，不能用`List.of`构造含null数组。此键只在check_id内唯一。
- SQL索引时间按UTC毫秒绑定，完整报告JSON保留T01的原始Instant，不截断scope/报告的时间精度；读取报告时JSON是原时点、证据和解释的权威来源。

### 2. 仓库公开接口

在 `core.integrity.IntegrityCheckRepository` 内定义小型不可变record，避免新增通用任务框架。构造器为 `(JdbcTemplate jdbc, PlatformTransactionManager transactions, IntegrityCheckJson json)`。

```java
void create(NewTask task, List<NewUnit> units);
Optional<TaskRecord> find(UUID checkId);
Optional<TaskRecord> findBySubmissionId(UUID submissionId);
Optional<ResultRecord> result(UUID checkId, UUID resultId);
void saveResult(UUID checkId, UUID resultId,
    IntegrityUnitResult result, List<NewIssue> issues);
Page<TaskRecord> tasks(TaskFilter filter, int page, int pageSize);
Page<ResultRecord> results(UUID checkId, ResultFilter filter, int page, int pageSize);
Page<IssueRecord> issues(UUID checkId, IssueFilter filter, int page, int pageSize);
```

record字段固定如下：

- `TaskScope(PluginId pluginId,List<String> symbols,LocalDate startDate,LocalDate endDate,List<ApiName> apiNames,Instant acceptedAt)`：T06传入已规范、固定、不可变范围；本项只验证结构一致，不自行重排/规范代码。
- `NewTask(UUID checkId,UUID submissionId,PluginId pluginId,Map<String,Object> originalRequest,TaskScope scope,String requestHash,String capabilityHash,List<IntegrityCheckJson.ApiSnapshot> definitionSnapshot,Instant createdAt)`：保存原请求顺序及快照；requestHash必须与`json.requestHash(originalRequest)`一致；capabilityHash来自受理校验，不用某个单元的定义哈希替换。definitionSnapshot允许保留整个插件的能力快照，所选apiNames必须是其子集；单元快照必须与其中对应API完全一致。
- `NewUnit(UUID resultId,IntegrityScope scope,IntegrityCheckJson.ApiSnapshot snapshot)`：scope.snapshotStartedAt为空；定义、可空descriptor、来源规则、coreRules及参考定义从受理快照取得。每个单元的api必须在task范围中，股票单元symbol在固定股票列表内；NON_STOCK使用null，缺描述单元保留指定symbol。scope的起止/acceptedAt和插件必须与task一致。计划中同api/symbol不得重复，planned_units取完整units.size；不扫描证券表决定计划。结构校验要求计划恰好覆盖所选股票×所选接口，NON_STOCK每接口一条null股票；不能缺单元或只保存部分范围。
- `NewIssue(String ruleId,String ruleVersion,IntegrityIssue issue)`：不依赖T04的BoundIssue，T07可按字段转换；issue_id由数据库生成。每条问题归属于传入result，规则身份必须在保存的来源/core描述中，symbol/api/dateField与结果一致。
- `TaskFilter(PluginId pluginId,IntegrityTaskStatus status,UUID submissionId)`。
- `ResultFilter(String symbol,ApiName apiName,IntegrityStatus overallStatus)`。
- `IssueFilter(UUID resultId,String symbol,ApiName apiName,IntegrityIssue.Type type,IntegrityStatus status,LocalDate dateFrom,LocalDate dateTo)`。
- `Page<T>(int page,int pageSize,long total,List<T> items)`，items防御复制。TaskRecord返回表中的身份、哈希、三份保存JSON、状态、计划数、时间/错误；ResultRecord返回身份/unitKey/定义快照和保存报告JSON；IssueRecord返回issueId/resultId/ruleId/version和保存问题JSON。JSON固定使用JsonNode；构造时和访问器返回时均deepCopy，不能泄漏可修改的内部对象。SQL列用于筛选，不重新构造或重算历史业务结论。

`find`类未命中为Optional.empty；列表未命中为空页。HTTP不存在任务映射留给T09。无效UUID/null必需参数、分页/日期、结构冲突及有长度列的超长值在SQL前拒绝为安全IllegalArgumentException（symbol按Unicode码点<=255，当前两来源均不触发此存储边界）；不把任意输入/底层异常原文写入错误信息。数据库唯一冲突保留可识别的DuplicateKeyException供T06并发重放处理；其他读写错误按现有QUERY_FAILED/PERSISTENCE_FAILED规范包装。

### 3. 原子写入

使用两个TransactionTemplate，写事务沿用现有超时常量，读事务为readOnly+REPEATABLE_READ。公开仓库方法拒绝已有Spring事务，避免调用方改变快照或把长证券读取与报告写事务混为一体。内部辅助方法不重新调用公开入口。

`create`先验证并编码所有输入，然后一次短写事务插入task及完整计划。初始task=QUEUED；每个result=PENDING，三个维度/overall均UNKNOWN、所有统计null、incomplete=true、issuesComplete=false、无快照时点/finishedAt；report使用同一T01 `IntegrityUnitResult`结构，reason为PENDING、message为“等待检查”。定义哈希由对应保存definition计算，publishedRange=null。任意计划插入失败，task和其全部单元一起回滚；重复submission_id不做UPDATE/REPLACE。幂等返回旧记录的决策由T06完成。

`saveResult`先完成输入编码，再按task行、result行的固定顺序`FOR UPDATE`。必须同时匹配checkId/resultId；只允许尚未有终态报告的计划单元（PENDING/RUNNING）写一次COMPLETED/ERROR/NOT_RUN。重复提交或跨任务写入拒绝，不删除旧问题后重写历史。验证scope原始股票/接口/日期/acceptedAt和definitionHash/descriptor均与计划快照一致，仅快照时点可由null变为执行时点；规则版本/名称来自计划快照，不能用最新插件元数据替换。

在同一事务按输入稳定顺序插入全部问题，然后更新完整report、索引状态/统计/时点/flags列；失败时一起回滚，旧计划仍可读、不得留下半份明细。UNIT ERROR报告允许保存已知FAIL与未完成问题，但所有issue.incomplete=true，正式expected/matched/rate均null；不得保存没有对应已知问题的“已发现下界”。任务终态/进度调度由T07增加，不在此任务启动或结束线程。

仓库不调用catalog、PluginRegistry或来源客户端查询当前口径，也不访问证券表。没有自动删除、报告覆盖、恢复旧快照或孤立问题清理逻辑。

### 4. 持久JSON与历史读取

扩展既有 `IntegrityCheckJson`，保持现有`write`/requestHash/definitionHash/capabilityHash字节合同不变：新增`unitKey(ApiName,String)`、存储文档的`writeDocument(Object)`/`readDocument(String)`及原始JSON树的`readValue(String)`，文档外壳固定`{"schemaVersion":1,"payload":...}`。payload固定为对象或数组且只接受既有精确编码支持的结构；readDocument返回校验后的payload树，readValue供问题键/关联日期/证据读取并使用相同严格解析与敏感字段检查。读端严格校验外壳恰有schemaVersion/payload、版本为1、JSON深度<=32及重复字段；坏JSON/未知版本作为查询失败，不能以空对象继续。

任务/结果文档用外壳保存；issue的business_key/related_dates/evidence为现有无外壳结构，与数据库JSON_TYPE约束一致，组合完整问题时同样保持原值。规则ID、版本、原因码和说明未在T01设短文本上限，使用LONGTEXT保存，不新增截断规则。Long/BigDecimal仍为十进制字符串，null不变，LocalDate/Instant为ISO；完整键值根据已保存文档展示，不按当前定义转型或由物理哈希逆推。只在生成报告时调用T01派生overall/rate，读取时返回保存的这些字段。

输入JSON继续拒绝T01敏感字段名和Float/Double等不精确值；存储字段只保存规范输入、最小问题定位及安全证据，不接收任意异常对象、原始响应或堆栈。JSON读取仅解析已保存内容，不调用`IntegrityStatus.aggregate`、compare或当前规则。

### 5. 三种列表查询

统一page>=1、pageSize范围1..100，缺省在调用层设1/20；仓库仍防御校验，offset以long乘法计算，禁止int溢出。每次列表在一个只读RR事务内先COUNT再SELECT，两个SQL用同一WHERE和参数，超末页返回原total+空items。不先取一页再用Java筛选。

| 列表 | 过滤条件（非null时AND相连） | 稳定顺序 |
| --- | --- | --- |
| tasks | plugin_id、status、submission_id精确相等 | created_at DESC,check_id DESC |
| results | 固定check_id；symbol、api_name、overall_status精确相等 | api_name ASC,symbol ASC,result_id ASC（MySQL null symbol先于非null） |
| issues | JOIN result并固定check_id；result_id、symbol、api_name、type、status；issue_date>=dateFrom、<=dateTo | issue_date IS NULL ASC,issue_date ASC,issue_id ASC |

issue日期边界各自可省略；两者均有时dateFrom<=dateTo。任何日期过滤自然排除NULL日期；不加日期过滤时未知日期在最后。resultId过滤必须仍受checkId限制，不泄漏其他报告。用户值全部绑定，表、列和排序片段固定。

历史/详情中的汇总由T07/T09消费已保存结果和任务状态，不在本项增加跨接口总覆盖率。股票值完整保存和过滤；VARCHAR按完整值排序，并以result_id兜底，验收覆盖共同长前缀的不同fixture symbol。

## Files

| 路径 | 责任 |
| --- | --- |
| `data-plane/tensor-app/src/main/resources/db/migration/V9__create_integrity_check_tables.sql` | 三表、约束、索引、中文注释 |
| `data-plane/tensor-core/src/main/java/com/akkc/tensor/core/integrity/IntegrityCheckRepository.java` | 本文嵌套record、原子创建/提交、查找和三类分页 |
| `data-plane/tensor-core/src/main/java/com/akkc/tensor/core/integrity/IntegrityCheckJson.java` | 兼容性不变的现有编码，新增unit_key和持久文档读写 |
| `data-plane/tensor-core/src/test/java/com/akkc/tensor/core/integrity/IntegrityCheckRepositoryIT.java` | 实际MySQL事务、约束、并发读取和历史验证 |
| `data-plane/tensor-core/src/test/java/com/akkc/tensor/core/integrity/IntegrityCheckJsonTest.java` | 精确JSON、稳定unit_key、未知版本/敏感值拒绝 |
| `docs/verification/DATA-INTEGRITY-T05.md` | 实施后的真实验收证据；不预填通过 |

为接入V9，同时维护 `tensor-app/src/test` 中 FlywaySchemaContractIT、DividendBusinessKeyMigrationIT、FixtureFlowIT、DownloadControllerIT、DownloadTaskLifecycleIT、ProductionApplicationContextIT 和 PackagedJarContractTest 的最新迁移清单/数量；旧迁移校验及证券数据断言保留；同步 `docs/runbook/configuration.md` 的当前迁移/表库存与报告保留说明。真实浏览器回归发现 `control-plane/e2e/download-task-lifecycle.spec.js` 的三个定位已落后于既有Studio组件，按现有可访问链接和错误容器修正测试，并按 Studio 看板已确认的仅PC范围将旧下载生命周期测试的390px调整为1024px，保留无溢出及所有业务断言，不修改产品前端；新完整性页面的后续窄屏验收要求不变。

仅上述任务范围；不修改下载任务仓库、T01 plugin-api或证券数据迁移。新增文件加入Git，保留当前隔离工作区已有暂存改动。

## Tests

先编写真实MySQL创建计划事务测试，观察缺少仓库/迁移的失败，再实现。IT复用现有Testcontainers MySQL8.4.6方式，实际加载新迁移，不能skip后宣称通过。

1. submission_id、(check_id,unit_key)冲突；NON_STOCK null symbol的unit_key稳定且与字符串"null"不同；同计划单元不能重复。直接非法SQL验证外键、全部状态/非负计数/日期/JSON/flags约束生效。
2. 创建第2个计划单元时注入数据库错误，验证无task及第1个孤立单元；并发相同submissionId最终只有一份完整任务，另一调用获唯一冲突。
3. 提交过程中另一连接看不到任何新问题或终态；提交后结果与所有问题同时出现；在问题插入及结果更新分别注入故障，验证回滚到原计划且无孤立问题。重复终态提交、错checkId/resultId、原范围/定义/规则变更均被拒绝。
4. 保存FAIL、UNKNOWN、全N/A、ERROR/incomplete、NOT_RUN及null计数；Long9007199254740993、DECIMAL1.000000000000000001、原始时点/范围、规则名称/版本、完整键和relatedDates读回不变。修改当前定义/来源规则后历史读回仍相同，禁止重算。
5. 三类列表逐一验证全部过滤、组合条件、同时间/同日期分页稳定、null日期末尾、日期过滤排除null及清除过滤恢复；不同checkId隔离；页大小1/20/100、101/0拒绝、反向日期拒绝、超末页total不变且items为空、大page不溢出。合法fixture symbol包含共同长前缀时仍正确排序。
6. 在COUNT和SELECT之间用另一连接插入/提交，当前列表total/items仍属旧快照，新查询见新值；三类列表都实际验证，不能以同一mock替代。
7. JSON Long字符串/null/日期完整往返，unit_key按独立已知JSON SHA-256核对；不改变现有T01能力/定义哈希。拒绝重复JSON键/未知版本/深度超限、敏感字段和不精确数值，安全异常不泄漏输入。
8. 证券表测试前后逐行一致，仓库不调用来源客户端。所有查询/创建入口拒绝已有外部事务，避免被外部隔离级别或回滚影响合同。

```sh
mvn -o -f data-plane/pom.xml -pl tensor-core,tensor-plugin-fixture -am \
  '-Dtest=IntegrityCheckJsonTest,IntegrityPluginContractTest' \
  '-DargLine=-javaagent:/Users/qiangzhiwei/.m2/repository/org/mockito/mockito-core/5.17.0/mockito-core-5.17.0.jar' \
  -Dsurefire.failIfNoSpecifiedTests=false test

DOCKER_HOST=unix:///Users/qiangzhiwei/.colima/default/docker.sock \
TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=/var/run/docker.sock \
mvn -o -f data-plane/pom.xml -pl tensor-core -am \
  '-Dtest=IntegrityCheckRepositoryIT,IntegrityComparisonIT,IntegrityReadRepositoryIT' \
  '-DargLine=-javaagent:/Users/qiangzhiwei/.m2/repository/org/mockito/mockito-core/5.17.0/mockito-core-5.17.0.jar' \
  -Dsurefire.failIfNoSpecifiedTests=false test

mvn -o -f data-plane/pom.xml \
  '-DargLine=-javaagent:/Users/qiangzhiwei/.m2/repository/org/mockito/mockito-core/5.17.0/mockito-core-5.17.0.jar' test

git diff --check
git diff --cached --check
```

预期测试实际执行、0失败/错误/跳过、BUILD SUCCESS；记录实际数量，不把普通test排除的IT计作执行。保留clean-main合同脚本既有门禁，不在隔离分支伪称通过。

## Acceptance

三张表和真实约束生效；受理计划全有或全无，单元报告和明细全有或全无。原请求、范围、定义/规则、证据、时点、精确计数和未完成标记完整读回；当前元数据变更不改写历史。三个列表覆盖共享设计第8节全部参数、稳定顺序、同事务总数/页数据及null日期语义。实际MySQL和后端回归通过并写入验收记录，新增文件加入Git。

## Risks

- V9必须在实施时再次检查，不能与其他分支迁移冲突。
- JSON是历史报告权威副本，冗余SQL筛选列必须由同一个结果对象一次生成并同事务保存；不得由调用方单独更新其中一套。
- T05只提供存储边界，不能据此宣称后台任务/幂等准入/API已完成；它们分别由T06/T07/T09实施。
- 报告长期保留，单次问题数量由执行/受理配置约束，首版不自动删除。宽报告和长字符串的容量问题通过实际测试记录，不截断用户范围或问题键。
- 无待用户决定的产品范围问题；持久化实现不能引入新的来源覆盖结论。
