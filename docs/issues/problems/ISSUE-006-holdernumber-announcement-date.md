# ISSUE-006：股东户数公告日期混入日期时间格式

## 当前阶段与授权

定位完成，按用户持续执行和“继续修复”的授权实施独立适配修复。M14-T05 先记录本轮真实失败并 BLOCKED，待本地修复与明确产物接入后恢复。

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
