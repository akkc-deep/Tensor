# DATA-INTEGRITY-T02 验收记录

日期：2026-09-16。工作区：`.worktrees/data-integrity`；分支：`feat/data-integrity`；HEAD 基线：`211094822aa061c2f9217d367cc06f8ddcae3b4e`。

依据 [T02 专属设计](../task-designs/DATA-INTEGRITY-T02-design.md)，验收本地能力发现与 40 接口口径。保留既有 Studio/T01 暂存基线；本次修改仅发生在隔离工作区。

## 可观察结果

| 验收项 | 实现与验证 |
| --- | --- |
| 本地能力与下载分离 | `PluginRegistry.findIntegrity` 在 enabled=true、无 Token 时返回能力；原 `find` 仍返回空。复用一次 descriptor/readiness 快照，无额外能力或下载调用。 |
| 兼容与拒绝 | 旧插件继续下载并说明不支持检查；显式停用、重复 ID、readiness 异常、未知 ID 都返回安全原因；null ID 拒绝。重复 ID 优先，异常内容不透传。 |
| 40 项固定口径 | 独立期望表逐项核对真实 YAML，并与 manifest 精确对应：36 条 COVERAGE、2 个 STOCK_SNAPSHOT、4 个 NON_STOCK。ann_date/end_date/ipo_date/in_date 没有被下载参数或 queryMode 替换。 |
| 来源与依赖校验 | 构造时验证所有定义、描述、实际规则、完整键、目标列及参考列；重漏、未知/异源定义、错误日期类型和缺参考列均拒绝。行情三项声明真实存在的日历、上市日期、停复牌列。 |
| 结论边界 | 所有股票覆盖规则当前为 UNKNOWN；两项快照为 HISTORY_NOT_STORED，其余为 EXPECTED_SET_UNPROVEN。统计七项均 null、覆盖率 null，证据保存原范围与快照时点。NON_STOCK 无股票规则，后续执行器负责 N/A 单元。 |
| 无上游调用 | 元数据查询、规范化及规则求值均验证 client 无交互；scan/compare/issueSink 设为调用即失败，全部通过。不持有数据库或网络对象的本地策略类承担口径。 |
| 股票范围 | trim + Locale.ROOT 大写，接受六位代码加 SH/SZ/BJ；`999999.SH` 等本地未下载的合法代码仍保留；非法格式拒绝。 |
| 下载限制 | fina_indicator/balancesheet/cashflow/repurchase 撤回 RANGE 后仍有本地描述，并保留既有 batchDescriptor 的限制说明。全部既有 SINGLE/RANGE 测试通过。 |

## 实际验证

基线专项 `RegistryTest,TushareProPluginTest,IntegrityPluginContractTest` 通过（39 项）。红/绿验证：新增注册合同先因缺少 `findIntegrity` 编译失败，实现后 7 项新增及 10 项既有 Registry 测试通过；Tushare 新策略先因缺少能力/策略类编译失败，实现后 100 项策略及 12 项既有插件测试通过。

完整后端回归命令：

```sh
mvn -o -f data-plane/pom.xml \
  '-DargLine=-javaagent:/Users/qiangzhiwei/.m2/repository/org/mockito/mockito-core/5.17.0/mockito-core-5.17.0.jar' test
```

退出码 0，BUILD SUCCESS，耗时 34.480 秒。Surefire XML 汇总：

| 模块 | 测试数 | 失败 / 错误 / 跳过 |
| --- | ---: | --- |
| tensor-plugin-api | 88 | 0 / 0 / 0 |
| tensor-core | 223 | 0 / 0 / 0 |
| tensor-plugin-tushare | 461 | 0 / 0 / 0 |
| tensor-plugin-fixture | 33 | 0 / 0 / 0 |
| tensor-app | 460 | 0 / 0 / 0 |
| 合计 | 1265 | 0 / 0 / 0 |

沿用 T01 的 Mockito javaagent；既有 WireMock 测试获准绑定本机端口，Maven 全程使用离线缓存。完整日志：`/tmp/tensor-t02-full-test.log`（临时文件，不作为持久交付）。独立代码审查未发现问题；`git diff --check` 通过。

## 验证边界

本项没有 SQL 扫描、真实集合比较、候选缺口算法、任务执行或 HTTP/页面。未进行 MySQL IT 或浏览器验收；它们仍归 T03–T13。Tushare 当前 UNKNOWN 规则不能作为生产完整率证明。未重新运行要求 clean main 的 `verify-contracts.sh`，其 T01 已记录的分支门禁保持不变。改动加入 Git 暂存，未将既有其他任务改动打包提交或合回原工作区。
