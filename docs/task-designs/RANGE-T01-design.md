# RANGE-T01 请求、完整性与恢复能力核实设计

## Goal

完成 [区间下载看板](../task-handoffs/tensor-range/tensor-range-task-board.md) 的 `RANGE-T01`：将现有 49 份 Dataset YAML 与官方资料逐项对照，交付可供 T03／T04／T07／T20 消费的请求、完整性和恢复依据。研究完成不表示来源能力、账号权限或区间功能验收通过。

## Scope

- 固定 19 个交易日期、15 个公告日期、1 个月份、3 个原生范围和 11 个原条件接口；保留现有股票、市场和状态条件。
- 核实官方请求参数、必填条件、日期含义及两端包含性、全市场限制、分页／合法分段、取全判据、输出对象时间归属与单独重试方式。
- 单列 5 项日期语义差异、`fina_mainbz.ann_date` 及 `express/top10_holders/top10_floatholders` 缺少股票条件的问题。
- 不改生产代码、Dataset YAML、共享需求、HTTP 合同或既有真实验收；不读取凭证或调用业务数据 API。ISSUE-008 的 9 项继续不依赖、未解决，真实调用排除。
- 日历适用性由 T02 研究，来源实际筛选／取全／独立恢复由 T20 的专属设计确定真实验证范围。

## Approach

按看板顺序读取 PRD §3／§6／§9.2／§10／附录、TRD §4.4／§5.2／§13.2、既有官方调研、49 份 YAML 与 ISSUE-008。使用 `designing-task-contracts` 固定本研究合同；本任务执行既定来源研究，不新增产品行为。

1. 先保存本设计并回填看板 Design document，再以用户本轮“执行当前任务”的请求作为 `READY -> IN_PROGRESS` 证据；首项 Handoff 保持 None。
2. 复核同日官方调研留下的 `/tmp/tensor-tushare-batch-research-20260908/evidence.json`，逐个比对原 HTML 的 SHA-256。将实际消费的公开输入表、介绍摘录、相关输出字段及摘要保存在版本控制内，临时缓存不作为唯一交付。缓存缺失时只读重取原官方 URL；页面不存在保留原 URL 与缺口，不擅换接口。
3. 建立 49 条结构化记录：apiName、目标分类、YAML 路径及摘要、原参数及业务键、官方 URL／摘要／摘录、候选来源方式及条件、时间语义、包含性证据、分页状态、上限、完整性启用／拒绝条件、对象时间字段、独立重试候选／缺口、REQUEST 边界和真实调用排除标志。提取输入表按表头辨认，不把 `index_classify` 的行业清单误作参数表。
4. Markdown 报告逐项展示结论并解释共享门槛。公开声明、文档冲突、未核实、真实验证分开；每项都给出启用条件或明确拒绝原因。短响应／HTTP 200／逐日请求／REQUEST 回退均不能单独证明完整。未公开 limit/offset 不表示来源绝不支持，但不得据此发送。
5. 请求没有合法依据时拒绝执行；有候选方式而整体取全未确认时保持未确认。仅独立恢复依据不足时采用 REQUEST，仍要求整体取全。STOCK_TIME 必须同时证明对象、时间、整体及单元完整性和精确单独重试，不能从 ts_code 列推断。
6. 为 T20 列出具体待验证事实和比较方法，候选样例须由 T20 按权限、日期与数据选定；本研究不冒充真实调用。对 ISSUE-008 项只记录缺口，不能排入 T20 调用。
7. 校验并记录本任务完成证据；按看板 Order 选择 T02，完成其研究设计、回填设计路径，再写 `docs/task-handoffs/tensor-range/RANGE-T02-handoff.md` 并准备 READY，不开始 T02 研究。

## Files

| 文件 | 职责 |
|---|---|
| `docs/task-designs/RANGE-T01-design.md` | 本研究合同 |
| `docs/research/RANGE-T01-source-capabilities.md` | 49 项结论、门槛、差异专题、AC 对照和待实测清单 |
| `docs/research/RANGE-T01-source-evidence.json` | 逐项公开证据及本地合同快照；不含业务响应或账号数据 |
| `docs/task-handoffs/tensor-range/tensor-range-task-board.md` | 设计引用、启动／完成证据与后继准备 |
| `docs/task-designs/RANGE-T02-design.md` | 当前任务完成后编写的后继研究设计 |
| `docs/task-handoffs/tensor-range/RANGE-T02-handoff.md` | 后继设计完成并链接后的入口交接 |
| `docs/task-handoffs/README.md` | 更新已过时的“尚未启动执行”说明 |

## Tests

本任务只交付研究文档，不运行 Maven／前端功能测试，不新增测试框架。

在仓库根执行：

```sh
python3 -m json.tool docs/research/RANGE-T01-source-evidence.json > /dev/null
git diff --check
git diff --cached --check
```

另用 Python 标准库进行一次结构核对，完整可复现命令放在报告的验证节：JSON 的接口集合等于 YAML 和 PRD 附录集合；49 个唯一项、分类 19／15／1／3／11；49 份 YAML 摘要一致；所有记录包含来源、原参数、日期、取全和恢复门槛；9 个排除项精确相等；5 个语义差异不采用公告区间直接透传；Markdown 49 行与 JSON 一一对应。检查新增文档的相对链接及看板设计／交接引用。预期均无缺失、集合差异或空白错误。

人工逐项复核本报告与保存的参数表和原始摘录相符，特别检查官方表头、必填／二选一冲突、原生日期字段、股票与非股票维度、分页未确认、包含性未实测、REQUEST 不豁免取全。只有这些输出级验收成立才标 COMPLETED。

## Acceptance

- 49 项均有官方链接、本地条件、具体结论、缺口及启用／拒绝条件；分组和输入条件不变。
- 5 项日期差异、公告参数缺失和 3 项股票条件问题有单独结论；原生公开说明与实际筛选验证明确分开。
- AC-PRD-RANGE-01／03／14／18／19／25／29 的来源前提可从报告追溯；不声称这些功能 AC 已通过。
- 取全与独立恢复分别判断，真实待验证事实可交给 T20，9 项排除范围保持。
- 设计、报告、证据及后继设计／交接加入 Git；保留用户本轮开始前的暂存修改。

## Risks

公开页面缺失、说明矛盾、未公开分页结束规则和两端包含性属于允许交付的研究结论，必须保留明确不可启用／待验证条件；不能因此把来源标为已支持。官方快照为既有同日调研的重新核读，不能称为本轮业务实测。未来来源变化需重新核实，T20 选定范围的实测缺口也不能靠本任务登记关闭。
