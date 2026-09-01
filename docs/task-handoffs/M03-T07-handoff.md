# Next Task Handoff

## Handoff Type

next-task

## Task Link

- **Task board:** `docs/task-handoffs/tensor-v1-task-board.md`
- **Completed task:** `M03-T06`
- **Next task:** `M03-T07`
- **Design document:** `docs/task-designs/M03-T07-design.md`
- **Expected next status:** `READY`

## Next Task

- **ID:** `M03-T07`
- **Title:** 公司行动 3 数据集 YAML
- **Goal:** 在 `tensor-plugin-tushare` 运行时资源中交付 `dividend`、`repurchase` 和 `share_float` 三份公司行动数据集定义，使其经 M03-T01 loader 严格加载后形成字段、精度、参数、业务键、筛选和展示行为均唯一的不可变 `DatasetDefinition`，并使空 `dividend` 模板仍具有完整运行时元数据。
- **Scope:** 只创建设计 Files 节列出的 3 份 YAML；逐项采用设计冻结的 30 列字段顺序、类型规则、可空性规则、必填参数、3 个 COMPOSITE 键、filters 和 fixedColumn。不得修改 Java、POM、schema、records、既有 YAML、模板、其他模块或创建永久测试类。
- **Acceptance criteria:** 空 `dividend` 模板的 14 列基线通过；临时公开-loader harness 经历缺失 YAML 的 RED 后输出 `M03-T07_OK:3:30`；3 个 API、30 列顺序/类型/可空性、参数、键、筛选和固定列逐项符合设计；loader 8/8、reactor 87/87、`verify`、Enforcer、JAR 3 文件、源目录 42 文件、范围和格式门禁通过；实现提交精确包含 3 个 YAML。

## Dependencies

### `M03-T01`

- **Artifact:** `data-plane/tensor-plugin-tushare/src/main/java/com/akkc/tensor/plugin/tushare/metadata/DatasetDefinitionLoader.java`、`DatasetDefinitionLoaderTest.java`、父/模块 POM 的 YAML/schema 依赖与 `contracts/dataset-definition.schema.json` 打包结果。
- **Decision:** 运行时元数据统一由公开 `loadAll(ResourcePatternResolver, String)` 发现资源，按 JSON Schema 2020-12、private raw records、M02 构造器和 M03 跨字段规则严格校验；任何错误确定性聚合为无 cause 的 `DATASET_MISCONFIGURED`，成功结果按 `apiName` 排序且不可变，外部 schema 引用在 I/O 前被禁止。
- **Rationale:** 后续 YAML 任务只维护业务定义，不复制 schema、解析、映射或错误处理逻辑；同一 loader 同时提供运行时行为和任务级可执行验收。
- **Constraint:** 不修改或绕过 loader、M02 records、schema、POM 和现有测试；3 份 YAML 必须位于 `src/main/resources/datasets/tushare_pro/`，精确满足 loader 的严格枚举、连续 displayOrder、表名派生、引用和默认 batchSize 契约；验证不得访问网络或提交临时 harness。空 `dividend` 模板不得转化为缺字段、缺文件或跳过接口。
- **Usage:** 按完成设计创建 3 份 YAML；先验证 `dividend` 字段完整但 `data` 为空，再运行现有 loader 测试生成 Surefire 精确 classpath，用 `/private/tmp/M03T07MetadataCheck.java` 对 3 个精确 classpath 资源路径分别调用公开 loader，执行 RED/GREEN 与完整 30 列契约断言。
- **Readiness evidence:** M03-T01 在权威看板中为 `COMPLETED`；提交 `80a5a8e`、`f5ab9d4` 和 `7fed596` 已记录实现与审查修复。看板完成证据记录主控最终复跑 loader 8/8、reactor 87/87、`verify`、三层 Enforcer、精确依赖、JAR schema 唯一路径/字节、公开 API 与六文件范围全部通过，且最终范围化复审确认所有发现解决。

- **Dependency comparison:** M03-T07 只有 M03-T01 一个直接任务依赖，不存在跨依赖决策冲突；PRD A.7、TRD 9.4、M00 schema、3 个模板投影和项目所有者批准的 30 列映射是设计已冻结的业务来源，与 loader 的序列化和 Java 不变量互补。`dividend` 模板为空与完整 YAML 契约不冲突：模板固定字段集合，批准设计固定缺少样例值时的类型和可空性。

## Start Here

1. 完整读取 `docs/task-designs/M03-T07-design.md`，以其中数据集总表、完整 30 列类型映射和可空性映射作为唯一实施契约。
2. 核对 `docs/superpowers/plans/tensor-modules/M03-tushare-metadata.md` 的 Global Constraints、Task M03-T07 和 Module Gate。
3. 核对 `docs/contracts/dataset-definition.schema.json`、M03-T01 loader/test 与 `data-plane/tensor-plugin-tushare/pom.xml`，不得修改它们。
4. 核对 PRD 附录 A.7 与 TRD 9.4 的同 3 API 行；对 3 个 JSON 模板只运行设计批准的 `jq '{api_name,query_mode,params,fields,sample:(.data[0] // null)}'` 投影，不读取完整数据数组或其他模板。
5. 首个实施动作：在 `/private/tmp/M03T07MetadataCheck.java` 创建完整公开-loader harness，硬编码 30 个字段名/顺序、设计的类型字段集合、业务键和数据集总表；不创建本任务任何运行时 YAML，先执行空 `dividend` 模板断言，再执行设计 Tests 节命令并确认只因精确的 `datasets/tushare_pro/<api>.yaml` 资源缺失而非 0，取得可归因 RED。

## Risks

- `dividend` 没有样例行；未来真实上游值若不符合批准的精确字段名映射，必须通过新的设计裁决及必要迁移处理，不得静默截断、填充或改型。
- `div_proc`、`proc` 和 `share_type` 固定为 `STRING(64)`，`holder_name` 固定为 `STRING(128)`，不建立没有完整取值集的 `ENUM`；未来出现超长值或闭集需求时必须另行设计。
- `repurchase.proc` 与 `share_float.holder_name/share_type` 是不可空业务键；真实上游空值必须显式失败，不得填充伪值。
- 所有数值列必须使用 `DECIMAL(38,18)` 并由后续适配严格十进制转换，不得经 `double`。
