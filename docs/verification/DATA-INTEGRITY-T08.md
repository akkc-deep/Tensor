# DATA-INTEGRITY-T08 验收记录

日期：2026-09-17。工作区 `.worktrees/data-integrity`，分支 `feat/data-integrity`。实施依据 `docs/task-designs/DATA-INTEGRITY-T08-design.md`。保留已有 Studio/T01–T07 混合暂存基线；不合回外部工作区。

## 基线与反例

- `/tmp/tensor-t08-baseline.log`：107 项（core 合同 7、Tushare 策略 100），BUILD SUCCESS，0 失败/错误/跳过。
- `/tmp/tensor-t08-app-red.log`：真实 MySQL 8.4.6、无 Token 生产应用，已受理的三行情接口完成且为 UNKNOWN；新增参考缺损断言失败，旧占位规则的问题列表为空。此为观察到的 RED，不计作通过。

## 真实装配验证

`/tmp/tensor-t08-mysql.log`：BUILD SUCCESS，30 项真实 MySQL 测试（ReadRepositoryIT 12、RunnerIT 17、ProductionApplicationContextIT 1），0 失败/错误/跳过。使用 Colima/Testcontainers MySQL 8.4.6，Ryuk 正常开启。同一 Maven 生命周期中前端 609 项测试和生产构建通过。

生产应用无 Token：先检查空参考，三接口均保留 UNKNOWN/REFERENCE_INCOMPLETE；再建立 2024-02-01 至 03-03 的完整 SSE 受控日历，仅 02-27/28 开市，原请求为 02-26..29。三张目标表各有 02-27 行；三个报告各产生一个 02-28 的 SUSPECTED_MISSING/WARN，完整键为股票+交易日、版本2，覆盖结论 UNKNOWN、expectedCount/matchedCount/coverageRate=null，不产生 MISSING/EXTRA。盘中停牌不移除候选，周/月扩大参考窗口而保持原请求。全部六张证券表内容前后一致，补充参考后旧空参考报告仍保留原统计。

## 最终专项与回归

- 规则首个 RED：weekly 完整周历中周五闭市，2024-03-28 周四为最后开市；旧@1占位规则没有调用 compare，`expected` 为 null 导致预期失败。实现后该用例通过。
- 行情专项14项、策略98项、core插件合同7项，合计119项通过，0失败/错误/跳过。策略覆盖全部40接口、仅三规则/能力升2、每项降级都会改变完整hash及旧@1描述JSON保留。规则覆盖日历异常、ISO跨年/闰月/部分周期、上海快照当日、上市日期、停牌线索、BJ无替代日历、无网络和扫描/比较/问题预算异常传播。
- `/tmp/tensor-t08-unit-final.log`：默认 `mvn test` BUILD SUCCESS，1411项（plugin-api88、core334、tushare474、fixture38、app477），0失败/错误/跳过。前端609项和生产构建同样通过。
- 最终流式停牌线索代码下，`/tmp/tensor-t08-final-green.log` 的 ProductionApplicationContextIT 已实际通过1项，0失败/错误/跳过。此组合命令整体失败原因见下。独立最终 `/tmp/tensor-t08-app-final.log` 已BUILD SUCCESS，实际MySQL8.4.6的ProductionApplicationContextIT 1项通过，0失败/错误/跳过；使用 `-Dskip.npm=true` 复用此前已通过的前端产物，未跳过Java测试。

## 审查与修正

独立审查规格PASS、质量APPROVE，无遗留问题。重复日历反例改为精确断言仅保留03-25/27/28，避免空集或重复日历误生候选漏检。全量回归发现既有 `TushareProPluginTest` 公开方法白名单遗漏了已有接口的新覆盖 `integrityReferenceReads`；最小补齐后该13项专项和最终回归均通过，未改公共插件合同。

一次组合命令 `-Dtest=*Test,ProductionApplicationContextIT` 覆盖了POM默认排除项，错误选入打包阶段的AcceptancePackagedJarContractTest；其3项因 `test` 阶段未生成验收JAR失败，不能算通过。随后按仓库默认生命周期完成全量单元回归；没有修改打包门禁或用skip测试冒充通过。T08未运行 `clean verify`/要求clean main的完整合同脚本，不代替T13最终发布验收。

## 验证命令

```sh
mvn -o -f data-plane/pom.xml -pl tensor-plugin-tushare -am \
  '-Dtest=TushareIntegrityRulesTest,TushareIntegrityPoliciesTest,IntegrityPluginContractTest' \
  '-DargLine=-javaagent:/Users/qiangzhiwei/.m2/repository/org/mockito/mockito-core/5.17.0/mockito-core-5.17.0.jar' \
  -Dsurefire.failIfNoSpecifiedTests=false test

DOCKER_HOST=unix:///Users/qiangzhiwei/.colima/default/docker.sock \
TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=/var/run/docker.sock \
mvn -o -f data-plane/pom.xml \
  '-Dtest=ProductionApplicationContextIT,IntegrityCheckRunnerIT,IntegrityReadRepositoryIT' \
  '-DargLine=-javaagent:/Users/qiangzhiwei/.m2/repository/org/mockito/mockito-core/5.17.0/mockito-core-5.17.0.jar' \
  -Dsurefire.failIfNoSpecifiedTests=false test

mvn -o -f data-plane/pom.xml \
  '-DargLine=-javaagent:/Users/qiangzhiwei/.m2/repository/org/mockito/mockito-core/5.17.0/mockito-core-5.17.0.jar' test
```

## 结果边界

日历完整不等于可靠生产全集；BJ适用日历、历史生命周期、全天停牌全集、来源服务边界与发布时间仍不可证明。规则只给候选差集及可定位的UNKNOWN原因，不新增正式覆盖率、上游调用或证券写入。参考读取和问题仍受核心预算约束，超限原异常交给核心保存ERROR/incomplete。

T08已按实测结果记为COMPLETED；实现留在原隔离区，新文件加入Git，原有暂存基线保留。后继按既定Order选择T09，在本项完成后准备其设计和交接，不启动T09实现。


后继 `docs/task-designs/DATA-INTEGRITY-T09-design.md` 已完成并回填，`docs/task-handoffs/DATA-INTEGRITY-T09-handoff.md` 已链接；T09为READY，未实施。
