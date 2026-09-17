# DATA-INTEGRITY-T10 验收记录

- 工作区：`.worktrees/data-integrity`，分支 `feat/data-integrity`。
- 范围：六个完整性API、精确DTO、不可变提交和能力确认、会话恢复、详情/当前结果页GET生命周期。不包含页面或真实后端浏览器闭环。
- 权威设计：`docs/task-designs/DATA-INTEGRITY-T10-design.md`；实施计划：`docs/superpowers/plans/2026-09-17-data-integrity-t10.md`。

## 基线与测试驱动

启动前既有前端37文件/609项通过（初始shell Node22.22.3）；专项和最终验收使用项目要求的Node24.15.0。

状态模块初始空实现的行为RED：`useIntegrityCheck.spec.js -t 'starts idle'`，断言 `undefined` 不等于 `idle`，并非导入失败。随后逐项实现提交、恢复和轮询，使用真实DTO及可控异步请求验证。

## 结果

在Node24.15.0下运行：

```sh
npm --prefix control-plane test
npm --prefix control-plane run build
```

首次完整集成回归：40文件/663项通过，0失败/跳过（13:41执行）。生产构建成功，1724模块；保留既有大于500kB chunk提示。本项没有新增页面入口，另以Node原生ESM导入 `useIntegrityCheck` 并创建/清理实例验证实际导出绑定。

专项命令：

```sh
npm --prefix control-plane test -- src/api/integrityDtos.spec.js src/api/integrityChecks.spec.js src/composables/useIntegrityCheck.spec.js src/api/api.spec.js
```

结果：4文件/66项通过，0失败/跳过。其中API/DTO/错误25项、状态/HTTP集成41项。完整前端回归40文件/663项通过，构建退出0；全部结果来自当前隔离工作区。

## 已证明的行为

- 六端点的路径、筛选、分页、200/202、Location、请求/正文/响应requestId与身份校验；五个完整性错误映射。
- T09全部15个公开例子、任务/单元/数据结论独立枚举、空描述/NON_STOCK/未完成报告。计数字符串直接转BigInt；0与null独立，原JSON大整数下载rowLimit和业务键无损，旧证据/关联日期/完整键保存。
- 冻结原请求、请求前保存缓存、双击合并；响应不确定先GET原submissionId，唯一匹配恢复或空页后显式同ID重发。能力变化重新获取并确认，历史查回不依赖当前能力；明确新检查才产生新ID。
- fake timers与deferred请求验证整轮settle后2000ms、慢请求不重叠、隐藏结果、切任务/页后的旧成功/错误/finally隔离、终态/路由离开/作用域卸载取消；查询失败保留数据/requestId并停止，重连只GET。
- 真实Axios adapter贯通能力→丢失POST响应→按ID找回→详情/结果→终态，验证只一次POST、精确计数与COMPLETED/FAIL独立。

## 审查与修复

首次状态审查发现两项P2：存储错误遮蔽原POST requestId，以及默认sessionStorage getter在保护范围外抛出。三项回归测试先RED后GREEN；增加只读storageError分开错误来源，成功写入后清除旧存储错误，保留原POST错误；默认存储读取放入受控边界。

最终独立审查对照T10专属设计、T09 schema/OpenAPI及实现：Spec PASS、Quality PASS，无新的可执行问题。详见 `.superpowers/sdd/2026-09-17-data-integrity-t10/task-1-report.md`、`task-2-review.md`、`final-review.md`。公开状态和校验器的补充已同步T10设计。

## 边界与交付

本项不含完整性页面，也没有把adapter测试当作真实MySQL/浏览器闭环；T13负责真实fixture与clean-main发布合同门禁。未改后端、下载状态机或既有页面。新增文件加入Git，保留原混合暂存基线与隔离工作区，不创建commit或合并主分支。
