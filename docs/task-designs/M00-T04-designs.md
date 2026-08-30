# M00-T04 Tensor 任务设计与验收证据模板——任务设计

任务编号：`M00-T04`  
对应任务：[M00-T04](../superpowers/plans/tensor-modules/M00-contracts.md#task-m00-t04-建立-tensor-任务设计与验收证据模板10h)  
实施产物：`docs/superpowers/task-templates/task-design.md`、`docs/superpowers/task-templates/acceptance-evidence.md`

## 做什么

创建两份 Tensor 项目级 Markdown 模板，使 M01～M14 的后续任务分别以一致结构记录实施前设计和实施后验收证据。

`task-design.md` 只承载项目设计结论：任务身份、对应任务卡、产物，以及 `做什么`、`怎么做`、`如何测试`、`如何验证`、`依赖什么信息` 五个稳定部分。`acceptance-evidence.md` 只承载结果级证据：需求标识、变更文件、验证命令、时间、退出码、通过/失败计数、有限长度摘要和敏感信息扫描结果。

本任务只创建上述两份模板并为 M00-T04 任务卡补充设计链接。不记录或实现任务当前状态、执行者权限、事件历史、交接、归档、恢复或任务看板能力；不修改 Java、SQL、YAML 数据集、Vue、BRD、PRD、TRD、需求追踪基线或既有 M00 契约；不把任何一次真实任务的执行结果写进模板示例。

## 怎么做

本设计直接冻结两份目标文件的完整正文。下列 `BEGIN`/`END` 标记之间是 Markdown 缩进代码块：实施时对每个非空行移除恰好四个前导空格，保留空行、字符、顺序和文件末尾换行，分别写入标记所指路径；不得增删或改写正文。尖括号内容是后续使用模板时填写的输入槽位，不是 M00-T04 的设计缺口。

### `docs/superpowers/task-templates/task-design.md` 完整正文

<!-- BEGIN task-design.md -->
    # <任务编号> <任务标题>——任务设计

    任务编号：`<任务编号>`  
    对应任务：[<任务编号>](<对应任务卡相对路径>)  
    实施产物：`<产物路径>`

    ## 做什么

    写明本任务交付的具体产物、任务边界、明确排除项，以及完成后可观察到的结果。只记录已由权威来源确定的项目事实；不得把计划中的证据责任写成已经执行或通过的验收结果。

    ## 怎么做

    列出需要创建、修改或删除的精确文件路径及各自职责。说明已选定的实现方案、公开接口或数据流、兼容边界和失败处理。每个会改变实现结果的选择都必须有唯一结论；仍缺少必要事实时停止设计并提出第一个实质问题。

    ## 如何测试

    按正常、异常、边界和回归场景列出精确命令。每项写明输入、预期输出或状态、预期退出码和通过/失败计数；新增行为先记录能够识别缺失交付物或错误行为的失败结果，再记录实现后的通过结果。

    ## 如何验证

    列出证明任务目标完成的可观察验收条件，以及范围、链接、格式、敏感信息和差异检查门禁。每条验证说明对应的 requirement/acceptance 标识和预期结果；通过命令不能代替结果级验收判断。

    ## 依赖什么信息

    | 依赖 | 用途 | 稳定约束或前置条件 |
    |---|---|---|
    | `<来源路径或任务编号>` | `<该输入解决的问题>` | `<必须保持的接口、版本、决策或可用条件>` |
<!-- END task-design.md -->

### `docs/superpowers/task-templates/acceptance-evidence.md` 完整正文

<!-- BEGIN acceptance-evidence.md -->
    # <任务编号> <任务标题>——验收证据

    任务编号：`<任务编号>`  
    对应设计：[<设计文档名称>](<设计文档相对路径>)  
    证据范围：<本文件覆盖的任务产物和验收边界>

    ## Requirement coverage

    | requirement ID | acceptance criterion | evidence references |
    |---|---|---|
    | `<稳定需求或验收标识>` | `<可观察验收条件>` | `<test command ID、文件或人工证据引用>` |

    ## Changed files

    | path | responsibility | requirement IDs |
    |---|---|---|
    | `<变更文件路径>` | `<该文件在任务中的职责>` | `<对应的稳定标识>` |

    ## Verification commands

    时间使用带时区的 ISO 8601。`bounded summary` 只保留判断所需的有限输出，不复制密钥、凭证、完整堆栈或无关日志。

    | test command ID | requirement IDs | command | timestamp | exit code | pass count | fail count | bounded summary |
    |---|---|---|---|---:|---:|---:|---|
    | `<唯一命令标识>` | `<对应的稳定标识>` | `<精确命令>` | `<YYYY-MM-DDTHH:MM:SS+HH:MM>` | `<退出码>` | `<通过数>` | `<失败数>` | `<有限长度结果摘要>` |

    ## Secret scan

    命中敏感信息时只记录脱敏后的文件定位、规则和计数，不复制敏感值。

    | test command ID | command | timestamp | exit code | match count | bounded summary |
    |---|---|---|---:|---:|---|
    | `<唯一扫描标识>` | `<精确扫描命令>` | `<YYYY-MM-DDTHH:MM:SS+HH:MM>` | `<退出码>` | `<匹配数>` | `<脱敏后的有限长度摘要>` |

    ## Acceptance conclusion

    - 通过 requirement IDs：<标识列表或 None>
    - 失败 requirement IDs：<标识列表或 None>
    - 缺少证据：<缺口列表或 None>
    - 结论摘要：<只依据以上证据形成的有限长度结论>
<!-- END acceptance-evidence.md -->

两个目标文件都保持纯 Markdown，不引入脚本、生成器或运行时状态存储。目录不存在时仅创建 `docs/superpowers/task-templates/` 及两个目标文件；模板正文以本设计的逐字内容为唯一实现契约。

## 如何测试

在两个目标文件尚不存在时先运行完整正文同步门禁，预期因缺少文件退出非 0；创建后重跑，证明门禁能识别缺失产物且两份实现与完整设计逐字一致。

检查两份目标文件与本设计嵌入的完整正文逐字一致：

```bash
python3 -c 'from pathlib import Path; design=Path("docs/task-designs/M00-T04-designs.md").read_text(encoding="utf-8").splitlines(); pairs=[("task-design.md",Path("docs/superpowers/task-templates/task-design.md")),("acceptance-evidence.md",Path("docs/superpowers/task-templates/acceptance-evidence.md"))]; extract=lambda name: "\n".join((line[4:] if line else "") for line in design[design.index(f"<!-- BEGIN {name} -->")+1:design.index(f"<!-- END {name} -->")])+"\n"; assert all(target.read_text(encoding="utf-8")==extract(name) for name,target in pairs)'
```

预期：两个文件均可读且与各自 `BEGIN`/`END` 标记中的完整正文逐字一致，退出码 0。

检查任务设计模板的五个二级标题及任务卡链接提示：

```bash
python3 -c 'from pathlib import Path; p=Path("docs/superpowers/task-templates/task-design.md"); lines=p.read_text(encoding="utf-8").splitlines(); headings=[line.strip() for line in lines if line.startswith("## ")]; assert headings==["## 做什么","## 怎么做","## 如何测试","## 如何验证","## 依赖什么信息"]; text="\n".join(lines); assert "对应任务" in text and "任务编号" in text and "实施产物" in text'
```

预期：二级标题恰有五个且顺序正确，任务元数据提示存在，退出码 0。

运行任务卡要求的验收证据字段门禁：

```bash
rg -q 'test command|exit code|requirement' docs/superpowers/task-templates/acceptance-evidence.md
```

预期：至少匹配到模板中的 `requirement ID`、`test command ID` 和 `exit code` 字段，退出码 0。

检查验收证据模板的精确列集合：

```bash
python3 -c 'from pathlib import Path; t=Path("docs/superpowers/task-templates/acceptance-evidence.md").read_text(encoding="utf-8"); required=["requirement ID | acceptance criterion | evidence references","path | responsibility | requirement IDs","test command ID | requirement IDs | command | timestamp | exit code | pass count | fail count | bounded summary","test command ID | command | timestamp | exit code | match count | bounded summary"]; assert all(value in t for value in required); assert "ISO 8601" in t'
```

预期：需求、文件、命令、时间、退出码、计数、摘要和敏感信息扫描字段齐全，退出码 0。

检查两份模板没有越权承载运行时交接职责：

```bash
rg -ni 'current status|actor authority|event history|handoff path|archive action|recovery action' docs/superpowers/task-templates/task-design.md docs/superpowers/task-templates/acceptance-evidence.md
```

预期：无输出，退出码 1。模板可以指导记录验收失败事实，但不得把它转化为看板状态或恢复动作。

## 如何验证

- 确认设计已经冻结 `docs/superpowers/task-templates/task-design.md` 和 `docs/superpowers/task-templates/acceptance-evidence.md` 的两份完整正文，实施文件与嵌入正文逐字一致；
- 确认 `task-design.md` 只有五个固定二级标题，并要求任务卡双向链接、精确命令/预期结果、验收标准和依赖用途；
- 确认 `acceptance-evidence.md` 覆盖 requirement IDs、changed files、test command IDs、commands、timestamps、exit codes、pass/fail counts、bounded summaries 和 secret scan result；
- 确认两份模板职责分离：设计模板不保存实际结果，验收模板不重新设计实现，二者都不保存当前状态、权限、事件、交接、归档或恢复信息；
- 确认模板只引用稳定需求/验收标识，不声称 `docs/traceability/tensor-v1-requirements.md` 中的计划证据已经执行或通过；
- 确认只创建两个目标文件并补充 M00-T04 设计双向链接，没有修改生产代码、既有契约、需求基线或其他任务产物；
- 运行本设计“如何测试”的五项命令并得到各自注明的预期结果；
- 运行 `python3 -c 'from pathlib import Path; p=Path("docs/task-designs/M00-T04-designs.md"); headings=[line.strip() for line in p.read_text(encoding="utf-8").splitlines() if line.startswith("## ")]; assert headings==["## 做什么","## 怎么做","## 如何测试","## 如何验证","## 依赖什么信息"]'`，预期退出码 0；
- 运行 `python3 -c 'from pathlib import Path; t=Path("docs/task-designs/M00-T04-designs.md").read_text(encoding="utf-8"); banned=["".join(map(chr,codes)) for codes in ([84,66,68],[84,79,68,79],[24453,23450],[26410,20915])]; assert not any(word in t for word in banned)'`，预期退出码 0；
- 仅当 Git 可用且目标文件仍有未提交变更时执行任务卡提交命令；不得重写或压平他人已存在的提交。

## 依赖什么信息

| 依赖 | 用途 | 稳定约束或前置条件 |
|---|---|---|
| `docs/task-handoffs/tensor-v1-task-board.md` 的 M00-T04 行与详情 | 确定任务 ID、目标、范围、依赖、状态和设计回填位置 | 权威看板是任务身份、顺序和状态的唯一来源；设计回填只修改 M00-T04 的设计引用 |
| `docs/superpowers/plans/tensor-modules/M00-contracts.md` 的 M00-T04 任务卡 | 获取两个目标文件、模板职责、字段和任务卡门禁 | 只创建 Markdown 模板，不读取或修改生产实现 |
| `docs/task-designs/README.md` | 冻结项目设计文件名、五个二级标题和设计就绪门禁 | 每个任务只有一份设计，任务卡与设计双向链接，设计不记录执行状态或实际结果 |
| `docs/traceability/tensor-v1-requirements.md` | 为验收证据模板提供稳定 requirement/acceptance 标识语义 | `Evidence` 只表示后续证据责任，不代表验收已经执行或通过 |
| `docs/superpowers/plans/2026-08-25-tensor-implementation-roadmap.md` 的 1、7 节 | 约束路线图、设计、单语言修复任务和验收证据的职责边界 | 路线图不保存运行时状态；缺陷证据必须包含最小复现与精确回归责任但修复另立任务 |
| `docs/superpowers/specs/2026-08-25-tensor-module-roadmap-design.md` 的 3、9 节 | 约束契约优先、语言隔离和任务卡必须包含的设计/验证信息 | 后续任务只凭稳定契约实施；设计和证据模板不得跨语言推断内部实现 |
