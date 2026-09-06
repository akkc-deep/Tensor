# ISSUE-015：后端漏洞扫描未形成有效报告

## 当前状态

新增，未解决。后端依赖风险尚未完成评估，不能报告“无高危漏洞”；未升级依赖、关闭分析器或添加 suppression。

## 实际证据

[安全证据](../../verification/M14-T07-security.md)中 Dependency-Check Maven 13.0.0 退出 1，报告不存在，固定错误类别为：

- `nvd-invalid-api-key`
- `cisa-http-403`
- `vulnerability-data-missing`
- `report-missing`

本轮未提供可选 NVD API key；`nvd-invalid-api-key` 是扫描器的实际错误分类，不表示已发现用户配置了错误密钥。此前独立小请求能够访问公开端点，也不能证明扫描器的数据更新或完整分析已恢复。

冻结包盘点有 50 个第三方 JAR，扫描命令补充了官方 `scanDirectory` 以覆盖实际打包库，包括自动打包的 Boot jarmode 工具。盘点完成不等于漏洞报告覆盖完成。前端 npm audit 的 196 项覆盖和零告警只证明前端审计结果。

## 关闭条件

按[批准设计](../../task-designs/M14-T07-design.md)恢复漏洞数据更新并取得有效的新报告，覆盖全部 reactor 模块及冻结包第三方库；无未接受的 CVSS ≥ 7 / HIGH / CRITICAL，缺失严重性完成评估。保持原扫描器版本、作用域、更新和错误门槛；凭证仅通过规定的单独环境输入，不进入命令参数、页面或证据。
