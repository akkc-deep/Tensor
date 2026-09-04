# M13-T02 单个可执行 JAR 打包和内容检查——任务设计

任务编号：`M13-T02`

对应任务：[M13-T02](../superpowers/plans/tensor-modules/M13-packaging-runbook.md#task-m13-t02-单-jar-与内容检查25hmavenjava-test)

实施产物：由 Maven 在后端测试通过后生成唯一主产物 `tensor-app-1.0-SNAPSHOT.jar`，以标准 Spring Boot 嵌套 JAR 布局携带应用、前端、Tushare 插件元数据和生产迁移，并在 `verify` 阶段检查完整内容合同

## Goal

把 M13-T01 生成的 Vue 静态资源和当前后端模块打包为一个可直接 `java -jar` 启动的 Spring Boot JAR。最终主产物必须包含 Boot launcher、应用入口、前端 index 及其哈希 JS/CSS、恰好 49 个 Tushare YAML 和生产 V1–V5 Flyway migration，同时排除 fixture 模块、fixture DDL、测试资源和真实凭证。JAR 内容合同必须在 repackage 完成后自动运行，任何缺失、重复或越界内容都使 `verify` 失败。

## Scope

包含：

- 修改 `data-plane/tensor-app/pom.xml`，把 M13-T01 的 `${project.build.directory}/generated-resources` 注册为未过滤的主资源；
- 配置 `org.springframework.boot:spring-boot-maven-plugin:${spring-boot.version}`，当前解析为 3.5.16，在 `package` 阶段以 `com.akkc.tensor.TensorApplication` 为启动类执行 `repackage` 并替换 app 主 artifact；
- 保持 `tensor-core`、`tensor-plugin-api` 和 `tensor-plugin-tushare` 为 `BOOT-INF/lib` 中的标准嵌套依赖，不展开或复制模块内容；
- 创建 `PackagedJarContractTest.java`，以 ZIP/JAR API 检查外层 Boot JAR和内层 Tensor 模块 JAR；
- 让普通 Surefire 单元测试阶段排除该打包合同，并由 Maven Failsafe 3.5.6 在 `integration-test`/`verify` 阶段、Boot repackage 之后运行它；
- 执行严格 RED、完整 `clean verify`、`jar tf`、范围、格式、敏感资源和禁止 Git 能力检查；
- 实现提交精确包含一修改、一新增文件，提交消息固定为 `build: package Tensor as one executable jar`。

排除：

- 不修改父 POM、其他模块 POM、Java 生产代码、`application.yml`、Flyway SQL、Tushare/fixture YAML、前端源码/配置或 M13-T01 构建步骤；
- 不把 Tushare YAML 复制到 `BOOT-INF/classes`，不展开依赖、不使用 Maven Shade、不创建第二个可执行产物；
- 不把 `tensor-plugin-fixture` 改为生产依赖，不移动 V6 或其他测试资源，不创建生产 fixture DDL；
- 不实现 M13-T03 的 SPA fallback、CORS、缓存调整、生产超时或优雅停机，也不实现 M13-T04 的运行说明和 smoke test；
- 不把凭证写入资源、manifest、文件名或测试日志；`application.yml` 继续只保存既有环境变量占位符；
- 不提交 `target`、`control-plane/dist`、`node_modules`、下载的 Node/npm 或任何临时解包文件；
- Maven 配置和 Java 测试不得读取 Git 分支、提交、工作树、仓库目录或 Git 环境元数据。

## Approach

### 标准 Boot 主产物

在 app 现有 `<build>` 中保留 M13-T01 的插件和执行顺序，并新增 `spring-boot-maven-plugin`：

- 版本使用父 POM 已固定的 `${spring-boot.version}`，不得重复写动态版本或修改父 POM；
- execution id 固定为 `repackage`，显式绑定 `package`，唯一 goal 为 `repackage`；
- `mainClass` 固定为 `com.akkc.tensor.TensorApplication`；
- 使用插件默认的主 artifact 替换语义，不设置 classifier，不 attach 第二个 JAR。

Maven 先由标准 jar lifecycle 创建普通 app JAR，再由 Boot repackage 原位替换 `target/tensor-app-1.0-SNAPSHOT.jar`。最终 manifest 的 `Main-Class` 必须为 `org.springframework.boot.loader.launch.JarLauncher`，`Start-Class` 必须为 `com.akkc.tensor.TensorApplication`；外层还必须含 launcher class 和 `BOOT-INF/classes/com/akkc/tensor/TensorApplication.class`。允许插件保留非 `.jar` 主产物的 `.jar.original` 中间文件，但 `target` 顶层以 `.jar` 结尾且匹配 app artifactId/version 的文件只能有一个。

### 主资源和模块资源布局

在 `<build><resources>` 中显式声明两个未过滤资源根，顺序固定为：

1. `${project.basedir}/src/main/resources`；
2. `${project.build.directory}/generated-resources`。

M13-T01 在 `generate-resources` 先生成 `target/generated-resources/static`；标准 `process-resources` 随后把两个根复制到 `${project.build.outputDirectory}`。因此前端进入 `BOOT-INF/classes/static`，现有 `application.yml` 和 V1–V5 继续进入 `BOOT-INF/classes`。不得把 copy-resources 的输出直接改到 classes，也不得省略现有主资源根。

`tensor-plugin-tushare` 保持 app 的 compile 依赖，其模块 JAR 作为 `BOOT-INF/lib/tensor-plugin-tushare-1.0-SNAPSHOT.jar` 嵌入最终产物，49 个 `datasets/tushare_pro/*.yaml` 保留在该内层 JAR。这样 `classpath*:datasets/tushare_pro/*.yaml` 继续按既有模块所有权加载，且不会在 app classes 中出现第二份 YAML。`tensor-plugin-api` 和 `tensor-core` 同样作为内层 JAR存在。`tensor-plugin-fixture` 继续为 test scope，禁止进入 `BOOT-INF/lib`；app 的 V6 和 Tushare 两个测试 YAML继续只在 test output 中存在。

### 打包后合同执行时机

新测试类名按任务卡固定为 `PackagedJarContractTest`，会匹配 Surefire 默认 `*Test` 规则，但最终 JAR 只在后续 `package` 阶段产生。为禁止读取陈旧 JAR并固定任务卡要求的顺序：

- 在 app POM 中声明已由父 `pluginManagement` 固定为 3.5.6 的 `maven-surefire-plugin`，只增加 `**/PackagedJarContractTest.java` exclusion；其他单元测试仍在 `test` 阶段运行一次；
- 声明同样已固定为 3.5.6 的 `maven-failsafe-plugin`，execution id 为 `packaged-jar-contract`，goals 按 `integration-test`、`verify` 排列，includes 只含 `**/PackagedJarContractTest.java`；
- 不使用跳过测试属性、额外 profile、JUnit tag、Antrun、Exec 或在测试中启动 Maven。

最终生命周期顺序为：M13-T01 前端安装/单测/build/复制 → Java 编译和 Surefire 后端测试 → 标准 jar → Boot repackage → Failsafe 打包合同 → verify。打包合同失败时 Failsafe `verify` 必须令 reactor 非零。

### `PackagedJarContractTest` 合同

在 `com.akkc.tensor.build` 包创建一个不加载 Spring、不连接数据库/网络、不写仓库文件的 JUnit 5 测试。它仅使用 JDK `Path`、`Files`、`JarFile`、`JarInputStream`、UTF-8 和 AssertJ，固定读取 `target/tensor-app-1.0-SNAPSHOT.jar`，并在一个结果级测试中完成以下断言：

1. `target` 顶层以 `.jar` 结尾的 app 产物只有该文件，文件是普通文件；
2. manifest 和 entries 包含上述 Boot `Main-Class`、`Start-Class`、launcher class 与应用入口 class；
3. 外层包含 `BOOT-INF/classes/static/index.html`，至少一个文件名满足 `^.+-[A-Za-z0-9_-]+[.]js$` 的 JS 和一个同形 CSS，且 UTF-8 index 同时引用所选 `assets/<文件名>`；
4. 外层 `BOOT-INF/classes/db/migration/` 下的版本化 SQL集合精确等于 V1、V2、V3、V4、V5 五个既有文件，无 V6 或额外 migration；
5. `BOOT-INF/lib` 精确存在版本为 `1.0-SNAPSHOT` 的 `tensor-plugin-api`、`tensor-core`、`tensor-plugin-tushare` 内层 JAR，不存在任何 `tensor-plugin-fixture-*.jar`；
6. 汇总外层 classes 与所有 `BOOT-INF/lib/tensor-*.jar` 中的 `datasets/tushare_pro/*.yaml`，总数和去重后集合大小都恰好为 49；外层 classes 中该路径为空，从而证明没有复制或重复；
7. 外层与 Tensor 内层 JAR 均不含 `datasets/fixture/fixture_daily.yaml`、`datasets/invalid-duplicate-column.yaml`、`datasets/valid-daily.yaml`、V6、`test-classes`、Surefire/Failsafe 报告或测试 class；
8. 外层及 Tensor 内层资源条目不存在 `.env`、`.pem`、`.key`、`.p12`、`.pfx`、`.jks`、`.keystore` 文件；打包的 `application.yml` 必须仍包含 `password: ${TENSOR_DB_PASSWORD}` 和 `token: ${TENSOR_TUSHARE_TOKEN:}`，证明已知凭证入口仍为环境占位符而非打包值。

读取内层 JAR 时直接从外层 `JarFile` entry 的输入流构造 `JarInputStream`，不得解包到项目目录或依赖 `jar`/`unzip` 子进程。所有流使用 try-with-resources；缺 entry、重复 entry、非法 UTF-8、I/O 错误或断言不符均让测试自然失败，不回退源目录或 classpath。

### 直接依赖兼容性

- M09-T06 提供根包 Boot 入口、环境变量化 `application.yml`、V1–V5 生产迁移以及 fixture/V6 仅测试边界；本任务只把这些已验证生产资源装入 Boot classes，并用内容合同阻止真实凭证、fixture 或 V6 越界；
- M13-T01 提供 `target/generated-resources/static` 和固定的前端验证顺序；本任务只把该目录注册为主资源，不改变生成位置、Node/npm、lockfile、Vite 或 copy execution，并在最终 JAR 再验证 index 与哈希资源引用。

两个直接输入互补且无冲突：前者固定后端入口、生产/测试资源和秘密边界，后者固定前端生成资源接口；标准 Boot repackage 在二者完成后组装产物，不改变任一输入行为。

## Files

创建：

- `data-plane/tensor-app/src/test/java/com/akkc/tensor/build/PackagedJarContractTest.java`：在 repackage 后验证唯一可执行 JAR 的 launcher、前端、模块 YAML、migration、测试/fixture 和凭证边界。

修改：

- `data-plane/tensor-app/pom.xml`：注册生成主资源，配置 Boot repackage，并把打包合同从 Surefire 移到 Failsafe `verify`。

不创建、修改或删除其他实现文件。实现提交精确包含上述一新增、一修改文件；设计、交接、看板、`target` 和临时产物不得混入实现提交。

## Tests

所有命令从仓库根目录运行。

严格 RED：先只创建完整 `PackagedJarContractTest.java`，保持 POM 不变；运行 `clean test` 保证不存在陈旧 package 产物：

```bash
mvn -f data-plane/pom.xml -pl tensor-app -am clean \
  -Dtest=PackagedJarContractTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

预期前端固定流水线成功，上游无匹配测试继续，app 编译并真实运行该测试，随后只因 `target/tensor-app-1.0-SNAPSHOT.jar` 尚不存在而失败。编译、模块解析、前端、Spring、数据库、网络或错误测试选择失败均不是有效 RED。不要提交 RED 状态。

GREEN 与完整回归：完成 POM 后运行：

```bash
mvn -f data-plane/pom.xml -pl tensor-app -am clean verify
```

预期 Maven 依次完成 M13-T01 固定前端 20 files / 120 tests、全部默认后端测试、Boot 3.5.16 repackage 和 Failsafe `PackagedJarContractTest` 1/1，reactor 退出 0。Surefire 报告不得包含 `PackagedJarContractTest`，Failsafe 报告必须包含且只运行该类；允许既有 Element Plus 大 chunk 和 Mockito 动态 agent 提示，不允许 skip 或失败。若沙箱仅阻止 Mockito/Byte Buddy self-attach，应在正常 JVM 权限下原样重跑，不修改实现。

产物检查：

```bash
jar tf data-plane/tensor-app/target/tensor-app-1.0-SNAPSHOT.jar
```

输出必须可见 Boot launcher、`BOOT-INF/classes/static/index.html`、哈希 JS/CSS、V1–V5、三个 Tensor 生产模块内层 JAR；不得出现 fixture JAR、V6、测试资源或测试报告。嵌套 Tushare YAML 的精确 49 项由 Java 合同打开内层 JAR证明，不能用外层 `jar tf` 缺少展开输出替代。

范围、格式和禁止能力门禁：

```bash
git diff --check
git status --short --untracked-files=all -- \
  data-plane/tensor-app/pom.xml \
  data-plane/tensor-app/src/test/java/com/akkc/tensor/build/PackagedJarContractTest.java
git diff -- control-plane data-plane/pom.xml \
  data-plane/tensor-core data-plane/tensor-plugin-api \
  data-plane/tensor-plugin-tushare data-plane/tensor-plugin-fixture \
  data-plane/tensor-app/src/main data-plane/tensor-app/src/test/resources
rg -n 'git[[:space:]]+(branch|rev-parse|status|log)|[.]git|GIT_(DIR|COMMON|BRANCH|COMMIT)' \
  data-plane/tensor-app/pom.xml \
  data-plane/tensor-app/src/test/java/com/akkc/tensor/build/PackagedJarContractTest.java
```

预期格式检查退出 0；scoped status 在实现提交前精确显示一修改、一新增；受保护路径无差异。宽泛 Git 扫描除既有强制坐标 `com.github.eirslett` 的 `.github` 子串外不得命中；另以边界化扫描确认没有 Git 命令、`.git` 路径或 `GIT_*` 探测。暂存后 `git diff --cached --name-status` 必须精确匹配 Files 节。

## Acceptance

- `mvn -f data-plane/pom.xml -pl tensor-app -am clean verify` 按前端构建、后端测试、Boot repackage、JAR 内容合同顺序退出 0；
- `target/tensor-app-1.0-SNAPSHOT.jar` 是 app 唯一以 `.jar` 结尾的主产物，可由标准 Boot `JarLauncher` 启动，manifest 指向 `TensorApplication`；
- JAR 中前端 index 及其引用的至少一个哈希 JS/CSS 位于 `BOOT-INF/classes/static`；
- 最终生产 classpath 中恰好有 49 个不重复的 Tushare YAML，全部保留在 Tushare 内层模块 JAR，不在 app classes 复制；
- `BOOT-INF/classes/db/migration` 精确包含既有 V1–V5，不含 V6 或额外生产 migration；
- app JAR包含 plugin-api、core、Tushare 生产模块，不含 fixture 模块、fixture YAML、Tushare 测试 YAML、测试 class/output/report 或凭证文件；打包的配置仍只引用既有数据库密码和 Tushare Token 环境变量；
- `PackagedJarContractTest` 不在 Surefire 阶段读取陈旧/缺失 JAR，只在 repackage 后由 Failsafe 运行一次并把失败传播到 `verify`；
- 未修改根 POM、其他模块、生产 Java/YAML/SQL、前端或 M13-T03/T04 行为，未提交生成物，Maven/Java 不读取 Git 元数据；
- 实现提交消息为 `build: package Tensor as one executable jar`，精确包含 Files 节的一新增、一修改文件。

## Risks

- Boot 可执行 JAR 的“单个产物”是 app 的唯一主 `.jar`；多模块 reactor 仍会为依赖模块生成各自构建 JAR，Boot 也可能保留 `.jar.original`。测试必须按 app `target` 顶层和 `.jar` 后缀冻结合同，不能错误禁止 Maven 中间产物。
- 49 个 YAML 位于 Boot 内层 Tushare JAR而非外层 `jar tf` 的直接条目；内容测试必须实际打开内层流并同时检查总数与去重数，否则可能漏掉缺失或复制。
- Surefire exclusion 与 Failsafe include 是执行时序的一部分；任一 glob 漂移都可能造成合同零次或两次运行，最终报告和 Maven 日志必须明确验证一次。
- `clean verify` 会重新下载/安装固定前端依赖并运行完整前后端回归；网络不可用必须让构建失败。Mockito/Byte Buddy self-attach 受沙箱限制时只能原命令移至正常权限环境，不能 skip 或改测试。
