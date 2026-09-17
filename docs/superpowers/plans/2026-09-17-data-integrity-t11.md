# DATA-INTEGRITY-T11 Implementation Plan

> **For agentic workers:** Use superpowers:subagent-driven-development for implementation and independent review. Preserve the existing mixed staged baseline; stage exact paths, do not commit or merge.

**Goal:** 在隔离区完成完整性创建、口径预览和历史页面。

**Architecture:** 用现有 T10 composable 管理创建与恢复；纯函数校验范围，表单与预览组件只负责交互，历史组件独立管理只读分页。页面沿用现有 Tensor 视觉系统。

**Tech Stack:** Vue 3, Element Plus, Vitest, Playwright, Node 24.15.0.

**Spec:** `docs/task-designs/DATA-INTEGRITY-T11-design.md`（完整设计是实现细节的权威）。

## Global Constraints

- 工作区 `.worktrees/data-integrity`，分支 `feat/data-integrity`；最小代码、新增文件加入 Git，保留旧暂存内容。
- 不改后端、T10 HTTP/DTO/状态语义或下载流程；无 Token 仍可本地检查。
- 保留完整范围、原提交 ID；计数和历史分页保持 BigInt。无跨接口总完整率、无历史 N+1。
- 1440/1024/390 无页面横溢，键盘可用；stub 与真实后端证据分开。

### Task 1: 创建、预览、历史与导航

**Files:** 新增 `control-plane/src/utils/integrityForm.js`、`components/integrity/{IntegrityCheckForm,IntegrityScopePreview,IntegrityCheckHistory}.vue`、`views/{IntegrityView,IntegrityCheckView}.vue` 及各自 spec；修改 `router/index.js`、`layouts/AppLayout.vue` 及相关导航 spec；仅必要时补充 scoped 或共享样式。

**Interfaces:** `parseIntegritySymbols(text, pluginId)` 返回有序去重股票数组；`validateIntegritySelection(selection, capability, today)` 返回 `{valid,errors,plannedUnits,rangeDays}`。form 使用 v-model 选择值，预览只读，历史自行 GET。页面使用 T10 `loadCapabilities/confirmCapability/prepareSubmission/submit/recoverSubmission/resendSubmission/dispose`；历史用 `listIntegrityChecks(criteria,{signal})`。

- [x] 首个 RED：为未实现函数提供空返回骨架，测试 `parseIntegritySymbols('999999.sz，999999.SZ 600000.sh','tushare_pro')` 得到 `['999999.SZ','600000.SH']`；两股票、一 STOCK_DATE、一 NON_STOCK 应为3单元，limits=3可提交、limits=2不可提交。实际观察断言失败。
- [x] 实现纯解析和验证，补齐空股票/接口、格式、严格日期、上海当天、闭区间天数、未知API/描述和每项限额测试。
- [x] 先写真实组件行为测试，再实现完整专属设计第1–6节：来源/分类并发隔离、默认全选、输入锁定、待添加文本提交、能力变化保留已失效选择、确认及恢复、请求ID分别显示、历史精确分页/失败保留与最小详情入口。
- [x] 运行专项及完整单元测试，修复由本项引起的回归，构建成功；变更仅按精确路径暂存。

```sh
PATH=/Users/qiangzhiwei/.nvm/versions/node/v24.15.0/bin:$PATH npm --prefix control-plane test -- src/utils/integrityForm.spec.js src/components/integrity src/views/IntegrityView.spec.js src/views/IntegrityCheckView.spec.js src/layouts/AppLayout.spec.js
```

### Task 2: 浏览器验收、审查与交接

**Files:** 新增 `control-plane/e2e/integrity-checks.spec.js`，必要时添加专用 fixture；`docs/verification/DATA-INTEGRITY-T11.md`、T12设计/交接及任务看板。

**Interfaces:** API stub 使用 T09 完整合同样例和请求的 X-Request-Id；创建返回带 Location 的202。通过可访问名称选择控件，不绕过严格 DTO。

- [x] 在页面实现同时准备 stub 测试：三宽度创建/预览/历史/键盘，能力更新/移除接口、来源/分类错误、响应丢失查回、空查回原ID重发、缓存恢复、历史大计数/筛选/错误、入口UUID。
- [x] 独立端口4178运行Vite和Playwright；统一截图检查三宽度，修复实测缺陷，运行机械设计检测一次。
- [x] 独立审查 T11 范围的规格与代码；修复有效问题，并复核修复。
- [x] 最终前端回归、构建及stub通过后记录具体结果，将T11置COMPLETED。
- [x] 按看板Order准备T12详细设计并回填，随后写next-task交接并置READY；不开始T12实施。
- [x] 精确暂存本项文件，检查差异无空白错误；保留隔离区，未提交/合并。

```sh
PATH=/Users/qiangzhiwei/.nvm/versions/node/v24.15.0/bin:$PATH npm --prefix control-plane test
PATH=/Users/qiangzhiwei/.nvm/versions/node/v24.15.0/bin:$PATH npm --prefix control-plane run build
PATH=/Users/qiangzhiwei/.nvm/versions/node/v24.15.0/bin:$PATH PLAYWRIGHT_BASE_URL=http://127.0.0.1:4178 npm --prefix control-plane run test:e2e -- e2e/integrity-checks.spec.js
```
