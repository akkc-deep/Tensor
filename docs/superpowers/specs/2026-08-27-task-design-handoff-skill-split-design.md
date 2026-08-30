# Task Design / Handoff Skill Split Design

日期：2026-08-27  
状态：架构与运行时扩展已获项目所有者批准，等待书面设计审阅

## 1. 背景与问题

现有 `managing-task-handoffs` skill 负责交接状态机、权限、读取契约、验证、终态记录和后继任务准备。它能够绑定一份已经存在且已审查的任务设计，但不负责创作该设计。

这产生了两个实际缺口：

1. 使用者容易把“管理设计绑定”理解为“自动编写下一任务设计”，导致当前任务完成时才发现后继设计不存在。
2. 现有运行时只能从当前状态中已经授权的计划执行 `prepare-next`，却没有在 `IDLE` 中安装增量计划修订的动作。当前 Tensor 交接计划仅包含 `M00-T01`，因此即使提前写好 `M00-T02` 设计，运行时仍无法合法地把它加入计划后再确定后继任务。

本设计把设计创作与交接控制拆成两个独立 skill，并增加一个最小的 `install-plan-revision` 生命周期动作，支持“一任务前瞻”的连续交接。

## 2. 目标

- 新建专一的 `designing-task-contracts` skill，负责创建、修订和审查任务设计及机器可读设计包。
- 收窄 `managing-task-handoffs`：只消费已批准设计，不创作设计内容。
- 定义稳定的 `TaskDesignPackage` version 1 作为两个 skill 的边界。
- 在当前任务启动前完成一个候选后继任务的设计包，避免当前任务结束后才开始设计。
- 在当前任务归档为 `IDLE` 后，用独立的计划安装动作增量加入后继任务，再由 `prepare-next` 确定唯一合法后继。
- 保留现有权限、终态记录、manifest、读取摘要和 READY/START 分离原则。

## 3. 非目标

- 不把任务索引、文件名或仓库扫描结果提升为任务顺序权威。
- 不让设计 skill 修改 `.task-handoff`。
- 不让交接 skill 编写或猜测 `docs/task-designs/*` 的内容。
- 不在前置任务完成前伪造 COMPLETED 记录、manifest 或运行时摘要。
- 不引入第三个常驻编排 skill；两个 skill 通过明确契约协作。
- 不允许在 `IN_PROGRESS`、`VERIFYING`、`BLOCKED` 等活动状态修改计划。
- 不改变 `READY` 不等于启动授权的既有语义。

## 4. 方案选择

### 4.1 采用：设计包契约 + IDLE 计划安装

`designing-task-contracts` 产出人类设计文档和机器可读设计包；`managing-task-handoffs` 验证并消费设计包，在终态证据可用后生成正式 v3 运行时绑定。

优点：

- 创作与审计职责分离；
- 设计可以提前完成，终态证据仍延迟到真实完成后绑定；
- 计划授权和后继选择保持两个独立动作；
- 不需要在活动任务中修改计划或就绪凭据。

### 4.2 不采用：只传 Markdown

只传设计文档会迫使交接 skill 从自然语言中重新推断依赖、读写范围、测试和预算，职责仍然耦合，且无法稳定验证字段完整性。

### 4.3 不采用：`prepare-next` 同时安装计划

该方案减少一次命令，但会把计划授权、计划持久化和后继选择合并在一个突变中，使权限审计、冲突报告和恢复重放更复杂。

### 4.4 不采用：活动任务期间修改计划

在 `READY` 或执行状态修改计划会使当前计划哈希、READY 凭据和执行证据产生漂移。计划安装因此限制在 `IDLE`。

## 5. 组件边界

### 5.1 `designing-task-contracts`

触发场景：用户或协调者要求创建、修订、审查当前或候选后继任务设计；连续交接要求在当前任务启动前准备下一任务设计。

职责：

- 从用户明确指定的候选任务、已授权规划材料和允许的项目输入形成任务设计；
- 明确目标、交付物、非目标、风险、验收和测试；
- 将每个依赖分类为 `ordering` 或 `input`；
- 对 `input` 依赖列出精确 `required_deliverables` 和语义读取意图；
- 声明项目基线读取、创建/修改范围、禁止范围和验证命令；
- 生成 `TaskDesignPackage` v1，并执行结构验证；
- 仅在收到直接所有者设计批准后记录 `APPROVED` 审查状态。

边界：

- 不调用交接突变命令；
- 不写 `.task-handoff/current.yaml`、events、records、config 或 locks；
- 不把候选设计包描述为已授权计划或启动权限；
- 不填充 predecessor `source_records`、manifest entries 或 COMPLETED 记录摘要；
- 不在没有明确来源权限时读取当前任务交付物或其他项目文件。

建议文件结构：

```text
~/.agents/skills/designing-task-contracts/
├── SKILL.md
├── agents/openai.yaml
├── references/task-design-package-v1.md
└── scripts/design_package.py
```

项目产物约定：

```text
docs/task-designs/TASK-ID-design.md
docs/task-designs/TASK-ID-package.json
```

`design_package.py` 只提供确定性结构校验和规范化摘要，不替模型撰写设计。

### 5.2 `managing-task-handoffs`

保留职责：

- 交接检测、结构验证和恢复；
- 状态机、角色和授权；
- v3 读取绑定和语义选择器；
- READY 凭据和独立 START 授权；
- checkpoint、verification、complete、manifest、archive；
- 计划修订安装和确定性 `prepare-next`。

新增边界：

- 收到缺失设计时停止，不自行编写设计；
- 需要设计创作或修订时明确调用 `designing-task-contracts`；
- 只消费 `APPROVED` 且结构有效的设计包；
- 对设计包中的路径、设计摘要、任务定义和访问意图重新验证；
- 运行时摘要、终态记录和 manifest 始终由交接运行时重新生成；
- 非末尾任务启动前执行“一任务前瞻”检查；缺少已批准候选后继设计包时停止并请求所有者处理。

一任务前瞻是工作流门禁，不是任务顺序权威。候选后继只有在 `IDLE` 中进入新授权计划、并被 `prepare-next` 确定后，才成为正式后继任务。

## 6. `TaskDesignPackage` v1

设计包采用规范化 JSON，顶层字段固定为：

| 字段 | 含义 |
|---|---|
| `package_version` | 固定整数 `1` |
| `task` | ID、标题、目标、风险、交付物和非目标 |
| `design` | 设计路径、版本和完整文件 SHA-256 |
| `order_intent` | 候选顺序及其来源说明；不是授权计划 |
| `dependencies` | `ordering/input` 分类和所需交付物 |
| `design_sources` | 编写设计时获准使用的精确来源与选择器 |
| `access_intent` | 后续 v3 reads、writes、tests 和禁止范围草案 |
| `acceptance` | 可验证的完成条件 |
| `context_budget_intent` | 输入、工具输出和执行空间估算来源 |
| `review` | `DRAFT/APPROVED`、审查人、时间和证据 |

### 6.1 依赖条目

每个依赖精确包含：

```json
{
  "task_id": "M00-T01",
  "kind": "input",
  "required_deliverables": [
    "docs/traceability/tensor-v1-requirements.md"
  ],
  "read_intents": [
    {
      "id": "m00-t01-traceability",
      "path": "docs/traceability/tensor-v1-requirements.md",
      "purpose": "读取下一任务所需的需求追踪关系",
      "selector": {
        "type": "markdown-headings",
        "heading_paths": [["Tensor v1 Requirements Traceability"]]
      }
    }
  ]
}
```

`ordering` 依赖的 `required_deliverables` 和 `read_intents` 必须为空。`input` 依赖必须覆盖每个所需交付物。

### 6.2 设计包不得包含的权威数据

设计包不保存以下运行时权威字段：

- predecessor COMPLETED record revision/hash；
- manifest entries；
- READY receipt；
- START authorization；
- verification result；
- completion manifest。

这些字段只能由 `managing-task-handoffs` 在相应生命周期阶段生成和验证。

### 6.3 批准语义

`review.status: APPROVED` 只证明设计经过所有者审查，不授予计划安装、任务启动、范围扩展、完成或归档权限。每个交接突变仍需独立满足角色和授权要求。

## 7. 一任务前瞻流程

### 7.1 当前任务启动前

1. 当前任务达到 `READY`。
2. 所有者明确指定一个候选后继任务供设计，不从文件名或仓库扫描猜测。
3. `designing-task-contracts` 创建并校验候选后继设计包。
4. 所有者批准设计包。
5. `managing-task-handoffs` 验证设计包存在、摘要匹配且状态为 `APPROVED`。
6. 当前任务仍需新鲜 READY receipt 和独立 START 授权。

如果当前任务被所有者明确声明为计划末尾任务，则不要求候选后继设计包。缺少这种声明时，不以“计划当前只含一个任务”推断任务已终止。

### 7.2 当前任务完成后

1. 当前任务按 checkpoint、verification、complete 和 archive 正常关闭。
2. 归档后进入 `IDLE`，终态 record 和 deliverable manifest 已固定。
3. 所有者或受委托协调者提交新计划修订，把已批准候选任务加入计划。
4. `install-plan-revision` 验证并安装计划。
5. 协调者调用 `prepare-next`；运行时从计划和终态记录确定唯一后继。
6. 交接 skill 把设计包中的意图转换为 v3 合同，并补充真实 record、manifest、source 和 selection 摘要。
7. 解析所有 read ID、审查门禁、`mark-ready`、`validate-ready`，停在后继任务 `READY`。

如果设计包在第 3 步前漂移，必须重新审查；不能用旧批准绑定新字节。

## 8. `install-plan-revision`

### 8.1 状态转换

```text
IDLE --install-plan-revision--> IDLE
```

该动作递增交接 state revision 并写入不可变事件，但不选择当前任务、不创建执行者、不产生 READY 或 START 权限。

### 8.2 请求形状

```json
{
  "action": "install-plan-revision",
  "expected_revision": 18,
  "actor": {
    "name": "project-owner",
    "role": "owner"
  },
  "reason": "Install the next authorized task plan revision",
  "authorization": {
    "kind": "direct_user",
    "evidence": "Owner approved plan revision 2"
  },
  "payload": {
    "observed_plan_revision": 1,
    "observed_plan_sha256": "64-character lowercase SHA-256",
    "plan": {
      "revision": 2,
      "authorized_by": "project-owner",
      "authorized_at": "2026-08-27T08:00:00Z",
      "authorization_evidence": "Owner approved plan revision 2",
      "tasks": []
    }
  }
}
```

示例中的 `tasks` 在真实请求中必须是非空的完整规范化任务数组；空数组不能通过校验。

### 8.3 守卫条件

- 当前状态必须是 `IDLE`；
- 必须存在旧授权计划；首次计划仍由现有 `prepare` 安装；
- `expected_revision`、旧计划 revision 和旧计划 SHA-256 必须精确匹配；
- 新计划 revision 必须等于旧 revision 加一；
- 旧任务对象必须逐字节保持不变且顺序不变；
- 新任务只能追加，ID 唯一，正整数 order 唯一且大于旧计划最大 order；
- 新任务设计路径必须受限于项目根、是常规文件，版本为正整数，SHA-256 与当前字节一致；
- 依赖必须引用新计划内任务、无环，并满足 `ordering/input` 结构；
- 不允许通过该动作插入早于旧任务的阻塞修复任务；阻塞插入继续使用独立 plan-amendment 流程；
- actor 只能是 owner 或 coordinator；必须分别携带 `direct_user` 或 `delegated_coordinator` 权限证据；
- 任一冲突不写事件、不改变 current state。

### 8.4 持久化与恢复

成功动作写入 `install-plan-revision` 全状态事件，`after_state` 仍为 `IDLE`，但包含新计划和递增 revision。恢复重放只验证旧计划到新计划的追加不变量和状态哈希，不依赖可变的外部设计包内容。

计划中的设计 SHA-256 是安装时的权威字节绑定；设计包只是构造和审查输入。后续设计漂移会使 `prepare-next` 失败。

## 9. 错误与停止条件

建议新增稳定错误码：

| 错误码 | 含义与响应 |
|---|---|
| `DESIGN_PACKAGE_REQUIRED` | 一任务前瞻要求后继设计包；停止 START 准备 |
| `DESIGN_PACKAGE_INVALID` | 结构、路径或摘要无效；返回设计 skill 修订 |
| `DESIGN_APPROVAL_REQUIRED` | 设计仍为 DRAFT；等待直接所有者批准 |
| `PLAN_REVISION_INSTALL_REQUIRED` | IDLE 中后继不在当前计划；安装已批准计划修订 |
| `PLAN_APPEND_ONLY_VIOLATION` | 新计划修改、删除或重排旧任务；拒绝写入 |
| `PLAN_REVISION_CONFLICT` | state 或 plan 证据过期；重新读取状态 |
| `SUCCESSOR_UNAVAILABLE` | 安装计划后仍无唯一合法后继；停止，不猜测 |
| `RUNTIME_BINDING_UNAVAILABLE` | 所需 predecessor record/manifest 不存在；停止准备 |

现有稳定错误码保持兼容。若新错误与现有码语义一致，应复用现有码而不是添加别名。

## 10. 权限模型

- 设计 skill 可以起草设计；只有直接所有者可以把设计包标记为已批准。
- 设计批准不等于计划批准。
- `install-plan-revision` 是 privileged mutation，只允许 owner/direct 或 coordinator/delegated。
- executor 不能安装计划、批准设计、选择后继或自我授予 START。
- `prepare-next` 仍为 coordinator-only，并且只能从已经安装的计划确定后继。
- 新会话不继承旧会话的 owner、coordinator、executor 或授权身份。

## 11. 测试策略

### 11.1 Skill 行为测试：RED

在编写新 skill 或修改旧 skill 前，用隔离临时工作区运行无新指导的基线场景，记录真实失败：

1. 当前任务即将完成但后继设计缺失，观察 agent 是否临时编写设计或虚构后继。
2. 用户要求提前写入 predecessor record/manifest 摘要，观察 agent 是否伪造运行时证据。
3. 当前计划只有一个任务，用户要求直接 `prepare-next`，观察 agent 是否从任务索引猜测顺序。
4. 时间压力下要求跳过下一任务设计，观察 agent 是否把 READY 或对话记忆当作充分证据。

只有基线确实出现失败，才写最小 skill 指导纠正对应行为。

### 11.2 `designing-task-contracts`：GREEN/REFACTOR

- 使用相同场景验证新 skill 只产出设计和设计包；
- 验证设计包完整分类依赖、读写范围和验收；
- 验证它拒绝伪造终态记录、manifest 和 START 权限；
- 对新出现的合理化行为增加最小约束并重测；
- 运行 skill `quick_validate.py`；
- 运行 `design_package.py` 的单元测试、规范化和错误路径测试。

### 11.3 `managing-task-handoffs` 更新：独立 RED/GREEN/REFACTOR

完成并验证新 skill 后，才开始修改交接 skill：

- RED：证明旧 skill 会尝试自己补设计或无法安装增量计划；
- GREEN：要求消费已批准设计包并执行一任务前瞻检查；
- REFACTOR：覆盖设计漂移、权限压力、计划冲突和恢复场景。

### 11.4 运行时测试

`install-plan-revision` 至少覆盖：

- 非 IDLE 状态拒绝；
- executor 和缺失权限拒绝；
- state revision、plan revision、plan hash 过期拒绝且零写入；
- 旧任务被修改、删除、重排时拒绝；
- 新任务 order 冲突、依赖缺失、依赖环、设计漂移时拒绝；
- 合法追加产生单一事件、revision 加一且状态仍为 IDLE；
- 事件链恢复可重放；
- 安装后 `prepare-next` 选择唯一最小 order 后继；
- 多后继歧义仍失败关闭；
- 现有 init、prepare、start、complete、archive、insertion 和 recovery 测试不回归。

## 12. 实施顺序

遵循 skill TDD 的“每个 skill 单独完成部署流程”约束：

1. 为 `designing-task-contracts` 建立无 skill 基线场景。
2. 创建新 skill、设计包参考和确定性验证脚本。
3. 运行有 skill 场景、脚本测试和 quick validation，完成 REFACTOR。
4. 停止并确认新 skill 已验证，再开始第二个 skill。
5. 为现有 `managing-task-handoffs` 更新建立独立失败场景。
6. 测试先行实现 `install-plan-revision` 运行时动作。
7. 更新交接 SKILL、schema、lifecycle、help 和错误码目录。
8. 运行完整 handoff 测试、恢复测试和当前 Tensor 状态只读验证。
9. 将两个 skill 安装到个人跨运行时目录 `~/.agents/skills/`。

不得在同一个未经验证的批次中同时部署两个 skill。

## 13. 当前 Tensor 状态迁移

当前 Tensor 状态保持不变：M00-T01 revision 3、`READY`、计划 revision 1 且只包含 M00-T01。

迁移步骤：

1. 新 skill 完成后，为 M00-T02 创建并批准设计包；不修改当前 `.task-handoff`。
2. 在启动 M00-T01 前，由更新后的交接 skill 验证 M00-T02 设计包，满足一任务前瞻。
3. M00-T01 正常执行、验证、完成并归档到 `IDLE`。
4. 用 `install-plan-revision` 安装追加 M00-T02 的计划 revision 2。
5. 运行 `prepare-next`，绑定 M00-T01 的真实终态记录和 manifest，把 M00-T02 准备到 `READY`。

这避免重写当前状态、伪造历史或为了增加后继任务而 supersede 已就绪的 M00-T01。

## 14. 成功标准

- 两个 skill 的描述具有互斥且清晰的触发条件；
- 设计 skill 不写交接状态，交接 skill 不创作任务设计；
- 设计包不能携带或伪造运行时权威字段；
- 非末尾任务缺少已批准后继设计包时，交接 skill 不准备 START；
- 当前任务归档后可在 IDLE 中安装追加计划，并由 `prepare-next` 确定后继；
- 计划安装失败时 current、events 和 records 零变化；
- 所有新旧运行时测试通过，恢复链可以重放；
- 当前 Tensor revision 3 状态在部署前后保持结构有效且内容不被隐式修改。
