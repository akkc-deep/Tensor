# Pause Handoff

## Handoff Type

pause

## Task Link

- **Task board:** `docs/task-handoffs/tensor-v1-task-board.md`
- **Task ID:** `M10-T04`
- **Transition:** `IN_PROGRESS -> BLOCKED`

## Current State

- **Complete:** 已完整消费设计、17 步实施计划和进入交接，并以提交 `e609063` 记录 `READY -> IN_PROGRESS`。严格 TDD 先只创建两个完整 spec，聚焦命令在收集期仅因 `date.js` 与 `FieldError.vue` 等目标生产模块不存在而退出 1；实现提交 `0a61e3f` 以固定消息精确新增设计规定的 7 个文件。提交态聚焦为 2 files / 15 tests、全量为 6 files / 34 tests，均全部通过；Vite 构建退出 0 且只有已批准的 Element Plus chunk-size 提示；精确导出、范围、格式与禁止能力门禁通过。
- **Partial:** 当前实现逐字符合已批准设计和计划，但独立审查发现 `formatIngestedAt` 对非法日历时间与无偏移时间的处理违反设计自己的“非法时间保持原值、宿主时区不泄漏”保证；尚未修订设计/计划，也尚未写入审查修复。
- **Blocked:** 修复需要把已批准的输入边界收紧为严格日历日期、完整时分秒和必需 `Z`/数值偏移，并按设计修订流程取得项目所有者明确批准。
- **Unverified:** 严格时间戳边界的新 RED/GREEN、跨 `TZ` 确定性验证、修复后完整回归/构建和最终独立复审尚未执行；任务未达到完成门禁。

## Changed Files

- `control-plane/src/utils/date.js`：提交 `0a61e3f` 新增严格下载日期/月转换和 DATE 原样展示。
- `control-plane/src/utils/format.js`：提交 `0a61e3f` 新增入库时间和单元格格式化；其中宽松 `new Date(value)` 是待修复边界。
- `control-plane/src/utils/validation.js`：提交 `0a61e3f` 新增三个校验原语。
- `control-plane/src/utils/format.spec.js`：提交 `0a61e3f` 新增 9 项工具测试；待在现有第 6 项中加入非法日历时间、无偏移时间和显式偏移回归。
- `control-plane/src/components/common/AsyncStatePanel.vue`：提交 `0a61e3f` 新增四态可访问状态面板。
- `control-plane/src/components/common/FieldError.vue`：提交 `0a61e3f` 新增文本型字段错误 alert。
- `control-plane/src/components/common/AsyncStatePanel.spec.js`：提交 `0a61e3f` 新增 6 项真实组件测试。

## Verification

- `PATH="/Users/qiangzhiwei/.nvm/versions/node/v24.15.0/bin:$PATH" npm run test:unit -- --run`（实现前）：4 files / 19 tests 全部通过。
- `PATH="/Users/qiangzhiwei/.nvm/versions/node/v24.15.0/bin:$PATH" npm run build`（实现前）：1599 modules transformed，退出 0；仅有已批准的 chunk-size 提示。
- `npm run test:unit -- --run src/utils/format.spec.js src/components/common/AsyncStatePanel.spec.js`（仅两个测试文件）：2 suites 在收集期失败，退出 1；唯一错误是目标生产模块不存在，形成严格 RED。
- 同一聚焦命令（提交态）：2 files / 15 tests 全部通过。
- `npm run test:unit -- --run`（提交态）：6 files / 34 tests 全部通过。
- `npm run build`（提交态）：1599 modules transformed，退出 0；仅有已批准的 chunk-size 提示。
- 计划中的 Node 精确导出/边界断言：退出 0，无输出；`git diff HEAD^ HEAD --check`：退出 0；提交范围精确为 7 个新增文件，工作树为空。
- 禁止能力扫描：无输出并退出 1；受保护现有路径无差异。
- 独立审查：无 Critical/Minor；一项 Important 指出 `new Date(value)` 会归一化非法日历日期并按宿主时区解释无偏移字符串，结论为 `Ready to merge: With fixes`。
- `TZ=UTC` 与 `TZ=America/New_York` 的 Node 24 复现：`2026-02-30T02:30:15Z` 均被错误显示为 `2026-03-02 10:30:15`；`2026-08-25T02:30:15` 分别显示为 `2026-08-25 10:30:15` 和 `2026-08-25 14:30:15`，确认审查问题。
- 权威输入核对：后端 `DatasetControllerIT` 固定实际 `Instant` JSON 为 `2026-08-07T08:09:10.123Z`；OpenAPI 示例的 `ingested_at` 为 `2026-08-25T10:30:15.123+08:00`，两者均含显式偏移。

## Remaining Work

- 取得项目所有者对严格时间戳最小修订设计的明确批准，并把同一裁决写入 `docs/task-designs/M10-T04-design.md` 与任务级实施计划。
- 在 `format.spec.js` 现有第 6 项中先加入不存在日期、无偏移时间和显式数值偏移断言，取得可归因 RED。
- 最小修改 `format.js`：只在严格 `YYYY-MM-DDTHH:mm:ss[.fraction](Z|±HH:mm)` 形状、真实公历日期和有效时分秒通过后构造 `Date`；其他字符串保持原值，公开导出不变。
- 运行聚焦、全量、构建、精确导出、范围、安全及 `TZ=UTC`/`TZ=America/New_York` 确定性门禁，创建只含 `format.js`/`format.spec.js` 的审查修复提交并请求最终独立复审。
- 依据结果级证据完成 M10-T04，并按权威看板顺序准备后继任务。

## Resume Task

恢复 `M10-T04`“日期、空值、精度格式化和无障碍状态组件”，继续其目标：为 M11/M12 提供严格日期转换、时区明确且精度安全的展示/校验工具，以及无业务状态的可访问状态与字段错误组件。

## Start Here

1. `docs/task-designs/M10-T04-design.md` 全文，重点是入库时间失败边界、Tests 和 Acceptance。
2. `docs/superpowers/plans/2026-09-04-m10-t04-shared-ui-utilities.md` 全文，重点是步骤 2、7、12～17。
3. `docs/task-handoffs/tensor-v1-task-board.md` 的 M10-T04 行与详情。
4. 本交接的 Current State、Verification 与 Blocker。
5. `docs/contracts/openapi-v1.yaml` 的查询响应示例、`data-plane/tensor-app/src/test/java/com/akkc/tensor/web/DatasetControllerIT.java` 的 `ingested_at` JSON 断言，以及提交 `0a61e3f` 的 `format.js`/`format.spec.js`。
6. **First action:** 由项目所有者明确批准本交接 Blocker 中的最小修订设计；批准前不修改设计、计划、测试或生产代码。

## Blocker

- **Reason:** 独立审查与 Node 24 双时区复现证明，已批准计划规定的宽松 `new Date(value)` 与同一设计的非法输入保持和宿主时区独立保证冲突；收紧已批准公开输入边界前必须取得项目所有者明确批准。
- **Resolution condition:** 项目所有者明确批准以下可观察合同并授权继续：`formatIngestedAt` 只转换严格 `YYYY-MM-DDTHH:mm:ss[.fraction](Z|±HH:mm)`、真实日历日期及有效 24 小时时分秒；无偏移、不存在日期、非法时间/偏移和非字符串均保持原值；继续接受后端 `Z` 与 OpenAPI 示例数值偏移，公开 API、依赖、组件和七文件总体范围不变。随后把批准裁决写入任务设计/计划并取得新增 RED。

## Risks

- 仅检查 `Date#getTime()` 不能阻止日期归一化；必须在构造前校验严格形状和日历真实性。
- 允许无偏移字符串会重新引入宿主 `TZ` 差异；时区标识必须是输入合同的必填组成。
- 修复不得把 `ingested_at` 与纯 `DATE` 路径合并，也不得新增依赖或导出。
