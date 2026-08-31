# Next Task Handoff

## Handoff Type

next-task

## Task Link

- **Task board:** `docs/task-handoffs/tensor-v1-task-board.md`
- **Completed task:** `M03-T03`
- **Next task:** `M03-T04`
- **Design document:** `docs/task-designs/M03-T04-design.md`
- **Expected next status:** `READY`

## Next Task

- **ID:** `M03-T04`
- **Title:** 交易与资金 6 数据集 YAML
- **Goal:** 在 `tensor-plugin-tushare` 运行时资源中交付 6 份交易与资金数据集定义，使其经 M03-T01 loader 严格加载后形成字段、精度、参数、业务键、筛选和展示行为均唯一的不可变 `DatasetDefinition`。
- **Scope:** 只创建设计 Files 节列出的 6 份 YAML；逐项采用设计冻结的 71 列类型/长度/可空性、必填参数、6 个 COMPOSITE 键、filters 和 fixedColumn。不得修改 Java、POM、schema、records、既有 YAML、模板、其他模块或创建永久测试类。
- **Acceptance criteria:** 临时公开-loader harness 经历缺失 YAML 的 RED 后输出 `M03-T04_OK:6`；6 个 API、71 列顺序/类型、参数、键、筛选和固定列逐项符合设计；loader 8/8、reactor 87/87、`verify`、Enforcer、JAR 6 文件、源目录 24 文件、范围和格式门禁通过；实现提交精确包含 6 个 YAML。

## Dependencies

### `M03-T01`

- **Artifact:** `data-plane/tensor-plugin-tushare/src/main/java/com/akkc/tensor/plugin/tushare/metadata/DatasetDefinitionLoader.java`、`DatasetDefinitionLoaderTest.java`、父/模块 POM 的 YAML/schema 依赖与 `contracts/dataset-definition.schema.json` 打包结果。
- **Decision:** 运行时元数据统一由公开 `loadAll(ResourcePatternResolver, String)` 发现资源，按 JSON Schema 2020-12、private raw records、M02 构造器和 M03 跨字段规则严格校验；任何错误确定性聚合为无 cause 的 `DATASET_MISCONFIGURED`，成功结果按 `apiName` 排序且不可变，外部 schema 引用在 I/O 前被禁止。
- **Rationale:** 后续 YAML 任务只维护业务定义，不复制 schema、解析、映射或错误处理逻辑；同一 loader 同时提供运行时行为和任务级可执行验收。
- **Constraint:** 不修改或绕过 loader、M02 records、schema、POM 和现有测试；6 份 YAML 必须位于 `src/main/resources/datasets/tushare_pro/`，精确满足 loader 的严格枚举、连续 displayOrder、表名派生、引用和默认 batchSize 契约；验证不得访问网络或提交临时 harness。
- **Usage:** 按完成设计创建 6 份 YAML；先运行现有 loader 测试生成 Surefire 精确 classpath，再用 `/private/tmp/M03T04MetadataCheck.java` 对 6 个精确 classpath 资源路径分别调用公开 loader，执行 RED/GREEN 与完整 6 文件契约断言。
- **Readiness evidence:** M03-T01 在权威看板中为 `COMPLETED`；提交 `80a5a8e`、`f5ab9d4`、`7fed596` 和看板提交 `78f4c31` 已记录实现、修复和完成证据。主控最终复跑 loader 8/8、reactor 87/87、`verify`、三层 Enforcer、精确依赖、JAR schema 唯一路径/字节、公开 API 与六文件范围全部通过，最终范围化复审确认所有发现解决且无新 Critical/Important breakage。

- **Dependency comparison:** M03-T04 只有 M03-T01 一个直接任务依赖，不存在跨依赖决策冲突；PRD A.3、TRD 9.4、M00 schema、6 个模板投影和项目所有者批准的 71 列策略是设计已冻结的业务来源，与 loader 的序列化和 Java 不变量互补。

## Start Here

1. 完整读取 `docs/task-designs/M03-T04-design.md`，以其中数据集总表、可空性规则和 71 列类型图作为唯一实施契约。
2. 核对 `docs/superpowers/plans/tensor-modules/M03-tushare-metadata.md` 的 Global Constraints、Task M03-T04 和 Module Gate。
3. 核对 `docs/contracts/dataset-definition.schema.json`、M03-T01 loader/test 与 `data-plane/tensor-plugin-tushare/pom.xml`，不得修改它们。
4. 核对 PRD 附录 A.3 与 TRD 9.4 的同 6 API 行；对 6 个 JSON 模板只运行设计批准的 `jq '{api_name,query_mode,params,fields,sample:(.data[0] // null)}'` 投影，不读取完整数据数组或其他模板。
5. 首个实施动作：在 `/private/tmp/M03T04MetadataCheck.java` 创建设计规定的完整公开-loader harness，不创建本任务任何运行时 YAML，执行设计 Tests 节命令并确认只因精确的 `datasets/tushare_pro/<api>.yaml` 资源缺失而非 0，取得可归因 RED。

## Risks

- `top_list.reason`、`top_inst.exalter/side/reason/net_buy` 和 `block_trade.buyer/seller/price/vol` 均为不可空复合键字段；真实上游若返回 null，后续适配必须失败，不得填充占位值或改变业务键。
- 所有数值列已批准为 `DECIMAL(38,18)`；即使样例表现为整数，也不得改为 `LONG` 或经 `double` 转换。
- `buyer`、`seller`、`reason` 和 `exalter` 固定 `STRING(255)`；不得改为 `TEXT` 或静默截断。
- 临时 harness 是 M03-T04 的聚焦证据，永久 49/49 Java 契约由 M03-T09 交付；harness 不得进入 Git。
