# Next Task Handoff

## Handoff Type

next-task

## Task Link

- **Task board:** `docs/task-handoffs/tensor-range/tensor-range-task-board.md`
- **Completed task:** `RANGE-T02`，看板已记录 COMPLETED。
- **Next task:** `RANGE-T03`，按大于前项的最小未完成 Order=3 选择。
- **Design document:** `docs/task-designs/RANGE-T03-design.md`，详细设计已完成并链接在本项看板行。
- **Expected next status:** `READY`；写入并链接本交接后执行 `NOT_STARTED -> READY`，准备不表示合同实施已启动。

## Next Task

`RANGE-T03`：HTTP 合同、错误码与需求追踪。

目标：让前后端和验收使用一致的区间下载、当前失败任务及本轮结果合同。

范围：更新 `docs/contracts/openapi-v1.yaml`、`docs/contracts/error-codes.md`，创建 `docs/traceability/tensor-range-requirements.md`、`docs/contracts/verify_range_contract.py` 和 `docs/verification/RANGE-T03-contracts.md`。固定49项请求、下载策略投影、S／F／N／H及R／I／U、部分完成／未确认结果、失败列表／详情／execute、原始区间、分页及错误阶段语义。不修改运行代码、数据库、Dataset YAML或历史追踪，不增加取消或永久幂等协议。

验收：38项新区间与11项原条件均可校验，保留附加条件且拒绝旧日期／混用；未知数量、保存失败和提交未知有明确表达。列表默认20、可选20／50／100；原始区间与当前失败选择器独立，同日两股和不连续日期不折叠。29项AC逐项映射实现／验证任务和预期证据。设计规定的合同正反例及差异检查通过，报告明确运行接口尚未迁移。T03合同修改、校验脚本及验证报告尚未实施，不能引用本交接为合同测试已通过的证据。

## Dependencies

### RANGE-T01

- **Artifact:** `docs/research/RANGE-T01-source-capabilities.md`、`docs/research/RANGE-T01-source-evidence.json`。
- **Decision:** 49项仍为19／15／1／3／11五类，38项迁移起止日期、11项保留原条件；40项仅DOCUMENTED_CANDIDATE，8项合法方式未确认，forecast同页冲突。49项恢复暂按有整体取全前提的REQUEST登记；Q合法请求、C整体完整性、R独立恢复分别需要证据。
- **Rationale:** 支持起止参数不证明当前条件可取全市场或按公告日期筛选；整批可取全也不证明可按股票独立恢复。REQUEST降低恢复粒度要求，但不能绕过整体取全。
- **Constraint:** 保留5项日期含义差异、fina_mainbz公告参数缺口及express／top10_holders／top10_floatholders股票条件缺口。不能换VIP接口、加未确认必填股票或扩大范围；ISSUE-008九项真实调用继续排除。
- **Usage:** 按JSON和当前YAML核对49项请求形状及附加条件，生成合同目标扩展表；将合法方式、截断／完整性和恢复边界映射到错误／结果合同。正向合同示例不宣称真实来源已支持；受控STOCK示例不启用生产独立恢复。
- **Readiness evidence:** 看板T01完成记录与报告已提供：49集合及分组、49份YAML摘要、9排除、5差异、报告行及链接Python校验PASS，JSON语法及两种git diff --check退出0，49份官方HTML摘要复核一致。未调用业务API、未运行功能测试；这些是研究输入可追溯证据。

### RANGE-T02

- **Artifact:** `docs/research/RANGE-T02-calendar-capabilities.md`、`docs/research/RANGE-T02-calendar-evidence.json`。
- **Decision:** 19项分类C-A=12、C-M=1、C-S=3、C-N=2、C-X=1；1项DOCUMENTED、16项UNCONFIRMED、2项CONFLICT。DOCUMENTED仅hsgt_top10静态适用关系，仍排除真实调用。全部必要日历本轮确认后取开盘并集；任一适用关系、覆盖或新鲜度未知时CALENDAR_UNCONFIRMED、零业务调用，首次无新任务、重试原项保留。
- **Rationale:** 本地枚举不证明trade_cal支持BSE；不同市场／互联互通方向不可互代；中证金融第21条只有条件等价，2024转融券暂停不等于无限期休市。两个原404接口不能凭名称补方向，monthly日期与margin_detail市场说明仍冲突。
- **Constraint:** C-M仅按原exchange_id选择必要市场；非交易日期不进入日历屏障，原生trade_cal保留休市行。只消费实际首次／失败范围，不从原始区间扩大重试；不以局部已知日历提前执行或删除失败项，不将周末过滤当作19项能力通过。
- **Usage:** 核对19项calendarProfile、策略公开说明、CALENDAR_UNCONFIRMED及全休市／未知结果示例；在29项AC追踪中保留来源证据、受控行为与真实验证的区别。
- **Readiness evidence:** 看板T02完成记录与报告已提供：40来源（35可读／5不可用），JSON及设计规定Python校验PASS；40原内容摘要、19报告行、字段／引用／链接、margin三分支、HKEX261工作日集合及monthly两个周末样例复核PASS；两种git diff --check退出0。19项liveStatus均NOT_RUN；没有业务API或功能验收通过记录。

输入比较：两项均要求先确认合法来源方式，再完整确认必要日历，取得完整响应后才处理；独立恢复缺依据回退REQUEST仍受整体完整性门槛约束。T02的monthly样例冲突补充了T01文档候选的限制，并未要求更换产品目标；合法请求与日历门槛分别表达。没有尚未解决的产品决策冲突；来源事实缺口按已确定拒绝规则进入合同和追踪，不作为已支持能力。

## Start Here

按顺序读取：

1. [完成的T03设计](../../task-designs/RANGE-T03-design.md)，路径 `docs/task-designs/RANGE-T03-design.md`；完整读取字段、例子、错误阶段、29项映射和Tests。
2. [权威看板](tensor-range-task-board.md) 的RANGE-T03行和详情，核对实际状态及本交接。
3. [PRD v1.4 §5—10](../../design/Tensor_区间下载_PRD_v1.0.md)，参数清单补读§3及附录。
4. [TRD v1.10 §4、§6.4、§8—12](../../design/Tensor_区间下载_TRD_v1.0.md)，选择器及原始日期补读§7.1。
5. [现有OpenAPI](../../contracts/openapi-v1.yaml)、[错误目录](../../contracts/error-codes.md)、[平台历史追踪索引](../../traceability/tensor-v1-requirements.md)；保留原请求外层、其他插件参数和只读查询合同。
6. [T01结论](../../research/RANGE-T01-source-capabilities.md)及[逐项证据](../../research/RANGE-T01-source-evidence.json)，核对本交接直接输入。
7. [T02结论](../../research/RANGE-T02-calendar-capabilities.md)及[逐项证据](../../research/RANGE-T02-calendar-evidence.json)，核对19项映射及拒绝边界。

取得本任务的明确启动请求并记录 `READY -> IN_PROGRESS` 后，第一步按设计在 `docs/contracts/openapi-v1.yaml` 增加RangeParams及三种带条件形状，为DownloadRequest添加Tushare的49项参数分支，并将daily示例迁移为两端均20260901；随后按设计顺序完成元数据、结果、错误、任务端点、示例、校验脚本和追踪。结构与行为已经固定，不需要重新创建设计。实施后运行设计中的合同检查并记录实际结果；完成T03以后才准备T04设计与交接。

## Risks

- 来源合法性、BSE日历、C-S等价、互联互通方向及monthly／margin_detail冲突尚有明确缺口；合同校验不消除这些缺口，不关闭ISSUE-008或任何功能AC。
- Schema不能自行证明公历差值、执行事实或事务原子性；文档语义检查与后续运行／数据库／真实来源验证各自保留边界。
- R／I／U只描述本轮确认小计；未开始、未保存及提交未知范围不保证可恢复，查询不得从原始区间或查不到任务推测成功。
- 工作区存在已暂存的其他文档；保留已有改动，新建文件加入Git，不顺带提交、发布或修改其他项目状态。
