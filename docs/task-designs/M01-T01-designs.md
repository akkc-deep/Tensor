# M01-T01 五模块 Maven 聚合骨架——任务设计

任务编号：`M01-T01`  
对应任务：[M01-T01](../superpowers/plans/tensor-modules/M01-backend-foundation.md#task-m01-t01-创建五模块聚合骨架15h)  
实施产物：`data-plane/pom.xml`、`data-plane/tensor-plugin-api/pom.xml`、`data-plane/tensor-core/pom.xml`、`data-plane/tensor-plugin-tushare/pom.xml`、`data-plane/tensor-plugin-fixture/pom.xml`、`data-plane/tensor-app/pom.xml`

## 做什么

把现有 `data-plane` Maven 工程改为父聚合工程，并创建 `tensor-plugin-api`、`tensor-core`、`tensor-plugin-tushare`、`tensor-plugin-fixture`、`tensor-app` 五个空实现子模块。完成后 Maven 能按上述固定顺序发现并校验五个模块，每个子模块的有效坐标均为 `com.akkc.tensor:tensor-<name>:1.0-SNAPSHOT`。

本任务只建立 Maven reactor 和父子坐标，不添加 Java 21、Spring Boot、测试库、业务依赖、插件版本、模块间依赖、Java 源码、资源、测试或架构门禁；这些分别留给 M01-T02、M01-T03 和后续业务任务。保留并且不修改 `data-plane/src/main/java/com/akkc/Main.java`，旧示例入口的删除及正式应用入口替换延后到 M09；不读取或修改 `control-plane`。

可观察结果是根 POM 的 packaging 为 `pom`，五个子目录各只有本任务创建的最小子 POM，`mvn validate` 成功且 reactor 顺序与 TRD 3.3 一致。

## 怎么做

修改 `data-plane/pom.xml`：保留 Maven 4.0.0 模型与 `data-plane` artifactId，把 groupId 固定为 `com.akkc.tensor`、version 固定为 `1.0-SNAPSHOT`，增加 `<packaging>pom</packaging>`，并按以下唯一顺序增加 `<modules>`：

1. `tensor-plugin-api`
2. `tensor-core`
3. `tensor-plugin-tushare`
4. `tensor-plugin-fixture`
5. `tensor-app`

分别创建五个子 POM。每个子 POM都使用 Maven 4.0.0 模型，并声明同一个父坐标：

```xml
<parent>
    <groupId>com.akkc.tensor</groupId>
    <artifactId>data-plane</artifactId>
    <version>1.0-SNAPSHOT</version>
    <relativePath>../pom.xml</relativePath>
</parent>
```

每个子 POM 只在 `<parent>` 之后声明与目录同名的 `<artifactId>`；groupId、version 和默认 `jar` packaging 均从父工程或 Maven 默认值获得。不得在本任务的父 POM或子 POM中增加 `<properties>`、`<dependencyManagement>`、`<dependencies>`、`<build>`、`<profiles>` 或模块间依赖，也不得创建空源码目录或占位类。

父 POM 解析失败、子 POM 缺失、父坐标不一致或 reactor 顺序漂移时，Maven 校验必须失败；不得通过删除模块、跳过模块或在子 POM中重复版本来绕过失败。Maven Help Plugin 无法解析时属于执行环境/依赖获取问题，不是预期的缺失骨架 RED；先保留诊断，再解决环境问题后重跑。

全部测试和验证通过后，仅暂存 `data-plane` 下本任务的六个 POM，并按任务卡使用提交消息 `build: create backend Maven modules`；不得把现有 M00 文档工作区变更混入该提交。

## 如何测试

实施前运行以下结构契约作为 RED：

```bash
python3 -c 'from pathlib import Path; import xml.etree.ElementTree as ET; ns={"m":"http://maven.apache.org/POM/4.0.0"}; expected=["tensor-plugin-api","tensor-core","tensor-plugin-tushare","tensor-plugin-fixture","tensor-app"]; root=ET.parse("data-plane/pom.xml").getroot(); assert root.findtext("m:packaging",namespaces=ns)=="pom"; modules=[node.text for node in root.findall("m:modules/m:module",ns)]; assert modules==expected; assert all((Path("data-plane")/name/"pom.xml").is_file() for name in expected)'
```

预期：当前缺少 parent packaging、modules 和五个子 POM，命令因结构断言失败退出码 1，0 项通过、1 项失败；失败必须来自缺失目标结构，而不是 XML 语法或环境错误。

实施后重跑同一命令，预期退出码 0，1 项通过、0 项失败。随后运行完整父子坐标和范围检查：

```bash
python3 -c 'from pathlib import Path; import xml.etree.ElementTree as ET; ns={"m":"http://maven.apache.org/POM/4.0.0"}; expected=["tensor-plugin-api","tensor-core","tensor-plugin-tushare","tensor-plugin-fixture","tensor-app"]; root=ET.parse("data-plane/pom.xml").getroot(); assert root.findtext("m:groupId",namespaces=ns)=="com.akkc.tensor" and root.findtext("m:artifactId",namespaces=ns)=="data-plane" and root.findtext("m:version",namespaces=ns)=="1.0-SNAPSHOT" and root.findtext("m:packaging",namespaces=ns)=="pom"; assert [n.text for n in root.findall("m:modules/m:module",ns)]==expected; banned=("m:properties","m:dependencyManagement","m:dependencies","m:build","m:profiles"); assert all(root.find(tag,ns) is None for tag in banned); exec("for name in expected:\n child=ET.parse(Path('data-plane')/name/'pom.xml').getroot(); parent=child.find('m:parent',ns); assert child.findtext('m:modelVersion',namespaces=ns)=='4.0.0'; assert parent is not None; assert parent.findtext('m:groupId',namespaces=ns)=='com.akkc.tensor'; assert parent.findtext('m:artifactId',namespaces=ns)=='data-plane'; assert parent.findtext('m:version',namespaces=ns)=='1.0-SNAPSHOT'; assert parent.findtext('m:relativePath',namespaces=ns)=='../pom.xml'; assert child.findtext('m:artifactId',namespaces=ns)==name; assert child.find('m:groupId',ns) is None and child.find('m:version',ns) is None; assert all(child.find(tag,ns) is None for tag in banned)")'
```

预期：父坐标、五个模块顺序、五个子 POM 的继承关系及 T02/T03 排除边界全部成立，退出码 0，1 项通过、0 项失败。

运行任务卡的 reactor 检查：

```bash
mvn -q -f data-plane/pom.xml help:evaluate -Dexpression=project.modules -DforceStdout
```

实施前预期输出不包含完整五模块列表；实施后预期输出按序包含 `[tensor-plugin-api, tensor-core, tensor-plugin-tushare, tensor-plugin-fixture, tensor-app]` 且退出码 0。命令本身在实施前可能因 Maven Help Plugin 对空 modules 的表示而退出 0，因此是否缺失骨架以先行结构契约的非零结果为准。

运行 Maven reactor 校验：

```bash
mvn -q -f data-plane/pom.xml validate
```

预期：父工程和五个子模块均能解析，退出码 0，六个 reactor project 校验成功、0 个失败。

最后运行范围回归：

```bash
git diff --quiet -- data-plane/src/main/java/com/akkc/Main.java
git status --short --untracked-files=all -- data-plane
git diff --check
```

预期：旧入口差异检查退出码 0；提交前状态只列出修改后的 `data-plane/pom.xml` 和五个新增子 POM，不列出其他 `data-plane` 文件；差异格式检查退出码 0。

## 如何验证

- `PRD 10.4`、`AC-017`、TRD 3.3：`data-plane/pom.xml` 是 `com.akkc.tensor:data-plane:1.0-SNAPSHOT` 的 `pom` 聚合工程，并按固定顺序列出五个模块；结构契约与 Maven reactor 检查均得到预期结果。
- `PRD 10.4`、TRD 3.3：五个子模块均继承同一父坐标，只声明各自 artifactId，从而形成 `com.akkc.tensor:tensor-*:1.0-SNAPSHOT` 坐标；父子坐标检查退出码 0。
- 任务范围：父子 POM 不包含 Java/Boot/测试依赖、插件管理、模块依赖或架构门禁，`Main.java` 保持无差异，未创建或修改前端、Java、资源和测试文件。
- 构建结果：`mvn -q -f data-plane/pom.xml validate` 退出码 0，五个子模块全部进入 reactor，且没有跳过或失败模块。
- 设计追踪：任务卡链接本设计，本设计链接回 M01-T01 任务卡；设计只有五个固定二级标题且没有需要裁决的占位内容。
- 质量门禁：运行本设计“如何测试”的全部命令并得到注明的退出码、通过/失败计数和有限摘要；运行 `git diff --check` 退出码 0。
- Git 可用时，在上述门禁通过后执行 `git add data-plane && git commit -m "build: create backend Maven modules"`；提交范围只允许六个 POM，不得包含当前工作区中的 M00 文档变更。

## 依赖什么信息

| 依赖 | 用途 | 稳定约束或前置条件 |
|---|---|---|
| `docs/task-handoffs/tensor-v1-task-board.md` 的 M01-T01 行与详情 | 确定任务身份、顺序、状态、直接依赖、范围和验收边界 | 看板是任务状态和设计/交接引用的唯一权威；只有 M00-T04 完成并完成本设计与交接后才可准备为 `READY` |
| `docs/superpowers/plans/tensor-modules/M01-backend-foundation.md` 的 Task M01-T01 | 确定六个 POM 路径、五模块顺序、父子坐标、RED/GREEN 与提交门禁 | 本任务只建骨架；Java/Boot/测试依赖和架构门禁不提前混入 |
| `docs/design/Tensor_多源证券数据平台_TRD_v1.0.md` 3.3 | 确定五模块职责、目录、依赖方向和包根 | 只在本任务中实现目录/坐标骨架；依赖边和 ArchUnit 验证由后续任务实现 |
| `data-plane/pom.xml` 的实施前基线 | 确定现有父坐标和缺失的 packaging/modules | 仅修改此 POM；`data-plane/src/main/java/com/akkc/Main.java` 保持不变并延后到 M09 处理 |
| `docs/superpowers/task-templates/task-design.md`、`docs/superpowers/task-templates/acceptance-evidence.md`（M00-T04） | 约束设计结构，并规定实施后命令、时间、退出码、计数、有限摘要和敏感扫描证据字段 | M00-T04 已在权威看板中 `COMPLETED`；模板不承载看板状态或恢复信息，也不要求本任务自行发明新的证据文件路径 |
| `docs/traceability/tensor-v1-requirements.md` | 提供 `PRD 10.4`、`AC-017` 的稳定追踪语义 | `Evidence` 只表示计划证据责任，不代表本任务验收已经执行或通过 |

Maven Help Plugin 可能需要本地缓存或网络才能执行；插件解析失败时应记录为环境阻塞，不得误报为成功的 RED。当前工作区已有未提交的 M00 文档，未来实施只允许暂存 `data-plane`，以避免跨任务混入。
