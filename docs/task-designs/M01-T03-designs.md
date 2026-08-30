# M01-T03 Maven Enforcer、ArchUnit 和禁止 Git 能力门禁——任务设计

任务编号：`M01-T03`
对应任务：[M01-T03](../superpowers/plans/tensor-modules/M01-backend-foundation.md#task-m01-t03-建立依赖与-git-能力门禁20h)
实施产物：`data-plane/pom.xml`、`data-plane/tensor-app/src/test/java/com/akkc/tensor/architecture/ModuleDependencyTest.java`、`data-plane/tensor-app/src/test/java/com/akkc/tensor/architecture/ForbiddenGitCapabilityTest.java`

## 做什么

在 M01-T02 已完成的 Java 21 / Spring Boot 3.5.16 六项目 reactor 上建立三层编译期门禁：Maven Enforcer 拒绝 JGit 和常见 GitHub、GitLab、Bitbucket Java API 依赖；ArchUnit 固定五模块允许的包依赖方向；JUnit 源码扫描拒绝生产 Java/文本资源引用 Git/代码托管 API 或执行 `git` 子进程。

完成后，父 POM 使用 Maven Enforcer Plugin `3.6.3` 在 `validate` 阶段检查直接和传递依赖；`tensor-app` 的两个测试在当前尚无模块生产类/资源时可通过，但用内建反例证明禁止规则不是空断言。`mvn test` 和 M01 模块门禁 `mvn verify` 均退出 0。

本任务不创建或修改业务 Java、资源、配置、前端和其他模块 POM；不调整 M01-T02 的依赖、版本或插件管理；不删除或迁移旧 `data-plane/src/main/java/com/akkc/Main.java`；不实现运行时仓库扫描、插件热加载或任何 Git 能力。

## 怎么做

修改 `data-plane/pom.xml`：在既有 `archunit.version` 后增加 `maven-enforcer-plugin.version=3.6.3`。该版本来自 2026-08-31 读取的 Maven Central `maven-enforcer-plugin` 元数据（`release=3.6.3`、`lastUpdated=20260518110420`），不得使用 milestone、snapshot 或动态版本。

保留现有 `<build><pluginManagement>` 原样，并在同一 `<build>` 下新增唯一的 `<plugins>` 项：

- 坐标 `org.apache.maven.plugins:maven-enforcer-plugin:${maven-enforcer-plugin.version}`；
- execution id 为 `ban-git-capabilities`，phase 为 `validate`，唯一 goal 为 `enforce`；
- `<fail>true</fail>`；
- 唯一规则为 `bannedDependencies`，`searchTransitive=true`；
- `excludes` 按下列顺序精确声明：
  1. `org.eclipse.jgit:*`；
  2. `org.kohsuke:github-api`；
  3. `org.gitlab4j:gitlab4j-api`；
  4. `com.cdancy:bitbucket-rest`；
  5. `io.github.cdancy:bitbucket-rest`；
  6. `com.atlassian.bitbucket:*`；
  7. `com.atlassian.bitbucket.server:*`。

规则 message 固定为 `Git and code-hosting API dependencies are forbidden in Tensor`。不得配置 `includes`，不得关闭传递依赖检查，不得增加其他 Enforcer 规则。

创建 `ModuleDependencyTest.java`，包名为 `com.akkc.tensor.architecture`。使用 `ClassFileImporter`、`ImportOption.DoNotIncludeTests` 和 `importPackages("com.akkc.tensor")` 只导入生产类；一个 JUnit `@Test` 对以下四条 `ArchRule` 逐条调用 `allowEmptyShould(true).check(classes)`：

1. `..plugin.api..` 不得依赖 `..core..`、`..plugin.tushare..`、`..plugin.fixture..`、`..app..`；
2. `..core..` 不得依赖 `..plugin.tushare..`、`..plugin.fixture..`、`..app..`；
3. `..plugin.tushare..` 不得依赖 `..core..`、`..plugin.fixture..`、`..app..`；
4. `..plugin.fixture..` 不得依赖 `..core..`、`..plugin.tushare..`、`..app..`。

这些规则允许 `app -> core -> plugin-api`、`app -> plugin-tushare -> plugin-api` 和测试 classpath 上的 `app -> plugin-fixture -> plugin-api`，并拒绝所有反向模块边。`allowEmptyShould(true)` 只处理当前尚无模块生产类的已知基线；不得用全局 ArchUnit 配置关闭空规则失败。

创建 `ForbiddenGitCapabilityTest.java`，包名相同。实现一个测试内私有扫描器，根目录固定为 `Path.of(System.getProperty("maven.multiModuleProjectDirectory"))`，递归扫描该根目录下所有模块的 `src/main/java` 和 `src/main/resources`，排除 `target` 与全部 `src/test`。只读取 UTF-8 文本后缀 `.java`、`.xml`、`.yml`、`.yaml`、`.properties`、`.json`、`.sql`、`.sh`、`.bat`、`.cmd`、`.ps1`，避免把二进制资源当文本。

扫描器必须报告相对路径与规则名称，不回显文件正文，并拒绝：

- 文本包含 `org.eclipse.jgit`、`org.kohsuke.github`、`org.gitlab4j.api`、`com.cdancy.bitbucket`、`io.github.cdancy.bitbucket` 或 `com.atlassian.bitbucket`；
- Java 文本在同一语句中以字符串字面量 `"git"` 构造 `ProcessBuilder`，包括直接 varargs 和 `List.of` 形式；
- Java 文本通过 `Runtime.getRuntime().exec(...)` 执行以 `git` 开头的字符串字面量；
- `.sh`、`.bat`、`.cmd`、`.ps1` 文本的非注释命令行以 `git` 开头。

匹配实现固定为六个包标记的精确 `String.contains`，以及以下三个预编译正则；不得临时改为只搜索单词 `git`：

```java
Pattern.compile("(?s)\\bnew\\s+ProcessBuilder\\s*\\(\\s*(?:List\\.of\\s*\\(\\s*)?\"git\"")
Pattern.compile("(?s)\\bRuntime\\s*\\.\\s*getRuntime\\s*\\(\\s*\\)\\s*\\.\\s*exec\\s*\\(\\s*\"git(?:\\s|\")")
Pattern.compile("^(?!\\s*(?:#|//|REM\\b|::))\\s*(?:exec\\s+)?git(?:\\s|$)", Pattern.MULTILINE | Pattern.CASE_INSENSITIVE)
```

前两个正则只应用于 `.java`，第三个只应用于四种脚本后缀；XML/YAML/properties/JSON/SQL 仍执行包标记检查。

该测试类包含：一个扫描真实生产文件的 `@Test`；一个 `@ParameterizedTest` + `@MethodSource`，分别用上述六个包标记、`ProcessBuilder("git", "status")`、`ProcessBuilder(List.of("git", "status"))`、`Runtime.getRuntime().exec("git status")` 和脚本 `git clone` 共十个反例断言扫描器返回违规；一个允许普通 `git` 业务文本和 `ProcessBuilder("java", "-version")` 的正例测试。连同 `ModuleDependencyTest`，`tensor-app` 共运行 13 个测试。

验证通过后，只暂存父 POM与两个新测试文件，提交消息固定为 `test: enforce backend architecture boundaries`；不得混入当前 docs 工作区变更。

## 如何测试

实施前运行以下无落盘结构契约作为 RED；当前父 POM没有 Enforcer 属性/插件，两个测试文件不存在，命令必须因此退出 1：

```bash
python3 - <<'PY'
from pathlib import Path
import xml.etree.ElementTree as ET

ns = {"m": "http://maven.apache.org/POM/4.0.0"}
root = ET.parse("data-plane/pom.xml").getroot()
properties = root.find("m:properties", ns)
assert properties is not None
assert properties.findtext("m:maven-enforcer-plugin.version", namespaces=ns) == "3.6.3"

plugins = root.findall("m:build/m:plugins/m:plugin", ns)
assert len(plugins) == 1
plugin = plugins[0]
assert plugin.findtext("m:groupId", namespaces=ns) == "org.apache.maven.plugins"
assert plugin.findtext("m:artifactId", namespaces=ns) == "maven-enforcer-plugin"
assert plugin.findtext("m:version", namespaces=ns) == "${maven-enforcer-plugin.version}"
execution = plugin.find("m:executions/m:execution", ns)
assert execution is not None
assert execution.findtext("m:id", namespaces=ns) == "ban-git-capabilities"
assert execution.findtext("m:phase", namespaces=ns) == "validate"
assert [node.text for node in execution.findall("m:goals/m:goal", ns)] == ["enforce"]
configuration = execution.find("m:configuration", ns)
assert configuration is not None
assert configuration.findtext("m:fail", namespaces=ns) == "true"
rule = configuration.find("m:rules/m:bannedDependencies", ns)
assert rule is not None
assert rule.findtext("m:searchTransitive", namespaces=ns) == "true"
assert [node.text for node in rule.findall("m:excludes/m:exclude", ns)] == [
    "org.eclipse.jgit:*",
    "org.kohsuke:github-api",
    "org.gitlab4j:gitlab4j-api",
    "com.cdancy:bitbucket-rest",
    "io.github.cdancy:bitbucket-rest",
    "com.atlassian.bitbucket:*",
    "com.atlassian.bitbucket.server:*",
]
assert rule.find("m:includes", ns) is None
assert rule.findtext("m:message", namespaces=ns) == (
    "Git and code-hosting API dependencies are forbidden in Tensor"
)

assert Path("data-plane/tensor-app/src/test/java/com/akkc/tensor/architecture/ModuleDependencyTest.java").is_file()
assert Path("data-plane/tensor-app/src/test/java/com/akkc/tensor/architecture/ForbiddenGitCapabilityTest.java").is_file()
PY
```

实施后重跑同一命令，预期退出码 0。再运行：

```bash
mvn -f data-plane/pom.xml validate
mvn -f data-plane/pom.xml -pl tensor-app -am test
mvn -f data-plane/pom.xml test
mvn -f data-plane/pom.xml verify
```

四条命令均预期退出码 0。`validate` 和 `verify` 必须显示 Enforcer `ban-git-capabilities` 执行且六项目 reactor 为 6/6 `SUCCESS`；两个 test 命令必须使用 Surefire 3.5.6，`tensor-app` 运行 13 个测试、0 failure、0 error、0 skipped。参数化反例必须全部通过，证明源码扫描器能拒绝每类已冻结能力；当前无模块生产类时 ArchUnit 规则通过但仍保持逐规则检查。

最后运行：

```bash
git diff --quiet -- data-plane/src/main/java/com/akkc/Main.java
git status --short --untracked-files=all -- data-plane
git diff --check
```

预期旧入口差异检查退出码 0；提交前 data-plane 状态只列父 POM与两个新测试；格式检查退出码 0。提交后使用 `git show --stat --oneline HEAD` 确认提交消息和三文件范围。

## 如何验证

- Enforcer：父 POM只增加 `maven-enforcer-plugin.version=3.6.3` 和一个 `validate` execution；七个 excludes 的顺序、传递依赖检查、消息与 fail 配置通过 XML 契约。
- 模块边界：ArchUnit 导入 `com.akkc.tensor` 生产类，四条规则精确表达允许图；当前空模块用逐规则 `allowEmptyShould(true)`，不全局放宽。
- 禁止 Git 能力：生产 Java/文本资源扫描覆盖六类 API 包、两种 `ProcessBuilder`、`Runtime.exec` 和脚本命令；十个反例与一个允许正例验证匹配边界，真实生产扫描零违规。
- 构建：`validate`、聚焦 test、全 reactor test 和 M01 `verify` 均退出 0；Enforcer 运行，`tensor-app` 为 13/13 tests passed。
- 范围：只修改父 POM并创建两个测试文件；保留 M01-T02 的版本/依赖/插件管理、五模块 POM、旧 Main 和 control-plane。
- Git：只提交三个目标文件，消息为 `test: enforce backend architecture boundaries`；`git diff --check` 退出 0，不混入 docs。

## 依赖什么信息

| 依赖 | 用途 | 稳定约束或前置条件 |
|---|---|---|
| `M01-T02` 提交 `6e692b9229cba8bbe5e83307402bcc5d1bfad14c` 的六 POM | 提供 Java 21、Boot 3.5.16、ArchUnit/JUnit/Surefire 和五模块依赖基线 | M01-T02 已在权威看板中 `COMPLETED`；本任务只扩展父 POM与 app 测试，不重排或改写既有管理项 |
| `docs/superpowers/plans/tensor-modules/M01-backend-foundation.md` 的 Task M01-T03 与 Global Constraints | 冻结三文件范围、模块图、Git 禁令、测试命令和提交消息 | 不读取前端，不引入业务实现，不把 Git 变成运行时能力 |
| `docs/design/Tensor_多源证券数据平台_TRD_v1.0.md` 的 1.4、3.3、16.2、20.1 | 确定禁止 Git/API/子进程、五模块依赖、Enforcer 与 ArchUnit 测试层 | package root 为 `com.akkc.tensor`；fixture 只在测试/验收启用 |
| Maven Central `maven-enforcer-plugin` 元数据和 Apache Maven Enforcer 3.6.3 `bannedDependencies` 文档（2026-08-31 读取） | 固定插件稳定版本、坐标匹配语法、传递依赖与 excludes 配置 | `release=3.6.3`，metadata `lastUpdated=20260518110420`；官方规则允许 `groupId:artifactId` 与通配符 |
| 本地 ArchUnit `1.5.0` API | 确认空基线的逐规则处理和生产类导入接口 | `ArchRule.allowEmptyShould(boolean)`、`ClassFileImporter.withImportOption(...)` 与 `importPackages(...)` 已由本地 JAR `javap` 确认 |
