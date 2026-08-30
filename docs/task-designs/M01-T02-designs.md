# M01-T02 Java 21、Boot 3.5.x 和测试依赖管理——任务设计

任务编号：`M01-T02`
对应任务：[M01-T02](../superpowers/plans/tensor-modules/M01-backend-foundation.md#task-m01-t02-锁定运行时与测试依赖15h)
实施产物：`data-plane/pom.xml` 与五个现有子模块 `pom.xml`

## 做什么

在 M01-T01 已完成的六 POM reactor 上锁定 Java 21、Spring Boot 3.5.x 最新稳定补丁、Maven 编译/测试插件以及 JUnit 5、AssertJ、Mockito、Testcontainers、WireMock、ArchUnit 版本，并只给五个模块声明其职责所需的模块、运行时和测试依赖。

本设计以 2026-08-31 读取的 Maven Central 元数据为版本裁决依据：`spring-boot-dependencies` 的最新稳定 `3.5.x` 为 `3.5.16`（元数据 `lastUpdated=20260820133516`）。与该 BOM 对齐，固定 Maven Compiler Plugin `3.14.1`、Surefire/Failsafe `3.5.6`、JUnit Jupiter `5.12.2`、AssertJ `3.27.7`、Mockito `5.17.0`、Testcontainers `1.21.4`；Maven Central 最新稳定 WireMock 3.x 为 `3.13.2`，ArchUnit 为 `1.5.0`。不得选用 Spring Boot 4.x、WireMock 4.0 beta、Surefire/Failsafe 3.6 milestone 或 Maven Compiler Plugin 4 beta。

完成后，父 POM 统一提供版本属性、Boot/Testcontainers BOM、内部模块和测试库 dependency management，以及 Java 21 的 Compiler/Surefire/Failsafe plugin management；五个子 POM只选择本模块所需依赖。`mvn help:effective-pom` 无版本解析错误，`mvn test` 在六项目 reactor 中退出 0。

本任务不创建或修改 Java、测试、资源、配置和前端文件；不添加 Enforcer、ArchUnit 测试或禁止 Git 扫描（M01-T03）；不删除或替换旧 `data-plane/src/main/java/com/akkc/Main.java`（延后到 M09）；不修改 M01-T01 的模块顺序和坐标。

## 怎么做

修改 `data-plane/pom.xml`，保留 M01-T01 的坐标、`pom` packaging 和五模块顺序，并增加以下精确属性：

| 属性 | 值 |
|---|---:|
| `maven.compiler.release` | `21` |
| `spring-boot.version` | `3.5.16` |
| `maven-compiler-plugin.version` | `3.14.1` |
| `maven-surefire-plugin.version` | `3.5.6` |
| `maven-failsafe-plugin.version` | `3.5.6` |
| `junit-jupiter.version` | `5.12.2` |
| `assertj.version` | `3.27.7` |
| `mockito.version` | `5.17.0` |
| `testcontainers.version` | `1.21.4` |
| `wiremock.version` | `3.13.2` |
| `archunit.version` | `1.5.0` |

父 POM 的 `<dependencyManagement>` 按以下顺序声明，子模块依赖均不重复版本：

1. import `org.springframework.boot:spring-boot-dependencies:${spring-boot.version}`；
2. import `org.testcontainers:testcontainers-bom:${testcontainers.version}`；
3. 管理四个内部依赖 `tensor-plugin-api`、`tensor-core`、`tensor-plugin-tushare`、`tensor-plugin-fixture`，版本均为 `${project.version}`；
4. 显式管理 `org.junit.jupiter:junit-jupiter:${junit-jupiter.version}`、`org.assertj:assertj-core:${assertj.version}`、`org.mockito:mockito-junit-jupiter:${mockito.version}`、`org.wiremock:wiremock-standalone:${wiremock.version}`、`com.tngtech.archunit:archunit-junit5:${archunit.version}`。

父 POM 的 `<build><pluginManagement>` 只管理以下三个插件：

- `org.apache.maven.plugins:maven-compiler-plugin:${maven-compiler-plugin.version}`，配置 `<release>${maven.compiler.release}</release>`；
- `org.apache.maven.plugins:maven-surefire-plugin:${maven-surefire-plugin.version}`；
- `org.apache.maven.plugins:maven-failsafe-plugin:${maven-failsafe-plugin.version}`。

不得在本任务为 Failsafe 增加 integration-test/verify execution；后续出现集成测试时再由对应任务声明 execution。不得继承 `spring-boot-starter-parent`，Boot 版本来源只能是上述 BOM import。

五个子 POM 的 `<dependencies>` 必须精确按以下职责选择，未注明 scope 的依赖使用默认 `compile`，所有依赖都省略 `<version>`：

- `tensor-plugin-api`：`junit-jupiter`、`assertj-core`、`mockito-junit-jupiter`，均为 `test`；不得有 Spring 或其他 compile/runtime 依赖。
- `tensor-core`：`tensor-plugin-api`、`org.springframework:spring-jdbc`、`jakarta.validation:jakarta.validation-api`；测试依赖为 JUnit/AssertJ/Mockito、`org.testcontainers:junit-jupiter` 和 `org.testcontainers:mysql`，均为 `test`。
- `tensor-plugin-tushare`：`tensor-plugin-api`、`org.springframework:spring-web`、`com.fasterxml.jackson.core:jackson-databind`；测试依赖为 JUnit/AssertJ/Mockito 和 `org.wiremock:wiremock-standalone`，均为 `test`。
- `tensor-plugin-fixture`：`tensor-plugin-api`；测试依赖为 JUnit/AssertJ/Mockito，均为 `test`；不得依赖 core、tushare 或 app。
- `tensor-app`：`tensor-core`、`tensor-plugin-tushare`、`spring-boot-starter-web`、`spring-boot-starter-validation`、`spring-boot-starter-jdbc`、`spring-boot-starter-actuator`、`org.flywaydb:flyway-core`、`org.flywaydb:flyway-mysql`；`com.mysql:mysql-connector-j` 为 `runtime`；`tensor-plugin-fixture`、JUnit/AssertJ/Mockito 和 `archunit-junit5` 为 `test`。

内部依赖方向因此固定为 `app -> core -> plugin-api`、`app -> plugin-tushare -> plugin-api`，fixture 只通过 app 的 test classpath 启用。不得新增 `core -> plugin-tushare`、外部 Git 能力或任务卡未列出的库。

全部验证通过后，只暂存六个 POM，并使用提交消息 `build: lock backend runtime and test dependencies`；不得把现有 docs 工作区变更混入提交。

## 如何测试

实施前运行以下无落盘结构契约作为 RED；当前父 POM没有 `properties`、`dependencyManagement`、`build`，命令必须因缺少目标属性/依赖结构退出 1，而不是因 XML 或 shell 语法错误退出：

```bash
python3 - <<'PY'
from pathlib import Path
import xml.etree.ElementTree as ET

ns = {"m": "http://maven.apache.org/POM/4.0.0"}
root = ET.parse("data-plane/pom.xml").getroot()
expected_properties = {
    "maven.compiler.release": "21",
    "spring-boot.version": "3.5.16",
    "maven-compiler-plugin.version": "3.14.1",
    "maven-surefire-plugin.version": "3.5.6",
    "maven-failsafe-plugin.version": "3.5.6",
    "junit-jupiter.version": "5.12.2",
    "assertj.version": "3.27.7",
    "mockito.version": "5.17.0",
    "testcontainers.version": "1.21.4",
    "wiremock.version": "3.13.2",
    "archunit.version": "1.5.0",
}
properties = root.find("m:properties", ns)
assert properties is not None
assert {node.tag.rsplit("}", 1)[-1]: node.text for node in properties} == expected_properties

def dependencies(project):
    result = []
    for dep in project.findall("m:dependencies/m:dependency", ns):
        result.append((
            dep.findtext("m:groupId", namespaces=ns),
            dep.findtext("m:artifactId", namespaces=ns),
            dep.findtext("m:scope", default="compile", namespaces=ns),
            dep.findtext("m:version", namespaces=ns),
        ))
    return result

expected = {
    "tensor-plugin-api": [
        ("org.junit.jupiter", "junit-jupiter", "test", None),
        ("org.assertj", "assertj-core", "test", None),
        ("org.mockito", "mockito-junit-jupiter", "test", None),
    ],
    "tensor-core": [
        ("com.akkc.tensor", "tensor-plugin-api", "compile", None),
        ("org.springframework", "spring-jdbc", "compile", None),
        ("jakarta.validation", "jakarta.validation-api", "compile", None),
        ("org.junit.jupiter", "junit-jupiter", "test", None),
        ("org.assertj", "assertj-core", "test", None),
        ("org.mockito", "mockito-junit-jupiter", "test", None),
        ("org.testcontainers", "junit-jupiter", "test", None),
        ("org.testcontainers", "mysql", "test", None),
    ],
    "tensor-plugin-tushare": [
        ("com.akkc.tensor", "tensor-plugin-api", "compile", None),
        ("org.springframework", "spring-web", "compile", None),
        ("com.fasterxml.jackson.core", "jackson-databind", "compile", None),
        ("org.junit.jupiter", "junit-jupiter", "test", None),
        ("org.assertj", "assertj-core", "test", None),
        ("org.mockito", "mockito-junit-jupiter", "test", None),
        ("org.wiremock", "wiremock-standalone", "test", None),
    ],
    "tensor-plugin-fixture": [
        ("com.akkc.tensor", "tensor-plugin-api", "compile", None),
        ("org.junit.jupiter", "junit-jupiter", "test", None),
        ("org.assertj", "assertj-core", "test", None),
        ("org.mockito", "mockito-junit-jupiter", "test", None),
    ],
    "tensor-app": [
        ("com.akkc.tensor", "tensor-core", "compile", None),
        ("com.akkc.tensor", "tensor-plugin-tushare", "compile", None),
        ("org.springframework.boot", "spring-boot-starter-web", "compile", None),
        ("org.springframework.boot", "spring-boot-starter-validation", "compile", None),
        ("org.springframework.boot", "spring-boot-starter-jdbc", "compile", None),
        ("org.springframework.boot", "spring-boot-starter-actuator", "compile", None),
        ("org.flywaydb", "flyway-core", "compile", None),
        ("org.flywaydb", "flyway-mysql", "compile", None),
        ("com.mysql", "mysql-connector-j", "runtime", None),
        ("com.akkc.tensor", "tensor-plugin-fixture", "test", None),
        ("org.junit.jupiter", "junit-jupiter", "test", None),
        ("org.assertj", "assertj-core", "test", None),
        ("org.mockito", "mockito-junit-jupiter", "test", None),
        ("com.tngtech.archunit", "archunit-junit5", "test", None),
    ],
}
for module, expected_dependencies in expected.items():
    child = ET.parse(Path("data-plane") / module / "pom.xml").getroot()
    assert dependencies(child) == expected_dependencies
PY
```

实施后重跑同一命令，预期退出码 0、1 个结构契约通过、0 个失败。再运行以下父级管理契约，预期退出码 0：

```bash
python3 - <<'PY'
import xml.etree.ElementTree as ET

ns = {"m": "http://maven.apache.org/POM/4.0.0"}
root = ET.parse("data-plane/pom.xml").getroot()
managed = [
    (
        node.findtext("m:groupId", namespaces=ns),
        node.findtext("m:artifactId", namespaces=ns),
        node.findtext("m:version", namespaces=ns),
        node.findtext("m:type", default="jar", namespaces=ns),
        node.findtext("m:scope", default="compile", namespaces=ns),
    )
    for node in root.findall("m:dependencyManagement/m:dependencies/m:dependency", ns)
]
assert managed == [
    ("org.springframework.boot", "spring-boot-dependencies", "${spring-boot.version}", "pom", "import"),
    ("org.testcontainers", "testcontainers-bom", "${testcontainers.version}", "pom", "import"),
    ("com.akkc.tensor", "tensor-plugin-api", "${project.version}", "jar", "compile"),
    ("com.akkc.tensor", "tensor-core", "${project.version}", "jar", "compile"),
    ("com.akkc.tensor", "tensor-plugin-tushare", "${project.version}", "jar", "compile"),
    ("com.akkc.tensor", "tensor-plugin-fixture", "${project.version}", "jar", "compile"),
    ("org.junit.jupiter", "junit-jupiter", "${junit-jupiter.version}", "jar", "compile"),
    ("org.assertj", "assertj-core", "${assertj.version}", "jar", "compile"),
    ("org.mockito", "mockito-junit-jupiter", "${mockito.version}", "jar", "compile"),
    ("org.wiremock", "wiremock-standalone", "${wiremock.version}", "jar", "compile"),
    ("com.tngtech.archunit", "archunit-junit5", "${archunit.version}", "jar", "compile"),
]
plugins = root.findall("m:build/m:pluginManagement/m:plugins/m:plugin", ns)
assert [node.findtext("m:artifactId", namespaces=ns) for node in plugins] == [
    "maven-compiler-plugin", "maven-surefire-plugin", "maven-failsafe-plugin"
]
assert [node.findtext("m:version", namespaces=ns) for node in plugins] == [
    "${maven-compiler-plugin.version}",
    "${maven-surefire-plugin.version}",
    "${maven-failsafe-plugin.version}",
]
assert plugins[0].findtext("m:configuration/m:release", namespaces=ns) == "${maven.compiler.release}"
assert all(node.find("m:executions", ns) is None for node in plugins)
PY
```

运行任务卡规定的有效 POM 和空套件验证：

```bash
mvn -f data-plane/pom.xml help:effective-pom -DskipTests
mvn -f data-plane/pom.xml test
```

两条命令均预期退出码 0、六个 reactor project `SUCCESS`、0 个失败；effective POM 不得出现缺失版本、不可解析 BOM/插件或重复坐标错误，test 必须使用 Java release 21 和 Surefire 3.5.6。首次执行可能需要 Maven Central；网络或本地仓库写权限失败属于环境问题，不能替代 GREEN。

最后运行：

```bash
mvn -q -f data-plane/pom.xml help:evaluate -Dexpression=maven.compiler.release -DforceStdout
git diff --quiet -- data-plane/src/main/java/com/akkc/Main.java
git status --short --untracked-files=all -- data-plane
git diff --check
```

预期 Java 表达式输出 `21` 且退出码 0；旧入口差异检查退出码 0；提交前 data-plane 状态只列六个 POM；格式检查退出码 0。

## 如何验证

- TRD 4、任务卡接口：父 POM 的 11 个属性精确匹配本设计，Boot `3.5.16` 和测试库/插件均为稳定版；不得出现动态范围、`LATEST`、milestone 或 beta。
- Java 21：Compiler Plugin `3.14.1` 的 effective configuration 中 release 为 `21`，本机 Maven 3.9.15/Java 21.0.11 环境能完成六模块 test reactor。
- 依赖管理：Boot/Testcontainers BOM、四个内部模块和五个显式测试库管理项顺序、版本与 scope 通过父级管理契约；子 POM 无显式版本。
- 模块边界：plugin-api 无 Spring compile/runtime 依赖；core 不依赖 tushare/app；fixture 不依赖 core/tushare/app；app 只以 test scope 消费 fixture；依赖结构契约退出 0。
- 构建结果：`help:effective-pom` 和 `mvn test` 均退出 0，六项目全部 `SUCCESS` 且无版本解析错误。
- 范围：只修改六个 POM；不创建源码、测试、资源或配置，不引入 Enforcer/Git 能力门禁，不修改旧 Main、control-plane 或 docs 实施基线。
- Git：`git diff --check` 退出 0；只暂存六个 POM并提交为 `build: lock backend runtime and test dependencies`，不得混入当前 docs 工作区变更。

## 依赖什么信息

| 依赖 | 用途 | 稳定约束或前置条件 |
|---|---|---|
| `M01-T01` 的提交 `09a5c65302b203c967b6eeb7540cd47cfbd1a78c` 与六个 POM | 提供已验证的父子坐标、固定模块顺序和最小 reactor | M01-T01 已在权威看板中 `COMPLETED`；M01-T02 保留坐标、顺序和旧 Main，只在六个 POM中增加版本/依赖/插件管理 |
| `docs/superpowers/plans/tensor-modules/M01-backend-foundation.md` 的 Task M01-T02 | 冻结文件范围、Java/Boot/测试接口、有效 POM/test 命令与提交消息 | 只读 M01 POM 和 TRD 4；Enforcer、ArchUnit 测试及禁止 Git 门禁仍属于 M01-T03 |
| `docs/design/Tensor_多源证券数据平台_TRD_v1.0.md` 第 4 节 | 确定 Java 21、Boot 3.5.x 最新补丁、Maven 3.9.x 和测试栈 | 补丁必须由构建文件固定，不得在运行时自动升级 |
| Maven Central `spring-boot-dependencies` 元数据与 `3.5.16` BOM（2026-08-31 读取） | 确定最新稳定 3.5.x 与 BOM 对齐版本 | 元数据最后更新 `20260820133516`；Boot `3.5.16`，Compiler `3.14.1`，Surefire/Failsafe `3.5.6`，JUnit `5.12.2`，AssertJ `3.27.7`，Mockito `5.17.0`，Testcontainers `1.21.4` |
| Maven Central WireMock 与 ArchUnit 元数据（2026-08-31 读取） | 确定 BOM 未统一锁定的测试工具稳定版 | WireMock 固定最新稳定 3.x `3.13.2`；ArchUnit 固定最新稳定 `1.5.0`；不得使用 WireMock 4 beta |
| 本机 `mvn -version` | 确认可执行环境满足 TRD 基线 | Maven `3.9.15`、Java `21.0.11`；首次解析新依赖仍可能需要 Maven Central 和本地仓库写权限 |
