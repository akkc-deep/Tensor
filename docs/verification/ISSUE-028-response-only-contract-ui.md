# ISSUE-028 HTTP合同与页面验收

2026-09-14，按[专属设计](../task-designs/ISSUE-028-design.md)完成。用户授权“完成issue28”；沿用现有分支和暂存成果，没有commit、push或发布。

## 交付与结果

- TaskResponse必需`extraction`：SINGLE为null；RANGE为精确`{policyVersion,ruleKind}`。Controller从同一任务快照取得controls与policySummary，列表复用详情转换；历史严格/UNKNOWN及新规则只读取保存值。损坏快照在详情/列表均返回QUERY_FAILED，不回退当前能力或泄露快照。
- 正式schema/examples/OpenAPI、前端严格DTO同步。RESPONSE_ONLY要求无阈值、有非空白依据、NATIVE_RANGE且不可拆，AVAILABLE/NEEDS_VERIFICATION同样检查。缺失、模式错配、额外键、空白版本、未知规则及非法能力组合被拒绝；18份能力/任务正式示例直接经保真JSON读取与前端解析验证。
- 下载提交按钮旁显示响应采集合同；列表和详情持续显示“数据完整性未确认，可能存在上游截断”。仅SUCCEEDED且来源行数大于0显示“返回记录已采集”，等于0显示“本次请求未返回记录”。失败/中断不冒充成功；UNKNOWN仅提示完整性未确认，原严格规则和SINGLE文案保持。页面显示保存的规则/版本，不新增弹窗或勾选步骤。
- 受控HTTP实际执行非空、空、来源失败及关闭中断，核对详情/列表的摘要、状态和计数。受控浏览器覆盖各状态、刷新/重开、当前能力变化、旧严格/UNKNOWN/SINGLE及手机/桌面；原40接口、主题、五视口与键盘回归保持。

## 验证

Java21.0.11、Maven3.9.15、Node24.15.0；白名单环境排除真实数据源和业务DB配置，Testcontainers使用已观察的Colima socket及mysql:8.4.6。浏览器使用仅监听127.0.0.1:4173的临时Vite，全部API由受控fixture拦截。日志和机器汇总在`/private/tmp/issue028-control/`。

| 检查 | 实际结果 | 日志 |
| --- | --- | --- |
| 正式schema RED | 9项中2项预期失败：缺新规则/摘要合同 | contract-red.log |
| DTO RED → GREEN | 7项预期失败 → 144通过；加入正式示例后162通过 | dto-red.log、dto-green.log、dto-published.log |
| 页面 RED | 列表/表单9项、详情8项预期失败 | ui-red.log、detail-red.log |
| 后端专项 | 原组合77通过，补充终态4通过；最终HTTP整类19通过 | backend-targeted-green.log、response-only-outcomes-green.log、controller-it-final.log |
| 全量acceptance verify | 66个后端测试类，1125项通过（含生产包4项、验收包3项）；BUILD SUCCESS | acceptance-verify.log、acceptance-summary.json |
| 前端全量与构建 | 34文件524项通过，Vite构建通过 | acceptance-verify.log |
| 受控浏览器 | 66项通过：任务15项、共用UI51项 | e2e-final.log |
| 静态/清理 | 两种diff check通过；页面detector无发现；无Testcontainers运行容器、无4173监听 | ui-detect.json；本次工具检查 |

上述最终验证失败/错误/跳过均为0。后端1125项与HTTP19项互不重复，合计1144项；前端524、浏览器66项另计。完整acceptance默认不执行ControllerIT，因此最终单独执行19项，未将其混称为同一Maven命令结果。

```sh
mvn -o -f data-plane/pom.xml -Dtest=DownloadTaskControllerTest,DownloadTaskControllerIT,DownloadTaskContractTest,DownloadTaskServiceTest,DownloadTaskJsonTest,TushareBatchAvailabilityTest -Dsurefire.failIfNoSpecifiedTests=false -Dskip.npm -Dskip.installnodenpm test
mvn -o -f data-plane/pom.xml -Pacceptance verify
mvn -o -f data-plane/pom.xml -Dskip.npm -Dskip.installnodenpm -Dtest=DownloadTaskControllerIT -Dsurefire.failIfNoSpecifiedTests=false test
npm --prefix control-plane test
npm --prefix control-plane run test:e2e -- e2e/download-tasks.spec.js e2e/ui-redesign.spec.js
git diff --check
git diff --cached --check
```

初次Mockito自附加及Vite监听被沙箱限制，在相同白名单环境获本地执行许可后通过；新示例测试最初遇到Vite URL/raw导入限制，改为既有测试使用的文件读取方式后通过。这些环境错误不充当功能RED或成功证据。最终源码不再修改后执行全量构建及完整HTTP集成。

## 审查与边界

[前端独立审查](../../.superpowers/sdd/2026-09-14-issue-028/task-2-review.md)与[整体/增量终审](../../.superpowers/sdd/2026-09-14-issue-028/final-review.md)均APPROVE，无剩余发现。审查发现的简化恢复fixture、缺HTTP终态观察、正式合同旧34项文案已修正并验证；[后端报告](../../.superpowers/sdd/2026-09-14-issue-028/task-1-report.md)保留专项过程。

生产策略文件与开始时备份逐字一致，保持4 AVAILABLE/30 NEEDS_VERIFICATION/6 SINGLE_ONLY。唯一真实验收索引逐字一致，SHA-256为`7e7d390e45eaffcb7310839db6670e736fdfd216ade006b82a79f95c7822957d`，仍25轮822case886请求。没有新增真实SOURCE/TASK/SQL、候选开放或索引升级。

本地生产JAR SHA-256：`a9012ff5eca0255abb670619cc1beb5dca4fc02c8138ef79a68ded276c03a756`；验收JAR：`93f41016b9c48f1f6d2cf8f6cd504137a0e1dbadfbbe6704de36af861b3eddb0`。这些是受控构建结果，不替代后续候选或真实验收身份。

ISSUE-028四项Acceptance成立，向ISSUE-029交付确定的HTTP合同、严格解析与页面语义。母任务ISSUE-026保持IN_PROGRESS；后继负责证据工具与四项新SOURCE，本次不开始后继实现。
