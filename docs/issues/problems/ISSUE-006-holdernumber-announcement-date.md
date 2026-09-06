# ISSUE-006：股东户数公告日期混入日期时间格式

## 当前阶段与授权

限定源字段兼容修复、回归、独立复审、构建与启动验证完成，待真实复验。按用户持续执行和“继续修复”的授权实施独立适配修复。M14-T05 已记录本轮真实失败并 BLOCKED，修复与明确产物接入成立后恢复。

## 已知事实

- 真实运行 6gn542ah：9 项通过，`stk_holdernumber` 的原样例失败，公开码 `ADAPTER_TYPE_INVALID`，完成事件 `failureStage=adapter`，requestId `841ad417-262b-4f40-a6ae-4154c536aac2`，37ms；失败计数 unavailable。具体真实字段和值未保存，不能把历史样本位置称为该次请求位置。
- 对仓库历史 `docs/data-template/stk_holdernumber.json` 仅在内存检查：149 行无重复键冲突，holder_num 为整数/null；来源第 12 行的 `ann_date` 是合法 `yyyy-MM-dd HH:mm:ss`，包含非零时间，无时区，其他日期为八位文本。不输出或另存真实值。
- M03-T08 明确该列为 DATE，数据库亦为 DATE；当前通用转换器按 M05-T04 只接受严格 `yyyyMMdd`，所以此源格式会触发适配拒绝。小数解码缺陷 ISSUE-005 已经真实复验通过，本问题不是其失败重试。

## 修复设计

在 Tushare 源协议边界 `TushareResponseValidator` 增加仅适用于 `stk_holdernumber.ann_date` 的规范化：严格完整匹配 19 字符 ASCII `yyyy-MM-dd HH:mm:ss`，使用 `uuuu-MM-dd HH:mm:ss`、Locale.ROOT、STRICT 校验真实日历和时分秒后，以其本地日历日期输出 `yyyyMMdd`。该字段已定义为公告日期，只映射日期部分；不做时区换算，不使用系统时区，不借用当前日期。原 8 位格式保持原样。

其他 API/列、null、空白与不匹配/非法日期时间原样传递，由既有适配器继续按原 `ADAPTER_*` 规则拒绝或处理。不能 substring 截断任意文本、宽松解析、丢弃行、更换样例或转 EMPTY。不修改通用 DATE/LONG/DECIMAL 规则、元数据/SQL、参数、业务键、错误合同、响应大小/重试/日志规则；不新增依赖或配置开关。

将此兼容限定在源边界，避免为一个上游差异放宽所有数据源的通用日期规则。所有输入行数、顺序、其他列和原对象均保留；只在需要规范化时复制该行。

## 实施与验收计划

1. 合成客户端到真实 adapter 测试先 RED：原八位日期、非零时分秒与闰日合法时间应映射为对应 LocalDate，holder_num 的整数/null 保留；无效日期/时间/时区后缀/其他列及其他 API 的相同文本仍失败。
2. 最小实现后 GREEN，运行受影响客户端、安全校验与通用日期/适配测试，不发真实上游请求；独立代码复审。
3. 独立源码快照构建新验收包，既有打包合同通过，原包和 ISSUE-005 包保留；比较展开内容并固定新路径/hash。记录 M14-T05 输入修订，准备新空库和一次性启动器再交用户已有 Token 终端复跑。
4. 新轮仍完整 40/48/80、fixture 2/3、固定 9 项排除。只有真实 stk_holdernumber 通过才关闭此问题；完整子集通过才 PAUSED，否则记录新的 BLOCKED。不以本地或历史样本代替真实结果。

## 2026-09-06 本地验证

- RED：`TushareAnnouncementDateAdaptationTest` 在未修复代码上 8 项中 7 通过、1 error；合法带时分秒样例于合成 `row=1, field=ann_date` 被拒绝，Maven exit 1。其他非法日期时间反例按原规则拒绝。
- GREEN：仅在 `TushareResponseValidator` 增加固定 API/列规范化；9 类相关测试共 94 通过、0 失败/错误/跳过、exit 0。命令如下，全部合成响应，无真实上游访问。

```sh
mvn -o -f data-plane/pom.xml -pl tensor-app -am \
  '-Dtest=TushareProClientTest,TushareRestClientFactoryTest,TushareErrorClassifierTest,TushareProPluginTest,ValueConverterTest,GenericDatasetAdapterTest,TushareDecimalAdaptationTest,TushareAnnouncementDateAdaptationTest,GlobalExceptionHandlerTest' \
  -Dsurefire.failIfNoSpecifiedTests=false -Dskip.installnodenpm=true -Dskip.npm=true test
```

- 独立代码复审无 Critical/Important；1 Minor 指出其他 API 测试应使用同名 `ann_date`。已改为 `stk_rewards` 的真实元数据/完整7字段合成响应，防止误删 API 限定。加强后的客户端12项、日期适配8项和小数适配1项随下述快照构建重新通过。生产代码未因该测试调整而改变。
- 快照 `/private/tmp/tensor-issue-006-build.2rctzavi` 基于 `adbd2fe` 加本次三份 Java 变更，全部模块从源码编译，静态前端逐字节复用原包。下述命令 exit 0，21 项相关测试与既有 7 唯一打包合同通过（两个 failsafe executions 各执行7项）。未运行工作区 clean，未改已有包或依赖。

```sh
# 独立快照根目录
mvn -o -f data-plane/pom.xml -Pacceptance \
  '-Dtest=TushareProClientTest,TushareDecimalAdaptationTest,TushareAnnouncementDateAdaptationTest' \
  -Dsurefire.failIfNoSpecifiedTests=false \
  '-Dit.test=PackagedJarContractTest,AcceptancePackagedJarContractTest' \
  -Dfailsafe.failIfNoSpecifiedTests=false -Dskip.installnodenpm=true -Dskip.npm=true verify
```

- 新包路径：`/private/tmp/tensor-issue-006-build.2rctzavi/data-plane/tensor-app/target/acceptance/tensor-app-1.0-SNAPSHOT-acceptance.jar`，SHA `f2fc35c933e69da5e85690fbabb13d691178538cd6ffb3b94284dfc95b10db89`。与 ISSUE-005 包展开均364文件、无增删，仅 `TushareResponseValidator.class` 内容改变；其他类/资源/数据库迁移保持一致。原 `a698...` 和 ISSUE-005 `7f794...` 两包保留。
- 历史样本整批验证：临时 Java 探针从两包分别解出依赖，执行各自真实 validator 与通用 adapter；仅在内存读取仓库已有149行，不生成数据副本、不打印值。旧包在 `row=12, ann_date` 确切复现拒绝，新包149行全部适配成功，来源行对象保持原值；无上游请求，精确探针目录已删除。这仍不代表新真实轮已通过。
- Live spec 接入只更新固定 JAR hash，设计记录新包优先于历史引用；语法与40项发现通过，40/48/80及9项排除不变。真实证据 `241813c` 全文 SHA 仍为 `699132b0e4373d9d74300f6b5b64b22a0b9c593601dd54b66feca428d9136afd`，未改写。
- 定点复审确认上述 Minor 已关闭，无新增问题；独立实算包 hash 和364文件比较与设计/spec一致。
- 新包合成 Token、仅 health 诊断（控制目录 `0xdt5neo`）：exit 0、health就绪、6成功迁移/50业务表全空；无秘密/包络扫描触发，JVM停止、终检扫描及清理通过，自有DB容器/卷/私密材料已删除。此目录已用完，不能供真实验收复用。

- 最终启动器接入复审通过：新控制目录 `/private/tmp/tensor-m14-t05-control.1gpnb4ru`，launcher SHA `1f2345efddcedb7d5dbdb38005af65ac492e8a0f2ff92a4163d20736f2211b4b`；唯一改动为固定包路径。8项公开配置、四hash、40/48/9/2000ms、0700/0600和未使用状态均核对通过，无审查发现。真实新轮仍待用户已有Token终端执行。
