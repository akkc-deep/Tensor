# Next Task Handoff

## Handoff Type

next-task

## Task Link

- **Task board:** `docs/task-handoffs/tensor-v1-task-board.md`
- **Completed task:** `M02-T05`
- **Next task:** `M03-T01`
- **Design document:** `docs/task-designs/M03-T01-design.md`
- **Expected next status:** `READY`

## Next Task

- **ID:** `M03-T01`
- **Title:** YAML 加载、schema 校验和模板对照测试框架
- **Goal:** 在 Java 21 `tensor-plugin-tushare` 模块中交付运行时完整的数据集元数据加载器，以 M00-T02 schema 严格校验 YAML，映射为 M02 的不可变 `DatasetDefinition`，执行已批准的跨字段语义校验，并以确定性 `DATASET_MISCONFIGURED` 诊断阻止任何部分成功。
- **Scope:** 修改父/模块 POM，创建 loader、一个测试类和两份测试 YAML；加入已批准的 Jackson YAML/networknt 依赖与权威 schema classpath 打包，执行严格解析、schema/M02/语义校验、聚合错误和排序不可变返回。不得创建 49 份运行时业务 YAML，不得修改 schema、M02 records、模板、既有生产 Java 或其他模块，也不得提前实现目录、参数值、数据库、适配、REST 或前端职责。
- **Acceptance criteria:** `loadAll` 返回按 `apiName` 排序的不可变有效定义；完整 `daily` 映射正确且默认 `batchSize=500`；重复键、多文档、schema/M02/M03 反例、重复 `apiName` 和零匹配均产生无绝对路径、稳定聚合的 `DATASET_MISCONFIGURED`；JAR 内 schema 与权威源字节一致；设计规定的 RED/GREEN、模块测试、Enforcer、版本、JAR、范围和格式门禁全部通过。

## Dependencies

### `M00-T02`

- **Artifact:** `docs/contracts/dataset-definition.schema.json` 与 `docs/contracts/dataset-definition.example.yaml`。
- **Decision:** 数据集 YAML 使用封闭的 JSON Schema 2020-12 对象，固定十个必填根字段及可选 `fixedColumn`；`businessKey` 为 `{mode, fields}`，`filters` 为字段名数组，参数/列使用已冻结形状，完整 `daily` 示例固定 11 列顺序。
- **Rationale:** schema 是 M03 业务 YAML 与后续 Java/REST 消费者共享的唯一序列化契约，示例提供独立于 loader 实现的正向基线。
- **Constraint:** 不修改或复制 schema 源，不放宽枚举、正则、条件字段或 `additionalProperties: false`；运行时不得读取工作区 `docs/` 或访问网络，必须消费构建打包的 classpath 副本；动态列引用和展示顺序留给本任务语义校验。
- **Usage:** 父/模块 POM 把权威 schema 原样打包为 `contracts/dataset-definition.schema.json`；loader 使用 networknt 2020-12 validator 校验每个 YAML，测试用 `daily` 示例形状核对完整映射。
- **Readiness evidence:** M00-T02 在权威看板中为 `COMPLETED`；看板记录 schema/example 正向校验、任务卡 `jq`、7/7 结构反例、`daily` 顺序/引用及精确契约门禁均退出 0，最终审查可合并且无未解决 Critical/Important。

### `M02-T03`

- **Artifact:** `data-plane/tensor-plugin-api/src/main/java/com/akkc/tensor/plugin/api/dataset/LogicalType.java`、`ColumnDefinition.java`、`BusinessKeyMode.java`、`BusinessKeyDefinition.java`、`FilterDefinition.java` 与 `DatasetDefinition.java`。
- **Decision:** 运行时元数据使用这六个不可变公共类型；`FilterDefinition` 只包装 schema 字段名，省略 YAML `batchSize` 时走 `DatasetDefinition` 十参数构造器并固定默认 500，集合保持声明顺序且复制为不可变值。
- **Rationale:** loader 直接构造已发布 M02 模型，可复用标识符、局部值、重复名、表名派生和业务键/filter/fixedColumn 引用约束，避免维护第二套公共 DTO 或重复校验契约。
- **Constraint:** 不修改 M02 类型，不增加枚举别名、值规范化或可变集合；必须把 `filters[]` 逐项包装为 `FilterDefinition`，缺省 allowed values 映射为空不可变列表，并保留 null 可选字段与列/参数声明顺序。
- **Usage:** loader 通过 private raw records 接收 schema-valid YAML，再构造 M02 值对象；本任务仅补充 `tushare_pro`、连续 display order、`scale <= precision`、related-parameter 引用、跨资源 apiName 唯一和零匹配语义。
- **Readiness evidence:** M02-T03 在权威看板中为 `COMPLETED`；看板记录最终聚焦测试 9/9、模块 `test`/`verify` 54/54、父/模块 Enforcer、范围和格式门禁通过，范围化复审无 Critical、Important 或 Minor。

- **Dependency comparison:** M00-T02 冻结 YAML/JSON Schema 形状并明确哪些动态关系交给运行时，M02-T03 冻结相同内容的 Java 公共模型与构造期不变量；两者对字段名、枚举、顺序、业务键、filters、fixedColumn 和缺省 batchSize 的决策互补且无冲突。已批准设计只在两者之间增加严格映射与明确的 M03 语义，不修改任一输入。

## Start Here

1. 完整读取 `docs/task-designs/M03-T01-design.md`。
2. 核对 `docs/superpowers/plans/tensor-modules/M03-tushare-metadata.md` 的 Global Constraints、Task M03-T01 与 Module Gate。
3. 核对 `docs/contracts/dataset-definition.schema.json`、`docs/contracts/dataset-definition.example.yaml` 和 `docs/data-template/daily.json` 的 `fields` 投影，不读取其他模板或完整数据数组。
4. 核对上述 M02-T03 六个类型、它们引用的 M02 model/descriptor 类型，以及 `data-plane/pom.xml`、`data-plane/tensor-plugin-tushare/pom.xml` 当前基线。
5. 首个实施动作：按设计先修改两项 POM，并只创建完整 `DatasetDefinitionLoaderTest.java` 与两份测试 YAML；暂不创建 `DatasetDefinitionLoader.java`，运行设计中的聚焦 Maven 命令，确认仅因 loader 缺失在 `testCompile` 退出非 0。

## Risks

- networknt 必须固定为 `1.5.9`，Jackson YAML 必须由当前 Boot BOM 解析为 `2.21.4`；版本漂移可能改变 parser/validator 行为和诊断措辞。
- schema 来自模块外的权威 `docs/contracts` 路径；实现必须保留普通 main resources，并以 JAR 路径和字节对照门禁证明打包正确且没有副本漂移。
- 错误聚合必须在所有 resolver 顺序下稳定，并去除绝对路径和底层 I/O 文本；不得为追求更详细诊断而暴露 cause 或资源 description。
