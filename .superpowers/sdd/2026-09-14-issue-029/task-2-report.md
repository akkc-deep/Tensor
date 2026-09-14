# ISSUE-029 Task 2 实施报告

## 结果

已在测试侧 `TushareRangeSourceProbe` 为 `fina_mainbz` 的 `RANGE` 模式实现 100 行阈值递归拆分：每个响应先完成 Envelope、请求范围和股票范围校验；`<100` 成为成功叶子，`>=100` 的多日窗口通过 core 的 `DateRangePlanner.split` 生成相邻闭区间并按左、右深度优先请求，单日仍满额则以 `BATCH_COMPLETENESS_UNCONFIRMED` 失败。其它 API 和 `SINGLE` 路径保持原单请求行为。

每个满额父节点标为 `SPLIT`，不记录成功行数，也不进入日期、P/D/I、业务键等安全投影。拆分时先同时登记两个 `NOT_RUN` 子节点，再执行左子树，因此失败、停止、超时或请求额度耗尽后仍保留完整的已知树结构。汇总只统计非 `SPLIT` 叶子；`requestCount` 保留 Context 中本 case 的实际成功预约数。`reviewMethod` 分开记录 `candidateLimitReached`、`splitParentCount` 和 `unresolvedFullLeafCount`。

插件 POM 仅增加 `tensor-core` 的 test-scope 依赖，以直接复用 `DateRangePlanner.split`；未复制算法，未增加生产依赖。

## 测试先行记录

所有命令均使用 `/private/tmp/issue029-control/run-tests.py` 的 Java 21/离线白名单环境。日志已设为 `0600`，未注入 Tushare credential 或业务数据库变量。

- 初始 RED：`probe-red.log`，66 tests，5 failures，0 errors，0 skipped。新增的递归、多层树、单日满额、坏 Envelope/越界、部分失败及停止断言按预期失败；其它 API/SINGLE 隔离测试直接通过，确认原行为基线。
- 第一次实现运行：`probe-green-1.log`，66 tests，2 failures，0 errors，0 skipped。剩余两项是失败 case 不应发布 projection 摘要的测试期望错误；按既有安全合同改为断言 projection 不出现，未放宽实现。
- 第一轮 GREEN：`probe-green-2.log`，66 tests，0 failures，0 errors，0 skipped。
- 请求额度 RED：`probe-budget-red.log`，test compile 1 error，原因是尚无受控 Context overload。增加 test-only overload 后可直接把 Context 预置到 4999 次，验证父请求占用最后一次额度时的子节点事实。
- 请求额度 GREEN：`probe-green-final.log`，67 tests，0 failures，0 errors，0 skipped。
- 最小化重构后复验：`probe-after-refactor.log`，67 tests，0 failures，0 errors，0 skipped。

最终 Probe 命令：

```sh
python3 /private/tmp/issue029-control/run-tests.py probe-after-refactor.log -pl tensor-plugin-tushare -am -Dtest=TushareRangeSourceProbeTest -Dsurefire.failIfNoSpecifiedTests=false test
```

## 覆盖

- 左右子树均发生多层拆分，且请求顺序为深度优先、先左后右。
- 满额父响应不计 `sourceRowCount`、成功叶数、日期、P/D/I 和业务键投影。
- 完整树中允许空成功叶；整 case 仍要求至少一个非空成功叶才能 PASS。
- 单日 100 行失败并记录未解决满额叶。
- 子请求坏 Envelope 和返回日期越界均失败，未执行兄弟保留 `NOT_RUN`。
- 左叶成功、右叶传输失败时只保留左叶行数和投影。
- stop/deadline 在父拆分后触发时两个子节点均保留 `NOT_RUN`；5000 次请求额度耗尽时尝试的左子失败、右子保留 `NOT_RUN`，case 内 `requestCount=1`。
- `fina_mainbz SINGLE` 及其它 RANGE API 不进入递归拆分。

## 相关回归

相关回归首次在沙箱内运行：`tushare-related.log`，154 tests，0 failures，29 errors，0 skipped；29 项均为 WireMock 无法绑定 `0.0.0.0:0` 的本地 socket 限制，不是断言失败。

获准使用本机临时 WireMock 端口后，以相同白名单 runner 重跑 `TushareBatchPoliciesTest,TushareBatchDownloadTest,TushareTradeCalendarTest`：`tushare-related-escalated.log`，154 tests，0 failures，0 errors，0 skipped，BUILD SUCCESS。

## 范围与关注点

改动文件仅为：

- `data-plane/tensor-plugin-tushare/pom.xml`
- `data-plane/tensor-plugin-tushare/src/test/java/com/akkc/tensor/plugin/tushare/batch/TushareRangeSourceProbe.java`
- `data-plane/tensor-plugin-tushare/src/test/java/com/akkc/tensor/plugin/tushare/batch/TushareRangeSourceProbeTest.java`
- 本报告

未修改生产策略、生产注册或 sourceVerified 状态；未运行 Probe entry、真实 API、TASK、SQL 或业务数据库，也未 commit/push。最坏拆分树仍受既有 5000 次/30 分钟 Context 限制，真实四项 SOURCE 能否完整完成必须由后续授权执行如实记录，本受控测试不替代真实证据。
