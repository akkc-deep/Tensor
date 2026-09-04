# Next Task Handoff

## Handoff Type

next-task

## Task Link

- **Task board:** `docs/task-handoffs/tensor-v1-task-board.md`
- **Completed task:** `M10-T02`
- **Next task:** `M10-T03`
- **Design document:** `docs/task-designs/M10-T03-design.md`
- **Expected next status:** `READY`; transition from `NOT_STARTED` only after this handoff is written.

## Next Task

- **Task:** `M10-T03` — Axios 客户端、DTO 和错误拦截。
- **Goal:** 建立唯一 Axios 实例、六个 OpenAPI 对齐的业务请求函数、逐请求 UUID 关联、JSDoc DTO 和严格区分的服务端/客户端错误边界，供 M11/M12 直接复用。
- **Scope:** 只创建 `control-plane/src/api/http.js`、`dataSources.js`、`downloads.js`、`datasets.js`、`errors.js`、`api.spec.js`；不修改依赖、配置、路由、布局、页面、样式、Java 或契约，不增加运行时 schema、重试、取消、日志、Pinia、composable 或网络集成测试。
- **Acceptance:** 唯一实例默认 `/api/v1`/130000ms 并可原子配置；六个函数的方法、路径、body/query 和字段大小写匹配 M00-T03；每次请求生成 UUID `X-Request-Id`；合法 16-code 错误形成不可变 `ApiError`，其余失败形成固定安全 `ClientError`；聚焦 12/12、完整前端 19/19、生产构建、默认/导出、安全、范围、格式和 Git 跟踪门禁达到设计预期，实施提交精确包含六个新增文件并使用 `feat(ui): add typed API client boundaries`。

## Dependencies

### `M00-T03`

- **Artifact:** `docs/contracts/openapi-v1.yaml`、`docs/contracts/error-codes.md`，实现提交 `068f001`。
- **Decision:** 六条 `/api/v1` 业务路径、九个公开 schema、固定 DTO camelCase、动态下载参数/分页业务字段 snake_case、`X-Request-Id`、`ApiError` 五字段，以及 16 项 code/HTTP/retryable 闭集均为公开合同。
- **Rationale:** M09 后端、M10 API 边界和后续页面必须共享一套路径、大小写、精度、关联 ID 和用户可行动错误语义，不能各自推导第二套协议。
- **Constraint:** 不添加业务路径或服务端错误码；不发送 Token/认证头；不把 DECIMAL/BIGINT 分页业务字符串转成 number；成功 DTO 不改名，错误 Header/body requestId 必须一致。
- **Usage:** M10-T03 逐条实现六个请求函数，以 JSDoc表达 DTO，并用 16 项矩阵验证响应错误后构造 `ApiError`。
- **Readiness evidence:** 权威看板状态为 `COMPLETED`；当前两个契约文件相对 `068f001` 的范围化 `git diff --exit-code` 退出 0。

### `M10-T01`

- **Artifact:** `control-plane/package.json`、`package-lock.json`、`vite.config.js`、`vitest.config.js`、`playwright.config.js`、`src/test/setup.js`，实现提交 `90c2029`。
- **Decision:** Node 固定为 `>=24.15.0 <25`，Axios 固定 `1.20.0`，单位测试使用 Vitest/jsdom 且收集 `src/**/*.spec.js`，`/api` 代理不 rewrite，安装与构建使用既有 npm scripts 和 lockfile。
- **Rationale:** M10-T03 应直接消费可重复的依赖、测试和代理基础，不重新选择 HTTP/test 工具或改变构建配置。
- **Constraint:** 不修改 package/lock/config/setup；Axios adapter 测试不得访问网络；客户端使用相对 `/api/v1` 以穿过既有不 rewrite 的同源代理；所有 npm 验收在 Node 24.15+ 运行。
- **Usage:** M10-T03 使用现有 Axios 创建唯一实例，用现有 Vitest 环境运行一个新增 `src/api/api.spec.js`，并复用现有完整单测和 build scripts。
- **Readiness evidence:** 权威看板状态为 `COMPLETED`；所列当前依赖/config/setup 文件相对 `90c2029` 的范围化 `git diff --exit-code` 退出 0；本机已安装 `/Users/qiangzhiwei/.nvm/versions/node/v24.15.0/bin/node`。

两项直接依赖无冲突：M00-T03 冻结浏览器必须调用的业务协议，M10-T01 提供 Axios 1.20.0、Node 24、测试环境和不 rewrite 的 `/api` 传输基础；M10-T03 设计只连接这两个边界，不修改任何依赖产物。

## Start Here

按以下顺序读取：

1. `docs/task-designs/M10-T03-design.md`；
2. `docs/superpowers/plans/2026-09-04-m10-t03-api-client.md`；
3. `docs/task-handoffs/tensor-v1-task-board.md` 的 M10-T03 行与详情；
4. `docs/superpowers/plans/tensor-modules/M10-frontend-foundation.md` 的 Global Constraints、Task M10-T03 和 Module Gate；
5. `docs/contracts/openapi-v1.yaml` 与 `docs/contracts/error-codes.md`；
6. `control-plane/package.json`、`vite.config.js`、`vitest.config.js` 和 `src/test/setup.js`。

首个实施动作：确认工作树为空并切换到已安装的 Node 24.15.0，然后只完整创建 `control-plane/src/api/api.spec.js`，运行 `npm run test:unit -- --run src/api/api.spec.js`，取得仅因五个目标生产模块尚不存在而失败的严格 RED。

## Risks

- shell 默认 `node` 仍可能是 22.22.3；必须先把 `/Users/qiangzhiwei/.nvm/versions/node/v24.15.0/bin` 放在本次命令 PATH 前端，不能放宽 engine 或用 Node 22 充当 RED/验收环境。
- 130 秒客户端超时只比当前后端 120 秒读取上限多 10 秒；M13 仍负责完整代理/应用/上游 timeout ordering，本任务不得提前改部署配置。
- Axios adapter 测试不证明真实浏览器、代理或后端联通；该端到端证据属于 M14。
- 当前生产构建会稳定出现 M10-T02 已批准的唯一 Element Plus chunk-size 提示；本任务只允许该既有提示，不得新增 warning/error 或修改 Vite 配置隐藏它。
