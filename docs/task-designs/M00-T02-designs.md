# M00-T02 数据集元数据 JSON Schema 与示例——任务设计

任务编号：`M00-T02`  
对应任务：[M00-T02](../superpowers/plans/tensor-modules/M00-contracts.md#task-m00-t02-冻结数据集定义-schema15h)  
实施产物：`docs/contracts/dataset-definition.schema.json`、`docs/contracts/dataset-definition.example.yaml`

## 做什么

冻结 Tensor v1 数据集元数据的 JSON Schema 2020-12 契约，并提供一份可以通过该 schema 校验的完整 `daily` YAML 示例，使后续 M02 的值对象和 M03 的 49 份数据集 YAML 使用同一字段形状。

Schema 根对象必须且只要求以下十个字段：`pluginId`、`apiName`、`tableName`、`category`、`displayName`、`queryMode`、`parameters`、`columns`、`businessKey`、`filters`；根级另允许可选字段 `fixedColumn`。根对象和所有嵌套对象均设置 `additionalProperties: false`。

本任务只创建上述两个契约文件并为 M00-T02 任务卡补充设计链接，不创建 49 份运行时 YAML，不修改 Java、Vue、Flyway、需求基线或数据模板，也不实现 M03 的元数据加载与跨字段语义校验。

## 怎么做

创建 `docs/contracts/dataset-definition.schema.json`，使用 `$defs` 定义可复用的参数、列和业务键结构；创建 `docs/contracts/dataset-definition.example.yaml`，按 `docs/data-template/daily.json` 的字段顺序声明完整示例。

### 根对象

| 字段 | 结构与约束 |
|---|---|
| `pluginId` | 字符串，正则 `^[a-z][a-z0-9_]{1,63}$` |
| `apiName` | 字符串，正则 `^[a-z][a-z0-9_]{1,63}$` |
| `tableName` | 字符串，首期正则 `^tushare_pro__[a-z][a-z0-9_]{1,63}$` |
| `category` | 非空字符串，最大 64 字符 |
| `displayName` | 非空字符串，最大 128 字符 |
| `queryMode` | `trade_date`、`ann_date`、`snapshot`、`date_range` 之一 |
| `parameters` | 参数对象数组，允许空数组 |
| `columns` | 列对象数组，至少一项 |
| `businessKey` | 必填对象 `{mode, fields}` |
| `filters` | 唯一字段名数组，允许空数组 |
| `fixedColumn` | 可选字段名；存在时表示页面默认固定列 |

`pluginId`、`apiName` 和 `tableName` 必须分别独立满足正则；schema 不通过字符串拼接推导表名。`category` 与展示文本只做非空和长度约束，不引入任务卡未定义的新枚举。

### 参数对象

参数对象必填 `name`、`label`、`type`、`required`，允许以下可选字段：`description`、`defaultValue`、`allowedValues`、`pattern`、`relatedParameter`。

- `name` 和 `relatedParameter` 使用 `^[a-z][a-z0-9_]{1,63}$`；
- `type` 只允许 `DATE`、`DATE_RANGE_MEMBER`、`MONTH`、`TS_CODE`、`ENUM`、`TEXT`；
- `label` 为非空展示文本，`description` 为非空说明文本；
- `defaultValue` 为字符串；`allowedValues` 为非空、元素唯一的字符串数组；`pattern` 为正则字符串；
- `ENUM` 参数必须声明 `allowedValues`；`DATE_RANGE_MEMBER` 参数必须声明 `relatedParameter`。

参数名唯一性、`relatedParameter` 引用存在性及日期范围先后顺序属于 TRD 8.5 的启动/参数语义校验，由 M03/M05 实现；JSON Schema 只冻结单个参数对象的形状。

### 列对象

列对象必填 `name`、`label`、`logicalType`、`nullable`、`displayOrder`，允许可选字段 `length`、`precision`、`scale`、`allowedValues`、`longText`。

- `name` 使用 `^[a-z][a-z0-9_]{1,63}$`；`label` 为非空展示文本；
- `logicalType` 只允许 `STRING`、`TEXT`、`DATE`、`MONTH`、`LONG`、`DECIMAL`、`ENUM`；
- `nullable` 为布尔值；`displayOrder` 为从 0 开始的非负整数；`longText` 为布尔值；
- `length` 为正整数；`precision` 为 1～65；`scale` 为 0～30；`allowedValues` 为非空、元素唯一的字符串数组；
- `STRING` 和 `ENUM` 必须声明 `length`；`DECIMAL` 必须声明 `precision` 与 `scale`；`ENUM` 可以省略 `allowedValues` 以表达 TRD 允许的开放枚举。

列名与展示顺序唯一性以及 `scale <= precision` 由 M03 的语义校验负责；本任务的 `daily` 示例必须自行满足这些约束。

### 业务键、筛选和固定列

`businessKey` 固定为对象：

```yaml
businessKey:
  mode: COMPOSITE
  fields: [ts_code, trade_date]
```

`mode` 只允许 `COMPOSITE`、`FINGERPRINT`；`fields` 是至少包含一个元素、元素唯一的字段名数组。`filters` 直接使用字段名数组，例如 `[ts_code, trade_date]`。`fixedColumn` 是可选根字段，例如 `ts_code`。

JSON Schema 2020-12 不能通用表达这些动态字段名必须引用同一实例的 `columns[].name`，因此 schema 负责形状和局部约束，M03 启动校验负责跨字段引用。当前任务通过独立验收命令确认 `daily` 的业务键、筛选和固定列均引用已声明列。

### `daily` 示例

示例固定使用：

- `pluginId: tushare_pro`、`apiName: daily`、`tableName: tushare_pro__daily`、`category: market`、`displayName: 日线行情`、`queryMode: trade_date`；
- 一个必填 `trade_date` 参数，类型为 `DATE`；
- 按模板顺序声明 `ts_code`、`trade_date`、`open`、`high`、`low`、`close`、`pre_close`、`change`、`pct_chg`、`vol`、`amount`；
- `ts_code` 使用 `STRING`、`length: 16` 且不可空，`trade_date` 使用 `DATE` 且不可空，其余九列使用 `DECIMAL`、`precision: 38`、`scale: 18` 且可空；
- `displayOrder` 依次为 0～10；业务键模式为 `COMPOSITE`，字段为 `[ts_code, trade_date]`；筛选字段为 `[ts_code, trade_date]`；`fixedColumn: ts_code`。

## 如何测试

先在两个目标文件不存在时运行完整正向校验命令，预期因找不到目标文件而退出非 0；创建 schema 与示例后重新运行，预期退出码 0。这一前后对照证明校验能够检测缺失产物。

运行 schema 自校验和示例正向校验：

```bash
python3 -c 'from pathlib import Path; import json, yaml; from jsonschema import Draft202012Validator; s=json.loads(Path("docs/contracts/dataset-definition.schema.json").read_text(encoding="utf-8")); Draft202012Validator.check_schema(s); x=yaml.safe_load(Path("docs/contracts/dataset-definition.example.yaml").read_text(encoding="utf-8")); Draft202012Validator(s).validate(x)'
```

预期：schema 是合法 JSON Schema 2020-12，YAML 可解析且实例校验通过，退出码 0。

运行任务卡根契约检查：

```bash
jq -e '."$schema" == "https://json-schema.org/draft/2020-12/schema" and (.required | length == 10) and .additionalProperties == false' docs/contracts/dataset-definition.schema.json
```

预期：输出 `true`，退出码 0。

运行枚举、正则、嵌套对象封闭性和反例检查：

```bash
python3 -c 'from copy import deepcopy; from pathlib import Path; import json, yaml; from jsonschema import Draft202012Validator; s=json.loads(Path("docs/contracts/dataset-definition.schema.json").read_text(encoding="utf-8")); x=yaml.safe_load(Path("docs/contracts/dataset-definition.example.yaml").read_text(encoding="utf-8")); v=Draft202012Validator(s); bad=[]; y=deepcopy(x); y["pluginId"]="Tushare-Pro"; bad.append(y); y=deepcopy(x); y["queryMode"]="range"; bad.append(y); y=deepcopy(x); y["businessKey"]["mode"]="HASH"; bad.append(y); y=deepcopy(x); y["parameters"][0]["type"]="DAY"; bad.append(y); y=deepcopy(x); y["columns"][0]["logicalType"]="VARCHAR"; bad.append(y); y=deepcopy(x); y["unexpected"]=True; bad.append(y); y=deepcopy(x); del y["filters"]; bad.append(y); assert all(list(v.iter_errors(case)) for case in bad)'
```

预期：七个反例都产生校验错误，退出码 0。

运行 `daily` 模板顺序和跨字段引用检查：

```bash
python3 -c 'from pathlib import Path; import json, yaml; t=json.loads(Path("docs/data-template/daily.json").read_text(encoding="utf-8")); x=yaml.safe_load(Path("docs/contracts/dataset-definition.example.yaml").read_text(encoding="utf-8")); names=[c["name"] for c in x["columns"]]; assert names==t["fields"]; assert len(names)==11 and len(set(names))==11; assert [c["displayOrder"] for c in x["columns"]]==list(range(11)); assert x["businessKey"]=={"mode":"COMPOSITE","fields":["ts_code","trade_date"]}; assert x["filters"]==["ts_code","trade_date"]; assert x["fixedColumn"]=="ts_code"; refs=x["businessKey"]["fields"]+x["filters"]+[x["fixedColumn"]]; assert all(name in names for name in refs)'
```

预期：11 个字段与模板名称和顺序完全相同，展示顺序连续，业务键、筛选和固定列精确匹配且引用存在，退出码 0。

## 如何验证

- 确认 schema 根 `required` 恰好包含任务卡规定的十个字段，`fixedColumn` 仅为可选根字段，所有对象都拒绝未知字段；
- 确认三条标识符正则与四组任务卡枚举逐字一致，没有扩大或缩小取值集合；
- 确认参数、列、业务键、筛选和固定列采用本设计批准的唯一结构，参数和列均包含后端校验及页面展示所需元数据；
- 确认完整 `daily` 示例能通过 schema，11 列与 `docs/data-template/daily.json` 的 `fields` 名称和顺序一致；
- 确认七个结构反例均被拒绝，跨字段引用检查通过；
- 确认只创建两个 `docs/contracts/` 产物并补充任务设计链接，没有创建运行时 YAML，也没有修改 Java、Vue、Flyway、需求基线或模板数据；
- 确认设计文档与 M00-T02 任务卡可以双向访问，设计只有五个固定二级标题且不含未完成占位内容；
- 仅当 Git 存在时执行任务卡提交命令；Git 不存在时不得初始化仓库，并在执行摘要中保留验证输出。

## 依赖什么信息

| 依赖 | 用途 | 稳定约束或前置条件 |
|---|---|---|
| `docs/task-handoffs/tensor-v1-task-board.md` 的 M00-T02 行与详情 | 确定任务 ID、目标、范围、状态和设计回填位置 | 权威看板为任务身份与状态的唯一来源 |
| `docs/task-handoffs/M00-T02-handoff.md` | 获取直接依赖、阻塞原因、恢复顺序和首个动作 | 用户批准的字段结构满足其解阻条件 |
| `docs/superpowers/plans/tensor-modules/M00-contracts.md` 的 M00-T02 任务卡 | 获取目标文件、十个根字段、正则、枚举、示例和命令门禁 | 任务卡边界禁止读取或修改 Java/Vue |
| `docs/traceability/tensor-v1-requirements.md` 的 12 个直接输入行 | 确认插件/API/展示元数据、参数、适配、表/列/键、筛选及扩展性语义 | `Evidence` 只表示后续证据责任，不代表验收已经通过 |
| TRD 5.1、5.3、8.1、8.2 | 获取标识符、参数描述符、元数据内容和 `daily` 基准形状 | 跨字段引用与运行时加载校验留给 M03/M05，不在本任务实现 |
| `docs/data-template/manifest.json` 的 `daily` 条目 | 确认接口名和 `trade_date` 查询模式 | 清单状态与行数不作为当前契约验收结果 |
| `docs/data-template/daily.json` 的 `fields` | 冻结 `daily` 的 11 个字段名称和顺序 | 只读取字段基线，不读取或复制业务数据 |
| 2026-08-30 项目所有者裁决 | 批准 `businessKey: {mode, fields}`、`filters: string[]`、根级可选 `fixedColumn`，参数采用 TRD 5.3 字段，列必填 `name/label/logicalType/nullable/displayOrder` 且长度、精度、长文本提示可选 | 该裁决是本设计解除 M00-T02 schema 形状阻塞的权威输入 |
