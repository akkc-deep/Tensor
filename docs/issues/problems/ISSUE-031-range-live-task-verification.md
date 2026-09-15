# ISSUE-031：完成真实区间任务与SQL验收

## 当前状态

COMPLETED（2026-09-15，按修订后的本次范围）。按[用户明确决定](../proposals/ISSUE-026-range-scope.md)，本次排除balancesheet、cashflow、repurchase、fina_indicator的RANGE批量下载；当前251项均已有清洁通过证据，原27项保留4 FAILED/23 NOT_RUN，不计PASS。范围与历史保全核对通过，111项证据测试通过，本项按251项范围完成；032保持NOT_STARTED，不准备或启动。

## 目标与范围

原计划278项保留总追踪；本次按[范围决定](../proposals/ISSUE-026-range-scope.md)纳入30个RANGE接口的251项，排除四接口27项。准确输入、续验映射、各自构建身份和原失败均保留，见[更新设计](../../task-designs/ISSUE-031-design.md)。

- 复用ISSUE-029工具及已验证候选。原十轮28/16/38/35/37/33/24/42/21/4项作为固定输入基线；延期与实际失败后的独立续验按更新设计执行，保留全部原始记录。
- 覆盖268既有输入、四项mainbz全年/六年宽窗TASK、两项disclosure_date重下及四个已开放接口回归；精确来源身份与参数不得自行替换。
- 各轮新空schema、固定清单/身份/预算，核对页面、202/Location、全部批次、records、原键/归属/实际写入/摘要及日志；保留成功数据和旧SINGLE结果。
- 逐轮追加唯一验收索引/登记；失败或环境变化停止并记录实际结果，按母设计撤回失败接口候选且递增版本，不自动重试或换日期求通过。

## 已知依据

直接消费[共享设计](../../task-designs/ISSUE-026-design.md)：共享设计Approach第5节及mainbz真实拆分要求；ISSUE-029实际来源、ISSUE-030候选身份、ISSUE-026十轮输入和T13/T14执行合同。 原采用决定和精确输入继续以[母issue输入表](ISSUE-026-range-task-final-acceptance.md)、[唯一验收索引](../../verification/ISSUE-018-range-acceptance.json)及[运行登记](../../verification/ISSUE-018-T14-runs.md)为准。候选和来源的实际交付见[ISSUE-030验收](../../verification/ISSUE-030-range-candidate-policies.md)；本项准备没有产生新SOURCE、TASK或SQL结果。

## 依赖与处理顺序

- 直接子issue前置：ISSUE-029, ISSUE-030；共享ISSUE-019～025采用决定与来源作为母任务已交付输入，不能推定未来新来源已成功。
- 串行顺序：ISSUE-027 → ISSUE-028 → ISSUE-029 → ISSUE-030 → ISSUE-031 → ISSUE-032；编号顺序不代替依赖的实际验收。
- [原暂停交接](../../task-handoffs/ISSUE-031-handoff.md)保留为历史。四接口已获明确范围排除，本次仅完成范围收尾，不再诊断/修复/补验；用户要求不开始032。

## 关闭条件

- 当前251个纳入计划项均有实际合格结果和清洁运行身份，source/insert/update与最终业务键数分开；整段/两端/股票保留和代表场景有证据。原27项按明确范围决定排除，不计PASS；不再要求ISSUE-033作为本次RANGE关闭前置。
- mainbz四TASK绑定新四SOURCE，至少一项真实观察到SPLIT父与完整成功子树；父无写入，原单日满额仍失败，受控拆分不能替代。
- 两次disclosure重下核对实际更新操作和SQL原键；本次纳入的8项RESPONSE_ONLY按响应采集合同验证，意外空任务的真实终态与非空代表性缺口分开记录。
- 所有拟标AVAILABLE的接口均有采用合同、有效SOURCE、匹配候选包TASK/SQL与清洁日志；局部核对/运行审查通过，交ISSUE-032统一回归和关闭，不提前关闭母issue。

## 实施落点与验证

`docs/verification/ISSUE-018-T14-runs.md`、唯一验收JSON/Markdown、必要的受影响运行说明；按固定私有清单运行既有harness，实际问题才修改相应实现并重新核对构建影响。

从仓库根使用项目Java21/Node24，按专属设计准备环境后执行以下相关检查；这些是待执行命令，不是本次结果：

```sh
TENSOR_TUSHARE_LIVE_E2E=1 ISSUE018_T13_PHASE=range npm --prefix control-plane run test:e2e -- e2e/tushare-live.spec.js --list
TENSOR_TUSHARE_LIVE_E2E=1 ISSUE018_T13_PHASE=range npm --prefix control-plane run test:e2e -- e2e/tushare-live.spec.js
```

## 约束

遵循共享设计、T13/T14及用户已批准差异，保持40接口、原日期/股票/业务键、无自动重试和历史证据。真实调用前固定输入、完整源码/两包身份和受限环境，SOURCE不入库，TASK每轮新空schema；至少2000ms间隔、每轮30分钟/5000请求和清理要求不变。无新原因不重复已有效验证；新增文件加入Git，不自动提交或发布。局部任务完成不替代尚未完成的生产准入/真实任务/母合同验收。
