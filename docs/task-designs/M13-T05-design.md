# M13-T05 独立 acceptance JAR 打包及启停验收——任务设计

任务编号：`M13-T05`

对应任务：[M13-T05](../superpowers/plans/tensor-modules/M13-packaging-runbook.md#task-m13-t05-独立-acceptance-jar-打包及启停验收mavenxmljava-test)

实施产物：显式构建的验收 JAR、归档合同测试和验收运行说明。

## Goal

为 M14-T01 提供带真实 fixture 插件、适配器及 V6 的完整 Servlet 应用，使后续工作只需启动已打包文件便能进行页面验收。继续以现有生产 JAR 为发布主产物；验收包不能改变生产包、依赖 scope、数据库迁移或生产运行说明。

## Scope

只修改 app POM，新增一个 test-scope Java 归档合同测试和一份运行说明。增加显式 Maven `acceptance` profile，在已完成 Boot repackage 的生产包基础上生成独立验收包；验证内容、运行入口、激活条件、禁用重启及生产隔离。

不修改父 POM、模块列表、生产 Java/YAML/Vue、fixture 模块、V1～V6、OpenAPI、既有 `PackagedJarContractTest`、生产 runbook 或 smoke 脚本；不增加 Spring 配置类、新的数据源/适配器、故障注入、下载逻辑、E2E 或通用打包框架。验收 JAR 不作为 Maven 主 artifact/classifier 发布，不新增 install/deploy 流程。

本任务只补齐打包和只读运行条件。SUCCESS/EMPTY 的页面下载及查询闭环属于 M14-T01；`PERSISTENCE_FAILURE` 的 note 标记不等于已经具备打包实例的数据库故障注入，不能在本任务中移入 M08-T03 的测试数据源。

## Approach

### 输入与方案选择

直接输入及用途：

| 输入 | 产物与约束 | 用途 |
|---|---|---|
| M13-T04 | 生产 `tensor-app-1.0-SNAPSHOT.jar`、`docs/runbook/first-run.md`、`docs/runbook/configuration.md`、`scripts/smoke-test.sh`；单 JAR、V1～V5、49 业务表、缺 Token 可启动、生产不含 fixture | 复用全部生产运行内容、配置与只读就绪检查 |
| M08-T02 | `tensor-plugin-fixture-1.0-SNAPSHOT.jar` 及 fixture 公开描述符；仅 `acceptance` 与 `tensor.plugins.fixture.enabled=true` 同时成立时注册 plugin/adapter，五个场景名称固定 | 向验收包附加完整原始模块 JAR，并保留双条件与场景职责 |
| M04-T06 | `data-plane/tensor-app/src/test/resources/db/migration/V6__create_fixture_tables.sql`；只创建一张七列 fixture 表 | 仅向验收包附加原始测试迁移，不复制为生产资源 |

采用 **Maven profile 内的 AntRun 归档装配**：它只增加一个构建 execution，使用标准 JAR/ZIP task，直接消费同次 reactor 构建出的三个输入。另两条候选方案不采用：改变 fixture 为 compile/runtime scope 会影响生产依赖解析；新增独立 Maven 模块会扩大已冻结的五模块边界。这里不需要运行时生产改动，`TensorApplication` 的标准组件扫描覆盖 fixture，`ApplicationConfiguration` 的 `ObjectProvider<DatasetAdapter>` 扩展接缝已能把它送入启动校验和目录。

新任务获批的边界仅是附加验收产物；M13-T02 的唯一**生产** JAR、M08 的生产排除及 M04 的 V6 test-scope 约束仍然有效。

### 两个产物与显式构建

默认命令保持：

```sh
mvn -f data-plane/pom.xml clean verify
```

只生成原 `data-plane/tensor-app/target/tensor-app-1.0-SNAPSHOT.jar`。新增测试类虽参与 testCompile，但不能由默认 Surefire 或默认 Failsafe 执行；默认 clean 构建不生成 `target/acceptance` 中的验收包。

显式命令固定为：

```sh
mvn -f data-plane/pom.xml -Pacceptance clean verify
```

此命令先完成既有前端安装/单测/构建、后端测试及原 Boot repackage，再额外产生：

```text
data-plane/tensor-app/target/acceptance/tensor-app-1.0-SNAPSHOT-acceptance.jar
```

普通 `target` 顶层依然只有一个 `.jar`，因此既有生产合同必须原样通过。新包只供隔离验收，不能分发到生产或把含 V6 的验收库改作生产库。Maven `-Pacceptance` 只选择构建过程，不自动激活 Spring profile 或 fixture 开关。

### POM 的精确修改

1. 在既有 Surefire `excludes` 中追加 `**/AcceptancePackagedJarContractTest.java`，保留原 `PackagedJarContractTest` 排除项。
2. 在 app POM 新增唯一、不默认激活的 `<profile><id>acceptance</id>`。不设置环境激活、`activeByDefault` 或新的生产配置属性，不改 fixture 的 `test` scope。
3. profile 中声明 `org.apache.maven.plugins:maven-antrun-plugin:3.1.0`，使用 `run` goal、`package-acceptance` execution ID、`package` phase。合并后的插件执行顺序必须位于既有 Boot repackage 之后；不能修改或替换原 repackage execution。
4. profile 中为既有 Failsafe 插件追加 execution `acceptance-jar-contract`，绑定 `integration-test` 和 `verify`，其 execution 级 `includes` 只包含 `**/AcceptancePackagedJarContractTest.java`。原 `packaged-jar-contract` execution 保持不变；两个执行分别运行原生产四项和新验收三项测试，不改全局 test discovery、不运行其他 `*IT`，不使用 skip。

Ant target 只使用标准文件、JAR/ZIP 和 move task，流程固定：

- 目标目录为 `${project.build.directory}/acceptance`；只清理本 execution 自己的 `${project.build.finalName}-acceptance.jar` 和 `${project.build.finalName}-acceptance.jar.tmp` 文件，不删除目录内其他文件。
- 显式检查三个输入均为普通文件：`${project.build.directory}/${project.build.finalName}.jar`、`${project.basedir}/../tensor-plugin-fixture/target/tensor-plugin-fixture-${project.version}.jar`、`${project.basedir}/src/test/resources/db/migration/V6__create_fixture_tables.sql`。任何输入缺失均使构建非零退出，不能生成空包或沿用旧验收包。版本来自 Maven，禁止 Git、环境秘密或网络数据进入归档逻辑。
- 使用 Ant `<jar>` 先生成同目录 `${project.build.finalName}-acceptance.jar.tmp`；`compress="false"`、`duplicate="fail"`、`update="false"`。不启用 `keepcompression`；所有嵌套 JAR 必须 STORED，以满足 Boot loader。保留正常目录条目，不使用 `filesonly`。
- 第一个 `zipfileset` 直接读取生产 JAR，保留全部条目，唯独排除 `META-INF/MANIFEST.MF`、`BOOT-INF/classpath.idx`、`BOOT-INF/layers.idx`。不解包到源码或 `target/classes`。
- 第二个文件集只将本次构建的 fixture 模块 JAR 放入 `BOOT-INF/lib/`，不能递归包含 fixture target、测试依赖或测试 class。
- 第三个文件集只把精确 V6 放入 `BOOT-INF/classes/db/migration/`，不能使用整个 `src/test/resources` 或 `target/test-classes` 作为资源源。
- 使用 Ant jar 的嵌套 manifest 明确设置下表属性。不合并原包 manifest，不保留两个已删除索引的属性；不生成替代索引。Boot 的归档 classpath 扫描仍发现所有嵌套库，验收包不提供分层镜像提取能力。
- 完整写包成功后，才将 `.tmp` 移动为最终验收文件；归档/移动失败均使构建失败，随后 Failsafe 不得报告成功。

| Manifest 属性 | 精确值 |
|---|---|
| `Manifest-Version` | `1.0`（Ant 的标准 manifest） |
| `Main-Class` | `org.springframework.boot.loader.launch.JarLauncher` |
| `Start-Class` | `com.akkc.tensor.TensorApplication` |
| `Spring-Boot-Version` | `${spring-boot.version}`，当前为 `3.5.16` |
| `Spring-Boot-Classes` | `BOOT-INF/classes/` |
| `Spring-Boot-Lib` | `BOOT-INF/lib/` |
| `Tensor-Artifact-Purpose` | `acceptance` |

除 Ant 自动产生的工具描述属性外，不添加其他自定义 manifest 项。`Spring-Boot-Classpath-Index` 和 `Spring-Boot-Layers-Index` 必须不存在。

### 三项归档合同测试

`AcceptancePackagedJarContractTest` 使用现有 JUnit 5、AssertJ 及 JDK `JarFile`/`JarInputStream`/`MessageDigest`；没有 Mockito、Spring context、外部命令、数据库或网络。所有辅助逻辑留在同一类，按普通文件条目比较，不把 ZIP 目录条目或压缩率作为应用内容。

固定输入为 app 当前 `target` 下的两个产物、兄弟模块当前 fixture JAR 与原 V6；文件名保持当前 `1.0-SNAPSHOT`，与原生产合同相同。恰好三个普通 `@Test`：

1. `preservesProductionContentsAndAddsOnlyFixtureAndV6`：断言输入、验收包存在；枚举 outer entries 并先拒绝重名，再建立内容映射。验收普通文件名集合必须精确等于生产集合减去 manifest/两个索引，加上新 manifest、一个 fixture 内层 JAR 和一个 V6。对所有保留的生产文件逐项比较解压内容 SHA-256；对新增 fixture/V6 比较其原始输入的 SHA-256。此检查同时保护页面哈希资源、所有生产 class/依赖、原 application.yml、V1～V5 和 49 份 Tushare YAML 所在内层 JAR，不从验收包自举期望。
2. `retainsRunnableBootLayoutAndOnlyFixtureRuntimeResources`：断言启动 manifest 的精确属性、入口 class、Boot launcher 和配置存在；两个索引及对应 manifest 属性不存在。所有 `BOOT-INF/lib/*.jar` 均为 `ZipEntry.STORED`。fixture 内层 JAR 的条目无重复，包含四个公开类型 `FixtureConfiguration`、`FixturePlugin`、`FixtureScenario`、`FixtureEnvelopeFactory` 及 `datasets/fixture/fixture_daily.yaml`；拒绝测试 class、报告、`.env` 和密钥文件。fixture 资源保留在内层，不向外层复制另一份。归档完整可读，不能只检查名称。
3. `keepsAcceptanceOutsideTheProductionArtifactDirectory`：普通 `target` 顶层的 `.jar` 精确为原生产包；`target/acceptance` 的 `.jar` 精确为一个规定名称的验收包；最终 `.tmp` 不存在。原生产包没有 fixture/V6/test 条目，其完整内容仍由不变的原合同单独检查。

不增加仅断言辅助方法自身、配置 XML 文本或实现细节的测试。

### 运行说明与真实启动合同

`docs/runbook/acceptance.md` 按以下顺序写完整操作步骤：

1. 用途、构建命令、精确输入/输出路径与“仅验收”的识别方式；构建者需要项目既有工具链及首次下载 AntRun 依赖的网络，运行者只需要分发文件、Java 21、MySQL 8.4 和 runbook 中的验证工具。
2. 创建独立的 `tensor_acceptance` schema，字符集/排序规则沿用生产说明；最小授权仍为此 schema 上 CREATE/SELECT/INSERT/UPDATE，host 对应实际来源。SQL 账号/密码使用明确教学占位符，隐藏密码输入、禁历史和恢复终端规则链接到 first-run。禁止复用生产库；既有 `tensor` 例子只在本说明中替换为 `tensor_acceptance`。
3. 从已构建产物复制验收 JAR、三份 runbook 与原 smoke 脚本到新运行目录，保留文档/脚本布局。非秘密 JDBC 示例固定为 `jdbc:mysql://127.0.0.1:3306/tensor_acceptance`；账号/密码交互注入，默认清除 Token 与开发 CORS。不添加 `TENSOR_FIXTURE_*` 自定义环境入口。
4. 从运行目录前台执行：

```sh
java -jar tensor-app-1.0-SNAPSHOT-acceptance.jar \
  --spring.profiles.active=acceptance --tensor.plugins.fixture.enabled=true \
  --server.address=127.0.0.1 --server.port=8080
```

5. 根 health 达到 HTTP 200/UP 后，运行原 `sh scripts/smoke-test.sh http://127.0.0.1:8080`，四项均通过；浏览器访问/直接刷新 `/downloads` 和 `/datasets`。fixture 应出现在数据源选择中，默认场景为 SUCCESS，可用值保留原五项。此处不点击下载。
6. 指定只读元数据验证：`GET /api/v1/data-sources` 恰有 fixture 与 tushare_pro 两个条目；fixture 的 enabled/credentialConfigured/downloadAvailable 均 true，无不可用原因；缺 Token 的 Tushare 状态仍 false/false。fixture API 列表与数据集列表各恰有 `fixture_daily`，完整定义包含四个业务列、来源列的既有投影和 `ts_code` 筛选。可用性不以反射/类存在代替 HTTP 事实。
7. 采用正常 SIGTERM 或前台 Ctrl-C，等 JVM 自行退出，再用同一验收包/库将开关改为 `--tensor.plugins.fixture.enabled=false` 重启；health/原 smoke 仍成功，数据源中 fixture 完全缺席，Tushare 摘要与首次一致。已建 fixture 表保留，不执行删除/history 修改。另验证 `--spring.profiles.active=production --tensor.plugins.fixture.enabled=true` 时也没有 fixture，证明两条件缺一不可。
8. 明确每阶段 70s 停机、120s/130s/代理顺序、秘密检查能力及日志处理继承生产说明。验收包无完整持久化故障注入；后续 E2E、故障和发布验收分别消费本产物，不能把打包通过当作这些任务已完成。

所有验收包启动方式都会从归档中发现 V6，即使 fixture 被禁用也可能创建 fixture 表；关闭开关只影响 Bean/目录暴露，不撤销迁移。首次启用状态必须得到 V1～V6 恰六条成功记录、49 张 Tushare 表加 1 张 fixture 表，history 另计；同库重启不得重复执行迁移。生产 JAR 不含 V6，仍只能在独立生产测试 schema 中验证 V1～V5。

## Files

修改：

- `data-plane/tensor-app/pom.xml`：仅追加测试排除项与显式 acceptance profile 的归档/Failsafe 绑定。

创建：

- `data-plane/tensor-app/src/test/java/com/akkc/tensor/build/AcceptancePackagedJarContractTest.java`：上述三项真实归档合同。
- `docs/runbook/acceptance.md`：构建、分发、隔离 schema、激活、只读验证与禁用重启操作说明。

实现提交精确包含这三个文件，消息为 `build: add isolated acceptance jar`。设计、看板和交接另行提交；构建输出、临时验证代码、数据库文件、响应、日志及凭证不提交。

## Tests

### RED 与构建门禁

先添加完整测试、Surefire 排除和 profile 内 Failsafe 绑定，但不添加 AntRun 装配；运行：

```sh
mvn -f data-plane/pom.xml -Pacceptance clean verify
```

预期前端、后端、生产 repackage/原四项 JAR 合同通过，新的归档测试只因验收 JAR 尚不存在而失败。依赖解析、编译、JVM attach 或错误测试选择失败不能充当 RED。

加入装配及运行说明后，顺序运行：

```sh
mvn -f data-plane/pom.xml clean verify
mvn -f data-plane/pom.xml -Pacceptance clean verify
```

默认构建预期前端 120、Surefire 368、原 Failsafe 4，零失败/错误/跳过，且不存在验收 JAR；显式构建预期同一 120/368 基线，加原 Failsafe 4 与验收 Failsafe 3，共 7 项归档测试，零失败/错误/跳过。两个 execution 的报告都必须存在，不能以 profile 下丢失原生产测试换取成功。清晰区分每次构建的实际测试计数；Byte Buddy 沙箱限制仍仅允许原命令在正常 JVM 权限下重跑。

再执行一次**不带 clean**的显式命令，确认临时文件/旧输出处理不会产生重复条目或跳过新内容；最后用默认 `clean verify` 确认验收目录被 Maven clean 清除且原生产隔离仍通过。验收产物若需继续做运行验证，应先复制到临时分发目录保留，不能提交或更名为生产 JAR。

### 真实打包实例验收

在 Java 21、一次性 MySQL 8.4.6 和独立运行目录中，完整按新说明执行 enabled、false 重启、production-profile 三种状态。所有数据库凭证运行时生成，仍用 schema 级账号启动；不用直接 SQL/API 下载替代后续页面验收。本任务只发只读 GET。

| 场景 | 必须观察到的结果 |
|---|---|
| 默认生产构建 | 原生产合同 4/4、无验收包、无 fixture/V6 |
| 显式验收构建 | 同时有原生产包和规定子目录的验收包；原4+新3合同通过，生产包不含fixture |
| acceptance + true | 根 health 200/UP、四项 smoke 通过、两页可刷新、fixture 可用、Tushare 缺Token状态不变；V1～V6与50业务表 |
| 同库重启 acceptance + false | JVM正常停止后重启，迁移数不变；fixture不再暴露、Tushare摘要不变、health/smoke通过 |
| production profile + true | 验收包仍可启动但不注册fixture，数据源列表只含原Tushare；不删除已迁移表 |
| 原生产 JAR + acceptance + true | 在另一空schema启动，仍只有Tushare及V1～V5/49业务表，激活参数不能把fixture带入生产包 |

使用临时工具读取 HTTP 状态/脱敏 JSON 标记和 migration/table 数量，保留版本、命令、结果及正常停止证据到看板完成详情。静态归档合同不能替代上述真实启动结果；新说明缺步骤时只在本任务三文件内修正并重验相应流程。

### 范围、格式和跟踪

```sh
git diff --check
git diff -- data-plane/pom.xml data-plane/tensor-app/src/main \
  data-plane/tensor-app/src/test/resources data-plane/tensor-plugin-api \
  data-plane/tensor-core data-plane/tensor-plugin-tushare \
  data-plane/tensor-plugin-fixture control-plane docs/contracts
git diff -- data-plane/tensor-app/src/test/java/com/akkc/tensor/build/PackagedJarContractTest.java
git diff --cached --name-status
git ls-files --stage -- data-plane/tensor-app/pom.xml \
  data-plane/tensor-app/src/test/java/com/akkc/tensor/build/AcceptancePackagedJarContractTest.java \
  docs/runbook/acceptance.md
```

格式检查通过，受保护路径无差异，实现暂存精确为一修改、两新增，三文件为 100644；文档相对链接可定位，无真实凭证、日志或待定实施选择。

## Acceptance

- 默认与显式构建都保持现有生产入口、资源、配置和 fixture/test-scope 隔离；原四项生产 JAR 合同未修改且继续执行。
- 显式 profile 在规定子目录产出唯一可 `java -jar` 启动的验收 JAR，包含原生产应用、原 fixture 模块及精确 V6，没有测试 class/依赖、故障注入或额外生产改动。
- 三项归档合同证明普通文件集合、每份保留内容、fixture/V6原始内容、Boot manifest、STORED 嵌套库和路径隔离；输入缺失/归档失败不能被旧文件掩盖。
- 真实隔离 MySQL/运行目录完成四种启动状态、fixture显隐、Tushare摘要保持、六项迁移/50业务表及生产五项/49表隔离、页面刷新、原smoke和正常停止。
- 新说明使 M14-T01 只凭验收分发物开始页面工作，无需选择打包方案、编辑源码或重装fixture；不宣称页面下载或持久化故障矩阵已验收。
- RED、默认/显式/无clean重建/默认清理验证和范围/格式/链接/Git门禁通过，精确三文件提交。

## Risks

- 验收包包含 V6；profile/fixture开关并不控制 Flyway 资源可见性，因此只能连接独立验收 schema。不能在已执行 V6 的库上启动不带 V6 的生产包并假定迁移历史兼容。
- STORED 会使验收包大于生产包；这是无需重新压缩/解压嵌套库即可满足 Boot loader 的局部取舍，不改变生产包大小。
- 本设计有意不保留原包的 classpath/layers 索引，manifest同步移除对应属性；若未来需要验收包分层镜像，则另行设计索引生成，不复用过期索引。
- 当前 fixture 的运行依赖已由生产应用覆盖；若后续增加新依赖，必须重新评估显式装配白名单和真实启动门禁，不能加入所有 test-scope 依赖。
- AntRun首次依赖下载、正常 JVM attach 与一次性 MySQL 可用性属于验证环境条件；不得使用 skip、空测试或放宽生产合同绕过。
