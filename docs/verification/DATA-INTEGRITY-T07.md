# DATA-INTEGRITY-T07 验收记录

日期：2026-09-16。隔离区 `.worktrees/data-integrity`，分支 `feat/data-integrity`。按 `docs/task-designs/DATA-INTEGRITY-T07-design.md` 实施；原有 244 个暂存文件保留，不创建混合提交或合回原工作区。

## 已实现范围

- 独立单线程检查协调器与应用生命周期：先中断旧 QUEUED/RUNNING，再开放首次受理；关闭先等在途受理完成，协作取消活动单元，等待实际退出并中断剩余队列。旧请求始终可重放。
- Runner 稳定分页遍历固定计划，每单元再次核验完整能力与保存的 API 快照。缺描述、NON_STOCK、定义变更及插件不可用按保存口径生成无扫描报告。
- 来源仅提议参考窗口；同来源、声明依赖/投影/用途和唯一许可经 Planner 校验，再由 T03 读仓库独立执行最终授权；core 没有 Tushare 分支。
- 每单元复用同一只读快照和 T04 规则执行，扫描/生成预期键累计计费，问题上限和单元/任务 deadline 生效。异常或取消保留已知问题，正式覆盖统计清空。
- 证券读取 READ_FAILED 是单元 ERROR，其他单元继续；任务计划/报告读取或写入故障为任务 FAILED。停止/超时/重启为 INTERRUPTED，剩余 NOT_RUN。进度只按已提交结果统计。
- 仓库增加原子状态转换、终止、重启恢复和 RR 进度查询；保留旧终态和读取时点，严格历史 scope/descriptor 解码；错误不携带原异常内容。

## 已执行证据

基线 80 项专项全部通过；首个 RunnerIT 在缺少 Runner/progress 接口时编译失败，随后实施。边界测试先确认缺少 Planner、取消重载及门禁接口；仓库反例和独立审查问题按 RED→GREEN 修复。

`/tmp/tensor-t07-lifecycle.log`：RunnerIT 17、CoordinatorTest 5、ConfigurationTest 1、ProductionApplicationContextIT 1 全部通过，BUILD SUCCESS，0 失败/错误/跳过。真实 Testcontainers MySQL 8.4.6；生产应用分别以无 Token/有 Token 启动既有 40 接口，原下载应用图继续可用。

最终验证均 BUILD SUCCESS，0 失败/错误/跳过：

| 范围 | 结果 | 执行日志 |
| --- | --- | --- |
| 完整后端单元回归 | 1399 项：plugin-api 88、core 334、tushare 462、fixture 38、app 477 | `/tmp/tensor-t07-unit-final.log` |
| 真实 MySQL 完整性组 | 71 项：Runner 17、Service 8、Repository 27、Comparison 7、ReadRepository 12 | `/tmp/tensor-t07-mysql-final.log` |
| 生命周期及真实应用启动 | Coordinator 5、配置 1、生产应用启动 1；同一命令另含 RunnerIT 17，均通过 | `/tmp/tensor-t07-lifecycle.log` |

日志为本机临时执行记录，本文件记录持久验收结论。预期故障注入会输出固定安全 ERROR；Maven 的既有编码、SLF4J、Mockito CDS 与 Flyway 版本提示不影响测试结果。真实应用故意停 MySQL 验证 health DOWN，连接池会记录连接断开日志；T07 自身的异常日志不携带原异常、SQL、凭据或堆栈。未运行要求 clean main 的合同脚本，也不声称完成 T13 的最终发布门禁。

## 验证命令

```sh
mvn -o -f data-plane/pom.xml \
  '-DargLine=-javaagent:/Users/qiangzhiwei/.m2/repository/org/mockito/mockito-core/5.17.0/mockito-core-5.17.0.jar' test

DOCKER_HOST=unix:///Users/qiangzhiwei/.colima/default/docker.sock \
TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=/var/run/docker.sock \
mvn -o -f data-plane/pom.xml -pl tensor-core -am \
  '-Dtest=IntegrityCheckRunnerIT,IntegrityCheckServiceIT,IntegrityCheckRepositoryIT,IntegrityComparisonIT,IntegrityReadRepositoryIT' \
  '-DargLine=-javaagent:/Users/qiangzhiwei/.m2/repository/org/mockito/mockito-core/5.17.0/mockito-core-5.17.0.jar' \
  -Dsurefire.failIfNoSpecifiedTests=false test

DOCKER_HOST=unix:///Users/qiangzhiwei/.colima/default/docker.sock \
TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=/var/run/docker.sock \
mvn -o -f data-plane/pom.xml \
  '-Dtest=IntegrityCheckCoordinatorTest,IntegrityCheckConfigurationTest,IntegrityCheckRunnerIT,ProductionApplicationContextIT' \
  '-DargLine=-javaagent:/Users/qiangzhiwei/.m2/repository/org/mockito/mockito-core/5.17.0/mockito-core-5.17.0.jar' \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

Docker/socket 和应用测试端口需沙箱外执行。首次受限 Docker 连接及未设置 socket override 的环境失败不算通过；最终使用设计中的 Colima 配置、Ryuk 开启。测试不访问上游服务、不需要真实 Token。

## 关键结果

- 混合 7 单元计划（股票/快照/缺描述/NON_STOCK）完整保留；101 单元跨页也逐项完成。确定缺失使数据 FAIL，同时任务可以 COMPLETED；证券内容前后一致。
- 单规则异常保留 UNKNOWN/RULE_EXECUTION_FAILED，独立覆盖规则保留 MISSING/FAIL；ruleId 稳定排序。目标与参考同一快照，另一个连接插入的数据到下一单元才可见。
- 累计读取/预期键精确等于上限允许，超一项单元 ERROR。问题上限保留限额内已知缺失并标 incomplete；正式覆盖率 null。
- 注入 Clock 验证单元 deadline、任务 deadline、排队时间不计费、协作取消和 Instant 加法饱和。任务截止后先保存已知问题，再将剩余置 NOT_RUN。
- 真实 MySQL 问题插入触发器故障使报告和问题一起回滚，任务 FAILED；终态写入也失败时保持数据库可证 RUNNING，由后续恢复中断，绝不假报已保存。
- 启动恢复覆盖未 publish 的 QUEUED、部分提交的 RUNNING 和已有 snapshotStartedAt 的单元。关闭与 create/publish 竞争时等待受理锁；活动快照未退出前 stop 回调不完成。

## 审查与边界

独立审查分仓库/JSON与Runner/生命周期两个范围，最终核对全部 T07 跨组件行为，规格和质量均批准，无遗留 Critical/Important 项：

- 截止时间在最后检查和预算构造之间越界：两项反例先得到 FAILED/INTERNAL_ERROR，修复后保存单元超时 ERROR，再按任务 deadline 决定继续或 INTERRUPTED。原 Budget 过期构造器的兼容行为保留。
- 仅篡改报告 JSON 的 definitionHash：反例原先静默覆盖，修复后 mapResult 验证 JSON/SQL 一致性，查询和终止统一 QUERY_FAILED，事务回滚且旧历史原样保留。

新增文件加入 Git；验收完成后按看板 Order 准备 T08，T07 实现保持在隔离区。

协作取消不能强杀不调用上下文边界的第三方无限循环；数据库不可用也可能阻止终态保存，下一进程启动仍负责中断遗留。没有自动恢复旧快照或补数。Tushare 日周月候选、HTTP 和前端仍由 T08 以后实施；fixture 证据不代表生产覆盖证明。

## 后继准备

先记录T07 COMPLETED，再按Order选择T08；`docs/task-designs/DATA-INTEGRITY-T08-design.md` 完成并回填，通过独立可实施性复查。看板同步明确三条旧@1规则/能力升级为2并补齐T07实际接口依赖，随后写入 `docs/task-handoffs/DATA-INTEGRITY-T08-handoff.md`，T08为READY。未实施或宣称T08测试通过。
