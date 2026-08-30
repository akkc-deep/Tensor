# M00-T01 建立需求追踪索引——任务设计

任务编号：`M00-T01`  
对应任务：[M00-T01](../superpowers/plans/tensor-modules/M00-contracts.md)  
实施产物：`docs/traceability/tensor-v1-requirements.md`

## 做什么

建立 Tensor v1 需求追踪索引，把 BRD、PRD、TRD、验收用例、实施模块和计划证据关联起来。

索引固定包含 37 行：

- `PRD-F-001`～`PRD-F-031`，每项功能需求一行；
- PRD 10.1～10.6，每类非功能要求一行。

输出表结构为：

```markdown
| BRD | PRD | Priority | TRD | Acceptance | Module | Evidence |
|---|---|---|---|---|---|---|
```

本任务只创建追踪文档，不修改原始需求，也不设计接口、库表或生产代码。

## 怎么做

只创建 `docs/traceability/tensor-v1-requirements.md`，不修改输入文档和生产源码。

1. 从 PRD 第 7、10 章提取 31 项功能需求和六类非功能要求；
2. 使用 PRD 第 13 章补充 BRD 关联；
3. 使用 TRD 第 21 章补充技术设计章节；
4. 使用 PRD 第 12.1 节补充验收用例；
5. 使用总实施路线图第 6 章补充实施模块和 M14 证据任务；
6. 一个需求有多个归属时合并在同一行，不创建重复行；
7. 按 PRD 编号排序，生成 37 行追踪表。

### 权威映射裁决

2026-08-30，项目所有者批准以下映射规则，用于解决原始需求矩阵未提供 NFR→BRD 和逐项 PRD/NFR→AC 交叉表的问题。

#### 非功能需求的 BRD 单元格

| PRD | BRD 单元格 |
|---|---|
| PRD 10.1 性能 | `N/A（BRD 未定义）` |
| PRD 10.2 可靠性 | `N/A（BRD 未定义）` |
| PRD 10.3 安全 | `N/A（BRD 未定义）` |
| PRD 10.4 可维护性与扩展性 | `FR-01` |
| PRD 10.5 兼容性与可用性 | `N/A（BRD 未定义）` |
| PRD 10.6 可观测性 | `N/A（BRD 未定义）` |

其中 `PRD 10.4 → FR-01` 来自 PRD 第 13 章；其余五项在 BRD 和 PRD 第 13 章均无正式映射，因此必须显式写 `N/A（BRD 未定义）`，不得改写为推断出的 BRD 分类。

#### Acceptance 表达规则

- `直接：AC-...`：列出的 AC 合计覆盖该 PRD 行的完整验收含义；
- `部分：AC-...；内联：<PRD 编号>`：列出的 AC 只覆盖部分含义，剩余部分以 PRD 第 7 或第 10 章本身作为验收依据；
- `内联：<PRD 编号>`：PRD 12.1 没有对应 AC，不附会相邻场景；
- `Evidence` 继续只表示未来计划证据责任，不能用来提升 Acceptance 的覆盖等级。

下表是 37 个 Acceptance 单元格的唯一权威取值：

| PRD | Acceptance 单元格 |
|---|---|
| PRD-F-001 | `部分：AC-001、AC-017；内联：PRD-F-001` |
| PRD-F-002 | `部分：AC-001～AC-003、AC-017；内联：PRD-F-002` |
| PRD-F-003 | `内联：PRD-F-003` |
| PRD-F-004 | `直接：AC-002` |
| PRD-F-005 | `直接：AC-017` |
| PRD-F-006 | `部分：AC-001、AC-002；内联：PRD-F-006` |
| PRD-F-007 | `部分：AC-003；内联：PRD-F-007` |
| PRD-F-008 | `部分：AC-006；内联：PRD-F-008` |
| PRD-F-009 | `部分：AC-004；内联：PRD-F-009` |
| PRD-F-010 | `内联：PRD-F-010` |
| PRD-F-011 | `直接：AC-004` |
| PRD-F-012 | `直接：AC-005` |
| PRD-F-013 | `部分：AC-007～AC-009；内联：PRD-F-013` |
| PRD-F-014 | `内联：PRD-F-014` |
| PRD-F-015 | `部分：AC-008、AC-017；内联：PRD-F-015` |
| PRD-F-016 | `部分：AC-008；内联：PRD-F-016` |
| PRD-F-017 | `内联：PRD-F-017` |
| PRD-F-018 | `直接：AC-008` |
| PRD-F-019 | `内联：PRD-F-019` |
| PRD-F-020 | `部分：AC-011；内联：PRD-F-020` |
| PRD-F-021 | `直接：AC-010` |
| PRD-F-022 | `直接：AC-009` |
| PRD-F-023 | `直接：AC-011` |
| PRD-F-024 | `部分：AC-001、AC-002、AC-012；内联：PRD-F-024` |
| PRD-F-025 | `直接：AC-013` |
| PRD-F-026 | `部分：AC-012；内联：PRD-F-026` |
| PRD-F-027 | `直接：AC-011、AC-015` |
| PRD-F-028 | `直接：AC-014` |
| PRD-F-029 | `内联：PRD-F-029` |
| PRD-F-030 | `直接：AC-016` |
| PRD-F-031 | `直接：AC-001～AC-018` |
| PRD 10.1 | `部分：AC-014；内联：PRD 10.1` |
| PRD 10.2 | `部分：AC-009、AC-010；内联：PRD 10.2` |
| PRD 10.3 | `内联：PRD 10.3` |
| PRD 10.4 | `部分：AC-017；内联：PRD 10.4` |
| PRD 10.5 | `部分：AC-015；内联：PRD 10.5` |
| PRD 10.6 | `内联：PRD 10.6` |

`PRD-F-014` 为 P1，其余功能需求为 P0，六类非功能要求标记为发布门禁。`Evidence` 只表示计划证据责任，不表示验收已经执行或通过。

上述裁决已经补齐本任务已知的缺失映射。出现裁决表未覆盖的新缺口，或既有矩阵直接冲突时，仍须停止生成并先修订设计，不在实施中猜测。

## 如何测试

| 测试场景 | 预期结果 |
|---|---|
| 表结构检查 | 表头与设计完全一致，每行固定七列 |
| 行数检查 | 数据行恰好为 37 行 |
| 唯一性检查 | 31 个 `PRD-F` 和六个非功能章节各出现一次 |
| 覆盖检查 | `FR-01`～`FR-09`、`AC-001`～`AC-018` 均可检索 |
| 映射裁决检查 | 五个无 BRD 映射的 NFR 精确使用 `N/A（BRD 未定义）`；37 个 Acceptance 单元格与权威映射裁决逐项一致 |
| 异常内容检查 | 不存在空列、重复 PRD 行或未决占位内容 |

### 验证命令

运行已登记的结构契约：

```bash
python3 -c "from pathlib import Path; import re; text=Path('docs/traceability/tensor-v1-requirements.md').read_text(encoding='utf-8'); lines=text.splitlines(); header='| BRD | PRD | Priority | TRD | Acceptance | Module | Evidence |'; i=lines.index(header); assert re.fullmatch(r'\\|(?:\\s*:?-+:?\\s*\\|){7}', lines[i+1]); body=[]; j=i+2; exec(\"while j < len(lines) and lines[j].startswith('|'):\\n body.append([cell.strip() for cell in lines[j].strip().strip('|').split('|')]); j += 1\"); assert len(body)==37; assert all(len(row)==7 and all(row) for row in body); assert all(sum(token in row[1] for row in body)==1 for token in [f'PRD-F-{n:03d}' for n in range(1,32)]); assert all(sum(token in row[1] for row in body)==1 for token in [f'10.{n}' for n in range(1,7)]); joined='\\n'.join(' | '.join(row) for row in body); assert all(token in joined for token in [f'FR-{n:02d}' for n in range(1,10)] + [f'AC-{n:03d}' for n in range(1,19)]); assert not re.search(r'(?i)\\b(?:TODO|TBD)\\b|待定|未决', joined); function_rows=[row for row in body if re.search(r'PRD-F-\\d{3}', row[1])]; assert all(('P1' in row[2]) == ('PRD-F-014' in row[1]) for row in function_rows); assert all(('P0' in row[2]) for row in function_rows if 'PRD-F-014' not in row[1]); assert all('发布门禁' in row[2] for row in body if re.search(r'10\\.[1-6]', row[1]))"
```

预期：退出码 0。

运行权威映射裁决对照；命令直接解析本设计中的两张裁决表，避免在测试中维护第二份手抄映射：

```bash
python3 -c 'from pathlib import Path; from itertools import dropwhile,takewhile; import re; d=Path("docs/task-designs/M00-T01-designs.md").read_text(encoding="utf-8"); o=Path("docs/traceability/tensor-v1-requirements.md").read_text(encoding="utf-8"); rows=lambda text,marker: [[c.strip() for c in line.strip().strip("|").split("|")] for line in list(takewhile(lambda line:line.startswith("|"),dropwhile(lambda line:not line.startswith("|---"),text.split(marker,1)[1].splitlines())))[1:]]; key=lambda value: re.search(r"PRD-F-\d{3}|PRD 10\.[1-6]",value).group(0); expected_a={key(r[0]):r[1].strip("`") for r in rows(d,"下表是 37 个 Acceptance 单元格的唯一权威取值：")}; expected_b={key(r[0]):r[1].strip("`") for r in rows(d,"#### 非功能需求的 BRD 单元格")}; actual=rows(o,"| BRD | PRD | Priority | TRD | Acceptance | Module | Evidence |"); assert len(expected_a)==len(actual)==37; assert expected_a=={key(r[1]):r[4] for r in actual}; assert expected_b=={key(r[1]):r[0] for r in actual if "PRD 10." in r[1]}'
```

预期：退出码 0；任一 BRD 或 Acceptance 单元格偏离裁决表时退出码非 0。

继续运行任务卡中的标识和空值检查：

```bash
for n in $(seq -w 1 31); do rg -q "PRD-F-0$n" docs/traceability/tensor-v1-requirements.md || exit 1; done
for n in $(seq -w 1 18); do rg -q "AC-0$n" docs/traceability/tensor-v1-requirements.md || exit 1; done
rg -n '\| *\| *$|T[B]D|T[O]DO' docs/traceability/tensor-v1-requirements.md
```

预期：前两个循环退出码 0；最后一个命令无输出。

## 如何验证

- 分别抽查插件、下载、持久化、查询和非功能要求，确认 BRD→PRD→TRD→验收链路与源矩阵一致；
- 逐行对照“权威映射裁决”，确认 BRD 和 Acceptance 没有扩大 `直接` 或 `部分` 覆盖范围；
- 确认 Module 和 Evidence 与总实施路线图第 6 章一致，且 Evidence 未被描述为已完成；
- 确认 BRD、PRD、TRD、路线图和生产源码没有变化；
- 确认 [M00-T01 任务](../superpowers/plans/tensor-modules/M00-contracts.md) 与本设计文档可以双向访问；
- 上述检查全部通过后，M00-T01 才满足设计中的完成条件。

## 依赖什么信息

### 精确基线读取声明

首任务没有前置任务交付物。以下项目既有文档是本任务唯一允许的非设计输入；交接必须按列出的 Markdown 标题范围读取最新内容，不得整文件读取，也不得扩展到其他章节：

| 基线文件 | 允许的 Markdown 标题路径 |
|---|---|
| `docs/design/Tensor_多源证券数据平台_BRD_v1.0.md` | `Tensor 多数据源证券数据下载与查看 BRD → 4. 功能需求`、`Tensor 多数据源证券数据下载与查看 BRD → 5. 页面范围`、`Tensor 多数据源证券数据下载与查看 BRD → 6. 数据存储要求`、`Tensor 多数据源证券数据下载与查看 BRD → 7. 验收标准` |
| `docs/design/Tensor_多源证券数据平台_PRD_v1.0.md` | `Tensor 多源证券数据平台 PRD → 7. 详细功能需求`、`Tensor 多源证券数据平台 PRD → 10. 非功能需求`、`Tensor 多源证券数据平台 PRD → 12. 验收方案与发布准入 → 12.1 核心验收场景`、`Tensor 多源证券数据平台 PRD → 13. BRD 追踪矩阵` |
| `docs/design/Tensor_多源证券数据平台_TRD_v1.0.md` | `Tensor 多源证券数据平台 TRD → 21. 需求追踪矩阵`、`Tensor 多源证券数据平台 TRD → 22. 风险与约束`、`Tensor 多源证券数据平台 TRD → 23. 实现与评审完成条件` |
| `docs/superpowers/plans/2026-08-25-tensor-implementation-roadmap.md` | `Tensor Module Roadmap Implementation Plan → 6. 需求追踪总矩阵` |
| `docs/superpowers/plans/tensor-modules/M00-contracts.md` | `M00 Requirements and Shared Contracts Implementation Plan → Project Planning Inputs → Task M00-T01: 建立需求追踪索引（1.0h）` |

| 依赖 | 用途 | 前置条件 |
|---|---|---|
| BRD 第 4～7 章 | 获取 `FR-01`～`FR-09` 及业务验收范围 | BRD 编号保持稳定 |
| PRD 第 7、10、12、13 章 | 获取功能需求、非功能要求、AC 和 BRD 映射 | PRD-F 与 AC 编号保持稳定 |
| TRD 第 21～23 章 | 获取 PRD→TRD 映射和完成条件 | TRD 章节编号保持稳定 |
| 总实施路线图第 6 章 | 获取实施模块和 M14 证据责任 | 模块与任务编号保持稳定 |
| M00-T01 任务定义 | 获取产物路径、步骤和任务验收要求 | 任务卡中的 Design 链接有效 |

不依赖 49 数据集附录、`docs/data-template/`、生产源码、凭证或运行环境配置。
