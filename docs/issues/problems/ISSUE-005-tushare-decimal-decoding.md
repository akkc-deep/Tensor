# ISSUE-005：Tushare 小数解析导致适配失败

## 当前阶段

已解决。用户实际执行 6gn542ah 启动器后，`stock_company` 三组原样例全部成功，页面和独立 DB 均为 6294 行；本问题真实复验通过。完整 40 接口验收仍在另一问题处失败，不能因此声明 M14-T05 完成。

## 已知事实

- [当前交接](../../task-handoffs/tensor-v1/M14-T05-handoff.md)：真实请求返回 `ADAPTER_TYPE_INVALID`，失败阶段为 adapter；具体字段和值未保留。不能把下述本地诊断当作该次请求的字段证据。
- `TushareProClient` 的私有 JSON mapper 未启用 `USE_BIG_DECIMAL_FOR_FLOATS`，无类型的 `items` 小数会解析为 `Double`。
- [M05-T04 设计](../../task-designs/M05-T04-design.md) 明确要求客户端保留小数为 `BigDecimal`，转换器拒绝 `Float/Double`，禁止从已丢失精度的二进制浮点恢复。
- 仅在内存检查仓库历史 `stock_company` 模板类型：6294 行的 `reg_capital` 都是 JSON 小数；`employees` 为整数或 null。未输出或另存业务值，也不以历史行数判断真实验收。

## 处理与关闭条件

按[设计与计划](../proposals/ISSUE-005-tushare-decimal-decoding.md)执行。需证明合成回归先失败后通过、相关安全/转换测试通过，构建独立修复产物且保留原产物。真实 `stock_company` 与完整 2000 档矩阵须使用用户已有 Token 终端及全新空库另跑；在此之前只记录本地修复通过，不将上次真实失败改判通过，ISSUE 保留待真实复验状态。

## 2026-09-06 真实复验与关闭

实际证据提交 `241813c`，全文扫描 SHA `699132b0e4373d9d74300f6b5b64b22a0b9c593601dd54b66feca428d9136afd`。运行 Git `ecbf035`、修复包 SHA `7f794f3494109c27f134c04846e486bda3fe18beec3a88246b58fbcea719cef9`。stock_company 三次 SUCCESS 分别 source/insert 为 2457、3083、754，update 均 0，合计 6294；末查/可见来源与时间/独立 DB 对应一致。当前该接口用例通过，与上次失败属于不同运行，历史失败不改写。

全轮耗时 58 秒（spec 阶段约 55 秒），9 passed / 1 failed / 30 did not run；新失败是 `stk_holdernumber` 的 `ADAPTER_TYPE_INVALID`，转入 ISSUE-006。fixture 2 POST/3 查询通过，38 个业务 requestId 与完成事件逐一唯一；扫描与所有自有资源清理通过。本问题关闭只证明小数解码及 stock_company 修复成立，不覆盖新错误或原 49 目标。

## 2026-09-06 本地验证结果

- RED：客户端合成精度测试 1 失败，发现小数被解析为 Double 且损失十进制精度；单独 app 合成链路测试 1 错误，安全异常定位为 `api=stock_company, row=0, field=reg_capital`。这只是合成复现位置。首次受限环境无法绑定 WireMock 端口的启动失败不计 RED，开放本地测试端口后才取得上述结果。
- GREEN：唯一生产改动启用 `USE_BIG_DECIMAL_FOR_FLOATS`。下列命令 exit 0，8 类共 85 测试通过、0 失败/错误/跳过。保留严格 LONG/DECIMAL、原响应校验、错误映射与非重试行为；异常处理测试产生其预期的错误日志。

```sh
mvn -o -f data-plane/pom.xml -pl tensor-app -am \
  '-Dtest=TushareProClientTest,TushareRestClientFactoryTest,TushareErrorClassifierTest,TushareProPluginTest,ValueConverterTest,GenericDatasetAdapterTest,TushareDecimalAdaptationTest,GlobalExceptionHandlerTest' \
  -Dsurefire.failIfNoSpecifiedTests=false -Dskip.installnodenpm=true -Dskip.npm=true test
```

- 独立只读复审：`review_decimal_fix` 对代码、测试、设计及证据边界无 Critical / Important / Minor 发现，可继续产物验证。
- 构建快照 `/private/tmp/tensor-issue-005-build.kibqgbn5`：基线 `13f3cbf` 加本次三份 Java 变更；全部模块从源码重编译，前端静态文件从原验收包逐字节复用，未运行或修改工作区前端依赖。既有 acceptance profile 构建，命令如下 exit 0。合成适配测试 1 通过；两类打包合同共 7 个唯一测试通过（既有两个 failsafe execution 各执行一次，即 14 次执行），未把它计作 14 个不同测试。

```sh
# 上述独立快照根目录
mvn -o -f data-plane/pom.xml -Pacceptance \
  -Dtest=TushareDecimalAdaptationTest -Dsurefire.failIfNoSpecifiedTests=false \
  '-Dit.test=PackagedJarContractTest,AcceptancePackagedJarContractTest' \
  -Dfailsafe.failIfNoSpecifiedTests=false -Dskip.installnodenpm=true -Dskip.npm=true verify
```

- 新验收包：`/private/tmp/tensor-issue-005-build.kibqgbn5/data-plane/tensor-app/target/acceptance/tensor-app-1.0-SNAPSHOT-acceptance.jar`，SHA-256 `7f794f3494109c27f134c04846e486bda3fe18beec3a88246b58fbcea719cef9`。与原包展开比较无条目增删；四个内部 Tensor JAR 因重建归档字节不同，但展开后仅 `TushareProClient.class` 改变，其余内容相同。原验收包 SHA 仍为 `a69874afa6ce783d4ef4e16a678ddb0ff457f2948b68f509a8e4a2c00440bcac`。
- 新包启动实测：独立诊断控制目录 `/private/tmp/tensor-m14-t05-control.j0uo9psx`，合成 Token 仅经环境注入，只访问 health；exit 0、health ready、6 成功迁移、50 业务表全空、无秘密/包络扫描触发。JVM 停止、CLI 后扫描与清理通过，容器/匿名卷及私密 DB 文件已删除。该环境已用完，不供真实复跑。
- 接入只替换 live spec 的固定 JAR hash，并在 M14-T05 设计中明确修复包路径及历史规则覆盖关系。40/48/80、fixture 2/3、9 项排除、manifest 和完整安全流程保持原样。旧真实证据文档内容和已扫描 SHA 未改写。
- 接入复审无 Critical / Important / Minor：独立实算 spec/证据/JAR/manifest 四项 hash 与新控制目录 `6gn542ah` 的配置一致，新旧包展开均 364 个文件且仅客户端类不同，40/48/9 精确一致。启动器仅固定新 JAR 路径，目录 0700、脚本/配置 0600、未运行；完成状态迁移即可交用户一次启动。
- 接入验证：spec 语法、40 个 Chromium 用例发现通过；合成环境调用实际 `validatePreconditions` 确认旧 JAR 被新固定 hash 门禁拒绝，未启动 JVM/上游，临时探针已删除。新 JAR 的正向门禁已由上述 health-only 诊断实际通过。
