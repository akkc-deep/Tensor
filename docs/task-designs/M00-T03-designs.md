# M00-T03 `/api/v1` OpenAPI 契约与错误码目录——任务设计

任务编号：`M00-T03`  
对应任务：[M00-T03](../superpowers/plans/tensor-modules/M00-contracts.md#task-m00-t03-冻结-rest-和错误契约15h)  
实施产物：`docs/contracts/openapi-v1.yaml`、`docs/contracts/error-codes.md`

## 做什么

冻结 Tensor v1 的 REST 与错误公开契约：创建一份 OpenAPI 3.1 文档，声明六条 `/api/v1` 业务路径、九个稳定 DTO schema、统一请求关联头和同步下载/只读分页语义；同时创建一份与 `ApiError.code` 完全一致的 16 项错误码目录。

完成后的契约必须让 M09 后端 API、M10 Axios/DTO 层和后续页面实现共享同一套路径、字段大小写、状态码、重试提示与精度规则。自有 DTO 字段统一使用 camelCase；下载参数对象中的动态插件参数名和分页记录中的动态业务字段名保持 snake_case。每个成功或失败响应都带 `X-Request-Id`，每个错误响应体都带同值 `requestId`。

本任务只创建 `docs/contracts/openapi-v1.yaml`、`docs/contracts/error-codes.md` 并为 M00-T03 任务卡补充设计链接。不实现控制器、异常处理器、插件调用、持久化、查询、Java/Vue DTO、鉴权或凭证配置；不增加健康检查路径；不修改 M00-T02 已冻结的数据集 schema、PRD、TRD 或需求追踪基线。

## 怎么做

### OpenAPI 文档骨架

`docs/contracts/openapi-v1.yaml` 使用 `openapi: 3.1.0`、UTF-8 和 `application/json`。`paths` 只声明以下六个业务操作，并为每个操作设置唯一 `operationId`：

| 方法 | 路径 | 成功体 | 输入 |
|---|---|---|---|
| GET | `/api/v1/data-sources` | `DataSourceSummary[]` | 无 |
| GET | `/api/v1/data-sources/{pluginId}/apis` | `ApiDescriptor[]` | `pluginId` path parameter |
| POST | `/api/v1/downloads` | `DownloadResponse` | `DownloadRequest` JSON body |
| GET | `/api/v1/data-sources/{pluginId}/datasets` | `DatasetSummary[]` | `pluginId` path parameter |
| GET | `/api/v1/data-sources/{pluginId}/datasets/{apiName}` | `DatasetDefinitionResponse` | `pluginId`、`apiName` path parameters |
| GET | `/api/v1/data-sources/{pluginId}/datasets/{apiName}/records` | `PageResponse` | path parameters、筛选参数和分页参数 |

所有 `pluginId`、`apiName` path schema 与 `DownloadRequest` 中的同名字段逐字复用正则 `^[a-z][a-z0-9_]{1,63}$`；路径参数统一放在对应 Path Item 的 `parameters` 中。OpenAPI 不声明 `securitySchemes`，任何 request/response property 都不得表示凭证值、配置路径或认证头。

每个 response 对象都内联声明必填响应头 `X-Request-Id`，其 schema 为非空字符串。错误 response 的媒体类型为 `application/json`，schema 引用 `ApiError`；响应体 `requestId` 与响应头值相同，这一相等约束作为运行时契约和后续 M09 测试断言。

### 九个公开 schema

`components.schemas` 至少包含且公开消费以下九个命名 schema。九个 DTO 根对象均使用 `additionalProperties: false`；动态参数对象和分页记录对象是仅有的开放键对象。

| Schema | 必填字段与约束 |
|---|---|
| `DataSourceSummary` | `pluginId`、`displayName`、`description`、`enabled`、`credentialConfigured`、`downloadAvailable`、`unavailableReason`。`pluginId` 使用统一正则，`displayName`/`description` 为非空展示字符串；三个状态字段为 boolean；`unavailableReason` 为 string 或 null，`downloadAvailable=true` 时必须为 null，否则必须为非空字符串。接口与数据集由独立路径返回，不嵌套在本摘要。 |
| `ApiDescriptor` | `apiName`、`displayName`、`category`、`queryMode`、`parameters`。`queryMode` 只允许 `trade_date|ann_date|snapshot|date_range`；`parameters` 允许空数组。参数对象必填 `name/label/type/required`，可选 `description/defaultValue/allowedValues/pattern/relatedParameter`，字段、正则、枚举和 `ENUM`/`DATE_RANGE_MEMBER` 条件与 M00-T02 schema 相同。 |
| `DatasetSummary` | `pluginId`、`apiName`、`displayName`、`category`、`queryMode`、`filters`、`fixedColumn`。`filters` 允许空数组；`fixedColumn` 是非空业务字段名。 |
| `DatasetDefinitionResponse` | 重复 `DatasetSummary` 的七个字段并增加非空 `columns`。列对象必填 `name/label/logicalType/nullable/displayOrder`，可选 `length/precision/scale/allowedValues/longText`；字段、枚举和条件约束与 M00-T02 schema 相同。响应不暴露内部 `tableName`、`businessKey` 或批量配置。 |
| `DownloadRequest` | `pluginId`、`apiName`、`params`。`params` 是允许空对象的动态键映射，通过 `propertyNames.pattern` 约束键保持插件定义的 snake_case 标识符，`additionalProperties` 只允许字符串值，以保留 `YYYYMMDD`、`YYYYMM`、代码和枚举原文。 |
| `DownloadResponse` | `requestId`、`outcome`、`pluginId`、`apiName`、`sourceRowCount`、`insertedRows`、`updatedRows`、`message`。`outcome` 只允许 `SUCCESS|EMPTY`；三个计数是非负 int64 控制值。`EMPTY` 时三个计数全为 0；`SUCCESS` 时 `sourceRowCount` 大于 0。 |
| `PageResponse` | `requestId`、`pluginId`、`apiName`、`page`、`pageSize`、`totalElements`、`totalPages`、`columns`、`items`。页码从 1 开始，`pageSize` 只允许 20、50、100 且默认 50；总数使用非负 int64，`columns` 是有序且唯一的字段名数组。`items[]` 是动态 snake_case 键对象，其 `additionalProperties.type` 固定为 `[string, "null"]`；因此 DECIMAL、BIGINT 及其他业务数值不以 JSON number 输出。 |
| `ApiError` | `requestId`、`code`、`message`、`retryable`、`fieldErrors`。五个字段都必填，`fieldErrors` 允许空数组；`code` enum 与错误目录的 16 项逐字一致。响应不得包含堆栈、SQL、内部路径或上游敏感原文。 |
| `FieldError` | `field`、`message`，均为非空字符串。动态下载参数字段名保持 snake_case，固定查询参数字段名保持 OpenAPI 中的 camelCase。 |

`filters` 在两个数据集响应中采用相同的内联对象形状 `{field, operator, controlType}`。只允许以下稳定组合，顺序沿用 M00-T02 `filters` 字段顺序：

| M00-T02 字段 | `field` | `operator` | `controlType` | records query 参数 |
|---|---|---|---|---|
| `ts_code` | `ts_code` | `EQ` | `TEXT` | `tsCode` |
| `trade_date` | `trade_date` | `BETWEEN` | `DATE_RANGE` | `tradeDateFrom`、`tradeDateTo` |
| `ann_date` | `ann_date` | `BETWEEN` | `DATE_RANGE` | `annDateFrom`、`annDateTo` |

`fixedColumn` 优先使用 M00-T02 元数据的同名字段；元数据未显式设置时使用第一列业务字段，保证 REST 响应始终提供确定值。列顺序严格按 `displayOrder`；分页记录的 `columns` 在业务字段之后追加 `source_plugin`、`source_api`、`ingested_at`。

### 请求、分页与成功语义

`POST /api/v1/downloads` 只接受一个插件和一个接口。成功且有数据与合法空数据都返回 HTTP 200；`SUCCESS` 返回本次上游行数、插入数和更新数，`EMPTY` 返回三个零计数。响应只能在持久化事务提交后形成，失败不能伪装为 `EMPTY`。

records 路径声明可选查询参数 `tsCode`、`tradeDateFrom`、`tradeDateTo`、`annDateFrom`、`annDateTo`，日期格式为 `YYYY-MM-DD`；只接受目标数据集元数据中实际声明的筛选项。`page` 默认 1、最小 1，`pageSize` 默认 50 且只允许 20/50/100。无匹配结果返回 HTTP 200、`items: []`、`totalElements: 0`、`totalPages: 0`；请求页超出有效范围时按 PRD 归一到新的最后一页。

### 错误目录与响应状态

`docs/contracts/error-codes.md` 使用固定表头 `Code | HTTP | Retryable | Meaning`，只列以下 16 行；OpenAPI `ApiError.code` 使用相同闭集：

| Code | HTTP | Retryable |
|---|---:|---|
| `PARAM_REQUIRED` | 400 | `false` |
| `PARAM_INVALID` | 400 | `false` |
| `PLUGIN_DISABLED` | 409 | `false` |
| `DATASET_MISCONFIGURED` | 409 | `false` |
| `SOURCE_AUTH_FAILED` | 502 | `false` |
| `SOURCE_PERMISSION_DENIED` | 502 | `false` |
| `SOURCE_RATE_LIMITED` | 502 | `true` |
| `SOURCE_UNAVAILABLE` | 502 | `true` |
| `SOURCE_NETWORK_ERROR` | 502 | `true` |
| `SOURCE_TIMEOUT` | 504 | `true` |
| `SOURCE_PAYLOAD_INVALID` | 502 | `true` |
| `ADAPTER_FIELD_MISSING` | 422 | `false` |
| `ADAPTER_TYPE_INVALID` | 422 | `false` |
| `PERSISTENCE_FAILED` | 500 | `true` |
| `QUERY_FAILED` | 500 | `true` |
| `INTERNAL_ERROR` | 500 | `false` |

错误目录的 `Meaning` 为每项提供面向调用方的简短含义和建议，但不复制原始上游响应。六条路径只声明该闭集能够表达的 400、409、422、500、502、504 错误响应；TRD 12.6 的宽泛 404/503 分类没有任务卡错误码且不在项目所有者批准的闭集内，本任务不新增代码或把既有代码改映射到 404/503。后续任务若要实现未知资源或整体依赖不可用语义，必须先以独立设计修订闭集，不能偏离本契约自行命名。

## 如何测试

在两个实施产物尚不存在时先运行 OpenAPI 结构检查和错误目录检查，预期均因缺少文件退出非 0；产物创建后重跑，预期退出码 0。该前后对照证明门禁能够识别缺失交付物。

解析 OpenAPI 并检查版本、六条路径、方法、九个 schema、标识符正则、响应头、下载 outcome、分页精度和安全边界：

```bash
python3 -c 'from pathlib import Path; import yaml; d=yaml.safe_load(Path("docs/contracts/openapi-v1.yaml").read_text(encoding="utf-8")); expected={"/api/v1/data-sources":"get","/api/v1/data-sources/{pluginId}/apis":"get","/api/v1/downloads":"post","/api/v1/data-sources/{pluginId}/datasets":"get","/api/v1/data-sources/{pluginId}/datasets/{apiName}":"get","/api/v1/data-sources/{pluginId}/datasets/{apiName}/records":"get"}; assert d["openapi"].startswith("3.1."); assert set(d["paths"])==set(expected); assert all(expected[p] in d["paths"][p] for p in expected); schemas=d["components"]["schemas"]; required={"DataSourceSummary","ApiDescriptor","DatasetSummary","DatasetDefinitionResponse","DownloadRequest","DownloadResponse","PageResponse","ApiError","FieldError"}; assert required <= set(schemas); assert set(schemas["DownloadResponse"]["properties"]["outcome"]["enum"])=={"SUCCESS","EMPTY"}; assert schemas["PageResponse"]["properties"]["items"]["items"]["additionalProperties"]["type"]==["string","null"]; operations=[d["paths"][p][m] for p,m in expected.items()]; assert all(all("X-Request-Id" in response["headers"] for response in operation["responses"].values()) for operation in operations); assert "securitySchemes" not in d["components"]'
```

预期：YAML 可解析，路径集合恰为六条，方法正确，九个 schema 均存在，下载 outcome 和分页记录值类型符合约束，每个响应均声明 `X-Request-Id`，且没有安全方案，退出码 0。

检查所有 path parameter 和下载标识符的正则，以及 records 查询参数名称、日期格式和分页枚举：

```bash
python3 -c 'from pathlib import Path; import yaml; d=yaml.safe_load(Path("docs/contracts/openapi-v1.yaml").read_text(encoding="utf-8")); pattern="^[a-z][a-z0-9_]{1,63}$"; paths=d["paths"]; params=[p for item in paths.values() for p in item.get("parameters",[])]; assert params and all(p["schema"].get("pattern")==pattern for p in params if p["name"] in {"pluginId","apiName"}); req=d["components"]["schemas"]["DownloadRequest"]["properties"]; assert req["pluginId"]["pattern"]==pattern and req["apiName"]["pattern"]==pattern; op=paths["/api/v1/data-sources/{pluginId}/datasets/{apiName}/records"]["get"]; query={p["name"]:p for p in op["parameters"] if p["in"]=="query"}; assert set(query)=={"tsCode","tradeDateFrom","tradeDateTo","annDateFrom","annDateTo","page","pageSize"}; assert query["pageSize"]["schema"]["enum"]==[20,50,100] and query["pageSize"]["schema"]["default"]==50; assert all(query[n]["schema"].get("format")=="date" for n in ("tradeDateFrom","tradeDateTo","annDateFrom","annDateTo"))'
```

预期：所有相关标识符正则逐字相同，查询参数集合、日期格式和分页值精确匹配设计，退出码 0。

运行任务卡的路径数量和敏感词门禁：

```bash
rg -c '^  /api/v1/' docs/contracts/openapi-v1.yaml
rg -ni 'token|password|authorization' docs/contracts/openapi-v1.yaml
```

预期：第一条输出 `6` 且退出码 0；第二条无输出且退出码 1。`credentialConfigured` 只表达布尔状态，不携带凭证内容。

检查错误目录恰有批准的 16 行，并与 OpenAPI enum 完全一致：

```bash
python3 -c 'from pathlib import Path; import re, yaml; text=Path("docs/contracts/error-codes.md").read_text(encoding="utf-8"); rows=re.findall(r"^\| `([A-Z_]+)` \| (\d{3}) \| `(true|false)` \|", text, re.M); expected={"PARAM_REQUIRED":("400","false"),"PARAM_INVALID":("400","false"),"PLUGIN_DISABLED":("409","false"),"DATASET_MISCONFIGURED":("409","false"),"SOURCE_AUTH_FAILED":("502","false"),"SOURCE_PERMISSION_DENIED":("502","false"),"SOURCE_RATE_LIMITED":("502","true"),"SOURCE_UNAVAILABLE":("502","true"),"SOURCE_NETWORK_ERROR":("502","true"),"SOURCE_TIMEOUT":("504","true"),"SOURCE_PAYLOAD_INVALID":("502","true"),"ADAPTER_FIELD_MISSING":("422","false"),"ADAPTER_TYPE_INVALID":("422","false"),"PERSISTENCE_FAILED":("500","true"),"QUERY_FAILED":("500","true"),"INTERNAL_ERROR":("500","false")}; assert len(rows)==16 and dict((code,(status,retryable)) for code,status,retryable in rows)==expected; d=yaml.safe_load(Path("docs/contracts/openapi-v1.yaml").read_text(encoding="utf-8")); assert set(d["components"]["schemas"]["ApiError"]["properties"]["code"]["enum"])==set(expected)'
```

预期：目录无缺项、增项或重复项，每项 HTTP/retryable 与批准矩阵一致，OpenAPI enum 与目录同集，退出码 0。

人工抽查两个成功示例、一个字段错误示例和分页记录示例：`EMPTY` 的三个计数必须为 0；`ApiError.requestId` 与 `X-Request-Id` 示例一致；DECIMAL/BIGINT 示例值带引号；分页控制字段仍为 JSON integer。预期四类示例都能通过各自 schema，且不出现失败伪装为空结果、业务数值精度丢失或敏感信息。

## 如何验证

- 确认 `openapi-v1.yaml` 是合法 OpenAPI 3.1 YAML，`paths` 恰含六条 `/api/v1` 业务路径，不混入 `/actuator/health` 或写操作；
- 确认九个公开 schema 的字段集合、大小写、枚举、正则、开放/封闭对象边界与本设计一致，动态下载参数和记录字段保持 snake_case，其余 DTO/查询字段保持 camelCase；
- 确认 `DataSourceSummary` 只返回凭证是否配置的 boolean 状态，两个产物都没有凭证值、认证头、SQL、堆栈或内部路径；
- 确认所有成功和失败响应都声明 `X-Request-Id`，`ApiError` 同时要求 `requestId`，错误体使用 `FieldError[]` 且无任意扩展字段；
- 确认同步下载严格区分 `SUCCESS` 与 `EMPTY`，分页只允许 20/50/100，DECIMAL/BIGINT 业务值以字符串表达而控制计数保持整数；
- 确认错误目录恰有 16 项，HTTP/retryable 与项目所有者批准矩阵一致，OpenAPI enum 与目录没有漂移，也没有自行增加 404/503 错误码；
- 确认只创建两个任务产物并补充任务设计双向链接，没有修改 Java、Vue、M00-T02 契约、需求基线或其他任务交付物；
- 运行 `python3 -c 'from pathlib import Path; p=Path("docs/task-designs/M00-T03-designs.md"); headings=[line.strip() for line in p.read_text(encoding="utf-8").splitlines() if line.startswith("## ")]; assert headings==["## 做什么","## 怎么做","## 如何测试","## 如何验证","## 依赖什么信息"]'`，预期退出码 0；
- 运行 `python3 -c 'from pathlib import Path; t=Path("docs/task-designs/M00-T03-designs.md").read_text(encoding="utf-8"); banned=["".join(map(chr,codes)) for codes in ([84,66,68],[84,79,68,79],[24453,23450],[26410,20915])]; assert not any(word in t for word in banned)'`，预期退出码 0；
- 仅当 `git rev-parse --is-inside-work-tree` 成功时执行任务卡提交命令；Git 不可用时不得初始化仓库，只保留命令输出作为后续执行证据。

## 依赖什么信息

| 依赖 | 用途 | 稳定约束或前置条件 |
|---|---|---|
| `docs/task-handoffs/tensor-v1-task-board.md` 的 M00-T03 行与详情 | 确定任务 ID、目标、范围、依赖和设计回填位置 | 权威看板是任务身份、顺序和状态的唯一来源；设计回填不改变其他单元格 |
| `docs/superpowers/plans/tensor-modules/M00-contracts.md` 的 M00-T03 任务卡 | 获取两个目标文件、六条路径、九个 schema、错误码闭集和 shell 门禁 | 边界禁止读取或修改 Java/Vue 实现，且只在 Git 可用时提交 |
| `docs/traceability/tensor-v1-requirements.md` 中映射到 PRD 5～9 的下载、适配、持久化与查看需求 | 核对元数据、动态参数、下载结果、查询、分页和失败语义 | `Evidence` 只表示后续证据责任，不代表当前验收结果 |
| `docs/contracts/dataset-definition.schema.json` 与 `docs/task-designs/M00-T02-designs.md` | 复用标识符正则、参数/列字段、枚举、筛选顺序和固定列语义 | REST 投影不得改变 M00-T02 的字段含义；筛选对象只补充页面所需 operator/controlType |
| PRD 5～9 | 确定页面所需元数据、提交/空结果、只读筛选分页、精度与用户可行动错误原则 | 失败不得伪装为空结果，凭证内容和内部诊断不得出现在页面响应 |
| TRD 12.1～12.6 | 确定基础路径、字段大小写、六个业务操作、请求/响应示例、分页包络、错误包络和宽泛 HTTP 分类 | 任务卡的 16 项错误码闭集及项目所有者批准的精确矩阵负责消除 12.6 未唯一规定的映射 |
| 2026-08-30 项目所有者裁决 | 冻结 16 项错误码各自的 HTTP 状态与 retryable 值 | 该矩阵是 `error-codes.md`、`ApiError.code` 和错误 response 的权威输入，不得由实施者调整 |
