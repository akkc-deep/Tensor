# ISSUE-018-T12 验收包浏览器迁移报告

## 范围与实现

本工作仅修改 `download-outcomes.spec.js`、`fixture-flow.spec.js`、`dataset-query.spec.js` 和 `tushare-metadata.spec.js`。四个 spec 都在首个 `beforeAll` 动作中调用 `configurePackagedEnvironment`，分别选择 `download-outcomes`、`fixture-flow`、`dataset-query` 和 `tushare-metadata` 的隔离数据库环境。没有修改 helper、Playwright 配置、生产文件或真实账户 spec。

页面下载从同步 `POST /api/v1/downloads` 迁移为 `POST /api/v1/download-tasks`：精确断言带 UUID `submissionId` 和 `mode: SINGLE` 的请求、202 回执字段、`X-Request-Id` 及 `Location`，再通过正式 task detail 与 leaf batches GET 断言终态。GET 监控只允许已知 API、精确 UUID 路径和固定任务分页参数；未知 API、外部请求、意外写入、pageerror 或业务 HTTP 错误仍会使测试失败。

`download-outcomes` 和 `dataset-query` 的迁移证据已更新为精确 history `1:1,2:1,3:1,4:1,5:1,6:1,7:1,8:1`、8 次迁移和 52 张业务表。原空库、证券行数、SQL 回滚、上游/SQL canary、日志脱敏、页面脱敏和关闭后查询断言均保留。

## 场景映射

| Spec | 保留的场景 | 迁移后的任务事实 |
|---|---|---|
| `download-outcomes` | 8 个原静态场景：必填日期、逆序日期、fixture 插入/更新、EMPTY、fixture source/type/persistence 失败、Tushare daily 成功；原 7 个参数化 source auth/permission/rate/unavailable/network/timeout/payload 失败也全部保留。 | 成功和 EMPTY 为 SUCCEEDED；所有实际失败由 GET 200 返回固定 StoredError 及 FAILED batch/task；失败详情不泄露 canary、raw adapter 或 SQL 内容。SOURCE_TIMEOUT 额外证明 202 在 5 秒内返回、来源仍在运行时表单已解锁，最终错误在来源约 120 秒超时后持久化。 |
| `fixture-flow` | 成功下载并跨页查询、EMPTY 不增加数据、重启后两页均隐藏禁用 fixture，共3项。 | 成功/EMPTY 都打开真实任务详情，刷新后比对 task/batch DTO 和 1/1/0 或 0/0/0 计数；数据库保存行相等性及重启边界保留。 |
| `dataset-query` | 原11项全部保留：375次页面种子/更新、声明式过滤、分页/总数、组合日期、重置/非法范围、修正后尾页、宽表/精确文本、跨数据集竞态、pending 重置、网络失败恢复和键盘操作。 | 375 次写入逐项经历 202→task SUCCEEDED→单个成功 leaf batch，并保留表单复用、上游调用计数和最终 SQL 行数。 |
| `tushare-metadata` | 全40项动态 metadata contract，包括40 API/40 dataset、39个必填阻断、1个无参数项、11张截图、0 records GET/0上游调用。 | 每项新增真实 capability GET，精确比对 SINGLE 参数和 `rangeCapability(apiName)` 的当前 34 `NEEDS_VERIFICATION` / 6 `UNSUPPORTED` 矩阵；近期任务列表 GET 固定为 page1/pageSize20；未提交时新任务 POST 与旧同步 POST 均精确为0。 |

`fina_mainbz` 有一个刻意保留的历史/当前差异：冻结 manifest 仍精确断言历史 `query_mode=ann_date` 及 `[{ts_code:"000001.SZ",ann_date:"20260807"}]` 样本；T06 之后的当前 EXPECTED、API 元数据与请求示例独立断言 `snapshot/ts_code`。没有改写历史 manifest 或其哈希。

## 日志与安全断言

`download-outcomes` 与 `dataset-query` 不再把任务提交当成旧 `tensor.operation.completed operation=download` 事件。保留的 completion 事件必须全部是 query；每个新任务必须恰好有一条 `tensor.download_task.accepted`、一条 `tensor.download_batch.finished` 和一条 `tensor.download_task.finished`，其 taskId、requestId、dataset、终态、行数和 errorCode 与 GET 持久事实一致。

## 验证结果

- `node --check` 逐项检查四个 spec：退出 0，无输出。该检查在主体迁移完成后运行；最后的 manifest 分支和白名单收紧之后，根协调者持有串行构建槽，按要求未再运行 Node。
- 陈旧契约扫描：退出 0，仅命中 `tushare-metadata` 中刻意保留的旧 `POST /api/v1/downloads` 零次监控；无旧按钮/成功文案、V7/50表固定值或宽泛 `/api/**` 放行。
- `git diff --check && git diff --cached --check`：退出 0，无输出（当前共享工作区）。
- `npm --prefix control-plane run test:e2e -- --list`：首次退出 1、0 tests，在发现阶段暴露 `fina_mainbz` 历史 manifest `ann_date` 与当前 `snapshot` 的已知分歧。已按上述双重精确断言修正 spec；修正后复查因共享构建槽限制暂未运行，无日志文件。`--list` 不作为浏览器通过证据。

未运行完整验收包浏览器套件或 Maven；根协调者负责生成当前验收 JAR、创建四个空 MySQL schema 并执行最终 ordinary suite。本报告不把发现检查或其他所有者的结果写成完整浏览器通过。

## 关注项

`dataset-query` 会串行轮询 375 个真实后台任务，`download-outcomes` 的受控 SOURCE_TIMEOUT 用例会等待约 120 秒；最终套件需在根协调者的独占 8080/JAR/MySQL 环境中确认时限。任务日志断言依据 repository 事务提交后 observer；已静态核对成功、source/adapter 失败及 `PERSISTENCE_FAILED` 都会产生单个 batch/task finished 事件，仍需由完整运行确认打包日志无额外事件。
