# ISSUE-018-T14 四接口本地候选策略

2026-09-13，按已确认的 [T14 设计](../task-designs/ISSUE-018-T14-design.md)、[T13 共享合同](../task-designs/ISSUE-018-T13-design.md)及[独立优先审查](ISSUE-018-T14-priority-review.md)实施。该修改供本地候选包验收；本报告不宣布真实 RANGE TASK、T14 或母 issue 完成。

## 策略决定

SOURCE 为 `issue018-t14-priority-source-20260913T092938Z`：33/33 PASS、exit 0、cleanup PASS、输入稳定。官网 ROW_LIMIT 与 SOURCE 语义分别成立，具体引文、样本及局限见独立审查和规范验收记录。

| API | ROW_LIMIT | policyVersion | documentationCheckedOn | sourceVerified | SOURCE 引用数 |
| --- | --- | --- | --- | --- | --- |
| daily_basic | 6000 | tushare-range-v2 | 2026-09-13 | true | 9 |
| stk_limit | 5800 | tushare-range-v2 | 2026-09-13 | true | 8 |
| moneyflow | 6000 | tushare-range-v2 | 2026-09-13 | true | 8 |
| margin_detail | 6000 | tushare-range-v2 | 2026-09-13 | true | 8 |

每项 `verificationEvidence` 引用 `docs/verification/ISSUE-018-range-acceptance.md#<api>`、完整 SOURCE runId 和该项全部 SOURCE caseId：两股票 SINGLE、两股票分别整段与两端；daily_basic 另含跨年。未增加生产开关，注册器仅四处显式提供已审查的固定 case 引用，其余 30 个 RANGE 条目的原规则、日期、v1、false/null 不变。6 项 SINGLE_ONLY 及全部 40 项 SINGLE 不变。

原有 UNKNOWN、top_list 的 BJ/未知后缀、trade_cal 的 BSE 拒绝逻辑不变。未以沪深样本推定北交所支持。ROW_LIMIT 仍按原始响应行数判断，等于阈值即 SPLIT_REQUIRED；最小单日满额不能写入或显示完整成功。

## TDD 与实际验证

使用 Java 21.0.11（`/Users/qiangzhiwei/.sdkman/candidates/java/21.0.11-oracle`）及项目 Node 24.15.0。子进程环境只继承 HOME、PATH、TMPDIR、LANG、LC_ALL，并显式选择 JAVA_HOME 与项目 Node PATH；未继承任何真实账户、数据库、Spring、来源或证据环境变量。Maven 使用 `-o`，HTTP 测试仅使用既有受控 WireMock / MockRestServiceServer。本子任务未执行真实来源或 SQL 账户操作。

1. 先修改独立期望和准入测试，未改生产策略。受控 RED 为 134 项、13 assertion failures 与 4 个预期的 BATCH_DOWNLOAD_UNAVAILABLE，全部对应四项尚未开放；Calendar 和旧受控场景无失败。初次沙箱运行另因本地端口绑定受限失败，随后在正常本地权限下重跑得到上述有效 RED。
2. 单独执行准入 RED：3 项、1 failure、0 errors，明确为四项预期 AVAILABLE、实际 NEEDS_VERIFICATION。其余 30 项拒绝及 40 项 SINGLE 接受仍通过。
3. 最小生产修改后，下列专项 GREEN 为 **254/254，0 failures/errors/skips，exit 0**：

```sh
mvn -o -f data-plane/pom.xml -Dtest=TushareRangeSourceProbeTest,TushareBatchPoliciesTest,TushareBatchDownloadTest,TushareTradeCalendarTest,TushareBatchAvailabilityTest,TushareProPluginTest,DownloadTaskRunnerTest,DownloadTaskServiceTest -Dsurefire.failIfNoSpecifiedTests=false test
```

| 测试类 | 数量 | 重点 |
| --- | --- | --- |
| TushareBatchPoliciesTest | 109 | 独立 34 项矩阵、精确四项开放、v2/日期/来源引用、生产四项 0/L−1/L/L+1、未决 UNKNOWN、BJ/BSE |
| TushareBatchDownloadTest | 21 | 新增四项生产策略 × 两股票受控 HTTP/参数/COMPLETE；原日历与故障路径 |
| TushareTradeCalendarTest | 4 | 完整日历、缺失/重复/非法标记，生产 trade_cal 保持 UNKNOWN |
| TushareBatchAvailabilityTest | 3 | 四项 × 两股票准入 QUEUED、零上游；30 未验证 RANGE 零入库/零排队查询/零上游；40 SINGLE |
| TushareRangeSourceProbeTest | 30 | 现有离线取证合同，无真实 SOURCE |
| TushareProPluginTest | 12 | 既有插件路径及未决 daily 拒绝 |
| DownloadTaskRunnerTest | 51 | 等号拆分、UNKNOWN/最小单日满额零提交、策略变更前后 TASK_DEFINITION_CHANGED |
| DownloadTaskServiceTest | 24 | 既有准入、快照与人工操作合同 |

4. `npm --prefix control-plane test`：**34 文件 / 468 项通过，exit 0**。
5. `npm --prefix control-plane run test:e2e -- --list`：**7 文件 / 126 项，exit 0**。仅发现，未宣称浏览器执行通过。
6. Node 直接消费独立 `rangeCapability` fixture 并核对安全 `source-run.json`：40 项中 4 AVAILABLE / 30 NEEDS_VERIFICATION / 6 UNSUPPORTED；四项阈值逐项正确，33 个实际 PASS SOURCE caseId 均被对应能力引用，exit 0。
7. `git diff --check` 通过。未修改或重置原 T13 暂存区，未提交。

本地日志目录：`/private/tmp/issue018-t14-priority-policy-tests/`，包括 `red.log`（端口受限）、`red-local.log`、`red-admission.log`、`green.log`、`node-unit.log`、`node-list.log`、`node-fixture.log`。构建生命周期仍输出既有 Vite 大 chunk 提示；无测试失败或跳过。

## 本子任务完整修改清单

同步候选快照时须包含以下全部 9 个文件；新增本报告由主流程显式 `git add`。

| 文件 | 修改 |
| --- | --- |
| `data-plane/tensor-plugin-tushare/src/main/java/com/akkc/tensor/plugin/tushare/batch/TushareBatchPolicies.java` | 四项固定候选证据、v2、核验日期与开放标记 |
| `data-plane/tensor-plugin-tushare/src/test/java/com/akkc/tensor/plugin/tushare/batch/TushareBatchPoliciesTest.java` | 独立生产矩阵及四项实际阈值测试 |
| `data-plane/tensor-plugin-tushare/src/test/java/com/akkc/tensor/plugin/tushare/batch/TushareBatchDownloadTest.java` | 四项生产策略两股票受控下载 |
| `data-plane/tensor-app/src/test/java/com/akkc/tensor/web/TushareBatchAvailabilityTest.java` | 4 可准入、30 拒绝、40 SINGLE |
| `data-plane/tensor-app/src/test/java/com/akkc/tensor/config/DownloadTaskApplicationConfigurationIT.java` | 启动后能力集合从 0/34/6 更新为 4/30/6 |
| `control-plane/e2e/ui-redesign.fixtures.js` | 独立四项能力与完整证据预期 |
| `control-plane/e2e/ui-redesign.spec.js` | 普通 UI 矩阵四项区间按钮可用，其余禁用；保留原 SINGLE 提交断言 |
| `control-plane/e2e/tushare-metadata.spec.js` | 实际服务能力/阈值/v2及区间按钮独立断言 |
| `docs/verification/ISSUE-018-T14-priority-policy.md` | 本报告 |

未改 `TushareTradeCalendar.java` 或其测试、live harness/helper/.test.js、规范 JSON 索引、看板、runbook、YAML 业务列/键及迁移。

## 主流程仍须执行

- 将上述文件完整同步至隔离源码，重新确认稳定输入并重建、绑定实际候选 production/acceptance JAR。此前 SOURCE 包与此候选包的不同是预期变化，不能沿用旧 TASK 包身份。
- 按 T14/T12 规定顺序执行六条当前源码门禁：全 `*Test,*IT`（排除两个 packaged 测试）、前端单测、production clean verify、acceptance clean verify、download-tasks 浏览器、全部普通浏览器。`DownloadTaskApplicationConfigurationIT`、实际 metadata/UI 浏览器尚未由本子任务执行，必须包含于本次门禁；7/126 浏览器数量未改变。
- 为四项执行独立固定 **25 个 RANGE TASK**：daily_basic 7 个、其他各 6 个，与本次引用的 25 个 RANGE SOURCE 精确参数匹配；TASK 的页面、全批次树、叶子、SQL、日志与清理均须成立。任何失败按设计保留事实并撤回失败接口本轮开放标记、递增版本。
- 主流程负责完整 SINGLE、规范索引、实际运行说明及最终结论。四项本地候选成功不解除其余 30 项、BJ/BSE、UNKNOWN 或其他代表场景的缺口。
- `sh scripts/verify-contracts.sh` 本子任务未执行，既有 main/干净输入/HEAD 前置仍适用。
