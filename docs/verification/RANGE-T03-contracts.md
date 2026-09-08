# RANGE-T03 合同验证

日期：2026-09-08。任务：[区间下载看板 RANGE-T03](../task-handoffs/tensor-range/tensor-range-task-board.md#range-t03)；实施依据：[完整设计](../task-designs/RANGE-T03-design.md)及[入口交接](../task-handoffs/tensor-range/RANGE-T03-handoff.md)。

本轮更新 [OpenAPI](../contracts/openapi-v1.yaml)、[错误目录](../contracts/error-codes.md)，交付 [离线校验脚本](../contracts/verify_range_contract.py)和 [29项增量追踪](../traceability/tensor-range-requirements.md)。合同校验通过，**运行接口尚未迁移**；没有修改 Java、Vue、Dataset YAML、数据库或运行配置。

## 验证命令与实际结果

仓库根执行 `python3 docs/contracts/verify_range_contract.py`，退出码 0：

```text
PASS OpenAPI structure/paths
PASS metadata download policy
PASS 49 targets and request migration
PASS all request/response examples and scopes
PASS result counters and uncertainty boundary
PASS retry task contract
PASS error catalog and unchanged query
PASS 29-row traceability
PASS mutation rejected: pageSize default 50
PASS mutation rejected: delete target
PASS mutation rejected: restore daily trade_date
PASS mutation rejected: allow UNCONFIRMED as 200
PASS summary: 49 targets (19/15/1/3/11), 38 migrated, 11 retained, 29 AC, 10 new errors, 3 new paths
```

脚本使用当前环境已有 PyYAML、jsonschema、Draft202012Validator 和 FormatChecker；只解析本地引用，不联网。四项变异在内存副本进行，每项都会使合同检查失败，随后保留原合同；不写回文件。脚本初次面对旧合同退出 1（缺少 retry operationIds），补齐目标合同后通过，未将“脚本能启动”当作合同覆盖证据。

额外独立核对：49份当前 YAML 的必填参数投影恰为30个纯区间、6个股票区间、1个 exchange_id 区间、1个 exchange 区间，以及原条件6个空对象／2个股票／各1个 hs_type、list_status、exchange；参数枚举未扩展。与本轮修改前快照比较，全部既有非下载路径及只读查询 schema 结构一致，精确数值仍为字符串；16个旧错误码 HTTP／retryable 元组保持，10个新增元组与设计逐项一致。29行 PRD 引用与 PRD §9.1 原文一致，实现／验证责任、预期证据和未执行状态与设计逐行一致。

## 验收观察面

| 范围 | 本轮已核对结果 | 证据边界 |
|---|---|---|
| 请求及元数据 | 49目标与T01精确一致，19日历类别与T02一致，9真实排除一致；38区间和11原条件均有正反例，保留股票与市场条件；公开策略和下载参数投影独立于原查询 | 目标输入及描述符合同，不代表来源合法性或账号可用 |
| 日期及选择器 | 严格公历、同日、逆序、31／32天、闰日、跨年、31天覆盖三月；STOCK／REQUEST与DATE／MONTH／RANGE／NONE；单日原生请求保存DATE再还原相等起止 | 形状用schema、日期关系用独立语义检查；运行绑定器由T05／T14验证 |
| 本轮结果 | 正常成功、合法空、全休市、2成功1失败、全失败、保存未确认、提交未知均有可验证例子；N未知为null，R／I／U为确认小计；未知结果不能作为HTTP200 | 不证明数据库提交、失败保存或真实执行事实 |
| 当前失败记录 | 列表默认20、可选20／50／100；详情原始1—10与失败3／7分开，部分后仅7、两股同日不折叠；原始区间四种状态、离线插件名称回退、空请求体execute、404／409已固定 | 仅定义当前记录查询和精确重试；不生成历史、未开始或未知失败记录 |
| 错误阶段 | 26码；执行前拒绝、执行中明确失败保存后继续、存储／提交未知停止分别表达；没有新增取消、自动重发或永久幂等协议 | 来源失败类别的HTTP值不代表提前终止下载循环 |
| 需求追踪 | 29项AC都有PRD／TRD／合同观察面、实现任务、验证任务及预期证据；T21统一收尾 | Result均为“未执行；T03仅合同校验”，没有将合同校验登记为功能AC通过 |

## 评审与交付检查

错误目录和需求追踪独立评审通过。完整合同／脚本评审发现的四处边界及随后发现的空值／缺失区别均已修正，两轮定向复审后规格与质量均通过，无遗留发现。已补齐：已知N可在UNCONFIRMED中为1或0；已有任务及数量的确认独立于最新原因更新状态；伪造RECORDED原始区间、显式null日期、同日股票折叠均被拒绝；S=0时R／I／U必须为0。最终脚本仍不使用跨变异的schema或元数据缓存，复核结果与上方输出一致。

`git diff --check`、`git diff --cached --check` 均退出0；新建脚本、追踪及本报告加入Git暂存。本轮保留原有暂存文档，未提交或发布工作区。

## 未验证范围

本轮未运行 Maven、前端、浏览器、MySQL 或业务 API 验证；未读取凭证。T01／T02 的来源合法性、整体取全、独立恢复和日历缺口继续保留，尤其 monthly／margin_detail 冲突、BSE、转融通和缺失来源页面。受控 STOCK 合同例子不启用生产独立恢复，49项仍按T01有整体取全前提的REQUEST登记。

49项合同、38项区间目标、49项恢复策略及具体账号实测范围分别报告。[ISSUE-008](../issues/problems/ISSUE-008-tushare-live-coverage-gap.md)九项仍“不依赖，未解决，用户后续单独处理”，不恢复真实调用，不改写旧验收结论。后继T04仅在T03完成后进行专属设计及READY准备，本轮不启动T04实现。
