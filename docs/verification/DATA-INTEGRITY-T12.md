# DATA-INTEGRITY-T12 验收记录

- 工作区：`.worktrees/data-integrity`，分支 `feat/data-integrity`。
- 权威设计：`docs/task-designs/DATA-INTEGRITY-T12-design.md`。
- 实施计划：`docs/superpowers/plans/2026-09-17-data-integrity-t12.md`。
- 范围：报告详情、结果/问题定位、分页、GET重连及复制条件再次检查；浏览器采用API stub。T13承担真实MySQL/fixture闭环。

## 基线与测试驱动

Node24.15.0 前端基线46文件/696项通过（0失败/跳过）。

浏览器首个RED：Chromium深链接报告页面，期望“计算已完成”，实际尚无报告内容，断言失败。未把最初沙箱拒绝启动Chromium视作RED；获得工具自动审核许可后实际运行测试并观察上述行为失败。本机Vite仅监听127.0.0.1:4178。

T12浏览器stub基于T09完整合同示例，新增保存报告/单元/问题；经真实DTO解析器验证形状，未绕过严格解析。响应传回真实X-Request-Id，创建包含Location；报告和再次检查使用不同checkId，服务端按resultId/主日期/状态过滤分页。可靠95%是受控页面数据，不代表生产Tushare全集。

## 最终验证

2026-09-17，在上述隔离区使用 Node24.15.0：

| 命令 | 实测结果 |
| --- | --- |
| `npm --prefix control-plane test -- src/utils/integrityReport.spec.js src/composables/useIntegrityIssues.spec.js src/components/integrity src/views/IntegrityCheckView.spec.js src/views/IntegrityView.spec.js` | 初次专项11文件 / 58项通过；修复专项3文件29项、最终页面18项通过，exit0 |
| `npm --prefix control-plane test` | 52文件 / 740项通过，0失败/跳过，exit0 |
| `npm --prefix control-plane run build` | exit0；既有app大于500kB提示保留，本项未改打包配置 |
| `PLAYWRIGHT_BASE_URL=http://127.0.0.1:4178 npm --prefix control-plane run test:e2e -- e2e/integrity-checks.spec.js` | Chromium 39项通过，0失败/跳过，35.0秒，exit0 |
| Impeccable detect（6个本项Vue文件） | `[]`，无机械问题 |

运行日志：`/tmp/tensor-t12-unit-round2.log`、`/tmp/tensor-t12-build-final.log`、`/tmp/tensor-t12-browser-final.log`。日志为本次会话临时证据，结果与截图持久记录于本文件。浏览器第一次最终回归发现展开标题增加股票标识后测试的精确文本选择器过期；依据trace修改为summary控件定位，然后36项全量重跑通过；独立审查修复后扩展到38项并再次全量通过。来源消失的断言采用页面实际“暂无数据源”，仍验证原来源、原日期可见且无POST。

实现过程的行为RED/GREEN覆盖格式化、问题GET竞态、分页、四组件及两页。额外两个复制边界先失败再修复：大写UUID不再停留在加载态；确认A后切换到读取失败的B时清空A范围和确认，不允许错误提交。最终完整前端包含既有下载、数据查看、设置及T10生命周期回归。

## 视觉与键盘

13张截图均已实际打开检查；修复摘要网格受全局definition-list间距影响、390px关闭按钮换行及状态图标后，最终浏览器再生成全部截图，复看三个宽度的完整报告与390px中断长值。1440/1024/390无文档横向溢出；长结果和问题表在各自容器滚动，筛选、问题打开/关闭及焦点还原可用。

- 报告：[1440](data-integrity-t12/report-1440.png)、[1024](data-integrity-t12/report-1024.png)、[390](data-integrity-t12/report-390.png)。
- 全UNKNOWN：[1440](data-integrity-t12/全UNKNOWN-1440.png)、[1024](data-integrity-t12/全UNKNOWN-1024.png)、[390](data-integrity-t12/全UNKNOWN-390.png)。
- 全N/A：[1440](data-integrity-t12/全N-A-1440.png)、[1024](data-integrity-t12/全N-A-1024.png)、[390](data-integrity-t12/全N-A-390.png)。
- 中断/长值：[1440](data-integrity-t12/中断与长值-1440.png)、[1024](data-integrity-t12/中断与长值-1024.png)、[390](data-integrity-t12/中断与长值-390.png)；[空结果](data-integrity-t12/empty-results.png)。

## 审查与交付

独立源码审查指出三个功能问题（更换复制来源后的阻断、被拒绝pending后复制loading/error被遮挡、日期提示使用未应用草稿）及一个排队文案遗漏。四个反例先RED（4失败/25通过）后GREEN（3文件/29通过）；完整前端739项及38项浏览器均在修复后通过。新增浏览器验证停用来源切换、拒绝后复制失败重试，并在三宽度下核对日期提示始终描述已应用筛选。定向复审又补齐非法复制编号的相同状态转换（1失败/17通过→18/18通过），最终完整前端740项、浏览器39项、构建通过。两轮定向复审均已收口，4项全部ADDRESSED，无遗留问题；最终集成审查通过：无新增Critical/Important/Minor；另核对T10调用/取消衔接与1440/390实图。T12已记录COMPLETED。仅本项前端功能与stub证据纳入T12；真实MySQL、真实fixture浏览器与clean-main合同门禁仍属于T13。

## 验收映射

| 设计要求 | 验证位置 |
| --- | --- |
| BigInt/null/0、十进制比例、完整键标量 | `src/utils/integrityReport.spec.js` |
| 按需问题GET、取消、generation、同条件失败保留 | `src/composables/useIntegrityIssues.spec.js` |
| 汇总与单元执行/数据结论分开、存档口径、不完整标记、精确分页 | `src/components/integrity/IntegritySummary.spec.js`、`IntegrityResultsTable.spec.js`、`IntegrityPagination.spec.js` |
| 完整问题键/关联日期/旧规则名称、主日期过滤和重置 | `src/components/integrity/IntegrityIssuesTable.spec.js` 与浏览器三宽度问题流程 |
| 深链接、非法编号、任务切换、终态停止、GET重连 | `src/views/IntegrityCheckView.spec.js`、既有真实T10 flow tests及浏览器重连场景 |
| scope复制、当前能力重新确认、pending优先、迟到读取 | `src/views/IntegrityView.spec.js` 与浏览器再次检查场景 |
| 三宽度/空页/长值/UNKNOWN/N/A/中断，旧报告保持 | `e2e/integrity-checks.spec.js` |

以上路径均相对 `control-plane/`，最终通过数量以上节实测结果为准。


交付保留在 `.worktrees/data-integrity` / `feat/data-integrity`，HEAD仍为2110948；本项源码、测试、验收和截图按精确路径加入Git。没有提交混合基线、合并或推送；临时Vite进程已正常停止。

T12完成后按Order准备T13，先完成并链接[专属设计](../task-designs/DATA-INTEGRITY-T13-design.md)，再写[交接](../task-handoffs/DATA-INTEGRITY-T13-handoff.md)，看板T13为READY；未开始后继实现。最终clean-main合同门禁前提已明确交接，不属于本次前端验收结果。
