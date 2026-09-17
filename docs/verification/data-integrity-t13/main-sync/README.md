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

[源代码清单](source-identity.json)覆盖607个源码、配置与合同文件；在浏览器验收完成时与已提交 `a7deb7d` 逐项SHA256一致。最终main集成保留其中606项原值，只同步合同门禁脚本的上游新增测试清单；差异与607项归档输入核对见[最终摘要](contract-summary.json)。历史T12/T13证据已恢复原内容。

## 最终门禁

2026-09-18 用户明确“授权本地合并并继续完成 T13”，解除此前自动审批对311文件默认分支合并的拒绝。`a7deb7d`及仅文档截图提交`1e3b039`已快进合并到真实main；恢复stash、隔离工作区均保留，按最新授权未推送远程。[集成记录](integration.json)保留原拒绝原因、最新授权与SHA。

第一次正式门禁在`1e3b039`上运行：Maven成功，但远程`5aaf6ad`新增`mainBusinessSingleIsOnlyAStockSnapshot`后metadata已为42项，门禁旧清单仍为41项，因此失败。仅同步精确方法、计数、自检和成功摘要，未改变任何门禁守卫；[限定复审](merge-review.md)通过，修复提交`6c3c13f`。[首次失败](contract-first-attempt.json)单独保留。

随后在干净main `6c3c13f908ca37acddfcf46fe6a76246a4283630` 实际执行完整`sh scripts/verify-contracts.sh`，**exit0，metadata42/schema47/package4全部通过，0失败/错误/跳过**；同轮前端50文件/738项及构建通过，11项拒绝自检通过，Maven46.605秒。40个YAML、生产54表/8迁移、验收55表/9迁移等合同通过；两个自有容器清理完成，既有容器保留。[最终摘要](contract-summary.json)及[原脚本证据](contract-verification.json)保存SHA、时间、报告方法和资源哈希。

正式门禁归档的607项输入与集成提交/工作区一致，产品与测试代码仍与既有隔离验收相同。任务依次记录 BLOCKED → READY → IN_PROGRESS → COMPLETED；T13为最后一项预定义任务，无后继交接。本次完成既定本地检查范围，不包含对话中讨论的生产规则增强。
