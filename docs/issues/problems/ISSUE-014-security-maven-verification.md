# ISSUE-014：安全 Maven 门禁因依赖传输失败未完成

## 当前状态

已解决（2026-09-07）。有效依赖已可用，按原命令在新的 Git HEAD 快照重跑，七类共 81 项测试及六模块 Enforcer 全部通过；包含工作区已有修复的独立快照也通过七类共 118 项测试。两轮均生成完整新报告，零失败、错误或跳过，见[重跑证据](../../verification/ISSUE-014-security-maven-verification.md)。

本问题关闭仅确认 Maven 专项门禁恢复。[ISSUE-015](ISSUE-015-backend-dependency-audit.md) 后续按用户接受剩余风险的决定标记为完成；M14-T07 整体安全验收仍未通过，历史失败报告保留，不以本次结果补写为通过。

## 实际证据

[安全证据](../../verification/M14-T07-security.md)对应脚本提交 `741376b`，运行时间为 2026-09-06 16:07:48～16:15:20 UTC。聚焦 Maven 命令退出 1，`maven` 为 `tests-or-enforcer-incomplete`。

已扫描的本轮 Maven 输出将失败定位于 `org.testcontainers:testcontainers:jar:1.21.4` 从 Maven Central 传输时正文提前结束（Content-Length 未满足），因此没有形成七个指定测试类的新完整报告。没有修改 POM、依赖或缓存内容来绕过门禁。

前一轮脚本提交 `7940b92` 曾得到七类共 81 项测试及 Enforcer 通过；这是另一轮结果，不能替代最终轮失败。历史原始安全报告仍保留在本机私有运行目录，最终提交报告按本轮状态记录。

## 关闭条件

已满足本问题的关闭条件：恢复有效的依赖获取后，按[批准设计](../../task-designs/M14-T07-design.md)原 Maven 命令重跑；Enforcer 实际执行，七类新报告均非空、零失败/错误/跳过。最终安全验收仍需一个全部必需门禁通过的完整运行，不用空报告或历史通过记录补足。
