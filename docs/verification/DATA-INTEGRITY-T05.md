# DATA-INTEGRITY-T05 验收记录

日期：2026-09-16。工作区：`.worktrees/data-integrity`，分支 `feat/data-integrity`。

## 结果与边界

依据 `docs/task-designs/DATA-INTEGRITY-T05-design.md` 完成 V9 三张报告表、原子创建完整计划、一次性保存单元及问题、历史/结果/问题分页和精确持久 JSON。独立最终复审通过，无遗留阻断问题。保留原工作区与本工作区已有混合暂存基线，新增文件加入 Git，不合并原分支、不创建混合基线提交。

T05只交付存储边界；幂等受理/队列、后台执行、HTTP和完整性页面分别由后续任务实施。本次真实MySQL使用合成数据，不证明Tushare生产数据覆盖。

## 验证结果

| 验证 | 实际结果 |
| --- | --- |
| 前置合同基线 | core/fixture的IntegrityPluginContractTest共24项，0失败/错误/跳过。 |
| 完整后端单元 | 1344项通过：plugin-api88、core297、tushare461、fixture38、app460；全部0失败/错误/跳过。 |
| 最终完整性专项 | 66项通过：IntegrityCheckRepositoryIT21、IntegrityReadRepositoryIT12、IntegrityComparisonIT7、IntegrityCheckJsonTest26，0失败/错误/跳过，BUILD SUCCESS。JSON26同时包含在后端单元中，不重复计数。 |
| 受影响应用MySQL回归 | 分组执行73个不同用例通过：FlywaySchemaContractIT47、DividendBusinessKeyMigrationIT5、FixtureFlowIT5、DownloadControllerIT10、ProductionApplicationContextIT1、DownloadTaskLifecycleIT5。浏览器/迁移工具修正及一次既有时序竞争的复跑见下文。 |
| 生产打包 | PackagedJarContractTest4项通过，生产JAR包含V9且不带fixture；最终verify为BUILD SUCCESS。 |
| 静态检查 | git diff --check及git diff --cached --check通过。 |

使用本机Colima、Testcontainers实际启动MySQL8.4.6，IT没有skip。Docker和本机端口访问通过授权执行通道运行。

```sh
# 完整后端单元：/tmp/tensor-t05-full.log；最终代码再次运行的单元结果见下文
mvn -o -f data-plane/pom.xml \
  '-DargLine=-javaagent:/Users/qiangzhiwei/.m2/repository/org/mockito/mockito-core/5.17.0/mockito-core-5.17.0.jar' test

# 最终专项：/tmp/t05-repository-verified.log
DOCKER_HOST=unix:///Users/qiangzhiwei/.colima/default/docker.sock \
TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=/var/run/docker.sock \
mvn -o -f data-plane/pom.xml -pl tensor-core -am \
  '-Dtest=IntegrityCheckRepositoryIT,IntegrityReadRepositoryIT,IntegrityComparisonIT,IntegrityCheckJsonTest' \
  '-DargLine=-javaagent:/Users/qiangzhiwei/.m2/repository/org/mockito/mockito-core/5.17.0/mockito-core-5.17.0.jar' \
  -Dsurefire.failIfNoSpecifiedTests=false test

# 受影响应用测试，保留同一Docker环境及javaagent参数
mvn -o -f data-plane/pom.xml \
  '-Dtest=FlywaySchemaContractIT,DividendBusinessKeyMigrationIT,FixtureFlowIT,DownloadControllerIT,DownloadTaskLifecycleIT,ProductionApplicationContextIT' \
  '-DargLine=-javaagent:/Users/qiangzhiwei/.m2/repository/org/mockito/mockito-core/5.17.0/mockito-core-5.17.0.jar' \
  -Dsurefire.failIfNoSpecifiedTests=false verify
```

应用命令首跑 `/tmp/tensor-t05-app-verify.log` 中，fixture5、download controller10、production context1、dividend5通过；Flyway测试工具和旧浏览器定位失败。修正后 `/tmp/tensor-t05-app-final.log` 中Flyway47通过，下载生命周期的4项通过；最后一个浏览器方法继续按下面的实际证据复验，不把失败的整条命令记为成功。

最终代码的完整单元复跑命令为 `mvn -o -f data-plane/pom.xml '-Dtest=*Test,!PackagedJarContractTest,!AcceptancePackagedJarContractTest,DownloadTaskLifecycleIT' '-DargLine=-javaagent:/Users/qiangzhiwei/.m2/repository/org/mockito/mockito-core/5.17.0/mockito-core-5.17.0.jar' -Dsurefire.failIfNoSpecifiedTests=false verify`，日志 `/tmp/tensor-t05-final-verify.log`：1344项单元全部通过，附加浏览器用例的390px断言失败，因此这次整体BUILD FAILURE。

最后使用上述Docker环境运行 `mvn -o -f data-plane/pom.xml '-Dtest=DownloadTaskLifecycleIT#browserDisconnectAndRetryUseTheRealApplication' '-DargLine=-javaagent:/Users/qiangzhiwei/.m2/repository/org/mockito/mockito-core/5.17.0/mockito-core-5.17.0.jar' -Dsurefire.failIfNoSpecifiedTests=false verify`，日志 `/tmp/tensor-t05-browser-package.log`：1项真实浏览器Java闭环（内部2项Playwright流程）与4项生产JAR检查均通过，0失败/错误/跳过，BUILD SUCCESS。

## 持久化与查询证据

- 三表唯一键、外键、全部状态/计数关系、日期、JSON形状与flags的非法SQL实际被MySQL拒绝；V9新增3表、59列、8个非主索引。生产库存54表/1103列/54主索引/56非主索引，测试V6另加1表7列1主索引。旧迁移checksum、既有证券数据和独立证券哨兵行保持不变。
- 完整计划覆盖全部选中股票×接口，NON_STOCK恰好一条null股票；缺描述仍保留股票单元。第二单元插入故障时task和第一单元一起回滚。相同submissionId并发仅一份完整任务成功，另一调用得到可识别唯一冲突。
- unit_key按独立规范JSON SHA-256核对，null与字符串"null"不同。同一任务单元唯一，跨任务/改范围/定义/规则身份以及重复终态提交被拒绝。
- 问题批量插入和报告更新同一事务；分别注入问题插入及报告更新失败均无半份结果。另一连接在提交前看到原PENDING及0条问题，提交后才同时看到终态及全部问题。
- 实际批量保存20,000条问题，包含1,032字符ruleId、2,048字符version、1,024字符reasonCode与3,000字符message，末页正确；这是本地功能容量验证，不是延迟SLA。
- Long9007199254740993、DECIMAL1.000000000000000001、纳秒Instant、完整业务键、relatedDates、证据及规则名称/版本原样读回。JsonNode构造与访问均防御复制，当前定义变化不重算历史。
- FAIL、UNKNOWN、全N/A、NOT_RUN、ERROR及null统计保持正确。未完成或非COMPLETED报告的整体和子规则正式expected/matched/rate均为null；ERROR的已发现下界须有对应问题支撑。FAIL/WARN/UNKNOWN问题不能被更弱维度结论隐藏；已保存历史查询不重新聚合。
- 三类列表全部过滤、组合过滤、跨任务隔离、稳定同值分页、超末页、page/pageSize边界及大offset已覆盖。日期仅过滤issue_date，NULL最后且有日期过滤时排除；共同长前缀股票完整排序。
- 三类列表分别在COUNT后由另一连接提交新数据，当前total/items仍为旧RR快照，新查询才见新值。所有8个公开入口拒绝外部Spring事务，避免快照隔离被调用方改变。
- JSON严格拒绝未知schemaVersion、重复字段、额外外壳字段、尾随JSON、深度超32、敏感字段及不精确数值；坏存储文档为安全QUERY_FAILED。固定旧编码器样例证明request/definition/capability哈希不变。

## TDD、审查及回归维护

初始RED为缺少仓库/JSON入口。审查进一步实际复现3项失败：漏计划单元、ERROR子规则残留覆盖统计、FAIL问题被弱结论隐藏；末轮又复现未完成COMPLETED/NOT_RUN的嵌套正式统计和WARN/UNKNOWN被PASS隐藏2项失败，修复后最终66项通过。独立最终复审批准，所有发现关闭。

V9使原Flyway库存数量增加；同时LONGTEXT的metadata字符容量4294967295超出旧测试工具Integer，已将测试metadata字符长度改为Long。生产存储类型未被缩短。

真实下载回归的两个“任务接收”aria-label和一个独立错误文本定位落后于已有Studio组件；改为实际可访问的“查看任务”链接与.batch-error容器内容断言。原390px测试不符合 `docs/task-handoffs/studio-frontend-task-board.md` 已确认的仅PC范围，改为1024px，保留无溢出和全部业务断言；未修改产品前端，也不改变后续完整性页面T13的窄屏验收。

一次1024px复跑收到既有TASK_STATE_CONFLICT，代码定位到下载协调器每秒空闲poll也占用active，而控制要求active==null。T05未改下载协调器；不放宽双击/单POST断言，同一用例独立复跑通过。此既有偶发时序风险保留在记录中，不宣称由T05修复。

未运行 `scripts/verify-contracts.sh`：其既有clean-main门禁不适用于保留混合暂存基线的隔离分支；没有修改门禁或宣称通过。本项无新的HTTP合同。
