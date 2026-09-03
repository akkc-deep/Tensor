# M07-T03 Tushare 上游错误分类——任务设计

任务编号：`M07-T03`
对应任务：[M07-T03](../superpowers/plans/tensor-modules/M07-tushare-plugin.md#task-m07-t03-错误分类25h)
实施产物：无状态 Tushare 错误分类器、M07-T02 客户端/validator 分类接缝和领域异常回归测试

## Goal

在 Java 21 `tensor-plugin-tushare` 模块中把 M07-T02 已安全拒绝但尚未分类的 HTTP、Tushare 非零业务码、网络、响应超时、JSON 和结构失败转换为 M02 `SourceException`。每个异常只携带既有七项来源 `ErrorCode` 之一及固定安全摘要，`retryable()` 完全沿用 `ErrorCode` 的既定真值；调用方因此能区分鉴权、权限、限流、服务不可用、网络、超时和无效载荷，同时不会接触 Token、原始响应、上游消息、URI 或底层 cause。

本任务保持 M07-T02 的精确请求、响应限长、严格 JSON、有序字段/行校验和成功/合法空包络行为，只在 status、业务 `code/msg` 或 transport/parse failure 仍是方法局部值的位置立即完成分类，不产生失败包络或半包络。

## Scope

包含：

- 创建包内可见、无状态的 final `TushareErrorClassifier`，以四个 package-private static 操作分别分类 HTTP status、非零业务失败消息、传输 cause 和无效响应载荷；
- HTTP 401、403、429、5xx 分别映射 `SOURCE_AUTH_FAILED`、`SOURCE_PERMISSION_DENIED`、`SOURCE_RATE_LIMITED`、`SOURCE_UNAVAILABLE`；其余非 2xx 使用固定安全的 `SOURCE_UNAVAILABLE`，不得把 status 保存到异常；
- 按用户于 2026-09-03 批准的业务消息规则，以大小写无关的 `token`、以及 `认证`、`用户不存在` 识别鉴权失败；以 `每分钟`、`每小时`、`频率`、`限流` 识别限流；以 `权限`、`积分` 识别权限不足；未知或 null 消息对应的其他非零业务码映射 `SOURCE_PAYLOAD_INVALID`；
- 固定业务识别顺序为鉴权、限流、权限、未知；其中限流必须先于权限，使同时含频率/限流和权限/积分词的消息分类为 `SOURCE_RATE_LIMITED`；
- 把 DNS、拒绝连接、无路由和 JDK 建连超时分类为 `SOURCE_NETWORK_ERROR`，把 socket/JDK 非建连读取超时分类为 `SOURCE_TIMEOUT`；读取超时识别先扫描完整 cause chain，再检查一般网络 cause，其他 transport/read I/O 失败安全回退为网络错误；
- 修改 M07-T02 `TushareProClient`，在 exchange callback 的非 2xx 分支、响应读取/限长/JSON 解析分支和外层 `ResourceAccessException` 分支调用分类器；
- 修改 M07-T02 `TushareResponseValidator`，使缺 code、未知业务失败、缺 data/fields/items、字段和行结构失败均抛对应安全 `SourceException`，其中非零业务码只将局部 `msg` 交给分类器；
- 创建八项普通 classifier 单元测试，并把既有十项 `TushareProClientTest` 的失败断言更新为 `SourceException` code、固定消息、retryable 和脱敏边界，同时完整保留 M07-T02 请求/成功/空结果/严格解析/限长/字段顺序/行宽回归；
- 执行严格 TDD、三类受控 mutation、聚焦与 reactor 回归、Enforcer、秘密/静态/范围/格式/清理和精确五文件提交门禁。

排除：

- 不修改 POM、M02 plugin-api、M03 metadata/YAML、M07-T01 properties/factory/test、三个协议 DTO、其他模块、合同或设计源；不增加依赖、资源或配置；
- 不实现 M07-T04 的 `TushareProPlugin`、Spring Bean 装配、readiness、描述符或 49 API 注册/委托；
- 不增加自动重试、退避、熔断、异步、指标、日志、MDC、失败 `DownloadEnvelope`、REST/HTTP 对外映射或前端文案；
- 不识别未经批准的 Tushare 数字业务码、正则/模糊同义词或本地化变体，不从 raw body 二次提取错误，也不把未知业务失败误判为成功/空结果；
- 不把 HTTP status、业务 code/msg、response body、Token、请求 JSON、header/cookie、URI、字段/行实际值、Jackson/网络消息、cause、stack/suppressed 数据保存到 `SourceException`、公共 DTO、包络或日志；
- 不改变调用方输入 null 的 `Objects.requireNonNull` 编程错误，不把请求序列化失败伪装成上游响应失败；不改变 M07-T02 验证顺序、字段完整同序语义、响应大小边界或零重试约束。

## Approach

### 最小分类器表面与固定安全摘要

在 `com.akkc.tensor.plugin.tushare.client` 中创建以下包内类型，不新增 public API：

```java
final class TushareErrorClassifier {
    static SourceException classifyHttp(int statusCode);
    static SourceException classifyBusiness(String message);
    static SourceException classifyTransport(Throwable failure);
    static SourceException invalidPayload();
}
```

类型有 private 无参构造器、无实例字段、无可变静态状态、无日志方法；四个方法均返回新 `SourceException`，不得返回裸 `ErrorCode` 加独立 retryable，也不得缓存异常。唯一内部构造 helper 按错误码选择以下固定英文摘要：

| ErrorCode | 固定 message | retryable（来自 M02） |
|---|---|---|
| `SOURCE_AUTH_FAILED` | `Tushare credentials were rejected` | `false` |
| `SOURCE_PERMISSION_DENIED` | `Tushare API permission is unavailable` | `false` |
| `SOURCE_RATE_LIMITED` | `Tushare rate limit was reached` | `true` |
| `SOURCE_UNAVAILABLE` | `Tushare service is unavailable` | `true` |
| `SOURCE_NETWORK_ERROR` | `Tushare could not be reached` | `true` |
| `SOURCE_TIMEOUT` | `Tushare response timed out` | `true` |
| `SOURCE_PAYLOAD_INVALID` | `Tushare returned an invalid payload` | `true` |

不得拼接或格式化任何入参。异常必须保持 `getCause() == null` 且无 suppressed exception；分类器不得调用 transport failure 的 `getMessage()`、`toString()` 或 stack API，也不得对传入 Throwable 调用 `initCause`/`addSuppressed`。

`classifyHttp` 仅消费原始整数值：401、403、429 精确匹配，500～599 匹配 unavailable，其余调用点保证为非 2xx，统一安全回退 unavailable。该回退避免为未获批准的 3xx/其他 4xx 猜测鉴权或 payload 语义，也保持来源拒绝为可重试的通用上游状态。

### 业务消息识别

`TushareResponseValidator` 先保持原顺序检查 `code == null`，缺失时直接 `invalidPayload()`；`code == 0` 才继续 data/字段/行校验。`code != 0` 时，code 只用于当前局部分支判断，不传入异常；原始 `msg` 只作为 `classifyBusiness` 的方法参数，分类完成后不保存。

`classifyBusiness` 对非 null 消息只以 `toLowerCase(Locale.ROOT)` 产生方法局部副本，并按以下顺序执行普通 substring `contains`：

1. 含 `token`、`认证` 或 `用户不存在`：`SOURCE_AUTH_FAILED`；
2. 含 `每分钟`、`每小时`、`频率` 或 `限流`：`SOURCE_RATE_LIMITED`；
3. 含 `权限` 或 `积分`：`SOURCE_PERMISSION_DENIED`；
4. null、空白或不含上述词：`SOURCE_PAYLOAD_INVALID`。

鉴权位于首位；用户特别裁决的限流优先级只覆盖与权限词同时出现的情况。不得 trim/修改 DTO 内原始 `msg`，不得把 lowercase 副本、命中词或原消息写入异常。

### HTTP、传输和 payload 接缝

`TushareProClient.execute` 保持原请求构造和一次 `RestClient.exchange` 调用。callback 读取 `response.getStatusCode().value()`：非 2xx 立即 `throw classifyHttp(...)`，仍不读取错误 body。整个 exchange 仅捕获 Spring `ResourceAccessException`，把局部 failure 交给 `classifyTransport`；classifier 返回的新异常不带原异常 cause。不得捕获并重分类已经生成的 `SourceException`。

`classifyTransport` 仅沿 `Throwable.getCause()` 有界遍历现有 cause chain，不读取每层消息。为避免异常自引用导致循环，最多检查 16 层；处理顺序固定为：

1. 第一遍寻找 `SocketTimeoutException`，或不是 `HttpConnectTimeoutException` 的 `HttpTimeoutException`，命中即 `SOURCE_TIMEOUT`；
2. 第二遍寻找 `UnknownHostException`、`ConnectException`、`NoRouteToHostException` 或 `HttpConnectTimeoutException`，命中即 `SOURCE_NETWORK_ERROR`；
3. 未命中已知类型的 transport/read failure 回退 `SOURCE_NETWORK_ERROR`。

M07-T01 使用 JDK HTTP client；其 `HttpConnectTimeoutException` 虽继承 `HttpTimeoutException`，仍按 TRD 的 connect failure 归为 network。先完整扫描读取 timeout 再扫描一般 network，确保嵌套 cause 同时包含一般网络异常与读取超时时，读取超时不会被提前吞掉。

现有有界 `read` 的 `IOException` 改为调用 `classifyTransport`；body 超过 `maxResponseBytes`、空/畸形/重复键/错误标量/尾随根值和所有响应结构失败统一调用 `invalidPayload()`。请求 `encode` 的固定 `IllegalStateException` 保留，因为它发生在发送前且不属于上游/传输分类。合法 2xx 成功和空结果继续走原 validator/`DownloadEnvelope`，不得改变任何 DTO 或响应内容。

### 直接依赖与裁决比较

- M07-T02 的设计 `docs/task-designs/M07-T02-design.md`、实现提交 `3244d92` 和看板完成证据冻结唯一公开 client、协议 DTO、HTTP-before-body、实际响应限长、严格 JSON、有序业务/结构校验、成功包络及零泄漏接缝；当前 client、validator 和 client test 相对实现提交无差异。该设计明确授权 M07-T03 修改 client/validator，在 status/code/msg/cause 仍为局部值时立即分类。
- M02-T05 的 `ErrorCode`/`SourceException` 冻结七项 source code、retryable 真值、无 cause 构造器和安全消息边界；当前实现只接受 `(ErrorCode,String)`，retryable 只由 code 派生。本任务只消费这些类型，不修改或复制错误矩阵。
- TRD 7.4 冻结 Token/权限积分/限流服务/网络/响应超时/JSON 结构到七项 source code 的语义；错误码目录冻结短、可行动、无原始上游内容的摘要边界。用户补充批准精确中文/英文业务消息词和限流高于权限的顺序。

这些输入无冲突：M07-T02 提供原始失败仍局部可见的唯一接缝，M02 提供公共异常/重试合同，TRD 与用户裁决补齐分类规则。本任务只在该接缝把不可信信息归约为固定 code/message，不修改传输、解析、包络或后继插件职责。

## Files

- Create `data-plane/tensor-plugin-tushare/src/main/java/com/akkc/tensor/plugin/tushare/client/TushareErrorClassifier.java`：包内无状态 HTTP、业务消息、transport 和 payload 分类，唯一固定安全 `SourceException` 构造点。
- Create `data-plane/tensor-plugin-tushare/src/test/java/com/akkc/tensor/plugin/tushare/client/TushareErrorClassifierTest.java`：八项普通单元测试，覆盖精确映射、识别顺序、cause 顺序、固定摘要、retryable 和零泄漏表面。
- Modify `data-plane/tensor-plugin-tushare/src/main/java/com/akkc/tensor/plugin/tushare/client/TushareProClient.java`：在现有 HTTP/read/decode/transport 局部接缝调用 classifier，保持请求、限长和 JSON 行为。
- Modify `data-plane/tensor-plugin-tushare/src/main/java/com/akkc/tensor/plugin/tushare/client/TushareResponseValidator.java`：把业务和结构固定失败替换为分类后的安全 `SourceException`，保持原校验顺序和成功包络。
- Modify `data-plane/tensor-plugin-tushare/src/test/java/com/akkc/tensor/plugin/tushare/client/TushareProClientTest.java`：保留十项既有回归并把失败断言升级为 code/message/retryable/cause/秘密边界。

实现提交只暂存上述五个文件，提交消息固定为 `feat(tushare): classify upstream failures`。设计、交接、看板、POM、协议 DTO、M07-T01/metadata/YAML、其他测试、生成的 `target` 和其他模块不得混入实现提交。

## Tests

### 基线、RED 与 GREEN

实现前运行：

```bash
mvn -Dmaven.repo.local=/private/tmp/tensor-m2 -f data-plane/pom.xml \
  -pl tensor-plugin-tushare -am test
```

预期退出 0；plugin-api 79 项、tensor-plugin-tushare 77 项，共 156/156，0 failure、0 error、0 skipped，三层 Enforcer 通过且只有既有 platform-encoding 警告类别。

先完整创建 `TushareErrorClassifierTest.java`，并把 `TushareProClientTest.java` 的既有失败断言完整改成设计规定的 `SourceException` 断言；不创建 classifier、不修改两个生产文件，运行：

```bash
mvn -Dmaven.repo.local=/private/tmp/tensor-m2 -f data-plane/pom.xml \
  -pl tensor-plugin-tushare -am \
  -Dtest=TushareErrorClassifierTest,TushareProClientTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

预期在 `tensor-plugin-tushare:testCompile` 只因 `TushareErrorClassifier` 缺失而退出非 0；不得因测试语法、依赖解析、WireMock、真实网络或环境错误失败，作为可归因 RED。

创建最小 classifier 并接入 client/validator 后重跑同一命令，预期 `TushareErrorClassifierTest` 恰有八个普通 `@Test`、`TushareProClientTest` 仍恰有十个普通 `@Test`，18/18 通过。classifier 八项分别覆盖：

1. 类型为 package-private final、private constructor、无状态，只有四个 package-private static 分类操作且无 public API；
2. HTTP 401/403/429/500/503 和一个其余非 2xx 分别得到 auth/permission/rate/unavailable/unavailable，固定摘要、retryable 与 M02 一致；
3. `token` 大小写变体、`认证`、`用户不存在` 分别得到 auth，混合 auth/rate/permission 仍按鉴权优先；
4. `每分钟`、`每小时`、`频率`、`限流` 分别得到 rate，含 `频率` 与 `权限`/`积分` 的消息必须仍为 rate；
5. `权限`、`积分` 分别得到 permission，null/空白/未知非零业务消息得到 payload invalid；
6. `UnknownHostException`、`ConnectException`、`NoRouteToHostException` 和 `HttpConnectTimeoutException` cause chain 得到 network；
7. `SocketTimeoutException` 和非 connect `HttpTimeoutException` 得到 timeout；当一般 network cause 外层嵌套读取 timeout 时仍以 timeout 胜出；
8. 七类结果逐项具有精确固定 message/retryable、null cause、空 suppressed，任何含 `m07-t03-secret-sentinel`、伪 Token/URI/body 的业务/transport 输入均不进入异常字符串或字段；`invalidPayload()` 固定得到 payload invalid。

既有 client 十项继续验证精确请求、成功、合法空、HTTP-before-body、严格 JSON、business-before-data、结构顺序、字段集合/顺序、行宽/null cell 和实际大小上限。所有旧固定 `IllegalStateException` 响应失败断言改为设计表中的固定 `SourceException`：503 为 unavailable；无效/超限 JSON、缺 code、未知业务消息和所有 data/field/row 结构问题为 payload invalid。断言同时检查 code、message、retryable、null cause、无 suppressed、无 raw body/msg/code/Token/URI，且 HTTP/业务/结构失败均不返回包络或重试。

### Mutation、回归与门禁

受控 mutation A：临时把 permission 检查移到 rate 前，重跑 classifier 第 4 项，预期同时含 `频率` 与 `权限` 的断言失败；恢复后 18/18。受控 mutation B：临时先返回一般 network cause 或把 read timeout 映射为 network，重跑第 7 项，预期嵌套 timeout 优先级断言失败；恢复后 18/18。受控 mutation C：临时把业务 msg 拼接到异常 message 或以 `initCause` 保存 transport failure，重跑第 8 项和对应 client 测试；预期脱敏/cause 断言失败，完整 Maven 输出仍不得出现 sentinel。mutation 不提交。

运行：

```bash
mvn -Dmaven.repo.local=/private/tmp/tensor-m2 -f data-plane/pom.xml \
  -pl tensor-plugin-tushare -am test
mvn -Dmaven.repo.local=/private/tmp/tensor-m2 -f data-plane/pom.xml \
  -pl tensor-plugin-tushare -am verify
```

两条命令均预期退出 0；plugin-api 保持 79 项，tushare 从 77 增至 85 项，共 164/164，0 failure、0 error、0 skipped，父项目/plugin-api/tushare 三层 Enforcer 通过且无新增警告类别。

运行静态、秘密、范围、格式和清理门禁：

```bash
rg -n 'Logger|MDC|System\.out|System\.err|Retry|retryWhen|initCause|addSuppressed|getMessage\(\)|printStackTrace|Authorization|Cookie' \
  data-plane/tensor-plugin-tushare/src/main/java/com/akkc/tensor/plugin/tushare/client/TushareErrorClassifier.java \
  data-plane/tensor-plugin-tushare/src/main/java/com/akkc/tensor/plugin/tushare/client/TushareProClient.java \
  data-plane/tensor-plugin-tushare/src/main/java/com/akkc/tensor/plugin/tushare/client/TushareResponseValidator.java
rg -n 'new SourceException' \
  data-plane/tensor-plugin-tushare/src/main/java/com/akkc/tensor/plugin/tushare/client
rg -n 'classifyHttp|classifyBusiness|classifyTransport|invalidPayload|ResourceAccessException|SOURCE_AUTH_FAILED|SOURCE_PERMISSION_DENIED|SOURCE_RATE_LIMITED|SOURCE_UNAVAILABLE|SOURCE_NETWORK_ERROR|SOURCE_TIMEOUT|SOURCE_PAYLOAD_INVALID' \
  data-plane/tensor-plugin-tushare/src/main/java/com/akkc/tensor/plugin/tushare/client/TushareErrorClassifier.java \
  data-plane/tensor-plugin-tushare/src/main/java/com/akkc/tensor/plugin/tushare/client/TushareProClient.java \
  data-plane/tensor-plugin-tushare/src/main/java/com/akkc/tensor/plugin/tushare/client/TushareResponseValidator.java
git diff --quiet -- data-plane/pom.xml data-plane/tensor-plugin-tushare/pom.xml \
  data-plane/tensor-plugin-api data-plane/tensor-core data-plane/tensor-app data-plane/tensor-plugin-fixture \
  data-plane/tensor-plugin-tushare/src/main/java/com/akkc/tensor/plugin/tushare/config \
  data-plane/tensor-plugin-tushare/src/main/java/com/akkc/tensor/plugin/tushare/client/TushareRequest.java \
  data-plane/tensor-plugin-tushare/src/main/java/com/akkc/tensor/plugin/tushare/client/TushareResponse.java \
  data-plane/tensor-plugin-tushare/src/main/java/com/akkc/tensor/plugin/tushare/client/TushareData.java \
  data-plane/tensor-plugin-tushare/src/main/java/com/akkc/tensor/plugin/tushare/client/TushareRestClientFactory.java \
  data-plane/tensor-plugin-tushare/src/main/java/com/akkc/tensor/plugin/tushare/metadata \
  data-plane/tensor-plugin-tushare/src/main/resources \
  data-plane/tensor-plugin-tushare/src/test/java/com/akkc/tensor/plugin/tushare/client/TushareRestClientFactoryTest.java \
  data-plane/tensor-plugin-tushare/src/test/java/com/akkc/tensor/plugin/tushare/metadata \
  data-plane/tensor-plugin-tushare/src/test/resources
git diff --check
mvn -Dmaven.repo.local=/private/tmp/tensor-m2 -f data-plane/pom.xml \
  -pl tensor-plugin-tushare -am clean
```

第一项预期无输出并退出 1；第二项只允许 classifier 出现固定异常构造点，client/validator 不得直接构造或拼接异常；第三项显示全部授权分类接缝；受保护路径与格式退出 0；clean 成功。把最终聚焦测试与三类 mutation 的完整输出分别保存为 `/private/tmp/m07-t03-focused.log`、`/private/tmp/m07-t03-mutation-order.log`、`/private/tmp/m07-t03-mutation-cause.log`、`/private/tmp/m07-t03-mutation-secret.log`，然后运行：

```bash
rg -n 'm07-t03-secret-sentinel' \
  /private/tmp/m07-t03-focused.log \
  /private/tmp/m07-t03-mutation-order.log \
  /private/tmp/m07-t03-mutation-cause.log \
  /private/tmp/m07-t03-mutation-secret.log
rm -f /private/tmp/m07-t03-focused.log \
  /private/tmp/m07-t03-mutation-order.log \
  /private/tmp/m07-t03-mutation-cause.log \
  /private/tmp/m07-t03-mutation-secret.log
```

`rg` 应无输出且退出 1；四个固定临时文件随后删除。clean 后提交前 Git 状态只能列 Files 节五个 Java 文件。提交后 `git show --stat --oneline HEAD` 必须显示固定消息和精确五文件范围，工作树干净。

## Acceptance

- 唯一新增生产类型是 package-private final、无状态的 `TushareErrorClassifier`；没有新增 public API、依赖、配置、日志、重试或第二套错误/重试模型；
- HTTP 401/403/429/5xx 和其他非 2xx、业务 auth/rate/permission/unknown、DNS/connect/read timeout/其他 transport、invalid JSON/size/structure 均按设计得到精确 `SourceException.code()` 与 M02 派生 retryable；
- 业务识别严格使用批准词表与 auth→rate→permission→unknown 顺序，rate 在与 permission 词冲突时胜出；不识别未批准数字码或同义词；
- JDK connect timeout 保持 network，socket/非 connect JDK timeout 为 timeout；嵌套一般 network 与读取 timeout 时 timeout 胜出，其他 transport 安全回退 network；
- 七项异常 message 完全固定，cause 为 null、suppressed 为空；status/code/msg/body/Token/URI/header/字段值/底层异常和解析位置均不进入异常、包络、日志或测试失败输出；
- M07-T02 的十项请求、成功/空、HTTP-before-body、有界读取、严格 JSON、有序业务/结构/字段/行校验和零重试回归全部保留，任何失败不构造半包络；
- 严格 TDD 得到只缺 classifier 的可归因 RED 后，聚焦 18/18、三类 mutation、reactor `test`/`verify` 164/164、三层 Enforcer、秘密/静态/范围/格式/清理和精确五文件提交门禁全部得到预期结果；
- 未修改 POM、plugin-api、协议 DTO、M07-T01、metadata/YAML、合同或其他模块，未提前实现 M07-T04、自动重试、REST、适配、持久化或前端职责。

## Risks

- Tushare 没有在仓库合同中冻结稳定的数字业务错误码，本任务因此只使用用户批准的最小消息词表。上游改写文案会安全回退 `SOURCE_PAYLOAD_INVALID`，而不会泄露原消息或错误地返回成功；扩展词表必须另行裁决和测试。
- `HttpConnectTimeoutException` 是 `HttpTimeoutException` 子类，但 TRD 将 connect 与 read timeout 分开；实现必须保留显式排除和 cause 顺序测试，否则建连超时可能误归为 `SOURCE_TIMEOUT`。
- 其余非 2xx 缺少更细合同，设计保守归为 `SOURCE_UNAVAILABLE`；若未来需要区分请求无效、重定向或其他 4xx，必须新增领域裁决，不能拼接 status/body 到异常。
- Throwable cause chain 理论上可被构造为循环；16 层上限保证分类终止，未识别或超深链安全回退 network。不得为诊断把原 cause 放入 `SourceException`。
- WireMock 必须绑定本地回环端口；受限沙箱内若出现 `SocketException: Operation not permitted`，应在允许本地监听的测试环境重跑，不得删除集成断言或把权限失败误判为实现失败。
