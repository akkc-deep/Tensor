# ISSUE-018-T14：剩余真实验收、完整性补证与逐项开放

## Goal

承接 T13 尚未完成的全部工作，为 40 项接口形成可追查的最终结论，仅在适用提取合同、有效 SOURCE 运行及真实 TASK / SQL 闭环均成立时开放对应 RANGE。ISSUE-025十一项按2026-09-14用户明确接受的不完整响应采集验收；其余接口完整性要求保持。

身份为 [ISSUE-018 看板](../task-handoffs/ISSUE-018/ISSUE-018-task-board.md)的 `ISSUE-018-T14`、Order 14。用户于 2026-09-13 明确要求将 T13 剩余工作新建为 T14，随后在 T14 执行。本次只准备任务，T13 的历史结果不因迁移而升级为通过。

## Scope

完整承接原 [T13 暂停交接](../task-handoffs/ISSUE-018/ISSUE-018-T13-handoff.md)的六项 Remaining Work：

1. 完整带入已有成果，隔离源码与构建输入，准备独立新空 schema。
2. 优先为 `daily_basic`、`stk_limit`、`moneyflow`、`margin_detail` 登记并执行新的固定 SOURCE 轮次。
3. 完成 34 个股票接口各两只股票、6 个非股票接口原方式的真实 SINGLE 任务、查询与 SQL 验收。
4. 补齐完整性合同、特殊日期 / 事件 / 历史样本、BJ / BSE、monthly 和 fina_mainbz 疑点。
5. 按证据逐项修改候选策略及版本，重建后完成 RANGE 任务验收和必要回归。
6. 完成全部设计验收、最终审查、运行说明及母 issue 关闭证据。

保留 40 个入口、34 股票 / 6 非股票、31 原生 RANGE + 3 逐日 RANGE + 6 SINGLE_ONLY。沿用 T13 的完整矩阵、日期轴、业务键、代表场景及证据合同，不重复已经完成的 helper / Probe / harness 实施。未决接口继续关闭，不新增范围排除、多股票合并、type / VIP、通用分页、生产验证开关或自动重试。

## Approach

### 直接输入与合同

- [T13 设计](ISSUE-018-T13-design.md)的 Approach、Files、Tests、Acceptance 作为共享验收合同；其中“首次新建工具”“启动 T13”及历史实施时态由本设计的当前入口取代。现有 `ISSUE018_T13_*` 环境变量与工具文件名保持兼容，不为任务编号重命名代码。
- [验收记录](../verification/ISSUE-018-range-acceptance.md)、[JSON 索引](../verification/ISSUE-018-range-acceptance.json)、[候选评估](../../.superpowers/sdd/2026-09-12-issue-018-t13/task-4-candidate-assessment.md)提供历史轮次、逐接口缺口和四项优先候选依据。SOURCE、TASK、完整性和清理结果分别判断；消费 T13 已审查产物不要求其原最终验收先完成。
- [T06 设计](ISSUE-018-T06-design.md)及 `data-plane/tensor-plugin-tushare/src/main/java/com/akkc/tensor/plugin/tushare/batch/TushareBatchPolicies.java`、同包 `TushareTradeCalendar.java` 固定策略、UNKNOWN 拒绝、版本与日历约束。
- [T12 设计](ISSUE-018-T12-design.md)及 [基础设施验证](../verification/ISSUE-018-task-infrastructure.md)提供真实 MySQL / Servlet、受控拆分与故障、普通浏览器环境和六条源码门禁，不代替账户证据。
- [总体设计](ISSUE-018-design.md) §6–7、[ISSUE-018 关闭条件](../issues/problems/ISSUE-018-date-range-batch-downloads.md#关闭条件)、[ISSUE-017 验证](../verification/ISSUE-017-stock-scoped-downloads.md)固定最终范围及股票归属要求。各输入没有开放结论上的冲突。

### 1. 隔离输入与登记新轮次

启动 T14 后先核对当前 HEAD、分支、`git status`、暂存及未暂存差异。将 T13 交接 Changed Files 的完整成果与届时 T14 文档带入独立工作目录，逐文件比较摘要；仅基于 HEAD 创建 worktree 会遗漏未提交成果。保留原工作树、暂存区及并行 demo / vite 提交，不缩小源码指纹覆盖来消除变化。

在实施产物 `docs/verification/ISSUE-018-T14-runs.md` 登记每个新轮次的目的、唯一 `issue018-t14-<phase>-<UTC或UUID>` runId、全局唯一 caseId、精确参数、依据、预期观察、请求预算及旧 case 关系。本次不创建占位轮次文档或实际私有输入。CASES_FILE 继续使用现有 `runId,cases` 合同，目录 0700 / 文件 0600、普通非 symlink，安全登记与私有输入摘要对应；未执行只记计划，不填结果或 PASS。

为任务轮次准备 MySQL 8.4.6 独立新 schema，沿用 harness 的 `tensor_m14_t05_<hex>` 约束，验证初始 0 表及单库最小权限，迁移后为 8 迁移 / 52 表。defaults 文件明确 utf8mb4，校验与 JDBC 对应但不输出凭证。旧库与 15 个成功任务继续保留。验证源码稳定、实际生产 / 验收 JAR、manifest / examples 哈希及配置权限；重建后重新绑定身份。

这些准备属于 T14 可立即开展的工作。READY 不表示环境、清单或调用条件已经具备；真实轮次必须先留下实际准备证据。T13 原恢复条件作为执行门禁继承，不要求先解决全部 UNKNOWN 才开始本地准备。

### 2. 四接口优先 SOURCE

从完整索引核对四接口原始参数和 case，按 T13 整段、两端、两股票及代表场景规则固定新清单。候选评估只决定优先级，不是已经存在的新执行清单。使用现有测试侧 `TushareRangeSourceProbe`，取证阶段生产 RANGE 门禁保持关闭。

新轮次明确对应原失败 run：`trade_cal-bse-direct` 的 `BATCH_COMPLETENESS_UNCONFIRMED` 及 `top_list-closed-calendar`、`top_list-sh-calendar`、`margin-bse-direct` 三项 NOT_RUN 继续进入疑难调查；四接口优先不取消其他目标。不挑旧 PASS 重包装成成功轮，不复制旧观察充当新请求。

### 3. 完整 SINGLE 闭环

按当前 `docs/contracts/download-request-examples.json` 和 T13 两股票规则登记独立新轮：34 项各 `000001.SZ`、`600000.SH`，6 项原方式，合计 40 个 API / 74 个真实任务 / 148 次 records 查询，fixture 另计 2 次提交 / 3 次查询。复用正式 task API harness，核对 202 / Location、全部批次、终态、SQL 业务键 / 股票归属 / 摘要，以及第二股票写入后的第一股票历史保留。

执行补充（2026-09-13）：现有SINGLE harness自行生成旧任务前缀随机runId，无法满足本项执行前固定T14身份的合同。最小修复允许SINGLE可选读取现有 `ISSUE018_T13_CASES_FILE`；私有权限、runId/caseId、参数白名单、摘要保持现有合同，额外要求精确匹配canonical 74项（34×2+6），禁止漏项、重复、换样本；按canonical顺序执行但保留登记caseId。未提供文件仍兼容原40API发现，RANGE输入不变。离线回归须证明精确匹配、固定身份及清理阶段输入/权限变化拒绝，然后在隔离源码中使用新固定清单。

新的 SINGLE 轮不依赖 RANGE 已开放。SUCCEEDED、查询错误和 SQL 缺口分别保存，清洁完整轮次才能形成有效验收；保留旧 15 次任务，不直接再次调用旧 runner、重试旧 submissionId 或覆盖旧数据。

### 4. 疑难项与代表场景

在轮次登记文档逐项记录缺口、调查范围、依据与固定样本，再将实际结果追加至既有 40 项验收记录和索引：

- 11 项上游 UNKNOWN：`adj_factor, suspend_d, income, balancesheet, cashflow, fina_audit, express, repurchase, stk_managers, top10_holders, top10_floatholders`。用户2026-09-14明确“可以接受不完整”，采用[T13响应采集限定合同](ISSUE-018-T13-design.md#issue-025-响应采集限定采用2026-09-14)及[决定](../issues/proposals/ISSUE-025-extraction-contracts.md#决策记录)：以RESPONSE_ONLY替代完整提取承诺，单次原生响应、无数值阈值、无行数拆分，严格校验并处理返回记录；少量/宽窄相同样本仍不能证明完整。独立规则、批次评估、快照、能力/页面、证据消费者、候选v2与真实TASK/SQL由ISSUE-026落实，本决定不直接开放生产。
- `daily / forecast / dividend` 的6000/3500/2000工程阈值已由用户在ISSUE-019明确同意方案A，准确范围和原严格口径的例外见[T13限定修订](ISSUE-018-T13-design.md#执行顺序和门禁闭环)及[决策记录](../issues/proposals/ISSUE-019-documented-range-limits.md#决策记录)。按原始行数小于L判定本片完整、达到或超过L拆分、单日满额失败；dividend逐自然日执行。保留19项原已公布上界的实际适用性调查，不将本次决定推广到其他接口。此采用口径不是新增上游保证，也不单独开放生产RANGE。
- ISSUE-021依据官网26及新沪深完整日历一致性，仅在测试Probe固定BJ→SSE候选并独立取证；证券仍为BJ，生产映射和候选版本由ISSUE-026连同TASK/SQL实施。安全日历摘要及准确边界见[T13限定扩展](ISSUE-018-T13-design.md#issue-021-日历取证限定扩展2026-09-13)。
- BJ 映射、BSE 直接输入，标停 `slb_sec / slb_sec_detail` 历史范围，monthly 最后交易日与自然月末冲突。
- ISSUE-024历史支持条款按2026-09-14用户明确“同意方案 A（推荐）”限定采用：slb_len/slb_sec/slb_sec_detail以官网历史查询能力、5000上界及代表窗口SOURCE验收，不再等待精确历史起止或持续保留保证；未知保证继续如实记录，不新增日期限制、排除接口或将停业日设为API截止。准确差异见[T13限定采用](ISSUE-018-T13-design.md#issue-024-历史支持限定采用2026-09-14)及[决定](../issues/proposals/ISSUE-024-historical-support.md#决策记录)。原业务键、5000拆分/单日满额失败、6空状态和33项匹配TASK/SQL要求保持；本限定同时适用于下文历史范围Acceptance，不单独完成T14。
- fina_mainbz由ISSUE-020两轮SOURCE确认所选股票默认实际返回P/D/I，普通RANGE也有110/150行，100不能解释为实际硬上限。用户明确“同意方案A（推荐）”：默认保留一次上游返回，SINGLE仅单次快照；RANGE100作为工程拆分阈值，<100按采用口径完整，>=100拆分，单日满额失败。准确范围与原严格验收差异见[T13限定采用](ISSUE-018-T13-design.md#issue-020-限定采用2026-09-13)及[决定](../issues/proposals/ISSUE-020-fina-mainbz-default-type.md#决策记录)。不逐类拼接、不改键、不将150改为新上限；4个满额SOURCE仍未确认，真实任务闭环归ISSUE-026。
- stock_basic 目标状态、特殊事件和非交易日公告，income / fina_indicator 同一行日期关系，repurchase 实际证券数量；仅新 SOURCE 使用已实现安全投影，不回填旧 run。

T13 全部代表场景继续适用，包括 daily 重叠、完整日历 / 全休市、disclosure_date 最新公告及至少一项最终开放接口的真实跨年 SOURCE / TASK。缺依据或空样本记录 EVIDENCE_MISSING；有依据的新样本先登记再另开轮，不换日期直到成功。新依据若要求 ROW_LIMIT / CALENDAR_COVERAGE 之外的算法，先修订本设计中的对应规则与测试再实现。

### 5. 策略、RANGE 与回归

明确规则与有效 SOURCE 同时成立才逐项更新 Policy 的规则、核验日期、证据和版本；v1 首次改为 `tushare-range-v2`，以后语义变化递增。top_list 还依赖完整日历及 trade_cal 可用性。重建并绑定候选包，使用完整 SOURCE 索引、独立 TASK caseId 和固定清单执行 RANGE，提交前核对运行时 AVAILABLE。

按 T13 合同保存页面、完整树 / 叶子覆盖、实际 source / insert / update、SQL 与日志。失败停止该轮，保留失败及未执行项，撤回失败接口的本轮开放标记并递增版本；不删除成功数据。同步独立能力预期、相关测试和运行说明后执行门禁。清理失败或输入变化的 run 不能用于清洁验收。

### 6. 完成与状态归属

剩余实施、暂停及解除阻塞均以 T14 为目标；T13 保留历史 BLOCKED 与原 Acceptance，不重复启动旧工具实施。T14 满足下述 Acceptance 后先记录完成，再用同批有效证据复核 T13 原验收；满足时按合法转换分别记录 `BLOCKED -> READY -> IN_PROGRESS -> COMPLETED`，仅做最终验收收尾，不另跑一遍任务。未满足不得改 T13 状态或关闭母 issue。无预定义后继，不自动创建 T15。

## Files

| 文件 | 责任 |
| --- | --- |
| 本设计、`docs/task-handoffs/ISSUE-018/ISSUE-018-T14-handoff.md`、ISSUE-018 看板 | 本次准备的设计、入口与状态 |
| `docs/verification/ISSUE-018-T14-runs.md` | 启动后登记新轮、旧失败关系、环境 / 构建身份和疑难调查 |
| `docs/verification/ISSUE-018-range-acceptance.md`、`.json` | 唯一 40 项验收结果，追加新证据，原 3 轮 / 329 case 不改写 |
| `control-plane/e2e/tushare-range-evidence.js`、对应 `.test.js`、`tushare-live.spec.js` | 复用已审查工具；具体新缺陷才最小修复与回归 |
| `data-plane/tensor-plugin-tushare/src/test/java/com/akkc/tensor/plugin/tushare/batch/TushareRangeSourceProbe.java`、同目录 `TushareRangeSourceProbeTest.java` | 复用来源入口、投影和离线测试 |
| T13 Files 的 Policy / Calendar、策略 / HTTP / 浏览器能力测试、运行手册 | 证据成立后逐项修改规则、独立预期与说明 |
| ISSUE-017 / ISSUE-018 问题、验证、索引、看板 | 只按实际证据更新，保留历史失败和范围决定 |

不改 V1–V8、40 份 YAML 业务列 / 键或历史 manifest。新增文件显式加入 Git，不自动提交、合并或发布。

## Tests

从隔离仓库根使用 Java 21、项目 Node 24。离线 / 普通命令移除真实账户 / 数据库 / 证据环境变量；SOURCE 与真实浏览器串行运行，T13 变量名保持兼容。

```sh
node --test control-plane/e2e/tushare-range-evidence.test.js
mvn -f data-plane/pom.xml -pl tensor-plugin-tushare -am -Dtest=TushareRangeSourceProbeTest -Dsurefire.failIfNoSpecifiedTests=false test
mvn -f data-plane/pom.xml -Dtest=TushareRangeSourceProbeTest,TushareBatchPoliciesTest,TushareBatchDownloadTest,TushareTradeCalendarTest,TushareBatchAvailabilityTest -Dsurefire.failIfNoSpecifiedTests=false test
TENSOR_TUSHARE_LIVE_E2E=1 ISSUE018_T13_PHASE=single npm --prefix control-plane run test:e2e -- --list
```

前三条预期退出 0、无失败 / 未解释跳过，覆盖漏项、UNKNOWN 误开放、错误身份 / 批次树 / SQL、敏感投影拒绝和策略边界。list 只发现 40 个 SINGLE API，不算真实执行；RANGE 数量与固定非空清单一致。

用户启动 T14 且本轮私有输入、稳定源码、授权配置、新空库及实际哈希准备成立后，依登记轮次执行：

```sh
TENSOR_TUSHARE_LIVE_E2E=1 mvn -f data-plane/pom.xml -pl tensor-plugin-tushare -am -Dtest=TushareRangeSourceProbe -Dsurefire.failIfNoSpecifiedTests=false test
TENSOR_TUSHARE_LIVE_E2E=1 ISSUE018_T13_PHASE=single npm --prefix control-plane run test:e2e -- e2e/tushare-live.spec.js
TENSOR_TUSHARE_LIVE_E2E=1 ISSUE018_T13_PHASE=range npm --prefix control-plane run test:e2e -- e2e/tushare-live.spec.js
```

环境路径 / 哈希、SQL defaults 按 T13 Tests 与浏览器合同核对，Token 只进后端环境。SOURCE 无任务 / SQL 事实；SINGLE 完整满足 74 / 148 及 fixture 独立计数；RANGE 逐项具备规则、有效来源、任务 / SQL 和清洁身份。失败保留实际退出码、NOT_RUN 和清理事实，不重试。

策略或应用源码变化后按 T12 顺序执行六条当前源码门禁：

```sh
mvn -f data-plane/pom.xml -Dtest='*Test,*IT,!PackagedJarContractTest,!AcceptancePackagedJarContractTest' -Dsurefire.failIfNoSpecifiedTests=false test
npm --prefix control-plane test
mvn -f data-plane/pom.xml clean verify
mvn -f data-plane/pom.xml -Pacceptance clean verify
npm --prefix control-plane run test:e2e -- e2e/download-tasks.spec.js
npm --prefix control-plane run test:e2e
```

预期退出 0，数量按实际报告记录；普通浏览器保留 7 文件 / 126 项或记录明确增量，使用新建 preview 和 T12 四隔离库。`git diff --check`、暂存差异和输入身份检查通过。完整 `sh scripts/verify-contracts.sh` 仍须既定 main / 干净输入 / HEAD 前置，未运行如实记录，不自动提交或切分支制造条件。本次文档准备不执行业务测试。

## Acceptance

1. 六项剩余范围全部有实际产物，40 项及 34 / 6、31 / 3 / 6 不变量保持；旧 3 轮 / 329 case、失败身份与 15 个任务不改写或删除。
2. 新执行有固定计划、旧 case 关系、隔离稳定源码、实际构建哈希、独立数据库和安全清理；未执行不冒充通过。
3. 完整 SINGLE 两股票 / 非股票任务与 SQL 验收成立，ISSUE-017 对应证据逐条更新；旧 14 个 TASK PASS 不单独作为有效整轮验收。
4. 每个 AVAILABLE 具备适用提取合同、真实 SOURCE 语义 / 边界及匹配候选包的 RANGE 页面 / 全批次 / SQL / 日志；十一项按已批准RESPONSE_ONLY合同验收并持续标注完整性未确认，其他项保持完整性要求。全部代表场景、含糊合同、BJ / BSE、标停、monthly、fina_mainbz 有明确依据；实现或TASK/SQL未完成则T14未完成，不自行排除。
5. 策略版本、能力、持久化快照、独立测试及说明一致，相关门禁和最终审查有证据；受控满额 / 故障与账户语义分开。UNKNOWN和未完成叶子不显示成功；ROW_LIMIT单点满额仍失败。RESPONSE_ONLY只有返回记录处理和持久化成功才显示本次采集成功，空响应不声明区间无数据，不能写成完整成功。
6. 满足总体设计 §6、T13 原 Acceptance 及母 issue 关闭条件后才完成 T14，并处理 T13 最终状态与 issue；新建任务或四接口优先通过均不等于母 issue 完成。

## Risks

- 完整性、账户权限、默认类型和特殊 / 历史样本是待取证事实，本设计不填造答案。
- 私有配置 / runner 可能失效；只检查存在与权限，按持久合同重建缺失环境，不打印凭证或猜遗失结果。
- 每轮至少 2000ms 来源间隔、30 分钟 / 5000 请求预算，串行、workers=1、retries=0，关闭 trace / screenshot / video；异常停止并保留在途任务实际状态。
- 完整性不等于跨批同一时刻快照，源码 / SQL / 清理失败仍使验收不成立；任务迁移不解除这些约束。
