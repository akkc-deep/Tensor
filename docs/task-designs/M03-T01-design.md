# M03-T01 YAML 加载、schema 校验和模板对照测试框架——任务设计

任务编号：`M03-T01`
对应任务：[M03-T01](../superpowers/plans/tensor-modules/M03-tushare-metadata.md#task-m03-t01-元数据加载与-schema-验证框架20hjava)
实施产物：`DatasetDefinitionLoader.java`、`DatasetDefinitionLoaderTest.java`、两份测试 YAML，以及父/模块 POM 中的依赖管理与 schema 打包配置

## Goal

在 Java 21 `tensor-plugin-tushare` 模块中交付运行时完整的 Tushare 数据集元数据加载器：从 Spring `ResourcePatternResolver` 匹配的 YAML 资源中严格解析定义，先按 M00-T02 的 JSON Schema 2020-12 校验，再映射为 M02 的不可变 `DatasetDefinition` 公共模型并执行跨字段语义校验。任何无效资源都以确定顺序聚合为 `DATASET_MISCONFIGURED`，不返回部分结果；成功结果按 `apiName` 排序并保持不可变，使 M03-T02～T08 可以只新增业务 YAML 而不复制加载或校验逻辑。

权威 schema 继续只维护在 `docs/contracts/dataset-definition.schema.json`。构建把该文件原样打包为模块 JAR 内的 `contracts/dataset-definition.schema.json`，运行时只读取 classpath，不读取 `docs/`，也不访问网络。

## Scope

包含：

- 在父 POM 显式管理 `com.networknt:json-schema-validator:1.5.9`，在 `tensor-plugin-tushare` 消费 Boot BOM 已管理的 `jackson-dataformat-yaml:2.21.4` 与该 schema validator；
- 在模块 POM 保留普通 `src/main/resources`，并把权威 schema 额外打包到 `contracts/dataset-definition.schema.json`，不创建第二份 schema 源文件；
- 创建一个公开、最终的 `DatasetDefinitionLoader` 及其唯一公开加载方法，完成资源发现、严格单文档 YAML 解析、schema 校验、私有 raw record 映射、M02 构造和 M03 跨字段校验；
- 创建一个真实的 `DatasetDefinitionLoaderTest` 和两份测试资源，覆盖有效 `daily`、schema/解析/语义反例、跨资源重复、零匹配、确定性错误与成功结果；
- 执行严格 TDD、聚焦测试、模块回归、Enforcer、依赖版本、JAR schema 内容、范围和格式门禁。

排除：

- 不创建或修改 `src/main/resources/datasets/tushare_pro/*.yaml`；49 份运行时业务定义属于 M03-T02～T08；
- 不修改 M00 schema/示例、M02 公共 records、既有生产 Java、其他模块或 `docs/data-template/`；
- 不读取全部 49 份模板，不把 `daily.json` 的完整 `data` 数组载入上下文；本任务只使用 M00 `daily` 示例和 `daily.json.fields` 基线；
- 不实现 Spring Bean/自动配置、目录注册、参数值校验、日期先后关系、数据库类型/Flyway、适配、下载、持久化、REST 或前端；
- 不创建公开异常类型、cause 构造器、网络 schema 解析、schema 缓存服务、builder 或第二套元数据 DTO。

## Approach

### 依赖与 schema 打包

在 `data-plane/pom.xml` 增加 `json-schema-validator.version=1.5.9`，并在 `dependencyManagement` 中管理 `com.networknt:json-schema-validator`。Jackson YAML 不另设版本，继续由当前 Spring Boot BOM 管理为 `2.21.4`。

在 `data-plane/tensor-plugin-tushare/pom.xml` 增加以下两个 compile 依赖，不改变既有依赖：

- `com.fasterxml.jackson.dataformat:jackson-dataformat-yaml`；
- `com.networknt:json-schema-validator`。

模块 `<build><resources>` 明确保留默认 `src/main/resources`，再从 `${project.basedir}/../../docs/contracts` 只包含 `dataset-definition.schema.json` 并以 `contracts` 为 `targetPath`。JAR 中必须恰有 `contracts/dataset-definition.schema.json`，其字节内容与权威 `docs/contracts/dataset-definition.schema.json` 相同；不得在模块源码树维护副本。

### 公开接口与数据流

创建：

```java
public final class DatasetDefinitionLoader {
    public List<DatasetDefinition> loadAll(
            ResourcePatternResolver resolver,
            String pattern);
}
```

类位于 `com.akkc.tensor.plugin.tushare.metadata`，提供 public 无参构造器，不增加其他 public/protected 方法或构造器。`loadAll` 拒绝 null `resolver`、null/空白 `pattern`，但不 trim、不改写 pattern。成功时返回 `List.copyOf` 形成的不可变列表，按 `definition.datasetKey().apiName().value()` 升序排列；解析资源的原始顺序不得影响输出。

一次加载按以下固定顺序执行：

1. 从 classpath `contracts/dataset-definition.schema.json` 读取 schema，使用 `JsonSchemaFactory.getInstance(VersionFlag.V202012)` 创建 JSON Schema 2020-12 validator；schema 不解析远程引用，也不读取工作区 `docs/`。
2. 用传入 resolver 和 pattern 发现全部资源；零匹配直接产生错误，不返回空成功列表。
3. 对每个资源以 Jackson YAML 解析为 `JsonNode`。启用严格重复键检测和 trailing-token 检测，使重复 YAML key 与第二个 YAML document 都失败；不启用大小写枚举、未知属性或标量类型的宽松兼容。
4. 收集该树的全部 schema validation messages；schema 失败时不进入 raw record/M02 构造，避免级联伪错误。
5. schema 通过后绑定到 `DatasetDefinitionLoader` 内部的 private nested raw records。raw records 与 M00 字段逐项同名，只用于区分 YAML 缺省值和公共模型构造，不成为模块 API。
6. 先收集本任务的跨字段语义错误，再把 raw 值映射为现有 M02 records；构造失败转换为当前资源的一项安全诊断，继续处理其他资源。
7. 对所有已映射定义按 `apiName` 分组，拒绝跨资源重复；若任何阶段存在错误，统一排序并抛异常，不返回已成功定义；否则排序并返回不可变列表。

YAML 到 M02 的映射固定如下：

- `pluginId`/`apiName` → `PluginId`/`ApiName` → `DatasetKey`，`tableName` → `TableName`；
- `queryMode`、参数 `type`、列 `logicalType`、业务键 `mode` 按枚举名称直接映射，不增加别名或大小写转换；
- 参数映射为 `ParameterDescriptor`；缺省 `allowedValues` 映射为 `List.of()`，其他可选字符串保持 null；
- 列映射为 `ColumnDefinition`；缺省 `allowedValues` 映射为 `List.of()`，缺省 `longText` 映射为 false，其他可选数值保持 null；
- `businessKey` 映射为 `BusinessKeyDefinition`；每个 `filters[]` 字符串包装为 `FilterDefinition`；`fixedColumn` 缺省保持 null；
- schema 不含 `batchSize`，因此调用 M02 的十参数 `DatasetDefinition` 构造器，固定采用已批准默认值 500；所有数组保持 YAML 声明顺序。

### 语义校验

schema 通过后，加载器补充且仅补充以下运行时语义：

- `pluginId` 必须精确等于 `tushare_pro`；
- 第 i 个 column 的 `displayOrder` 必须精确等于 i，因而顺序连续为 `0..n-1`；
- 当 `scale` 与 `precision` 同时存在时，必须满足 `scale <= precision`；
- 每个非 null `relatedParameter` 必须引用同一资源中已声明的参数名；
- M02 构造器继续作为重复参数/列/filter、非法局部值、表名派生、业务键/filter/fixedColumn 列引用与 batchSize 默认值的唯一执行契约，不复制或弱化这些检查；
- 不同资源不得声明相同 `apiName`；资源模式必须至少匹配一个资源。

本任务不校验参数日期先后关系，也不把 `queryMode` 推断成特定参数集合；这些属于 M05 参数校验和 M03 后续 49/49 总契约。

### 错误边界与确定性

在 `DatasetDefinitionLoader.java` 内创建 private static final `DatasetMisconfiguredException extends TensorException`，其唯一 package-private 构造器只接收安全 message，并固定调用 `super(ErrorCode.DATASET_MISCONFIGURED, message)`。不提供 cause 构造器，不向外传播 Jackson、networknt、I/O 或 M02 的原始异常对象。

每项资源诊断都使用 `Resource.getFilename()`；无法取得文件名时使用字面量 `<unnamed-resource>`，绝不使用 `Resource.getDescription()`、绝对路径或 URI。schema 缺失/无效使用资源名 `dataset-definition.schema.json`，零匹配使用资源名 `<pattern>`。原因取 schema message、Jackson `getOriginalMessage()` 或受控语义/M02 message，折叠连续空白为单个空格并去除首尾空白；读取失败使用固定原因 `resource cannot be read`，不拼接底层异常文本。

最终诊断按资源名、再按规范化原因的自然顺序排序并去重，异常 message 固定为：

```text
Invalid dataset definitions:
- <resource-name>: <normalized-reason>
- <resource-name>: <normalized-reason>
```

跨资源重复 `apiName` 时，为涉及的每个资源各加入 `duplicate apiName: <apiName>`。即使 resolver 返回顺序不同，同一输入集合的异常 code、message 和行顺序也必须完全相同。`DATASET_MISCONFIGURED.retryable()` 保持 false；任何无效资源都阻止部分列表返回。

## Files

- Modify `data-plane/pom.xml`：管理 `json-schema-validator:1.5.9`，不改变 Boot、Java、测试或其他依赖版本。
- Modify `data-plane/tensor-plugin-tushare/pom.xml`：加入 YAML/schema 运行时依赖，并把权威 schema 与普通 `src/main/resources` 一起打包。
- Create `data-plane/tensor-plugin-tushare/src/main/java/com/akkc/tensor/plugin/tushare/metadata/DatasetDefinitionLoader.java`：实现公开加载接口、私有 raw records、schema/语义校验与私有领域异常。
- Create `data-plane/tensor-plugin-tushare/src/test/java/com/akkc/tensor/plugin/tushare/metadata/DatasetDefinitionLoaderTest.java`：验证严格解析、完整映射、聚合错误、确定性和不可变结果。
- Create `data-plane/tensor-plugin-tushare/src/test/resources/datasets/valid-daily.yaml`：提供通过 schema 和语义校验的完整 11 列 `daily` 测试定义。
- Create `data-plane/tensor-plugin-tushare/src/test/resources/datasets/invalid-duplicate-column.yaml`：提供 schema 合法但 M02 构造期因重复列名失败的固定反例。

实现提交只暂存上述六个文件，提交消息固定为 `feat(tushare): load validated dataset metadata`。任务设计、交接、任务卡、看板、生成的 `target`、schema 源文件、运行时业务 YAML、其他模块和既有 Java 文件不得混入实现提交。

## Tests

先完成测试类和两份测试 YAML，并完成两项 POM 配置，但不创建 `DatasetDefinitionLoader.java`，然后运行：

```bash
mvn -f data-plane/pom.xml -pl tensor-plugin-tushare -am \
  -Dtest=DatasetDefinitionLoaderTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

预期在 `testCompile` 只因 `DatasetDefinitionLoader` 不存在而退出非 0；失败不得来自测试语法、依赖解析、schema 打包路径或环境错误，作为 RED。

`DatasetDefinitionLoaderTest` 使用 JUnit 5、AssertJ、`@TempDir`、真实 `PathMatchingResourcePatternResolver` 和真实 loader，不使用 mocks，并覆盖：

- `valid-daily.yaml` 恰加载一个 `tushare_pro/daily` 定义；11 列、参数、表名、queryMode、业务键、filters、fixedColumn、默认 `batchSize=500` 与 M00 示例逐项一致，列顺序保持；
- 返回多个合法临时 YAML 时按 `apiName` 排序，输入资源顺序不影响结果，返回列表不可修改；
- `invalid-duplicate-column.yaml`、表名不匹配、缺失 business-key column、非法 filter reference 分别得到 `DATASET_MISCONFIGURED` 且 message 含对应文件名；
- 错误 pluginId、不连续/错位 displayOrder、`scale > precision`、悬空 relatedParameter 分别被 M03 语义检查拒绝；
- schema 未知字段/缺失必填字段、重复 YAML key、多个 YAML document 均失败，不被宽松 Jackson 行为接受；
- 两个以上无效资源的全部诊断被聚合；交换 resolver 返回顺序后 message 逐字相同，按文件名/原因排序，不含临时目录绝对路径，`retryable()` 为 false；
- 两个资源重复 `apiName` 时两者均出现在确定性诊断中；零匹配得到 `<pattern>: no resources matched`；任何失败路径都不暴露部分结果或原始 cause。

完成最小实现后重跑聚焦命令，预期 `DatasetDefinitionLoaderTest` 全部通过，0 failure、0 error、0 skipped，命令退出 0。随后运行：

```bash
mvn -f data-plane/pom.xml -pl tensor-plugin-tushare -am test
mvn -f data-plane/pom.xml -pl tensor-plugin-tushare -am verify
```

两条命令均预期退出 0；所有既有测试和新测试通过，父项目、`tensor-plugin-api` 与 `tensor-plugin-tushare` 的 `ban-git-capabilities` 均通过，不新增构建警告类别。

验证依赖和 JAR 内容：

```bash
mvn -f data-plane/pom.xml -pl tensor-plugin-tushare \
  dependency:tree \
  -Dincludes=com.fasterxml.jackson.dataformat:jackson-dataformat-yaml,com.networknt:json-schema-validator
jar tf data-plane/tensor-plugin-tushare/target/tensor-plugin-tushare-1.0-SNAPSHOT.jar \
  | rg '^contracts/dataset-definition\.schema\.json$'
cmp docs/contracts/dataset-definition.schema.json \
  <(unzip -p data-plane/tensor-plugin-tushare/target/tensor-plugin-tushare-1.0-SNAPSHOT.jar \
    contracts/dataset-definition.schema.json)
```

第一条预期只解析到 YAML `2.21.4` 和 schema validator `1.5.9` 的一条直接版本，不发生版本冲突；第二条恰输出一行目标路径；`cmp` 无输出，三条命令均退出 0。

最后运行：

```bash
git status --short --untracked-files=all -- \
  data-plane/pom.xml \
  data-plane/tensor-plugin-tushare
git diff --check
```

提交前第一条只列 Files 节的六个实施文件且不列 `target`；格式检查退出 0。提交后以 `git show --stat --oneline HEAD` 确认固定消息和精确六文件范围。

## Acceptance

- `loadAll` 以单一公开接口从匹配资源产生按 `apiName` 排序的不可变 `DatasetDefinition` 列表，完整 `daily` 映射与 M00 schema、M02 records 和默认 `batchSize=500` 一致。
- 重复键、多文档、schema 反例、M03 跨字段反例、M02 构造反例、重复 `apiName` 和零匹配全部以 code 为 `DATASET_MISCONFIGURED`、retryable 为 false 的私有领域异常失败；不返回部分结果或暴露原始 cause。
- 多资源错误全部聚合，诊断只含资源文件名/受控占位名，按资源名和规范化原因稳定排序；resolver 顺序变化不改变 message，不出现绝对路径。
- schema validator 全程使用 JSON Schema 2020-12 且不访问网络；运行时只从 JAR classpath 读取与权威 schema 字节相同的 `contracts/dataset-definition.schema.json`，模块仍保留普通 main resources。
- 聚焦测试经历可归因 RED 后 GREEN；模块 `test`、`verify`、Enforcer、依赖版本、JAR 内容、范围和格式门禁全部得到预期结果。
- 净实现和实现提交精确包含 Files 节六个文件，未修改 schema/M02 records/其他模块，未创建运行时业务 YAML 或提前实现目录、参数、数据库、适配、REST、前端职责。

## Risks

无未决设计选择。已接受的风险是 validator/Jackson 的原始校验措辞可能随版本变化，因此 networknt 固定为 `1.5.9`、Jackson 固定由当前 Boot BOM 解析为 `2.21.4`，测试只冻结本设计定义的规范化与排序结果；外部 schema 文件通过 Maven resource 配置进入 JAR，必须用字节对照门禁防止路径错误或内容漂移。M03-T02～T08 只能新增业务 YAML 并消费本加载器，不得复制 schema、放宽严格解析或绕过 M02 构造约束。
