# ISSUE-018-T14 四接口 SOURCE 与固定 SINGLE 限定审查

2026-09-13 中途审查。四接口可以进入本地候选开放；最终 RANGE AVAILABLE、完整 SINGLE 验收及 T14 完成尚未成立。本报告不修改生产策略或规范结果索引，不登记新的实际请求轮次。

## 范围与判定

已阅读全文 [T14 设计](../task-designs/ISSUE-018-T14-design.md)、[T13 设计](../task-designs/ISSUE-018-T13-design.md)，以及 [T14 轮次登记](ISSUE-018-T14-runs.md)、[T13 候选评估](../../.superpowers/sdd/2026-09-12-issue-018-t13/task-4-candidate-assessment.md)、既有官方复核与四接口官方引文。检查了 `tushare-range-evidence.js` 的完整索引、case/run 校验、`validateInterface`、`validateAvailable`、`selectTaskCases` 及实际任务能力匹配。

私有文件仅读取 `/private/tmp/issue018-t14-priority-source-20260913T092938Z/` 下的 `cases.json`、`source-evidence.json`、`source-run.json`、`source-input-identity.json` 四个安全投影。未读取凭证、SQL defaults、原始金融响应或运行日志；未调用来源、SQL、真实浏览器或创建子代理。SOURCE 清洁身份的独立结论限于这些安全文件；主代理另报告 `inputUnchanged/privateLogSafe=true`，本审查没有读取私有日志独立复做该检查。

## 最终 SOURCE 身份与固定计划

| 项目 | 已复核事实 |
| --- | --- |
| runId | `issue018-t14-priority-source-20260913T092938Z` |
| 开始 / 结束 UTC | `2026-09-13T09:29:59.798Z` / `2026-09-13T09:31:17.440Z` |
| 退出 / 清理 | exitCode `0`；cleanup `PASS`，完成于 `2026-09-13T09:31:17.515Z` |
| cases / 请求 | 33 / 33；33 PASS，0 FAILED、EVIDENCE_MISSING、NOT_RUN |
| SOURCE 差异指纹 | `78b78c30971c82a463e5953025b1f4095d9cfd22c0c470ddaf0fc852f07c8eaa` |
| production JAR | `4f0766b5595c2ee7bd11133056577f766c5d8629905ad6647efa6683f5324d6f` |
| acceptance JAR | `60bcf60adb8025dba2792ae8d2edb61121b9ff0745be2184b6462be56fcd42e7` |
| manifest | `386f46a99b6605e203129836d7a744b96b65304307f52991dd8bba6fd1870984` |
| request examples | `6d4c74a1a539b59ac20fb0cbd3ba1fba0954c40ef1209b652f7dcc2192ec932f` |
| cases.json SHA-256 | `679a8f28f52a6ecfdde21236e5ba4fa31aea09f6ada064e2295896f808ef3395` |
| source-evidence.json SHA-256 | `99aaa9665e87a4a1392f93b7edc898af5637f21ff5aeea7b0b751b5229fa9444` |
| source-run.json SHA-256 | `789bfe0939eeeb75b65be310eb5ea8116f7e464826cd40205c71b80f1d1200ca` |
| source-input-identity.json SHA-256 | `937ef98b967c91ee7f8b0bf6f3efeb53438812247b63d82c0c0e1b8173f82b99` |

最终 `source-run.cases` 与 `source-evidence.cases` 完全相同；每项 `caseId/apiName/mode/params/dateAxis` 与固定清单逐项相同；输入身份中的摘要、33 项计数和登记摘要一致。所有新 caseId 均未与原 329 项重复。

计划符合本轮合同：四 API 各有两股票 `000001.SZ` / `600000.SH` 的 SINGLE（`trade_date=20260807`），以及两股票分别独立的整段 `20260803～20260810`、下端同日 `20260803`、上端同日 `20260810`，合计 32 项；另有 daily_basic 的 `000001.SZ` 跨年 `20251229～20260105`，共 33 项。第二股票没有借用 SINGLE 充当 RANGE；没有多股票合并或执行后换样本。

登记预期为 33 次请求，实际也是 33 次；该四项使用原生范围，不需要日历请求。运行约 77.642 秒，未达到 30 分钟或 5000 请求预算。Probe 源码仍拒绝小于 2000ms 的配置，并在共享 Context 中限制 30 分钟/5000 次；安全投影没有每次请求启动时间，本审查不由总耗时反推逐次间隔。

原 `trade_cal-bse-direct` 的 `BATCH_COMPLETENESS_UNCONFIRMED` 与三个 NOT_RUN（`top_list-closed-calendar`、`top_list-sh-calendar`、`margin-bse-direct`）继续留在旧轮和调查范围内。本轮四项成功没有解决这些目标，也没有把旧失败轮升级。

## 四接口候选结论与证据边界

四接口各两股票 SINGLE 均 1 行；两股票整段均 6 行，两端各 1 行；实际区间日期均为 `20260803,20260804,20260805,20260806,20260807,20260810`。全部通过字段、股票与 trade_date 闭区间检查，均未触及候选阈值。daily_basic 跨年返回 4 行，实际日期为 `20251229,20251230,20251231,20260105`。

| API | 本地候选判断 | 官方依据与仍须保留的边界 |
| --- | --- | --- |
| daily_basic | 可以，ROW_LIMIT 6000 | [官方引文](ISSUE-018-range-acceptance.md#daily_basic)明确“单次请求最大返回6000条”。本轮两股票、两端、跨年来源成立；跨年 TASK/SQL 尚未取得。 |
| stk_limit | 可以，ROW_LIMIT 5800 | [官方引文](ISSUE-018-range-acceptance.md#stk_limit)明确“单次最多提取5800条”。循环调用声明和本片阈值分别成立；本轮只实测两只沪深股票，未实测全部市场、B 股或基金。 |
| moneyflow | 可以，ROW_LIMIT 6000 | [官方引文](ISSUE-018-range-acceptance.md#moneyflow)明确“单次最大提取6000行”，公开历史起点为 2010。本轮只实测所选 2026 窗口，不能称全部历史逐日实测或证明北交所覆盖。 |
| margin_detail | 可以，ROW_LIMIT 6000 | [官方引文](ISSUE-018-range-acceptance.md#margin_detail)明确“单次请求最大返回6000行”。股票与日期观察成立；业务键、写入归属与历史保留待 TASK/SQL；沪深样本不能推定所有 BSE 股票支持。 |

上述是本地待验收构建的准入判断：按设计逐项由 v1 升为 `tushare-range-v2`，写实际核验日期、完整性与新 SOURCE 引用，重建后绑定新包并执行 RANGE。尚未产生该候选包的实际 TASK，规范索引目前保持 NEEDS_VERIFICATION 是正确的。

SOURCE 中 `expectedCoverage=SOURCE_SEMANTICS_ONLY_COMPLETENESS_UNCONFIRMED`、`reviewMethod` 含 `completeness UNKNOWN`，记录的是取证当时生产门禁未开放的事实；不得事后改成已确认完整或虚构 SQL。公开完整性合同与 SOURCE 语义分别判断。小样本均低于阈值也不证明等号、超过阈值的拆分或最小单点满额处理；这些边界仍须引用 T12 的受控证据及策略回归。

## 必须匹配的 25 个 RANGE TASK

令 `S = issue018-t14-priority-source-20260913T092938Z`。下表每个 SOURCE 完整 caseId 都是 `S-<API>-<后缀>`，对应 TASK 必须使用独立新 runId、全局唯一 caseId，且 API、模式 RANGE、TRADE_DATE 与精确参数完全相同。本表是对后续固定清单的匹配要求，不是已登记或已执行的 TASK 清单。

| SOURCE 后缀 | 股票 | start_date | end_date | 适用 API / TASK 数 |
| --- | --- | --- | --- | --- |
| range | 000001.SZ | 20260803 | 20260810 | daily_basic、stk_limit、moneyflow、margin_detail / 4 |
| lower-bound | 000001.SZ | 20260803 | 20260803 | 同上 / 4 |
| upper-bound | 000001.SZ | 20260810 | 20260810 | 同上 / 4 |
| range-600000 | 600000.SH | 20260803 | 20260810 | 同上 / 4 |
| lower-bound-600000 | 600000.SH | 20260803 | 20260803 | 同上 / 4 |
| upper-bound-600000 | 600000.SH | 20260810 | 20260810 | 同上 / 4 |
| cross-year | 000001.SZ | 20251229 | 20260105 | daily_basic / 1 |

所以 daily_basic 为 7 个 RANGE TASK，另三项各 6 个，合计 25 个；不能只执行每 API 一段就称整套引用满足 `validateAvailable`。8 个 SOURCE SINGLE 可以保留为语义佐证，它们不替代独立完整 SINGLE 轮的 74 次真实任务、148 次 records 查询及单独计数的 fixture。

`selectTaskCases('range')` 会从完整索引中选择 clean/exit0、PASS、同参数的 SOURCE，并记录其真实 run/case 绑定；它允许接口当前处于 NEEDS_VERIFICATION，以便本地候选先执行 TASK。真正提交前 `submitDownload` 另核对运行时 AVAILABLE、策略版本、日期轴、规划模式和阈值。TASK 结果必须绑定候选包，保存页面、完整树/叶子、SQL 业务键/股票归属/摘要、两股票历史保留、实际 source/insert/update 与日志。SOURCE 和 TASK 包允许因候选策略变更而不同，不能用旧包哈希代替重建后的 TASK 身份。

## 旧失败如何保留、最终接口如何引用

已将审查时完整规范索引与 Git 原暂存索引比较：原三个 run 连同全部 329 case 逐字段 deepEqual。原运行历史如下：

| 原 run | case 数 | 状态 | 退出 / 清理 |
| --- | --- | --- | --- |
| issue018-t13-source-20260912T123840Z | 181 | 88 PASS、89 EVIDENCE_MISSING、1 FAILED、3 NOT_RUN | 1 / PASS |
| issue018-t13-single-43721811-2900-4e53-9d5f-51c156e1c5de | 74 | 74 NOT_RUN | 1 / PASS |
| issue018-t13-single-5f17f7aa-0b5e-40bd-b6d5-b8d816d94d65 | 74 | 14 PASS、1 EVIDENCE_MISSING、59 NOT_RUN | 1 / FAILED |

当前第四 run 与允许读取的完整新 `source-run.json` 相同；索引为 4 轮 / 362 case。对原索引和当前完整索引调用 `validateEvidence` 均成功。原三 run 的规范 JSON（`JSON.stringify`）SHA-256 为 `bc740683e9b7b15d66ed70d1bbbb23c404c89139374bea11205fb43bec0b79ce`，供后续比较使用。

`interfaces[].cases` 是该接口当前结论所采用的证据集，`runs[].cases` 才是全部不可改写的历史事实。现有 schema/validator 不要求每个历史 case 都被当前接口再次引用，不能为了“历史仍在”把失败 run 强塞进最终 AVAILABLE 的当前决定集。

1. 继续完整追加新 run；旧 run 的 runId、caseId、时间、参数、状态、退出码、清理、计数、构建指纹都不改写、不移除。不得拆出旧 PASS、新增成功 run 包装它们。
2. TASK 尚未结束时，四接口可继续 NEEDS_VERIFICATION，引用新 SOURCE 并保留实际未决项。最终决定时，将四接口的 `cases` 更新为本次完整相关 SOURCE（daily_basic 9 项、另三项各 8 项）与新有效匹配 TASK；旧失败关联保留在本报告、轮次登记及原 run 中。
3. `validateInterface` 仅从当前引用集聚合 phaseStatus；混入旧 SINGLE NOT_RUN 会使 taskStatus 无法为 PASS。`validateAvailable` 进一步要求**所有引用所属 run**均 exit0/cleanup PASS，因此连旧失败轮中的 PASS SOURCE/SINGLE 也不能放入最终有效决定集。
4. `validateAvailable` 对每个引用 RANGE 双向要求另一阶段、不同 run、同日期轴/同参数的证据；保留全部 25 个新 RANGE SOURCE 就必须有匹配的 25 个 TASK。任何新 TASK 失败轮同样不得挑 PASS 转为清洁验收。
5. 如果最终决定同时引用完整新 SINGLE TASK 轮，该轮也须 exit0/cleanup PASS，所引用样本逐项 PASS。没有引用 SINGLE TASK 不免除 T14 的独立 74/148 总验收要求。
6. 最终 AVAILABLE 还要求 sourceStatus/taskStatus PASS、非 UNKNOWN、版本至少 v2、非空 decisionRef、无开放阻断 unresolved。已解决的旧运行门禁可由新清洁轮替代；样本/市场/历史覆盖边界继续写在明确决策和说明中，不能删掉限制后扩张为未实测结论。

本审查另在内存中构造了全部 25 个同参数、独立 caseId 的匹配方案，只调用纯 `selectTaskCases`：25 项全部选中，所有绑定均指向新 SOURCE run；没有写出清单、执行任务或向索引填入合成事实。

## 附录：固定 SINGLE 计划代码审查

范围为主工作树三个文件相对原暂存区的本次增量：`tushare-live.spec.js`、`tushare-range-evidence.js` 及对应测试。未发现阻止按已登记固定计划启动新 SINGLE 的问题。

- 精确范围：`selectTaskCases('single', plan)` 先校验字段、参数形状与唯一 caseId，再要求 74 项且每个独立 canonical 样本恰有一项同 API/mode/params。遗漏、额外、重复、改日期/股票/交易所/状态等均拒绝；34 股票各两项与 6 非股票原方式保持。
- 执行身份：声明 CASES_FILE 时读取其原 runId/caseId；不会再加生成前缀或静默使用默认清单。返回顺序仍按 canonical API 与第一/第二股票，保持历史股票复核的执行顺序；未配置文件时兼容旧自动身份。固定计划本身由运行登记保证全局身份不复用。
- 私有输入：读取要求绝对路径、当前用户普通非 symlink 文件，计划文件 0600、直接父目录为当前用户普通非 symlink 的 0700 目录。显式空环境变量也会失败，不回退默认。
- 清理身份：`evidenceInputsUnchanged` 对**原绑定路径**重新执行同一权限/文件校验并比较原字节 SHA-256，覆盖换内容、改权限、目录权限变化、删除及 symlink 替换。写出的 `casePlanSha256` 保留加载时摘要；清理失败不会用替换文件摘要覆盖。JAR 已验证的运行一旦 immutableInputs=false，仍写失败事实并使整轮清理失败。
- 离线验证审阅：新增测试直接执行 helper 与实际 harness 注册/读入/清理函数，覆盖 canonical 74 项、独立 ID、顺序、危险路径/权限、摘要变化、无计划兼容与 single/range 清理拒绝。未注册真实浏览器/JVM/SQL/来源执行。实现代理报告的完整 Node 测试结果由主代理记录；本审查未复跑业务测试。

该附录只证明本次固定输入接入逻辑可供新轮使用，不证明实际 74 次任务、SQL、查询或清理已通过。

### 显式 utf8mb4 defaults 兼容修复复查

原 `validateSqlInputs` 强制包含 `[client]` 的总计 7 行，与 T14 要求 defaults 显式 `default-character-set=utf8mb4` 的 8 行配置冲突，属于新轮执行前应消除的实际拒绝点。修复后没有发现阻断问题：

- 仅接受原 7 行或新增 charset 的 8 行。移除 `[client]` 后，精确键集合分别为 6 个基础键，或基础键加唯一 `default-character-set`；总行数与唯一键数共同拒绝重复键、缺失键及未知键。
- 新 charset 值必须严格为 `utf8mb4`；其他编码、空值或错误键拒绝。旧 7 行兼容不改变 T14 实际新轮必须显式登记 utf8mb4 的要求。
- 私有 defaults 的绝对路径、普通非 symlink、当前用户、0600 条件未变；host、port、database、user、password、TCP 与应用 JDBC/账号全量匹配未变。`mysql` CLI 仍固定 `--default-character-set=utf8mb4`，该增量没有新增 SQL 或放宽语句范围。
- 新三项离线回归调用实际函数，覆盖两种合法格式、错误编码、重复/未知选项及全部账号/schema 不匹配。实现代理报告先 RED（原 8 行被拒绝），再 Node24 全套 77/77；本审查通过代码和断言核对，没有读取真实 defaults 或重新执行 SQL。

### 本次实际核对

执行的是安全 JSON 聚合、SHA-256、全量原/新 `validateEvidence`、固定计划 `validateCasePlan`、内存 25 项 SOURCE 绑定选择，以及报告差异检查；这些均不进行来源、SQL 或浏览器调用。真实环境/运行门禁仍由主代理在新轮开始前核验。本审查未发现上述两个限定修复的阻断问题，不宣称 T14 或母 issue 已完成。
