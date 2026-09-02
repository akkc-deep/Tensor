# M05-T03 元数据驱动参数校验——任务设计

任务编号：`M05-T03`
对应任务：[M05-T03](../superpowers/plans/tensor-modules/M05-core-registry-adapter.md#task-m05-t03-元数据驱动参数校验30h)
实施产物：`ParameterValidator`、`ValidatedParameters` 和 `ParameterValidatorTest`

## Goal

在 `tensor-core` 中建立唯一的后端参数准入边界：只使用 `ApiDescriptor.parameters` 校验来自下载请求的字符串参数，拒绝未知、缺失和无效值，按元数据规范化合法值并返回不可变、有序的 `ValidatedParameters`。这样前端校验不能成为信任边界，插件只能接收已经通过同一元数据合同校验且不含 Token 的参数。

## Scope

包含：

- 创建无状态 `ParameterValidator`，公开任务卡固定的 `validate(ApiDescriptor, Map<String,Object>)`；
- 创建 `ValidatedParameters`，以不可变 `Map<String,Object>` 保存按描述符声明顺序排列的规范化字符串；
- 在 `ParameterValidator` 内嵌公开且只读的 `ParameterValidationException` 与 `FieldError`，分别承载 `PARAM_REQUIRED|PARAM_INVALID` 和确定排序的字段错误；
- 只依据 `ParameterDescriptor` 的 type、required、defaultValue、allowedValues、pattern、relatedParameter 和声明顺序执行校验，不读取 API 名或具体插件实现；
- 冻结空值、默认值、TEXT/TS_CODE 规范化、DATE/MONTH 严格解析、ENUM、pattern、范围顺序、错误聚合和安全消息规则；
- 以严格 TDD 完成任务卡场景，并执行模块回归、Enforcer、公开表面、静态、范围、格式和清理门禁。

排除：

- 不修改 POM、M02/M03/M05-T01～T02 的既有 Java、YAML、schema、OpenAPI 或其他模块；
- 不创建顶层第三个生产类型，不修改 `TensorException`/`ErrorCode`，不增加 Spring Bean、REST DTO、异常映射、日志或国际化；
- 不按 `ApiName`、`PluginId`、数据集或参数名字写分支，不调用插件、网络、数据库或持久化；
- 不实现 M05-T04 的数据列类型转换、M05-T05 的适配、M07/M08 下载或 M09 API 编排；
- 不接收、读取、保存或回显 Token、凭证、请求头和原始无效值。

## Approach

### 公开表面

在 `com.akkc.tensor.core.validation` 中冻结以下公开合同，不增加 builder、工厂、重载、cause 构造器或额外 diagnostics：

```java
public final class ParameterValidator {
    public ValidatedParameters validate(ApiDescriptor api, Map<String, Object> raw);

    public static final class ParameterValidationException extends TensorException {
        public List<FieldError> fieldErrors();
    }

    public record FieldError(String field, String message) {}
}

public record ValidatedParameters(Map<String, Object> values) {}
```

`ParameterValidator` 使用 public 无参构造器且不保存状态。`ParameterValidationException` 自身保持 final，构造器为 private，只允许外层 validator 产生类别正确的异常；它只接受 `PARAM_REQUIRED` 或 `PARAM_INVALID`，以 `List.copyOf` 保存非空、无 null 的字段错误。`FieldError` 拒绝 null、空或空白 field/message，不 trim 或改写安全消息。异常不保存 raw map、原始值、API 名、Token、cause、requestId 或堆栈文本；继承的 `code()`、`retryable()` 和 `getMessage()` 供后续 M09 映射。

`ValidatedParameters` 的 public compact constructor 拒绝 null map、null key/value、非 `String` value 和不满足 `^[a-z][a-z0-9_]{1,63}$` 的 key；以 `Collections.unmodifiableMap(new LinkedHashMap<>(values))` 保存快照，避免 `Map.copyOf` 丢失迭代顺序。validator 的成功结果只包含当前 API 已声明的参数，因此不会包含未知 `token`/`Token` 键。公开 record 可被直接构造，但只有 `ParameterValidator.validate` 的返回值代表完成语义校验的结果。

### 输入与元数据预检

`validate` 用 `Objects.requireNonNull` 拒绝 null `api` 或 `raw`，因为二者是核心调用方编程错误，不伪装成用户字段错误。OpenAPI `DownloadRequest.params.additionalProperties` 已冻结为 string；已声明字段的非 null 非 `String` 值因此是 `PARAM_INVALID`。null 或 `String.isBlank()` 的值视为未提供；有 `defaultValue` 时改用默认值，无默认值的可选参数从结果省略，无默认值的必填参数进入 required 阶段。

每次调用先预检可信元数据：非 null pattern 必须可由 `Pattern.compile` 编译；非 null defaultValue 必须通过该参数的全部 type/allowedValues/pattern 规则；每个 `DATE_RANGE_MEMBER` 必须指向同一参数列表中互相反向关联的另一个 `DATE_RANGE_MEMBER`。无效 pattern、默认值或关系以不带 cause 和原始内容的固定 `IllegalStateException("Invalid parameter metadata: " + parameter.name())` 阻止调用，不错误归类为用户输入问题。M03-T09 当前 49 API 的 pattern/defaultValue 均为 null，三组日期范围均按 `start_date,end_date` 声明且双向关联。

### 类型规范化

所有成功值仍为 `String`，可直接装入现有 `DataSourcePlugin.download(ApiName, Map<String,Object>)`：

- `DATE` 与 `DATE_RANGE_MEMBER`：不 trim，使用 `uuuuMMdd` 和 STRICT resolver 完整解析真实日历日期，成功后保留原八位字符串；
- `MONTH`：不 trim，以 STRICT `YearMonth` 完整解析 `uuuuMM`，成功后保留原六位字符串；
- `TS_CODE`：先 `strip()` 去除 Unicode 首尾空白，再用 `Locale.ROOT` 大写，随后完整匹配 `[A-Z0-9]+\.[A-Z0-9]+`；只接受一个非空的“代码.市场”分隔，具体插件若需进一步收窄可使用 pattern；
- `ENUM`：不 trim、不改写大小写，必须与 `allowedValues` 中一项逐字相等；
- `TEXT`：`strip()` 去除 Unicode 首尾空白，保留内部内容；规范化后为空视为未提供，并重新应用 default/required/optional 规则；
- 非 null pattern 在上述类型规范化之后用 matcher `matches()` 做整串匹配，不使用 substring `find()`。

validator 不修改传入 map、字符串或 descriptor。结果以 `ApiDescriptor.parameters` 顺序写入 `LinkedHashMap`；raw map 的迭代顺序不得影响结果。

### 日期范围

范围方向使用项目所有者批准的声明顺序，不从 `start_date`/`end_date` 等名字推断，也不修改 M02 公共类型。对每一对互相关联的 `DATE_RANGE_MEMBER` 只检查一次：参数列表中先声明者为下界，后声明者为上界；两者均成功解析且均存在时要求下界 `<=` 上界，相等合法。任一成员缺失或自身格式无效时不重复产生范围错误。

### 错误顺序与安全边界

用户输入错误分两阶段，同一调用不混合顶层 code：

1. 按描述符声明顺序聚合全部无默认值的必填缺失，抛 `PARAM_REQUIRED`，顶层消息固定为 `Required parameters are missing`，字段消息固定为 `is required`；此时暂不报告其他错误。
2. required 阶段通过后，聚合全部无效输入并抛 `PARAM_INVALID`，顶层消息固定为 `Parameters are invalid`。null 或不满足参数名正则的 raw key 不回显原文，合并为一个字段 `params`、消息 `contains an invalid field name` 的错误；符合参数名正则但未声明的 key 按字段名字典序列在其后，消息为 `is not declared`；已声明字段的非字符串或 type/enum/pattern 错误按描述符顺序排列，统一消息 `has invalid value`；反向范围只在先声明成员上记录 `must not be after <relatedParameter>`。

已声明字段名、relatedParameter 以及符合安全参数名正则的未知键可以出现在错误中；任意其他 raw key、raw value、defaultValue、pattern、API 名、Token 和异常 cause 不得进入顶层或字段消息。validator 不记录日志。

### 直接输入与兼容性

- M02-T02 的 `ApiDescriptor`、`ParameterDescriptor` 和六项 `ParameterType` 是校验规则与声明顺序的唯一运行时来源；validator 不改变这些不可变 records。
- M02-T05 的 `ErrorCode.PARAM_REQUIRED|PARAM_INVALID`、抽象 `TensorException` 和 `DataSourcePlugin.download(..., Map<String,Object>)` 提供错误分类、继承边界和下游参数容器；内嵌异常不改变 plugin-api。
- M03-T09 通过公开 loader 冻结 49 API 的显式参数全集、顺序、required、allowedValues 和 relatedParameter：当前全部声明参数均 required，ENUM 闭集明确，DATE/MONTH/TS_CODE 分组明确，三组范围参数均双向关联。

三项直接输入职责互补：M02-T02 定义规则形状，M02-T05 定义错误与 SPI，M03-T09 提供具体且独立验证过的参数实例；不存在依赖环。因批准的异常合同直接消费 M02-T05，看板 M05-T03 的直接依赖同步补充 M02-T05。

## Files

- Create `data-plane/tensor-core/src/main/java/com/akkc/tensor/core/validation/ParameterValidator.java`：实现元数据预检、两阶段校验、类型规范化、范围检查及两个内嵌公开错误类型。
- Create `data-plane/tensor-core/src/main/java/com/akkc/tensor/core/validation/ValidatedParameters.java`：保存有序、不可变的规范化字符串 map。
- Create `data-plane/tensor-core/src/test/java/com/akkc/tensor/core/validation/ParameterValidatorTest.java`：以真实描述符和公开类型覆盖十项行为与公开表面。

实现提交只暂存上述三个 Java 文件，固定消息为 `feat(core): validate plugin parameters from metadata`。设计、交接、看板、POM、既有 Java 和生成的 `target` 不得混入实现提交。

## Tests

### 基线与可归因 RED

实施前运行：

```bash
mvn -Dmaven.repo.local=/private/tmp/tensor-m2 -f data-plane/pom.xml \
  -pl tensor-core -am test
```

预期 `tensor-plugin-api` 79 项、`tensor-core` 20 项，共 99/99，0 failure、0 error、0 skipped；父项目、plugin-api、core 三层 Enforcer 通过。已有 M05-T02 Mockito/JDK 动态 agent 安全运行时提示允许保留，Maven/编译不得增加既有 platform-encoding 之外的警告类别。

随后先完整创建 `ParameterValidatorTest.java`，不创建两个生产文件，再运行：

```bash
mvn -Dmaven.repo.local=/private/tmp/tensor-m2 -f data-plane/pom.xml \
  -pl tensor-core -am -Dtest=ParameterValidatorTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

命令必须只因 `ParameterValidator`、`ValidatedParameters` 及内嵌错误类型不存在而在 `tensor-core:testCompile` 非零；不得因依赖解析、上游未匹配测试、测试语法或既有失败形成伪 RED。

### 聚焦 GREEN

创建最小生产实现后重跑同一聚焦命令。`ParameterValidatorTest` 固定恰有 10 个普通 `@Test`，10/10 通过且不使用 Mockito：

1. 六种参数类型的合法值按声明顺序成为字符串 map；输入后续修改不影响结果，record 输入/返回 map 不可修改；
2. optional 缺失被省略，合法 default 经相同规则规范化写入，空白 TEXT 重新走 default/required/optional；
3. 多个 null/空白必填值按描述符顺序聚合为 `PARAM_REQUIRED`，错误列表不可变且没有其他阶段错误；
4. 安全格式的未知字段按字典序、已声明非字符串字段按描述符顺序聚合为 `PARAM_INVALID`，null/非法格式 key 合并映射为 `params`，`token` 值不出现在异常中；
5. `000001.SZ` 与带首尾空白/小写市场的证券代码规范化成功，缺少代码或市场、多个点、内部空白和非法字符失败；
6. DATE/DATE_RANGE_MEMBER 接受严格真实 `yyyyMMdd` 与闰日，拒绝分隔符、首尾空白、非法日和宽松日期；
7. MONTH 接受严格真实 `yyyyMM`，拒绝分隔符、首尾空白、长度错误和 00/13 月；
8. ENUM 只逐字接受 allowedValues；pattern 在规范化后整串匹配，substring 不得通过；
9. 互相关联范围以声明顺序接受前小于后及相等，反向时只在先声明字段产生一个安全错误；
10. null api/raw 被拒绝；非法 pattern/default/范围元数据以固定安全 `IllegalStateException` 阻止；反射确认公开表面、异常类别限制、不可变 field errors 且没有 cause/raw/token 字段。

测试只使用 JUnit 5、AssertJ、真实 `ApiDescriptor`/`ParameterDescriptor`/错误类型和手工字面期望。不得从 validator private helper 反向生成期望，不断言日志，不把原始秘密放入失败消息。

### 模块、静态与范围门禁

运行：

```bash
mvn -Dmaven.repo.local=/private/tmp/tensor-m2 -f data-plane/pom.xml \
  -pl tensor-core -am test
mvn -Dmaven.repo.local=/private/tmp/tensor-m2 -f data-plane/pom.xml \
  -pl tensor-core -am verify
```

两条命令均预期 `tensor-plugin-api` 79 项、`RegistryTest` 10 项、`DatasetStartupValidatorTest` 10 项、`ParameterValidatorTest` 10 项，共 109/109，0 failure、0 error、0 skipped；三层 Enforcer 通过。

运行：

```bash
rg -n 'apiName\(|ApiName|PluginId|DatasetKey|tushare|daily|(?i:token|credential)|ServiceLoader|RestClient|org\.springframework|java\.sql|javax\.sql' \
  data-plane/tensor-core/src/main/java/com/akkc/tensor/core/validation
mvn -Dmaven.repo.local=/private/tmp/tensor-m2 -f data-plane/pom.xml \
  -pl tensor-core -am clean
git diff --quiet -- data-plane/pom.xml data-plane/tensor-app \
  data-plane/tensor-plugin-api data-plane/tensor-plugin-tushare
git status --short --untracked-files=all -- \
  data-plane/tensor-core/src/main/java/com/akkc/tensor/core/validation \
  data-plane/tensor-core/src/test/java/com/akkc/tensor/core/validation
git diff --check
```

扫描预期无输出并退出 1；`clean` 退出 0；非目标 POM/app/plugin-api/plugin-tushare 无差异；提交前 scoped status 精确列出三个新 Java 文件且无 `target`；格式检查退出 0。提交后 `git show --stat --oneline HEAD` 必须显示固定消息和精确三文件范围，工作树干净。

## Acceptance

- `ParameterValidator`、`ValidatedParameters`、内嵌异常和字段错误的公开表面与本设计精确一致；结果和错误集合均为有序不可变快照；
- validator 只读取参数元数据，无 API/插件/数据集名字分支，成功结果只含已声明键和规范化字符串，不能携带未知 Token；
- required/default/optional、非字符串、未知字段、六类 type、整串 pattern 和声明顺序范围规则均得到确定结果；日期/月严格、TS_CODE/TEXT 只执行批准的规范化、ENUM 精确匹配；
- 用户错误按两阶段分别产生 `PARAM_REQUIRED` 或 `PARAM_INVALID` 及确定排序的字段错误；消息不含 raw value、默认值、pattern、Token、cause 或内部路径；错误元数据安全地阻止调用；
- 严格 TDD 得到缺两个生产类型的可归因 RED 后 10/10 GREEN；模块 `test`/`verify` 109/109、三层 Enforcer、静态、范围、格式、清理和精确三文件提交门禁全部通过；
- 未修改 POM、plugin-api、Tushare metadata 或其他模块，未提前实现 Spring/REST/插件调用、列转换、适配、下载或持久化职责。

## Risks

- Java 正则没有执行超时；v1 的 49 份已批准元数据目前没有 pattern，未来插件提供 pattern 时必须把它视为受信配置并经审查，不能接受用户动态正则。
- `ValidatedParameters` 的 component 保持 `Map<String,Object>` 是为了与既有 SPI 精确兼容；本设计以构造器不变量保证其中实际值均为 String，后续消费者不得向 map 强制写入其他类型。
- 日期范围方向依赖已批准并由 M03-T09 冻结的参数声明顺序。若未来需要独立于展示顺序的方向，应先扩展 M02 元数据合同，而不是回退到参数名或 API 名猜测。
