# RANGE-T10 两表失败存储验证

本记录对应[区间下载看板](../task-handoffs/tensor-range/tensor-range-task-board.md)的 `RANGE-T10`，依据为[详细设计](../task-designs/RANGE-T10-design.md)。执行日期为2026-09-09；实现、规定验证、真实MySQL测试及独立规格／质量／集成评审均已完成。

## 实施结果

- 唯一 V8 新增 `tensor_download_task`、`tensor_download_task_item`。JSON 对象约束、五列联合主键、RESTRICT 外键、UTC DATETIME(3) 和列表索引与设计一致；49 个业务定义及旧迁移保留。
- Core 增加严格 JSON 编解码、失败仓储及存储服务，App 装配三个 bean。首次主表／首明细同事务；追加与重复原因更新锁定主表，按完整 key 操作并同步更新时间。创建后不更新公共参数和原始日期。
- 创建在事务内回读 MySQL 原生 JSON 并递归比较语义，拒绝有效数字损失；合法嵌套对象／数组／null 可保存。Java 编解码支持精确数字不代表 MySQL 任意精度支持。
- SavedFailure 仅在事务执行正常返回后产生；保存 SQL／事务异常安全返回 TASK_RECORD_SAVE_UNCONFIRMED，无重试或补记。读取使用独立只读 REPEATABLE_READ 快照，按双 DESC 分页、批量读取全部子项。
- 六个公开仓储原语加入调用者绑定的同一事务，提供锁定、完整 key 检查／删除及主表收尾；本项没有业务 Upsert 与删除的组合服务。

## 验证过程

1. 先写 `RetryTaskStorageIT.firstItemFailureRollsBackHeaderAndPreservesPreviouslySavedTask` 并执行设计 App IT 命令。因三种新 Core 类型缺失而编译失败；这是接口尚未实现的编译证据，不是运行回滚反例。
2. Core 的 JSON／服务单测先于实现创建；首次缺类型编译失败，另修正测试泛型编译问题。实现后 JSON 测试通过，服务测试遇 Mockito 沙箱附加限制；沿用授权的沙箱外执行后15项通过。
3. App IT 首轮已编译，但 Testcontainers 默认 `/var/run/docker.sock` 不存在，四类均启动错误，没有数据库行为证据。只读 Docker context 核对实际使用 Colima；后续命令为测试进程设置下述两个环境变量，未改依赖、跳过测试或修改全局配置。
4. 配置后四类 App MySQL IT 共73项通过。独立评审补齐三处既有集成测试的 V8 计数、完整 key 隔离／顺序和正确保存值断言。之后自检添加的连接检查误用受保护 Spring API，完整 Core 编译暴露该问题；修正及最终重跑结果在下表记录。
5. 三个受V8影响的既有App集成测试实际回归：FixtureFlowIT 5项、DownloadControllerIT 10项通过；ProductionApplicationContextIT首次受本机TENSOR_TUSHARE_TOKEN覆盖测试默认值影响，其无凭证断言失败。仅在测试子进程用 `env -u TENSOR_TUSHARE_TOKEN` 移除该变量后单独重跑，1项通过；无生产代码更改或真实来源调用。对应三份最终XML全部零失败／错误／跳过。
6. 独立集成评审要求严格拒绝缩写／大写保存 UUID，以免与区分大小写的数据库主键错配；同时统一设计规定的非法输入异常。修正后的验证结果以最终记录为准。

数据库测试环境：Docker 29.5.2、`mysql:8.4.6`，独立 Testcontainers schema、utf8mb4／utf8mb4_0900_as_cs。仅隔离测试库清理；没有访问或清理用户业务数据库。命令从仓库根执行，Maven 使用沙箱外执行以支持 Mockito 与 Docker。

```sh
export DOCKER_HOST=unix:///Users/qiangzhiwei/.colima/default/docker.sock
export TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=/var/run/docker.sock
```

临时日志及 clean 前 XML 副本位于 `/tmp/tensor-range-t10/`。下表为本轮最终通过结果，不沿用前驱证据。

| 命令／检查 | 最终结果 |
|---|---|
| `mvn -f data-plane/pom.xml -pl tensor-core -am test` | 退出0；plugin-api 99＋Core 190，共289项，零失败／错误／跳过 |
| `mvn -f data-plane/pom.xml -pl tensor-app -am -Dtest=RetryTaskStorageIT,DownloadFailureMigrationIT,FlywaySchemaContractIT,DividendBusinessKeyMigrationIT -Dsurefire.failIfNoSpecifiedTests=false test` | 退出0；最终73项通过（13＋2＋53＋5），零失败／错误／跳过；62秒 |
| `mvn -f data-plane/pom.xml -pl tensor-core -am -Dtest=PersistenceServiceIT,ExistingKeyRepositoryIT -Dsurefire.failIfNoSpecifiedTests=false test` | 退出0；8＋8共16项通过，零失败／错误／跳过；19.787秒 |
| `mvn -f data-plane/pom.xml verify` | 退出0；741项单测＋4项生产JAR合同＝745项Java检查，170项前端测试及构建通过；38.482秒 |
| `mvn -f data-plane/pom.xml -Pacceptance clean verify` | 退出0；741项单测＋4项生产JAR＋3项acceptance合同＝748项Java检查，170项前端测试及构建通过；44.565秒 |
| `PYTHONDONTWRITEBYTECODE=1 python3 docs/contracts/verify_range_contract.py` | 退出0；8组合同＋4个变异反例通过 |
| `git diff --check`、`git diff --cached --check`、资源摘要／原暂存检查 | 两条差异检查退出0；57份摘要及任务外原暂存基线复核通过，详见下文 |

## 实际 MySQL 证据

下列六份最终 Surefire XML 均已逐一读取，tests>0，failures／errors／skipped均为0，并在acceptance clean前复制到临时证据目录。

| 测试类 | 实际数量 | 主要结果 |
|---|---:|---|
| RetryTaskStorageIT | 13 | 首次一主一子、首次与追加／重复原因／update后半段失败回滚；完整五字段key及同日两股精确更新／删除；固定安全原因和原参数／日期保留 |
| DownloadFailureMigrationIT | 2 | 生产七迁移／51表无fixture；V7→V8仅一次，完整stock_basic／dividend业务行、业务键、来源时间及旧Flyway历史不变，validate及重复迁移通过 |
| FlywaySchemaContractIT | 53 | 49业务定义＋fixture＋两管理表，八迁移／52表／1022列／52主键／42非主键索引；两管理表精确列、JSON、CHECK、FK、collation和索引通过 |
| DividendBusinessKeyMigrationIT | 5 | V6→V7仍固定target7；旧行指纹、业务值／来源值、分红阶段和V7故障用例通过，新库全量八迁移 |
| PersistenceServiceIT | 8 | 原业务持久化、SQL分组事务及数据集锁回归通过 |
| ExistingKeyRepositoryIT | 8 | 原业务键查询、类型与SQL条件回归通过 |

存储13项还覆盖：真实JSON对象绑定与回读、整数／小数／缩放小数／嵌套null；两类及嵌套有损数字拒绝且两表无新记录，超过uint64但实际无损值允许；JSON根、联合PK及FK／RESTRICT约束；仓储同连接外层回滚、锁直到提交／回滚且其他任务不受阻；非UTC JVM与+08:00会话仍保存相同UTC毫秒；23项双DESC分页、20／50／100与空／超尾页、独立筛选及完整子项顺序；COUNT后并发写入仍保持同一快照；真实T05转换器的REQUEST／STOCK公共条件与旧日期读取；重建服务读取已提交记录、未提交主表不可见；损坏标识／选择器／原因及非规范UUID安全拒绝。

## 评审

独立 App 静态评审和独立全量规格／质量／集成评审已完成。初审发现非规范保存UUID和null预校验异常差异，修正并补测试后定向复审PASS；连接检查改用受支持的Spring API，当前16项新Core单测、289项Core reactor和最终73项App MySQL均对应修正后的代码。无遗留实质问题；评审没有代替执行验证或将未完成收尾计为通过。

## 行为与验收边界

本轮确认 AC-PRD-RANGE-21／22／26 的存储部分；89项规定MySQL检查与16项追加应用回归均有实际证据。业务数据＋明细删除原子提交、首次／重试编排、HTTP 与页面仍由 T11 及后续任务实现。没有真实来源调用或浏览器验收；生产日历和完整批次来源注册表、49项 REQUEST 策略没有升级，ISSUE-008 仍“不依赖，未解决”。

## 文件与工作区核对

旧生产迁移6份、49份Dataset YAML、2份生产策略共57份SHA-256与实施前完全相等；分支仍为 `feat/date-range-download`，HEAD不变。720份任务外已有文件内容保持原暂存基线；执行期间另有ISSUE-017文档与issue索引改动，本项保留原样且未操作其暂存状态；收尾复核时另一路工作已将这三份issue文件暂存。V8所需的三处额外App IT计数修正保留其已有暂存内容，只增加本项差异。

规定六份MySQL XML在acceptance clean前已验证和另存；clean后生产／acceptance打包检查通过，不能用打包结果代替MySQL测试。本项新增代码／测试／验证文件显式加入Git，不提交／发布。最终差异、设计和交接链接检查在看板完成记录中注明。
