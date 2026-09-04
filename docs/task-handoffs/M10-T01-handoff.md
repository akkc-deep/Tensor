# Next Task Handoff

## Handoff Type

next-task

## Task Link

- **Task board:** `docs/task-handoffs/tensor-v1-task-board.md`
- **Completed task:** `M09-T06`
- **Next task:** `M10-T01`
- **Design document:** `docs/task-designs/M10-T01-design.md`
- **Expected next status:** `READY`

## Next Task

- **Task ID:** `M10-T01`
- **Title:** Vue 依赖、Vitest、VTU 和 Playwright 配置
- **Goal:** 把当前 Vue/Vite 示例工程固定为 Node.js 24 LTS 下可重复安装、可运行真实组件 smoke test、可生产构建并可承接后续 E2E 的 `control-plane` 前端基础。
- **Scope:** 精确修改 `control-plane/package.json`、`control-plane/vite.config.js`，创建 lockfile、Vitest/Playwright 配置、测试 setup 与 `App.vue` smoke test；固定依赖、脚本、`/api` 代理和测试目录。不实现路由、API client、业务页面、Pinia 或真实 E2E，不读取 Java 实现，也不修改 M00-T03 契约。
- **Acceptance:** Node `>=24.15.0 <25` 下 `npm ci`、唯一 App smoke test 和生产构建退出 0；十个顶层依赖精确锁定；Vite 代理不 rewrite `/api/v1/**` 且不把后端地址暴露给浏览器；Vitest/VTU 清理边界和 Playwright 桌面 Chrome 基线可加载；实现提交精确包含设计指定的 5 个新增和 2 个修改文件，没有生成物或后续任务内容。

## Dependencies

### `M00-T03`

- **Artifact:** `docs/contracts/openapi-v1.yaml`；配套错误闭集为 `docs/contracts/error-codes.md`。
- **Decision:** 前端继续消费冻结的六条 `/api/v1` 业务路径、camelCase 固定 DTO/查询字段、snake_case 动态插件字段、`X-Request-Id` 与安全 `ApiError` 边界；本任务只建立 Axios 依赖和透传 `/api` 的开发代理，不提前实现 DTO 或错误映射。
- **Rationale:** 同一 OpenAPI/错误契约必须供后端和后续前端 API 层共同消费；M10-T01 先固定依赖与不改写路径的代理，才能让后续客户端在不读取 Java 实现的情况下实现契约边界。
- **Constraint:** `/api` 代理不得 rewrite `/api/v1/**`，不得加入 Token、Authorization、Cookie、请求体日志或浏览器可见的 `VITE_` 后端地址；不得改写六条路径、九个公开 schema、16 项错误码闭集或 DECIMAL/BIGINT 字符串精度规则。
- **Usage:** 用 OpenAPI 的 `/api/v1` 前缀验证 Vite 代理保留原路径；安装并锁定 Axios 供后续客户端任务消费，但把请求函数、DTO 和拦截器严格留在后续任务。
- **Readiness evidence:** M00-T03 在权威看板中为 `COMPLETED`；当前两个契约产物相对实现提交 `068f001` 无差异，已记录的严格 YAML、六路径/九 schema、16 项错误码、精度与敏感边界验证均退出 0。

## Start Here

1. `docs/task-designs/M10-T01-design.md` 全文。
2. `docs/task-handoffs/tensor-v1-task-board.md` 的 `M10-T01` 行与任务详情。
3. `docs/superpowers/plans/tensor-modules/M10-frontend-foundation.md` 的 M10-T01 任务卡和 Global Constraints。
4. `docs/contracts/openapi-v1.yaml` 与 `docs/contracts/error-codes.md`。
5. `control-plane/package.json`、`control-plane/vite.config.js`、`control-plane/src/App.vue` 与 `control-plane/src/components/HelloWorld.vue`。
6. **First action:** 切换到 Node `>=24.15.0 <25`，从仓库根确认工作树符合设计前置条件，然后只创建完整 `control-plane/src/App.spec.js` 并运行 `cd control-plane && npm run test:unit -- --run`，记录仅因缺少 `test:unit` script 导致的严格 RED。

## Risks

- 当前工作机默认 Node 为 22.22.3，不满足已冻结的 Node 24.15+ 验收范围；开始任何 npm、lockfile 或 RED/GREEN 操作前必须先取得合规运行时，不能放宽 engine、降级依赖或把 Node 22 结果当作验收证据。
- `vue-router` 的 npm latest 已进入 5.x；必须保留设计固定的 `4.6.4`，不得用无主版本边界的升级改写 lockfile。
- Playwright 浏览器安装、打包 JAR 启动和真实页面流程属于后续集成任务；本任务只验证配置 import，不得把零 E2E 用例表示为业务流程通过。
