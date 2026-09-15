# ISSUE-029：升级验收工具并补齐主营业务拆分来源

## 当前状态

COMPLETED（2026-09-14）。schema1/2与严格来源身份、HTTP/页面证据消费者及mainbz测试侧二分已交付。四项固定SOURCE均PASS，共42请求、19拆分父、23成功叶；源码/两包和清理核对、局部/隔离构建及独立终审通过，旧对象和生产保持。详见[实际验收](../../verification/ISSUE-029-range-evidence-and-split-source.md)及[子看板](../../task-handoffs/ISSUE-026/ISSUE-026-task-board.md)。候选和真实TASK/SQL仍由ISSUE-030/031完成。

## 目标与范围

让证据工具识别响应采集并精确绑定来源，取得主营业务全年及六年宽窗的四项完整拆分SOURCE。

- 实现共享设计第4节的schema1/2兼容、独立RESPONSE_ONLY证据规则，以及harness对能力、持久extraction、单请求单叶子的核对；通过后才升级正式索引schemaVersion。
- selectTaskCases按明确runId/caseId来源引用选择，拒绝错配和歧义；受限兼容旧#caseId/唯一候选，初始证据与执行共用sourceBindings。
- 仅扩展测试侧fina_mainbz RANGE探针，按已批准100阈值递归日期二分，记录完整真实SPLIT树，父不计成功行/投影，单日满额失败。
- 固定两股票000001.SZ/600000.SH各20250101～20251231、20200101～20251231，执行四新SOURCE并保存真实身份；不创建TASK/SQL，不修改生产候选开放标记/版本。

## 已知依据

直接消费[共享设计](../../task-designs/ISSUE-026-design.md)：共享设计Approach第4节及“fina_mainbz 已承诺的真实拆分来源与任务”；ISSUE-028接口合同；ISSUE-020已批准方案、原12有效/4满额来源和唯一索引。 原采用决定和精确输入继续以[母issue输入表](ISSUE-026-range-task-final-acceptance.md)、[唯一验收索引](../../verification/ISSUE-018-range-acceptance.json)及[运行登记](../../verification/ISSUE-018-T14-runs.md)为准。拆分没有产生新的实现、SOURCE、TASK、SQL或测试结果。

## 依赖与处理顺序

- 直接子issue前置：ISSUE-028；共享ISSUE-019～025采用决定与来源作为母任务已交付输入，不能推定未来新来源已成功。
- 串行顺序：ISSUE-027 → ISSUE-028 → ISSUE-029 → ISSUE-030 → ISSUE-031 → ISSUE-032；编号顺序不代替依赖的实际验收。
- 第一动作：消费已完成的专属设计，在tushare-range-evidence.test.js增加两同参数清洁SOURCE的指定身份选择/歧义拒绝及schema1/2新规则测试，运行观察RED。

## 关闭条件

- schema1历史可读且不能承载新规则；schema2缺决定/错接口/伪完整/错身份/无任务或SQL不能通过。旧25轮822case逐对象保留，新增来源仅追加。
- 同参数两个清洁SOURCE分别按指定身份绑定，错误不回退first-match；特别是新000001-annual不能绑定回旧74行whole，原12项仍绑定指定旧来源。
- 四新SOURCE均有完整合法覆盖、成功叶子<100且至少一叶子非空、清洁退出/清理和稳定源码/两包；旧四满额EVIDENCE_MISSING不重标，未执行或失败不当PASS。
- Node及Probe局部回归、隔离构建与独立审查通过；交付四项新精确来源供ISSUE-030/031。SOURCE没有TASK/SQL事实，十一项当前UNKNOWN不因工具支持而直接开放。

## 实施落点与验证

`control-plane/e2e/tushare-range-evidence.js`、`.test.js`、`tushare-live.spec.js`；测试侧TushareRangeSourceProbe及Test；唯一验收JSON/Markdown和T14运行登记。

从仓库根使用项目Java21/Node24，按专属设计准备环境后执行以下相关检查；这些是待执行命令，不是本次结果：

```sh
node --test control-plane/e2e/tushare-range-evidence.test.js
mvn -o -f data-plane/pom.xml -pl tensor-plugin-tushare -am -Dtest=TushareRangeSourceProbeTest -Dsurefire.failIfNoSpecifiedTests=false test
```

## 约束

遵循共享设计、T13/T14及用户已批准差异，保持40接口、原日期/股票/业务键、无自动重试和历史证据。真实调用前固定输入、完整源码/两包身份和受限环境，SOURCE不入库，TASK每轮新空schema；至少2000ms间隔、每轮30分钟/5000请求和清理要求不变。无新原因不重复已有效验证；新增文件加入Git，不自动提交或发布。局部任务完成不替代尚未完成的生产准入/真实任务/母合同验收。
