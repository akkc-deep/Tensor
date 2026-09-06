# Pause Handoff

## Handoff Type

pause

## Task Link

- **Task board:** `docs/task-handoffs/tensor-v1-task-board.md`
- **Task ID:** `M14-T07`
- **Transition:** `IN_PROGRESS -> BLOCKED`
- **Design document:** `docs/task-designs/M14-T07-design.md`

## Current State

2026-09-07 后续处置：用户明确要求“先标记完成吧，后续我单独处理这些问题”，权威看板已按该例外将 M14-T07 从 BLOCKED 直接标为 COMPLETED，仅表示任务收尾。ISSUE-010～015 仍未解决，安全验收结果保持失败。本文件保留为历史暂停快照；下文 Remaining Work、Resume Task、Start Here 和 Blocker 记录原暂停时的处理条件，不再作为当前任务的恢复指令。问题由用户后续单独处理，M14-T08 未准备或启动。

本任务安全验收未通过。已实现单一验证入口并取得真实生产包证据；最终正式轮为 18 pass / 6 fail / 0 not-run，退出 1。证据单独提交 `67b1be6`，完整文件 SHA-256 为 `5ff4a5d5005f1ee3ab558867e1cb3e8adb38f3a6bc7d06ae4fc60a6d927cd83b`，与本轮扫描后的输出逐字节相同。

页面两次下载、查询、HTML/tooltip 文本与安全错误验证通过；S07 输入反例、S08 安全头/CORS、前端审计、凭证扫描及自有资源清理通过。存在四项产品合同缺口及两项工具门禁失败，分别登记 ISSUE-010～015。未修改生产实现或冻结 JAR，未将失败改判通过，未准备 M14-T08。

终审发现的检查器 F1（把 Docker 查询错误误判为资源不存在）已由 `0faf9dd` 修正并通过一次专项复审，无未解决的检查器审查项。正式报告仍对应 `741376b`，未改写为新脚本的运行结果。

## Changed Files

- `scripts/security/verify-release.sh`：可复跑的假凭证、回环上游、生产包安全验证与反例入口。
- `docs/verification/M14-T07-security.md`：本轮实际结果、输入身份、逐探针结果、扫描及清理证据。
- `docs/issues/problems/ISSUE-010-health-component-exposure.md`：根 health 公开结构。
- `docs/issues/problems/ISSUE-011-dataset-method-status.md`：12 项方法反例返回 500。
- `docs/issues/problems/ISSUE-012-query-extra-parameters.md`：6 类额外查询参数返回 200。
- `docs/issues/problems/ISSUE-013-query-completion-events.md`：4 个校验失败查询缺少完成事件。
- `docs/issues/problems/ISSUE-014-security-maven-verification.md`：本轮 Maven 依赖传输失败。
- `docs/issues/problems/ISSUE-015-backend-dependency-audit.md`：后端漏洞扫描未完成。
- 问题索引、本交接、实施计划及权威看板：记录实际验证和阻塞条件。

## Verification

- 正式启动命令：`/Users/qiangzhiwei/.pyenv/versions/3.11.5/bin/python3 /private/tmp/m14-t07-formal-launch.py`。该私有启动器只提供指定工具环境与 `M14_SECURITY_JAR`，内部执行 `sh scripts/security/verify-release.sh`；无真实凭证输入。
- 本轮源提交 `741376b605dc37ca9075b17abe98cf254230d6ad`，脚本 SHA `41f0d54f071bf545e2e59a28b532d8c335ef6a53a86639be10a6728357d9afec`。运行时间 2026-09-06 16:07:48～16:15:20 UTC；退出 1，18/6/0。
- Java 21.0.11、Node 24.15.0、npm 11.12.1、Maven 3.9.15、Python 3.11.5、Playwright 1.62.1、MySQL Server 8.4.6。运行及再次验证都应显式选择这些已验证工具；系统 Python 3.9 不支持脚本所用的安全解包参数。
- 冻结生产 JAR SHA `acbba3d2d0f240a31b526560e80d217f274d432518f96a07459ba9d44d3467ef` 前后相同；六次迁移、49 张初始空业务表、50 个第三方库符合盘点。
- S01 根 health 200 且存在 components/groups；S05 的 12 项均为 500/INTERNAL_ERROR；S06 的 6 项均为 200；各次行指纹及 stub 调用数不变。15 个已跟踪请求只有 11 个完成事件，缺失项为 S07.tsCode/page/pageSize/tradeDateFrom。
- Maven 退出 1：Testcontainers 1.21.4 的依赖传输正文提前结束；七类新报告不完整。后端扫描退出 1、无报告，记录 NVD/CISA 更新及数据缺失错误。前端审计覆盖 196，告警为 0。
- 浏览器请求 21、下载 2；stub 恰 2 次，无非预期 stub 调用。JVM 对外请求未独立测量，不能把回环配置当作完整出站审计。
- 跟踪源码 569 文件、递归 JAR 18,063 条目、本轮产物 1,299 文件、49 张业务表及报告终检完成，未发现本轮凭证命中。JVM 退出 143；browser/stub、容器及 1 个卷、3 个初始化凭证文件清理通过，8080 空闲。
- `741376b` 的 `sh scripts/security/verify-release.sh --self-test` 为 284 项通过；shell/Python/Node 语法检查通过。这些是检查器证据，不代表产品验收通过。前一轮 `7940b92` 的 81 项 Maven 测试通过为历史结果，不替代本轮 Maven 失败。
- 最新检查器 `0faf9dd` 的 25 项实际清理函数反例、309 项离线自检及语法检查通过；保存的本轮命令 89/90 响应通过修正后的精确对象缺失判定。此项只重验已有响应语义，不是新的凭证扫描或完整运行，原始证据 SHA 保持不变。

## Remaining Work

1. 按 ISSUE-010～013 的问题流程确认并完成产品合同缺口的修复；本安全任务不越界修改生产实现。
2. 恢复 ISSUE-014 的构建依赖获取与 ISSUE-015 的有效漏洞数据/完整扫描报告。
3. 如修复产生新 JAR，先按任务设计流程明确并确认新冻结身份。恢复后运行全部必需门禁，保留实际失败和未执行项；只有完整运行退出 0 才能满足 M14-T07 完成条件。

## Resume Task

恢复 `M14-T07`：验证凭证隔离、只读及 SQL 输入边界、页面文本、安全响应头、依赖风险与运行暴露面，并取得完整安全验收证据。状态以权威看板为准；解除阻塞和重新启动必须分别记录。

## Start Here

1. 权威看板 `docs/task-handoffs/tensor-v1-task-board.md` 的 M14-T07 行及状态证据。
2. 完整读取 `docs/task-designs/M14-T07-design.md`，再读本交接。
3. `docs/verification/M14-T07-security.md` 与 `docs/issues/README.md` 中 ISSUE-010～015 的具体证据和关闭条件。
4. `docs/superpowers/plans/2026-09-06-m14-t07-security-verification.md`、验证脚本、现行 runbook 及设计列出的公开合同/测试。

首动作：处理并提供 ISSUE-010～015 的解除证据；产品修复先走问题设计流程，验证数据源先恢复有效输入。不要在未解除阻塞时直接重启原安全验收或启动 M14-T08。

## Blocker

- **Reason:** health 公开组件结构、非只读方法状态码错误、额外查询参数未拒绝、四个查询缺少完成事件；Maven 测试门禁和后端漏洞扫描在最终轮未完成。
- **Resolution condition:** 上述合同缺口及工具输入失败分别有可核对的解除证据，验证目标仍满足已批准冻结身份要求或已完成身份修订确认后，才能记录 `BLOCKED -> READY`；随后另行 `READY -> IN_PROGRESS`，以一次完整通过的安全验证判断是否完成。

## Risks

- 当前结果不满足安全或发布门禁。未证明远端 HTTPS、数据库 TLS、外层身份控制或完整 JVM 出站隔离；这些部署要求继续保留。
- M14-T05 原 49 接口中的 9 项缺口及 ISSUE-009 性能缺口仍未解决。M14-T08 保持 `NOT_STARTED`。
- 临时冻结包可能被外部清理；恢复时必须重新核对 SHA，不能使用 acceptance 包或任意新包替代。
- 最新检查器的最后一处清理判定修正尚未重新执行全流程；当前只有对应反例和原始响应语义验证。未来完成验收仍必须运行全部门禁，不能将局部验证当作完整通过。
