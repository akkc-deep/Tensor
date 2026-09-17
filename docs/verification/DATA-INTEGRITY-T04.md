# DATA-INTEGRITY-T04 验收记录

日期：2026-09-16。工作区：`.worktrees/data-integrity`，分支 `feat/data-integrity`。

## 范围与执行状态

本项实现完整精确业务键比较、三条固定 core 规则、同步单元计算、规则归属问题和结果聚合。依据 `docs/task-designs/DATA-INTEGRITY-T04-design.md`。专项与完整后端回归均已实际执行，独立复审无遗留 Critical/Important 问题。

前置基线命令为设计中的 offline Maven/Mockito javaagent，选择 `IntegrityPluginContractTest,IntegrityReadRepositoryTest`，实际 42 项通过（core 合同7、读取18、fixture合同17），0失败/错误/跳过。

## 已执行的真实 MySQL 验证

Java 21.0.11，本机 Colima Docker 29.5.2，Testcontainers 实际启动 MySQL 8.4.6，未跳过集成测试。

```sh
DOCKER_HOST=unix:///Users/qiangzhiwei/.colima/default/docker.sock \
TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=/var/run/docker.sock \
mvn -o -f data-plane/pom.xml -pl tensor-core -am \
  '-Dtest=IntegrityComparisonIT,IntegrityReadRepositoryTest,IntegrityReadRepositoryIT' \
  '-DargLine=-javaagent:/Users/qiangzhiwei/.m2/repository/org/mockito/mockito-core/5.17.0/mockito-core-5.17.0.jar' \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

结果：37项通过（新增 `IntegrityComparisonIT` 7项、已有读取 unit18 + IT12），0失败/错误/跳过，BUILD SUCCESS。

| 行为 | 实际证据 |
| --- | --- |
| 合法20/19/1 | 范围Jan1..21、E为Jan1..20、A为Jan1..19加Jan21，batchSize=2；actual20/expected20/matched19/missing1/extra1、0.950000、FAIL，日期分别为Jan20/Jan21；证券表前后逐行一致。 |
| 空日期 | 日期轴不在键中，event2日期为空；不算命中、不确认缺失，保留完整键和date=null，统计比例null，单元COMPLETED但coverage UNKNOWN。 |
| 指纹与数值 | DECIMAL 1.0/1.00和Long 9007199254740993精确匹配；损坏物理指纹产生KEY/FAIL但不制造逻辑缺失；修复为现有codec生成的指纹后PASS。 |
| 累计预算 | 目标2行+参考1行+预期2项（包含重复）恰好5通过，4触发SCAN_LIMIT_EXCEEDED；覆盖率null，报告未完成，两个证券表保持不变。 |
| 读取失败 | 来源catch扫描回调异常仍为整个单元ERROR/READ_FAILED；异常原文不写入报告。 |
| 问题上限 | 第3条超过上限2，保留前2条已知FAIL，全部标incomplete，issuesComplete=false。 |
| 异常包装 | 在真实scan回调内超过问题上限且来源catch异常，最终仍为ISSUE_LIMIT_EXCEEDED，不能被READ_FAILED包装覆盖或返回PASS。 |

## 单元、fixture、回归与审查

```sh
mvn -o -f data-plane/pom.xml -pl tensor-core,tensor-plugin-fixture -am \
  '-Dtest=IntegrityComparisonTest,IntegrityCoreRulesTest,FixtureIntegrityComparisonTest,IntegrityPluginContractTest' \
  '-DargLine=-javaagent:/Users/qiangzhiwei/.m2/repository/org/mockito/mockito-core/5.17.0/mockito-core-5.17.0.jar' \
  -Dsurefire.failIfNoSpecifiedTests=false test

mvn -o -f data-plane/pom.xml \
  '-DargLine=-javaagent:/Users/qiangzhiwei/.m2/repository/org/mockito/mockito-core/5.17.0/mockito-core-5.17.0.jar' test

git diff --check
git diff --cached --check
```

专项59项通过：`IntegrityComparisonTest`22、`IntegrityCoreRulesTest`8、`FixtureIntegrityComparisonTest`5、两模块既有合同24。最终完整后端1318项通过（plugin-api88、core271、tushare461、fixture38、app460），均0失败/错误/跳过，BUILD SUCCESS。普通test不包含IT，真实MySQL由上节命令单独运行；不累加重复执行用例冒充不同测试。

- 可靠空集合为VERIFIED_EMPTY，0/0保持null；UNCONFIRMED即使候选全命中/无候选仍UNKNOWN，不判EXTRA。
- 同一来源规则独立scan确认局部缺失，同时compare整个窗口的未知集合；两种到达顺序均保留一条确认缺失及另一条疑似缺口，不重复计数，不产生整体比例。
- 精确DECIMAL/超安全整数Long在COMPOSITE和FINGERPRINT中匹配；完整财报版本键使用ann_date定位，保留end_date关联日期，不按同一天合并。非法精度/缺键/错范围/迟到非法键不留下半份差集。
- 非法实际键导致KEY/FAIL及覆盖未知，缺口只能疑似；nullable、必填字段、来源身份、规范化键碰撞独立定位；无行core规则为N/A/NO_ROWS。
- 独立规则普通异常为UNKNOWN，已知FAIL仍保留；规则按ID排序，上下文/收集器跨线程及调用结束后拒绝使用。
- 重复/无限预期键、重复参考扫描消耗同一预算；生成时、规则返回时及快照出口达到deadline都停止。目标读取未完成时actualCount=null；清理失败保留已知问题但清除单元及所有规则的正式expected/matched/coverageRate。
- 展示舍入为1.000000不会覆盖已知MISSING/FAIL。来源发出的缺口必须包含完整、正确归属的键；空键、错误股票或日期不能计入目标报告。
- 既有fixture实际执行生产比较器，可靠窗口仍只为Jan1..20；扩大到Jan21或使用UNCONFIRMED股票时保持UNKNOWN，完整本地缺失股票得到20个确认缺失及core NO_ROWS。

TDD初始RED为入口/规则尚未实现的编译失败。审查回归进一步实际观察到并修复：非法Double键导致构造问题失败；可表示的非法枚举X/Y被置null导致定位丢失；空/错归属的来源缺口被接受；快照清理失败后规则子结果仍保留正式统计。对应失败测试修复后通过。独立复审确认所有上述问题关闭，无遗留阻断项；只读/预算组件及plugin-api未修改。

本次未运行`verify-contracts.sh`：该脚本既有clean-main门禁不适用于保留混合暂存基线的隔离分支；未修改门禁或宣称其通过。本任务没有改动HTTP合同或前端。

## 边界

可靠fixture和集成测试输入是合成全集，不证明Tushare生产完整率。未实现T05持久化、T07后台调度、T08生产候选推导或HTTP/前端。检查复用T01和T03合同，没有改变指纹codec或既有下载行为。工作留在隔离分支，保留原有混合暂存基线。
