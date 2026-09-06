# ISSUE-011：数据集非只读方法返回 500

## 当前状态

新增，未解决。M14-T07 的 12 个方法反例均未达到预期状态码；未修改生产实现。

## 实际证据

[安全证据](../../verification/M14-T07-security.md)的 S05 对以下路径分别发送 POST、PUT、PATCH、DELETE，正文为 `{}`：

- `/api/v1/data-sources/tushare_pro/datasets`
- `/api/v1/data-sources/tushare_pro/datasets/stock_company`
- `/api/v1/data-sources/tushare_pro/datasets/stock_company/records`

12 次均返回 HTTP 500 / `INTERNAL_ERROR`，预期为 HTTP 405。每次只读核对均确认业务行与 stub 调用数未变化；没有观察到写入成功。

## 关闭条件

按问题流程确认修复设计，使不支持的方法稳定返回 405，同时保留安全响应头、无凭证/内部信息的错误体和只读状态。修复包固定身份后复跑[批准设计](../../task-designs/M14-T07-design.md) S05，12 项全部通过；不得将 500 视为满足方法拒绝合同。
