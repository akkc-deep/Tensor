# ISSUE-011：数据集非只读方法返回 500

## 当前状态

已解决（2026-09-07）。全局异常处理器已专门处理不支持的 HTTP 方法；新生产包 S05 12/12 通过，均返回 405，数据与上游调用数不变。完整证据见[验收记录](../../verification/ISSUE-011-dataset-method-status.md)。

## 实际证据

[安全证据](../../verification/M14-T07-security.md)的 S05 对以下路径分别发送 POST、PUT、PATCH、DELETE，正文为 `{}`：

- `/api/v1/data-sources/tushare_pro/datasets`
- `/api/v1/data-sources/tushare_pro/datasets/stock_company`
- `/api/v1/data-sources/tushare_pro/datasets/stock_company/records`

12 次均返回 HTTP 500 / `INTERNAL_ERROR`，预期为 HTTP 405。每次只读核对均确认业务行与 stub 调用数未变化；没有观察到写入成功。

## 关闭条件

按问题流程确认修复设计，使不支持的方法稳定返回 405，同时保留安全响应头、无凭证/内部信息的错误体和只读状态。修复包固定身份后复跑[批准设计](../../task-designs/M14-T07-design.md) S05，12 项全部通过；不得将 500 视为满足方法拒绝合同。

## 修复设计与实施计划

本轮按用户“解决 ISSUE-11”的要求恢复已有 S05 合同。根因是 `GlobalExceptionHandler` 的 `Exception.class` 兜底先于 Spring 默认异常解析器执行，将 `HttpRequestMethodNotSupportedException` 映射为 `INTERNAL_ERROR`。原 `DatasetControllerIT` 未装配该 advice，未能发现生产组合下的偏差。

在全局处理器增加该异常的专用处理方法，返回 405 并复制 Spring 提供的 `Allow` 响应头。响应体为空，与现有路由级 404 的处理方式一致；请求 ID 和安全头仍由既有过滤器提供。无需新增业务错误码、修改 Controller 路由或业务服务。

1. 回归：实际两个 Controller、全局 advice、请求 ID 与安全头过滤器共同装配，参数化覆盖三个路径乘四种方法；断言 405、Allow、安全头、空响应体、业务依赖零访问。先观察 12 项因 500 失败。
2. 修复：增加专用异常处理并运行异常映射、元数据与 Web 配置回归，确认既有业务错误语义不变。
3. 验收：独立源码快照构建生产 JAR，先固定源码和二进制身份，再复用 M14-T07 的合成页面下载/查询前置步骤、S05 判定、每次数据库指纹与 stub 调用数核对，以及扫描和资源清理。仅处理本 issue，不将其他安全检查的问题标为通过。

## 完成结果

- 回归先红后绿：新增 12 项先全部因 500 失败，修复后定向 77 项通过。
- 独立快照完整构建通过：397 项后端单测、4 项生产包合同、170 项前端单测通过。
- 固定生产包 S05 12/12 通过：405、Allow、安全头、请求 ID、空响应体、业务表指纹不变、stub 无新增调用均满足要求。
- 限定范围的 19 项生产验证、309 项检查器自检、泄露扫描、身份复核与自有资源清理通过；其他 issue 保留各自状态。
