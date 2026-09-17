# T13 同步远程 main 后的验收

本轮起点：用户要求“把远程main的代码拉到数据检验隔离工作区，然后继续完成T13”。在 `.worktrees/data-integrity` / `feat/data-integrity` 中执行；远程 `main` 为 `5aaf6ad`，原工作区 HEAD 为 `2110948`。

## 同步与保护

- 原暂存内容保存在恢复 stash `ee0c3d3b52f576e5bb52459caff1fc3ae6ca1707`，额外二进制补丁在 `/private/tmp/data-integrity-t13-sync-20260917T142152Z`；恢复副本保留。
- 先快进远程 main，再三方恢复数据检验改动。25 个冲突逐项合并；远程独有 147 个文件、隔离区独有 263 个文件均保留，13 个重叠文件按功能集成。[独立审查](merge-review.md)通过，未遗留 Critical/Important/Minor。
- Studio 下载/查看/显示设置及数据字段中文名保留远程版本；完整性入口、六端点、规则、V9、T01–T13 实现与历史证据保留。相对 main 的待提交差异已排除继承的旧 Studio 版本。
- 合并后 390px 导航溢出：旧窄屏适配未保留，实测 scrollWidth=690。原有浏览器用例先 32 通过/7 失败；恢复 680px 内导航适配并使用远程 rem 单位后 39/39 通过（36.9s）。无断言削弱。[摘要](stub-summary.json)。
- 旧 T12/T13 截图及测试记录保持原样，本轮证据单列于本目录。页面测试的固定输出路径在运行后立即归档并恢复旧文件。

## 本轮验证

- Node24.15.0：50 文件/738 前端单测通过（9.24s）。测试总数与远程 main 的删除/新增一致，未为通过合并删除断言。
- Chromium API stub：39/39，通过；1440/1024/390 报告截图已目视核对。它不代替真实后端。
- 首次 Java 专项在 Testcontainers 新容器握手阶段停滞：MySQL 已 ready，但映射端口33331无 greeting。仅终止本轮 Maven/JVM，Ryuk 清理自有容器；原有数据库不动。该轮不算通过，诊断见 [environment-recovery.json](environment-recovery.json)。相同专项用全新容器重跑通过：441项，含125个真实IT用例，0失败/错误/跳过；用时1分58秒。[精确报告](integrity-tests.json)。
- production `clean verify`：1466项 Java测试、50文件/738项前端测试及构建通过，0失败/错误/跳过；生产包8迁移且无fixture/acceptance条目。[构建](production-build.json)、[测试](production-tests.json)。
- acceptance `clean verify`：1469项 Java测试、50文件/738项前端测试及构建通过，0失败/错误/跳过，50.167秒。[构建](acceptance-build.json)、[测试](acceptance-tests.json)。
- 本轮真实完整性闭环1/1通过（25.6秒），SQL独立复算95%→100%，整只无行股票20个完整缺失键；六次检查证券快照逐行及摘要不变，上游调用0；同库规则2→3历史保留，新规则通用显示。[浏览器/SQL证据](real/browser-evidence.json)。1440/1024/390截图单列本目录，JAR哈希与本轮acceptance构建完全一致。
- 首轮下载回归2通过/1失败/12未执行：fixture元数据已中文化，而 `assertSingleRow` 仅为daily定义中文表头，fixture仍期望裸字段名。按独立七列表头补上明确fixture分支，未改产品、JAR、body/row/SQL/HTTP/网络断言。限定复审通过。主动停止首轮后续套件，自有进程/容器/凭据已清理；该轮不计整体通过。[首轮摘要](browser-first-attempt.json)。
- 第二轮下载14通过/1失败：第一次读取FAILED时，执行器租约尚未释放，当前 `canRetry=false`。`DownloadTaskService.controls` / `DownloadTaskCoordinator.controlAllowed` 明确以执行器空闲为控制门禁；原测试只等status后立即断言旧DTO的canRetry。将同一个既有GET轮询同时等待status/canRetry/canResume，保留原timeout及后续精确断言，不提交/重试任何新任务、不改产品。[第二轮摘要](browser-second-attempt.json)，本轮未计整体通过。
- 固定上述最终测试输入后再次完整执行五套；其结果与正式 clean-main 合同门禁在实际执行后补充。

## 最终门禁

设计第6节及 `verify-contracts.sh` 的 main/干净受保护输入/归档 HEAD 要求保持。依照用户本轮“继续完成T13”与仓库“可以直接在 main 分支工作”的授权，在同步冲突审查与回归通过后，仅将已核对的数据检验差异提交并本地集成到真实 main，再运行原正式门禁；保留隔离工作区和恢复 stash。此流程不涉及推送远程。

当前任务仍保留 BLOCKED 历史；只有实际门禁成功及输入一致性记录成立，才按状态机办理解除、恢复与完成。
