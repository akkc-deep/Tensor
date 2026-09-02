# M05-T04 严格字段类型转换——任务设计

任务编号：`M05-T04`
对应任务：[M05-T04](../superpowers/plans/tensor-modules/M05-core-registry-adapter.md#task-m05-t04-严格字段类型转换35h)
实施产物：`ValueConverter`、`ConversionContext` 和 `ValueConverterTest`

## Goal

在 `tensor-core` 中建立唯一的元数据驱动单元值转换边界：依据 `ColumnDefinition.logicalType` 把来源标量严格转换为 `String`、`LocalDate`、`Long`、`BigDecimal` 或 `null`，拒绝模糊日期、整数小数/溢出、十进制舍入/超精度、闭合枚举越界和不受支持的运行时类型。失败以不含原始值的统一 `ADAPTER_TYPE_INVALID` 摘要定位 API、来源行和字段，使后续通用适配可以整批失败且不发生静默改值或精度损失。

## Scope

包含：

- 创建无状态 `ValueConverter`，公开任务卡固定的 `convert(Object, ColumnDefinition, ConversionContext)`；
- 创建只携带 `ApiName` 和从 0 开始行下标的不可变 `ConversionContext`；
- 冻结 null/空字符串、STRING/TEXT/ENUM、DATE/MONTH、LONG/DECIMAL 的输入类型、规范化、返回类型和失败规则；
- 对短字符串按 Unicode 码点执行长度门禁，对闭合枚举执行大小写敏感的精确成员检查；
- 对日期和月份执行 ASCII 固定宽度与真实日历严格校验，对数值执行 exact 转换、precision/scale 和 `RoundingMode.UNNECESSARY`；
- 以严格 TDD 覆盖批准的输入矩阵、安全错误和边界反例，并执行模块回归、Enforcer、静态、范围、格式和清理门禁。

排除：

- 不修改 POM、plugin-api、M03 YAML、Flyway、既有 M05 类型或其他模块；
- 不实现 M05-T05 的 `GenericDatasetAdapter`、字段/包络映射、不可空列/业务键校验、去重、指纹键或批次构造；
- 不调用插件、网络、数据库、JDBC、Spring、REST、日志或持久化；
- 不接受 `Float`/`Double`，不调用任意来源对象的 `toString()`，不静默截断字符串、取整、舍入或猜测日期；
- 不把原始来源值、来源运行时类名、目标类型、解析异常文本、cause、Token 或其他敏感诊断写入异常消息。

## Approach

### 公开表面与职责边界

在 `com.akkc.tensor.core.adapter` 中冻结以下公开合同，不增加 builder、工厂、重载、策略类或额外公开诊断类型：

```java
public final class ValueConverter {
    public Object convert(Object source, ColumnDefinition column, ConversionContext context);
}

public record ConversionContext(ApiName apiName, int rowIndex) {}
```

`ConversionContext` 的 public compact constructor 用 `Objects.requireNonNull` 拒绝 null `apiName`，用 `IllegalArgumentException` 拒绝 `rowIndex < 0`；行下标从 0 开始，与后续 `DownloadEnvelope.data()` 列表下标一致。record 不增加便捷构造器、字段或方法。

`ValueConverter` 使用 public 无参构造器且不保存实例状态。`convert` 用 `Objects.requireNonNull` 拒绝 null `column`/`context`，因为它们是核心调用方编程错误，不伪装成来源数据错误；`source` 允许 null。方法只按 `column.logicalType()` 的 exhaustive switch 分派到私有转换方法，不按 API、字段名、插件或数据集写分支。

`source == null` 始终返回 null，不读取 `column.nullable()`。除 `TEXT` 外，String 来源先执行 `trim()`，规范化后为空也返回 null；这一路径同样不读取 nullable。M05-T05 必须在类型转换之后，以 `ADAPTER_FIELD_MISSING` 校验全部 `nullable == false` 列和业务键字段，M05-T04 不提前复制该职责。

### 文本与枚举

- `STRING` 只接受 `String`。执行 `trim()` 后，空结果返回 null；非空结果按 `codePointCount(0, length)` 计算 Unicode 码点数，超过 `column.length()` 时失败，绝不截断。成功返回规范化字符串。
- `TEXT` 只接受 `String`，原样返回，包括首尾空格、空字符串和纯空白；不解释或执行 HTML，也不应用短字符串长度规则。只有来源 null 返回 null。
- `ENUM` 只接受 `String`，先执行与 STRING 相同的 trim、空值和码点长度规则。`column.allowedValues()` 为空表示开放枚举，返回规范化字符串；非空表示闭合枚举，必须按 Java `String.equals` 与其中一项大小写敏感、逐字相等，否则失败。转换器不 trim、排序或改写元数据成员。

`ColumnDefinition` 已保证 STRING/ENUM 的 `length` 非 null；转换器直接消费该稳定不变量，不复制元数据构造校验。

### 日期与月份

DATE/MONTH 只接受 `String`。两者先执行通用 `trim()`；空结果返回 null。

- `DATE` 先完整匹配 `[0-9]{8}`，再使用 Locale.ROOT、`uuuuMMdd` 和 `ResolverStyle.STRICT` 解析，成功返回 `LocalDate`。ASCII 门禁必须拒绝分隔符、缩短/扩展年份、负年份和非 ASCII 数字；严格解析必须拒绝无效月日并接受合法闰日。
- `MONTH` 先完整匹配 `[0-9]{6}`，再使用 Locale.ROOT、`uuuuMM` 和 `ResolverStyle.STRICT` 解析为 `YearMonth` 仅作验证，成功返回 trim 后的原六位 `String`。不得返回 `YearMonth`，因为任务卡冻结的公开返回集合不包含该类型。

转换器不接受已经构造的 `LocalDate`/`YearMonth`，也不使用系统时区、当前日期或宽松回退格式。

### 整数与精确数值

`LONG` 接受以下精确来源：

- trim 后完整匹配 `[+-]?[0-9]+` 的整数文本，以 `BigInteger.longValueExact()` 转换；
- `Byte`、`Short`、`Integer`、`Long`，以 `longValue()` 返回；
- `BigInteger`，以 `longValueExact()` 返回；
- scale 精确等于 0 的 `BigDecimal`，以 `longValueExact()` 返回。

其他 `Number`、带小数点/指数的文本、scale 非 0 的 BigDecimal、布尔值和任意其他对象均失败。数学上等于整数但以小数表示的 `1.0` 仍属于被禁止的小数输入；超出 Long 范围必须失败，不截断、不饱和。

`DECIMAL` 接受 trim 后可由 `new BigDecimal(String)` 精确解析的非空文本、`BigDecimal`、`BigInteger`、`Byte`、`Short`、`Integer` 和 `Long`。整数包装类型通过 `BigDecimal.valueOf(longValue())` 转换，BigInteger 通过精确构造器转换；BigDecimal 原值直接使用。`Float`、`Double`、其他 Number、布尔值和任意其他对象均失败，任何路径都不得先经过 `double`。

取得 BigDecimal 后依次执行：

```java
BigDecimal scaled = value.setScale(column.scale(), RoundingMode.UNNECESSARY);
if (scaled.precision() > column.precision()) {
    // fail
}
return scaled;
```

允许仅补充零而不改变数值的 scale 调整；需要舍弃非零小数位时失败。precision 在目标 scale 固化之后检查，使超出 `DECIMAL(p,s)` 总位数的整数部分可靠失败。成功必须返回 `scaled`，不得返回输入对象的非目标 scale 版本。

### 统一失败与安全边界

下列来源数据问题统一抛 `AdapterException(ErrorCode.ADAPTER_TYPE_INVALID, message)`：不支持的运行时类型、字符串超长、闭合枚举不匹配、日期/月格式或日历无效、整数格式/小数/溢出、十进制格式/舍入/precision 超限。

message 精确为：

```text
Invalid adapter value: api=<ApiName.value>, row=<rowIndex>, field=<ColumnDefinition.name>
```

例如：`Invalid adapter value: api=daily, row=0, field=trade_date`。所有失败分支委托同一个私有 helper 创建异常；不附带 cause，不传播 `DateTimeException`、`NumberFormatException` 或 `ArithmeticException` 的文本。消息只使用已经通过 M02 值对象/元数据校验的 API 名、非负行号和字段名，不包含原始值、来源类名、逻辑类型、长度/precision/scale、allowedValues 或内部路径。

### 直接输入与兼容性

- M02-T03 的 `LogicalType` 精确七值与 `ColumnDefinition` record 是分派、类型参数、长度、枚举闭集和字段安全名的唯一运行时来源；转换器不修改这些不可变公共类型。
- M02-T05 的 `AdapterException` 与 `ErrorCode.ADAPTER_TYPE_INVALID` 提供唯一失败类别；转换器遵守其仅安全摘要、无 cause 重载和不可重试语义。

两项直接输入职责互补：M02-T03 定义“转换成什么以及列参数是什么”，M02-T05 定义“转换失败如何跨模块表达”，不存在重叠或冲突。任务不直接消费具体 M03 数据集、M04 表或插件实现；具体 `ColumnDefinition` 实例由后续 M05-T05 经已验证目录传入。

## Files

- Create `data-plane/tensor-core/src/main/java/com/akkc/tensor/core/adapter/ValueConverter.java`：实现七类逻辑类型分派、严格转换、目标约束与统一安全失败。
- Create `data-plane/tensor-core/src/main/java/com/akkc/tensor/core/adapter/ConversionContext.java`：保存已校验 API 名和非负、从 0 开始的来源行下标。
- Create `data-plane/tensor-core/src/test/java/com/akkc/tensor/core/adapter/ValueConverterTest.java`：以真实 plugin-api 类型覆盖公开表面、批准输入矩阵、边界反例和安全消息。

实现提交只暂存上述三个 Java 文件，固定消息为 `feat(core): add strict dataset value conversion`。设计、交接、看板、POM、既有 Java、YAML、SQL 和生成的 `target` 不得混入实现提交。

## Tests

### 基线与可归因 RED

实施前运行：

```bash
mvn -Dmaven.repo.local=/private/tmp/tensor-m2 -f data-plane/pom.xml \
  -pl tensor-core -am test
```

预期 `tensor-plugin-api` 79 项、`tensor-core` 30 项，共 109/109，0 failure、0 error、0 skipped；父项目、plugin-api、core 三层 Enforcer 通过。已有 Mockito/JDK 动态 agent 安全运行时提示允许保留，Maven/编译不得增加既有 platform-encoding 之外的警告类别。该 reactor 基线必须在允许 Mockito/Byte Buddy 执行本地 JVM attach 的环境运行；attach 受限沙箱产生的十项 `MockMaker` 初始化错误是执行环境失败，不能作为代码 RED 或回归结论。

随后只完整创建 `ValueConverterTest.java`，不创建两个生产文件，再运行：

```bash
mvn -Dmaven.repo.local=/private/tmp/tensor-m2 -f data-plane/pom.xml \
  -pl tensor-core -am -Dtest=ValueConverterTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

命令必须只因 `ValueConverter` 和 `ConversionContext` 不存在而在 `tensor-core:testCompile` 非零；不得因依赖解析、上游未匹配测试、测试语法或既有失败形成伪 RED。

### 聚焦 GREEN

创建最小生产实现后重跑同一聚焦命令。`ValueConverterTest` 固定恰有 12 个普通 `@Test`，12/12 通过且不使用 Mockito：

1. 反射确认 `ValueConverter` final、唯一公开 convert 签名与无额外公开方法；确认 `ConversionContext` 精确两个 record components，并拒绝 null API/负行号；
2. source null 对 nullable true/false 均返回 null，null column/context 作为编程错误被拒绝；
3. STRING 执行 trim、空转 null、Unicode 码点长度门禁并从不截断；
4. TEXT 原样保留普通文本、首尾空格、空字符串和纯空白；
5. ENUM 对空 allowedValues 开放、对非空列表大小写敏感精确匹配，并复用 trim/null/长度规则；
6. DATE 接受 ASCII 八位真实日期与闰日并返回 LocalDate，拒绝分隔符、错误宽度、扩展/负年份、非 ASCII 数字和无效日历；
7. MONTH 接受 ASCII 六位真实月份并返回同一 String，拒绝分隔符、错误宽度、扩展/负年份、非 ASCII 数字和 00/13 月；
8. LONG 从整数文本、四种整数包装类、BigInteger 和 scale 0 BigDecimal 精确返回 Long；
9. LONG 拒绝小数/指数文本、scale 非 0 BigDecimal、溢出、Float/Double、布尔值和任意对象；
10. DECIMAL 从文本、BigDecimal、BigInteger 和四种整数包装类精确转换，证明没有二进制浮点路径并拒绝 Float/Double/其他对象；
11. DECIMAL 允许只补零的目标 scale，拒绝 `UNNECESSARY` 舍入与固化 scale 后的 precision 超限；
12. 对七类逻辑类型各触发至少一个失败，逐项断言同一精确 code/message、`retryable == false`，且消息不含原始敏感值、来源类名、目标类型或底层异常文本。

测试只使用 JUnit 5、AssertJ、真实 `ApiName`、`ColumnDefinition`、`LogicalType`、`AdapterException` 和字面期望。不得从生产 private helper 反向生成期望，不断言日志，不把 Token 或真实凭证作为测试值。

### 模块、静态与范围门禁

运行：

```bash
mvn -Dmaven.repo.local=/private/tmp/tensor-m2 -f data-plane/pom.xml \
  -pl tensor-core -am test
mvn -Dmaven.repo.local=/private/tmp/tensor-m2 -f data-plane/pom.xml \
  -pl tensor-core -am verify
```

两条命令均预期 `tensor-plugin-api` 79 项、既有 `tensor-core` 30 项、`ValueConverterTest` 12 项，共 121/121，0 failure、0 error、0 skipped；三层 Enforcer 通过。

运行：

```bash
rg -n 'org\.springframework|java\.sql|javax\.sql|tushare|RestClient|ServiceLoader|(?i:token|credential)|source\.toString|String\.valueOf\(source\)|getMessage\(\)|initCause' \
  data-plane/tensor-core/src/main/java/com/akkc/tensor/core/adapter
rg -n 'Float|Double|\.doubleValue\(\)|Double\.parseDouble|Float\.parseFloat' \
  data-plane/tensor-core/src/main/java/com/akkc/tensor/core/adapter
mvn -Dmaven.repo.local=/private/tmp/tensor-m2 -f data-plane/pom.xml \
  -pl tensor-core -am clean
git diff --quiet -- data-plane/pom.xml data-plane/tensor-app \
  data-plane/tensor-plugin-api data-plane/tensor-plugin-tushare
git status --short --untracked-files=all -- \
  data-plane/tensor-core/src/main/java/com/akkc/tensor/core/adapter \
  data-plane/tensor-core/src/test/java/com/akkc/tensor/core/adapter
git diff --check
```

两项扫描均预期无输出并退出 1；`clean` 退出 0；非目标 POM/app/plugin-api/plugin-tushare 无差异；提交前 scoped status 精确列出三个新 Java 文件且无 `target`；格式检查退出 0。提交后 `git show --stat --oneline HEAD` 必须显示固定消息和精确三文件范围，工作树干净。

## Acceptance

- `ValueConverter` 与 `ConversionContext` 的公开表面、无状态性和不变量与本设计精确一致，没有额外生产类型、重载或跨模块依赖；
- 七项 `LogicalType` 只产生任务卡批准的 `String`、`LocalDate`、`Long`、`BigDecimal` 或 null；null/blank、TEXT 保留、字符串码点长度及开放/闭合 ENUM 行为确定且不截断；
- DATE/MONTH 具有 ASCII 固定宽度和严格真实日历语义；LONG 禁止小数和溢出；DECIMAL 不经 double、按 `UNNECESSARY` 固化 scale 并拒绝 precision 超限；
- 所有批准运行时输入成功，Float/Double、布尔值、任意对象和各类非法边界统一失败，不发生隐式 `toString()`、猜测、取整或舍入；
- 所有来源转换失败均为 `ADAPTER_TYPE_INVALID`，精确摘要只含 API、从 0 开始行号和字段名，不含原始值、类型、底层异常、cause、Token 或内部路径；
- 严格 TDD 得到缺两个生产类型的可归因 RED 后 12/12 GREEN；模块 `test`/`verify` 121/121、三层 Enforcer、静态、范围、格式、清理和精确三文件提交门禁全部通过；
- 未修改 POM、plugin-api、YAML、SQL、既有类型或其他模块，未提前实现通用适配、不可空/业务键校验、下载、持久化、REST 或日志职责。

## Risks

- 当前 `DatasetStartupValidatorTest` 使用 Mockito inline mock maker；实施与最终 reactor 门禁必须在允许本地 JVM attach 的执行环境运行。2026-09-02 的同命令单变量复跑已确认：受限沙箱因 Byte Buddy attach 失败产生 10 errors，允许 attach 后立即恢复 plugin-api 79/79、core 30/30 和三层 Enforcer 全绿。
- M07-T02 必须在把上游 JSON 数值放入 `DownloadEnvelope.data()` 时保留小数为 BigDecimal；如果先生成 Float/Double，M05-T04 会按设计拒绝，不能在转换器中尝试恢复已经可能丢失的精度。
- M05-T05 必须在转换后校验 `nullable == false` 和业务键字段非 null，并把这类缺失归为 `ADAPTER_FIELD_MISSING`；若遗漏，数据库虽可在事务内拒绝不可空值，但会延迟并错误分类本应在适配层发现的问题。
- `trim()` 是本任务经项目所有者批准的短文本兼容规则；TEXT 刻意保留空字符串和纯空白，使其与 null 在后续查询/UI 中保持可区分。未来若要改变 Unicode 空白或 TEXT 规范化，必须先修订公开设计和相应迁移/展示合同。
