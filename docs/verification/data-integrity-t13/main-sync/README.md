# T13 同步远程 main 后的验收

本轮起点：用户要求“把远程main的代码拉到数据检验隔离工作区，然后继续完成T13”。在 `.worktrees/data-integrity` / `feat/data-integrity` 中执行；远程 `main` 为 `5aaf6ad`，原工作区 HEAD 为 `2110948`。

## 同步与保护

- 原暂存内容保存在恢复 stash `ee0c3d3b52f576e5bb52459caff1fc3ae6ca1707`，额外二进制补丁在 `/private/tmp/data-integrity-t13-sync-20260917T142152Z`；恢复副本保留。
- 先快进远程 main，再三方恢复数据检验改动。25 个冲突逐项合并；远程独有 147 个文件、隔离区独有 263 个文件均保留，13 个重叠文件按功能集成。[独立审查](merge-review.md)通过，未遗留 Critical/Important/Minor。
- Studio 下载/查看/显示设置及数据字段中文名保留远程版本；完整性入口、六端点、规则、V9、T01–T13 实现与历史证据保留。相对 main 的提交差异已排除继承的旧 Studio 版本。
- 合并后 390px 导航溢出：旧窄屏适配未保留，实测 scrollWidth=690。原有浏览器用例先 32 通过/7 失败；恢复 680px 内导航适配并使用远程 rem 单位后 39/39 通过（36.9s）。无断言削弱。[摘要](stub-summary.json)。
- 旧 T12/T13 截图及测试记录保持原样，本轮证据单列于本目录。页面测试的固定输出路径在运行后立即归档并恢复旧文件。

## 本轮验证

- Node24.15.0：50 文件/738 前端单测通过（9.24s）。测试总数与远程 main 的删除/新增一致，未为通过合并删除断言。
- Chromium API stub：39/39，通过；1440/1024/390 报告截图已目视核对。它不代替真实后端。
- 首次 Java 专项在 Testcontainers 新容器握手阶段停滞：MySQL 已 ready，但映射端口33331无 greeting。仅终止本轮 Maven/JVM，Ryuk 清理自有容器；原有数据库不动。该轮不算通过，诊断见 [environment-recovery.json](environment-recovery.json)。相同专项用全新容器重跑通过：441项，含125个真实IT用例，0失败/错误/跳过；用时1分58秒。[精确报告](integrity-tests.json)。
- production `clean verify`：1466项 Java测试、50文件/738项前端测试及构建通过，0失败/错误/跳过；生产包8迁移且无fixture/acceptance条目。[构建](production-build.json)、[测试](production-tests.json)。
- acceptance `clean verify`：1469项 Java测试、50文件/738项前端测试及构建通过，0失败/错误/跳过，50.167秒。[构建](acceptance-build.json)、[测试](acceptance-tests.json)。
- 最终真实完整性闭环1/1通过（26.120秒），SQL独立复算95%→100%，整只无行股票20个完整缺失键；六次检查证券快照逐行及摘要不变，上游调用0；同库规则2→3历史保留，新规则通用显示。[浏览器/SQL证据](real/browser-evidence.json)。1440/1024/390截图单列本目录，JAR哈希与本轮acceptance构建完全一致。
- 首轮下载回归2通过/1失败/12未执行：fixture元数据已中文化，而 `assertSingleRow` 仅为daily定义中文表头，fixture仍期望裸字段名。按独立七列表头补上明确fixture分支，未改产品、JAR、body/row/SQL/HTTP/网络断言。限定复审通过。主动停止首轮后续套件，自有进程/容器/凭据已清理；该轮不计整体通过。[首轮摘要](browser-first-attempt.json)。
- 第二轮下载14通过/1失败：第一次读取FAILED时，执行器租约尚未释放，当前 `canRetry=false`。`DownloadTaskService.controls` / `DownloadTaskCoordinator.controlAllowed` 明确以执行器空闲为控制门禁；原测试只等status后立即断言旧DTO的canRetry。将同一个既有GET轮询同时等待status/canRetry/canResume，保留原timeout及后续精确断言，不提交/重试任何新任务、不改产品。[第二轮摘要](browser-second-attempt.json)，本轮未计整体通过。
- 固定上述最终测试输入后，正式启动器于 `2026-09-17T14:44:53.680712Z` 至 `14:57:43.616850Z` 完整执行五套，**70通过、0失败/跳过，退出码0**。详见[逐项浏览器结果](browser-summary.json)及[启动器摘要](real/launcher-summary.json)。

| 套件 | 通过 | 秒 |
| --- | ---: | ---: |
| integrity-fixture | 1 | 26.120 |
| download-outcomes | 15 | 159.530 |
| dataset-query | 11 | 415.571 |
| tushare-metadata | 40 | 143.130 |
| fixture-flow | 3 | 18.749 |

本轮 acceptance JAR SHA256 为 `a5ff0e16d960b164702fd08890da9696b41b7c9b8514637f7722683a62a03ddf`。两次下载测试修复均只改浏览器测试，未改产品或此JAR；限定复审通过。最终清理完成：自有容器、JVM/浏览器及临时凭据已删除或退出，8080空闲，仅保留预先存在的 `tensor-issue018-t13-0bc7f37f27ff` 容器。

[源代码清单](source-identity.json)覆盖607个源码、配置与合同文件；与已提交 `a7deb7d` 及最终工作区逐项SHA256一致。后续仅补文档和验收摘要，历史T12/T13证据已恢复原内容。

## 最终门禁

设计第6节及 `verify-contracts.sh` 的 main/干净受保护输入/归档 HEAD 要求保持。已将同步后经审查的数据检验差异提交为 `a7deb7dd2bcac252171d1fd9d4c62fff2e07039e`；隔离工作区与恢复 stash 保留。

尝试将该提交快进到真实本地 main 时，自动审批拒绝了该操作，理由为311文件提交会改变默认分支历史，需要用户对此具体合并明确授权。main仍为 `5aaf6ad`，没有绕过审批或更改门禁；已向用户请求授权，隔离区全部验证已完成。详见 [integration.json](integration.json)。

当前任务状态仍为 BLOCKED；只有获准集成、实际门禁成功及输入一致性记录成立，才依次记录 BLOCKED → READY → IN_PROGRESS → COMPLETED。没有推送，隔离工作区与恢复副本继续保留。
