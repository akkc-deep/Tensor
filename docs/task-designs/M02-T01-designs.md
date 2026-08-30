# M02-T01 `PluginId`、`ApiName`、`DatasetKey`、`TableName`、`RequestId`——任务设计

任务编号：`M02-T01`
对应任务：[M02-T01](../superpowers/plans/tensor-modules/M02-plugin-api.md#task-m02-t01-标识和值对象15h)
实施产物：`data-plane/tensor-plugin-api/src/main/java/com/akkc/tensor/plugin/api/model/` 下五个值对象，以及 `IdentifierTest.java`

## 做什么

在已建立的 Java 21 `tensor-plugin-api` 模块中交付五个不可变 Java records：`PluginId`、`ApiName`、`DatasetKey`、`TableName` 和 `RequestId`。它们冻结插件、上游接口、数据集、数据库表和请求关联标识的最小公共表示，使后续描述符、数据集定义、注册表、持久化和请求日志只消费已校验值对象，不重复传递裸字符串。

`PluginId` 与 `ApiName` 精确执行 `^[a-z][a-z0-9_]{1,63}$`，不 trim、不改写大小写；`DatasetKey` 由两者组成；`TableName` 固定为 `<plugin_id>__<api_name>`；`RequestId.newId()` 生成不接收用户输入的 UUID。示例组合必须得到 `tushare_pro/daily` 的两个组件和 `tushare_pro__daily` 表名。

本任务不创建描述符、数据集字段、下载包络、SPI、异常体系、Spring Bean、数据库或 REST DTO；不修改任何 POM、现有 M01 门禁、其他模块、旧 Main、资源、配置和前端；不实现客户端请求标识沿用逻辑，该行为属于后续 `RequestIdFilter` 任务。

## 怎么做

在包 `com.akkc.tensor.plugin.api.model` 中创建以下公开 records；除下列接口外不增加别名工厂、解析器或共享基类：

```java
public record PluginId(String value) {
    public static PluginId of(String value);
}

public record ApiName(String value) {
    public static ApiName of(String value);
}

public record DatasetKey(PluginId pluginId, ApiName apiName) {
    public static DatasetKey of(PluginId pluginId, ApiName apiName);
}

public record TableName(String value) {
    public static TableName from(DatasetKey datasetKey);
}

public record RequestId(UUID value) {
    public static RequestId newId();
}
```

`PluginId` 和 `ApiName` 各自在 compact constructor 中先以 `Objects.requireNonNull` 拒绝 null，再以预编译 `Pattern` 对原始字符串执行整串匹配；空串、空白、单字符、首字符非小写字母、含大写或连字符、长度超过 64 均抛 `IllegalArgumentException`。允许的最短值为两个字符，最长值为 64 个字符；工厂只调用 canonical constructor，不做规范化。

`DatasetKey` 的 compact constructor 对两个组件执行 `Objects.requireNonNull`，`of` 只构造该 record。保持 record 默认的组件访问器、值相等和哈希语义，不增加拼接字符串作为第三份状态。

`TableName` 的 compact constructor 拒绝 null，并用 `^[a-z][a-z0-9_]{1,63}__[a-z][a-z0-9_]{1,63}$` 验证所有可构造实例都对应一个合法插件/接口组合；`from` 先拒绝 null，再且仅以 `datasetKey.pluginId().value() + "__" + datasetKey.apiName().value()` 形成值。不得接受单下划线分隔、大小写或连字符，不截断 64 字符组件。

`RequestId` 使用 `java.util.UUID` 作为唯一组件，compact constructor 拒绝 null；`newId` 只调用 `UUID.randomUUID()`，没有输入参数、用户数据或外部状态。保持 record 默认值语义，不在本任务加入来自字符串或客户端头的工厂。

创建一个 `IdentifierTest.java`，使用 JUnit 5 与 AssertJ 直接测试上述真实 records，不使用 mocks。测试以字面量形成预期结果，并覆盖：

- `tushare_pro`、`daily`、两字符和 64 字符边界均被两个字符串标识工厂接受；
- uppercase、hyphen、leading digit、one-character、65-character、empty、blank 和 null 被两个工厂拒绝，且无 trim/小写化；
- `DatasetKey.of` 保留两个精确组件并拒绝任一 null；
- `TableName.from(DatasetKey.of(PluginId.of("tushare_pro"), ApiName.of("daily")))` 的 `value()` 精确为 `tushare_pro__daily`，并拒绝 null、单下划线、非法大小写/连字符 canonical 值；
- `RequestId.newId()` 返回非 null、UUID version 4 / RFC 4122 variant 2 的值，连续两次结果不同。

只暂存五个生产 records 和 `IdentifierTest.java`，提交消息固定为 `feat(plugin-api): add validated identifiers`；不得混入当前任务准备文档或生成的 `target` 内容。

## 如何测试

实施前先创建完整 `IdentifierTest.java`，不创建五个生产 records，然后运行：

```bash
mvn -f data-plane/pom.xml -pl tensor-plugin-api -am \
  -Dtest=IdentifierTest -Dsurefire.failIfNoSpecifiedTests=false test
```

命令必须因 `PluginId`、`ApiName`、`DatasetKey`、`TableName` 和 `RequestId` 不存在而在 `testCompile` 退出非 0；确认失败来自缺失交付物而非测试语法、依赖解析或环境错误，作为 RED。

按“怎么做”完成最小实现后重跑同一命令，预期 Surefire 3.5.6 执行 `IdentifierTest`，0 failure、0 error、0 skipped，命令退出 0。随后运行：

```bash
mvn -f data-plane/pom.xml -pl tensor-plugin-api -am test
mvn -f data-plane/pom.xml -pl tensor-plugin-api -am verify
```

两条命令均预期退出 0；`verify` 必须显示 `ban-git-capabilities` Enforcer 对 `data-plane` 与 `tensor-plugin-api` 通过，模块 reactor 为 2/2 `SUCCESS`。测试不得依赖执行顺序或固定 UUID 值。

最后运行：

```bash
git diff --quiet -- data-plane/pom.xml data-plane/tensor-app
git status --short --untracked-files=all -- data-plane/tensor-plugin-api
git diff --check
```

预期第一条退出 0；提交前状态只列五个生产 records 和一个测试，且不列 `target`；格式检查退出 0。提交后用 `git show --stat --oneline HEAD` 确认固定消息和六文件范围。

## 如何验证

- 标识边界：两个字符串标识对象对原始输入执行同一条冻结正则，接受 2～64 字符合法边界，拒绝任务卡列出的五类非法值及 null/空白，且不做静默规范化。
- 派生关系：`DatasetKey` 只保存非 null `PluginId`/`ApiName`；`TableName.from` 对 `tushare_pro/daily` 唯一产生 `tushare_pro__daily`，所有 `TableName` 实例满足双下划线和两个合法组件的结构。
- 请求标识：公开工厂恰为无参 `RequestId.newId()`，返回 version 4、variant 2 UUID；接口不接收或存储用户数据。
- 不可变性与依赖：五类均为 Java records，只依赖 JDK；`tensor-plugin-api` 不新增 Spring、JDBC、HTTP、具体插件或其他模块依赖。
- TDD 与构建：目标测试先因五类缺失 RED，后 GREEN；聚焦测试、模块回归和 `verify` 全部退出 0，M01 Enforcer 门禁继续通过。
- 范围与 Git：净实现仅六个指定 Java 文件，父/子 POM、app 架构测试和其他模块无差异；提交消息与文件范围精确匹配任务卡。

## 依赖什么信息

| 依赖或来源 | 用途 | 稳定约束或前置条件 |
|---|---|---|
| `M01-T02` 提交 `6e692b9229cba8bbe5e83307402bcc5d1bfad14c` 的父 POM与 `tensor-plugin-api/pom.xml` | 提供 Java 21、JUnit 5、AssertJ、Surefire 3.5.6 和可测试模块基线 | M01-T02 在权威看板中为 `COMPLETED`；本任务不修改 POM |
| `docs/superpowers/plans/tensor-modules/M02-plugin-api.md` 的 Global Constraints、Task M02-T01 与 Module Gate | 冻结包根、五个文件/工厂、非法输入、TDD 命令、范围和提交消息 | records 不依赖具体插件、数据库、Spring 或 Vue；不临时扩展后续任务类型 |
| `docs/design/Tensor_多源证券数据平台_TRD_v1.0.md` 5.1 | 冻结五个值对象的正则、组合、表名派生和服务端 UUID 语义 | `PluginId`/`ApiName` 为 2～64 字符；表名双下划线；RequestId 不含用户数据 |
| `docs/contracts/dataset-definition.schema.json` 的 `pluginId`、`apiName` 和 `tableName` | 对照 M00-T02 已冻结的字符串正则和 `tushare_pro__<api>` 示例域 | Java 通用表名仍按任意合法 PluginId/ApiName 派生，不把 `tushare_pro` 写死 |
| 已完成 M01-T03 的 Enforcer 与生产源码门禁 | 在目标 `test`/`verify` 中继续拒绝 Git/代码托管依赖和能力 | 只作为当前构建环境门禁；不改变权威看板列出的直接依赖 M01-T02 |
