# M02-T05 `DataSourcePlugin`、`DatasetAdapter` 和领域错误——任务设计

任务编号：`M02-T05`
对应任务：[M02-T05](../superpowers/plans/tensor-modules/M02-plugin-api.md#task-m02-t05-spi-和领域错误20h)
实施产物：`com.akkc.tensor.plugin.api` 下两个公开 SPI、`error` 子包下四个公开领域错误类型，以及 `PluginApiSurfaceTest.java`

## Goal

在 Java 21 `tensor-plugin-api` 模块中发布不依赖 Spring、JDBC、HTTP 或具体插件的最小数据源/适配器 SPI，并以同一组错误码和 retryable 真值表达受控领域失败。后续注册表、Tushare/fixture 插件、通用适配、核心服务和 REST 异常映射可以直接实现或消费这些稳定接口，而不复制方法签名、错误码或重试判断。

## Scope

包含：

- 创建 `DataSourcePlugin` 与 `DatasetAdapter`，冻结任务卡规定的方法名、参数和返回类型；
- 创建 `ErrorCode`、抽象 `TensorException`、最终类 `SourceException` 与 `AdapterException`；
- 冻结 16 项错误码及其 retryable 真值、异常构造器、错误类别限制和安全消息边界；
- 创建一个真实的 `PluginApiSurfaceTest`，覆盖精确公开接口、错误矩阵、异常类别和安全表面；
- 执行严格 TDD、模块回归、Enforcer、`jdeps`、范围和提交门禁。

排除：

- 不修改 POM、M00 契约或 M02-T01～T04 的既有 Java 类型；
- 不实现任何插件/适配器、注册表、参数校验、网络调用、类型转换、业务键生成、持久化、事务、查询、REST DTO、HTTP 状态映射、日志记录或前端行为；
- 不创建参数、注册、持久化、查询或内部错误的具体异常子类；这些后续类型可以继承 `TensorException`，但不属于本任务；
- 不新增接收或保存 Throwable cause、Token、凭证、原始上游响应、请求头、配置路径、SQL、堆栈文本、requestId、fieldErrors 或本地化消息的构造器/字段；
- 不向公共表面加入 Spring annotation、Bean、JDBC、HTTP 客户端、Jackson 或具体插件类型。

## Approach

### SPI

在包 `com.akkc.tensor.plugin.api` 中创建以下两个 public interfaces，方法集合和签名固定，不增加 default/static 方法或重载：

```java
public interface DataSourcePlugin {
    PluginDescriptor descriptor();
    PluginReadiness readiness();
    DownloadEnvelope download(ApiName apiName, Map<String, Object> params);
}

public interface DatasetAdapter {
    DatasetKey datasetKey();
    DatasetDefinition definition();
    AdaptedBatch adapt(DownloadEnvelope envelope, Instant ingestedAt);
}
```

`DataSourcePlugin` 只定义描述符、当前 readiness 与同步来源下载边界；参数语义校验仍由后续核心参数校验器负责。`DatasetAdapter` 只定义自身数据集、元数据定义和来源包络到适配批次的转换边界；它不得执行数据库操作。接口直接复用已完成 M02-T02 的描述符、M02-T03 的数据集定义与 M02-T04 的下载/批次类型，不创建等价裸字符串或重复 DTO。

### 错误码

在包 `com.akkc.tensor.plugin.api.error` 中创建以下精确闭集；常量顺序与 `docs/contracts/error-codes.md` 和 OpenAPI `ApiError.code` 相同：

```java
public enum ErrorCode {
    PARAM_REQUIRED(false),
    PARAM_INVALID(false),
    PLUGIN_DISABLED(false),
    DATASET_MISCONFIGURED(false),
    SOURCE_AUTH_FAILED(false),
    SOURCE_PERMISSION_DENIED(false),
    SOURCE_RATE_LIMITED(true),
    SOURCE_UNAVAILABLE(true),
    SOURCE_NETWORK_ERROR(true),
    SOURCE_TIMEOUT(true),
    SOURCE_PAYLOAD_INVALID(true),
    ADAPTER_FIELD_MISSING(false),
    ADAPTER_TYPE_INVALID(false),
    PERSISTENCE_FAILED(true),
    QUERY_FAILED(true),
    INTERNAL_ERROR(false);

    public boolean retryable();
}
```

`ErrorCode` 用 private final boolean 保存 retryable 真值，并由唯一 enum constructor 初始化；`retryable()` 原样返回该值。HTTP 状态属于 M09 的 REST 映射，不进入 plugin-api。异常不得接收独立 retryable 参数，避免调用者构造出与授权矩阵冲突的 `code/retryable` 组合。

### 领域异常

公开类型形状固定为：

```java
public abstract class TensorException extends RuntimeException {
    protected TensorException(ErrorCode code, String message);
    public final ErrorCode code();
    public final boolean retryable();
}

public final class SourceException extends TensorException {
    public SourceException(ErrorCode code, String message);
}

public final class AdapterException extends TensorException {
    public AdapterException(ErrorCode code, String message);
}
```

`TensorException` 保存一个 private final `ErrorCode code`；构造器用 `Objects.requireNonNull` 拒绝 null code/message，用 `message.isBlank()` 拒绝空白安全摘要，不 trim、不本地化、不改写消息。`code()` 返回已保存错误码；`retryable()` 必须只委托 `code.retryable()`，不保存第二份布尔状态。基类保持 abstract、构造器保持 protected，使后续预定义任务可以添加受控子类但不能直接构造无类别异常。

`SourceException` 只接受以下七项来源错误：`SOURCE_AUTH_FAILED`、`SOURCE_PERMISSION_DENIED`、`SOURCE_RATE_LIMITED`、`SOURCE_UNAVAILABLE`、`SOURCE_NETWORK_ERROR`、`SOURCE_TIMEOUT`、`SOURCE_PAYLOAD_INVALID`。`AdapterException` 只接受 `ADAPTER_FIELD_MISSING`、`ADAPTER_TYPE_INVALID`。null code 抛 `NullPointerException`；非本类别 code 抛 `IllegalArgumentException`。两个子类均为 final、无新增字段、无 cause/重载构造器。

安全消息是已经分类、可行动且可对外映射的摘要。类型层只能通过最小构造器和无新增诊断字段的声明表面收窄边界，不能分析任意字符串是否包含秘密，也不能移除 `RuntimeException` 继承的标准 Throwable 状态；调用者仍必须遵守 M00-T03/TRD 的规则，不把 Token、原始响应、请求头、SQL、堆栈或内部路径传入 message 或继承的 cause。M09 只把 `code()`、`getMessage()` 和 `retryable()` 映射到统一错误包络，绝不序列化 cause 或堆栈。

### 直接输入

- M00-T03 的 `docs/contracts/error-codes.md` 与 `docs/contracts/openapi-v1.yaml` 冻结 16 项 code/retryable 对应关系；plugin-api 不消费其中 HTTP 状态。
- M02-T02 提供 `PluginDescriptor`、`PluginReadiness`、`ApiName` 引用和无敏感信息 readiness 约束。
- M02-T03 提供 `DatasetDefinition`、`DatasetKey` 引用及适配器元数据边界。
- M02-T04 提供 `DownloadEnvelope`、`AdaptedBatch` 及成功/失败包络边界。

四项输入职责互补且无冲突：SPI 复用三组已完成 Java 契约，错误类型复用 M00-T03 的固定矩阵；错误类型不反向进入 M02-T04，避免依赖环。

## Files

- Create `data-plane/tensor-plugin-api/src/main/java/com/akkc/tensor/plugin/api/DataSourcePlugin.java`：发布最小数据源 SPI。
- Create `data-plane/tensor-plugin-api/src/main/java/com/akkc/tensor/plugin/api/DatasetAdapter.java`：发布最小数据集适配器 SPI。
- Create `data-plane/tensor-plugin-api/src/main/java/com/akkc/tensor/plugin/api/error/ErrorCode.java`：冻结 16 项领域错误码与 retryable 真值。
- Create `data-plane/tensor-plugin-api/src/main/java/com/akkc/tensor/plugin/api/error/TensorException.java`：提供安全消息、错误码和派生 retryable 的抽象基类。
- Create `data-plane/tensor-plugin-api/src/main/java/com/akkc/tensor/plugin/api/error/SourceException.java`：限制七项来源错误。
- Create `data-plane/tensor-plugin-api/src/main/java/com/akkc/tensor/plugin/api/error/AdapterException.java`：限制两项适配错误。
- Create `data-plane/tensor-plugin-api/src/test/java/com/akkc/tensor/plugin/api/PluginApiSurfaceTest.java`：验证公开 SPI、错误矩阵、异常类别与安全表面。

实现提交只暂存上述七个 Java 文件，提交消息固定为 `feat(plugin-api): publish plugin and adapter SPI`。任务准备文档、生成的 `target`、POM、既有 Java 文件和其他模块不得混入该提交。

## Tests

先完整创建 `PluginApiSurfaceTest.java`，不创建六个生产类型，然后运行：

```bash
mvn -f data-plane/pom.xml -pl tensor-plugin-api -am \
  -Dtest=PluginApiSurfaceTest -Dsurefire.failIfNoSpecifiedTests=false test
```

预期在 `testCompile` 因 `DataSourcePlugin`、`DatasetAdapter`、`ErrorCode`、`TensorException`、`SourceException` 和 `AdapterException` 不存在而退出非 0；失败必须来自缺失交付物，而不是测试语法、依赖解析或环境错误，作为 RED。

`PluginApiSurfaceTest` 只使用 JUnit 5、AssertJ、reflection 和真实类型，不使用 mocks，并覆盖：

- 两个接口各自恰有任务卡指定的三个 public abstract 方法；方法名集合、参数类型、泛型 `Map<String,Object>` 和返回类型精确一致，无 default/static/重载方法；
- `ErrorCode.values()` 的 16 项名称和顺序精确一致，每项 `retryable()` 与错误目录矩阵逐项一致；
- `TensorException` 是 abstract `RuntimeException`，只声明 protected `(ErrorCode,String)` 构造器、private final code 状态及 public final `code()`/`retryable()`；
- `SourceException` 与 `AdapterException` 是 final，只声明 public `(ErrorCode,String)` 构造器，无新增实例字段；
- 七项 source code 均可构造 `SourceException`，两项 adapter code 均可构造 `AdapterException`，并保留 code、原始安全消息和授权 retryable；
- source/adapter 以外的错误码分别被拒绝，null code/message 抛 `NullPointerException`，空或空白 message 抛 `IllegalArgumentException`；
- 反射检查任务新增的构造器、声明字段和声明方法不包含 Throwable、HTTP、Spring、JDBC、原始响应、请求头、Token、路径、SQL、requestId 或 fieldErrors 类型/字段；不把 `RuntimeException` 继承的标准方法误判为本任务新增表面。

完成最小实现后重跑聚焦命令，预期 `PluginApiSurfaceTest` 全部通过，0 failure、0 error、0 skipped，命令退出 0。随后运行：

```bash
mvn -f data-plane/pom.xml -pl tensor-plugin-api -am test
mvn -f data-plane/pom.xml -pl tensor-plugin-api -am verify
jdeps --multi-release 21 --print-module-deps \
  data-plane/tensor-plugin-api/target/tensor-plugin-api-1.0-SNAPSHOT.jar
```

前两条命令均预期退出 0；M02-T01～T04 的 71 项既有测试继续通过，新测试全部通过，父项目和 `tensor-plugin-api` 的 `ban-git-capabilities` 均通过。`jdeps` 预期只输出 `java.base` 并退出 0，不出现 Spring、JDBC、HTTP、具体插件或其他模块依赖。

最后运行：

```bash
rg -n 'org\.springframework|java\.sql|javax\.sql|jakarta\.persistence|java\.net\.http|RestClient|JdbcTemplate' \
  data-plane/tensor-plugin-api/src/main/java/com/akkc/tensor/plugin/api/DataSourcePlugin.java \
  data-plane/tensor-plugin-api/src/main/java/com/akkc/tensor/plugin/api/DatasetAdapter.java \
  data-plane/tensor-plugin-api/src/main/java/com/akkc/tensor/plugin/api/error
mvn -f data-plane/pom.xml -pl tensor-plugin-api -am clean
git diff --quiet -- data-plane/pom.xml data-plane/tensor-app
git status --short --untracked-files=all -- data-plane/tensor-plugin-api
git diff --check
```

第一条预期无输出且退出 1；`clean` 退出 0；POM/app diff check 退出 0；提交前 plugin-api 状态只列六个生产类型和一个测试且不列 `target`；格式检查退出 0。提交后以 `git show --stat --oneline HEAD` 确认固定消息和精确七文件范围。

## Acceptance

- 两个 SPI 的方法名集合、参数、泛型和返回类型与任务卡/TRD 6.1 逐项一致，没有额外公开方法或框架依赖。
- `ErrorCode` 与 M00-T03 的 16 项闭集、顺序及 retryable 真值完全一致，且不承担 HTTP 状态映射。
- 抽象基类和两个最终异常的继承、构造器、code、安全消息、派生 retryable 与类别限制均可观察；不存在 code/retryable 漂移或本任务新增的敏感诊断字段/构造器。
- source/adapter 合法错误可构造，跨类别错误、null 和空白消息在构造期失败；M09 可直接用 `code()`、`getMessage()`、`retryable()` 映射统一错误包络。
- 聚焦测试经历可归因 RED 后 GREEN；模块 `test`、`verify`、Enforcer、`jdeps`、禁用依赖扫描、范围、格式和精确七文件提交门禁全部得到预期结果。
- 未修改 POM、既有类型、M00 契约或其他模块，未提前实现插件、适配器、注册、核心编排、持久化、HTTP 或前端职责。

## Risks

异常声明表面刻意不提供 Throwable cause 构造器或诊断字段，以降低原始上游内容和秘密被主动带入跨模块/响应边界的风险；但 `RuntimeException` 仍继承标准 cause/stack/suppressed 状态，本任务不能宣称异常对象绝对不可变，也不能阻止调用者事后调用 `initCause`。后续 M07/M08 实现必须在受控、脱敏日志边界内记录必要诊断，不得把不安全 Throwable 作为跨模块数据，并以本设计的安全摘要构造 `SourceException`/`AdapterException`；M09 只能序列化 `code()`、`getMessage()` 和 `retryable()`。如果未来确需正式支持 cause 构造器，必须先形成新的授权设计，并证明不会让响应或普通日志暴露原始响应、Token、请求头或内部路径。
