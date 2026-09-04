# Pause Handoff

## Handoff Type

pause

## Task Link

- **Task board:** `docs/task-handoffs/tensor-v1-task-board.md`
- **Task ID:** `M10-T02`
- **Transition:** `IN_PROGRESS -> BLOCKED`

## Current State

- **Complete:** 已按严格 TDD 写入三份测试，取得仅因缺少目标 router/layout 模块的预期 RED；随后完成设计规定的最小 router、语义化顶部导航、三个无状态 view、生产入口与桌面 CSS，并删除五个无引用示例路径。聚焦与完整单测均为 3 files/7 tests，Vite 生产构建退出 0；清理、敏感前缀、生成目录 ignore、M10-T01 配置不变和精确 16 文件范围门禁通过。
- **Partial:** 7 个新增、4 个修改和 5 个删除的实现工作树已形成；五个删除路径已暂存，其余实现路径尚未暂存，尚无实现提交。
- **Blocked:** 全量安装 Element Plus 使生产 bundle 稳定生成约 1.00 MB JS，Vite 因超过默认 500 kB 阈值发出 chunk size warning；这与设计中“任何构建警告都视为缺陷”冲突，而当前设计又同时强制 `.use(ElementPlus)`、禁止修改 Vite 配置并限制实现为 16 个文件。
- **Unverified:** 尚未进行精确 16 文件暂存、实现提交、独立审查、提交态官方 registry 重装与最终验证；任务未达到完成门禁。

## Changed Files

- `control-plane/src/router/index.js`：新增可注入 history 的 router factory、生产 router、根重定向、两个业务路由和 catch-all 404。
- `control-plane/src/router/index.spec.js`：新增 3 个真实路由合同测试。
- `control-plane/src/layouts/AppLayout.vue`：新增品牌、语义化两项顶部导航和 RouterView 主内容壳。
- `control-plane/src/layouts/AppLayout.spec.js`：新增 3 个真实导航、active、焦点和 404 恢复测试。
- `control-plane/src/views/DownloadView.vue`：新增数据下载最终标题与未完成引导。
- `control-plane/src/views/DatasetView.vue`：新增数据查看最终标题与未完成引导。
- `control-plane/src/views/NotFoundView.vue`：新增轻量 404 与返回下载页链接。
- `control-plane/src/App.vue`：以唯一 AppLayout 替换示例组件。
- `control-plane/src/App.spec.js`：把现有 smoke test 改为使用 memory router 验证真实应用壳。
- `control-plane/src/main.js`：安装生产 router、Element Plus 和 Element Plus CSS。
- `control-plane/src/style.css`：以 1280px 桌面壳、导航 active/focus 和页面卡片规则替换示例 CSS。
- `control-plane/src/components/HelloWorld.vue`：删除无引用示例组件。
- `control-plane/src/assets/hero.png`：删除无引用示例资源。
- `control-plane/src/assets/vite.svg`：删除无引用示例资源。
- `control-plane/src/assets/vue.svg`：删除无引用示例资源。
- `control-plane/public/icons.svg`：删除无引用示例资源；仍被引用的 `public/favicon.svg` 保留。

## Verification

- `source /Users/qiangzhiwei/.nvm/nvm.sh && nvm use 24.15.0 && npm ci --registry=https://registry.npmjs.org/ && npm run test:unit -- --run && npm run build`：基线安装 172 packages；1 file/1 test 通过；Vite 16 modules 构建通过。
- `npm run test:unit -- --run src/App.spec.js src/router/index.spec.js src/layouts/AppLayout.spec.js`（仅测试存在时）：退出 1；三个 suite 均只因 `src/router/index.js` 不存在而无法解析，形成预期 RED。
- 同一聚焦命令（实现后）：3 files/7 tests 全通过，无测试 warning 或 error。
- `npm test`：3 files/7 tests 全通过。
- `npm run build`：退出 0，1599 modules transformed；生成 361.64 kB CSS 与 1004.62 kB JS，同时稳定产生超过 500 kB 的 chunk size warning。
- 示例引用 `rg`：无输出，按预期退出 1；五个目标路径不存在检查退出 0。
- `git diff --check`：退出 0；`git status --short --untracked-files=all -- control-plane` 精确显示 7 新增、4 修改、5 删除。
- 敏感/浏览器前缀 `rg`：无输出，按预期退出 1；`git check-ignore` 确认 `node_modules`、`dist` 和 Playwright 产物目录均被忽略。
- `git diff --exit-code 90c2029..HEAD --` M10-T01 的 package、lock、Vite/Vitest/Playwright 配置和测试 setup：退出 0。

## Remaining Work

- 取得项目所有者对 Element Plus chunk size warning 与设计冲突的精确裁决，并把裁决写入任务设计和实施计划。
- 在不越过裁决后范围的前提下恢复任务；若批准把该已知提示作为非阻断风险，则保持当前生产实现不变并继续暂存/审查/提交；若要求消除提示，则先由项目所有者批准修改全量安装或 Vite 配置/文件范围的具体方案。
- 完成精确 16 文件暂存、固定消息实现提交、独立审查、提交态官方 registry 重装、3 files/7 tests 与生产构建最终验证，再依据结果级证据决定是否完成任务。

## Resume Task

恢复 `M10-T02`“`/downloads`、`/datasets` 路由和桌面布局”，继续其目标：交付稳定路由、语义化顶部导航、桌面应用壳、三个初始页面和可恢复 404，且不混入后续业务功能。

## Start Here

1. `docs/task-designs/M10-T02-design.md`，重点是 Approach 的生产 Element Plus 安装、TDD failure handling 和 Tests/Acceptance。
2. `docs/superpowers/plans/2026-09-04-m10-t02-routes-desktop-layout.md`，重点是 Global Constraints 及步骤 9、13、17。
3. `docs/task-handoffs/tensor-v1-task-board.md` 的 M10-T02 行与详情。
4. 本交接的 Current State、Verification 与 Blocker。
5. `control-plane/src/main.js`、`control-plane/vite.config.js` 和最近一次 Vite 构建输出。
6. **First action:** 由项目所有者明确批准“把全量 Element Plus 导致的唯一 chunk size warning 记录为非阻断风险并保持现有 16 文件实现”，或批准一个能改变相关既定约束的精确替代方案；在该裁决前不继续实施或提交。

## Blocker

- **Reason:** 已批准设计同时要求生产入口全量安装 Element Plus、禁止修改 Vite 配置、固定 16 文件范围，又规定任何构建警告都视为缺陷；实际构建证明全量 Element Plus 稳定产生超过 500 kB 的 Vite chunk size warning，现有约束无法同时满足。
- **Resolution condition:** 项目所有者明确批准并写入任务设计的一项可观察裁决：要么该唯一已知 Element Plus 体积提示不阻断本任务完成且保持现有全局安装/16 文件范围，要么给出并批准修改安装策略、Vite 配置及文件范围的精确替代合同；随后依该合同重跑对应构建门禁。

## Risks

- 仅提高 `chunkSizeWarningLimit` 会隐藏症状而不改变 bundle，不能在未获批准时作为修复。
- 改为按需注册会改变后续 M11/M12 默认可用的 Element Plus 组件合同；拆包或修改 Vite 配置会越过当前 16 文件范围。
- 当前实现尚未提交或独立审查；不能把 7/7 与构建退出 0 表示为任务已完成。
