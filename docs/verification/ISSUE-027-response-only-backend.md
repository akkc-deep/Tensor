# ISSUE-027 后端响应采集验收（2026-09-14）

按[专属设计](../task-designs/ISSUE-027-design.md)完成公共合同、Tushare通用策略、runner匹配及持久策略摘要。实际受控检查覆盖280项后端测试、7项包合同和468项前端测试，失败/错误/跳过均0；独立审查及最终增量审查无待修复发现。

## 实际交付

- `CompletenessRule.Kind.RESPONSE_ONLY`：rowLimit=null、非空evidence，仅NATIVE_RANGE且splittable=false。`BatchAssessment.RESPONSE_ONLY`为独立枚举；UNKNOWN仍不可AVAILABLE。
- Tushare通用规则经过原字段/请求/日期/股票检查后返回RESPONSE_ONLY，合法空亦如此。未sourceVerified仍UNKNOWN并拒绝plan；十一项测试候选通过包内构造注入。`repurchase`保持原日期查询全市场形状，其余十项保持指定股票。
- runner先拒绝UNKNOWN，再按持久规则精确匹配；RESPONSE_ONLY与COMPLETE/SPLIT_REQUIRED互相冒用返回DATASET_MISCONFIGURED，零适配/入库。错误响应、预算、超时、中断与原子提交行为保持。
- 新增`DownloadTaskService.TaskPolicySummary(String policyVersion, CompletenessRule.Kind ruleKind)`及public `policySummary(DownloadTask)`。SINGLE=null；RANGE只解析policySnapshot。非法JSON继续抛无敏感内容的IllegalArgumentException，null任务为PARAM_INVALID；不查插件、能力、适配器、数据库。
- JSON现有schemaVersion=1已支持严格新旧枚举读取，无需修改`DownloadTaskJson`实现或数据库迁移。规则/版本继续进入definitionHash，人工retry/resume对变更定义拒绝。

## 验证记录

执行环境Java21.0.11、Maven3.9.15、项目Node24.15.0/npm11.12.1；受控HTTP使用WireMock，事务使用Testcontainers的mysql:8.4.6。白名单环境排除真实Tushare/业务DB变量；本地Colima socket显式传给Testcontainers。测试后只读`docker ps --filter label=org.testcontainers=true`无运行容器。日志及机器汇总位于`/private/tmp/issue027-control/`。

| 检查 | 实际结果 | 日志 |
| --- | --- | --- |
| 公共规则RED → GREEN | 9项中1预期失败 → 9通过 | `api-red.log`、`api-green.log` |
| runner匹配RED | 54项中1预期失败：应DATASET_MISCONFIGURED却成功 | `runner-red-local.log` |
| Tushare规则RED | 121项中12预期失败：缺RESPONSE_ONLY | `tushare-red.log` |
| 历史摘要RED | 26项中1预期失败：缺policySummary | `summary-red.log` |
| 专项后端 | 279通过，含11项MySQL IT | `backend-regression-colima.log` |
| 最终runner及两包验证 | runner55、生产包4、验收包3全部通过，BUILD SUCCESS | `package-verification.log` |
| 前端测试与构建 | 468通过，Vite构建通过 | 上述最后两轮Maven日志 |

专项命令：

```sh
mvn -o -f data-plane/pom.xml -Dtest=BatchDownloadDescriptorTest,TushareBatchPoliciesTest,TushareBatchDownloadTest,TushareTradeCalendarTest,DownloadTaskRunnerTest,DownloadTaskRunnerIT,DownloadTaskServiceTest,DownloadTaskJsonTest,TushareBatchAvailabilityTest -Dsurefire.failIfNoSpecifiedTests=false test
mvn -o -f data-plane/pom.xml -Pacceptance -Dtest=DownloadTaskRunnerTest -Dsurefire.failIfNoSpecifiedTests=false verify
```

第二条重新验证新增SINGLE/RESPONSE_ONLY坏响应参数化场景，runner从54增至55；其余通过结果复用，去重后专项280项、两包7项。最终XML明细：Descriptor9、Runner55、RunnerIT11、Service27、Json21、Policies121、BatchDownload29、TradeCalendar4、Availability3、PackagedJar4、AcceptancePackagedJar3。不是全仓库所有后端测试或母任务六条门禁已执行。

初始`runner-red.log`因沙箱禁止Mockito self-attach失败，获本地测试执行许可后`runner-red-local.log`取得实际行为失败；`backend-regression.log`因Testcontainers未自动识别Colima失败，显式使用已观察的socket后完整专项通过。首次规则GREEN中一个`repurchase`测试错把全市场查询当股票限定，修正测试后通过；生产请求形状未改。上述环境/测试失败日志保留，不能作为功能通过结果。

## 关键结果与兼容性

MySQL实际验证RESPONSE_ONLY单请求单叶子：两条不同原键提交；第二任务更新一条并保留另一条；空响应成功且零写入；适配错误零写入；第二行触发数据库失败时第一行同步回滚；双向规则冒用及UNKNOWN零数据、零SPLIT。原拆分、单批事务、失败恢复、停机和SINGLE回归保持。

十一接口受控策略测试覆盖空、两端、7000行无数值拆分、越界、坏日期、股票或原非股票范围、字段/请求错误及未验证拒绝；HTTP实客户端覆盖合法空/非空和坏股票/日期/字段/行宽/响应体/HTTP错误，不重试。runner补行数/请求/时间预算和下载后/适配后中断。历史严格/UNKNOWN和新规则的v1/v2快照往返、非法新组合、失去当前插件/适配器的摘要、SINGLE null、损坏快照及定义变化人工重放拒绝全部通过。

生产`createPolicies()`及之后的注册/版本代码与任务开始备份逐字一致；独立矩阵验证仍4 AVAILABLE/30 NEEDS_VERIFICATION/6 SINGLE_ONLY。唯一验收索引字节一致，SHA-256为`7e7d390e45eaffcb7310839db6670e736fdfd216ade006b82a79f95c7822957d`，原25轮822case886请求没有新增或重写。本次没有真实SOURCE/TASK/账户SQL，测试SQL不登记为真实来源验收。

本次生成生产JAR SHA-256：`ce996f91affd6c768fdb9f4439fcee4f9b263951297649c0e6c11e4fa0e88a49`；验收JAR：`b72664bda4d0b93316e4a3f7e06959b1507df85af5b9a2a178461b9de70e8101`。仅是本地受控构建产物，不替代ISSUE-030未来候选包身份或发布结果。

## 审查与后继输入

独立审查核对完整未暂存实现及设计，确认无阻塞/正确性发现；唯一低优先级建议为`BatchDownloadSupport.assess`注释中未知完整性与RESPONSE_ONLY容易混淆，已明确按采集规则评估。最终独立增量审查确认注释和SINGLE/RESPONSE_ONLY坏响应覆盖，755项去重检查均通过。

ISSUE-028直接消费上述public summary和枚举，详情/列表新增extraction、同步正式schema/examples/OpenAPI及严格前端解析。它必须根据任务保存的规则显示历史语义，持续显示完整性未确认；生产候选仍由后续issue处理。ISSUE-027完成不关闭ISSUE-026/T13/T14或母issue，不自动commit/push/发布。
