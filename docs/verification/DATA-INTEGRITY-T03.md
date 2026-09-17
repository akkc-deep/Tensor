# DATA-INTEGRITY-T03 一致快照与受限只读扫描验收

日期：2026-09-16。工作区：`.worktrees/data-integrity`，分支：`feat/data-integrity`。范围依据 `docs/task-designs/DATA-INTEGRITY-T03-design.md`；保留既有 Studio/T01/T02 暂存内容。

## 实现

新增 `IntegrityReadRepository`、`IntegrityReadPlan`、`IntegrityReadBudget` 和 `IntegrityReadException`。每个单元独占一个 MySQL REPEATABLE_READ / READ ONLY 事务，使用完整物理键游标；目标、空日期和参考扫描共享快照与累计预算。调用者通过 `ReadSession` 读取，插件合同不增加 JDBC 或任意 SQL 接口。T04 可委托 `scan`，T07 在报告写事务外装配并调用 `withSnapshot`。

目标查询强制固定股票和原日期范围，参考查询仅接受同来源、已声明用途与投影列，并注入股票/交易所条件。日历仅允许原范围、完整 ISO 周或自然月边界；历史扩展不影响原目标范围。空日期通过独立 `TargetBatch(true, rows)` 交付，不进入范围内批次。

LONG、DECIMAL、DATE、UTC Instant 和 nullable null 精确保留。达到行数上限允许，下一行触限；各次目标/参考/空日期读取与后续预期键生成使用同一预算；首次超限被锁存，捕获异常后仍不能成功。SQL 准备及参数绑定完成后重新读取剩余时间，超时取整秒下界，不足一秒不再启动语句。读取/回调失败使会话失效，上层吞掉异常也无法返回成功；连接最终回滚、恢复原设置并释放。

## 专项验证

Java 21；本机 Docker 29.5.2；Testcontainers 实际启动 `mysql:8.4.6`。运行命令：

```sh
DOCKER_HOST=unix:///Users/qiangzhiwei/.colima/default/docker.sock \
TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=/var/run/docker.sock \
mvn -o -f data-plane/pom.xml -pl tensor-core -am \
  '-Dtest=IntegrityReadRepositoryTest,IntegrityReadRepositoryIT' \
  '-DargLine=-javaagent:/Users/qiangzhiwei/.m2/repository/org/mockito/mockito-core/5.17.0/mockito-core-5.17.0.jar' \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

结果：30 项通过（18 unit + 12 真实 MySQL IT），0 失败、0 错误、0 跳过，BUILD SUCCESS。

| 验收行为 | 实际证据 |
| --- | --- |
| 一致快照与完整游标 | batchSize=2，5 行复合键跨三批；首批后另一连接提交插入/删除/更新，后续页、重复扫描、首次参考读取均见旧值，新会话见新值。另在 action 首次读取前提交新行，当前快照仍不见该行。 |
| FINGERPRINT 物理键 | 五个物理键顺序与逻辑 event 顺序不同，投影省略物理键，仍逐条一次；空表、整批、尾批正常结束。 |
| 授权 | 非法投影、未声明数据集/用途、跨股票、越日期、覆盖固定交易所、错误类型/精度在证券 SELECT 前拒绝，跟踪计数为零。股票参考不越股票/窗口，日历周/月扩展保持原目标范围。 |
| 日期无法归属 | 当前股票两行有效日期、一行空日期分别交付；其他股票空日期及区间外记录被排除。快照表保留历史日期字段，不按该字段错误过滤。 |
| 精确值与不可变结果 | Long `9007199254740993`、Decimal `1.000000000000000001`、LocalDate、UTC 时间和 null 无损；批次、行及读取记录不可修改。 |
| 累计预算 | 恰好 3 行成功，第 4 行失败；目标+参考、目标+空日期、重复扫描及预期键消费合计均触限。 |
| 时间边界 | 实际 PreparedStatement 的超时不超过剩余预算；批间只剩 0.5 秒不启动下一查询；action 返回时到 deadline 仍失败。 |
| 资源与中断 | 读取/回调异常即使被 action 捕获也不能成功；真实连接回滚、恢复、关闭。逃逸、异线程、递归扫描及已有 Spring 事务拒绝。 |
| 证券表不变 | 检查前后逐行相等。只读事务调用试写存储过程，MySQL 返回错误码 1792；并发写入测试中的外部修改另行验证。 |

另用真实 MySQL 将物理列改为 DECIMAL(38,19)，catalog 声明仍为 DECIMAL(38,18)：需舍入的值被安全拒绝且不交付，只有多余零的值无损归一化。此回归先实际观察到错误成功（RED），修复后通过。单测覆盖跨来源、直接共享预算异常被捕获、prepare/绑定耗时、输出长度/枚举/精度、空回调和跨多周期最小外扩。

初始 RED 为读取类型尚不存在时的编译失败；首轮真实 IT 揭示中间 `List.copyOf` 拒绝 null，修复为允许 null 的不可变列表并新增 unit 回归，完整专项复验通过。

## 回归与审查

基线 `IntegrityPluginContractTest` 7 项通过。最终完整后端回归命令：

```sh
mvn -o -f data-plane/pom.xml \
  '-DargLine=-javaagent:/Users/qiangzhiwei/.m2/repository/org/mockito/mockito-core/5.17.0/mockito-core-5.17.0.jar' test
git diff --check
git diff --cached --check
```

结果：1283 项通过（plugin-api 88、core 241、tushare 461、fixture 33、app 460），0 失败、0 错误、0 跳过，BUILD SUCCESS。真实 MySQL IT 另由上方显式命令运行，不计入普通 test 的 1283 项。暂存和未暂存差异空白检查通过。

独立审查首轮提出异源引用、共享预算终止状态、SQL 准备耗时、输出合同校验四项问题；均已修复并增加回归。补充修正跨线程状态可见性、空 callback 终止和跨多周期最小参考外扩。最终复审 Critical 0 / Important 0，无遗留阻断项。

## 边界

本项只交付读取组件；尚未实现 T04 集合比较、T07 执行器或 T08 来源候选规则。真实 MySQL 测试使用合成数据，不证明 Tushare 生产数据覆盖完整。默认 batchSize=500、maxItems=500000、单元120秒由后续配置/装配传入；本组件验证显式参数且执行其预算。未合并原工作区，未提交混合暂存基线。
