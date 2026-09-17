# DATA-INTEGRITY-T11 验收记录

- 工作区：`.worktrees/data-integrity`，分支 `feat/data-integrity`。
- 权威设计：`docs/task-designs/DATA-INTEGRITY-T11-design.md`。
- 实施计划：`docs/superpowers/plans/2026-09-17-data-integrity-t11.md`。
- 范围：创建检查、口径预览、历史查询、导航与最小报告入口。报告内容由 T12、真实后端闭环由 T13 验收。

## 基线与测试驱动

Node 24.15.0 下既有前端基线：40 文件 / 663 项通过，0 失败或跳过。

首个纯函数 RED：空骨架下股票解析预期为有序去重股票数组但实际为 `[]`，计划单元与限额合法性断言失败；2 项失败均为行为断言，非导入失败。

浏览器首次 RED：真实 Chromium 打开 `/integrity` 时找不到“数据完整性”标题，尚未注册路由，1 项预期失败。启动本地服务及 Chromium 所需的沙箱外执行已获工具自动审核通过；服务仅监听 127.0.0.1:4178。

## 验证边界

浏览器使用 `e2e/integrity-checks.fixtures.js` 中的受控 API stub，响应以 T09 完整合同示例为基础，回传真实请求的 `X-Request-Id`，创建响应包含正确 `Location`；未绕过 T10 严格 DTO。测试数据覆盖 STOCK_DATE、STOCK_SNAPSHOT、NON_STOCK 与 descriptor=null。

此记录不把 stub 或单元测试视为真实数据库及生产完整性证明。T11 最小报告入口只显示已验证的检查编号及返回导航，不提供报告结论占位值。


## 最终结果

Node 24.15.0 下在隔离区执行：

```sh
npm --prefix control-plane test
npm --prefix control-plane run build
PLAYWRIGHT_BASE_URL=http://127.0.0.1:4178 npm --prefix control-plane run test:e2e -- e2e/integrity-checks.spec.js
```

- 完整前端：46 文件 / 696 项通过，0 失败 / 跳过；2026-09-17 14:18 最终执行，退出码 0。
- 生产构建：通过，退出码 0。保留既有大于 500 kB 的 chunk 提示，未开展无关打包重构。
- Chromium API stub：14 项通过，0 失败 / 跳过，退出码 0。
- Impeccable 对五个新增 Vue 页/组件执行一次机械检查：`[]`，无发现。
- `git diff --check` 通过。新增代码、测试、文档及截图按精确路径加入 Git；保留原暂存基线，未提交或合并。

## 已证明的行为

- 无下载 Token 的启用来源仍可本地检查；来源空、来源/能力/分类错误各自可恢复，分类失败不删能力 API。未知覆盖规则与 NON_STOCK 不被剔除。
- 股票逐项/逗号/空白/换行粘贴，有序去重，不依赖本地清单；尚未点击添加的文本也参与预览与提交。严格日期、上海当日、股票/天数/计划单元的等于及超限边界有单元测试。
- 当前快照、日期字段、依赖和未知依据显示在预览，明确确认后提交；清空接口禁提交，能力变更保留原股票/日期及失效接口，重新确认才创建新 ID。
- 首次提交期间锁定，发送完整显式 API 数组；响应丢失先按 submissionId 查回，找不到后显式同 ID / 同载荷重发；已受理查回、缓存刷新恢复不产生重复 POST。恢复不依赖当前数据源加载成功。
- 存储失败不发 POST，prepared 表单锁定后重试原快照；损坏缓存明确展示错误，只有用户完整填写并确认后才准备新请求。POST / 查回 / 存储错误独立显示。
- 历史服务端分页/筛选遵循合同，BigInt 总数 `9007199254740993` 显示精确，总页 `450359962737050` 不舍入；刷新失败保留同条件快照，改变条件清除旧结果；没有逐条详情 GET、虚构 PASS/UNKNOWN 或跨接口百分比。
- 创建与历史均进入约定报告 URL；非法 UUID 不请求；详情入口和顶部导航可返回当前页面。完整报告内容仍属 T12。

## 审查与修复

独立审查发现并复现两项 P2：迟到的分类响应会撤销用户清空；取消后重新勾选会改变 API 原顺序。两项均先以专项回归观察 RED，再修复为能力成功后独立初始化且受 selection generation 保护、已知 API 按能力原顺序选择并保留失效项。对应浏览器回归也通过。

最终独立 scoped re-review：两项均解决，Spec PASS / Quality PASS，无新增可执行问题。实现与复审明细见 `.superpowers/sdd/2026-09-17-data-integrity-t11/task-1-report.md`、`final-review.md`。初轮浏览器的消失复选框等待问题修正为 click 后断言移除；格式化中 Vite 短暂文件不可用的一轮中止，不计为通过。最终结果来自稳定代码的完整重跑。

## 布局与键盘

1440 / 1024 / 390 浏览器实际测量 `document.documentElement.scrollWidth <= innerWidth` 通过；包含长接口说明、30 只股票摘要与390px恢复错误场景。创建确认、提交和恢复可用键盘操作；表单控件有 label / 错误关联，状态使用文字与图标，历史长表格仅在自身容器滚动。

统一截图检查后修复添加按钮换行并复查；以下三张最终截图已逐一打开确认内容完整、布局符合当前 Tensor 样式：

- [1440 创建/预览/历史](data-integrity-t11/create-1440.png)
- [1024 创建/预览/历史](data-integrity-t11/create-1024.png)
- [390 创建/预览/历史](data-integrity-t11/create-390.png)
