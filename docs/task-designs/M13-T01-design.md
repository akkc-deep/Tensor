# M13-T01 前端确定性构建及静态资源复制——任务设计

任务编号：`M13-T01`

对应任务：[M13-T01](../superpowers/plans/tensor-modules/M13-packaging-runbook.md#task-m13-t01-前端确定性构建与资源复制25hmavenxml)

实施产物：由 Maven 使用固定 Node/npm 对 `control-plane` 执行确定性安装、完整单测和 Vite 构建，再把生成的入口及哈希资源复制到 `tensor-app` 的生成资源目录

## Goal

让 `tensor-app` 的 Maven `generate-resources` 生命周期成为前端静态资源的唯一构建入口：它必须使用提交的 npm lockfile 和固定工具链，依次完成安装、单测、生产构建与复制，最终产生 `target/generated-resources/static/index.html` 及其引用的哈希 JS/CSS。这样后续 M13-T02 可以直接消费已验证的生成资源完成单 JAR 打包，而不依赖系统预装 Node、手工生成的 `dist` 或 Git 仓库状态。

## Scope

包含：

- 修改 `data-plane/tensor-app/pom.xml`，固定 `frontend-maven-plugin` 1.15.4、Node v24.15.0、npm 11.12.1 和 `maven-resources-plugin` 3.4.0；
- 在 Maven `generate-resources` 阶段按固定顺序运行 `npm ci`、`npm run test:unit -- --run`、`npm run build`，然后复制 `control-plane/dist`；
- 把插件管理的 Node/npm 安装在 `tensor-app/target/frontend`，所有 npm 命令的工作目录固定为仓库内 `control-plane`；
- 创建 `FrontendResourceBuildTest.java`，验证生成入口、哈希 JS/CSS 及入口引用关系；
- 执行严格 RED、聚焦 GREEN、完整 Maven 回归、范围检查、lockfile 不变检查和禁止 Git 读取检查；
- 实现提交只包含任务卡规定的一修改、一新增文件，提交消息固定为 `build: integrate frontend assets into app`。

排除：

- 不修改 `data-plane/pom.xml`、`control-plane/package.json`、`control-plane/package-lock.json`、Vite/Vitest 配置或前端源码；
- 不提交或手工维护 `control-plane/dist`、`node_modules`、Maven `target` 或下载的 Node/npm；
- 不配置 Spring Boot repackage、JAR 内容、资源装载、SPA fallback、缓存/CORS、生产配置、运行说明或 smoke test；这些分别属于 M13-T02～T04；
- 不使用 `exec-maven-plugin`、系统 Node/npm、`npm install`、跳过前端测试的 profile 或旧 `dist` 回退；
- Maven 构建逻辑和 Java 测试均不得读取 Git 分支、提交、工作树、仓库目录或环境中的 Git 元数据；
- 不修改业务 Java、API、插件、数据库、前端行为或既有测试。

## Approach

### 固定工具链与路径

在 `tensor-app/pom.xml` 的 `<build><plugins>` 中直接声明 `com.github.eirslett:frontend-maven-plugin:1.15.4`。插件公共配置固定为：

- `workingDirectory`：`${project.basedir}/../../control-plane`；
- `installDirectory`：`${project.build.directory}/frontend`。

`install-node-and-npm` execution 固定 `nodeVersion` 为 `v24.15.0`、`npmVersion` 为 `11.12.1`。插件只能使用该项目内工具链；不得探测或回退到 `PATH` 中的 Node/npm。版本来自 POM 显式值，不来自 Git、当前机器或动态查询。

### `generate-resources` 顺序

`frontend-maven-plugin` 的四个 execution 均绑定 `generate-resources`，并在 POM 中按下列顺序声明：

1. `install-frontend-toolchain`：执行 `install-node-and-npm`；
2. `npm-ci`：执行 `npm` goal，参数精确为 `ci`；
3. `frontend-unit-tests`：执行 `npm` goal，参数精确为 `run test:unit -- --run`；
4. `frontend-production-build`：执行 `npm` goal，参数精确为 `run build`。

随后声明 `org.apache.maven.plugins:maven-resources-plugin:3.4.0` 的 `copy-frontend-resources` execution，同样绑定 `generate-resources`，执行 `copy-resources`：

- 输入目录：`${project.basedir}/../../control-plane/dist`；
- 输出目录：`${project.build.directory}/generated-resources/static`；
- `filtering` 为 `false`，不得改写 HTML、JS、CSS 或资源名。

Maven 按同阶段的 POM 声明顺序执行上述插件。任一工具安装、`npm ci`、前端测试或 Vite build 失败时生命周期立即停止，资源复制不会运行；不得从旧资源恢复或继续后端测试。Vite build 负责刷新 `control-plane/dist`，最终发布构建由 M13 模块门禁的 `clean` 生命周期清理 `target`。

### 资源合同测试

创建包 `com.akkc.tensor.build` 下的 `FrontendResourceBuildTest`，使用 JUnit 5、JDK `Path`/`Files` 和 AssertJ，不加载 Spring 容器、不执行 npm、不修改文件。唯一测试从 Surefire 默认模块工作目录解析 `target/generated-resources/static`，断言：

1. `index.html` 存在且是普通文件；
2. `assets` 是目录；
3. 至少存在一个文件名满足正则 `^.+-[A-Za-z0-9_-]+[.]js$` 的普通文件；
4. 至少存在一个文件名满足正则 `^.+-[A-Za-z0-9_-]+[.]css$` 的普通文件；
5. UTF-8 读取的 `index.html` 同时包含所选 JS 与 CSS 的 `assets/<文件名>` 引用。

哈希形状只要求扩展名前存在非空的字母、数字、下划线或连字符哈希段，不冻结具体 hash、入口 basename、资源大小或 chunk 数量。测试验证复制后的公开资源合同，不重复测试 Vite 内部实现，也不为 M13-T02 提前检查 JAR。

### 聚焦命令修正

任务卡的 `-pl tensor-app -am -Dtest=FrontendResourceBuildTest` 会把 `-Dtest` 传给每个上游模块，而目标测试只存在于 `tensor-app`。当前 Maven/Surefire 3.5.6 已实测在第一个上游模块因“无匹配测试”提前失败。项目所有者批准在聚焦命令中增加：

```text
-Dsurefire.failIfNoSpecifiedTests=false
```

该参数只允许没有此测试的上游模块继续，不跳过 `tensor-app` 中匹配的目标测试，也不改变完整回归的默认失败规则。

## Files

创建：

- `data-plane/tensor-app/src/test/java/com/akkc/tensor/build/FrontendResourceBuildTest.java`：验证 Maven 生成目录中的入口、哈希 JS/CSS 和入口引用。

修改：

- `data-plane/tensor-app/pom.xml`：固定前端工具链、四步 npm execution 和资源复制 execution。

不创建、修改或删除其他文件。实现提交精确包含上述一新增、一修改文件；本设计、任务看板、交接和后续实施计划不得混入该实现提交。

## Tests

所有命令从仓库根目录运行。

严格 RED：先只创建完整的 `FrontendResourceBuildTest.java`，保持 `tensor-app/pom.xml` 不变，并确认 `tensor-app/target/generated-resources/static` 当前不存在。运行：

```bash
mvn -f data-plane/pom.xml -pl tensor-app -am \
  -Dtest=FrontendResourceBuildTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

预期命令非零；上游模块允许无匹配测试并继续，失败必须来自 `tensor-app` 的新测试找不到生成的 `index.html`/`assets`，不得来自测试编译、模块解析、Spring、数据库、网络业务依赖或错误的测试选择。

GREEN：只修改 `tensor-app/pom.xml` 完成 Approach 后，重跑同一命令。预期固定 Node/npm 安装成功，`npm ci` 使用提交的 lockfile，前端完整 20 files / 120 tests 通过，Vite 8.2.2 build 成功，资源复制完成，`FrontendResourceBuildTest` 通过且 Maven 退出 0。只允许既有 Element Plus 大 chunk 提示。

完整回归：

```bash
mvn -f data-plane/pom.xml test
```

预期完整 reactor 退出 0；到达 `tensor-app` 时仍按固定顺序完成前端安装、120 项单测、Vite build、复制，再运行全部后端默认测试及新资源测试。任何前端步骤失败时不得运行复制或 `tensor-app` 测试。

范围、格式和静态门禁：

```bash
git diff --check
git status --short --untracked-files=all -- \
  data-plane/tensor-app/pom.xml \
  data-plane/tensor-app/src/test/java/com/akkc/tensor/build/FrontendResourceBuildTest.java
git diff -- control-plane/package.json control-plane/package-lock.json \
  control-plane/vite.config.js control-plane/vitest.config.js control-plane/src \
  data-plane/pom.xml
rg -n 'git[[:space:]]+(branch|rev-parse|status|log)|[.]git|GIT_(DIR|COMMON|BRANCH|COMMIT)' \
  data-plane/tensor-app/pom.xml \
  data-plane/tensor-app/src/test/java/com/akkc/tensor/build/FrontendResourceBuildTest.java
```

预期 `git diff --check` 退出 0；status 精确显示 Files 节的一新增、一修改；受保护路径无差异；Git 能力扫描无输出并退出 1。暂存后 `git diff --cached --name-status` 必须精确显示同一两文件集合，提交消息与 Files 节固定值一致。

## Acceptance

- `mvn generate-resources` 到达 `tensor-app` 时只使用 POM 固定的 frontend-maven-plugin 1.15.4、Node v24.15.0 和 npm 11.12.1，不依赖系统 Node/npm；
- Maven 按 `npm ci`、完整前端单测、Vite build、资源复制的顺序执行，任一步失败即停止且不复制旧 `dist`；
- `npm ci` 使用当前提交的 lockfile，构建后 `control-plane/package-lock.json` 无差异，未使用 `npm install`；
- `tensor-app/target/generated-resources/static/index.html` 与 `assets` 下至少一个哈希 JS、一个哈希 CSS 均存在，入口引用这些复制后的资源；
- 聚焦命令通过批准的 Surefire 参数越过上游模块的无匹配测试，并在 `tensor-app` 真正运行且通过 `FrontendResourceBuildTest`；
- 完整 Maven 回归退出 0，其中前端保持 20 files / 120 tests 全通过，Vite build 只允许既有大 chunk 提示；
- Maven 构建和新 Java 测试不读取 Git 分支、提交、状态、目录或环境元数据，不动态派生版本；
- 没有配置 M13-T02～T04 的 JAR、生产 Web 或运行说明，也没有修改前端、根 POM、业务模块或生成物；
- 实现提交精确包含 Files 节的一新增、一修改文件，消息为 `build: integrate frontend assets into app`。

## Risks

- 首次构建必须从插件和 npm registry 下载固定 Node/npm 及 lockfile 依赖；网络或 registry 不可用时构建会明确失败。不得因此回退到系统工具链、`npm install` 或旧资源。
- 任何包含 `tensor-app` 且到达 `generate-resources` 的 Maven 生命周期都会运行完整前端安装、单测和构建，构建时间会增加；这是保证后端测试消费已验证前端资源的有意成本，本任务不增加跳过 profile。
- 测试只冻结“存在哈希 JS/CSS 且由入口引用”的公开形状，不比较跨机器字节 checksum；确定性来自精确 Node/npm、提交的 lockfile、`npm ci` 和固定 Vite 配置。若将来需要可复现字节证明，应另建任务。
- 同阶段执行顺序依赖 POM 中 frontend 插件先于 resources 插件的声明顺序；实现审查必须核对 execution 顺序，并由 Maven 日志与失败门禁验证。
