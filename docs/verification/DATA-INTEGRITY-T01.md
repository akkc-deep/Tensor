# DATA-INTEGRITY-T01 验收记录

日期：2026-09-16。隔离工作区：`.worktrees/data-integrity`；分支：`feat/data-integrity`；HEAD 基线：`211094822aa061c2f9217d367cc06f8ddcae3b4e`。

本记录仅验收 T01 的能力/报告合同、稳定编码与 fixture 输入，依据 [任务设计](../task-designs/DATA-INTEGRITY-T01-design.md)。工作区既有 Studio 暂存基线保留；本次没有同步、合并或覆盖原工作区。

## 实现与可观察结果

| 验收项 | 实现与证据 |
| --- | --- |
| 可选能力与兼容 | `IntegrityCheckSupport` 保持 `DataSourcePlugin` 原方法不变；实际 legacy 插件仍可下载；fixture 沿用 acceptance profile 与 enabled 双门禁。 |
| 规则合同 | STOCK_DATE/STOCK_SNAPSHOT 恰好一条 COVERAGE，NON_STOCK 无执行规则；校验实现/描述一致、全插件规则 ID 唯一、必需列与参考列存在、日期列类型及声明依赖。 |
| 固定范围与证据 | 保留目标股票、原始闭区间、受理/快照时刻；完整键、防御复制、惰性键生成器、证据覆盖窗口与局部证据均有测试。 |
| 稳定版本 | API 有序，JSON 对象键规范排序，哈希包含目标/参考定义、日期轴、能力版本、全部来源和 core 规则版本；版本、业务键顺序、定义变化改变哈希。能力快照不接收凭据或运行时点。 |
| 精确报告 | Long `9007199254740993` 与 DECIMAL `1.000000000000000001` 无损；unknown=null；1/6 显示 `0.166667`；9999999/10000000 可显示 `1.000000`，精确缺失数仍为 1。财报完整键、主日期/关联日期及证据原样保存。 |
| 状态边界 | 数据结论 FAIL > UNKNOWN > WARN > PASS；全 N/A 返回 NOT_APPLICABLE；任务、单元执行状态分离；单元保留自身 reasonCode/message，不完整结果不能发布覆盖率。 |
| Fixture | PROVEN/UNCONFIRMED 固定 20 键输入，越出可靠窗口保持完整请求并降为 UNCONFIRMED；统计未计算时 UNKNOWN，已知缺失保留 FAIL，不把 null 当 0。 |

`IntegrityPluginContractTest` 位于 fixture 测试模块，借既有 api/core 依赖覆盖完整调用边界，避免新增模块依赖。共 17 项，全部通过，0 失败、0 错误、0 跳过。

## 实际验证

最终执行：

```sh
mvn -o -f data-plane/pom.xml \
  '-DargLine=-javaagent:/Users/qiangzhiwei/.m2/repository/org/mockito/mockito-core/5.17.0/mockito-core-5.17.0.jar' test
```

退出码 0，`BUILD SUCCESS`，耗时 40.860 秒。最终 Surefire XML 汇总：

| 模块 | 测试数 | 失败/错误/跳过 |
| --- | ---: | --- |
| tensor-plugin-api | 88 | 0 / 0 / 0 |
| tensor-core | 216 | 0 / 0 / 0 |
| tensor-plugin-tushare | 361 | 0 / 0 / 0 |
| tensor-plugin-fixture | 33（含本项 17） | 0 / 0 / 0 |
| tensor-app | 460 | 0 / 0 / 0 |
| 合计 | 1158 | 0 / 0 / 0 |

日志：`/tmp/tensor-integrity-final-test.log`（本机临时日志，非版本化交付）。`git diff --check` 通过。

基线初次运行因 Mockito 自附加受限失败，显式加载已有 agent 后通过。全模块首次运行因 WireMock 绑定本机端口被沙箱拒绝（`SocketException: Operation not permitted`），获得执行权限后运行最终回归；未修改项目配置或弱化测试。

红/绿验证：初次新合同测试因缺少新类型编译失败；随后通过。审查新增 fixture 未计算计数案例先出现“期望 UNKNOWN，实际 PASS”的断言失败，修复后通过。独立代码审查发现的单元原因字段缺失和 fixture 空计数误判均已修复并复查通过。

## 验证边界

`sh scripts/verify-contracts.sh` 实际退出 `maven-repository-missing`；指定本机已有 Maven 缓存后退出 `branch`。该脚本要求干净 main，而本次按用户要求在保留已有暂存基线的隔离分支工作；未改门禁，也不声明此脚本通过。

本项不实现 SQL、集合差集、通用规则执行、后台任务、HTTP 或前端。未执行 MySQL IT、完整 clean verify 或浏览器验收；这些按看板分别归 T03–T13。fixture 的可靠集合仅是验收合成输入，不证明 Tushare 生产数据完整。
