# ISSUE-012：查询接口未拒绝任意字段参数

## 当前状态

新增，未解决。M14-T07 发现查询入口未按批准合同拒绝六类额外参数；未修改生产实现。

## 实际证据

对 `/api/v1/data-sources/tushare_pro/datasets/stock_company/records` 分别附加以下参数，[安全证据](../../verification/M14-T07-security.md) S06 六次均为 HTTP 200：

| 参数 | 固定测试值 |
| --- | --- |
| table | other_table |
| column | introduction |
| columns | * |
| sort | ts_code DESC |
| orderBy | ts_code |
| sql | SELECT 1 |

每次业务行及 stub 调用数均未变化。证据证明“未拒绝输入”，不证明提交的 SQL 被执行。

## 关闭条件

[批准设计](../../task-designs/M14-T07-design.md) S06 明确要求全部返回 400 / `PARAM_INVALID`，忽略参数后返回 200 也不满足要求。按问题流程确认输入边界的修复设计，保留固定 SQL 标识符和值绑定、安全错误、合法查询行为，并以新冻结生产包取得六项通过证据。
