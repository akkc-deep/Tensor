# DATA-INTEGRITY-T06 验收记录

日期：2026-09-16。工作区：`.worktrees/data-integrity`；分支：`feat/data-integrity`。依据 `docs/task-designs/DATA-INTEGRITY-T06-design.md` 实现；保留 Studio/T01–T05 混合暂存基线，不创建混合提交或合回原工作区。

## 结果

新增 `IntegrityCheckService` 与 `IntegrityCheckQueue`：先按原请求重放，首次提交才验证能力和范围；股票规范去重、接口默认全量、完整能力快照与选中范围分别保存。股票级和缺描述接口逐股票计划，NON_STOCK 每接口一条，未执行报告保持 PENDING/UNKNOWN。预留名额后原子持久化完整计划，提交后入队；唯一键竞争释放名额后查询赢家，响应读取失败可原请求重放。

已装配十项 `tensor.integrity` 配置，正数及 workers=1 在启动时强制校验；Tushare 日期使用受理时刻对应上海日。新增四项安全错误码和 HTTP 状态映射，共享错误 schema 枚举同步；未新增完整性 HTTP 路由、执行线程或证券读写。执行预算本次仅配置绑定，T07 才接入运行。

## 实际验证

Java 21，本机 Colima Docker，真实 Testcontainers MySQL 8.4.6。以下最终命令均 BUILD SUCCESS，无失败/错误/跳过。

| 验证 | 实际结果 | 日志 |
| --- | --- | --- |
| 前置合同/JSON | 33项通过 | `/tmp/tensor-t06-baseline.log` |
| 完整后端单元 | 1385项通过：plugin-api88、core320、tushare462、fixture38、app477 | `/tmp/tensor-t06-unit-verified.log` |
| Maven 同时运行前端单元 | 37文件、609项通过，前端构建通过 | 同上 |
| 真实MySQL受理与存储 | ServiceIT8 + RepositoryIT21，共29项通过 | `/tmp/tensor-t06-mysql-final.log` |
| 真实应用启动/MySQL | ProductionApplicationContextIT 1项通过；无Token及有Token两个应用上下文，40项本地能力与原下载服务正常 | `/tmp/tensor-t06-app-mysql.log` |

```sh
mvn -o -f data-plane/pom.xml \
  '-DargLine=-javaagent:/Users/qiangzhiwei/.m2/repository/org/mockito/mockito-core/5.17.0/mockito-core-5.17.0.jar' test

DOCKER_HOST=unix:///Users/qiangzhiwei/.colima/default/docker.sock \
TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=/var/run/docker.sock \
mvn -o -f data-plane/pom.xml -pl tensor-core -am \
  '-Dtest=IntegrityCheckServiceIT,IntegrityCheckRepositoryIT' \
  '-DargLine=-javaagent:/Users/qiangzhiwei/.m2/repository/org/mockito/mockito-core/5.17.0/mockito-core-5.17.0.jar' \
  -Dsurefire.failIfNoSpecifiedTests=false test

# 使用相同 Docker 环境与 javaagent，验证实际 Spring 应用装配
mvn -o -f data-plane/pom.xml '-Dtest=ProductionApplicationContextIT' \
  '-DargLine=-javaagent:/Users/qiangzhiwei/.m2/repository/org/mockito/mockito-core/5.17.0/mockito-core-5.17.0.jar' \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

Docker与WireMock/Spring本机端口需要沙箱外授权执行；首次受限运行的环境失败不算通过。IT没有skip，未使用H2或模拟事务代替MySQL。

## 验收证据

- 同ID原请求在插件不可用、规则升级、新增API及满队列时返回旧任务，不再次创建或发布；对象键换序可重放，股票数组次序/大小写/空白与省略或显式接口列表的原始差异发生冲突。
- 严格UUID、字符串数组、ISO日期/实际日历日、数据库日期边界、正向范围和未知字段均受校验；显式null/空apiNames拒绝。源校验异常不暴露输入。上海午夜前后的受理日使用固定Instant验证，client无交互。
- 规范化按首次顺序去重，子集使用全插件能力hash，计划按catalog顺序固定。混合2股票×3股票接口+1非股票接口得到7单元；完整快照含4接口，引用定义去重排序且不增加计划单元。快照和原请求防御复制，入队前snapshotStartedAt为空。
- 股票/自然日/单元计数各自等于配置上限允许、超一项拒绝，不调用create、不删减范围。默认配置全部绑定，自定义十项值完整传递；每个0/负数和workers=2实际阻止Spring上下文启动。
- 队列20名额并发40次恰好20成功；未发布不可见，关闭释放一次，发布后关闭不释放，重复发布拒绝；成功消费才释放，超时/中断不增减未消费名额。容量1同时publish/close/poll竞争后仍只有一个可用名额。
- MySQL12个并发相同提交只有一任务、7个单元和一次入队；不同原请求同ID一胜一冲突。两个独立service共用数据库/队列，用barrier强制撞唯一键，分别验证同请求重放和异请求冲突、无泄漏名额。
- MySQL第二单元触发故障后整任务/第一单元回滚，队列可再次接受同请求；容量3并发8个不同ID恰好3成功、5个被拒ID无记录。测试库根本没有证券表，合法未下载股票仍被完整受理。
- create返回时独立数据库连接已能读到完整计划，而队列仍为空；随后才publish。提交后读取返回值失败仍保留任务和单一队列项，原请求重放成功。无赢家的唯一冲突安全返回PERSISTENCE_FAILED。
- 真实应用加载V9并装配检查服务/仓库/队列；无Token可获得40项描述和稳定64位hash。T06配置本身没有SmartLifecycle，不访问数据库、不运行检查；下载协调器仍正常运行。

## TDD、审查与修正

初始RED为受理/队列入口缺失；Tushare晚于受理日测试实际观察到错误放行，再补上上海日校验。独立审查发现报告编码器把Long/BigDecimal转成字符串，可能导致错误类型的股票数组碰撞旧请求；新增测试实际复现错误重放，随后在原请求复制阶段拒绝这些非法数值类型，保留既有JSON格式。复查和最终专项审查无遗留问题。

测试工具修正：LocalPlugin补齐旧readiness接口；Mockito重新设定失败使用doThrow；既有快照读回为数组，按T05真实结构断言；Spring配置验证采用真实JdbcTemplate+模拟DataSource，避免把正常afterPropertiesSet当数据库访问。均没有放宽业务验收。

完整回归指出冻结错误码白名单和公共schema枚举必须覆盖新ErrorCode；追加四项并保留旧枚举顺序，同步`PluginApiSurfaceTest`、`download-task.schema.json`、`openapi-v1.yaml`及错误码说明。此为既有合同兼容调整，完整性路由仍归T09。

静态检查 `git diff --check`、`git diff --cached --check` 均通过，新增文件已加入Git暂存；原工作区 `git status --short` 为空。

## 边界

本地队列仅单实例，数据库提交到内存发布之间的进程崩溃窗口由T07启动中断处理；本项不保证崩溃后自动执行。扫描/问题/时间预算尚未运行，缺描述和NON_STOCK终态由T07生成。真实测试是合成数据，不证明生产Tushare覆盖。

未运行`verify-contracts.sh`：其既有clean-main门禁与本隔离区混合暂存基线不兼容；未修改门禁或宣称通过。公共错误schema由完整后端合同测试实际验证。未新增完整性页面或进行浏览器端到端验收。

## 后继准备

先记录T06 COMPLETED，再按Order选择T07。`docs/task-designs/DATA-INTEGRITY-T07-design.md`已回填并完成独立可实施性复查，明确启动/关闭门禁、扫描ERROR与报告仓库FAILED分层、恢复保留已有snapshotStartedAt；`docs/task-handoffs/DATA-INTEGRITY-T07-handoff.md`已链接，T07为READY，未开始实现。
