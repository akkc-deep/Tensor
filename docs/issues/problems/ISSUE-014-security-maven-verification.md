# ISSUE-014：安全 Maven 门禁因依赖传输失败未完成

## 当前状态

新增，未解决。本次是构建输入获取失败，不是已证实的测试断言失败；最终轮 Maven 门禁不能算通过。

## 实际证据

[安全证据](../../verification/M14-T07-security.md)对应脚本提交 `741376b`，运行时间为 2026-09-06 16:07:48～16:15:20 UTC。聚焦 Maven 命令退出 1，`maven` 为 `tests-or-enforcer-incomplete`。

已扫描的本轮 Maven 输出将失败定位于 `org.testcontainers:testcontainers:jar:1.21.4` 从 Maven Central 传输时正文提前结束（Content-Length 未满足），因此没有形成七个指定测试类的新完整报告。没有修改 POM、依赖或缓存内容来绕过门禁。

前一轮脚本提交 `7940b92` 曾得到七类共 81 项测试及 Enforcer 通过；这是另一轮结果，不能替代最终轮失败。历史原始安全报告仍保留在本机私有运行目录，最终提交报告按本轮状态记录。

## 关闭条件

恢复有效的依赖获取后，按[批准设计](../../task-designs/M14-T07-design.md)原 Maven 命令重跑；Enforcer 实际执行，七类新报告均非空、零失败/错误/跳过。最终安全验收仍需一个全部必需门禁通过的完整运行，不用空报告或历史通过记录补足。
