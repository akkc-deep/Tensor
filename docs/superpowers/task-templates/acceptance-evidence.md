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
