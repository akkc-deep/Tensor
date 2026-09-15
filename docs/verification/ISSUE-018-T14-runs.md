# ISSUE-018-T14 新轮次登记与执行证据

## 执行边界

2026-09-13 用户明确启动 T14；按已链接设计承接六项剩余工作。旧 T13 三轮 / 329 case、15 个成功任务原样保留；来源、任务、完整性和清理分别判断。运行先登记固定输入，失败停止该轮，不重试旧 submissionId，不用旧 PASS 拼接新成功轮。

## 源码、构建与环境

- 原 HEAD：`34f3e283c0ee7f58c19bf72e8a470c1c858ac26d`，分支 `feat/download-by-date-range`，启动时22个暂存文件、0未暂存差异。
- 独立 Git clone：`/private/tmp/issue018-t14-work-20260913T092556Z`。从原工作树逐文件复制并核对822个Git管理文件（包括全部22个暂存文件），未仅依赖HEAD；原暂存区不重置，demo/vite提交保留。迁移清单位于受限控制目录 `snapshot.json`，SHA-256 `58ac3bb611e9aa806f9aa66f5eaf84d8d8cdb5ce402de81025ec41353eb0866d`。
- 构建输入指纹继续覆盖 HEAD 与 data-plane/control-plane/scripts/docs/contracts 全差异及未追踪文件，不缩减范围。依赖复制为独立文件，构建目录没有指向原工作树的共享写入。
- 从独立源码运行 `mvn -f data-plane/pom.xml -Pacceptance verify`，2026-09-13 exit 0、BUILD SUCCESS，42.231s；不是 clean verify / 六条完整门禁。真实账户/DB变量从构建环境移除。日志 `/private/tmp/issue018-t14-control-20260913T092556Z/build.log`。
- 新 MySQL `mysql:8.4.6`，沿用已核验自有容器，独立 schema 初始0表，账号仅 CREATE/SELECT/INSERT/UPDATE/ALTER/INDEX/REFERENCES 单库权限；旧库不删除。数据库配置目录 `/private/tmp/issue018-t14-single-db-20260913T092822Z` 为0700、环境及defaults为0600，defaults显式utf8mb4。迁移8/52由后续任务harness实测，当前未宣称完成。
- 真实来源与浏览器串行；每轮30分钟/5000来源预算、至少2000ms间隔，workers1/retries0，trace/screenshot/video关闭。Token只进入后端/Probe环境，证据不含凭证或原始金融响应。

实际初始构建身份：

| 输入 | SHA-256 |
| --- | --- |
| productionJarSha256 | `4f0766b5595c2ee7bd11133056577f766c5d8629905ad6647efa6683f5324d6f` |
| acceptanceJarSha256 | `60bcf60adb8025dba2792ae8d2edb61121b9ff0745be2184b6462be56fcd42e7` |
| manifestSha256 | `386f46a99b6605e203129836d7a744b96b65304307f52991dd8bba6fd1870984` |
| requestExamplesSha256 | `6d4c74a1a539b59ac20fb0cbd3ba1fba0954c40ef1209b652f7dcc2192ec932f` |

## 四接口优先 SOURCE（执行前登记）

- runId：`issue018-t14-priority-source-20260913T092938Z`。
- 私有清单：`/private/tmp/issue018-t14-priority-source-20260913T092938Z/cases.json`，普通文件0600、父0700；SHA-256 `679a8f28f52a6ecfdde21236e5ba4fa31aea09f6ada064e2295896f808ef3395`。
- 固定33项：四API各2个SINGLE + 两股票各整段/两端RANGE，共32；另daily_basic跨年1项。预期33次请求，无需日历；硬预算仍5000次/30分钟。预期观察字段/股票/交易日期/非空样本/闭区间及候选阈值，不预填PASS。
- 依据：T14§2优先顺序、T13矩阵和代表场景、既有官方明确6000/5800/6000/6000最大条数合同。四接口先执行的目的为在稳定输入下重新取得完整独立SOURCE；不把旧run升级。全部原样本对应关系如下。
- 原`trade_cal-bse-direct`失败及`top_list-closed-calendar`、`top_list-sh-calendar`、`margin-bse-direct`三NOT_RUN继续在疑难调查中；本轮无这些接口，不对其作新结论。
- 当前状态：PLANNED；尚未发起本轮请求。执行后追加实际结果，不替换本段计划。

| caseId | API / mode | 精确参数 | 旧case关系 |
| --- | --- | --- | --- |
| `issue018-t14-priority-source-20260913T092938Z-daily_basic-single-000001` | daily_basic / SINGLE | `{"ts_code":"000001.SZ","trade_date":"20260807"}` | daily_basic-single-000001 |
| `issue018-t14-priority-source-20260913T092938Z-daily_basic-single-600000` | daily_basic / SINGLE | `{"ts_code":"600000.SH","trade_date":"20260807"}` | daily_basic-single-600000 |
| `issue018-t14-priority-source-20260913T092938Z-daily_basic-range` | daily_basic / RANGE | `{"ts_code":"000001.SZ","start_date":"20260803","end_date":"20260810"}` | daily_basic-range |
| `issue018-t14-priority-source-20260913T092938Z-daily_basic-lower-bound` | daily_basic / RANGE | `{"ts_code":"000001.SZ","start_date":"20260803","end_date":"20260803"}` | daily_basic-lower-bound |
| `issue018-t14-priority-source-20260913T092938Z-daily_basic-upper-bound` | daily_basic / RANGE | `{"ts_code":"000001.SZ","start_date":"20260810","end_date":"20260810"}` | daily_basic-upper-bound |
| `issue018-t14-priority-source-20260913T092938Z-daily_basic-range-600000` | daily_basic / RANGE | `{"ts_code":"600000.SH","start_date":"20260803","end_date":"20260810"}` | daily_basic-range（同窗第二股票） |
| `issue018-t14-priority-source-20260913T092938Z-daily_basic-lower-bound-600000` | daily_basic / RANGE | `{"ts_code":"600000.SH","start_date":"20260803","end_date":"20260803"}` | daily_basic-lower-bound（同窗第二股票） |
| `issue018-t14-priority-source-20260913T092938Z-daily_basic-upper-bound-600000` | daily_basic / RANGE | `{"ts_code":"600000.SH","start_date":"20260810","end_date":"20260810"}` | daily_basic-upper-bound（同窗第二股票） |
| `issue018-t14-priority-source-20260913T092938Z-stk_limit-single-000001` | stk_limit / SINGLE | `{"ts_code":"000001.SZ","trade_date":"20260807"}` | stk_limit-single-000001 |
| `issue018-t14-priority-source-20260913T092938Z-stk_limit-single-600000` | stk_limit / SINGLE | `{"ts_code":"600000.SH","trade_date":"20260807"}` | stk_limit-single-600000 |
| `issue018-t14-priority-source-20260913T092938Z-stk_limit-range` | stk_limit / RANGE | `{"ts_code":"000001.SZ","start_date":"20260803","end_date":"20260810"}` | stk_limit-range |
| `issue018-t14-priority-source-20260913T092938Z-stk_limit-lower-bound` | stk_limit / RANGE | `{"ts_code":"000001.SZ","start_date":"20260803","end_date":"20260803"}` | stk_limit-lower-bound |
| `issue018-t14-priority-source-20260913T092938Z-stk_limit-upper-bound` | stk_limit / RANGE | `{"ts_code":"000001.SZ","start_date":"20260810","end_date":"20260810"}` | stk_limit-upper-bound |
| `issue018-t14-priority-source-20260913T092938Z-stk_limit-range-600000` | stk_limit / RANGE | `{"ts_code":"600000.SH","start_date":"20260803","end_date":"20260810"}` | stk_limit-range（同窗第二股票） |
| `issue018-t14-priority-source-20260913T092938Z-stk_limit-lower-bound-600000` | stk_limit / RANGE | `{"ts_code":"600000.SH","start_date":"20260803","end_date":"20260803"}` | stk_limit-lower-bound（同窗第二股票） |
| `issue018-t14-priority-source-20260913T092938Z-stk_limit-upper-bound-600000` | stk_limit / RANGE | `{"ts_code":"600000.SH","start_date":"20260810","end_date":"20260810"}` | stk_limit-upper-bound（同窗第二股票） |
| `issue018-t14-priority-source-20260913T092938Z-moneyflow-single-000001` | moneyflow / SINGLE | `{"ts_code":"000001.SZ","trade_date":"20260807"}` | moneyflow-single-000001 |
| `issue018-t14-priority-source-20260913T092938Z-moneyflow-single-600000` | moneyflow / SINGLE | `{"ts_code":"600000.SH","trade_date":"20260807"}` | moneyflow-single-600000 |
| `issue018-t14-priority-source-20260913T092938Z-moneyflow-range` | moneyflow / RANGE | `{"ts_code":"000001.SZ","start_date":"20260803","end_date":"20260810"}` | moneyflow-range |
| `issue018-t14-priority-source-20260913T092938Z-moneyflow-lower-bound` | moneyflow / RANGE | `{"ts_code":"000001.SZ","start_date":"20260803","end_date":"20260803"}` | moneyflow-lower-bound |
| `issue018-t14-priority-source-20260913T092938Z-moneyflow-upper-bound` | moneyflow / RANGE | `{"ts_code":"000001.SZ","start_date":"20260810","end_date":"20260810"}` | moneyflow-upper-bound |
| `issue018-t14-priority-source-20260913T092938Z-moneyflow-range-600000` | moneyflow / RANGE | `{"ts_code":"600000.SH","start_date":"20260803","end_date":"20260810"}` | moneyflow-range（同窗第二股票） |
| `issue018-t14-priority-source-20260913T092938Z-moneyflow-lower-bound-600000` | moneyflow / RANGE | `{"ts_code":"600000.SH","start_date":"20260803","end_date":"20260803"}` | moneyflow-lower-bound（同窗第二股票） |
| `issue018-t14-priority-source-20260913T092938Z-moneyflow-upper-bound-600000` | moneyflow / RANGE | `{"ts_code":"600000.SH","start_date":"20260810","end_date":"20260810"}` | moneyflow-upper-bound（同窗第二股票） |
| `issue018-t14-priority-source-20260913T092938Z-margin_detail-single-000001` | margin_detail / SINGLE | `{"ts_code":"000001.SZ","trade_date":"20260807"}` | margin_detail-single-000001 |
| `issue018-t14-priority-source-20260913T092938Z-margin_detail-single-600000` | margin_detail / SINGLE | `{"ts_code":"600000.SH","trade_date":"20260807"}` | margin_detail-single-600000 |
| `issue018-t14-priority-source-20260913T092938Z-margin_detail-range` | margin_detail / RANGE | `{"ts_code":"000001.SZ","start_date":"20260803","end_date":"20260810"}` | margin_detail-range |
| `issue018-t14-priority-source-20260913T092938Z-margin_detail-lower-bound` | margin_detail / RANGE | `{"ts_code":"000001.SZ","start_date":"20260803","end_date":"20260803"}` | margin_detail-lower-bound |
| `issue018-t14-priority-source-20260913T092938Z-margin_detail-upper-bound` | margin_detail / RANGE | `{"ts_code":"000001.SZ","start_date":"20260810","end_date":"20260810"}` | margin_detail-upper-bound |
| `issue018-t14-priority-source-20260913T092938Z-margin_detail-range-600000` | margin_detail / RANGE | `{"ts_code":"600000.SH","start_date":"20260803","end_date":"20260810"}` | margin_detail-range（同窗第二股票） |
| `issue018-t14-priority-source-20260913T092938Z-margin_detail-lower-bound-600000` | margin_detail / RANGE | `{"ts_code":"600000.SH","start_date":"20260803","end_date":"20260803"}` | margin_detail-lower-bound（同窗第二股票） |
| `issue018-t14-priority-source-20260913T092938Z-margin_detail-upper-bound-600000` | margin_detail / RANGE | `{"ts_code":"600000.SH","start_date":"20260810","end_date":"20260810"}` | margin_detail-upper-bound（同窗第二股票） |
| `issue018-t14-priority-source-20260913T092938Z-daily_basic-cross-year` | daily_basic / RANGE | `{"ts_code":"000001.SZ","start_date":"20251229","end_date":"20260105"}` | T13代表场景合同：至少一项最终开放接口跨年；独立新case |

## 初始离线验证

- 独立快照 Node24：`node --test control-plane/e2e/tushare-range-evidence.test.js`，67/67、0失败/跳过、exit0。
- 初始 `-Pacceptance verify` XML汇总1058项、0失败/错误/跳过（含Probe30）；前端与构建同生命周期结果见构建日志。该次运行不是新增源码后的最终门禁。
- T14私有环境、defaults当前用户/普通非symlink/父0700/文件0600、utf8mb4均实测通过。

## 四接口 SOURCE 实际结果

- 2026-09-13T09:29:59.798Z ～ 2026-09-13T09:31:17.440Z，exit0，33/33 PASS、33请求、0 FAILED / EVIDENCE_MISSING / NOT_RUN；cleanup PASS，privateLogSafe=true、inputUnchanged=true。
- 四项各两股票SINGLE、两股票整段与两端均非空并通过字段/股票/日期轴核对；daily_basic跨年样本通过。来源侧没有任务提交、证券写入或SQL事实。
- 实际sourceDiffSha256=`78b78c30971c82a463e5953025b1f4095d9cfd22c0c470ddaf0fc852f07c8eaa`；两包哈希如初始构建表，全部输入运行前后稳定。全33项已追加至唯一JSON索引，原三轮329项内容逐对象比较不变。
- 此结果只允许进行完整性复核及本地候选准备；仍须新固定SINGLE和匹配候选包RANGE TASK/SQL，当前未改任何生产开放标记。

## 完整 SINGLE（执行前登记）

- runId：`issue018-t14-single-20260913T093339Z`，全局唯一caseId固定为该runId + canonical API单样本编号。
- 私有输入 `/private/tmp/issue018-t14-single-20260913T093339Z-plan/cases.json`，普通0600、父0700，SHA-256 `4b4fcc593fff2ccf761b16f45b7eda89e9bcec870fdf0a5db7bd156ee72a9f5b`。固定清单从当前74个canonical SINGLE样本建立，精确参数逐行如下。
- 新独立轮次承接旧两轮SINGLE未完成验收；不重试旧submissionId、不删除15个旧任务。绑定T14新schema及初始重建包，等待固定身份工具离线审查通过并复制到隔离目录后执行。
- 预算：74个真实任务/74次来源/148次records查询；fixture另2次提交/3次查询，不计来源预算。期望40API完整执行、两股票独立归属与第一股票历史保留、SQL业务键摘要与计数、202/Location/完整批次/终态/日志闭合。30分钟/5000来源硬边界、2秒间隔、workers1/retries0，失败停止。PLANNED；本登记不是结果。

| caseId | API | 精确SINGLE参数 | 旧轮次关系 |
| --- | --- | --- | --- |
| `issue018-t14-single-20260913T093339Z-stock_basic-single-1` | stock_basic | `{"ts_code":"000001.SZ","list_status":"L"}` | 对应旧两SINGLE轮同API/股票；新任务身份与新库 |
| `issue018-t14-single-20260913T093339Z-stock_basic-single-2` | stock_basic | `{"ts_code":"600000.SH","list_status":"L"}` | 对应旧两SINGLE轮同API/股票；新任务身份与新库 |
| `issue018-t14-single-20260913T093339Z-stock_company-single-1` | stock_company | `{"ts_code":"000001.SZ","exchange":"SZSE"}` | 对应旧两SINGLE轮同API/股票；新任务身份与新库 |
| `issue018-t14-single-20260913T093339Z-stock_company-single-2` | stock_company | `{"ts_code":"600000.SH","exchange":"SSE"}` | 对应旧两SINGLE轮同API/股票；新任务身份与新库 |
| `issue018-t14-single-20260913T093339Z-income-single-1` | income | `{"ts_code":"000001.SZ","ann_date":"20260807"}` | 对应旧两SINGLE轮同API/股票；新任务身份与新库 |
| `issue018-t14-single-20260913T093339Z-income-single-2` | income | `{"ts_code":"600000.SH","ann_date":"20260807"}` | 对应旧两SINGLE轮同API/股票；新任务身份与新库 |
| `issue018-t14-single-20260913T093339Z-balancesheet-single-1` | balancesheet | `{"ts_code":"000001.SZ","ann_date":"20260807"}` | 对应旧两SINGLE轮同API/股票；新任务身份与新库 |
| `issue018-t14-single-20260913T093339Z-balancesheet-single-2` | balancesheet | `{"ts_code":"600000.SH","ann_date":"20260807"}` | 对应旧两SINGLE轮同API/股票；新任务身份与新库 |
| `issue018-t14-single-20260913T093339Z-cashflow-single-1` | cashflow | `{"ts_code":"000001.SZ","ann_date":"20260807"}` | 对应旧两SINGLE轮同API/股票；新任务身份与新库 |
| `issue018-t14-single-20260913T093339Z-cashflow-single-2` | cashflow | `{"ts_code":"600000.SH","ann_date":"20260807"}` | 对应旧两SINGLE轮同API/股票；新任务身份与新库 |
| `issue018-t14-single-20260913T093339Z-fina_indicator-single-1` | fina_indicator | `{"ts_code":"000001.SZ","ann_date":"20260807"}` | 对应旧两SINGLE轮同API/股票；新任务身份与新库 |
| `issue018-t14-single-20260913T093339Z-fina_indicator-single-2` | fina_indicator | `{"ts_code":"600000.SH","ann_date":"20260807"}` | 对应旧两SINGLE轮同API/股票；新任务身份与新库 |
| `issue018-t14-single-20260913T093339Z-fina_audit-single-1` | fina_audit | `{"ts_code":"000001.SZ","ann_date":"20260807"}` | 对应旧两SINGLE轮同API/股票；新任务身份与新库 |
| `issue018-t14-single-20260913T093339Z-fina_audit-single-2` | fina_audit | `{"ts_code":"600000.SH","ann_date":"20260807"}` | 对应旧两SINGLE轮同API/股票；新任务身份与新库 |
| `issue018-t14-single-20260913T093339Z-fina_mainbz-single-1` | fina_mainbz | `{"ts_code":"000001.SZ"}` | 对应旧两SINGLE轮同API/股票；新任务身份与新库 |
| `issue018-t14-single-20260913T093339Z-fina_mainbz-single-2` | fina_mainbz | `{"ts_code":"600000.SH"}` | 对应旧两SINGLE轮同API/股票；新任务身份与新库 |
| `issue018-t14-single-20260913T093339Z-stk_rewards-single-1` | stk_rewards | `{"ts_code":"000001.SZ"}` | 对应旧两SINGLE轮同API/股票；新任务身份与新库 |
| `issue018-t14-single-20260913T093339Z-stk_rewards-single-2` | stk_rewards | `{"ts_code":"600000.SH"}` | 对应旧两SINGLE轮同API/股票；新任务身份与新库 |
| `issue018-t14-single-20260913T093339Z-stk_holdernumber-single-1` | stk_holdernumber | `{"ts_code":"000001.SZ"}` | 对应旧两SINGLE轮同API/股票；新任务身份与新库 |
| `issue018-t14-single-20260913T093339Z-stk_holdernumber-single-2` | stk_holdernumber | `{"ts_code":"600000.SH"}` | 对应旧两SINGLE轮同API/股票；新任务身份与新库 |
| `issue018-t14-single-20260913T093339Z-trade_cal-single-1` | trade_cal | `{"exchange":"SSE","start_date":"20260807","end_date":"20260807"}` | 对应旧两SINGLE轮同API/股票；新任务身份与新库 |
| `issue018-t14-single-20260913T093339Z-margin-single-1` | margin | `{"exchange_id":"SSE","trade_date":"20260807"}` | 对应旧两SINGLE轮同API/股票；新任务身份与新库 |
| `issue018-t14-single-20260913T093339Z-daily-single-1` | daily | `{"ts_code":"000001.SZ","trade_date":"20260807"}` | 对应旧两SINGLE轮同API/股票；新任务身份与新库 |
| `issue018-t14-single-20260913T093339Z-daily-single-2` | daily | `{"ts_code":"600000.SH","trade_date":"20260807"}` | 对应旧两SINGLE轮同API/股票；新任务身份与新库 |
| `issue018-t14-single-20260913T093339Z-weekly-single-1` | weekly | `{"ts_code":"000001.SZ","trade_date":"20260807"}` | 对应旧两SINGLE轮同API/股票；新任务身份与新库 |
| `issue018-t14-single-20260913T093339Z-weekly-single-2` | weekly | `{"ts_code":"600000.SH","trade_date":"20260807"}` | 对应旧两SINGLE轮同API/股票；新任务身份与新库 |
| `issue018-t14-single-20260913T093339Z-monthly-single-1` | monthly | `{"ts_code":"000001.SZ","trade_date":"20260807"}` | 对应旧两SINGLE轮同API/股票；新任务身份与新库 |
| `issue018-t14-single-20260913T093339Z-monthly-single-2` | monthly | `{"ts_code":"600000.SH","trade_date":"20260807"}` | 对应旧两SINGLE轮同API/股票；新任务身份与新库 |
| `issue018-t14-single-20260913T093339Z-adj_factor-single-1` | adj_factor | `{"ts_code":"000001.SZ","trade_date":"20260807"}` | 对应旧两SINGLE轮同API/股票；新任务身份与新库 |
| `issue018-t14-single-20260913T093339Z-adj_factor-single-2` | adj_factor | `{"ts_code":"600000.SH","trade_date":"20260807"}` | 对应旧两SINGLE轮同API/股票；新任务身份与新库 |
| `issue018-t14-single-20260913T093339Z-suspend_d-single-1` | suspend_d | `{"ts_code":"000001.SZ","trade_date":"20260807"}` | 对应旧两SINGLE轮同API/股票；新任务身份与新库 |
| `issue018-t14-single-20260913T093339Z-suspend_d-single-2` | suspend_d | `{"ts_code":"600000.SH","trade_date":"20260807"}` | 对应旧两SINGLE轮同API/股票；新任务身份与新库 |
| `issue018-t14-single-20260913T093339Z-daily_basic-single-1` | daily_basic | `{"ts_code":"000001.SZ","trade_date":"20260807"}` | 对应旧两SINGLE轮同API/股票；新任务身份与新库 |
| `issue018-t14-single-20260913T093339Z-daily_basic-single-2` | daily_basic | `{"ts_code":"600000.SH","trade_date":"20260807"}` | 对应旧两SINGLE轮同API/股票；新任务身份与新库 |
| `issue018-t14-single-20260913T093339Z-moneyflow-single-1` | moneyflow | `{"ts_code":"000001.SZ","trade_date":"20260807"}` | 对应旧两SINGLE轮同API/股票；新任务身份与新库 |
| `issue018-t14-single-20260913T093339Z-moneyflow-single-2` | moneyflow | `{"ts_code":"600000.SH","trade_date":"20260807"}` | 对应旧两SINGLE轮同API/股票；新任务身份与新库 |
| `issue018-t14-single-20260913T093339Z-stk_limit-single-1` | stk_limit | `{"ts_code":"000001.SZ","trade_date":"20260807"}` | 对应旧两SINGLE轮同API/股票；新任务身份与新库 |
| `issue018-t14-single-20260913T093339Z-stk_limit-single-2` | stk_limit | `{"ts_code":"600000.SH","trade_date":"20260807"}` | 对应旧两SINGLE轮同API/股票；新任务身份与新库 |
| `issue018-t14-single-20260913T093339Z-top_list-single-1` | top_list | `{"ts_code":"000001.SZ","trade_date":"20260807"}` | 对应旧两SINGLE轮同API/股票；新任务身份与新库 |
| `issue018-t14-single-20260913T093339Z-top_list-single-2` | top_list | `{"ts_code":"600000.SH","trade_date":"20260807"}` | 对应旧两SINGLE轮同API/股票；新任务身份与新库 |
| `issue018-t14-single-20260913T093339Z-margin_detail-single-1` | margin_detail | `{"ts_code":"000001.SZ","trade_date":"20260807"}` | 对应旧两SINGLE轮同API/股票；新任务身份与新库 |
| `issue018-t14-single-20260913T093339Z-margin_detail-single-2` | margin_detail | `{"ts_code":"600000.SH","trade_date":"20260807"}` | 对应旧两SINGLE轮同API/股票；新任务身份与新库 |
| `issue018-t14-single-20260913T093339Z-block_trade-single-1` | block_trade | `{"ts_code":"000001.SZ","trade_date":"20260807"}` | 对应旧两SINGLE轮同API/股票；新任务身份与新库 |
| `issue018-t14-single-20260913T093339Z-block_trade-single-2` | block_trade | `{"ts_code":"600000.SH","trade_date":"20260807"}` | 对应旧两SINGLE轮同API/股票；新任务身份与新库 |
| `issue018-t14-single-20260913T093339Z-slb_len-single-1` | slb_len | `{"trade_date":"20260807"}` | 对应旧两SINGLE轮同API/股票；新任务身份与新库 |
| `issue018-t14-single-20260913T093339Z-slb_sec-single-1` | slb_sec | `{"ts_code":"000001.SZ","trade_date":"20260807"}` | 对应旧两SINGLE轮同API/股票；新任务身份与新库 |
| `issue018-t14-single-20260913T093339Z-slb_sec-single-2` | slb_sec | `{"ts_code":"600000.SH","trade_date":"20260807"}` | 对应旧两SINGLE轮同API/股票；新任务身份与新库 |
| `issue018-t14-single-20260913T093339Z-slb_sec_detail-single-1` | slb_sec_detail | `{"ts_code":"000001.SZ","trade_date":"20260807"}` | 对应旧两SINGLE轮同API/股票；新任务身份与新库 |
| `issue018-t14-single-20260913T093339Z-slb_sec_detail-single-2` | slb_sec_detail | `{"ts_code":"600000.SH","trade_date":"20260807"}` | 对应旧两SINGLE轮同API/股票；新任务身份与新库 |
| `issue018-t14-single-20260913T093339Z-forecast-single-1` | forecast | `{"ts_code":"000001.SZ","ann_date":"20260807"}` | 对应旧两SINGLE轮同API/股票；新任务身份与新库 |
| `issue018-t14-single-20260913T093339Z-forecast-single-2` | forecast | `{"ts_code":"600000.SH","ann_date":"20260807"}` | 对应旧两SINGLE轮同API/股票；新任务身份与新库 |
| `issue018-t14-single-20260913T093339Z-express-single-1` | express | `{"ts_code":"000001.SZ","ann_date":"20260807"}` | 对应旧两SINGLE轮同API/股票；新任务身份与新库 |
| `issue018-t14-single-20260913T093339Z-express-single-2` | express | `{"ts_code":"600000.SH","ann_date":"20260807"}` | 对应旧两SINGLE轮同API/股票；新任务身份与新库 |
| `issue018-t14-single-20260913T093339Z-dividend-single-1` | dividend | `{"ts_code":"000001.SZ","ann_date":"20260807"}` | 对应旧两SINGLE轮同API/股票；新任务身份与新库 |
| `issue018-t14-single-20260913T093339Z-dividend-single-2` | dividend | `{"ts_code":"600000.SH","ann_date":"20260807"}` | 对应旧两SINGLE轮同API/股票；新任务身份与新库 |
| `issue018-t14-single-20260913T093339Z-disclosure_date-single-1` | disclosure_date | `{"ts_code":"000001.SZ","ann_date":"20260807"}` | 对应旧两SINGLE轮同API/股票；新任务身份与新库 |
| `issue018-t14-single-20260913T093339Z-disclosure_date-single-2` | disclosure_date | `{"ts_code":"600000.SH","ann_date":"20260807"}` | 对应旧两SINGLE轮同API/股票；新任务身份与新库 |
| `issue018-t14-single-20260913T093339Z-repurchase-single-1` | repurchase | `{"ann_date":"20260807"}` | 对应旧两SINGLE轮同API/股票；新任务身份与新库 |
| `issue018-t14-single-20260913T093339Z-stk_holdertrade-single-1` | stk_holdertrade | `{"ts_code":"000001.SZ","ann_date":"20260807"}` | 对应旧两SINGLE轮同API/股票；新任务身份与新库 |
| `issue018-t14-single-20260913T093339Z-stk_holdertrade-single-2` | stk_holdertrade | `{"ts_code":"600000.SH","ann_date":"20260807"}` | 对应旧两SINGLE轮同API/股票；新任务身份与新库 |
| `issue018-t14-single-20260913T093339Z-top10_holders-single-1` | top10_holders | `{"ts_code":"000001.SZ","ann_date":"20260807"}` | 对应旧两SINGLE轮同API/股票；新任务身份与新库 |
| `issue018-t14-single-20260913T093339Z-top10_holders-single-2` | top10_holders | `{"ts_code":"600000.SH","ann_date":"20260807"}` | 对应旧两SINGLE轮同API/股票；新任务身份与新库 |
| `issue018-t14-single-20260913T093339Z-top10_floatholders-single-1` | top10_floatholders | `{"ts_code":"000001.SZ","ann_date":"20260807"}` | 对应旧两SINGLE轮同API/股票；新任务身份与新库 |
| `issue018-t14-single-20260913T093339Z-top10_floatholders-single-2` | top10_floatholders | `{"ts_code":"600000.SH","ann_date":"20260807"}` | 对应旧两SINGLE轮同API/股票；新任务身份与新库 |
| `issue018-t14-single-20260913T093339Z-new_share-single-1` | new_share | `{"start_date":"20260807","end_date":"20260807"}` | 对应旧两SINGLE轮同API/股票；新任务身份与新库 |
| `issue018-t14-single-20260913T093339Z-stk_managers-single-1` | stk_managers | `{"ts_code":"000001.SZ"}` | 对应旧两SINGLE轮同API/股票；新任务身份与新库 |
| `issue018-t14-single-20260913T093339Z-stk_managers-single-2` | stk_managers | `{"ts_code":"600000.SH"}` | 对应旧两SINGLE轮同API/股票；新任务身份与新库 |
| `issue018-t14-single-20260913T093339Z-pledge_stat-single-1` | pledge_stat | `{"ts_code":"000001.SZ"}` | 对应旧两SINGLE轮同API/股票；新任务身份与新库 |
| `issue018-t14-single-20260913T093339Z-pledge_stat-single-2` | pledge_stat | `{"ts_code":"600000.SH"}` | 对应旧两SINGLE轮同API/股票；新任务身份与新库 |
| `issue018-t14-single-20260913T093339Z-pledge_detail-single-1` | pledge_detail | `{"ts_code":"000001.SZ"}` | 对应旧两SINGLE轮同API/股票；新任务身份与新库 |
| `issue018-t14-single-20260913T093339Z-pledge_detail-single-2` | pledge_detail | `{"ts_code":"600000.SH"}` | 对应旧两SINGLE轮同API/股票；新任务身份与新库 |
| `issue018-t14-single-20260913T093339Z-index_classify-single-1` | index_classify | `{}` | 对应旧两SINGLE轮同API/股票；新任务身份与新库 |
| `issue018-t14-single-20260913T093339Z-index_member_all-single-1` | index_member_all | `{"ts_code":"000001.SZ"}` | 对应旧两SINGLE轮同API/股票；新任务身份与新库 |
| `issue018-t14-single-20260913T093339Z-index_member_all-single-2` | index_member_all | `{"ts_code":"600000.SH"}` | 对应旧两SINGLE轮同API/股票；新任务身份与新库 |

## 执行前工具核对

固定SINGLE输入入口按设计补充修复，沿用原环境变量；独立离线74/74与两种40API发现已通过，等待限定审查。额外发现现有SQL defaults校验仅允许旧7行，不能读取本设计显式utf8mb4的8行配置；在真实SINGLE启动前修复并测试兼容，未消费本轮任何来源/任务请求。

## 历史与月线判别 SOURCE（执行前登记）

- runId `issue018-t14-history-source-20260913T093708Z`；私有 `/private/tmp/issue018-t14-history-source-20260913T093708Z/cases.json`（0700/0600、普通非symlink），SHA-256 `6966169a08a2ffbfa61f393cce0b66631271cfbc2046f0bcd2556e110e265074`。
- 依据 [公开补证](ISSUE-018-T14-official-evidence.md) §1–2、§7：证监会业务暂停前且Tushare直接给出目标股的20240620历史例，monthly样例20180930与官方SSE休市/前一交易日20180928对照。
- 固定6项，预计6次请求，仍有5000次/30分钟硬边界、2秒间隔、失败停止；与所有账户TASK串行。PLANNED，未发请求。预期日期/股票/完整自然日日历及实际可读性，不能把单月/单日结果推成通用完整历史。
- 关系：monthly旧2026样本没有判别力，独立历史组补证；slb两项旧未来期空样本保留，新日来自官网既定示例；trade_cal新历史参照不替代旧BSE失败与三NOT_RUN。新case不回填旧观察，不换日期重试。

| caseId | API | 精确参数 | 预期观察 |
| --- | --- | --- | --- |
| `issue018-t14-history-source-20260913T093708Z-monthly-201809-whole` | monthly | `{"ts_code":"000001.SZ","start_date":"20180901","end_date":"20180930"}` | 股票/日期轴/字段与实际行数；空或矛盾如实保存 |
| `issue018-t14-history-source-20260913T093708Z-monthly-201809-last-open` | monthly | `{"ts_code":"000001.SZ","start_date":"20180928","end_date":"20180928"}` | 股票/日期轴/字段与实际行数；空或矛盾如实保存 |
| `issue018-t14-history-source-20260913T093708Z-monthly-201809-natural-end` | monthly | `{"ts_code":"000001.SZ","start_date":"20180930","end_date":"20180930"}` | 股票/日期轴/字段与实际行数；空或矛盾如实保存 |
| `issue018-t14-history-source-20260913T093708Z-trade-cal-201809-monthly-reference` | trade_cal | `{"exchange":"SSE","start_date":"20180928","end_date":"20180930"}` | 股票/日期轴/字段与实际行数；空或矛盾如实保存 |
| `issue018-t14-history-source-20260913T093708Z-slb-sec-20240620-history` | slb_sec | `{"ts_code":"000001.SZ","start_date":"20240620","end_date":"20240620"}` | 股票/日期轴/字段与实际行数；空或矛盾如实保存 |
| `issue018-t14-history-source-20260913T093708Z-slb-sec-detail-20240620-history` | slb_sec_detail | `{"ts_code":"000001.SZ","start_date":"20240620","end_date":"20240620"}` | 股票/日期轴/字段与实际行数；空或矛盾如实保存 |

## 历史与月线 SOURCE 实际结果

- 2026-09-13T09:37:21.367Z ～ 2026-09-13T09:37:34.737Z，exit0/cleanup PASS，6个固定case全部执行、6请求；输入稳定。状态分布 {'PASS': 5, 'EVIDENCE_MISSING': 1}。
- 全run原样追加至唯一索引，原4轮不变。以下只是实际来源观察；EVIDENCE_MISSING保留，不把exit0等同于全部样本充分。

| caseId | 状态 | 行数 | 安全复查事实 |
| --- | --- | --- | --- |
| `issue018-t14-history-source-20260913T093708Z-monthly-201809-whole` | PASS | 1 | SOURCE validationStatus=PASS; parameter, envelope and stock scope checks; completeness UNKNOWN; actualDateColumn=trade_date; datePredicate=closed interval; actualDates=20180928; uniqueDateCount=1; min=20180928; max=20180928; candidateRowLimit=4500; candidateLimitReached=false |
| `issue018-t14-history-source-20260913T093708Z-monthly-201809-last-open` | PASS | 1 | SOURCE validationStatus=PASS; parameter, envelope and stock scope checks; completeness UNKNOWN; actualDateColumn=trade_date; datePredicate=closed interval; actualDates=20180928; uniqueDateCount=1; min=20180928; max=20180928; candidateRowLimit=4500; candidateLimitReached=false |
| `issue018-t14-history-source-20260913T093708Z-monthly-201809-natural-end` | EVIDENCE_MISSING | 0 | SOURCE validationStatus=EVIDENCE_MISSING; parameter, envelope and stock scope checks; completeness UNKNOWN; actualDateColumn=trade_date; datePredicate=closed interval; actualDates=; uniqueDateCount=0; min=none; max=none; candidateRowLimit=4500; candidateLimitReached=false |
| `issue018-t14-history-source-20260913T093708Z-trade-cal-201809-monthly-reference` | PASS | 3 | SOURCE validationStatus=PASS; parameter, envelope and stock scope checks; completeness UNKNOWN; actualDateColumn=cal_date; datePredicate=closed interval; actualDates=20180928,20180929,20180930; uniqueDateCount=3; min=20180928; max=20180930; candidateRowLimit=UNKNOWN; candidateLimitReached=false; calendarOpenDayCount=1; closedCalendar=false |
| `issue018-t14-history-source-20260913T093708Z-slb-sec-20240620-history` | PASS | 1 | SOURCE validationStatus=PASS; parameter, envelope and stock scope checks; completeness UNKNOWN; actualDateColumn=trade_date; datePredicate=closed interval; actualDates=20240620; uniqueDateCount=1; min=20240620; max=20240620; candidateRowLimit=5000; candidateLimitReached=false |
| `issue018-t14-history-source-20260913T093708Z-slb-sec-detail-20240620-history` | PASS | 1 | SOURCE validationStatus=PASS; parameter, envelope and stock scope checks; completeness UNKNOWN; actualDateColumn=trade_date; datePredicate=closed interval; actualDates=20240620; uniqueDateCount=1; min=20240620; max=20240620; candidateRowLimit=5000; candidateLimitReached=false |

## SINGLE 启动前最终核对

固定计划及utf8mb4 defaults修复经限定独立审查通过，复制三工具文件至独立快照并逐字比较；该快照Node77/77、0失败/跳过，固定74项计划发现40 tests/1 file，exit0。新SOURCE两轮已全部结束，才启动登记SINGLE；包文件未改变，源码指纹按新工具字节重新计算，运行中不修改该快照。

## 日期轴/多股票投影与旧 NOT_RUN 补证 SOURCE（执行前登记）

- runId `issue018-t14-representative-source-20260913T094124Z`；私有 `/private/tmp/issue018-t14-representative-source-20260913T094124Z/cases.json` 0700/0600，SHA-256 `d47be64c51d11928c9736b493057cac3b02142db7d399a0a3495b6b8178e0671`，8项固定样本。SINGLE结束后另轮串行执行，不改正在运行的快照。
- 目的：T13最后离线修复的income/fina_indicator同一行日期比较、repurchase证券数量投影尚无真实证据，按原矩阵固定窗口取得这些新观察；完整性仍UNKNOWN，不用少量样本替代合同。两股票分开。另实际补执行原SOURCE尾部三个NOT_RUN的原参数，保留原未执行事实，不给原case填结果。
- 预期最多约14次来源请求（两top_list含完整日历及开市日），硬预算5000/30分钟、2秒间隔、失败停止；不强制非空。BSE日历原失败仍根据原失败及新增官方枚举调查保留，不自动重复已失败请求或替换SSE。
- 状态PLANNED，未执行；所有任务/SQL字段为null。精确参数与旧关系如下。

| caseId | API | 精确参数 | 旧case关系 / 新观察 |
| --- | --- | --- | --- |
| `issue018-t14-representative-source-20260913T094124Z-income-range` | income | `{"ts_code":"000001.SZ","start_date":"20260801","end_date":"20260831"}` | income-range |
| `issue018-t14-representative-source-20260913T094124Z-income-range-600000` | income | `{"ts_code":"600000.SH","start_date":"20260801","end_date":"20260831"}` | income-range；T13两股票合同 |
| `issue018-t14-representative-source-20260913T094124Z-fina_indicator-range` | fina_indicator | `{"ts_code":"000001.SZ","start_date":"20250101","end_date":"20251231"}` | fina_indicator-range |
| `issue018-t14-representative-source-20260913T094124Z-fina_indicator-range-600000` | fina_indicator | `{"ts_code":"600000.SH","start_date":"20250101","end_date":"20251231"}` | fina_indicator-range；T13两股票合同 |
| `issue018-t14-representative-source-20260913T094124Z-repurchase-range` | repurchase | `{"start_date":"20260801","end_date":"20260831"}` | repurchase-range |
| `issue018-t14-representative-source-20260913T094124Z-top_list-closed-calendar` | top_list | `{"ts_code":"000001.SZ","start_date":"20260808","end_date":"20260809"}` | top_list-closed-calendar |
| `issue018-t14-representative-source-20260913T094124Z-top_list-sh-calendar` | top_list | `{"ts_code":"600000.SH","start_date":"20260803","end_date":"20260810"}` | top_list-sh-calendar |
| `issue018-t14-representative-source-20260913T094124Z-margin-bse-direct` | margin | `{"exchange_id":"BSE","start_date":"20260803","end_date":"20260810"}` | margin-bse-direct |

## 四接口 RANGE TASK（执行前登记）

- runId `issue018-t14-priority-range-20260913T094551Z`；私有 `/private/tmp/issue018-t14-priority-range-20260913T094551Z-plan/cases.json` 0700/0600，SHA-256 `1d76f05054f675799d96a97e3727a8061ad6fd566376184faee419deb635db9c`；全25项与新有效SOURCE精确参数一一对应，daily_basic7项、其余各6项；没有只选整段或删掉端点。
- 独立新MySQL8.4.6 schema已准备，初始0表，最小单库权限，环境目录 `/private/tmp/issue018-t14-range-db-20260913T094505Z`；所有旧库及本次SINGLE库保留。产物目录 `/private/tmp/issue018-t14-range-20260913T094505Z`。
- 预期25任务/50 records查询，fixture另2提交/3查询；每样本核对202/Location、候选v2能力、全部批次/覆盖、SQL业务键/归属/其他股票保留、source/insert/update和日志；端点任务对已入库区间的实际update计数应如实保留。来源预计25次、最多5000次/30分钟、2秒间隔、失败停止，不自动重试。
- PLANNED：候选四项须完成代码/独立预期与审查，重建绑定新生产/验收包，完整证据索引私有快照在开始前定稿，才执行本轮。最终AVAILABLE以本轮实际结果及干净运行身份判断。

| TASK caseId | 精确参数 | 对应有效SOURCE caseId |
| --- | --- | --- |
| `issue018-t14-priority-range-20260913T094551Z-daily_basic-range` | `{"ts_code":"000001.SZ","start_date":"20260803","end_date":"20260810"}` | `issue018-t14-priority-source-20260913T092938Z-daily_basic-range` |
| `issue018-t14-priority-range-20260913T094551Z-daily_basic-lower-bound` | `{"ts_code":"000001.SZ","start_date":"20260803","end_date":"20260803"}` | `issue018-t14-priority-source-20260913T092938Z-daily_basic-lower-bound` |
| `issue018-t14-priority-range-20260913T094551Z-daily_basic-upper-bound` | `{"ts_code":"000001.SZ","start_date":"20260810","end_date":"20260810"}` | `issue018-t14-priority-source-20260913T092938Z-daily_basic-upper-bound` |
| `issue018-t14-priority-range-20260913T094551Z-daily_basic-range-600000` | `{"ts_code":"600000.SH","start_date":"20260803","end_date":"20260810"}` | `issue018-t14-priority-source-20260913T092938Z-daily_basic-range-600000` |
| `issue018-t14-priority-range-20260913T094551Z-daily_basic-lower-bound-600000` | `{"ts_code":"600000.SH","start_date":"20260803","end_date":"20260803"}` | `issue018-t14-priority-source-20260913T092938Z-daily_basic-lower-bound-600000` |
| `issue018-t14-priority-range-20260913T094551Z-daily_basic-upper-bound-600000` | `{"ts_code":"600000.SH","start_date":"20260810","end_date":"20260810"}` | `issue018-t14-priority-source-20260913T092938Z-daily_basic-upper-bound-600000` |
| `issue018-t14-priority-range-20260913T094551Z-stk_limit-range` | `{"ts_code":"000001.SZ","start_date":"20260803","end_date":"20260810"}` | `issue018-t14-priority-source-20260913T092938Z-stk_limit-range` |
| `issue018-t14-priority-range-20260913T094551Z-stk_limit-lower-bound` | `{"ts_code":"000001.SZ","start_date":"20260803","end_date":"20260803"}` | `issue018-t14-priority-source-20260913T092938Z-stk_limit-lower-bound` |
| `issue018-t14-priority-range-20260913T094551Z-stk_limit-upper-bound` | `{"ts_code":"000001.SZ","start_date":"20260810","end_date":"20260810"}` | `issue018-t14-priority-source-20260913T092938Z-stk_limit-upper-bound` |
| `issue018-t14-priority-range-20260913T094551Z-stk_limit-range-600000` | `{"ts_code":"600000.SH","start_date":"20260803","end_date":"20260810"}` | `issue018-t14-priority-source-20260913T092938Z-stk_limit-range-600000` |
| `issue018-t14-priority-range-20260913T094551Z-stk_limit-lower-bound-600000` | `{"ts_code":"600000.SH","start_date":"20260803","end_date":"20260803"}` | `issue018-t14-priority-source-20260913T092938Z-stk_limit-lower-bound-600000` |
| `issue018-t14-priority-range-20260913T094551Z-stk_limit-upper-bound-600000` | `{"ts_code":"600000.SH","start_date":"20260810","end_date":"20260810"}` | `issue018-t14-priority-source-20260913T092938Z-stk_limit-upper-bound-600000` |
| `issue018-t14-priority-range-20260913T094551Z-moneyflow-range` | `{"ts_code":"000001.SZ","start_date":"20260803","end_date":"20260810"}` | `issue018-t14-priority-source-20260913T092938Z-moneyflow-range` |
| `issue018-t14-priority-range-20260913T094551Z-moneyflow-lower-bound` | `{"ts_code":"000001.SZ","start_date":"20260803","end_date":"20260803"}` | `issue018-t14-priority-source-20260913T092938Z-moneyflow-lower-bound` |
| `issue018-t14-priority-range-20260913T094551Z-moneyflow-upper-bound` | `{"ts_code":"000001.SZ","start_date":"20260810","end_date":"20260810"}` | `issue018-t14-priority-source-20260913T092938Z-moneyflow-upper-bound` |
| `issue018-t14-priority-range-20260913T094551Z-moneyflow-range-600000` | `{"ts_code":"600000.SH","start_date":"20260803","end_date":"20260810"}` | `issue018-t14-priority-source-20260913T092938Z-moneyflow-range-600000` |
| `issue018-t14-priority-range-20260913T094551Z-moneyflow-lower-bound-600000` | `{"ts_code":"600000.SH","start_date":"20260803","end_date":"20260803"}` | `issue018-t14-priority-source-20260913T092938Z-moneyflow-lower-bound-600000` |
| `issue018-t14-priority-range-20260913T094551Z-moneyflow-upper-bound-600000` | `{"ts_code":"600000.SH","start_date":"20260810","end_date":"20260810"}` | `issue018-t14-priority-source-20260913T092938Z-moneyflow-upper-bound-600000` |
| `issue018-t14-priority-range-20260913T094551Z-margin_detail-range` | `{"ts_code":"000001.SZ","start_date":"20260803","end_date":"20260810"}` | `issue018-t14-priority-source-20260913T092938Z-margin_detail-range` |
| `issue018-t14-priority-range-20260913T094551Z-margin_detail-lower-bound` | `{"ts_code":"000001.SZ","start_date":"20260803","end_date":"20260803"}` | `issue018-t14-priority-source-20260913T092938Z-margin_detail-lower-bound` |
| `issue018-t14-priority-range-20260913T094551Z-margin_detail-upper-bound` | `{"ts_code":"000001.SZ","start_date":"20260810","end_date":"20260810"}` | `issue018-t14-priority-source-20260913T092938Z-margin_detail-upper-bound` |
| `issue018-t14-priority-range-20260913T094551Z-margin_detail-range-600000` | `{"ts_code":"600000.SH","start_date":"20260803","end_date":"20260810"}` | `issue018-t14-priority-source-20260913T092938Z-margin_detail-range-600000` |
| `issue018-t14-priority-range-20260913T094551Z-margin_detail-lower-bound-600000` | `{"ts_code":"600000.SH","start_date":"20260803","end_date":"20260803"}` | `issue018-t14-priority-source-20260913T092938Z-margin_detail-lower-bound-600000` |
| `issue018-t14-priority-range-20260913T094551Z-margin_detail-upper-bound-600000` | `{"ts_code":"600000.SH","start_date":"20260810","end_date":"20260810"}` | `issue018-t14-priority-source-20260913T092938Z-margin_detail-upper-bound-600000` |
| `issue018-t14-priority-range-20260913T094551Z-daily_basic-cross-year` | `{"ts_code":"000001.SZ","start_date":"20251229","end_date":"20260105"}` | `issue018-t14-priority-source-20260913T092938Z-daily_basic-cross-year` |

## 完整 SINGLE 实际结果

- runId `issue018-t14-single-20260913T093339Z`，2026-09-13T09:38:45.087Z ～ 2026-09-13T09:48:21.950Z，40/40 API、74/74 TASK PASS，exit0/cleanup PASS；wrapper privateLogSafe/inputUnchanged/ownedProcessReaped全部true。
- 实际74个Tushare任务提交/74次来源/148次records查询；fixture另2任务/3查询/2受控来源，合计76条accepted/started/finished/batch事件逐一关联；workers1/retries0/间隔2000ms。无未运行或失败样本。
- SQL实际证券source5265/insert5265/update0。每股票任务两次records与前后SQL摘要一致，第二股票写入不改变第一股票历史；未以任务计数代替SQL。空SINGLE允许成功，此结果不承诺完整历史。所有任务均SUCCEEDED。
- 真实preconditions确认新schema初始0表，启动后Flyway1～8全部成功、52张业务/任务表（history另计）。自有JVM及浏览器已停止，数据库保留以便复查。没有删除旧15任务。
- 本轮全74个case原样追加至唯一索引，原五轮不变。接口当前TASK引用改为本次完整有效SINGLE（每API全部当前样本）；旧两轮TASK仍完整保存在runs，历史关系见本登记和T13原报告，不将旧PASS拼入新轮。
- sourceDiffSha256 `6cc7d464de436e7283c5669616bb961da0431544b0211f48bbd6ca51103c395e`；acceptance SHA `60bcf60adb8025dba2792ae8d2edb61121b9ff0745be2184b6462be56fcd42e7`；fixed plan SHA `4b4fcc593fff2ccf761b16f45b7eda89e9bcec870fdf0a5db7bd156ee72a9f5b`；spec SHA `ba27393b114444bc8dfbf777f21580571ac026557cad56c7ff2e682cef79acba`。包身份是初始重建包，尚不是后续四项v2候选包。

| API | 样本数 | SOURCE / INSERT / UPDATE | TASK / 查询 / SQL |
| --- | --- | --- | --- |
| stock_basic | 2 | 2 / 2 / 0 | 全部PASS，股票/业务键/摘要及历史保留检查通过 |
| stock_company | 2 | 2 / 2 / 0 | 全部PASS，股票/业务键/摘要及历史保留检查通过 |
| income | 2 | 0 / 0 / 0 | 全部PASS，股票/业务键/摘要及历史保留检查通过 |
| balancesheet | 2 | 0 / 0 / 0 | 全部PASS，股票/业务键/摘要及历史保留检查通过 |
| cashflow | 2 | 0 / 0 / 0 | 全部PASS，股票/业务键/摘要及历史保留检查通过 |
| fina_indicator | 2 | 0 / 0 / 0 | 全部PASS，股票/业务键/摘要及历史保留检查通过 |
| fina_audit | 2 | 0 / 0 / 0 | 全部PASS，股票/业务键/摘要及历史保留检查通过 |
| fina_mainbz | 2 | 300 / 300 / 0 | 全部PASS，股票/业务键/摘要及历史保留检查通过 |
| stk_rewards | 2 | 2494 / 2494 / 0 | 全部PASS，股票/业务键/摘要及历史保留检查通过 |
| stk_holdernumber | 2 | 278 / 278 / 0 | 全部PASS，股票/业务键/摘要及历史保留检查通过 |
| trade_cal | 1 | 1 / 1 / 0 | 全部PASS，股票/业务键/摘要及历史保留检查通过 |
| margin | 1 | 1 / 1 / 0 | 全部PASS，股票/业务键/摘要及历史保留检查通过 |
| daily | 2 | 2 / 2 / 0 | 全部PASS，股票/业务键/摘要及历史保留检查通过 |
| weekly | 2 | 2 / 2 / 0 | 全部PASS，股票/业务键/摘要及历史保留检查通过 |
| monthly | 2 | 0 / 0 / 0 | 全部PASS，股票/业务键/摘要及历史保留检查通过 |
| adj_factor | 2 | 2 / 2 / 0 | 全部PASS，股票/业务键/摘要及历史保留检查通过 |
| suspend_d | 2 | 0 / 0 / 0 | 全部PASS，股票/业务键/摘要及历史保留检查通过 |
| daily_basic | 2 | 2 / 2 / 0 | 全部PASS，股票/业务键/摘要及历史保留检查通过 |
| moneyflow | 2 | 2 / 2 / 0 | 全部PASS，股票/业务键/摘要及历史保留检查通过 |
| stk_limit | 2 | 2 / 2 / 0 | 全部PASS，股票/业务键/摘要及历史保留检查通过 |
| top_list | 2 | 0 / 0 / 0 | 全部PASS，股票/业务键/摘要及历史保留检查通过 |
| margin_detail | 2 | 2 / 2 / 0 | 全部PASS，股票/业务键/摘要及历史保留检查通过 |
| block_trade | 2 | 0 / 0 / 0 | 全部PASS，股票/业务键/摘要及历史保留检查通过 |
| slb_len | 1 | 0 / 0 / 0 | 全部PASS，股票/业务键/摘要及历史保留检查通过 |
| slb_sec | 2 | 0 / 0 / 0 | 全部PASS，股票/业务键/摘要及历史保留检查通过 |
| slb_sec_detail | 2 | 0 / 0 / 0 | 全部PASS，股票/业务键/摘要及历史保留检查通过 |
| forecast | 2 | 0 / 0 / 0 | 全部PASS，股票/业务键/摘要及历史保留检查通过 |
| express | 2 | 0 / 0 / 0 | 全部PASS，股票/业务键/摘要及历史保留检查通过 |
| dividend | 2 | 0 / 0 / 0 | 全部PASS，股票/业务键/摘要及历史保留检查通过 |
| disclosure_date | 2 | 0 / 0 / 0 | 全部PASS，股票/业务键/摘要及历史保留检查通过 |
| repurchase | 1 | 27 / 27 / 0 | 全部PASS，股票/业务键/摘要及历史保留检查通过 |
| stk_holdertrade | 2 | 0 / 0 / 0 | 全部PASS，股票/业务键/摘要及历史保留检查通过 |
| top10_holders | 2 | 0 / 0 / 0 | 全部PASS，股票/业务键/摘要及历史保留检查通过 |
| top10_floatholders | 2 | 0 / 0 / 0 | 全部PASS，股票/业务键/摘要及历史保留检查通过 |
| new_share | 1 | 1 / 1 / 0 | 全部PASS，股票/业务键/摘要及历史保留检查通过 |
| stk_managers | 2 | 509 / 509 / 0 | 全部PASS，股票/业务键/摘要及历史保留检查通过 |
| pledge_stat | 2 | 1273 / 1273 / 0 | 全部PASS，股票/业务键/摘要及历史保留检查通过 |
| pledge_detail | 2 | 2 / 2 / 0 | 全部PASS，股票/业务键/摘要及历史保留检查通过 |
| index_classify | 1 | 359 / 359 / 0 | 全部PASS，股票/业务键/摘要及历史保留检查通过 |
| index_member_all | 2 | 2 / 2 / 0 | 全部PASS，股票/业务键/摘要及历史保留检查通过 |

## 日期轴/多股票及旧NOT_RUN补证 SOURCE 实际结果

- 2026-09-13T09:48:36.733Z ～ 2026-09-13T09:49:06.871Z，exit0/cleanup PASS，14请求，{'PASS': 7, 'EVIDENCE_MISSING': 1}；全部8项均执行，输入稳定。原三NOT_RUN只在此新run得到实际观察，旧run不改。

| caseId | 状态/行数 | 安全投影与实测限制 |
| --- | --- | --- |
| `issue018-t14-representative-source-20260913T094124Z-income-range` | PASS / 2 | SOURCE validationStatus=PASS; parameter, envelope and stock scope checks; completeness UNKNOWN; actualDateColumn=ann_date; datePredicate=closed interval; actualDates=20260815; uniqueDateCount=1; min=20260815; max=20260815; candidateRowLimit=UNKNOWN; candidateLimitReached=false; dateComparisonValidRows=2; dateComparisonUnavailableRows=0; annDateDifferentFromEndDateRows=2; annInRangeEndOutsideRows=2 |
| `issue018-t14-representative-source-20260913T094124Z-income-range-600000` | PASS / 2 | SOURCE validationStatus=PASS; parameter, envelope and stock scope checks; completeness UNKNOWN; actualDateColumn=ann_date; datePredicate=closed interval; actualDates=20260828; uniqueDateCount=1; min=20260828; max=20260828; candidateRowLimit=UNKNOWN; candidateLimitReached=false; dateComparisonValidRows=2; dateComparisonUnavailableRows=0; annDateDifferentFromEndDateRows=2; annInRangeEndOutsideRows=2 |
| `issue018-t14-representative-source-20260913T094124Z-fina_indicator-range` | PASS / 5 | SOURCE validationStatus=PASS; parameter, envelope and stock scope checks; completeness UNKNOWN; actualDateColumn=end_date; datePredicate=closed interval; actualDates=20250331,20250630,20250930,20251231; uniqueDateCount=4; min=20250331; max=20251231; candidateRowLimit=100; candidateLimitReached=false; dateComparisonValidRows=5; dateComparisonUnavailableRows=0; endInRangeAnnOutsideRows=1 |
| `issue018-t14-representative-source-20260913T094124Z-fina_indicator-range-600000` | PASS / 4 | SOURCE validationStatus=PASS; parameter, envelope and stock scope checks; completeness UNKNOWN; actualDateColumn=end_date; datePredicate=closed interval; actualDates=20250331,20250630,20250930,20251231; uniqueDateCount=4; min=20250331; max=20251231; candidateRowLimit=100; candidateLimitReached=false; dateComparisonValidRows=4; dateComparisonUnavailableRows=0; endInRangeAnnOutsideRows=1 |
| `issue018-t14-representative-source-20260913T094124Z-repurchase-range` | PASS / 852 | SOURCE validationStatus=PASS; parameter, envelope and stock scope checks; completeness UNKNOWN; actualDateColumn=ann_date; datePredicate=closed interval; actualDates=20260801,20260803,20260804,20260805,20260806,20260807,20260808,20260810,20260811,20260812,20260813,20260814,20260815,20260817,20260818,20260819,20260820,20260821,20260822,20260824,20260825,20260826,20260827,20260828,20260829,20260831; uniqueDateCount=26; min=20260801; max=20260831; candidateRowLimit=UNKNOWN; candidateLimitReached=false; validStockCodeRows=852; unavailableStockCodeRows=0; distinctStockCodeCount=585 |
| `issue018-t14-representative-source-20260913T094124Z-top_list-closed-calendar` | PASS / 0 | SOURCE validationStatus=PASS; parameter, envelope and stock scope checks; completeness UNKNOWN; actualDateColumn=trade_date; datePredicate=closed interval; actualDates=; uniqueDateCount=0; min=none; max=none; candidateRowLimit=10000; candidateLimitReached=false; calendarOpenDayCount=0; closedCalendar=true |
| `issue018-t14-representative-source-20260913T094124Z-top_list-sh-calendar` | EVIDENCE_MISSING / 0 | SOURCE validationStatus=EVIDENCE_MISSING; parameter, envelope and stock scope checks; completeness UNKNOWN; actualDateColumn=trade_date; datePredicate=closed interval; actualDates=; uniqueDateCount=0; min=none; max=none; candidateRowLimit=10000; candidateLimitReached=false; calendarOpenDayCount=6; closedCalendar=false |
| `issue018-t14-representative-source-20260913T094124Z-margin-bse-direct` | PASS / 6 | SOURCE validationStatus=PASS; parameter, envelope and stock scope checks; completeness UNKNOWN; actualDateColumn=trade_date; datePredicate=closed interval; actualDates=20260803,20260804,20260805,20260806,20260807,20260810; uniqueDateCount=6; min=20260803; max=20260810; candidateRowLimit=4000; candidateLimitReached=false |

## 候选包与前四门禁

四项本地v2候选已通过两次独立审查；仅8个策略/测试/能力预期文件同步到隔离源码，原Java日历/BJ/BSE/UNKNOWN拒绝及业务键不变。候选包重建后实际身份如下；规范索引四项policyVersion同步v2、处置仍NEEDS_VERIFICATION，尚不宣称RANGE通过。

| 门禁 | 实际结果 |
| --- | --- |
| g1 | exit0；{'tests': 1318, 'failures': 0, 'errors': 0, 'skipped': 0} |
| g2 | exit0；34文件/468前端测试PASS |
| g3 | exit0；{'tests': 1060, 'failures': 0, 'errors': 0, 'skipped': 0} |
| g4 | exit0；{'tests': 1063, 'failures': 0, 'errors': 0, 'skipped': 0} |

| 候选输入 | SHA-256 |
| --- | --- |
| productionJarSha256 | `b6f1a90dc9fbe54916895fe1c896a7b69e1982dd3a2c8e26124e7e6451811cfa` |
| acceptanceJarSha256 | `cb6c7a750c0e75768ee54ad89cf5ed368690edd00f7210ac10dff33a1cdc1a5f` |
| casePlanSha256 | `1d76f05054f675799d96a97e3727a8061ad6fd566376184faee419deb635db9c` |
| evidenceIndexSha256 | `1a45d8abcc3f673762ccc7ce886de4e349bcb651acfb86131360f2a2c2addf66` |

完整候选索引私有快照为固定计划目录下 `evidence-index.json`（普通0600/父0700），包括全部7轮450case，SOURCE所属运行身份未丢失。RANGE前仍需实际核对AVAILABLE/v2/阈值，失败停止并保留运行。

## G5 与 RANGE 提交前核对

`npm --prefix control-plane run test:e2e -- e2e/download-tasks.spec.js`：3/3、exit0，使用本次自有4173 preview。完整私有候选索引经现有selectTaskCases纯校验，25个TASK全部且仅绑定 `issue018-t14-priority-source-20260913T092938Z` 的同参数SOURCE，未选旧失败轮case。四API运行时仍需逐项验证AVAILABLE/v2/规则，不能用发现或纯校验代替提交。RANGE于G5完成后在8080独立新库启动，与其他账户/普通浏览器串行。

## 四接口 RANGE TASK 实际结果

- runId `issue018-t14-priority-range-20260913T094551Z`，2026-09-13T09:58:21.120Z ～ 2026-09-13T10:02:00.195Z。全部25/25 PASS、25次Tushare请求/50次records查询、exit0/cleanup PASS，输入保持稳定。fixture另2任务/3查询/2受控来源，不计入25项。
- 实际运行时逐API核对AVAILABLE、tushare-range-v2、TRADE_DATE及ROW_LIMIT；所有25项只匹配本次干净优先SOURCE的相同参数。25条叶子全部SUCCEEDED，202/Location、页面、完整批次树、SQL业务键/股票归属/摘要和27条accepted/started/finished/batch事件（含fixture）逐一对应。
- SQL合计来源68/插入52/更新16；端点任务复用了整段写入数据，更新操作与SQL一致，未删除成功数据。新schema初始0表，迁移后8个成功版本/52表。自有JVM及浏览器退出，账户库保留。
- sourceDiffSha256 `982336d95ab36dee851f4c1bef470af9af783e3bd3ca2ea516633ee0b9c3b049`；生产/验收包、清单和完整SOURCE索引SHA均与上文固定候选身份一致。私有安全摘要 `/private/tmp/issue018-t14-range-20260913T094505Z/task-summary.json` 的privateLogSafe/inputUnchanged/ownedProcessReaped全部true。

| API | RANGE TASK | SOURCE / INSERT / UPDATE | 覆盖 |
| --- | ---: | --- | --- |
| daily_basic | 7 PASS | 20 / 16 / 4 | 两股票整段/下端/上端，另000001.SZ跨年20251229–20260105 |
| stk_limit | 6 PASS | 16 / 12 / 4 | 两股票整段/下端/上端 |
| moneyflow | 6 PASS | 16 / 12 / 4 | 两股票整段/下端/上端 |
| margin_detail | 6 PASS | 16 / 12 / 4 | 两股票整段/下端/上端 |

全run与25个case原样追加到唯一索引，四项当前决定只引用干净优先SOURCE、完整SINGLE和本轮RANGE，不引用旧失败轮。当前40接口/8轮/475case，4 AVAILABLE/v2、30 NEEDS_VERIFICATION、6 SINGLE_ONLY；累计411次Tushare请求（SOURCE297、TASK114），fixture另计。旧3轮329case逐对象不变。

持久索引回归先因尚无RANGE证据观察RED1，再在实际结果落盘后77/77 GREEN；最终期望明确限定四项AVAILABLE/v2及25个真实RANGE case，未通过放宽校验接受缺证据。测试期望最终字节位于原工作区，不影响已绑定的运行快照或实际两包。

最终只读审查说明：RANGE TASK的 `expectedCoverage=SOURCE_SEMANTICS_ONLY_COMPLETENESS_UNCONFIRMED` 由harness原样继承其绑定SOURCE的取证阶段标签，不是该TASK的最终完整性判定。实际准入已核对AVAILABLE/v2/ROW_LIMIT，最终按完整叶子覆盖、终态及独立SQL判断PASS。保留实际run/case元数据，不回填旧SOURCE为已确认；最终开放依据是接口决定、运行身份及任务验证的组合。

## G6 首轮失败与普通浏览器用例修正

首轮 `npm --prefix control-plane run test:e2e` 使用已绑定候选包、自有preview及四个新空schema，真实账户变量移除。已观察daily_basic metadata寻找SINGLE的trade_date标签失败、34股票表单寻找trade_date超时、UI矩阵四项断言默认SINGLE失败；完整退出计数待本轮结束登记。失败日志 `g6.log` 和Playwright失败产物保留，不把失败或未运行项算通过。

根因已由页面错误快照与 `useDownloadFlow.js` 对照确认：既有第185行在能力AVAILABLE时默认RANGE，既有单测 `loads available capabilities as RANGE and replaces the parameter model on mode switches` 明确此合同。四项开放后，三个旧普通用例仍直接操作SINGLE字段；产品默认行为正确。

最小修正仅涉及 `tushare-metadata.spec.js`、`ui-redesign.spec.js`、`stock-download-parameters.spec.js`：先确认可用RANGE默认选中，再显式check单次请求后测试原SINGLE参数/必填/股票提交。普通测试修正不改产品、策略、实际两包或任何SOURCE/TASK证据，不追加账户请求。待本轮完全退出及自有容器清理后，保留失败产物、复制三文件并逐字核对，再用另一个新四schema容器执行完整126项复验。

G6首轮最终：exit1，97 PASS / 7 FAILED / 22 NOT_RUN，17.3分钟；自有容器及临时秘密清理成功。失败产物完整保存在控制目录 `g6-first-artifacts`，结果为 `g6-first-result.json`。另一个同根因失败是键盘用例搜索daily后ArrowDown选中daily_basic，旧contains断言未区分两者；现改为键盘搜索唯一“日线行情”、候选数1及精确所选daily标签，再测原单日焦点。三个文件最终字节在首轮退出后才复制至隔离源码，包与真实账户证据均不变。

收尾只读SQL另核对三库的实际task_id/status：原T13全部15任务、本次SINGLE全部74任务及RANGE全部25任务均仍存在且SUCCEEDED，集合与唯一索引逐一相符；未发现额外Tushare任务。安全计数保存于控制目录 `task-retention.json`，不打印凭证、数据库URL或原始错误，不调用上游。

## G6 模式点击动作补充验证

第二次全量日志 `g6-final.log` 实际暴露Element Plus radio原生input被可见inner span拦截，`.check()`在真正切换模式时超时。已由独立全mock浏览器 `radio-probe.mjs` 复现：原生check失败，点击可见标签后SINGLE选中。为停止已知重复超时，核对自有Playwright进程身份后发SIGINT；保留49 PASS / 1 FAILED / 1 INTERRUPTED / 75 NOT_RUN、exit130、14.9分钟，runner正常清理自有容器/秘密，完整产物 `g6-second-artifacts` 保留。该轮不计通过。

第一个4项聚焦轮 `g6-focused.log` 为3 PASS/1 FAILED、exit1/cleanup PASS：股票循环产生的近期任务表也含“单次请求”，未限定区域的文字定位冲突，失败产物 `g6-focused-first-artifacts` 保留。最终动作限定 `下载模式` radiogroup中的可见标签，再断言原生radio选中，不用force、不删除覆盖。4项新聚焦 `g6-focused-final.log` 全部PASS（36.5s）、exit0/cleanup PASS：34股票提交循环、daily_basic UI矩阵、精确daily键盘操作及daily_basic真实包metadata。

同类动作亦修正于真实账户harness的chooseDownload，确保以后四项AVAILABLE下执行SINGLE可切换模式。现有命令式离线用例更新为验证模式组/精确标签/选中态；观察77项中的1 RED后，最终77/77 GREEN。此前实际SINGLE在四项关闭时默认SINGLE、RANGE在开放后默认RANGE，原运行均未需要这次反向切换；已有真实成功事实不改写，也没有为修复重新调用账户。

最后五份harness/测试文件在先前运行退出后复制并逐字核对，记录 `g6-complete-inputs.json`；实际两包未改变。最终全量复验日志为 `g6-complete.log`，使用另一自有MySQL8.4.6四schema容器、账户变量移除；结果待实际结束登记。

当前harness spec SHA-256 `b2f6c85819d8ebc7b59bb70eae358d64e524a9d6f0c28b110eaa8be362c181b7`；旧SINGLE/RANGE运行记录中的spec SHA `ba27393b114444bc8dfbf777f21580571ac026557cad56c7ff2e682cef79acba` 是当时真实字节，保持原样。门禁代码和运行证据按各自身份解释。

## 最终门禁与环境清理

`npm --prefix control-plane run test:e2e` 的最终完整轮 `g6-complete.log`：**7文件/126项全部PASS、0失败/跳过、exit0，15.9分钟**。包含完整40元数据、34股票循环、四项默认RANGE与显式SINGLE、键盘/移动端及全部普通套件。输入文件摘要与实际两包稳定，安全结果 `g6-complete-result.json` 保留；前两次全量失败/中断和第一聚焦失败不覆盖、不拼接计数。

| 当前源码门禁 | 最终有效结果 |
| --- | --- |
| G1 全部Test/IT（排除打包测试） | 1318，0失败/错误/跳过，exit0 |
| G2 前端单测 | 34文件/468，exit0 |
| G3 生产clean verify | 1060，0失败/错误/跳过，exit0 |
| G4 acceptance clean verify | 1063，0失败/错误/跳过，exit0 |
| G5 download-tasks浏览器 | 3/3，exit0 |
| G6 普通浏览器全量 | 7文件/126，exit0 |

Node证据回归最终77/77、聚焦浏览器4/4、独立最终/定位修正复审均通过，见 [最终审查](ISSUE-018-T14-final-review.md)。后续只变更harness/普通测试动作，应用/策略/包无变化，未重复Java构建或真实账户请求；当前与过去spec按各轮身份保留。

自有普通测试容器及临时秘密由runner清理；核对PID/进程组/工作目录后SIGTERM停止自建preview，wrapper观察到预期信号退出。4173/8080均空闲，自有regression标签容器为0，原T13账户容器仍运行。三库15/74/25个账户任务已只读核对全部保留且SUCCEEDED，安全事实见 `final-cleanup.json` 及 `task-retention.json`。原成果和新增项目文件Git纳管，无提交/合并/发布。完整发布脚本因main/干净输入/HEAD前置未满足而未运行。

T14仍缺Acceptance第4/6项要求的全部上游/代表场景及其余30项RANGE证据；本次可执行验证和审查收尾后，按 [pause交接](../task-handoffs/ISSUE-018/ISSUE-018-T14-handoff.md) 记录IN_PROGRESS→BLOCKED。T13保持历史BLOCKED，母issue不关闭，不创建T15。

## ISSUE-019 独立来源取证
### 公开公告依据（执行前）
2026-09-13 续办 ISSUE-019。限量方案 A/B 仍待选择；以下来源取证沿用严格合同，不修改 UNKNOWN、生产准入或旧验收要求。公开页面只用于固定样本，不算新的 Tushare SOURCE。
- 平安银行公开预告：公告日20160121、报告期20151231，净利润预期同比增长5%～15%；浦发银行公告日20081014、报告期20080930，预期同比增长150%左右。选用明确预测样本，未将页面中后来混列的业绩快报当成预告。
- ST星源公告日20190131、报告期20181231，预增618.56%～945.18%，与Tushare doc45的000005.SZ同日示例一致。作为额外交叉样本，保留原两股票验证。
- 平安银行分红页逐字行：`2026-08-15 0 0 2.49 预案 -- -- -- 查看`，栏目为公告日期/每10股送股、转增、税前派息/进度/除息、登记、红股上市日。详情列税前红利2.49，实施日相关栏为空。20260815为周六，与旧SOURCE日期一致；这支持预案公告的非交易日事件，不把它称为实施公告。
- 浦发银行分红页20260710为“实施”，不能据该行推定其Tushare ann_date。本轮仅作相同周末窗口的第二股票独立对照，不预言其非空；无数据时保留缺口。
- 以上为新浪公开第三方记录，不宣称已取得发行人公告原件。缓存为 `/private/tmp/issue019-research`；下载完成时间取文件mtime UTC，摘要按原始GB字节计算。Bing两次多词查询只返回不相关结果，不作证据；无账户请求、无发送消息。
| 公开缓存 / URL | 下载完成 UTC | SHA-256 |
| --- | --- | --- |
| [forecast-pingan](https://vip.stock.finance.sina.com.cn/corp/go.php/vFD_AchievementNotice/stockid/000001.phtml) | 2026-09-13T12:17:43.827265+00:00 | `05b98589c33e418931c2c9fd3ac790bae8c95711844aaa50a1caf73a58dea407` |
| [forecast-pufa](https://vip.stock.finance.sina.com.cn/corp/go.php/vFD_AchievementNotice/stockid/600000.phtml) | 2026-09-13T12:17:46.471311+00:00 | `b0b5878802bf2401d0eb60a4d3b52d4f7498ebcdcae76d8e7808ad5cd3de8642` |
| [forecast-xingyuan](https://vip.stock.finance.sina.com.cn/corp/go.php/vFD_AchievementNotice/stockid/000005.phtml) | 2026-09-13T12:15:46.754690+00:00 | `d8438cd1decd8c8a6803c5ecf0822200c23725acba3075d166951243df19e810` |
| [dividend-pingan](https://vip.stock.finance.sina.com.cn/corp/go.php/vISSUE_ShareBonus/stockid/000001.phtml) | 2026-09-13T12:15:48.577124+00:00 | `ffb86063c188e71ec694ece97e0726dc1e1fabbb6dd092ca923e53a2bd503d1c` |
| [dividend-pingan-detail](https://vip.stock.finance.sina.com.cn/corp/view/vISSUE_ShareBonusDetail.php?stockid=000001&type=1&end_date=2026-08-15) | 2026-09-13T12:16:30.155802+00:00 | `3f8dbe28563ab330c1f5e8bc9e9ee0556d5c27d99ffea10f6ee85b457f5d5a0d` |
| [dividend-pufa](https://vip.stock.finance.sina.com.cn/corp/go.php/vISSUE_ShareBonus/stockid/600000.phtml) | 2026-09-13T12:17:46.867967+00:00 | `7c3877982ed2d3c5eefe3ccd0235ed27722d32bc362ac7a49de53644fec7f1f2` |

### 固定 SOURCE 计划
- runId `issue019-source-20260913T122126Z`；专属[设计](../task-designs/ISSUE-019-design.md)。仅SOURCE，尚未执行。
- 输入 `/private/tmp/issue019-source-20260913T122126Z/cases.json`，普通0600/父0700，SHA-256 `437c45cab694f21b3421a46db804a72427e2477d7b67dc684bd9ed5be5266f60`；隔离源码计划路径 `/private/tmp/issue019-work-20260913T122126Z`。
- 共32 case：daily8、forecast12、dividend12；未触及候选阈值时40次来源请求（8+12+20），仍受30分钟/5000请求、至少2000ms间隔限制。旧8轮/475 case完整保留，原daily/forecast/dividend旧SOURCE run失败不改写。
- daily复用T13固定日期和重叠；forecast替换无公告依据的202608空窗为上述已查到的历史事件，额外保留官方样本；dividend围绕已观察并交叉核对的20260815固定三个自然日。每个边界或重叠均独立请求；空/失败原样记录，不改日期直到非空。
- 预期只核对字段、股票、日期闭区间、公告边界；没有已确认截断合同，不以少量或空样本证明完整。不执行数据库写入/任务提交，未来TASK使用相同参数和全新caseId。
| caseId（共同前缀为runId） | API | 精确参数 |
| --- | --- | --- |
| `daily-000001-whole` | daily | `{"ts_code":"000001.SZ","start_date":"20260803","end_date":"20260810"}` |
| `daily-000001-lower` | daily | `{"ts_code":"000001.SZ","start_date":"20260803","end_date":"20260803"}` |
| `daily-000001-upper` | daily | `{"ts_code":"000001.SZ","start_date":"20260810","end_date":"20260810"}` |
| `daily-000001-overlap` | daily | `{"ts_code":"000001.SZ","start_date":"20260805","end_date":"20260810"}` |
| `daily-600000-whole` | daily | `{"ts_code":"600000.SH","start_date":"20260803","end_date":"20260810"}` |
| `daily-600000-lower` | daily | `{"ts_code":"600000.SH","start_date":"20260803","end_date":"20260803"}` |
| `daily-600000-upper` | daily | `{"ts_code":"600000.SH","start_date":"20260810","end_date":"20260810"}` |
| `daily-600000-overlap` | daily | `{"ts_code":"600000.SH","start_date":"20260805","end_date":"20260810"}` |
| `forecast-000001-whole` | forecast | `{"ts_code":"000001.SZ","start_date":"20160120","end_date":"20160122"}` |
| `forecast-000001-event-at-lower` | forecast | `{"ts_code":"000001.SZ","start_date":"20160121","end_date":"20160122"}` |
| `forecast-000001-event-at-upper` | forecast | `{"ts_code":"000001.SZ","start_date":"20160120","end_date":"20160121"}` |
| `forecast-000001-event` | forecast | `{"ts_code":"000001.SZ","start_date":"20160121","end_date":"20160121"}` |
| `forecast-600000-whole` | forecast | `{"ts_code":"600000.SH","start_date":"20081013","end_date":"20081015"}` |
| `forecast-600000-event-at-lower` | forecast | `{"ts_code":"600000.SH","start_date":"20081014","end_date":"20081015"}` |
| `forecast-600000-event-at-upper` | forecast | `{"ts_code":"600000.SH","start_date":"20081013","end_date":"20081014"}` |
| `forecast-600000-event` | forecast | `{"ts_code":"600000.SH","start_date":"20081014","end_date":"20081014"}` |
| `forecast-000005-whole` | forecast | `{"ts_code":"000005.SZ","start_date":"20190130","end_date":"20190201"}` |
| `forecast-000005-event-at-lower` | forecast | `{"ts_code":"000005.SZ","start_date":"20190131","end_date":"20190201"}` |
| `forecast-000005-event-at-upper` | forecast | `{"ts_code":"000005.SZ","start_date":"20190130","end_date":"20190131"}` |
| `forecast-000005-event` | forecast | `{"ts_code":"000005.SZ","start_date":"20190131","end_date":"20190131"}` |
| `dividend-000001-whole` | dividend | `{"ts_code":"000001.SZ","start_date":"20260814","end_date":"20260816"}` |
| `dividend-000001-lower` | dividend | `{"ts_code":"000001.SZ","start_date":"20260814","end_date":"20260814"}` |
| `dividend-000001-event` | dividend | `{"ts_code":"000001.SZ","start_date":"20260815","end_date":"20260815"}` |
| `dividend-000001-upper` | dividend | `{"ts_code":"000001.SZ","start_date":"20260816","end_date":"20260816"}` |
| `dividend-000001-event-at-lower` | dividend | `{"ts_code":"000001.SZ","start_date":"20260815","end_date":"20260816"}` |
| `dividend-000001-event-at-upper` | dividend | `{"ts_code":"000001.SZ","start_date":"20260814","end_date":"20260815"}` |
| `dividend-600000-whole` | dividend | `{"ts_code":"600000.SH","start_date":"20260814","end_date":"20260816"}` |
| `dividend-600000-lower` | dividend | `{"ts_code":"600000.SH","start_date":"20260814","end_date":"20260814"}` |
| `dividend-600000-event` | dividend | `{"ts_code":"600000.SH","start_date":"20260815","end_date":"20260815"}` |
| `dividend-600000-upper` | dividend | `{"ts_code":"600000.SH","start_date":"20260816","end_date":"20260816"}` |
| `dividend-600000-event-at-lower` | dividend | `{"ts_code":"600000.SH","start_date":"20260815","end_date":"20260816"}` |
| `dividend-600000-event-at-upper` | dividend | `{"ts_code":"600000.SH","start_date":"20260814","end_date":"20260815"}` |

### ISSUE-019 执行前环境核对

- 独立clone完整复制841个Git管理文件，包含原暂存成果；snapshot SHA-256 `f35c1be07a0ae52d1743ad3440e77c40b8c611729ce28de66fac3843cc78a657`。原工作树及暂存区保留。
- 首次普通沙箱构建exit1，Mockito/Byte Buddy无法self-attach，尚未执行来源；日志保留为 `/private/tmp/issue019-control-20260913T122126Z/build.log`。在允许本地JVM测试进程的环境中执行同一 `mvn -o -f data-plane/pom.xml -Pacceptance verify`，exit0；1063项本轮新XML后端检查，失败/错误/跳过均0；前端468项通过，生产与验收包合同通过。日志 `/private/tmp/issue019-control-20260913T122126Z/build-unrestricted.log`。构建环境仅保留PATH/HOME/JAVA_HOME/TMPDIR/LANG/LC_ALL，无真实账户或DB变量。
- 项目Node24证据工具测试77/77，失败/跳过0，exit0；构建同时运行现有Policy/Download/Probe回归，未修改实现或规则。
- productionJarSha256: `48fc3e36be041634c9a0e5e9388a85122d05838355789fa37da676c33da186c8`。
- acceptanceJarSha256: `0983cee590b7737809180184aa94aecc01ca14f13df3b7b35e3a9fce725d51ad`。
- manifestSha256: `386f46a99b6605e203129836d7a744b96b65304307f52991dd8bba6fd1870984`。
- requestExamplesSha256: `6d4c74a1a539b59ac20fb0cbd3ba1fba0954c40ef1209b652f7dcc2192ec932f`。

### ISSUE-019 首轮实际结果

- `issue019-source-20260913T122126Z`：2026-09-13T12:24:49.799Z～12:26:14.978Z，exit0、cleanup PASS、privateLogSafe/inputUnchanged均true。32case，24 PASS、8 EVIDENCE_MISSING（空样本），0 FAILED/NOT_RUN，40次请求。源码差异SHA-256 `5389d28af89236c1687be71106b0684298be97090a4500394efd8eebb9df5a52`；构建包身份如上。
- daily两股票各整段6行、下端1行、上端1行、重叠4行；forecast三股票各4种公告窗口均1行，12/12非空且ann_date匹配既定事件；dividend平安银行整段/公告单日/两种公告边界窗口均2行，前后两个单日为空。浦发银行同一周末6项全空，原样保留。未触及候选阈值，无TASK或SQL。

### ISSUE-019 浦发银行分红补证

- 新公开依据：[东方财富分红记录](https://datacenter-web.eastmoney.com/api/data/v1/get?reportName=RPT_SHAREBONUS_DET&columns=ALL&filter=%28SECURITY_CODE%3D%22600000%22%29&pageSize=10&sortColumns=REPORT_DATE&sortTypes=-1)，获取完成UTC `2026-09-13T12:26:29.802861+00:00`，原始JSON SHA-256 `75e10eaa5b594d8125f32c8680e1bf1abd650c50d332591b99967da827ee2f23`，私有缓存 `/private/tmp/issue019-research/dividend-pufa-public.json`。SECUCODE=600000.SH、REPORT_DATE=2025-12-31、PLAN_NOTICE_DATE/PUBLISH_DATE=2026-03-31、NOTICE_DATE=2026-07-10、EQUITY_RECORD_DATE=2026-07-15、EX_DIVIDEND_DATE=2026-07-16。NOTICE_DATE和派息安排与新浪实施记录相符；本次依据明确的PLAN_NOTICE_DATE固定预案公告日，不把7月10日移植为ann_date。该第三方公开资料只作取样依据，不是Tushare响应或来源完整性保证。
- 查询只读公开资料，不带账户凭证；源代码/现有工具/限量口径未改，复用已经验证的隔离包。首轮8项空样本继续保留，不覆盖或重试旧case。独立新轮在取得该公开依据后登记，补第二股票事件，不能将第二轮观察回填首轮。
- runId `issue019-dividend-source-20260913T122801Z`；清单 `/private/tmp/issue019-dividend-source-20260913T122801Z/cases.json`，0600/父0700、SHA-256 `41c01eca8025f2003e33c373ab0341222e77fb0c8e6afdcbf508a0fd26cb2e81`。6case、无满额时10次来源请求，预算仍30分钟/5000次、至少2秒间隔。预期检查ann_date=20260331的整段/两端包含性，邻日允许为空；尚未执行，不预填PASS。

| caseId（前缀为runId） | 精确参数 |
| --- | --- |
| `whole` | `{"ts_code":"600000.SH","start_date":"20260330","end_date":"20260401"}` |
| `lower` | `{"ts_code":"600000.SH","start_date":"20260330","end_date":"20260330"}` |
| `event` | `{"ts_code":"600000.SH","start_date":"20260331","end_date":"20260331"}` |
| `upper` | `{"ts_code":"600000.SH","start_date":"20260401","end_date":"20260401"}` |
| `event-at-lower` | `{"ts_code":"600000.SH","start_date":"20260331","end_date":"20260401"}` |
| `event-at-upper` | `{"ts_code":"600000.SH","start_date":"20260330","end_date":"20260331"}` |

### ISSUE-019 第二轮实际结果与后续输入

- `issue019-dividend-source-20260913T122801Z`：2026-09-13T12:28:12.845Z～12:28:34.575Z，exit0、cleanup PASS，privateLogSafe/inputUnchanged均true；与首轮源码差异及两包SHA-256一致。6case/10请求，4 PASS、2 EVIDENCE_MISSING（空单日）、0 FAILED/NOT_RUN。浦发银行整段、公告单日及公告在上下边界的两个窗口各1行，ann_date均20260331；20260330与20260401为空。
- 两轮合计38case/50请求，28 PASS、10空样本；SOURCE无任务/SQL。全部结果追加唯一JSON，新索引10轮/513case/461次累计来源请求，旧8轮/475case逐对象不变。三接口仍UNKNOWN/NEEDS_VERIFICATION/v1，其他37项决定不变。
- 为ISSUE-026提供28项非空候选：首轮全部8个daily、12个forecast，以及dividend-000001的whole/event/event-at-lower/event-at-upper；第二轮whole/event/event-at-lower/event-at-upper。每项精确params/dateAxis及SOURCE caseId均在本文件预登记表和唯一JSON中，TASK应复制参数并使用ISSUE-026新runId/caseId。预期36次来源调用（daily8+forecast12+两股票分红各8），daily整段先于重叠，后者须检查实际update及SQL业务键；不能从SOURCE行数推导写入数。
- 10项空对照属于真实历史观察，保留在run和当前SOURCE引用中，不能冒充非空代表场景；未来建立候选引用时须明确将其保留为对照而非覆盖缺口的证据，不删除其历史记录。当前未筛掉它们以制造dividend聚合PASS。若要验收空TASK，须由ISSUE-026按完整性决定及原空结果合同另行登记，不能直接把SOURCE的EVIDENCE_MISSING改为PASS。
- 限量选择仍未到达：不修改T13/T14严格合同、completeness或生产Policy。源码无需为本轮取证改动；正式TASK/SQL和准入仍由ISSUE-026处理。ISSUE-019当前不宣称关闭。

### ISSUE-019 当前收尾校验

- 追加真实结果后，从当前工作树以不含真实账户/DB变量的环境运行项目Node24 `--test control-plane/e2e/tushare-range-evidence.test.js`：77/77、0失败/错误/跳过，exit0；日志 `/private/tmp/issue019-control-20260913T122126Z/final-evidence-tests.log`。
- 索引独立检查：旧8轮/475case逐对象不变，其他37个接口记录不变；10轮/513个全局唯一case、461次累计来源请求；两个新run保留全部38个实际case。相关本地Markdown链接存在，`git diff --check`及`git diff --cached --check`通过。
- 没有修改生产代码；新建设计文件已加入Git，ISSUE-019仍IN_PROGRESS，待明确限量决定。

### ISSUE-019 方案A确认与最终验收

- 2026-09-13，用户在明确询问“是否确认采用方案A，按官方6000／3500／2000行作为阈值”后回复“同意”。接受范围和残余前提见[决策记录](../issues/proposals/ISSUE-019-documented-range-limits.md#决策记录)；本决定不是上游新增保证。此前本文件“待决/UNKNOWN”段落保留为当时事实。
- T13/T14已对仅这三项记录采用例外，正式索引改为ROW_LIMIT并附官方URL、原文/获取身份所在报告及用户决定引用。其他37接口记录、三个生产Policy及v1/NEEDS_VERIFICATION不变，当前仍4 AVAILABLE；没有新增SOURCE/TASK/SQL或改写历史run。
- RED：更新独立规则预期及拒绝提前开放检查后，针对两项测试运行Node，因三接口仍UNKNOWN而失败，exit1；日志 `/private/tmp/issue019-close-20260913T123820Z/red.log`。GREEN：更新索引后最终Node全套78/78、0失败/跳过、exit0；日志同目录 `node-final.log`。
- Java21离线回归命令：`mvn -o -f data-plane/pom.xml -pl tensor-plugin-tushare -am -Dtest=TushareBatchPoliciesTest,TushareBatchDownloadTest,TushareRangeSourceProbeTest -Dsurefire.failIfNoSpecifiedTests=false test`。Policy109、Download21、Probe30，共160，失败/错误/跳过0、exit0；日志同目录 `maven.log`。环境去除真实账户/DB变量，允许Mockito附加自有JVM；未重复真实来源。
- [ISSUE-026输入](../issues/problems/ISSUE-026-range-task-final-acceptance.md#issue-019-已交付输入)已明确交付规则/用户决定、28项SOURCE身份及参数复制合同、10项空对照保留方式、候选v2和新TASK/SQL验证要求。现有selectTaskCases离线接受28项且精确绑定到两真实run的对应case，未提交任何任务；核对脚本 `/private/tmp/issue019-close-20260913T123820Z/verify-handoff.mjs`。
- 关闭条件复核：三项请求范围/依据/采用口径/异常处理明确；固定来源覆盖两股票、整段/边界、daily重叠和非交易日公告，forecast已非空；两run退出/清理通过，空对照未改写；ISSUE-026输入已交付，生产未提前开放。本issue要求的规则和来源工作已具备关闭依据，母issue及TASK/SQL仍按原合同处理。

## ISSUE-020 分类与限量独立取证

### 固定SOURCE计划（执行前登记）

- runId `issue020-source-20260913T131245Z`；专属[设计](../task-designs/ISSUE-020-design.md)，尚未执行。
- 私有清单 `/private/tmp/issue020-source-20260913T131245Z/cases.json`，普通0600/父0700，SHA-256 `0e14708ce6016c0c6800fbb1435495796262b5a50dfe41fa076139eca4a76ae0`；计划16case/16请求，至少2000ms、30分钟/5000请求许可。
- 旧10轮/513case原样保留；新caseId均唯一。旧fina_mainbz两股票SINGLE150行与旧2025整段74行是取样依据，新观察不追认旧失败run。固定宽窗口用于调查限量；达到100将保留EVIDENCE_MISSING。
- 安全摘要仅分类/日期/业务键计数，真实取证不传type/period，不增加自定义fields（客户端仍显式请求YAML的8列），不入库。两股票独立请求；第二股同报告期是否非空待观察。

| caseId（省略runId前缀） | mode | 精确params |
| --- | --- | --- |
| `000001-single` | SINGLE | `{"ts_code":"000001.SZ"}` |
| `000001-whole` | RANGE | `{"ts_code":"000001.SZ","start_date":"20250101","end_date":"20251231"}` |
| `000001-half` | RANGE | `{"ts_code":"000001.SZ","start_date":"20250630","end_date":"20250630"}` |
| `000001-annual` | RANGE | `{"ts_code":"000001.SZ","start_date":"20251231","end_date":"20251231"}` |
| `000001-both-boundaries` | RANGE | `{"ts_code":"000001.SZ","start_date":"20250630","end_date":"20251231"}` |
| `000001-event-at-upper` | RANGE | `{"ts_code":"000001.SZ","start_date":"20250629","end_date":"20250630"}` |
| `000001-event-at-lower` | RANGE | `{"ts_code":"000001.SZ","start_date":"20250630","end_date":"20250701"}` |
| `000001-wide-limit` | RANGE | `{"ts_code":"000001.SZ","start_date":"20200101","end_date":"20251231"}` |
| `600000-single` | SINGLE | `{"ts_code":"600000.SH"}` |
| `600000-whole` | RANGE | `{"ts_code":"600000.SH","start_date":"20250101","end_date":"20251231"}` |
| `600000-half` | RANGE | `{"ts_code":"600000.SH","start_date":"20250630","end_date":"20250630"}` |
| `600000-annual` | RANGE | `{"ts_code":"600000.SH","start_date":"20251231","end_date":"20251231"}` |
| `600000-both-boundaries` | RANGE | `{"ts_code":"600000.SH","start_date":"20250630","end_date":"20251231"}` |
| `600000-event-at-upper` | RANGE | `{"ts_code":"600000.SH","start_date":"20250629","end_date":"20250630"}` |
| `600000-event-at-lower` | RANGE | `{"ts_code":"600000.SH","start_date":"20250630","end_date":"20250701"}` |
| `600000-wide-limit` | RANGE | `{"ts_code":"600000.SH","start_date":"20200101","end_date":"20251231"}` |

### ISSUE-020 公开依据与执行前身份

- https://tushare.pro/wctapi/documents/81.md；获取UTC `2026-09-13T13:13:37.118300+00:00`；SHA-256 `d0d5d6992c024518c0041ad73679c17df3afdb303ae2d6aef54c347a941662f2`。
- https://tushare.pro/document/2?doc_id=81；获取UTC `2026-09-13T13:13:37.259707+00:00`；SHA-256 `ff0793cd132fca776afe16c73b23dabb3e10284f5ad3105c59e3eba362819b9d`。
- 两种官方呈现与T14此前原字节摘要相同；5组关键引文交叉核对通过。`type`可选、“P-按产品 D-按地区 I-按行业”、`bz_code`为主营业务来源类型、start/end是报告期、“单次最大提取100行，总量不限制，可循环获取”保持。未提供省略type的默认值或SINGLE例外；普通样例显式P不能说明默认也是P。
- 客户端只有一次原始响应，没有150硬编码、分页或合并；请求fields由当前8列YAML生成（包含bz_code），本次不改字段或请求参数。
- 隔离目录 `/private/tmp/issue020-work-20260913T131245Z`；完整复制842个Git管理文件并逐文件核对，包括原暂存成果；snapshot SHA-256 `285082904dd8bec81542346b0a15e7fafc792d0c857a8743072aa10aadf02cc1`。后续只在主工作树追加文档，不修改隔离源码/包。
- 本地RED33项中3项因缺投影失败；GREEN33/33，无错误/跳过。隔离 `mvn -o -f data-plane/pom.xml -Pacceptance verify` 42.648秒exit0/BUILD SUCCESS；Surefire1059、Failsafe7项，失败/错误/跳过均0；前端468通过。Node证据78/78通过。普通构建/测试无真实账户或DB环境。日志 `/private/tmp/issue020-control`。
- productionJarSha256: `d4c1252577bae6e0953fcd6c6a4537a248419a8abbcb69b34bb461adab0fc073`。
- acceptanceJarSha256: `c1dd50a6437f8d27915cc16906693e1207bf1d5a376ab2d724a0b612c6e4abc9`。
- manifestSha256: `386f46a99b6605e203129836d7a744b96b65304307f52991dd8bba6fd1870984`。
- requestExamplesSha256: `6d4c74a1a539b59ac20fb0cbd3ba1fba0954c40ef1209b652f7dcc2192ec932f`。

### ISSUE-020 首轮实际结果

- `issue020-source-20260913T131245Z`：2026-09-13T13:16:01.915Z～2026-09-13T13:16:36.429Z；exit0、cleanup PASS，privateLogSafe/inputUnchanged均true。16case/16请求，12 PASS、4 EVIDENCE_MISSING，0空/FAILED/NOT_RUN。
- sourceDiffSha256 `2c9fd1b55d1b5380089761452b744d00f17636262d5e609b5ae1f3c4b710fec1`，两包与执行前身份一致。索引追加后11轮/529case，原10轮/513case及其他39接口逐对象不变；累计来源477次（原461+16）。
- 只读专项审查未发现需修复的重要问题；已检查分类/未知值、报告期、键、空/失败/未执行、安全摘要及请求不变。审查不代表本issue已经完成。

| caseId（省略runId前缀） | 状态 | 原始行 | P / D / I | 不同键 / 跨类共用键 |
| --- | --- | ---: | --- | --- |
| `000001-single` | PASS | 150 | 107 / 22 / 21 | 150 / 0 |
| `000001-whole` | PASS | 74 | 52 / 14 / 8 | 74 / 0 |
| `000001-half` | PASS | 37 | 26 / 7 / 4 | 37 / 0 |
| `000001-annual` | PASS | 37 | 26 / 7 / 4 | 37 / 0 |
| `000001-both-boundaries` | PASS | 74 | 52 / 14 / 8 | 74 / 0 |
| `000001-event-at-upper` | PASS | 37 | 26 / 7 / 4 | 37 / 0 |
| `000001-event-at-lower` | PASS | 37 | 26 / 7 / 4 | 37 / 0 |
| `000001-wide-limit` | EVIDENCE_MISSING | 150 | 106 / 28 / 16 | 150 / 0 |
| `600000-single` | PASS | 150 | 98 / 28 / 24 | 150 / 0 |
| `600000-whole` | EVIDENCE_MISSING | 110 | 74 / 20 / 16 | 110 / 0 |
| `600000-half` | PASS | 55 | 37 / 10 / 8 | 55 / 0 |
| `600000-annual` | PASS | 55 | 37 / 10 / 8 | 55 / 0 |
| `600000-both-boundaries` | EVIDENCE_MISSING | 110 | 74 / 20 / 16 | 110 / 0 |
| `600000-event-at-upper` | PASS | 55 | 37 / 10 / 8 | 55 / 0 |
| `600000-event-at-lower` | PASS | 55 | 37 / 10 / 8 | 55 / 0 |
| `600000-wide-limit` | EVIDENCE_MISSING | 150 | 111 / 20 / 19 | 150 / 0 |

省略type在本轮两股票SINGLE/RANGE均返回P/D/I；仅证明本样本，不补写全历史默认集合。2025两报告期分项相加在数量和分类计数上与整段一致，但没有逐行内容集合比较，也不能由一致性推定全历史完整。SOURCE无TASK/SQL。

普通RANGE的110及150已反驳“100仅适用RANGE”的解释；不将150当新上限。4个达到100的case原样保留EVIDENCE_MISSING，不能为开放重标PASS。[具体采用方案](../issues/proposals/ISSUE-020-fina-mainbz-default-type.md)待用户决定。

### ISSUE-020 报告期整段补证计划（执行前）

- 新runId `issue020-boundary-source-20260913T131859Z`，2case/2请求；基于首轮两股票20250630各37/55行，固定两股票20250629～20250701整段。用于与首轮单日和上下边界共同检验同一报告期；不换掉全年110或六年150的旧结果。采用决定尚待用户回复，SOURCE补证不修改规则。
- 私有清单 `/private/tmp/issue020-boundary-source-20260913T131859Z/cases.json`，0600/父0700，SHA-256 `f4fa4effce0d022ba86c9775d39ab1e6aacbdf97b28b780db5e39ebaadc37f27`；复用已核验隔离源码/两包，至少2秒节流、30分钟/5000请求许可，无TASK/SQL。

| caseId | params |
| --- | --- |
| `issue020-boundary-source-20260913T131859Z-000001-whole` | `{"ts_code":"000001.SZ","start_date":"20250629","end_date":"20250701"}` |
| `issue020-boundary-source-20260913T131859Z-600000-whole` | `{"ts_code":"600000.SH","start_date":"20250629","end_date":"20250701"}` |

### ISSUE-020 整段补证实际结果

- `issue020-boundary-source-20260913T131859Z`：2026-09-13T13:19:20.930Z～2026-09-13T13:19:26.411Z，exit0/cleanup PASS、privateLogSafe/inputUnchanged均true；2case/2请求/2 PASS，无空/失败/未执行。源码差异和两包SHA-256与首轮完全一致。
- 两股票20250629～20250701整段分别37/55行，均仅end_date=20250630，P/D/I分别26/7/4及37/10/8；与首轮同日报告期和上下边界各自数量/分类一致。分类/报告期/键不可用值均0、跨类共用键0、不同键37/55。只有计数和日期观察，不声称跨请求内容摘要完全一致。
- 合计两轮18case/18请求：14 PASS（含2个SINGLE）、4 EVIDENCE_MISSING（达到候选100），无空/失败/未执行；唯一索引12轮/531case、累计479次来源请求，原10轮/513case不变。来源补证可独立成立，采用方案仍待用户决定，RANGE不开放。

### ISSUE-020 当前验证与待决项

- 最后Node证据校验78/78、0失败/跳过，exit0；日志 `/private/tmp/issue020-control/node-final.log`。原10轮/513case、其他39接口和顶层输入哈希逐对象不变；新18case的分类计数之和、合法日期/键和4个达到100的未确认状态交叉核对通过。
- 离线消费检查 `/private/tmp/issue020-control/verify-handoff.mjs`：12项RANGE条件输入逐项绑定到两个真实SOURCE run/case；4项满额对照全部拒绝，无TASK提交或索引改写。该检查只验证结构/匹配，不代表待决采用方案已获授权。
- 两轮均由wrapper确认日志安全、输入/源码/包未变和进程结束，未创建数据库或修改已有账户任务；不重跑已有74个SINGLE任务。取证工具审查通过。普通浏览器六门禁与发布脚本未运行，本次未改生产策略或应用源码，不称作发布验收。
- 采用口径尚待用户明确选择，ISSUE-020保持IN_PROGRESS；本次已有工作和可执行条件输入全部保留，不把工程阈值自行写成上游已确认合同。
- 最终限定只读复审：18case/18请求=14 PASS+4满额未确认、12项RANGE输入精确匹配、原10轮/513case及其他39接口保留，未发现重要矛盾；总请求479=SOURCE365+TASK114。decisionRef为空且生产v1/NEEDS_VERIFICATION保持。新增设计/方案及对应改动已Git纳管，工作树及暂存差异空白检查通过；未创建提交。

### ISSUE-020 方案A确认与最终验收

- 2026-09-13用户明确回复“同意方案A（推荐）”。已记录[原话及精确范围](../issues/proposals/ISSUE-020-fina-mainbz-default-type.md#决策记录)，限定修订T13/T14和ISSUE-020设计：默认保留一次上游实际分类，SINGLE单次快照；RANGE100工程阈值，<100采用本片完整口径，>=100拆分，最小单日仍满额失败。官方缺省集合/硬上限未获保证的事实保留，150不成为新阈值，参数、8列、键及生产准入未改。
- RED：更新独立决定引用预期后，索引decisionRef仍null导致聚焦测试失败，exit1；补齐fina_mainbz决定与官方/来源引用后，Node全套78/78、无失败/跳过、exit0。不提前开放回归覆盖fina_mainbz：只有SOURCE和SINGLE TASK仍不得记录AVAILABLE。
- 决定后相关Maven163/163通过：Policies109、BatchDownload21、Probe33，失败/错误/跳过均0，exit0。命令 `mvn -o -f data-plane/pom.xml -pl tensor-plugin-tushare -am -Dtest=TushareBatchPoliciesTest,TushareBatchDownloadTest,TushareRangeSourceProbeTest -Dsurefire.failIfNoSpecifiedTests=false test`。Node命令 `node --test control-plane/e2e/tushare-range-evidence.test.js`。普通环境移除真实账户/DB变量，日志 `/private/tmp/issue020-close-20260913T132602Z` 的red.log/node-final.log/maven.log。
- 所有12轮/531case、其他39接口和顶层输入哈希逐对象不变；采用后的当前fina_mainbz引用包含本issue全部18个SOURCE及T14两个清洁SINGLE TASK，4个满额仍EVIDENCE_MISSING。旧失败SOURCE仅移出当前引用，原run/case一字未改。本轮无新来源/任务/SQL请求，累计479请求保持。
- [ISSUE-026已交付输入](../issues/problems/ISSUE-026-range-task-final-acceptance.md#issue-020-已交付输入)包含已采用规则、12项准确RANGE SOURCE参数与身份、新任务/候选v2/SQL要求和4个满额对照处理。最终离线消费者12项逐一绑定、4项满额拒绝；未提交任务，全年/宽窗口由ISSUE-026另行固定来源覆盖和拆分验收。ISSUE-017当前事实已同步。
- 限定只读复审未发现阻止关闭的重要问题。已全文复核本issue专属设计及三项Acceptance：默认/阈值结论按明确决定成立，两股票整段/非空边界来源有效，规则/来源/任务样本正式交付；因此满足本issue关闭条件。生产仍4 AVAILABLE、30 NEEDS_VERIFICATION、6 SINGLE_ONLY，fina_mainbz仍v1，T13/T14及母issue保持未完成。

## ISSUE-021 日历、交易所与交易日独立取证

### 第一轮固定计划（执行前）

- runId `issue021-source-20260913T135006Z`；[专属设计](../task-designs/ISSUE-021-design.md)。31case，预计47次来源请求（12日历+9融资汇总+26龙虎榜含10次规划日历）；至少2000ms，30分钟/5000请求许可，失败停轮。尚未执行。
- 私有输入 `/private/tmp/issue021-source-20260913T135006Z/cases.json`，0600/父0700，SHA-256 `1bf4cd9845f305ff14d0100ad06128ac9c0278fb9b3078b15bf7ae1bef5f989b`。BSE直接日历独立登记，不放在本轮造成后续NOT_RUN。
- 新摘要先RED35项2失败，再GREEN169/169（Probe35/Calendar4/Policies109/Download21），exit0、无失败/错误/跳过；日志 `/private/tmp/issue021-control/red.log`、`green.log`。

| caseId后缀（共同前缀为runId加短横线） | 参数 | 依据与预期观察 |
| --- | --- | --- |
| `trade_cal-sse-whole` | `{"exchange":"SSE","start_date":"20180927","end_date":"20181008"}` | 官网26完整自然日；开市/休市由实际校验决定 |
| `trade_cal-sse-lower` | `{"exchange":"SSE","start_date":"20180927","end_date":"20180927"}` | 官网26完整自然日；开市/休市由实际校验决定 |
| `trade_cal-sse-upper` | `{"exchange":"SSE","start_date":"20181008","end_date":"20181008"}` | 官网26完整自然日；开市/休市由实际校验决定 |
| `trade_cal-sse-closed` | `{"exchange":"SSE","start_date":"20181001","end_date":"20181007"}` | 官网26完整自然日；开市/休市由实际校验决定 |
| `trade_cal-sse-cross-year` | `{"exchange":"SSE","start_date":"20251229","end_date":"20260105"}` | 官网26完整自然日；开市/休市由实际校验决定 |
| `trade_cal-sse-current` | `{"exchange":"SSE","start_date":"20260803","end_date":"20260810"}` | 官网26完整自然日；开市/休市由实际校验决定 |
| `trade_cal-szse-whole` | `{"exchange":"SZSE","start_date":"20180927","end_date":"20181008"}` | 官网26完整自然日；开市/休市由实际校验决定 |
| `trade_cal-szse-lower` | `{"exchange":"SZSE","start_date":"20180927","end_date":"20180927"}` | 官网26完整自然日；开市/休市由实际校验决定 |
| `trade_cal-szse-upper` | `{"exchange":"SZSE","start_date":"20181008","end_date":"20181008"}` | 官网26完整自然日；开市/休市由实际校验决定 |
| `trade_cal-szse-closed` | `{"exchange":"SZSE","start_date":"20181001","end_date":"20181007"}` | 官网26完整自然日；开市/休市由实际校验决定 |
| `trade_cal-szse-cross-year` | `{"exchange":"SZSE","start_date":"20251229","end_date":"20260105"}` | 官网26完整自然日；开市/休市由实际校验决定 |
| `trade_cal-szse-current` | `{"exchange":"SZSE","start_date":"20260803","end_date":"20260810"}` | 官网26完整自然日；开市/休市由实际校验决定 |
| `margin-sse-whole` | `{"exchange_id":"SSE","start_date":"20260803","end_date":"20260810"}` | 官网58；既有8月窗口、exchange_id逐行一致 |
| `margin-sse-lower` | `{"exchange_id":"SSE","start_date":"20260803","end_date":"20260803"}` | 官网58；既有8月窗口、exchange_id逐行一致 |
| `margin-sse-upper` | `{"exchange_id":"SSE","start_date":"20260810","end_date":"20260810"}` | 官网58；既有8月窗口、exchange_id逐行一致 |
| `margin-szse-whole` | `{"exchange_id":"SZSE","start_date":"20260803","end_date":"20260810"}` | 官网58；既有8月窗口、exchange_id逐行一致 |
| `margin-szse-lower` | `{"exchange_id":"SZSE","start_date":"20260803","end_date":"20260803"}` | 官网58；既有8月窗口、exchange_id逐行一致 |
| `margin-szse-upper` | `{"exchange_id":"SZSE","start_date":"20260810","end_date":"20260810"}` | 官网58；既有8月窗口、exchange_id逐行一致 |
| `margin-bse-whole` | `{"exchange_id":"BSE","start_date":"20260803","end_date":"20260810"}` | 官网58；既有8月窗口、exchange_id逐行一致 |
| `margin-bse-lower` | `{"exchange_id":"BSE","start_date":"20260803","end_date":"20260803"}` | 官网58；既有8月窗口、exchange_id逐行一致 |
| `margin-bse-upper` | `{"exchange_id":"BSE","start_date":"20260810","end_date":"20260810"}` | 官网58；既有8月窗口、exchange_id逐行一致 |
| `top_list-000007-whole` | `{"ts_code":"000007.SZ","start_date":"20180927","end_date":"20181008"}` | 官网106的000007.SZ / 东方财富同日600318.SH事件；不预言其他开市日非空 |
| `top_list-000007-event` | `{"ts_code":"000007.SZ","start_date":"20180928","end_date":"20180928"}` | 官网106的000007.SZ / 东方财富同日600318.SH事件；不预言其他开市日非空 |
| `top_list-000007-event-at-lower` | `{"ts_code":"000007.SZ","start_date":"20180928","end_date":"20181008"}` | 官网106的000007.SZ / 东方财富同日600318.SH事件；不预言其他开市日非空 |
| `top_list-000007-event-at-upper` | `{"ts_code":"000007.SZ","start_date":"20180927","end_date":"20180928"}` | 官网106的000007.SZ / 东方财富同日600318.SH事件；不预言其他开市日非空 |
| `top_list-000007-closed` | `{"ts_code":"000007.SZ","start_date":"20181001","end_date":"20181007"}` | 官网106的000007.SZ / 东方财富同日600318.SH事件；不预言其他开市日非空 |
| `top_list-600318-whole` | `{"ts_code":"600318.SH","start_date":"20180927","end_date":"20181008"}` | 官网106的000007.SZ / 东方财富同日600318.SH事件；不预言其他开市日非空 |
| `top_list-600318-event` | `{"ts_code":"600318.SH","start_date":"20180928","end_date":"20180928"}` | 官网106的000007.SZ / 东方财富同日600318.SH事件；不预言其他开市日非空 |
| `top_list-600318-event-at-lower` | `{"ts_code":"600318.SH","start_date":"20180928","end_date":"20181008"}` | 官网106的000007.SZ / 东方财富同日600318.SH事件；不预言其他开市日非空 |
| `top_list-600318-event-at-upper` | `{"ts_code":"600318.SH","start_date":"20180927","end_date":"20180928"}` | 官网106的000007.SZ / 东方财富同日600318.SH事件；不预言其他开市日非空 |
| `top_list-600318-closed` | `{"ts_code":"600318.SH","start_date":"20181001","end_date":"20181007"}` | 官网106的000007.SZ / 东方财富同日600318.SH事件；不预言其他开市日非空 |

### 公开取样依据

- `https://tushare.pro/wctapi/documents/26.md`；获取完成UTC `2026-09-13T13:44:17.054062+00:00`；原字节SHA-256 `1b56715c6f328df101c3536f8dbb706f005b02ff799dfce2d256107e71536e52`。
- `https://tushare.pro/wctapi/documents/58.md`；获取完成UTC `2026-09-13T13:45:08.315152+00:00`；原字节SHA-256 `0168ee9c6e9e44f2e2306e8698eb534a9ee5c4854181c20642b81e5760ea5189`。
- `https://tushare.pro/wctapi/documents/106.md`；获取完成UTC `2026-09-13T13:45:49.283691+00:00`；原字节SHA-256 `bc2d883cf542748c6d73e464e9ff7453451017c47844b7ce14ef945c1eeccd85`。
- 官网26逐字：“三大交易所的交易日历都是一样的，北交所交易日历参考上交所和深交所。”输入枚举不含BSE；58逐字：“单次请求最大返回4000行数据，可根据日期循环”，exchange_id明确含SSE/SZSE/BSE；106逐字：“单次请求返回最大10000行数据，可通过参数循环获取全部历史”，样例000007.SZ的20180928非空。
- [东方财富20180928龙虎榜记录](https://datacenter-web.eastmoney.com/api/data/v1/get?reportName=RPT_DAILYBILLBOARD_DETAILS&columns=SECURITY_CODE%2CSECUCODE%2CSECURITY_NAME_ABBR%2CTRADE_DATE%2CEXPLANATION&filter=%28TRADE_DATE%3D%272018-09-28%27%29&pageSize=200)；获取UTC `2026-09-13T13:49:02.692639+00:00`，SHA-256 `42853b7294c3a036d2fac07958ba255a80a6a9c57c324f4e6e84fb32b9f60de3`，私有缓存 `/private/tmp/issue021-research/top-list-public-all.json`。600318.SH/新力金融记录为日收盘跌幅偏离7%；仅作为独立取样依据，不是Tushare SOURCE。最初like筛选公开请求返回不支持like，保留缓存，不算有效事件结果。官网58/106初次TLS读取失败后换公开读取方式成功，不是账户来源重试。

### 首轮执行前身份

- 完整隔离源码 `/private/tmp/issue021-work-20260913T135006Z`；844个Git管理文件逐字核对，snapshot SHA-256 `726089a21dca660bd00341ab806ed4afdbbbf95c0fb535c899bfbc092f40a359`。
- `mvn -o -f data-plane/pom.xml -Pacceptance verify` exit0/BUILD SUCCESS，1068项后端/包检查，无失败/错误/跳过；前端468通过。独立Node证据78/78通过。普通测试环境去除账户及DB变量；日志 `/private/tmp/issue021-control/build.log`、`node-pre-source.log`。
- productionJarSha256: `78cc20f6d6fda749948ce3648a78003329f51df8a7c4073f7e6f440cef96356a`。
- acceptanceJarSha256: `ef8ec5cda406cf685bd32c073c933259ab21beb6274f9e707a769e3c3ca889b7`。

### BSE直接输入独立固定计划（执行前）

- runId `issue021-bse-source-20260913T135304Z`，caseId `issue021-bse-source-20260913T135304Z-direct`；精确参数 `{"exchange":"BSE","start_date":"20260803","end_date":"20260810"}`，CALENDAR_DATE。旧`trade_cal-bse-direct`同窗口，新合法Envelope响应行数摘要用于区分真实空返回和缺日，旧失败事实不改写。1case/1请求，至少2秒，复用首轮稳定源码/包；本轮独立，失败不污染其他case。尚未执行。
- 私有清单 `/private/tmp/issue021-bse-source-20260913T135304Z/cases.json`，0600/父0700，SHA-256 `3e1f4dae20b67af722eb2d696e66ea75301a7eb6196be82c17a2d795ddd8ca23`。若失败，仅保存固定分类/合法响应计数；HTTP拒绝、空响应和完整性不足按实际事实分开。

### issue021-source-20260913T135006Z 实际结果

- UTC `2026-09-13T13:52:18.041Z`～`2026-09-13T13:53:55.490Z`，exit0/cleanup PASS；privateLogSafe/inputUnchanged均true，源码和两包保持首轮绑定，无TASK/SQL。
- sourceDiffSha256 `ad0a0d6e26d69b16c13108c3d13f4ca78d0494489c2ea18945a66cefa2272ba1`。

| caseId后缀 | 状态 / 请求数 / 成功来源行数 | 安全日期及日历观察 |
| --- | --- | --- |
| `trade_cal-sse-whole` | PASS / 1 / 12 | SOURCE validationStatus=PASS; parameter, envelope and stock scope checks; completeness UNKNOWN; actualDateColumn=cal_date; datePredicate=closed interval; actualDates=20180927,20180928,20180929,20180930,20181001,20181002,20181003,20181004,20181005,20181006,20181007,20181008; uniqueDateCount=12; min=20180927; max=20181008; candidateRowLimit=UNKNOWN; candidateLimitReached=false; calendarExchange=SSE; calendarResponseRowCount=12; calendarOpenDates=20180927,20180928,20181008;; calendarOpenDayCount=3; closedCalendar=false |
| `trade_cal-sse-lower` | PASS / 1 / 1 | SOURCE validationStatus=PASS; parameter, envelope and stock scope checks; completeness UNKNOWN; actualDateColumn=cal_date; datePredicate=closed interval; actualDates=20180927; uniqueDateCount=1; min=20180927; max=20180927; candidateRowLimit=UNKNOWN; candidateLimitReached=false; calendarExchange=SSE; calendarResponseRowCount=1; calendarOpenDates=20180927;; calendarOpenDayCount=1; closedCalendar=false |
| `trade_cal-sse-upper` | PASS / 1 / 1 | SOURCE validationStatus=PASS; parameter, envelope and stock scope checks; completeness UNKNOWN; actualDateColumn=cal_date; datePredicate=closed interval; actualDates=20181008; uniqueDateCount=1; min=20181008; max=20181008; candidateRowLimit=UNKNOWN; candidateLimitReached=false; calendarExchange=SSE; calendarResponseRowCount=1; calendarOpenDates=20181008;; calendarOpenDayCount=1; closedCalendar=false |
| `trade_cal-sse-closed` | PASS / 1 / 7 | SOURCE validationStatus=PASS; parameter, envelope and stock scope checks; completeness UNKNOWN; actualDateColumn=cal_date; datePredicate=closed interval; actualDates=20181001,20181002,20181003,20181004,20181005,20181006,20181007; uniqueDateCount=7; min=20181001; max=20181007; candidateRowLimit=UNKNOWN; candidateLimitReached=false; calendarExchange=SSE; calendarResponseRowCount=7; calendarOpenDates=;; calendarOpenDayCount=0; closedCalendar=true |
| `trade_cal-sse-cross-year` | PASS / 1 / 8 | SOURCE validationStatus=PASS; parameter, envelope and stock scope checks; completeness UNKNOWN; actualDateColumn=cal_date; datePredicate=closed interval; actualDates=20251229,20251230,20251231,20260101,20260102,20260103,20260104,20260105; uniqueDateCount=8; min=20251229; max=20260105; candidateRowLimit=UNKNOWN; candidateLimitReached=false; calendarExchange=SSE; calendarResponseRowCount=8; calendarOpenDates=20251229,20251230,20251231,20260105;; calendarOpenDayCount=4; closedCalendar=false |
| `trade_cal-sse-current` | PASS / 1 / 8 | SOURCE validationStatus=PASS; parameter, envelope and stock scope checks; completeness UNKNOWN; actualDateColumn=cal_date; datePredicate=closed interval; actualDates=20260803,20260804,20260805,20260806,20260807,20260808,20260809,20260810; uniqueDateCount=8; min=20260803; max=20260810; candidateRowLimit=UNKNOWN; candidateLimitReached=false; calendarExchange=SSE; calendarResponseRowCount=8; calendarOpenDates=20260803,20260804,20260805,20260806,20260807,20260810;; calendarOpenDayCount=6; closedCalendar=false |
| `trade_cal-szse-whole` | PASS / 1 / 12 | SOURCE validationStatus=PASS; parameter, envelope and stock scope checks; completeness UNKNOWN; actualDateColumn=cal_date; datePredicate=closed interval; actualDates=20180927,20180928,20180929,20180930,20181001,20181002,20181003,20181004,20181005,20181006,20181007,20181008; uniqueDateCount=12; min=20180927; max=20181008; candidateRowLimit=UNKNOWN; candidateLimitReached=false; calendarExchange=SZSE; calendarResponseRowCount=12; calendarOpenDates=20180927,20180928,20181008;; calendarOpenDayCount=3; closedCalendar=false |
| `trade_cal-szse-lower` | PASS / 1 / 1 | SOURCE validationStatus=PASS; parameter, envelope and stock scope checks; completeness UNKNOWN; actualDateColumn=cal_date; datePredicate=closed interval; actualDates=20180927; uniqueDateCount=1; min=20180927; max=20180927; candidateRowLimit=UNKNOWN; candidateLimitReached=false; calendarExchange=SZSE; calendarResponseRowCount=1; calendarOpenDates=20180927;; calendarOpenDayCount=1; closedCalendar=false |
| `trade_cal-szse-upper` | PASS / 1 / 1 | SOURCE validationStatus=PASS; parameter, envelope and stock scope checks; completeness UNKNOWN; actualDateColumn=cal_date; datePredicate=closed interval; actualDates=20181008; uniqueDateCount=1; min=20181008; max=20181008; candidateRowLimit=UNKNOWN; candidateLimitReached=false; calendarExchange=SZSE; calendarResponseRowCount=1; calendarOpenDates=20181008;; calendarOpenDayCount=1; closedCalendar=false |
| `trade_cal-szse-closed` | PASS / 1 / 7 | SOURCE validationStatus=PASS; parameter, envelope and stock scope checks; completeness UNKNOWN; actualDateColumn=cal_date; datePredicate=closed interval; actualDates=20181001,20181002,20181003,20181004,20181005,20181006,20181007; uniqueDateCount=7; min=20181001; max=20181007; candidateRowLimit=UNKNOWN; candidateLimitReached=false; calendarExchange=SZSE; calendarResponseRowCount=7; calendarOpenDates=;; calendarOpenDayCount=0; closedCalendar=true |
| `trade_cal-szse-cross-year` | PASS / 1 / 8 | SOURCE validationStatus=PASS; parameter, envelope and stock scope checks; completeness UNKNOWN; actualDateColumn=cal_date; datePredicate=closed interval; actualDates=20251229,20251230,20251231,20260101,20260102,20260103,20260104,20260105; uniqueDateCount=8; min=20251229; max=20260105; candidateRowLimit=UNKNOWN; candidateLimitReached=false; calendarExchange=SZSE; calendarResponseRowCount=8; calendarOpenDates=20251229,20251230,20251231,20260105;; calendarOpenDayCount=4; closedCalendar=false |
| `trade_cal-szse-current` | PASS / 1 / 8 | SOURCE validationStatus=PASS; parameter, envelope and stock scope checks; completeness UNKNOWN; actualDateColumn=cal_date; datePredicate=closed interval; actualDates=20260803,20260804,20260805,20260806,20260807,20260808,20260809,20260810; uniqueDateCount=8; min=20260803; max=20260810; candidateRowLimit=UNKNOWN; candidateLimitReached=false; calendarExchange=SZSE; calendarResponseRowCount=8; calendarOpenDates=20260803,20260804,20260805,20260806,20260807,20260810;; calendarOpenDayCount=6; closedCalendar=false |
| `margin-sse-whole` | PASS / 1 / 6 | SOURCE validationStatus=PASS; parameter, envelope and stock scope checks; completeness UNKNOWN; actualDateColumn=trade_date; datePredicate=closed interval; actualDates=20260803,20260804,20260805,20260806,20260807,20260810; uniqueDateCount=6; min=20260803; max=20260810; candidateRowLimit=4000; candidateLimitReached=false |
| `margin-sse-lower` | PASS / 1 / 1 | SOURCE validationStatus=PASS; parameter, envelope and stock scope checks; completeness UNKNOWN; actualDateColumn=trade_date; datePredicate=closed interval; actualDates=20260803; uniqueDateCount=1; min=20260803; max=20260803; candidateRowLimit=4000; candidateLimitReached=false |
| `margin-sse-upper` | PASS / 1 / 1 | SOURCE validationStatus=PASS; parameter, envelope and stock scope checks; completeness UNKNOWN; actualDateColumn=trade_date; datePredicate=closed interval; actualDates=20260810; uniqueDateCount=1; min=20260810; max=20260810; candidateRowLimit=4000; candidateLimitReached=false |
| `margin-szse-whole` | PASS / 1 / 6 | SOURCE validationStatus=PASS; parameter, envelope and stock scope checks; completeness UNKNOWN; actualDateColumn=trade_date; datePredicate=closed interval; actualDates=20260803,20260804,20260805,20260806,20260807,20260810; uniqueDateCount=6; min=20260803; max=20260810; candidateRowLimit=4000; candidateLimitReached=false |
| `margin-szse-lower` | PASS / 1 / 1 | SOURCE validationStatus=PASS; parameter, envelope and stock scope checks; completeness UNKNOWN; actualDateColumn=trade_date; datePredicate=closed interval; actualDates=20260803; uniqueDateCount=1; min=20260803; max=20260803; candidateRowLimit=4000; candidateLimitReached=false |
| `margin-szse-upper` | PASS / 1 / 1 | SOURCE validationStatus=PASS; parameter, envelope and stock scope checks; completeness UNKNOWN; actualDateColumn=trade_date; datePredicate=closed interval; actualDates=20260810; uniqueDateCount=1; min=20260810; max=20260810; candidateRowLimit=4000; candidateLimitReached=false |
| `margin-bse-whole` | PASS / 1 / 6 | SOURCE validationStatus=PASS; parameter, envelope and stock scope checks; completeness UNKNOWN; actualDateColumn=trade_date; datePredicate=closed interval; actualDates=20260803,20260804,20260805,20260806,20260807,20260810; uniqueDateCount=6; min=20260803; max=20260810; candidateRowLimit=4000; candidateLimitReached=false |
| `margin-bse-lower` | PASS / 1 / 1 | SOURCE validationStatus=PASS; parameter, envelope and stock scope checks; completeness UNKNOWN; actualDateColumn=trade_date; datePredicate=closed interval; actualDates=20260803; uniqueDateCount=1; min=20260803; max=20260803; candidateRowLimit=4000; candidateLimitReached=false |
| `margin-bse-upper` | PASS / 1 / 1 | SOURCE validationStatus=PASS; parameter, envelope and stock scope checks; completeness UNKNOWN; actualDateColumn=trade_date; datePredicate=closed interval; actualDates=20260810; uniqueDateCount=1; min=20260810; max=20260810; candidateRowLimit=4000; candidateLimitReached=false |
| `top_list-000007-whole` | PASS / 4 / 3 | SOURCE validationStatus=PASS; parameter, envelope and stock scope checks; completeness UNKNOWN; actualDateColumn=trade_date; datePredicate=closed interval; actualDates=20180927,20180928,20181008; uniqueDateCount=3; min=20180927; max=20181008; candidateRowLimit=10000; candidateLimitReached=false; calendarExchange=SZSE; calendarResponseRowCount=12; calendarOpenDates=20180927,20180928,20181008;; calendarOpenDayCount=3; closedCalendar=false |
| `top_list-000007-event` | PASS / 2 / 1 | SOURCE validationStatus=PASS; parameter, envelope and stock scope checks; completeness UNKNOWN; actualDateColumn=trade_date; datePredicate=closed interval; actualDates=20180928; uniqueDateCount=1; min=20180928; max=20180928; candidateRowLimit=10000; candidateLimitReached=false; calendarExchange=SZSE; calendarResponseRowCount=1; calendarOpenDates=20180928;; calendarOpenDayCount=1; closedCalendar=false |
| `top_list-000007-event-at-lower` | PASS / 3 / 2 | SOURCE validationStatus=PASS; parameter, envelope and stock scope checks; completeness UNKNOWN; actualDateColumn=trade_date; datePredicate=closed interval; actualDates=20180928,20181008; uniqueDateCount=2; min=20180928; max=20181008; candidateRowLimit=10000; candidateLimitReached=false; calendarExchange=SZSE; calendarResponseRowCount=11; calendarOpenDates=20180928,20181008;; calendarOpenDayCount=2; closedCalendar=false |
| `top_list-000007-event-at-upper` | PASS / 3 / 2 | SOURCE validationStatus=PASS; parameter, envelope and stock scope checks; completeness UNKNOWN; actualDateColumn=trade_date; datePredicate=closed interval; actualDates=20180927,20180928; uniqueDateCount=2; min=20180927; max=20180928; candidateRowLimit=10000; candidateLimitReached=false; calendarExchange=SZSE; calendarResponseRowCount=2; calendarOpenDates=20180927,20180928;; calendarOpenDayCount=2; closedCalendar=false |
| `top_list-000007-closed` | PASS / 1 / 0 | SOURCE validationStatus=PASS; parameter, envelope and stock scope checks; completeness UNKNOWN; actualDateColumn=trade_date; datePredicate=closed interval; actualDates=; uniqueDateCount=0; min=none; max=none; candidateRowLimit=10000; candidateLimitReached=false; calendarExchange=SZSE; calendarResponseRowCount=7; calendarOpenDates=;; calendarOpenDayCount=0; closedCalendar=true |
| `top_list-600318-whole` | PASS / 4 / 1 | SOURCE validationStatus=PASS; parameter, envelope and stock scope checks; completeness UNKNOWN; actualDateColumn=trade_date; datePredicate=closed interval; actualDates=20180928; uniqueDateCount=1; min=20180928; max=20180928; candidateRowLimit=10000; candidateLimitReached=false; calendarExchange=SSE; calendarResponseRowCount=12; calendarOpenDates=20180927,20180928,20181008;; calendarOpenDayCount=3; closedCalendar=false |
| `top_list-600318-event` | PASS / 2 / 1 | SOURCE validationStatus=PASS; parameter, envelope and stock scope checks; completeness UNKNOWN; actualDateColumn=trade_date; datePredicate=closed interval; actualDates=20180928; uniqueDateCount=1; min=20180928; max=20180928; candidateRowLimit=10000; candidateLimitReached=false; calendarExchange=SSE; calendarResponseRowCount=1; calendarOpenDates=20180928;; calendarOpenDayCount=1; closedCalendar=false |
| `top_list-600318-event-at-lower` | PASS / 3 / 1 | SOURCE validationStatus=PASS; parameter, envelope and stock scope checks; completeness UNKNOWN; actualDateColumn=trade_date; datePredicate=closed interval; actualDates=20180928; uniqueDateCount=1; min=20180928; max=20180928; candidateRowLimit=10000; candidateLimitReached=false; calendarExchange=SSE; calendarResponseRowCount=11; calendarOpenDates=20180928,20181008;; calendarOpenDayCount=2; closedCalendar=false |
| `top_list-600318-event-at-upper` | PASS / 3 / 1 | SOURCE validationStatus=PASS; parameter, envelope and stock scope checks; completeness UNKNOWN; actualDateColumn=trade_date; datePredicate=closed interval; actualDates=20180928; uniqueDateCount=1; min=20180928; max=20180928; candidateRowLimit=10000; candidateLimitReached=false; calendarExchange=SSE; calendarResponseRowCount=2; calendarOpenDates=20180927,20180928;; calendarOpenDayCount=2; closedCalendar=false |
| `top_list-600318-closed` | PASS / 1 / 0 | SOURCE validationStatus=PASS; parameter, envelope and stock scope checks; completeness UNKNOWN; actualDateColumn=trade_date; datePredicate=closed interval; actualDates=; uniqueDateCount=0; min=none; max=none; candidateRowLimit=10000; candidateLimitReached=false; calendarExchange=SSE; calendarResponseRowCount=7; calendarOpenDates=;; calendarOpenDayCount=0; closedCalendar=true |

### issue021-bse-source-20260913T135304Z 实际结果

- UTC `2026-09-13T13:55:24.059Z`～`2026-09-13T13:55:27.175Z`，exit1/cleanup PASS；privateLogSafe/inputUnchanged均true，源码和两包保持首轮绑定，无TASK/SQL。
- sourceDiffSha256 `ad0a0d6e26d69b16c13108c3d13f4ca78d0494489c2ea18945a66cefa2272ba1`。

| caseId后缀 | 状态 / 请求数 / 成功来源行数 | 安全日期及日历观察 |
| --- | --- | --- |
| `direct` | FAILED / 1 / 0 | SOURCE validationStatus=FAILED; parameter, envelope and stock scope checks; completeness UNKNOWN; actualDateColumn=cal_date; datePredicate=closed interval; actualDates=; uniqueDateCount=0; min=none; max=none; candidateRowLimit=UNKNOWN; candidateLimitReached=false; calendarExchange=BSE; calendarResponseRowCount=0; calendarOpenDates=unconfirmed;; calendarOpenDayCount=unconfirmed; closedCalendar=false |

### BJ候选独立固定计划（执行前）

- 首轮完整退出0，SSE/SZSE六组开市日期逐项一致；依据官网26同历说明，在测试Probe固定BJ→SSE候选。仅日历使用SSE，股票仍为920008.BJ；生产拒绝不变，未来候选版本与TASK/SQL由ISSUE-026验收。T13/T14已先限定修订，未改YAML、业务键或生产策略。
- BJ测试先RED36项1失败（当前本地拒绝），GREEN170/170（Probe36、Calendar4、Policies109、Download21），无失败/错误/跳过，exit0；日志 `/private/tmp/issue021-control/bj-red.log`、`bj-green.log`。
- 新runId `issue021-bj-source-20260913T135704Z`，7case、预计15请求；私有输入 `/private/tmp/issue021-bj-source-20260913T135704Z/cases.json`，0600/父0700，SHA-256 `876374c529d95e684df05844bbcc4f6d9bfbb1ff624b506358850c8f6d207e2f`。至少2秒/30分钟/5000次，失败停轮，尚未执行。
- [东方财富20240930龙虎榜](https://datacenter-web.eastmoney.com/api/data/v1/get?reportName=RPT_DAILYBILLBOARD_DETAILS&columns=SECURITY_CODE%2CSECUCODE%2CSECURITY_NAME_ABBR%2CTRADE_DATE%2CEXPLANATION&filter=%28TRADE_DATE%3D%272024-09-30%27%29&pageSize=500)；UTC `2026-09-13T13:51:40.136004+00:00`；SHA-256 `a45a0113d84925730e22823dd0c2c0bf29838ce65ec770924e7966c9fd8d0cb1`，缓存 `/private/tmp/issue021-research/bj-public-all.json`。113条公开记录中有920008.BJ/成电光信“当日收盘价涨幅达到20%的前5只股票”。采用该明确代码与日期，不将其他股票的新旧代码互换；公开组合条件先报语法错误，缓存保留，后读取同日列表，未换日搜成功。

| caseId后缀 | 精确参数 | 预期观察 |
| --- | --- | --- |
| `calendar-sse` | `{"exchange":"SSE","start_date":"20240927","end_date":"20241008"}` | 完整日历并比较两交易所开市日期 |
| `calendar-szse` | `{"exchange":"SZSE","start_date":"20240927","end_date":"20241008"}` | 完整日历并比较两交易所开市日期 |
| `920008-whole` | `{"ts_code":"920008.BJ","start_date":"20240927","end_date":"20241008"}` | 日历SSE/证券BJ；全部开市日执行，非空或空均按实际记录 |
| `920008-event` | `{"ts_code":"920008.BJ","start_date":"20240930","end_date":"20240930"}` | 日历SSE/证券BJ；全部开市日执行，非空或空均按实际记录 |
| `920008-event-at-lower` | `{"ts_code":"920008.BJ","start_date":"20240930","end_date":"20241008"}` | 日历SSE/证券BJ；全部开市日执行，非空或空均按实际记录 |
| `920008-event-at-upper` | `{"ts_code":"920008.BJ","start_date":"20240927","end_date":"20240930"}` | 日历SSE/证券BJ；全部开市日执行，非空或空均按实际记录 |
| `920008-closed` | `{"ts_code":"920008.BJ","start_date":"20241001","end_date":"20241007"}` | 日历SSE/证券BJ；全部开市日执行，非空或空均按实际记录 |

### BJ轮执行前身份

- 完整隔离源码 `/private/tmp/issue021-bj-work-20260913T135704Z`；844个Git管理文件逐字核对，snapshot SHA-256 `0f2af81c36c18682e72ec38f95f2cfcc4f6856806b58fbdd70d1aa4cb194cd6c`。首轮隔离源码/包保留，没有修改它们。
- `mvn -o -f data-plane/pom.xml -Pacceptance verify` exit0/BUILD SUCCESS，1069项后端/包检查，无失败/错误/跳过；前端468通过。Node证据78/78通过；普通测试环境去除账户和DB变量。日志 `/private/tmp/issue021-control/bj-build.log`、`bj-node-pre-source.log`。
- productionJarSha256: `d96c2c25bc3f80ff17370e4e7cc5021e28035da3bdb852729f247de8dfd53a6f`。
- acceptanceJarSha256: `0cd3d34613222de90b21488876e3016006ecc8dd2ac7d64caaffdce885d7b3dd`。

### issue021-bj-source-20260913T135704Z 实际结果

- UTC `2026-09-13T13:58:36.404Z`～`2026-09-13T13:59:08.815Z`，exit0/cleanup PASS，privateLogSafe/inputUnchanged均true；7case/15请求全部PASS。源码指纹 `db62c1ff91854e13c359bcbb0b63a6e363f8944bc31f49a52912bb7522c211be`，生产/验收包保持本轮绑定，SOURCE无TASK/SQL。

| caseId后缀 | 状态 / 请求数 / 来源行数 | 安全日期及日历观察 |
| --- | --- | --- |
| `calendar-sse` | PASS / 1 / 12 | SOURCE validationStatus=PASS; parameter, envelope and stock scope checks; completeness UNKNOWN; actualDateColumn=cal_date; datePredicate=closed interval; actualDates=20240927,20240928,20240929,20240930,20241001,20241002,20241003,20241004,20241005,20241006,20241007,20241008; uniqueDateCount=12; min=20240927; max=20241008; candidateRowLimit=UNKNOWN; candidateLimitReached=false; calendarExchange=SSE; calendarResponseRowCount=12; calendarOpenDates=20240927,20240930,20241008;; calendarOpenDayCount=3; closedCalendar=false |
| `calendar-szse` | PASS / 1 / 12 | SOURCE validationStatus=PASS; parameter, envelope and stock scope checks; completeness UNKNOWN; actualDateColumn=cal_date; datePredicate=closed interval; actualDates=20240927,20240928,20240929,20240930,20241001,20241002,20241003,20241004,20241005,20241006,20241007,20241008; uniqueDateCount=12; min=20240927; max=20241008; candidateRowLimit=UNKNOWN; candidateLimitReached=false; calendarExchange=SZSE; calendarResponseRowCount=12; calendarOpenDates=20240927,20240930,20241008;; calendarOpenDayCount=3; closedCalendar=false |
| `920008-whole` | PASS / 4 / 1 | SOURCE validationStatus=PASS; parameter, envelope and stock scope checks; completeness UNKNOWN; actualDateColumn=trade_date; datePredicate=closed interval; actualDates=20240930; uniqueDateCount=1; min=20240930; max=20240930; candidateRowLimit=10000; candidateLimitReached=false; calendarExchange=SSE; calendarResponseRowCount=12; calendarOpenDates=20240927,20240930,20241008;; calendarOpenDayCount=3; closedCalendar=false |
| `920008-event` | PASS / 2 / 1 | SOURCE validationStatus=PASS; parameter, envelope and stock scope checks; completeness UNKNOWN; actualDateColumn=trade_date; datePredicate=closed interval; actualDates=20240930; uniqueDateCount=1; min=20240930; max=20240930; candidateRowLimit=10000; candidateLimitReached=false; calendarExchange=SSE; calendarResponseRowCount=1; calendarOpenDates=20240930;; calendarOpenDayCount=1; closedCalendar=false |
| `920008-event-at-lower` | PASS / 3 / 1 | SOURCE validationStatus=PASS; parameter, envelope and stock scope checks; completeness UNKNOWN; actualDateColumn=trade_date; datePredicate=closed interval; actualDates=20240930; uniqueDateCount=1; min=20240930; max=20240930; candidateRowLimit=10000; candidateLimitReached=false; calendarExchange=SSE; calendarResponseRowCount=9; calendarOpenDates=20240930,20241008;; calendarOpenDayCount=2; closedCalendar=false |
| `920008-event-at-upper` | PASS / 3 / 1 | SOURCE validationStatus=PASS; parameter, envelope and stock scope checks; completeness UNKNOWN; actualDateColumn=trade_date; datePredicate=closed interval; actualDates=20240930; uniqueDateCount=1; min=20240930; max=20240930; candidateRowLimit=10000; candidateLimitReached=false; calendarExchange=SSE; calendarResponseRowCount=4; calendarOpenDates=20240927,20240930;; calendarOpenDayCount=2; closedCalendar=false |
| `920008-closed` | PASS / 1 / 0 | SOURCE validationStatus=PASS; parameter, envelope and stock scope checks; completeness UNKNOWN; actualDateColumn=trade_date; datePredicate=closed interval; actualDates=; uniqueDateCount=0; min=none; max=none; candidateRowLimit=10000; candidateLimitReached=false; calendarExchange=SSE; calendarResponseRowCount=7; calendarOpenDates=;; calendarOpenDayCount=0; closedCalendar=true |

### ISSUE-021 来源结论

- 本issue三轮39case/63请求：38 PASS（35非空、3完整休市零证券请求）及1 BSE直接完整性FAILED。两成功run exit0，BSE负例run exit1，三个cleanup均PASS；无自动重试、无TASK/SQL。新唯一索引15轮/570case、累计542请求；旧12轮/531case及其他37接口逐对象核对不变。
- SSE/SZSE 14项完整日历含2018国庆、2024BJ事件区间、2025→2026跨年和2026当前窗口；每自然日唯一无缺失，两交易所对应开市日期一致。2018整段12自然日/3开市，国庆7自然日/0开市；跨年8自然日/4开市。
- BSE新结构合法的响应确为0行，故本窗口不能形成完整日历；不推断HTTP拒绝或全部历史支持性。原BSE失败没有响应计数摘要，原记录不回填。直接BSE继续拒绝；BJ参照SSE属于不同请求含义。
- margin保留exchange_id，SSE/SZSE/BSE各整段6行、两端各1行，均逐行正确归属且低于官网4000；并未将BSE融资结果用于证明BSE日历支持。
- top_list 000007.SZ/600318.SH各有4项非空来源及1完整休市；每日按完整日历执行，前者整段3行、后者1行，边界都包含20180928事件。920008.BJ候选日历SSE，每个开市日仍请求BJ代码，整段/单日/两端各1行，日期20240930；全休市仅1日历请求/0证券请求。SH/SZ/BJ生产任务与持久化日期序列仍须ISSUE-026验证。
- 三接口当前引用改为本issue完整新SOURCE及原干净SINGLE TASK；旧失败/空样本从当前引用移出但原runs/cases全部保留，新BSE负例仍在当前引用，trade_cal sourceStatus=FAILED；margin/top_list sourceStatus=PASS。三接口仍NEEDS_VERIFICATION/v1，4 AVAILABLE不变。这个来源分层结论不把BSE负例隐藏，也不单独开放任何接口。

### ISSUE-021 最终验收

- 最终Java相关回归170/170：Probe36、TradeCalendar4、Policies109、Download21，失败/错误/跳过0，exit0；`mvn -o -f data-plane/pom.xml -pl tensor-plugin-tushare -am -Dtest=TushareRangeSourceProbeTest,TushareTradeCalendarTest,TushareBatchPoliciesTest,TushareBatchDownloadTest -Dsurefire.failIfNoSpecifiedTests=false test`，日志 `/private/tmp/issue021-control/bj-green.log`。两次完整隔离acceptance构建的1068/1069后端及各468前端检查见执行前身份，无需重跑账户。
- 最终追加新SOURCE并清理当前引用后，Node独立预期仍要求三接口引用旧失败轮，77/78、exit1；日志 `node-final.log`。核对实际新证据后仅同步这三项来源计数/状态/负例预期，把“没有RANGE TASK不得开放”回归扩展到trade_cal/margin/top_list。未修改生产或harness，未改历史run；最终 `node --test control-plane/e2e/tushare-range-evidence.test.js` 78/78、失败/跳过0、exit0，日志 `node-green-final.log`。这次只同步独立离线预期，不影响此前真实Probe源码或包身份。
- `/private/tmp/issue021-control/verify-handoff.mjs` 检查完整索引、三轮与私有原对象逐字一致、38项SOURCE→TASK参数和run/case身份、15项交易日叶子和请求计数、3闭市计划、BSE负例拒绝，全部通过，日志 `handoff.log`。私有 `issue026-candidate-inputs.json` 是离线可审阅输入，未提交任务或另建实际run；持久身份与精确参数仍以本登记和唯一JSON为准。
- 旧12轮/531case、顶层哈希与其他37接口逐对象保持；新索引15轮/570case、累计542次请求，4 AVAILABLE/30 NEEDS_VERIFICATION/6 SINGLE_ONLY保持。BSE负例保留FAILED；三接口生产v1未改变，原74个SINGLE和25个RANGE任务未重跑。
- 按requesting-code-review进行三阶段只读复审：摘要和BJ候选无必要代码修复；最终发现报告当前表的margin/top_list SOURCE两格尚为旧状态，已改为PASS，trade_cal仍FAILED。全部40行SOURCE/TASK状态与处置逐项对齐JSON，Markdown文件目标存在，差异空白检查通过。
- 对照[专属设计](../task-designs/ISSUE-021-design.md)及issue三项关闭条件：沪深完整日历/休市与BJ/BSE独立结论成立；margin归属和参数保持；top_list两股票及额外BJ非空边界成立，38项匹配输入已交ISSUE-026。记录ISSUE-021 `IN_PROGRESS → COMPLETED`；生产映射、候选版本、真实TASK/SQL及六门禁由ISSUE-026执行。T13/T14与母issue保持剩余验收，未启动ISSUE-022。

## ISSUE-022 第一轮固定来源计划

2026-09-13用户要求“完成issue22”，[专属设计](../task-designs/ISSUE-022-design.md)和[计划](../superpowers/plans/2026-09-13-issue-022.md)已完成。沿用日期轴/完整性合同；先RED39项2失败（缺新增指标，exit1），再GREEN后重建隔离源码。以下计划在真实调用前登记；本段不填PASS。

### 依据核对

复核2026-09-12公开GET的原HTML缓存，获取时间和正文引文仍见[原官方章节](ISSUE-018-range-acceptance.md#官方依据)，没有把本次缓存复核写成新HTTP。五页SHA-256与原登记相同：

| API/官网 | 缓存HTML SHA-256 |
| --- | --- |
| [weekly](https://tushare.pro/document/2?doc_id=144) | `27b02e23f287bdf4d21f1ac90707ad286b945dac749b8915ce073ebdb01da140` |
| [monthly](https://tushare.pro/document/2?doc_id=145) | `3cbffadc8002fa2970fe4e3fb34e08c5db0fbf3882f974c24f5860c9250e6f24` |
| [fina_indicator](https://tushare.pro/document/2?doc_id=79) | `36e943d50ae8c175422a9e14b702c21bdeaa53716416ce53ac3e892eb5794db6` |
| [stk_holdernumber](https://tushare.pro/document/2?doc_id=166) | `92003ae3541b63110c9efac2405934892e1c55aedcbd874a06ade99e2f62d8fe` |
| [new_share](https://tushare.pro/document/2?doc_id=123) | `4e3cb17e93d8a532d460648a9ff477213ec90da1825e675b0428b2599694a8c8` |

weekly原文“每周最后一个交易日”、单次最大6000；monthly“每月最后一个交易日”、单次最大4500，但样例20180930仍冲突。fina_indicator明确报告期开始/结束，最多100；stk_holdernumber公告区间、输出end_date为截止日、输入enddate另义，最大3000；new_share为上网发行起止、ipo_date申购/issue_date上市，最大2000。限量仍为既有候选，不凭本次设计开放生产。

weekly另据ISSUE-021两交易所20240930～20241007真实完整日历（仅0930开市）；monthly据旧历史轮000001.SZ的20180928非空/0930空和官网145的20181031。fina_indicator两股票旧有效年窗实际集合含20250331/20251231。新股选官网已列明两端及同一行日期的历史样例；股东户数第二股票先固定同公告窗，只有取得实际日期后才能为缺口登记新轮。

- runId `issue022-source-20260913T141544Z`；36case，预计36次原生来源请求（含4项辅助日历）；每次至少2000ms，30分钟/5000请求，失败停轮，无自动重试。
- 私有清单 `/private/tmp/issue022-source-20260913T141544Z/cases.json`，普通0600/父0700，SHA-256 `4e01aad219e114549742a89734b1f0d06e3110dc5cd3fb55a2fea50cd8b3c517`。隔离源码及两包实际身份在执行前另记，SOURCE不连接数据库。
- 完整caseId均为`issue022-source-20260913T141544Z-`加下表后缀；全部RANGE，dateAxis依专属设计及唯一索引，start/end与params对应。

| caseId后缀 | API | 精确params | 依据与观察 |
| --- | --- | --- | --- |
| `cal-week-SSE` | `trade_cal` | `{"exchange":"SSE","start_date":"20240923","end_date":"20241013"}` | 官网144+ISSUE-021已有国庆完整日历；按每周真实最后开市日对照。 |
| `cal-month-SSE` | `trade_cal` | `{"exchange":"SSE","start_date":"20180901","end_date":"20181031"}` | 官网145的9/10月样例、官网40及旧9月真实样本；按每月最后开市日对照。 |
| `cal-week-SZSE` | `trade_cal` | `{"exchange":"SZSE","start_date":"20240923","end_date":"20241013"}` | 官网144+ISSUE-021已有国庆完整日历；按每周真实最后开市日对照。 |
| `cal-month-SZSE` | `trade_cal` | `{"exchange":"SZSE","start_date":"20180901","end_date":"20181031"}` | 官网145的9/10月样例、官网40及旧9月真实样本；按每月最后开市日对照。 |
| `weekly-000001-whole` | `weekly` | `{"ts_code":"000001.SZ","start_date":"20240927","end_date":"20241011"}` | 整段/两端/国庆周一最后交易日/休市周五对照；空仍未确认。 |
| `weekly-000001-lower` | `weekly` | `{"ts_code":"000001.SZ","start_date":"20240927","end_date":"20240927"}` | 整段/两端/国庆周一最后交易日/休市周五对照；空仍未确认。 |
| `weekly-000001-upper` | `weekly` | `{"ts_code":"000001.SZ","start_date":"20241011","end_date":"20241011"}` | 整段/两端/国庆周一最后交易日/休市周五对照；空仍未确认。 |
| `weekly-000001-holiday-last` | `weekly` | `{"ts_code":"000001.SZ","start_date":"20240930","end_date":"20240930"}` | 整段/两端/国庆周一最后交易日/休市周五对照；空仍未确认。 |
| `weekly-000001-holiday-friday` | `weekly` | `{"ts_code":"000001.SZ","start_date":"20241004","end_date":"20241004"}` | 整段/两端/国庆周一最后交易日/休市周五对照；空仍未确认。 |
| `monthly-000001-whole` | `monthly` | `{"ts_code":"000001.SZ","start_date":"20180928","end_date":"20181031"}` | 官网145同两月份及旧000001.SZ的28日非空/30日空；两股票独立请求。 |
| `monthly-000001-lower` | `monthly` | `{"ts_code":"000001.SZ","start_date":"20180928","end_date":"20180928"}` | 官网145同两月份及旧000001.SZ的28日非空/30日空；两股票独立请求。 |
| `monthly-000001-upper` | `monthly` | `{"ts_code":"000001.SZ","start_date":"20181031","end_date":"20181031"}` | 官网145同两月份及旧000001.SZ的28日非空/30日空；两股票独立请求。 |
| `monthly-000001-natural-end` | `monthly` | `{"ts_code":"000001.SZ","start_date":"20180930","end_date":"20180930"}` | 官网145同两月份及旧000001.SZ的28日非空/30日空；两股票独立请求。 |
| `indicator-000001-whole` | `fina_indicator` | `{"ts_code":"000001.SZ","start_date":"20250331","end_date":"20251231"}` | 旧两股票2025报告期实际集合；补两端及同一行公告在外。 |
| `indicator-000001-lower` | `fina_indicator` | `{"ts_code":"000001.SZ","start_date":"20250331","end_date":"20250331"}` | 旧两股票2025报告期实际集合；补两端及同一行公告在外。 |
| `indicator-000001-upper` | `fina_indicator` | `{"ts_code":"000001.SZ","start_date":"20251231","end_date":"20251231"}` | 旧两股票2025报告期实际集合；补两端及同一行公告在外。 |
| `holder-000001-whole` | `stk_holdernumber` | `{"ts_code":"000001.SZ","start_date":"20260801","end_date":"20260831"}` | 旧000001.SZ公告20260815；600000.SH沿用原同公告窗独立取证，未知不预填。 |
| `weekly-600000-whole` | `weekly` | `{"ts_code":"600000.SH","start_date":"20240927","end_date":"20241011"}` | 整段/两端/国庆周一最后交易日/休市周五对照；空仍未确认。 |
| `weekly-600000-lower` | `weekly` | `{"ts_code":"600000.SH","start_date":"20240927","end_date":"20240927"}` | 整段/两端/国庆周一最后交易日/休市周五对照；空仍未确认。 |
| `weekly-600000-upper` | `weekly` | `{"ts_code":"600000.SH","start_date":"20241011","end_date":"20241011"}` | 整段/两端/国庆周一最后交易日/休市周五对照；空仍未确认。 |
| `weekly-600000-holiday-last` | `weekly` | `{"ts_code":"600000.SH","start_date":"20240930","end_date":"20240930"}` | 整段/两端/国庆周一最后交易日/休市周五对照；空仍未确认。 |
| `weekly-600000-holiday-friday` | `weekly` | `{"ts_code":"600000.SH","start_date":"20241004","end_date":"20241004"}` | 整段/两端/国庆周一最后交易日/休市周五对照；空仍未确认。 |
| `monthly-600000-whole` | `monthly` | `{"ts_code":"600000.SH","start_date":"20180928","end_date":"20181031"}` | 官网145同两月份及旧000001.SZ的28日非空/30日空；两股票独立请求。 |
| `monthly-600000-lower` | `monthly` | `{"ts_code":"600000.SH","start_date":"20180928","end_date":"20180928"}` | 官网145同两月份及旧000001.SZ的28日非空/30日空；两股票独立请求。 |
| `monthly-600000-upper` | `monthly` | `{"ts_code":"600000.SH","start_date":"20181031","end_date":"20181031"}` | 官网145同两月份及旧000001.SZ的28日非空/30日空；两股票独立请求。 |
| `monthly-600000-natural-end` | `monthly` | `{"ts_code":"600000.SH","start_date":"20180930","end_date":"20180930"}` | 官网145同两月份及旧000001.SZ的28日非空/30日空；两股票独立请求。 |
| `indicator-600000-whole` | `fina_indicator` | `{"ts_code":"600000.SH","start_date":"20250331","end_date":"20251231"}` | 旧两股票2025报告期实际集合；补两端及同一行公告在外。 |
| `indicator-600000-lower` | `fina_indicator` | `{"ts_code":"600000.SH","start_date":"20250331","end_date":"20250331"}` | 旧两股票2025报告期实际集合；补两端及同一行公告在外。 |
| `indicator-600000-upper` | `fina_indicator` | `{"ts_code":"600000.SH","start_date":"20251231","end_date":"20251231"}` | 旧两股票2025报告期实际集合；补两端及同一行公告在外。 |
| `holder-600000-whole` | `stk_holdernumber` | `{"ts_code":"600000.SH","start_date":"20260801","end_date":"20260831"}` | 旧000001.SZ公告20260815；600000.SH沿用原同公告窗独立取证，未知不预填。 |
| `holder-000001-event` | `stk_holdernumber` | `{"ts_code":"000001.SZ","start_date":"20260815","end_date":"20260815"}` | 旧000001.SZ公告20260815明确观察；单日及事件在两端，同一行截止日比较。 |
| `holder-000001-lower-edge` | `stk_holdernumber` | `{"ts_code":"000001.SZ","start_date":"20260815","end_date":"20260831"}` | 旧000001.SZ公告20260815明确观察；单日及事件在两端，同一行截止日比较。 |
| `holder-000001-upper-edge` | `stk_holdernumber` | `{"ts_code":"000001.SZ","start_date":"20260801","end_date":"20260815"}` | 旧000001.SZ公告20260815明确观察；单日及事件在两端，同一行截止日比较。 |
| `ipo-whole` | `new_share` | `{"start_date":"20180905","end_date":"20180927"}` | 官网123：鹏鼎控股申购0905/上市0918；蠡湖股份、迈瑞医疗申购0927/上市1015、1016。原非股票范围。 |
| `ipo-lower` | `new_share` | `{"start_date":"20180905","end_date":"20180905"}` | 官网123：鹏鼎控股申购0905/上市0918；蠡湖股份、迈瑞医疗申购0927/上市1015、1016。原非股票范围。 |
| `ipo-upper` | `new_share` | `{"start_date":"20180927","end_date":"20180927"}` | 官网123：鹏鼎控股申购0905/上市0918；蠡湖股份、迈瑞医疗申购0927/上市1015、1016。原非股票范围。 |

### ISSUE-022 第一轮执行前身份

- 完整隔离源码`/private/tmp/issue022-work-20260913T141544Z`；846个Git管理文件逐字一致，snapshot SHA-256 `664dda58f5f3db81afa12ab677c03842374d855e5729f88dbc57cc345c89ab67`。复用已缓存Node工具，所有源码与两包由本轮Maven重新构建；运行期间只更新主工作树文档。
- 专项RED39项2失败；Probe GREEN39/39。第一次相关测试因沙箱拒绝WireMock端口21项启动错误（Operation not permitted），日志green.log保留；允许本地端口后同命令173/173通过（Probe39、Calendar4、Policies109、Download21），exit0、零失败/错误/跳过。环境移除账户和DB变量，日志`/private/tmp/issue022-control/green-unrestricted.log`。
- 隔离`mvn -o -f data-plane/pom.xml -Pacceptance verify` exit0/BUILD SUCCESS，1072项后端/包检查（Surefire1065、Failsafe7），468项前端通过；Node证据78/78、exit0。日志`/private/tmp/issue022-control/build.log`及`node-pre-source.log`。仅改测试Probe和文档，无生产源码六门禁的重复触发。
- sourceDiff SHA-256 `490f99b2827718d63b0f38f397c69d60497edae243e59af6bbe378284e26dccc`；生产包 `38a99f9f6037313eb5ae1829fbd298da4d8cdc64120668c5c507a0e08afdda44`；验收包 `efe8ea0bebade6dc9c7093c61aacbb762dcfa1755f543625f39a3c53aebdf3a9`。manifest/examples由wrapper实际记录并与既有固定输入核对。
- 独立设计/实现初审无Critical/Important；月线000001.SZ/20180928与旧干净来源同参，消费者会绑定最早有效run，交付必须记录实际绑定而非声称新run取代旧run。

### issue022-source-20260913T141544Z 实际结果

- UTC `2026-09-13T14:18:57.329Z`～`2026-09-13T14:20:12.449Z`；exit 0、cleanup PASS；36case/36请求，状态{'PASS': 32, 'EVIDENCE_MISSING': 4}。原始安全对象与输入逐项相符，完整追加唯一索引，不改旧状态。
- 来源指纹 `490f99b2827718d63b0f38f397c69d60497edae243e59af6bbe378284e26dccc`，生产包 `38a99f9f6037313eb5ae1829fbd298da4d8cdc64120668c5c507a0e08afdda44`，验收包 `efe8ea0bebade6dc9c7093c61aacbb762dcfa1755f543625f39a3c53aebdf3a9`；manifest `386f46a99b6605e203129836d7a744b96b65304307f52991dd8bba6fd1870984`，examples `6d4c74a1a539b59ac20fb0cbd3ba1fba0954c40ef1209b652f7dcc2192ec932f`。wrapper输入/包/源码稳定与日志秘密扫描均按实际清理结果记录；SOURCE的TASK/SQL字段为null。

| caseId后缀 | 状态 | 行数 | 实际主轴日期 | 同一行计数 valid/unavailable/different/outside |
| --- | --- | ---: | --- | --- |
| `cal-week-SSE` | PASS | 21 | 20240923～20241013（21日，完整集合见JSON） | — |
| `cal-month-SSE` | PASS | 61 | 20180901～20181031（61日，完整集合见JSON） | — |
| `cal-week-SZSE` | PASS | 21 | 20240923～20241013（21日，完整集合见JSON） | — |
| `cal-month-SZSE` | PASS | 61 | 20180901～20181031（61日，完整集合见JSON） | — |
| `weekly-000001-whole` | PASS | 3 | 20240927,20240930,20241011 | — |
| `weekly-000001-lower` | PASS | 1 | 20240927 | — |
| `weekly-000001-upper` | PASS | 1 | 20241011 | — |
| `weekly-000001-holiday-last` | PASS | 1 | 20240930 | — |
| `weekly-000001-holiday-friday` | EVIDENCE_MISSING | 0 | 空 | — |
| `monthly-000001-whole` | PASS | 2 | 20180928,20181031 | — |
| `monthly-000001-lower` | PASS | 1 | 20180928 | — |
| `monthly-000001-upper` | PASS | 1 | 20181031 | — |
| `monthly-000001-natural-end` | EVIDENCE_MISSING | 0 | 空 | — |
| `indicator-000001-whole` | PASS | 5 | 20250331,20250630,20250930,20251231 | 5/0/—/1 |
| `indicator-000001-lower` | PASS | 2 | 20250331 | 2/0/—/2 |
| `indicator-000001-upper` | PASS | 1 | 20251231 | 1/0/—/1 |
| `holder-000001-whole` | PASS | 1 | 20260815 | 1/0/1/1 |
| `weekly-600000-whole` | PASS | 3 | 20240927,20240930,20241011 | — |
| `weekly-600000-lower` | PASS | 1 | 20240927 | — |
| `weekly-600000-upper` | PASS | 1 | 20241011 | — |
| `weekly-600000-holiday-last` | PASS | 1 | 20240930 | — |
| `weekly-600000-holiday-friday` | EVIDENCE_MISSING | 0 | 空 | — |
| `monthly-600000-whole` | PASS | 2 | 20180928,20181031 | — |
| `monthly-600000-lower` | PASS | 1 | 20180928 | — |
| `monthly-600000-upper` | PASS | 1 | 20181031 | — |
| `monthly-600000-natural-end` | EVIDENCE_MISSING | 0 | 空 | — |
| `indicator-600000-whole` | PASS | 4 | 20250331,20250630,20250930,20251231 | 4/0/—/1 |
| `indicator-600000-lower` | PASS | 1 | 20250331 | 1/0/—/1 |
| `indicator-600000-upper` | PASS | 1 | 20251231 | 1/0/—/1 |
| `holder-600000-whole` | PASS | 1 | 20260828 | 1/0/1/1 |
| `holder-000001-event` | PASS | 1 | 20260815 | 1/0/1/1 |
| `holder-000001-lower-edge` | PASS | 1 | 20260815 | 1/0/1/1 |
| `holder-000001-upper-edge` | PASS | 1 | 20260815 | 1/0/1/1 |
| `ipo-whole` | PASS | 10 | 20180905,20180906,20180907,20180911,20180912,20180913,20180927 | 10/0/10/2 |
| `ipo-lower` | PASS | 1 | 20180905 | 1/0/1/1 |
| `ipo-upper` | PASS | 2 | 20180927 | 2/0/2/2 |

### ISSUE-022 股东户数第二股票边界固定计划

- 第一轮36case/36请求已完成，32 PASS、4个空对照EVIDENCE_MISSING，exit0/cleanup PASS，输入稳定。两股票周线主轴20240927/20240930/20241011、月线20180928/20181031；fina_indicator两端有效，000001.SZ下端同报告期2条不去重。
- 依据第一轮`issue022-source-20260913T141544Z-holder-600000-whole`真实唯一公告日20260828，固定600000.SH公告单日、事件位于下边界和上边界三项。该行ann_date/end_date有效且不同、公告在内而截止日在外；不是换日期直到成功，不重跑第一轮。
- 新runId `issue022-holder-source-20260913T142200Z`；3case/3请求，至少2000ms、30分钟/5000次、失败停轮。私有清单`/private/tmp/issue022-holder-source-20260913T142200Z/cases.json`，0600/父0700，SHA-256 `ac6b0ef0f25a122b505b145e9ea1c906bb6e0c4af6b0cbb55845c81de56a38f1`。复用第一轮完整隔离源码、已验证两包和同一wrapper；执行前检查其指纹/包与第一轮记录逐字相同，SOURCE无数据库。此时尚未执行，不填PASS。

| caseId后缀（前缀runId加短横线） | 精确params | 观察 |
| --- | --- | --- |
| `holder-600000-event` | `{"ts_code":"600000.SH","start_date":"20260828","end_date":"20260828"}` | ann_date闭区间/截止日同一行对照 |
| `holder-600000-lower-edge` | `{"ts_code":"600000.SH","start_date":"20260828","end_date":"20260831"}` | ann_date闭区间/截止日同一行对照 |
| `holder-600000-upper-edge` | `{"ts_code":"600000.SH","start_date":"20260801","end_date":"20260828"}` | ann_date闭区间/截止日同一行对照 |

### issue022-holder-source-20260913T142200Z 实际结果

- UTC `2026-09-13T14:22:12.366Z`～`2026-09-13T14:22:19.585Z`；exit 0、cleanup PASS；3case/3请求，状态{'PASS': 3}。原始安全对象与输入逐项相符，完整追加唯一索引，不改旧状态。
- 来源指纹 `490f99b2827718d63b0f38f397c69d60497edae243e59af6bbe378284e26dccc`，生产包 `38a99f9f6037313eb5ae1829fbd298da4d8cdc64120668c5c507a0e08afdda44`，验收包 `efe8ea0bebade6dc9c7093c61aacbb762dcfa1755f543625f39a3c53aebdf3a9`；manifest `386f46a99b6605e203129836d7a744b96b65304307f52991dd8bba6fd1870984`，examples `6d4c74a1a539b59ac20fb0cbd3ba1fba0954c40ef1209b652f7dcc2192ec932f`。wrapper输入/包/源码稳定与日志秘密扫描均按实际清理结果记录；SOURCE的TASK/SQL字段为null。

| caseId后缀 | 状态 | 行数 | 实际主轴日期 | 同一行计数 valid/unavailable/different/outside |
| --- | --- | ---: | --- | --- |
| `holder-600000-event` | PASS | 1 | 20260828 | 1/0/1/1 |
| `holder-600000-lower-edge` | PASS | 1 | 20260828 | 1/0/1/1 |
| `holder-600000-upper-edge` | PASS | 1 | 20260828 | 1/0/1/1 |

### ISSUE-022 来源结论与交付检查

- 两轮39case/39请求：35非空PASS、4空EVIDENCE_MISSING，两个exit0/cleanup PASS，privateLogSafe/inputUnchanged=true，第二轮源码与两包哈希与第一轮一致。
- 4项辅助日历沪深分别完整21/61自然日，开市集合逐项相同；离线按完整开市集合分周/月取最后日期，精确等于两股票周线0927/0930/1011与月线20180928/20181031。国庆周一和自然月末空对照具有判别性，不用本地工作日猜交易日。
- fina_indicator两股票整段5/4行、下端2/1行、上端1/1行；每项都有报告期在内/公告在外的同一行。holder两股票8项各1行，公告/截止日均有效、不同且截止日在外。new_share整段10行、下端1行、上端2行，全部日期有效不同，整段有2行上市日在外。副轴unavailable均0；反例由离线测试证明不会被计为不同/在外。
- 私有`/private/tmp/issue022-control/verify-handoff.mjs`已核对：完整索引、17轮609case581请求、两新run与私有原对象逐字相同、输入哈希、旧15轮570case及顶层输入不变、其他34接口不变、辅助日历仅追加引用、4 AVAILABLE保持；日期计数/周期/两股票与边界成立。
- 现有selectTaskCases接受35项新TASK候选并精确绑定到两轮SOURCE，4空对照拒绝。当前五接口SOURCE引用仅新轮（及原干净SINGLE TASK），旧run原样保留，避免同参数monthly绑定历史轮的歧义；没有改消费者。候选参数与来源/规则已[正式交付ISSUE-026](../issues/problems/ISSUE-026-range-task-final-acceptance.md#issue-022-已交付输入)，未提交TASK或连接数据库。
- 生产准入/版本未变；只有39次新增SOURCE，没有新的TASK/SQL或母issue关闭结果。最终回归、文档一致性与独立复审结论见下节。

### ISSUE-022 最终回归与文档复核

- 追加真实JSON后，旧独立预期仍要求五接口保留旧SOURCE失败缺口，Node78项1失败，exit1，日志`/private/tmp/issue022-control/node-final.log`；同步五接口实际引用/状态、辅助日历新增引用，并扩展五接口禁止仅凭SOURCE/SINGLE冒充AVAILABLE的拒绝检查。最终Node78/78、零失败/跳过，exit0，日志`node-final-green.log`。这只是主工作树验收预期同步，未改真实运行隔离源码或包。
- 相关Java173/173与完整隔离构建1072后端/包+468前端的实际通过见执行前身份；本次Probe及其测试与真实隔离源码逐字相同，无后续生产行为变化，不重复账户调用。
- 35项精确绑定/4空拒绝/所有历史对象保留检查exit0，日志`/private/tmp/issue022-control/handoff.log`；40行报告SOURCE/TASK/disposition与唯一JSON逐项相同，新增设计及交付的本地文件链接均存在，`git diff --check`通过。
- 独立最终复审发现当前缺口章节仍有五项旧描述，已按实际新SOURCE结论同步；旧历史章节和全部run未改写。该修正已交独立复核，最终结论见下节。

### ISSUE-022 最终验收

- 独立最终复审重新读取真实安全对象、逐项绑定、所有日期/边界和完整日历，检查旧15轮保留、实际隔离JAR哈希、Probe/测试逐字一致、trade_cal精确新增4引用及40项表。确认当前缺口五项旧文字已修复；Critical/Important/Minor均无剩余。
- 复审使用隔离Node v24.15.0重跑78/78、exit0，35精确绑定与4空拒绝无写入复查通过，diff检查通过。主验收Java173/173、隔离1072后端/包+468前端、Node78/78及日志位置见前文；没有重复真实账户请求。
- 对照issue三项关闭条件及专属设计：周期行情由真实完整日历最后开市日核对；报告期/公告日/统计截止日/申购日/上市日同一行对照成立；五接口两股票或原方式的有效整段/边界SOURCE及35项固定候选输入正式交ISSUE-026。精确状态为17轮/609case/581累计请求，新增39请求；35新非空PASS和4空对照均保留。
- 记录ISSUE-022 `IN_PROGRESS → COMPLETED`，同步问题文档、问题索引、专属设计、计划及followups看板。新文件加入Git，不提交、合并或发布；生产五接口仍NEEDS_VERIFICATION/v1，TASK/SQL由ISSUE-026完成，母issue/T13/T14状态保持，ISSUE-023未启动。

### ISSUE-023 启动、公开依据与取证范围

- 用户2026-09-13要求“完成issue23”；专属[设计](../task-designs/ISSUE-023-design.md)与[计划](../superpowers/plans/2026-09-13-issue-023.md)先于真实执行完成。启动基线17轮/609case/581累计请求，私有快照`/private/tmp/issue023-control/baseline.json`及工作树差异保留；其他36接口及旧run对象保持。
- 核对官网缓存161/162/175/111的正文及HTML哈希均与原验收引用相同；本轮为缓存复读，不伪称新的HTTP请求。安全核对文件`/private/tmp/issue023-control/official-cache-check.json`记录路径和摘要。四接口最大行数1000/6000/3000/1000，原合同与生产业务键保持。
- 官网161明确20181227的601318.SH同日五笔及多个买方；官网175给20190426的300115.SZ同日两笔；官网111给000014.SZ在20171216、20171221、20180106的公告与20171114业务开始日；官网162给300619.SZ的20181228公告、20181231报告期及20190122预计/实际日期。这些只用于预先选样，不代表新的账户返回。
- 官网175的begin_date/close_date和162的modify_date未在当前生产列内。仅测试RANGE附加选择后作同一行安全观察，严格校验扩展响应再将原列交生产策略；参数、原列和键不变，SOURCE无SQL。完整合同见专属设计及T13限定扩展。
- 核对旧唯一JSON发现：`issue018-t14-single-20260913T093339Z`的pledge_detail两项sourceRowCount分别为000001.SZ=0、600000.SH=2。当前文档“两股票各2行”是汇总错误，不是新API事实；旧run原样保留。本轮可用原SINGLE快照的合法公告日期安全集合登记后续边界，空样本仍为空。

### ISSUE-023 第一轮固定来源计划

- runId `issue023-source-20260913T144308Z`；固定14case/16次来源请求，其中disclosure_date的三自然日逐日调用，其他每case一次；无自动重试。30分钟/5000次总上限、至少2000ms间隔、35分钟外层超时、失败停轮。
- 私有清单`/private/tmp/issue023-source-20260913T144308Z/cases.json`，0600/父0700，SHA-256 `a9ff7fe4c1db264e528586e371436cba592c8a1e97a7c6dc1abbb90ddd79d66d`。所有caseId前缀为runId加短横线；精确参数及依据如下。此处仅计划，未执行、不填PASS；完整隔离构建、Node及源码/包身份仍须先通过并登记。
- 第一轮四官网代表样本只补特殊事件；两股票SINGLE快照用于固定后续质押日期，不能代替RANGE；两股票大宗交易采用已查明的公开日期。后续边界及披露/股东基准日期须另登记有依据的新轮。

| caseId后缀 | API / mode / dateAxis | 精确params | 事件依据与观察 |
| --- | --- | --- | --- |
| `block-official` | block_trade / RANGE / TRADE_DATE | `{"ts_code":"601318.SH","start_date":"20181226","end_date":"20181228"}` | 官网161：20181227同股同日五笔及不同买方 |
| `holder-official` | stk_holdertrade / RANGE / ANNOUNCEMENT_DATE | `{"ts_code":"300115.SZ","start_date":"20190425","end_date":"20190427"}` | 官网175：20190426两笔减持，比较begin_date/close_date |
| `pledge-official` | pledge_detail / RANGE / ANNOUNCEMENT_DATE | `{"ts_code":"000014.SZ","start_date":"20171216","end_date":"20180106"}` | 官网111：三个公告日期和20171114业务起始日 |
| `disclosure-official` | disclosure_date / RANGE / ANNOUNCEMENT_DATE | `{"ts_code":"300619.SZ","start_date":"20181227","end_date":"20181229"}` | 官网162：20181228公告及20181231报告期，逐三自然日 |
| `pledge-snapshot-000001` | pledge_detail / SINGLE / null | `{"ts_code":"000001.SZ"}` | 复查原SINGLE快照，仅取合法公告日期集合；不保证非空或全历史 |
| `pledge-snapshot-600000` | pledge_detail / SINGLE / null | `{"ts_code":"600000.SH"}` | 复查原SINGLE快照，仅取合法公告日期集合；不保证非空或全历史 |
| `block-000001-whole` | block_trade / RANGE / TRADE_DATE | `{"ts_code":"000001.SZ","start_date":"20230219","end_date":"20230221"}` | 新浪公开大宗交易表 https://vip.stock.finance.sina.com.cn/q/go.php/vInvestConsult/kind/dzjy/index.phtml?symbol=sz000001 明确事件日20230220 |
| `block-000001-event` | block_trade / RANGE / TRADE_DATE | `{"ts_code":"000001.SZ","start_date":"20230220","end_date":"20230220"}` | 新浪公开大宗交易表 https://vip.stock.finance.sina.com.cn/q/go.php/vInvestConsult/kind/dzjy/index.phtml?symbol=sz000001 明确事件日20230220 |
| `block-000001-lower-edge` | block_trade / RANGE / TRADE_DATE | `{"ts_code":"000001.SZ","start_date":"20230220","end_date":"20230221"}` | 新浪公开大宗交易表 https://vip.stock.finance.sina.com.cn/q/go.php/vInvestConsult/kind/dzjy/index.phtml?symbol=sz000001 明确事件日20230220 |
| `block-000001-upper-edge` | block_trade / RANGE / TRADE_DATE | `{"ts_code":"000001.SZ","start_date":"20230219","end_date":"20230220"}` | 新浪公开大宗交易表 https://vip.stock.finance.sina.com.cn/q/go.php/vInvestConsult/kind/dzjy/index.phtml?symbol=sz000001 明确事件日20230220 |
| `block-600000-whole` | block_trade / RANGE / TRADE_DATE | `{"ts_code":"600000.SH","start_date":"20220331","end_date":"20220402"}` | 新浪公开大宗交易表 https://vip.stock.finance.sina.com.cn/q/go.php/vInvestConsult/kind/dzjy/index.phtml?symbol=sh600000 明确事件日20220401 |
| `block-600000-event` | block_trade / RANGE / TRADE_DATE | `{"ts_code":"600000.SH","start_date":"20220401","end_date":"20220401"}` | 新浪公开大宗交易表 https://vip.stock.finance.sina.com.cn/q/go.php/vInvestConsult/kind/dzjy/index.phtml?symbol=sh600000 明确事件日20220401 |
| `block-600000-lower-edge` | block_trade / RANGE / TRADE_DATE | `{"ts_code":"600000.SH","start_date":"20220401","end_date":"20220402"}` | 新浪公开大宗交易表 https://vip.stock.finance.sina.com.cn/q/go.php/vInvestConsult/kind/dzjy/index.phtml?symbol=sh600000 明确事件日20220401 |
| `block-600000-upper-edge` | block_trade / RANGE / TRADE_DATE | `{"ts_code":"600000.SH","start_date":"20220331","end_date":"20220401"}` | 新浪公开大宗交易表 https://vip.stock.finance.sina.com.cn/q/go.php/vInvestConsult/kind/dzjy/index.phtml?symbol=sh600000 明确事件日20220401 |

### ISSUE-023 第二轮股东事件与披露修订固定计划

- runId `issue023-events-source-20260913T145005Z`；10case/96次来源请求（股东8次、披露27+61自然日），与第一轮独立的公开依据已在[补证第12节](ISSUE-018-T14-official-evidence.md#12-issue-023-稀疏事件公开依据2026-09-13)核对。本轮预登记先于执行，未填任何结果。
- 私有清单`/private/tmp/issue023-events-source-20260913T145005Z/cases.json`，0600/父0700，SHA-256 `7a4e17b5f48eb4838e9b975d7916a1ce3f827a24227721479c836ebb1cf2dacc`；复用第一轮受检隔离源码和两包，执行前核对身份与第一轮相同。至少2000ms、30分钟/5000次、35分钟外层期限，无自动重试，失败停轮。
- 两个披露窗口由已证实的报告期和预约改期事件固定。公告日期未知是明确调查问题；不会将首次预约/新预约/实际披露/EITIME直接当ann_date。仅基于本轮真实日期另登记边界与原键复查；窗口空则保留缺口，不能自动前移或后移搜索成功。

| caseId后缀（前缀runId加短横线） | API / dateAxis | 精确params | 依据与预期观察 |
| --- | --- | --- | --- |
| `holder-000001-whole` | stk_holdertrade / ANNOUNCEMENT_DATE | `{"ts_code":"000001.SZ","start_date":"20210906","end_date":"20210908"}` | 公开股东增减持表与公告索引/原文明确公告20210907；同一行比较业务起止日，不把业务日当公告日 |
| `holder-000001-event` | stk_holdertrade / ANNOUNCEMENT_DATE | `{"ts_code":"000001.SZ","start_date":"20210907","end_date":"20210907"}` | 公开股东增减持表与公告索引/原文明确公告20210907；同一行比较业务起止日，不把业务日当公告日 |
| `holder-000001-lower-edge` | stk_holdertrade / ANNOUNCEMENT_DATE | `{"ts_code":"000001.SZ","start_date":"20210907","end_date":"20210908"}` | 公开股东增减持表与公告索引/原文明确公告20210907；同一行比较业务起止日，不把业务日当公告日 |
| `holder-000001-upper-edge` | stk_holdertrade / ANNOUNCEMENT_DATE | `{"ts_code":"000001.SZ","start_date":"20210906","end_date":"20210907"}` | 公开股东增减持表与公告索引/原文明确公告20210907；同一行比较业务起止日，不把业务日当公告日 |
| `holder-600000-whole` | stk_holdertrade / ANNOUNCEMENT_DATE | `{"ts_code":"600000.SH","start_date":"20241219","end_date":"20241221"}` | 公开股东增减持表与公告索引/原文明确公告20241220；同一行比较业务起止日，不把业务日当公告日 |
| `holder-600000-event` | stk_holdertrade / ANNOUNCEMENT_DATE | `{"ts_code":"600000.SH","start_date":"20241220","end_date":"20241220"}` | 公开股东增减持表与公告索引/原文明确公告20241220；同一行比较业务起止日，不把业务日当公告日 |
| `holder-600000-lower-edge` | stk_holdertrade / ANNOUNCEMENT_DATE | `{"ts_code":"600000.SH","start_date":"20241220","end_date":"20241221"}` | 公开股东增减持表与公告索引/原文明确公告20241220；同一行比较业务起止日，不把业务日当公告日 |
| `holder-600000-upper-edge` | stk_holdertrade / ANNOUNCEMENT_DATE | `{"ts_code":"600000.SH","start_date":"20241219","end_date":"20241220"}` | 公开股东增减持表与公告索引/原文明确公告20241220；同一行比较业务起止日，不把业务日当公告日 |
| `disclosure-000001-revision-window` | disclosure_date / ANNOUNCEMENT_DATE | `{"ts_code":"000001.SZ","start_date":"20240930","end_date":"20241026"}` | 公开2024Q3报告期20240930，预约由20241026改20241019；未获确切改期公布日，固定27自然日调查最新ann_date |
| `disclosure-600000-revision-window` | disclosure_date / ANNOUNCEMENT_DATE | `{"ts_code":"600000.SH","start_date":"20240630","end_date":"20240829"}` | 公开2024H1报告期20240630，预约由20240829改20240820；未获确切改期公布日，固定61自然日调查最新ann_date |

### ISSUE-023 执行前构建与复审身份

- 主树保持原`feat/download-by-date-range`分支及既有成果；独立clone `/private/tmp/issue023-work-20260913T144803Z`完整带入Git管理的848个文件，逐文件内容相同，快照SHA-256 `15e6269b3e18b44e74dd2bdc8cb76eb92ab476363ea1429be2faa0f6ac1c5892`。普通命令白名单环境排除账户/DB。
- Probe RED 50项/10预期失败/0错误，GREEN50/50；四类回归184/184。首轮回归21个错误来自沙箱禁止WireMock本地端口，明确本地测试升级后184项全通过；未进行真实来源调用。日志`/private/tmp/issue023-control/probe-red-complete.log`、`probe-green.log`、`probe-regression-local-socket.log`。
- 隔离`mvn -o -f data-plane/pom.xml -Pacceptance verify` exit0/BUILD SUCCESS（`build.log`）；隔离Node证据测试78/78、exit0（`node-pre-source.log`）。独立Task1复审无Critical/Important/Minor发现，扩展校验/日期不可比/脱敏/摘要/失败及SINGLE范围均核对。Probe和测试与隔离文件逐字相同。
- 执行前源码指纹 `dccda646e1a6895d9c6ba0fed93a9470ba62e87dc6e992169fe9f79ee7e485c6`；实际生产JAR `fa821d4090d26149521ff5c8180e7771d238206d7ab4a152cdfc08b3b3c1bdec`；验收JAR `1d25af856b7a5198c33b7d57b6c28fe56a5ad9ecfb8c1743c084476ebdda4151`。manifest/examples沿原索引哈希，wrapper运行前后继续逐项核对。此记录为准备门禁，尚未产生SOURCE结果。

### issue023-source-20260913T144308Z 实际结果

- UTC `2026-09-13T14:51:13.011Z`～`2026-09-13T14:51:47.654Z`；exit 0、cleanup PASS；14case/16请求，状态{'PASS': 13, 'EVIDENCE_MISSING': 1}。安全原对象与预登记输入逐项相符，完整追加唯一索引，旧run未改。
- 来源指纹 `dccda646e1a6895d9c6ba0fed93a9470ba62e87dc6e992169fe9f79ee7e485c6`，生产包 `fa821d4090d26149521ff5c8180e7771d238206d7ab4a152cdfc08b3b3c1bdec`，验收包 `1d25af856b7a5198c33b7d57b6c28fe56a5ad9ecfb8c1743c084476ebdda4151`；manifest `386f46a99b6605e203129836d7a744b96b65304307f52991dd8bba6fd1870984`，examples `6d4c74a1a539b59ac20fb0cbd3ba1fba0954c40ef1209b652f7dcc2192ec932f`。wrapper清理检查结果见本轮私有source-run.json；SOURCE的TASK/SQL字段为null。

| caseId后缀 | 状态 | 行数 | 安全日期/计数/摘要观察 |
| --- | --- | ---: | --- |
| `block-official` | PASS | 14 | actualDates=20181226,20181227,20181228; uniqueDateCount=3; min=20181226; max=20181228; candidateRowLimit=1000; candidateLimitReached=false; blockTradeBusinessKeyValidRows=14; blockTradeBusinessKeyUnavailableRows=0; blockTradeDistinctBusinessKeyCount=14; blockTradeSameStockDateMultipleKeyGroupCount=3; blockTradeDistinctBuyerSellerPairCount=11 |
| `holder-official` | PASS | 2 | actualDates=20190426; uniqueDateCount=1; min=20190426; max=20190426; candidateRowLimit=3000; candidateLimitReached=false; holderBeginDateComparisonValidRows=2; holderBeginDateComparisonUnavailableRows=0; holderAnnDateDifferentFromBeginDateRows=2; holderAnnInRangeBeginOutsideRows=1; holderCloseDateComparisonValidRows=2; holderCloseDateComparisonUnavailableRows=0; holderAnnDateDifferentFromCloseDateRows=2; holderAnnInRangeCloseOutsideRows=1 |
| `pledge-official` | PASS | 8 | actualDates=20171216,20171221,20180106; uniqueDateCount=3; min=20171216; max=20180106; candidateRowLimit=1000; candidateLimitReached=false; pledgeStartDateComparisonValidRows=8; pledgeStartDateComparisonUnavailableRows=0; pledgeAnnDateDifferentFromStartDateRows=8; pledgeAnnInRangeStartOutsideRows=8; pledgeEndDateComparisonValidRows=8; pledgeEndDateComparisonUnavailableRows=0; pledgeAnnDateDifferentFromEndDateRows=8; pledgeAnnInRangeEndOutsideRows=8; pledgeReleaseDateComparisonValidRows=6; pledgeReleaseDateComparisonUnavailableRows=2; pledgeAnnDateDifferentFromReleaseDateRows=6; pledgeAnnInRangeReleaseOutsideRows=2 |
| `disclosure-official` | PASS | 1 | actualDates=20181228; uniqueDateCount=1; min=20181228; max=20181228; candidateRowLimit=6000; candidateLimitReached=false; disclosureReportDates=20181231; disclosureReportDateUnavailableRows=0; disclosureBusinessKeyValidRows=1; disclosureBusinessKeyUnavailableRows=0; disclosureDistinctBusinessKeyCount=1; disclosureBusinessKeyDigestSha256=19cf44509a111227f690e6bec7ec31763b983a9a1cbbcf524ca66425c754ab59; disclosureRecordDigestSha256=0ce310d09050f534a03fd502bf212aafa88366866fc99a1f8ac958df9b186839; disclosureSameKeyDifferentRecordCount=0; disclosureAnnEndDateComparisonValidRows=1; disclosureAnnEndDateComparisonUnavailableRows=0; disclosureAnnDateDifferentFromEndDateRows=1; disclosurePreActualDateComparisonValidRows=1; disclosurePreActualDateComparisonUnavailableRows=0; disclosurePreDateDifferentFromActualDateRows=0; disclosureModifyDatePresentRows=0; disclosureModifyDateParseableRows=0; disclosureModifyDateUnavailableRows=1; disclosureModifyDateMultipleDatesRows=0 |
| `pledge-snapshot-000001` | EVIDENCE_MISSING | 0 | actualDates=; uniqueDateCount=0; min=none; max=none; candidateRowLimit=1000; candidateLimitReached=false; pledgeAnnouncementDates= |
| `pledge-snapshot-600000` | PASS | 2 | actualDates=; uniqueDateCount=0; min=none; max=none; candidateRowLimit=1000; candidateLimitReached=false; pledgeAnnouncementDates=20140324 |
| `block-000001-whole` | PASS | 2 | actualDates=20230220; uniqueDateCount=1; min=20230220; max=20230220; candidateRowLimit=1000; candidateLimitReached=false; blockTradeBusinessKeyValidRows=2; blockTradeBusinessKeyUnavailableRows=0; blockTradeDistinctBusinessKeyCount=2; blockTradeSameStockDateMultipleKeyGroupCount=1; blockTradeDistinctBuyerSellerPairCount=1 |
| `block-000001-event` | PASS | 2 | actualDates=20230220; uniqueDateCount=1; min=20230220; max=20230220; candidateRowLimit=1000; candidateLimitReached=false; blockTradeBusinessKeyValidRows=2; blockTradeBusinessKeyUnavailableRows=0; blockTradeDistinctBusinessKeyCount=2; blockTradeSameStockDateMultipleKeyGroupCount=1; blockTradeDistinctBuyerSellerPairCount=1 |
| `block-000001-lower-edge` | PASS | 2 | actualDates=20230220; uniqueDateCount=1; min=20230220; max=20230220; candidateRowLimit=1000; candidateLimitReached=false; blockTradeBusinessKeyValidRows=2; blockTradeBusinessKeyUnavailableRows=0; blockTradeDistinctBusinessKeyCount=2; blockTradeSameStockDateMultipleKeyGroupCount=1; blockTradeDistinctBuyerSellerPairCount=1 |
| `block-000001-upper-edge` | PASS | 2 | actualDates=20230220; uniqueDateCount=1; min=20230220; max=20230220; candidateRowLimit=1000; candidateLimitReached=false; blockTradeBusinessKeyValidRows=2; blockTradeBusinessKeyUnavailableRows=0; blockTradeDistinctBusinessKeyCount=2; blockTradeSameStockDateMultipleKeyGroupCount=1; blockTradeDistinctBuyerSellerPairCount=1 |
| `block-600000-whole` | PASS | 2 | actualDates=20220401; uniqueDateCount=1; min=20220401; max=20220401; candidateRowLimit=1000; candidateLimitReached=false; blockTradeBusinessKeyValidRows=2; blockTradeBusinessKeyUnavailableRows=0; blockTradeDistinctBusinessKeyCount=2; blockTradeSameStockDateMultipleKeyGroupCount=1; blockTradeDistinctBuyerSellerPairCount=1 |
| `block-600000-event` | PASS | 2 | actualDates=20220401; uniqueDateCount=1; min=20220401; max=20220401; candidateRowLimit=1000; candidateLimitReached=false; blockTradeBusinessKeyValidRows=2; blockTradeBusinessKeyUnavailableRows=0; blockTradeDistinctBusinessKeyCount=2; blockTradeSameStockDateMultipleKeyGroupCount=1; blockTradeDistinctBuyerSellerPairCount=1 |
| `block-600000-lower-edge` | PASS | 2 | actualDates=20220401; uniqueDateCount=1; min=20220401; max=20220401; candidateRowLimit=1000; candidateLimitReached=false; blockTradeBusinessKeyValidRows=2; blockTradeBusinessKeyUnavailableRows=0; blockTradeDistinctBusinessKeyCount=2; blockTradeSameStockDateMultipleKeyGroupCount=1; blockTradeDistinctBuyerSellerPairCount=1 |
| `block-600000-upper-edge` | PASS | 2 | actualDates=20220401; uniqueDateCount=1; min=20220401; max=20220401; candidateRowLimit=1000; candidateLimitReached=false; blockTradeBusinessKeyValidRows=2; blockTradeBusinessKeyUnavailableRows=0; blockTradeDistinctBusinessKeyCount=2; blockTradeSameStockDateMultipleKeyGroupCount=1; blockTradeDistinctBuyerSellerPairCount=1 |

### ISSUE-023 首轮观察与待决样本

- 第一轮14case/16请求，13非空PASS、1个SINGLE空EVIDENCE_MISSING，exit0/cleanup PASS；与执行前源码/两包身份相同，日志秘密检查与输入稳定检查通过。完整隔离构建实际1083项后端/包测试及468项前端测试通过，未以SOURCE结果代替构建验证。
- 官网block_trade整段14行/14原业务键/3个同股同日多键组/11种买卖方组合；两基准股票各四种窗口均2行/2键，原始行保留。后续固定官网20181227单日区分同日买方，不能从整段组合数单独推定某一天的买方数量。
- 官网增减持两行起止日期均可比较，均与公告不同，各1行业务日落在公告窗口外。官网质押8行均有不同的开始/结束日且在公告窗外，release_date为6行可比较/2行不可比较；不可比较没有算作范围外。
- 官网披露记录1行：实际公告20181228、报告期20181231，预计与实际日期相同，modify_date为空。本样本证明公告轴与原报告期键，不能证明发生过修订。第二轮已有公开修订事件依据，单独调查基准股票。
- 质押SINGLE中000001.SZ再次0行，600000.SH两行安全公告集合仅20140324（第三方另列20021231不直接写成Tushare实际日期）。已向用户提交具体样本决定：是否保留000001.SZ空证据，使用已非空000014.SZ与600000.SH作质押非空整段/边界股票。决定尚未收到；补充官方样本取证已授权，但不先行将它写成基准样本要求已满足。

### 质押非空样本决定（2026-09-13）

用户明确“同意调整质押非空样本（推荐）”：使用第一轮真实非空的000014.SZ和600000.SH补齐质押整段/边界；000001.SZ空结果原样保留。专属设计与T13作限定修订，生产参数、原业务键、支持范围、完整性和历史承诺保持。该决定只是验收样本选择，不是新的来源或TASK/SQL通过事实。

### issue023-events-source-20260913T145005Z 实际结果

- UTC `2026-09-13T14:52:36.214Z`～`2026-09-13T14:55:53.665Z`；exit 0、cleanup PASS；10case/96请求，状态{'PASS': 10}。安全原对象与预登记输入逐项相符，完整追加唯一索引，旧run未改。
- 来源指纹 `dccda646e1a6895d9c6ba0fed93a9470ba62e87dc6e992169fe9f79ee7e485c6`，生产包 `fa821d4090d26149521ff5c8180e7771d238206d7ab4a152cdfc08b3b3c1bdec`，验收包 `1d25af856b7a5198c33b7d57b6c28fe56a5ad9ecfb8c1743c084476ebdda4151`；manifest `386f46a99b6605e203129836d7a744b96b65304307f52991dd8bba6fd1870984`，examples `6d4c74a1a539b59ac20fb0cbd3ba1fba0954c40ef1209b652f7dcc2192ec932f`。wrapper清理检查结果见本轮私有source-run.json；SOURCE的TASK/SQL字段为null。

| caseId后缀 | 状态 | 行数 | 安全日期/计数/摘要观察 |
| --- | --- | ---: | --- |
| `holder-000001-whole` | PASS | 6 | actualDates=20210907; uniqueDateCount=1; min=20210907; max=20210907; candidateRowLimit=3000; candidateLimitReached=false; holderBeginDateComparisonValidRows=6; holderBeginDateComparisonUnavailableRows=0; holderAnnDateDifferentFromBeginDateRows=6; holderAnnInRangeBeginOutsideRows=6; holderCloseDateComparisonValidRows=6; holderCloseDateComparisonUnavailableRows=0; holderAnnDateDifferentFromCloseDateRows=6; holderAnnInRangeCloseOutsideRows=0 |
| `holder-000001-event` | PASS | 6 | actualDates=20210907; uniqueDateCount=1; min=20210907; max=20210907; candidateRowLimit=3000; candidateLimitReached=false; holderBeginDateComparisonValidRows=6; holderBeginDateComparisonUnavailableRows=0; holderAnnDateDifferentFromBeginDateRows=6; holderAnnInRangeBeginOutsideRows=6; holderCloseDateComparisonValidRows=6; holderCloseDateComparisonUnavailableRows=0; holderAnnDateDifferentFromCloseDateRows=6; holderAnnInRangeCloseOutsideRows=6 |
| `holder-000001-lower-edge` | PASS | 6 | actualDates=20210907; uniqueDateCount=1; min=20210907; max=20210907; candidateRowLimit=3000; candidateLimitReached=false; holderBeginDateComparisonValidRows=6; holderBeginDateComparisonUnavailableRows=0; holderAnnDateDifferentFromBeginDateRows=6; holderAnnInRangeBeginOutsideRows=6; holderCloseDateComparisonValidRows=6; holderCloseDateComparisonUnavailableRows=0; holderAnnDateDifferentFromCloseDateRows=6; holderAnnInRangeCloseOutsideRows=6 |
| `holder-000001-upper-edge` | PASS | 6 | actualDates=20210907; uniqueDateCount=1; min=20210907; max=20210907; candidateRowLimit=3000; candidateLimitReached=false; holderBeginDateComparisonValidRows=6; holderBeginDateComparisonUnavailableRows=0; holderAnnDateDifferentFromBeginDateRows=6; holderAnnInRangeBeginOutsideRows=6; holderCloseDateComparisonValidRows=6; holderCloseDateComparisonUnavailableRows=0; holderAnnDateDifferentFromCloseDateRows=6; holderAnnInRangeCloseOutsideRows=0 |
| `holder-600000-whole` | PASS | 1 | actualDates=20241220; uniqueDateCount=1; min=20241220; max=20241220; candidateRowLimit=3000; candidateLimitReached=false; holderBeginDateComparisonValidRows=1; holderBeginDateComparisonUnavailableRows=0; holderAnnDateDifferentFromBeginDateRows=1; holderAnnInRangeBeginOutsideRows=0; holderCloseDateComparisonValidRows=1; holderCloseDateComparisonUnavailableRows=0; holderAnnDateDifferentFromCloseDateRows=1; holderAnnInRangeCloseOutsideRows=0 |
| `holder-600000-event` | PASS | 1 | actualDates=20241220; uniqueDateCount=1; min=20241220; max=20241220; candidateRowLimit=3000; candidateLimitReached=false; holderBeginDateComparisonValidRows=1; holderBeginDateComparisonUnavailableRows=0; holderAnnDateDifferentFromBeginDateRows=1; holderAnnInRangeBeginOutsideRows=1; holderCloseDateComparisonValidRows=1; holderCloseDateComparisonUnavailableRows=0; holderAnnDateDifferentFromCloseDateRows=1; holderAnnInRangeCloseOutsideRows=1 |
| `holder-600000-lower-edge` | PASS | 1 | actualDates=20241220; uniqueDateCount=1; min=20241220; max=20241220; candidateRowLimit=3000; candidateLimitReached=false; holderBeginDateComparisonValidRows=1; holderBeginDateComparisonUnavailableRows=0; holderAnnDateDifferentFromBeginDateRows=1; holderAnnInRangeBeginOutsideRows=1; holderCloseDateComparisonValidRows=1; holderCloseDateComparisonUnavailableRows=0; holderAnnDateDifferentFromCloseDateRows=1; holderAnnInRangeCloseOutsideRows=1 |
| `holder-600000-upper-edge` | PASS | 1 | actualDates=20241220; uniqueDateCount=1; min=20241220; max=20241220; candidateRowLimit=3000; candidateLimitReached=false; holderBeginDateComparisonValidRows=1; holderBeginDateComparisonUnavailableRows=0; holderAnnDateDifferentFromBeginDateRows=1; holderAnnInRangeBeginOutsideRows=0; holderCloseDateComparisonValidRows=1; holderCloseDateComparisonUnavailableRows=0; holderAnnDateDifferentFromCloseDateRows=1; holderAnnInRangeCloseOutsideRows=0 |
| `disclosure-000001-revision-window` | PASS | 1 | actualDates=20241009; uniqueDateCount=1; min=20241009; max=20241009; candidateRowLimit=6000; candidateLimitReached=false; disclosureReportDates=20240930; disclosureReportDateUnavailableRows=0; disclosureBusinessKeyValidRows=1; disclosureBusinessKeyUnavailableRows=0; disclosureDistinctBusinessKeyCount=1; disclosureBusinessKeyDigestSha256=cf8c67a97ba096b8b078deac2abaa5ecd65d988345ba09f0580c27cc8ff40db3; disclosureRecordDigestSha256=b887563e90304e2bc07e6dc62e915dd47c5e1d5db15f2bddec653ba2c71f3e21; disclosureSameKeyDifferentRecordCount=0; disclosureAnnEndDateComparisonValidRows=1; disclosureAnnEndDateComparisonUnavailableRows=0; disclosureAnnDateDifferentFromEndDateRows=1; disclosurePreActualDateComparisonValidRows=1; disclosurePreActualDateComparisonUnavailableRows=0; disclosurePreDateDifferentFromActualDateRows=0; disclosureModifyDatePresentRows=0; disclosureModifyDateParseableRows=0; disclosureModifyDateUnavailableRows=1; disclosureModifyDateMultipleDatesRows=0 |
| `disclosure-600000-revision-window` | PASS | 1 | actualDates=20240813; uniqueDateCount=1; min=20240813; max=20240813; candidateRowLimit=6000; candidateLimitReached=false; disclosureReportDates=20240630; disclosureReportDateUnavailableRows=0; disclosureBusinessKeyValidRows=1; disclosureBusinessKeyUnavailableRows=0; disclosureDistinctBusinessKeyCount=1; disclosureBusinessKeyDigestSha256=e8f39c1b1ee8031e51f95b14cc3bf22fca33dc99922e149bf01c7618f4e87f89; disclosureRecordDigestSha256=d28aebeb3b72839c705fdc4a628cb964f7fc6811e5b57008ac0946c520963bb3; disclosureSameKeyDifferentRecordCount=0; disclosureAnnEndDateComparisonValidRows=1; disclosureAnnEndDateComparisonUnavailableRows=0; disclosureAnnDateDifferentFromEndDateRows=1; disclosurePreActualDateComparisonValidRows=1; disclosurePreActualDateComparisonUnavailableRows=0; disclosurePreDateDifferentFromActualDateRows=0; disclosureModifyDatePresentRows=0; disclosureModifyDateParseableRows=0; disclosureModifyDateUnavailableRows=1; disclosureModifyDateMultipleDatesRows=0 |

### ISSUE-023 第三轮固定边界与同键复查

- 第二轮10case/96请求全部非空PASS，exit0/cleanup PASS；两基准股东各4窗口为6行/1行，实际披露公告分别20241009/20240813，报告期分别20240930/20240630。新增轮只消费这些明确日期与第一轮质押公告集合。
- 两披露记录的modify_date均为空（不可用=1，不计作修订记录可解析）。公开资料已明确首次预约→改期→实际日期；用这些公开日期加真实ann_date构造期望五列摘要，精确等于SOURCE的原五列摘要，且与“保留首次预约日”的假设摘要不同。因此当前记录对应公开修订后的预约；没有声称读取过上游旧版本或在两次请求间发生新修订。安全复查`/private/tmp/issue023-control/disclosure-revision-check.json`，第三轮将再读原键。
- runId `issue023-boundary-source-20260913T145716Z`；15case/19次请求，私有清单`/private/tmp/issue023-boundary-source-20260913T145716Z/cases.json`，0600/父0700，SHA-256 `08a062c965029489c037f32335163a20d70aeb4fd3c61f1fce28ab16c3cbe359`。所有重复复查事先独立caseId登记；同包同源码至少2000ms、30分钟/5000次、35分钟外层期限，失败停轮，无自动重试。此处仅计划，未填结果；执行前再核对第一轮身份。

| caseId后缀（前缀runId加短横线） | API / dateAxis | 精确params | 依据与观察 |
| --- | --- | --- | --- |
| `block-official-event` | block_trade / TRADE_DATE | `{"ts_code":"601318.SH","start_date":"20181227","end_date":"20181227"}` | 第一轮整段已含20181227且官网同日五笔；单独复查同日买卖方组合 |
| `pledge-000014-lower` | pledge_detail / ANNOUNCEMENT_DATE | `{"ts_code":"000014.SZ","start_date":"20171216","end_date":"20171216"}` | 第一轮pledge-official真实日期集合的lower端；用户已明确同意本股作非空代表 |
| `pledge-000014-upper` | pledge_detail / ANNOUNCEMENT_DATE | `{"ts_code":"000014.SZ","start_date":"20180106","end_date":"20180106"}` | 第一轮pledge-official真实日期集合的upper端；用户已明确同意本股作非空代表 |
| `pledge-600000-whole` | pledge_detail / ANNOUNCEMENT_DATE | `{"ts_code":"600000.SH","start_date":"20140323","end_date":"20140325"}` | 第一轮SINGLE两行真实安全公告日期集合仅20140324；与公开公告依据相符 |
| `pledge-600000-event` | pledge_detail / ANNOUNCEMENT_DATE | `{"ts_code":"600000.SH","start_date":"20140324","end_date":"20140324"}` | 第一轮SINGLE两行真实安全公告日期集合仅20140324；与公开公告依据相符 |
| `pledge-600000-lower-edge` | pledge_detail / ANNOUNCEMENT_DATE | `{"ts_code":"600000.SH","start_date":"20140324","end_date":"20140325"}` | 第一轮SINGLE两行真实安全公告日期集合仅20140324；与公开公告依据相符 |
| `pledge-600000-upper-edge` | pledge_detail / ANNOUNCEMENT_DATE | `{"ts_code":"600000.SH","start_date":"20140323","end_date":"20140324"}` | 第一轮SINGLE两行真实安全公告日期集合仅20140324；与公开公告依据相符 |
| `disclosure-000001-event` | disclosure_date / ANNOUNCEMENT_DATE | `{"ts_code":"000001.SZ","start_date":"20241009","end_date":"20241009"}` | 第二轮issue023-events-source-20260913T145005Z-disclosure-000001-revision-window实际最新公告日；复查原报告期键与修订后五列摘要，重复日期预先登记不是失败重试 |
| `disclosure-000001-lower-edge` | disclosure_date / ANNOUNCEMENT_DATE | `{"ts_code":"000001.SZ","start_date":"20241009","end_date":"20241010"}` | 第二轮issue023-events-source-20260913T145005Z-disclosure-000001-revision-window实际最新公告日；复查原报告期键与修订后五列摘要，重复日期预先登记不是失败重试 |
| `disclosure-000001-upper-edge` | disclosure_date / ANNOUNCEMENT_DATE | `{"ts_code":"000001.SZ","start_date":"20241008","end_date":"20241009"}` | 第二轮issue023-events-source-20260913T145005Z-disclosure-000001-revision-window实际最新公告日；复查原报告期键与修订后五列摘要，重复日期预先登记不是失败重试 |
| `disclosure-000001-same-key-recheck` | disclosure_date / ANNOUNCEMENT_DATE | `{"ts_code":"000001.SZ","start_date":"20241009","end_date":"20241009"}` | 第二轮issue023-events-source-20260913T145005Z-disclosure-000001-revision-window实际最新公告日；复查原报告期键与修订后五列摘要，重复日期预先登记不是失败重试 |
| `disclosure-600000-event` | disclosure_date / ANNOUNCEMENT_DATE | `{"ts_code":"600000.SH","start_date":"20240813","end_date":"20240813"}` | 第二轮issue023-events-source-20260913T145005Z-disclosure-600000-revision-window实际最新公告日；复查原报告期键与修订后五列摘要，重复日期预先登记不是失败重试 |
| `disclosure-600000-lower-edge` | disclosure_date / ANNOUNCEMENT_DATE | `{"ts_code":"600000.SH","start_date":"20240813","end_date":"20240814"}` | 第二轮issue023-events-source-20260913T145005Z-disclosure-600000-revision-window实际最新公告日；复查原报告期键与修订后五列摘要，重复日期预先登记不是失败重试 |
| `disclosure-600000-upper-edge` | disclosure_date / ANNOUNCEMENT_DATE | `{"ts_code":"600000.SH","start_date":"20240812","end_date":"20240813"}` | 第二轮issue023-events-source-20260913T145005Z-disclosure-600000-revision-window实际最新公告日；复查原报告期键与修订后五列摘要，重复日期预先登记不是失败重试 |
| `disclosure-600000-same-key-recheck` | disclosure_date / ANNOUNCEMENT_DATE | `{"ts_code":"600000.SH","start_date":"20240813","end_date":"20240813"}` | 第二轮issue023-events-source-20260913T145005Z-disclosure-600000-revision-window实际最新公告日；复查原报告期键与修订后五列摘要，重复日期预先登记不是失败重试 |

### issue023-boundary-source-20260913T145716Z 实际结果

- UTC `2026-09-13T14:57:48.240Z`～`2026-09-13T14:58:28.457Z`；exit 0、cleanup PASS；15case/19请求，状态{'PASS': 15}。安全原对象与预登记输入逐项相符，完整追加唯一索引，旧run未改。
- 来源指纹 `dccda646e1a6895d9c6ba0fed93a9470ba62e87dc6e992169fe9f79ee7e485c6`，生产包 `fa821d4090d26149521ff5c8180e7771d238206d7ab4a152cdfc08b3b3c1bdec`，验收包 `1d25af856b7a5198c33b7d57b6c28fe56a5ad9ecfb8c1743c084476ebdda4151`；manifest `386f46a99b6605e203129836d7a744b96b65304307f52991dd8bba6fd1870984`，examples `6d4c74a1a539b59ac20fb0cbd3ba1fba0954c40ef1209b652f7dcc2192ec932f`。wrapper清理检查结果见本轮私有source-run.json；SOURCE的TASK/SQL字段为null。

| caseId后缀 | 状态 | 行数 | 安全日期/计数/摘要观察 |
| --- | --- | ---: | --- |
| `block-official-event` | PASS | 5 | actualDates=20181227; uniqueDateCount=1; min=20181227; max=20181227; candidateRowLimit=1000; candidateLimitReached=false; blockTradeBusinessKeyValidRows=5; blockTradeBusinessKeyUnavailableRows=0; blockTradeDistinctBusinessKeyCount=5; blockTradeSameStockDateMultipleKeyGroupCount=1; blockTradeDistinctBuyerSellerPairCount=5 |
| `pledge-000014-lower` | PASS | 2 | actualDates=20171216; uniqueDateCount=1; min=20171216; max=20171216; candidateRowLimit=1000; candidateLimitReached=false; pledgeStartDateComparisonValidRows=2; pledgeStartDateComparisonUnavailableRows=0; pledgeAnnDateDifferentFromStartDateRows=2; pledgeAnnInRangeStartOutsideRows=2; pledgeEndDateComparisonValidRows=2; pledgeEndDateComparisonUnavailableRows=0; pledgeAnnDateDifferentFromEndDateRows=2; pledgeAnnInRangeEndOutsideRows=2; pledgeReleaseDateComparisonValidRows=2; pledgeReleaseDateComparisonUnavailableRows=0; pledgeAnnDateDifferentFromReleaseDateRows=2; pledgeAnnInRangeReleaseOutsideRows=2 |
| `pledge-000014-upper` | PASS | 4 | actualDates=20180106; uniqueDateCount=1; min=20180106; max=20180106; candidateRowLimit=1000; candidateLimitReached=false; pledgeStartDateComparisonValidRows=4; pledgeStartDateComparisonUnavailableRows=0; pledgeAnnDateDifferentFromStartDateRows=4; pledgeAnnInRangeStartOutsideRows=4; pledgeEndDateComparisonValidRows=4; pledgeEndDateComparisonUnavailableRows=0; pledgeAnnDateDifferentFromEndDateRows=4; pledgeAnnInRangeEndOutsideRows=4; pledgeReleaseDateComparisonValidRows=2; pledgeReleaseDateComparisonUnavailableRows=2; pledgeAnnDateDifferentFromReleaseDateRows=2; pledgeAnnInRangeReleaseOutsideRows=2 |
| `pledge-600000-whole` | PASS | 2 | actualDates=20140324; uniqueDateCount=1; min=20140324; max=20140324; candidateRowLimit=1000; candidateLimitReached=false; pledgeStartDateComparisonValidRows=2; pledgeStartDateComparisonUnavailableRows=0; pledgeAnnDateDifferentFromStartDateRows=2; pledgeAnnInRangeStartOutsideRows=2; pledgeEndDateComparisonValidRows=2; pledgeEndDateComparisonUnavailableRows=0; pledgeAnnDateDifferentFromEndDateRows=2; pledgeAnnInRangeEndOutsideRows=2; pledgeReleaseDateComparisonValidRows=2; pledgeReleaseDateComparisonUnavailableRows=0; pledgeAnnDateDifferentFromReleaseDateRows=0; pledgeAnnInRangeReleaseOutsideRows=0 |
| `pledge-600000-event` | PASS | 2 | actualDates=20140324; uniqueDateCount=1; min=20140324; max=20140324; candidateRowLimit=1000; candidateLimitReached=false; pledgeStartDateComparisonValidRows=2; pledgeStartDateComparisonUnavailableRows=0; pledgeAnnDateDifferentFromStartDateRows=2; pledgeAnnInRangeStartOutsideRows=2; pledgeEndDateComparisonValidRows=2; pledgeEndDateComparisonUnavailableRows=0; pledgeAnnDateDifferentFromEndDateRows=2; pledgeAnnInRangeEndOutsideRows=2; pledgeReleaseDateComparisonValidRows=2; pledgeReleaseDateComparisonUnavailableRows=0; pledgeAnnDateDifferentFromReleaseDateRows=0; pledgeAnnInRangeReleaseOutsideRows=0 |
| `pledge-600000-lower-edge` | PASS | 2 | actualDates=20140324; uniqueDateCount=1; min=20140324; max=20140324; candidateRowLimit=1000; candidateLimitReached=false; pledgeStartDateComparisonValidRows=2; pledgeStartDateComparisonUnavailableRows=0; pledgeAnnDateDifferentFromStartDateRows=2; pledgeAnnInRangeStartOutsideRows=2; pledgeEndDateComparisonValidRows=2; pledgeEndDateComparisonUnavailableRows=0; pledgeAnnDateDifferentFromEndDateRows=2; pledgeAnnInRangeEndOutsideRows=2; pledgeReleaseDateComparisonValidRows=2; pledgeReleaseDateComparisonUnavailableRows=0; pledgeAnnDateDifferentFromReleaseDateRows=0; pledgeAnnInRangeReleaseOutsideRows=0 |
| `pledge-600000-upper-edge` | PASS | 2 | actualDates=20140324; uniqueDateCount=1; min=20140324; max=20140324; candidateRowLimit=1000; candidateLimitReached=false; pledgeStartDateComparisonValidRows=2; pledgeStartDateComparisonUnavailableRows=0; pledgeAnnDateDifferentFromStartDateRows=2; pledgeAnnInRangeStartOutsideRows=2; pledgeEndDateComparisonValidRows=2; pledgeEndDateComparisonUnavailableRows=0; pledgeAnnDateDifferentFromEndDateRows=2; pledgeAnnInRangeEndOutsideRows=2; pledgeReleaseDateComparisonValidRows=2; pledgeReleaseDateComparisonUnavailableRows=0; pledgeAnnDateDifferentFromReleaseDateRows=0; pledgeAnnInRangeReleaseOutsideRows=0 |
| `disclosure-000001-event` | PASS | 1 | actualDates=20241009; uniqueDateCount=1; min=20241009; max=20241009; candidateRowLimit=6000; candidateLimitReached=false; disclosureReportDates=20240930; disclosureReportDateUnavailableRows=0; disclosureBusinessKeyValidRows=1; disclosureBusinessKeyUnavailableRows=0; disclosureDistinctBusinessKeyCount=1; disclosureBusinessKeyDigestSha256=cf8c67a97ba096b8b078deac2abaa5ecd65d988345ba09f0580c27cc8ff40db3; disclosureRecordDigestSha256=b887563e90304e2bc07e6dc62e915dd47c5e1d5db15f2bddec653ba2c71f3e21; disclosureSameKeyDifferentRecordCount=0; disclosureAnnEndDateComparisonValidRows=1; disclosureAnnEndDateComparisonUnavailableRows=0; disclosureAnnDateDifferentFromEndDateRows=1; disclosurePreActualDateComparisonValidRows=1; disclosurePreActualDateComparisonUnavailableRows=0; disclosurePreDateDifferentFromActualDateRows=0; disclosureModifyDatePresentRows=0; disclosureModifyDateParseableRows=0; disclosureModifyDateUnavailableRows=1; disclosureModifyDateMultipleDatesRows=0 |
| `disclosure-000001-lower-edge` | PASS | 1 | actualDates=20241009; uniqueDateCount=1; min=20241009; max=20241009; candidateRowLimit=6000; candidateLimitReached=false; disclosureReportDates=20240930; disclosureReportDateUnavailableRows=0; disclosureBusinessKeyValidRows=1; disclosureBusinessKeyUnavailableRows=0; disclosureDistinctBusinessKeyCount=1; disclosureBusinessKeyDigestSha256=cf8c67a97ba096b8b078deac2abaa5ecd65d988345ba09f0580c27cc8ff40db3; disclosureRecordDigestSha256=b887563e90304e2bc07e6dc62e915dd47c5e1d5db15f2bddec653ba2c71f3e21; disclosureSameKeyDifferentRecordCount=0; disclosureAnnEndDateComparisonValidRows=1; disclosureAnnEndDateComparisonUnavailableRows=0; disclosureAnnDateDifferentFromEndDateRows=1; disclosurePreActualDateComparisonValidRows=1; disclosurePreActualDateComparisonUnavailableRows=0; disclosurePreDateDifferentFromActualDateRows=0; disclosureModifyDatePresentRows=0; disclosureModifyDateParseableRows=0; disclosureModifyDateUnavailableRows=1; disclosureModifyDateMultipleDatesRows=0 |
| `disclosure-000001-upper-edge` | PASS | 1 | actualDates=20241009; uniqueDateCount=1; min=20241009; max=20241009; candidateRowLimit=6000; candidateLimitReached=false; disclosureReportDates=20240930; disclosureReportDateUnavailableRows=0; disclosureBusinessKeyValidRows=1; disclosureBusinessKeyUnavailableRows=0; disclosureDistinctBusinessKeyCount=1; disclosureBusinessKeyDigestSha256=cf8c67a97ba096b8b078deac2abaa5ecd65d988345ba09f0580c27cc8ff40db3; disclosureRecordDigestSha256=b887563e90304e2bc07e6dc62e915dd47c5e1d5db15f2bddec653ba2c71f3e21; disclosureSameKeyDifferentRecordCount=0; disclosureAnnEndDateComparisonValidRows=1; disclosureAnnEndDateComparisonUnavailableRows=0; disclosureAnnDateDifferentFromEndDateRows=1; disclosurePreActualDateComparisonValidRows=1; disclosurePreActualDateComparisonUnavailableRows=0; disclosurePreDateDifferentFromActualDateRows=0; disclosureModifyDatePresentRows=0; disclosureModifyDateParseableRows=0; disclosureModifyDateUnavailableRows=1; disclosureModifyDateMultipleDatesRows=0 |
| `disclosure-000001-same-key-recheck` | PASS | 1 | actualDates=20241009; uniqueDateCount=1; min=20241009; max=20241009; candidateRowLimit=6000; candidateLimitReached=false; disclosureReportDates=20240930; disclosureReportDateUnavailableRows=0; disclosureBusinessKeyValidRows=1; disclosureBusinessKeyUnavailableRows=0; disclosureDistinctBusinessKeyCount=1; disclosureBusinessKeyDigestSha256=cf8c67a97ba096b8b078deac2abaa5ecd65d988345ba09f0580c27cc8ff40db3; disclosureRecordDigestSha256=b887563e90304e2bc07e6dc62e915dd47c5e1d5db15f2bddec653ba2c71f3e21; disclosureSameKeyDifferentRecordCount=0; disclosureAnnEndDateComparisonValidRows=1; disclosureAnnEndDateComparisonUnavailableRows=0; disclosureAnnDateDifferentFromEndDateRows=1; disclosurePreActualDateComparisonValidRows=1; disclosurePreActualDateComparisonUnavailableRows=0; disclosurePreDateDifferentFromActualDateRows=0; disclosureModifyDatePresentRows=0; disclosureModifyDateParseableRows=0; disclosureModifyDateUnavailableRows=1; disclosureModifyDateMultipleDatesRows=0 |
| `disclosure-600000-event` | PASS | 1 | actualDates=20240813; uniqueDateCount=1; min=20240813; max=20240813; candidateRowLimit=6000; candidateLimitReached=false; disclosureReportDates=20240630; disclosureReportDateUnavailableRows=0; disclosureBusinessKeyValidRows=1; disclosureBusinessKeyUnavailableRows=0; disclosureDistinctBusinessKeyCount=1; disclosureBusinessKeyDigestSha256=e8f39c1b1ee8031e51f95b14cc3bf22fca33dc99922e149bf01c7618f4e87f89; disclosureRecordDigestSha256=d28aebeb3b72839c705fdc4a628cb964f7fc6811e5b57008ac0946c520963bb3; disclosureSameKeyDifferentRecordCount=0; disclosureAnnEndDateComparisonValidRows=1; disclosureAnnEndDateComparisonUnavailableRows=0; disclosureAnnDateDifferentFromEndDateRows=1; disclosurePreActualDateComparisonValidRows=1; disclosurePreActualDateComparisonUnavailableRows=0; disclosurePreDateDifferentFromActualDateRows=0; disclosureModifyDatePresentRows=0; disclosureModifyDateParseableRows=0; disclosureModifyDateUnavailableRows=1; disclosureModifyDateMultipleDatesRows=0 |
| `disclosure-600000-lower-edge` | PASS | 1 | actualDates=20240813; uniqueDateCount=1; min=20240813; max=20240813; candidateRowLimit=6000; candidateLimitReached=false; disclosureReportDates=20240630; disclosureReportDateUnavailableRows=0; disclosureBusinessKeyValidRows=1; disclosureBusinessKeyUnavailableRows=0; disclosureDistinctBusinessKeyCount=1; disclosureBusinessKeyDigestSha256=e8f39c1b1ee8031e51f95b14cc3bf22fca33dc99922e149bf01c7618f4e87f89; disclosureRecordDigestSha256=d28aebeb3b72839c705fdc4a628cb964f7fc6811e5b57008ac0946c520963bb3; disclosureSameKeyDifferentRecordCount=0; disclosureAnnEndDateComparisonValidRows=1; disclosureAnnEndDateComparisonUnavailableRows=0; disclosureAnnDateDifferentFromEndDateRows=1; disclosurePreActualDateComparisonValidRows=1; disclosurePreActualDateComparisonUnavailableRows=0; disclosurePreDateDifferentFromActualDateRows=0; disclosureModifyDatePresentRows=0; disclosureModifyDateParseableRows=0; disclosureModifyDateUnavailableRows=1; disclosureModifyDateMultipleDatesRows=0 |
| `disclosure-600000-upper-edge` | PASS | 1 | actualDates=20240813; uniqueDateCount=1; min=20240813; max=20240813; candidateRowLimit=6000; candidateLimitReached=false; disclosureReportDates=20240630; disclosureReportDateUnavailableRows=0; disclosureBusinessKeyValidRows=1; disclosureBusinessKeyUnavailableRows=0; disclosureDistinctBusinessKeyCount=1; disclosureBusinessKeyDigestSha256=e8f39c1b1ee8031e51f95b14cc3bf22fca33dc99922e149bf01c7618f4e87f89; disclosureRecordDigestSha256=d28aebeb3b72839c705fdc4a628cb964f7fc6811e5b57008ac0946c520963bb3; disclosureSameKeyDifferentRecordCount=0; disclosureAnnEndDateComparisonValidRows=1; disclosureAnnEndDateComparisonUnavailableRows=0; disclosureAnnDateDifferentFromEndDateRows=1; disclosurePreActualDateComparisonValidRows=1; disclosurePreActualDateComparisonUnavailableRows=0; disclosurePreDateDifferentFromActualDateRows=0; disclosureModifyDatePresentRows=0; disclosureModifyDateParseableRows=0; disclosureModifyDateUnavailableRows=1; disclosureModifyDateMultipleDatesRows=0 |
| `disclosure-600000-same-key-recheck` | PASS | 1 | actualDates=20240813; uniqueDateCount=1; min=20240813; max=20240813; candidateRowLimit=6000; candidateLimitReached=false; disclosureReportDates=20240630; disclosureReportDateUnavailableRows=0; disclosureBusinessKeyValidRows=1; disclosureBusinessKeyUnavailableRows=0; disclosureDistinctBusinessKeyCount=1; disclosureBusinessKeyDigestSha256=e8f39c1b1ee8031e51f95b14cc3bf22fca33dc99922e149bf01c7618f4e87f89; disclosureRecordDigestSha256=d28aebeb3b72839c705fdc4a628cb964f7fc6811e5b57008ac0946c520963bb3; disclosureSameKeyDifferentRecordCount=0; disclosureAnnEndDateComparisonValidRows=1; disclosureAnnEndDateComparisonUnavailableRows=0; disclosureAnnDateDifferentFromEndDateRows=1; disclosurePreActualDateComparisonValidRows=1; disclosurePreActualDateComparisonUnavailableRows=0; disclosurePreDateDifferentFromActualDateRows=0; disclosureModifyDatePresentRows=0; disclosureModifyDateParseableRows=0; disclosureModifyDateUnavailableRows=1; disclosureModifyDateMultipleDatesRows=0 |

### ISSUE-023 来源与交付验收

- 三轮实际14/10/15case、16/96/19请求，共39case/131请求；38非空PASS、1个000001.SZ质押SINGLE空EVIDENCE_MISSING，三个exit0/cleanup PASS。三轮源码及两JAR哈希完全相同，与执行前身份匹配；私有输入/安全原对象逐项核对，旧17轮609case及其他36接口保持。唯一JSON现20轮648case712累计请求，生产仍4 AVAILABLE。
- `verify-events.mjs`核对固定整段/边界、完整自然日叶子、原始行数、同股同日原键与同日买卖方、公告/业务日整数分母，以及公开改期后的五列摘要和重复原键，exit0。官方block单日5行/5键/5买卖方，基准两股各2行/2键；holder两股单日6/1行的begin/close均在公告窗外。质押按用户决定两股票非空边界成立，不可比解押日期不当作范围外。
- 披露两股5种窗口/复查各1行、同原报告期键及相同记录摘要；modify_date全部为空，没有本轮跨时点变化。000001.SZ期望修订后摘要`b887563e90304e2bc07e6dc62e915dd47c5e1d5db15f2bddec653ba2c71f3e21`，600000.SH为`d28aebeb3b72839c705fdc4a628cb964f7fc6811e5b57008ac0946c520963bb3`，均由公开修订后的预约/实际日期和实际ann_date独立构造，与SOURCE精确相同；“保留首次预约日”的假设摘要不同，该假设不是上游旧版观察。
- 现有`selectTaskCases`接受35项唯一参数候选，sourceBindings精确绑定三轮来源；原10项空/失败轮次RANGE候选以及空SINGLE当RANGE均拒绝，未改消费者。[ISSUE-026交付](../issues/problems/ISSUE-026-range-task-final-acceptance.md#issue-023-已交付输入)包含35项可恢复精确params/dateAxis/来源身份/规则及TASK观察。两次相同参数披露复查原case保留，仅不重复计唯一候选。
- 当前四接口引用为三轮实际SOURCE及原清洁SINGLE TASK；质押聚合EVIDENCE_MISSING来自保留的空SINGLE，其余三接口SOURCE PASS。pledge的decisionRef指用户样本决定，其完整性ROW_LIMIT仍1000；其他规则、v1和NEEDS_VERIFICATION保持，没有TASK/SQL或母issue关闭事实。
- Node同步前78项1失败，准确原因是旧测试还要求四接口保留旧SOURCE_RUN_FAILED当前缺口（`node-current-red.log`）；同步四接口真实引用、计数、质押决定/空样本和禁止仅凭SOURCE/SINGLE开放的检查后78/78、exit0（`node-final.log`）。只有主树证据测试更新，真实隔离源码和两包保持。
- Probe相关184/184、完整隔离1083后端/包+468前端、Node78/78通过。已完成Task1独立复审；最终完整证据/交付/文档复审另记，不先写完成。私有核对脚本与日志均在`/private/tmp/issue023-control/`，没有新增真实TASK、数据库操作或生产准入变化。

### ISSUE-023 最终验收

- 独立最终复审无Critical/Important/Minor发现，确认可以关闭本issue。复审重新执行`verify-events.mjs`、`verify-handoff.mjs`、`verify-identity.py`及隔离Node24当前证据测试78/78；另直接核对三轮source-evidence.cases与source-run/索引相同，输入SHA及0600/0700权限、披露全部自然日叶子、20轮648case712请求、40行报告和35项交付表精确恢复、15份公开缓存哈希与原始预约变更字段。
- 复审确认184相关回归、完整隔离1083后端/包及468前端的日志计数；当前data-plane/scripts/docs-contracts与固定clone逐文件保持，Probe及测试一致，两新文档已Git纳管。204本地链接/锚点、`git diff --check`通过。主树离线验收日志见`/private/tmp/issue023-control/final-proof.log`、`node-final.log`；未重复真实SOURCE或TASK/SQL。
- 三项关闭条件成立：每轮事先有公开事件或此前实际日期并固定参数；多笔/买卖方、公开修订后披露及原键复查、同一行业务日期有实际证据；四接口非空整段和边界齐备，质押按明确决定用000014.SZ/600000.SH，原000001.SZ空结果不提升，35项候选正式交付ISSUE-026。modify_date为空及没有本轮跨时点变化明确保留，实际入库更新由ISSUE-026验证。
- 据此记录ISSUE-023 `IN_PROGRESS → COMPLETED`，同步issue、索引、设计、计划及followups看板。源数据新增39case/131请求、38非空/1空；旧17轮609case与其他36接口保持，三轮源码及两包不变。新增文件Git纳管，不提交或推送；四接口仍NEEDS_VERIFICATION/v1，母issue/T13/T14未完成，ISSUE-024未启动。

## ISSUE-024 历史来源验证

- 用户2026-09-13要求“完成issue24”，专属[设计](../task-designs/ISSUE-024-design.md)及[计划](../superpowers/plans/2026-09-13-issue-024.md)已完成。基线20轮/648case/712请求，私有原索引`/private/tmp/issue024-control/baseline.json`。仅三接口RANGE测试投影增量，原业务键/生产准入保留。

### ISSUE-024 第一轮固定来源计划

- runId `issue024-source-20260913T153146Z`；23case/23次预期请求；至少2000ms间隔、30分钟/5000次预算、35分钟外层超时、失败停轮、无自动重试。
- 私有清单`/private/tmp/issue024-source-20260913T153146Z/cases.json`，0600/父0700，SHA-256 `4b866a0a6548ed3cbda1125a15c2038d82500eecfa074809a60249b994fa4e07`。此时仅登记，未执行；隔离构建、Node与源码/两包身份须先通过。历史窗不是API合同，空结果保留不改通过。

| caseId后缀 | API / dateAxis | 精确params | 依据与预期 |
| --- | --- | --- | --- |
| `len-whole` | slb_len / TRADE_DATE | `{"start_date":"20240601","end_date":"20240620"}` | 官网331整段示例13行、实际首尾0603/0620；核对原trade_date+ob键及无期限融资汇总基数 |
| `len-lower` | slb_len / TRADE_DATE | `{"start_date":"20240603","end_date":"20240603"}` | 官网331整段示例13行、实际首尾0603/0620；核对原trade_date+ob键及无期限融资汇总基数 |
| `len-upper` | slb_len / TRADE_DATE | `{"start_date":"20240620","end_date":"20240620"}` | 官网331整段示例13行、实际首尾0603/0620；核对原trade_date+ob键及无期限融资汇总基数 |
| `slb_sec-000001-whole` | slb_sec / TRADE_DATE | `{"ts_code":"000001.SZ","start_date":"20240601","end_date":"20240620"}` | 官网332/333明确000001.SZ在20240620，固定同月整段；600000.SH为原合同第二股票调查，不预设非空 |
| `slb_sec-000001-official-day` | slb_sec / TRADE_DATE | `{"ts_code":"000001.SZ","start_date":"20240620","end_date":"20240620"}` | 官网332/333明确000001.SZ在20240620，固定同月整段；600000.SH为原合同第二股票调查，不预设非空 |
| `slb_sec-600000-whole` | slb_sec / TRADE_DATE | `{"ts_code":"600000.SH","start_date":"20240601","end_date":"20240620"}` | 官网332/333明确000001.SZ在20240620，固定同月整段；600000.SH为原合同第二股票调查，不预设非空 |
| `slb_sec-600000-official-day` | slb_sec / TRADE_DATE | `{"ts_code":"600000.SH","start_date":"20240620","end_date":"20240620"}` | 官网332/333明确000001.SZ在20240620，固定同月整段；600000.SH为原合同第二股票调查，不预设非空 |
| `slb_sec_detail-000001-whole` | slb_sec_detail / TRADE_DATE | `{"ts_code":"000001.SZ","start_date":"20240601","end_date":"20240620"}` | 官网332/333明确000001.SZ在20240620，固定同月整段；600000.SH为原合同第二股票调查，不预设非空 |
| `slb_sec_detail-000001-official-day` | slb_sec_detail / TRADE_DATE | `{"ts_code":"000001.SZ","start_date":"20240620","end_date":"20240620"}` | 官网332/333明确000001.SZ在20240620，固定同月整段；600000.SH为原合同第二股票调查，不预设非空 |
| `slb_sec_detail-600000-whole` | slb_sec_detail / TRADE_DATE | `{"ts_code":"600000.SH","start_date":"20240601","end_date":"20240620"}` | 官网332/333明确000001.SZ在20240620，固定同月整段；600000.SH为原合同第二股票调查，不预设非空 |
| `slb_sec_detail-600000-official-day` | slb_sec_detail / TRADE_DATE | `{"ts_code":"600000.SH","start_date":"20240620","end_date":"20240620"}` | 官网332/333明确000001.SZ在20240620，固定同月整段；600000.SH为原合同第二股票调查，不预设非空 |
| `detail-000001-long` | slb_sec_detail / TRADE_DATE | `{"ts_code":"000001.SZ","start_date":"20230101","end_date":"20240620"}` | 官网333可循环获取所有历史且展示20240620；固定从2023首日起较长窗调查期限/费率，起日是调查输入，不声称为API历史起点 |
| `detail-600000-long` | slb_sec_detail / TRADE_DATE | `{"ts_code":"600000.SH","start_date":"20230101","end_date":"20240620"}` | 官网333可循环获取所有历史且展示20240620；固定从2023首日起较长窗调查期限/费率，起日是调查输入，不声称为API历史起点 |
| `slb_len-all-suspension` | slb_len / TRADE_DATE | `{"start_date":"20240701","end_date":"20240711"}` | 证监会20240711暂停转融券新业务/20240930存量最迟了结；固定周边窗对照，通知不直接适用于融资或宣告API停用/最后返回日期 |
| `slb_len-all-settlement` | slb_len / TRADE_DATE | `{"start_date":"20240930","end_date":"20241001"}` | 证监会20240711暂停转融券新业务/20240930存量最迟了结；固定周边窗对照，通知不直接适用于融资或宣告API停用/最后返回日期 |
| `slb_sec-000001-suspension` | slb_sec / TRADE_DATE | `{"ts_code":"000001.SZ","start_date":"20240701","end_date":"20240711"}` | 证监会20240711暂停转融券新业务/20240930存量最迟了结；固定周边窗对照，通知不直接适用于融资或宣告API停用/最后返回日期 |
| `slb_sec-000001-settlement` | slb_sec / TRADE_DATE | `{"ts_code":"000001.SZ","start_date":"20240930","end_date":"20241001"}` | 证监会20240711暂停转融券新业务/20240930存量最迟了结；固定周边窗对照，通知不直接适用于融资或宣告API停用/最后返回日期 |
| `slb_sec-600000-suspension` | slb_sec / TRADE_DATE | `{"ts_code":"600000.SH","start_date":"20240701","end_date":"20240711"}` | 证监会20240711暂停转融券新业务/20240930存量最迟了结；固定周边窗对照，通知不直接适用于融资或宣告API停用/最后返回日期 |
| `slb_sec-600000-settlement` | slb_sec / TRADE_DATE | `{"ts_code":"600000.SH","start_date":"20240930","end_date":"20241001"}` | 证监会20240711暂停转融券新业务/20240930存量最迟了结；固定周边窗对照，通知不直接适用于融资或宣告API停用/最后返回日期 |
| `slb_sec_detail-000001-suspension` | slb_sec_detail / TRADE_DATE | `{"ts_code":"000001.SZ","start_date":"20240701","end_date":"20240711"}` | 证监会20240711暂停转融券新业务/20240930存量最迟了结；固定周边窗对照，通知不直接适用于融资或宣告API停用/最后返回日期 |
| `slb_sec_detail-000001-settlement` | slb_sec_detail / TRADE_DATE | `{"ts_code":"000001.SZ","start_date":"20240930","end_date":"20241001"}` | 证监会20240711暂停转融券新业务/20240930存量最迟了结；固定周边窗对照，通知不直接适用于融资或宣告API停用/最后返回日期 |
| `slb_sec_detail-600000-suspension` | slb_sec_detail / TRADE_DATE | `{"ts_code":"600000.SH","start_date":"20240701","end_date":"20240711"}` | 证监会20240711暂停转融券新业务/20240930存量最迟了结；固定周边窗对照，通知不直接适用于融资或宣告API停用/最后返回日期 |
| `slb_sec_detail-600000-settlement` | slb_sec_detail / TRADE_DATE | `{"ts_code":"600000.SH","start_date":"20240930","end_date":"20241001"}` | 证监会20240711暂停转融券新业务/20240930存量最迟了结；固定周边窗对照，通知不直接适用于融资或宣告API停用/最后返回日期 |

### ISSUE-024 执行前最终构建身份

- 原树保留原分支及前序改动；独立clone `/private/tmp/issue024-work-20260913T153000Z`完整复制850个Git管理文件，逐文件相等，快照SHA-256 `df2afa8b3e6704ac8aca97d49f1c5f02e7d688075e6dcf57b0dc3f6a82722b38`。普通命令白名单环境移除账户/DB。
- Probe首次RED56项5预期失败，GREEN56/56；独立复审无阻断问题，仅发现极端十进制规范化溢出。新增该例RED56项1失败后，捕获ArithmeticException并按不可用键计数；最终相关回归190/190通过，日志`regression.log`。
- 最终隔离`mvn -o -f data-plane/pom.xml -Pacceptance verify` exit0/BUILD SUCCESS；XML汇总1089项后端/包、失败/错误/跳过0，前端468通过。Node证据78/78、exit0。日志均在`/private/tmp/issue024-control/`，分别`build-final.log`、`node-pre-source.log`；先前未含修复的build.log不作为真实轮次最终身份。
- sourceDiffSha256 `74bf848dadb80b666faaff9e6d7dcd2aeb5ea96b39e70ab17b7f4a01cc867807`；productionJarSha256 `fad4613c267331b981f1cdc509a3700ef4d6a58513291049da7c6cef6eb97b17`；acceptanceJarSha256 `d7e9de1fd1c14fcd862c8e0eb419a2a342b27db012c62b44fb86d327f45d0f1b`；manifest `386f46a99b6605e203129836d7a744b96b65304307f52991dd8bba6fd1870984`；examples `6d4c74a1a539b59ac20fb0cbd3ba1fba0954c40ef1209b652f7dcc2192ec932f`。wrapper运行前后继续核对。尚未执行SOURCE。

### issue024-source-20260913T153146Z 实际结果

- UTC `2026-09-13T15:34:43.747Z`～`2026-09-13T15:35:33.534Z`；exit 0、cleanup PASS；23case/23请求，状态{'PASS': 19, 'EVIDENCE_MISSING': 4}。安全原对象与预登记输入逐项相符，完整追加唯一索引，旧run未改。
- 来源指纹 `74bf848dadb80b666faaff9e6d7dcd2aeb5ea96b39e70ab17b7f4a01cc867807`，生产包 `fad4613c267331b981f1cdc509a3700ef4d6a58513291049da7c6cef6eb97b17`，验收包 `d7e9de1fd1c14fcd862c8e0eb419a2a342b27db012c62b44fb86d327f45d0f1b`；manifest `386f46a99b6605e203129836d7a744b96b65304307f52991dd8bba6fd1870984`，examples `6d4c74a1a539b59ac20fb0cbd3ba1fba0954c40ef1209b652f7dcc2192ec932f`。wrapper清理检查结果见本轮私有source-run.json；SOURCE的TASK/SQL字段为null。

| caseId后缀 | 状态 | 行数 | 安全日期/计数/摘要观察 |
| --- | --- | ---: | --- |
| `len-whole` | PASS | 13 | actualDates=20240603,20240604,20240605,20240606,20240607,20240611,20240612,20240613,20240614,20240617,20240618,20240619,20240620; uniqueDateCount=13; min=20240603; max=20240620; candidateRowLimit=5000; candidateLimitReached=false; slbBusinessKeyValidRows=13; slbBusinessKeyUnavailableRows=0; slbDistinctBusinessKeyCount=13; slbDuplicateBusinessKeyRows=0; slbSameDateMultipleKeyGroupCount=0; slbBusinessKeyDigestSha256=4c09e6289aa276b38478e0cb9516c40c2e8d04bd27bc24fbe6db1025341600ab |
| `len-lower` | PASS | 1 | actualDates=20240603; uniqueDateCount=1; min=20240603; max=20240603; candidateRowLimit=5000; candidateLimitReached=false; slbBusinessKeyValidRows=1; slbBusinessKeyUnavailableRows=0; slbDistinctBusinessKeyCount=1; slbDuplicateBusinessKeyRows=0; slbSameDateMultipleKeyGroupCount=0; slbBusinessKeyDigestSha256=34381335c26e8b7656a843b688b896ae5f0ed9b3e37d368728a0dfc15c0387ce |
| `len-upper` | PASS | 1 | actualDates=20240620; uniqueDateCount=1; min=20240620; max=20240620; candidateRowLimit=5000; candidateLimitReached=false; slbBusinessKeyValidRows=1; slbBusinessKeyUnavailableRows=0; slbDistinctBusinessKeyCount=1; slbDuplicateBusinessKeyRows=0; slbSameDateMultipleKeyGroupCount=0; slbBusinessKeyDigestSha256=543f870746f111130bd19917a6f7d9d44e3932cec30856400fabec2b1c8d408f |
| `slb_sec-000001-whole` | PASS | 13 | actualDates=20240603,20240604,20240605,20240606,20240607,20240611,20240612,20240613,20240614,20240617,20240618,20240619,20240620; uniqueDateCount=13; min=20240603; max=20240620; candidateRowLimit=5000; candidateLimitReached=false; slbBusinessKeyValidRows=13; slbBusinessKeyUnavailableRows=0; slbDistinctBusinessKeyCount=13; slbDuplicateBusinessKeyRows=0; slbSameDateMultipleKeyGroupCount=0; slbBusinessKeyDigestSha256=123436b2e4afa593bf5ead1ba668faa6b03c3b7497d2cfba31e490a618fd8853 |
| `slb_sec-000001-official-day` | PASS | 1 | actualDates=20240620; uniqueDateCount=1; min=20240620; max=20240620; candidateRowLimit=5000; candidateLimitReached=false; slbBusinessKeyValidRows=1; slbBusinessKeyUnavailableRows=0; slbDistinctBusinessKeyCount=1; slbDuplicateBusinessKeyRows=0; slbSameDateMultipleKeyGroupCount=0; slbBusinessKeyDigestSha256=5500f79739902848b17851329d12095ffdf555114a570a282c126a6d7e21b9ba |
| `slb_sec-600000-whole` | PASS | 13 | actualDates=20240603,20240604,20240605,20240606,20240607,20240611,20240612,20240613,20240614,20240617,20240618,20240619,20240620; uniqueDateCount=13; min=20240603; max=20240620; candidateRowLimit=5000; candidateLimitReached=false; slbBusinessKeyValidRows=13; slbBusinessKeyUnavailableRows=0; slbDistinctBusinessKeyCount=13; slbDuplicateBusinessKeyRows=0; slbSameDateMultipleKeyGroupCount=0; slbBusinessKeyDigestSha256=99302cd3c7b4456ff79c520d08b663ef3e57e1bf241f0193eea08fb48e86f671 |
| `slb_sec-600000-official-day` | PASS | 1 | actualDates=20240620; uniqueDateCount=1; min=20240620; max=20240620; candidateRowLimit=5000; candidateLimitReached=false; slbBusinessKeyValidRows=1; slbBusinessKeyUnavailableRows=0; slbDistinctBusinessKeyCount=1; slbDuplicateBusinessKeyRows=0; slbSameDateMultipleKeyGroupCount=0; slbBusinessKeyDigestSha256=8b074fa8853565519d3f5ac22fc6b289b95c7d78fdd817c2186120a6576e4c5e |
| `slb_sec_detail-000001-whole` | PASS | 2 | actualDates=20240611,20240620; uniqueDateCount=2; min=20240611; max=20240620; candidateRowLimit=5000; candidateLimitReached=false; slbBusinessKeyValidRows=2; slbBusinessKeyUnavailableRows=0; slbDistinctBusinessKeyCount=2; slbDuplicateBusinessKeyRows=0; slbSameDateMultipleKeyGroupCount=0; slbBusinessKeyDigestSha256=bcdc9c22165a0c345eed7c78809ea05dd60b7b854dff1fa9f94f2cb858f91a5d; slbDistinctTenorCount=1; slbDistinctFeeRateCount=2; slbDistinctTenorFeeRatePairCount=2; slbSameStockDateMultipleTenorGroupCount=0; slbSameStockDateTenorMultipleFeeRateGroupCount=0 |
| `slb_sec_detail-000001-official-day` | PASS | 1 | actualDates=20240620; uniqueDateCount=1; min=20240620; max=20240620; candidateRowLimit=5000; candidateLimitReached=false; slbBusinessKeyValidRows=1; slbBusinessKeyUnavailableRows=0; slbDistinctBusinessKeyCount=1; slbDuplicateBusinessKeyRows=0; slbSameDateMultipleKeyGroupCount=0; slbBusinessKeyDigestSha256=a3206678f605c1843c7bbb316015ebc341ce1502d1453b62f92884696a7d689f; slbDistinctTenorCount=1; slbDistinctFeeRateCount=1; slbDistinctTenorFeeRatePairCount=1; slbSameStockDateMultipleTenorGroupCount=0; slbSameStockDateTenorMultipleFeeRateGroupCount=0 |
| `slb_sec_detail-600000-whole` | PASS | 3 | actualDates=20240605,20240613,20240620; uniqueDateCount=3; min=20240605; max=20240620; candidateRowLimit=5000; candidateLimitReached=false; slbBusinessKeyValidRows=3; slbBusinessKeyUnavailableRows=0; slbDistinctBusinessKeyCount=3; slbDuplicateBusinessKeyRows=0; slbSameDateMultipleKeyGroupCount=0; slbBusinessKeyDigestSha256=a7bbea76aa6ba9a54861b15a8d756eb49a5b26a41462c3208b781249d7e8e04d; slbDistinctTenorCount=1; slbDistinctFeeRateCount=3; slbDistinctTenorFeeRatePairCount=3; slbSameStockDateMultipleTenorGroupCount=0; slbSameStockDateTenorMultipleFeeRateGroupCount=0 |
| `slb_sec_detail-600000-official-day` | PASS | 1 | actualDates=20240620; uniqueDateCount=1; min=20240620; max=20240620; candidateRowLimit=5000; candidateLimitReached=false; slbBusinessKeyValidRows=1; slbBusinessKeyUnavailableRows=0; slbDistinctBusinessKeyCount=1; slbDuplicateBusinessKeyRows=0; slbSameDateMultipleKeyGroupCount=0; slbBusinessKeyDigestSha256=a991ecc0dbb21b7f6c6c53605e4097f0d60311fb918f58fa0d1faa5325f1fd93; slbDistinctTenorCount=1; slbDistinctFeeRateCount=1; slbDistinctTenorFeeRatePairCount=1; slbSameStockDateMultipleTenorGroupCount=0; slbSameStockDateTenorMultipleFeeRateGroupCount=0 |
| `detail-000001-long` | PASS | 242 | actualDates=见唯一JSON; uniqueDateCount=196; min=20230103; max=20240620; candidateRowLimit=5000; candidateLimitReached=false; slbBusinessKeyValidRows=242; slbBusinessKeyUnavailableRows=0; slbDistinctBusinessKeyCount=242; slbDuplicateBusinessKeyRows=0; slbSameDateMultipleKeyGroupCount=41; slbBusinessKeyDigestSha256=a70e1f56f74260d1b4cf4ac6cf64fc984b26270c2fda278b11159db7bb6d5230; slbDistinctTenorCount=9; slbDistinctFeeRateCount=54; slbDistinctTenorFeeRatePairCount=84; slbSameStockDateMultipleTenorGroupCount=41; slbSameStockDateTenorMultipleFeeRateGroupCount=0 |
| `detail-600000-long` | PASS | 190 | actualDates=见唯一JSON; uniqueDateCount=150; min=20230104; max=20240620; candidateRowLimit=5000; candidateLimitReached=false; slbBusinessKeyValidRows=190; slbBusinessKeyUnavailableRows=0; slbDistinctBusinessKeyCount=190; slbDuplicateBusinessKeyRows=0; slbSameDateMultipleKeyGroupCount=33; slbBusinessKeyDigestSha256=8a7af20d7ff86d164817580a1bed990ce31fee00ffa0b0e408a7a8d4b6879457; slbDistinctTenorCount=7; slbDistinctFeeRateCount=39; slbDistinctTenorFeeRatePairCount=62; slbSameStockDateMultipleTenorGroupCount=33; slbSameStockDateTenorMultipleFeeRateGroupCount=0 |
| `slb_len-all-suspension` | PASS | 9 | actualDates=20240701,20240702,20240703,20240704,20240705,20240708,20240709,20240710,20240711; uniqueDateCount=9; min=20240701; max=20240711; candidateRowLimit=5000; candidateLimitReached=false; slbBusinessKeyValidRows=9; slbBusinessKeyUnavailableRows=0; slbDistinctBusinessKeyCount=9; slbDuplicateBusinessKeyRows=0; slbSameDateMultipleKeyGroupCount=0; slbBusinessKeyDigestSha256=42758148831bc52b832a86a03555de2e6aabbfd9c6bb0f9d0c276e8e57711076 |
| `slb_len-all-settlement` | PASS | 1 | actualDates=20240930; uniqueDateCount=1; min=20240930; max=20240930; candidateRowLimit=5000; candidateLimitReached=false; slbBusinessKeyValidRows=1; slbBusinessKeyUnavailableRows=0; slbDistinctBusinessKeyCount=1; slbDuplicateBusinessKeyRows=0; slbSameDateMultipleKeyGroupCount=0; slbBusinessKeyDigestSha256=ed3783123a2c6353e6d9326542133e1eefde8a853a06d170966fd2141e72f565 |
| `slb_sec-000001-suspension` | PASS | 9 | actualDates=20240701,20240702,20240703,20240704,20240705,20240708,20240709,20240710,20240711; uniqueDateCount=9; min=20240701; max=20240711; candidateRowLimit=5000; candidateLimitReached=false; slbBusinessKeyValidRows=9; slbBusinessKeyUnavailableRows=0; slbDistinctBusinessKeyCount=9; slbDuplicateBusinessKeyRows=0; slbSameDateMultipleKeyGroupCount=0; slbBusinessKeyDigestSha256=8a01ed9cd0fc861fd983a1ca39e3a08dfd536b774f3f103cd97d6e0b11a05ea0 |
| `slb_sec-000001-settlement` | EVIDENCE_MISSING | 0 | actualDates=; uniqueDateCount=0; min=none; max=none; candidateRowLimit=5000; candidateLimitReached=false; slbBusinessKeyValidRows=0; slbBusinessKeyUnavailableRows=0; slbDistinctBusinessKeyCount=0; slbDuplicateBusinessKeyRows=0; slbSameDateMultipleKeyGroupCount=0; slbBusinessKeyDigestSha256=4f53cda18c2baa0c0354bb5f9a3ecbe5ed12ab4d8e11ba873c2f11161202b945 |
| `slb_sec-600000-suspension` | PASS | 9 | actualDates=20240701,20240702,20240703,20240704,20240705,20240708,20240709,20240710,20240711; uniqueDateCount=9; min=20240701; max=20240711; candidateRowLimit=5000; candidateLimitReached=false; slbBusinessKeyValidRows=9; slbBusinessKeyUnavailableRows=0; slbDistinctBusinessKeyCount=9; slbDuplicateBusinessKeyRows=0; slbSameDateMultipleKeyGroupCount=0; slbBusinessKeyDigestSha256=3e422fb8a70c9ff956f1813f146eaa835ee01f2255e11d24b8daa6a3707aae1d |
| `slb_sec-600000-settlement` | EVIDENCE_MISSING | 0 | actualDates=; uniqueDateCount=0; min=none; max=none; candidateRowLimit=5000; candidateLimitReached=false; slbBusinessKeyValidRows=0; slbBusinessKeyUnavailableRows=0; slbDistinctBusinessKeyCount=0; slbDuplicateBusinessKeyRows=0; slbSameDateMultipleKeyGroupCount=0; slbBusinessKeyDigestSha256=4f53cda18c2baa0c0354bb5f9a3ecbe5ed12ab4d8e11ba873c2f11161202b945 |
| `slb_sec_detail-000001-suspension` | PASS | 7 | actualDates=20240701,20240702,20240703,20240704,20240708,20240709,20240710; uniqueDateCount=7; min=20240701; max=20240710; candidateRowLimit=5000; candidateLimitReached=false; slbBusinessKeyValidRows=7; slbBusinessKeyUnavailableRows=0; slbDistinctBusinessKeyCount=7; slbDuplicateBusinessKeyRows=0; slbSameDateMultipleKeyGroupCount=0; slbBusinessKeyDigestSha256=eb1298227559bcce8c994fa5583d75de381ee13bd810633a6bbfbc7c56b1eef0; slbDistinctTenorCount=1; slbDistinctFeeRateCount=3; slbDistinctTenorFeeRatePairCount=3; slbSameStockDateMultipleTenorGroupCount=0; slbSameStockDateTenorMultipleFeeRateGroupCount=0 |
| `slb_sec_detail-000001-settlement` | EVIDENCE_MISSING | 0 | actualDates=; uniqueDateCount=0; min=none; max=none; candidateRowLimit=5000; candidateLimitReached=false; slbBusinessKeyValidRows=0; slbBusinessKeyUnavailableRows=0; slbDistinctBusinessKeyCount=0; slbDuplicateBusinessKeyRows=0; slbSameDateMultipleKeyGroupCount=0; slbBusinessKeyDigestSha256=4f53cda18c2baa0c0354bb5f9a3ecbe5ed12ab4d8e11ba873c2f11161202b945; slbDistinctTenorCount=0; slbDistinctFeeRateCount=0; slbDistinctTenorFeeRatePairCount=0; slbSameStockDateMultipleTenorGroupCount=0; slbSameStockDateTenorMultipleFeeRateGroupCount=0 |
| `slb_sec_detail-600000-suspension` | PASS | 5 | actualDates=20240701,20240703,20240708,20240709,20240710; uniqueDateCount=5; min=20240701; max=20240710; candidateRowLimit=5000; candidateLimitReached=false; slbBusinessKeyValidRows=5; slbBusinessKeyUnavailableRows=0; slbDistinctBusinessKeyCount=5; slbDuplicateBusinessKeyRows=0; slbSameDateMultipleKeyGroupCount=0; slbBusinessKeyDigestSha256=ce7610108df4153382a68edcc3d580d2fab9dde0c2b431b02ee982a1c2312bfa; slbDistinctTenorCount=1; slbDistinctFeeRateCount=2; slbDistinctTenorFeeRatePairCount=2; slbSameStockDateMultipleTenorGroupCount=0; slbSameStockDateTenorMultipleFeeRateGroupCount=0 |
| `slb_sec_detail-600000-settlement` | EVIDENCE_MISSING | 0 | actualDates=; uniqueDateCount=0; min=none; max=none; candidateRowLimit=5000; candidateLimitReached=false; slbBusinessKeyValidRows=0; slbBusinessKeyUnavailableRows=0; slbDistinctBusinessKeyCount=0; slbDuplicateBusinessKeyRows=0; slbSameDateMultipleKeyGroupCount=0; slbBusinessKeyDigestSha256=4f53cda18c2baa0c0354bb5f9a3ecbe5ed12ab4d8e11ba873c2f11161202b945; slbDistinctTenorCount=0; slbDistinctFeeRateCount=0; slbDistinctTenorFeeRatePairCount=0; slbSameStockDateMultipleTenorGroupCount=0; slbSameStockDateTenorMultipleFeeRateGroupCount=0 |

### ISSUE-024 第二轮固定边界计划

- 首轮23case/23请求，19非空PASS、4个转融券0930～1001空EVIDENCE_MISSING，exit0/cleanup PASS。融资官网窗口13行/13原键，一日一行；两股票明细长窗分别242行/9期限/54费率及190行/7期限/39费率，同股同日不同期限分别41/33组，原始行未去重。
- 两股票汇总最早0603；明细000001.SZ最早0611、600000.SH最早0605，均到0620。监管周边汇总可读到0711，明细本窗口最后为0710；这不是全历史最后日期。第二轮据此固定边界，不搜索替代样本。
- runId `issue024-boundary-source-20260913T153639Z`，16case/16预期请求；同一隔离源码/两包/wrapper；至少2000ms、30分钟/5000次、失败停轮无自动重试。私有清单`/private/tmp/issue024-boundary-source-20260913T153639Z/cases.json`，0600/父0700，SHA-256 `57a853459b9d5242fe215aa889ec5559313ebcda13b3a55343a232f92a89408b`。以下仅预登记，未执行。

| caseId后缀 | API / dateAxis | 精确params | 依据与预期 |
| --- | --- | --- | --- |
| `slb_sec-000001-lower` | slb_sec / TRADE_DATE | `{"ts_code":"000001.SZ","start_date":"20240603","end_date":"20240603"}` | 首轮issue024-source-20260913T153146Z-slb_sec-000001-whole实际最早/最晚日期固定，单日下边界与含该日整段；上边界0620已有独立非空 |
| `slb_sec-000001-lower-edge` | slb_sec / TRADE_DATE | `{"ts_code":"000001.SZ","start_date":"20240603","end_date":"20240620"}` | 首轮issue024-source-20260913T153146Z-slb_sec-000001-whole实际最早/最晚日期固定，单日下边界与含该日整段；上边界0620已有独立非空 |
| `slb_sec-000001-20240710` | slb_sec / TRADE_DATE | `{"ts_code":"000001.SZ","start_date":"20240710","end_date":"20240710"}` | 首轮监管周边窗口的实际日期：汇总到0711，明细到0710；固定0710/0711单日对照，不推导最后可访问日期 |
| `slb_sec-000001-20240711` | slb_sec / TRADE_DATE | `{"ts_code":"000001.SZ","start_date":"20240711","end_date":"20240711"}` | 首轮监管周边窗口的实际日期：汇总到0711，明细到0710；固定0710/0711单日对照，不推导最后可访问日期 |
| `slb_sec-600000-lower` | slb_sec / TRADE_DATE | `{"ts_code":"600000.SH","start_date":"20240603","end_date":"20240603"}` | 首轮issue024-source-20260913T153146Z-slb_sec-600000-whole实际最早/最晚日期固定，单日下边界与含该日整段；上边界0620已有独立非空 |
| `slb_sec-600000-lower-edge` | slb_sec / TRADE_DATE | `{"ts_code":"600000.SH","start_date":"20240603","end_date":"20240620"}` | 首轮issue024-source-20260913T153146Z-slb_sec-600000-whole实际最早/最晚日期固定，单日下边界与含该日整段；上边界0620已有独立非空 |
| `slb_sec-600000-20240710` | slb_sec / TRADE_DATE | `{"ts_code":"600000.SH","start_date":"20240710","end_date":"20240710"}` | 首轮监管周边窗口的实际日期：汇总到0711，明细到0710；固定0710/0711单日对照，不推导最后可访问日期 |
| `slb_sec-600000-20240711` | slb_sec / TRADE_DATE | `{"ts_code":"600000.SH","start_date":"20240711","end_date":"20240711"}` | 首轮监管周边窗口的实际日期：汇总到0711，明细到0710；固定0710/0711单日对照，不推导最后可访问日期 |
| `slb_sec_detail-000001-lower` | slb_sec_detail / TRADE_DATE | `{"ts_code":"000001.SZ","start_date":"20240611","end_date":"20240611"}` | 首轮issue024-source-20260913T153146Z-slb_sec_detail-000001-whole实际最早/最晚日期固定，单日下边界与含该日整段；上边界0620已有独立非空 |
| `slb_sec_detail-000001-lower-edge` | slb_sec_detail / TRADE_DATE | `{"ts_code":"000001.SZ","start_date":"20240611","end_date":"20240620"}` | 首轮issue024-source-20260913T153146Z-slb_sec_detail-000001-whole实际最早/最晚日期固定，单日下边界与含该日整段；上边界0620已有独立非空 |
| `slb_sec_detail-000001-20240710` | slb_sec_detail / TRADE_DATE | `{"ts_code":"000001.SZ","start_date":"20240710","end_date":"20240710"}` | 首轮监管周边窗口的实际日期：汇总到0711，明细到0710；固定0710/0711单日对照，不推导最后可访问日期 |
| `slb_sec_detail-000001-20240711` | slb_sec_detail / TRADE_DATE | `{"ts_code":"000001.SZ","start_date":"20240711","end_date":"20240711"}` | 首轮监管周边窗口的实际日期：汇总到0711，明细到0710；固定0710/0711单日对照，不推导最后可访问日期 |
| `slb_sec_detail-600000-lower` | slb_sec_detail / TRADE_DATE | `{"ts_code":"600000.SH","start_date":"20240605","end_date":"20240605"}` | 首轮issue024-source-20260913T153146Z-slb_sec_detail-600000-whole实际最早/最晚日期固定，单日下边界与含该日整段；上边界0620已有独立非空 |
| `slb_sec_detail-600000-lower-edge` | slb_sec_detail / TRADE_DATE | `{"ts_code":"600000.SH","start_date":"20240605","end_date":"20240620"}` | 首轮issue024-source-20260913T153146Z-slb_sec_detail-600000-whole实际最早/最晚日期固定，单日下边界与含该日整段；上边界0620已有独立非空 |
| `slb_sec_detail-600000-20240710` | slb_sec_detail / TRADE_DATE | `{"ts_code":"600000.SH","start_date":"20240710","end_date":"20240710"}` | 首轮监管周边窗口的实际日期：汇总到0711，明细到0710；固定0710/0711单日对照，不推导最后可访问日期 |
| `slb_sec_detail-600000-20240711` | slb_sec_detail / TRADE_DATE | `{"ts_code":"600000.SH","start_date":"20240711","end_date":"20240711"}` | 首轮监管周边窗口的实际日期：汇总到0711，明细到0710；固定0710/0711单日对照，不推导最后可访问日期 |

### issue024-boundary-source-20260913T153639Z 实际结果

- UTC `2026-09-13T15:36:49.409Z`～`2026-09-13T15:37:23.303Z`；exit 0、cleanup PASS；16case/16请求，状态{'PASS': 14, 'EVIDENCE_MISSING': 2}。安全原对象与预登记输入逐项相符，完整追加唯一索引，旧run未改。
- 来源指纹 `74bf848dadb80b666faaff9e6d7dcd2aeb5ea96b39e70ab17b7f4a01cc867807`，生产包 `fad4613c267331b981f1cdc509a3700ef4d6a58513291049da7c6cef6eb97b17`，验收包 `d7e9de1fd1c14fcd862c8e0eb419a2a342b27db012c62b44fb86d327f45d0f1b`；manifest `386f46a99b6605e203129836d7a744b96b65304307f52991dd8bba6fd1870984`，examples `6d4c74a1a539b59ac20fb0cbd3ba1fba0954c40ef1209b652f7dcc2192ec932f`。wrapper清理检查结果见本轮私有source-run.json；SOURCE的TASK/SQL字段为null。

| caseId后缀 | 状态 | 行数 | 安全日期/计数/摘要观察 |
| --- | --- | ---: | --- |
| `slb_sec-000001-lower` | PASS | 1 | actualDates=20240603; uniqueDateCount=1; min=20240603; max=20240603; candidateRowLimit=5000; candidateLimitReached=false; slbBusinessKeyValidRows=1; slbBusinessKeyUnavailableRows=0; slbDistinctBusinessKeyCount=1; slbDuplicateBusinessKeyRows=0; slbSameDateMultipleKeyGroupCount=0; slbBusinessKeyDigestSha256=6a871d9f2f2dce91b685f935379a515c6ea5fdf3bf138285de24b74e93eaf2d5 |
| `slb_sec-000001-lower-edge` | PASS | 13 | actualDates=20240603,20240604,20240605,20240606,20240607,20240611,20240612,20240613,20240614,20240617,20240618,20240619,20240620; uniqueDateCount=13; min=20240603; max=20240620; candidateRowLimit=5000; candidateLimitReached=false; slbBusinessKeyValidRows=13; slbBusinessKeyUnavailableRows=0; slbDistinctBusinessKeyCount=13; slbDuplicateBusinessKeyRows=0; slbSameDateMultipleKeyGroupCount=0; slbBusinessKeyDigestSha256=123436b2e4afa593bf5ead1ba668faa6b03c3b7497d2cfba31e490a618fd8853 |
| `slb_sec-000001-20240710` | PASS | 1 | actualDates=20240710; uniqueDateCount=1; min=20240710; max=20240710; candidateRowLimit=5000; candidateLimitReached=false; slbBusinessKeyValidRows=1; slbBusinessKeyUnavailableRows=0; slbDistinctBusinessKeyCount=1; slbDuplicateBusinessKeyRows=0; slbSameDateMultipleKeyGroupCount=0; slbBusinessKeyDigestSha256=f55a0e3b52d5e5f838517655ec6ab5a3395e1b3ed481e31bcbd04705ac147ce4 |
| `slb_sec-000001-20240711` | PASS | 1 | actualDates=20240711; uniqueDateCount=1; min=20240711; max=20240711; candidateRowLimit=5000; candidateLimitReached=false; slbBusinessKeyValidRows=1; slbBusinessKeyUnavailableRows=0; slbDistinctBusinessKeyCount=1; slbDuplicateBusinessKeyRows=0; slbSameDateMultipleKeyGroupCount=0; slbBusinessKeyDigestSha256=d602d62602b615431bdd2a48cb3395696491be356ad67568657f66894387ee83 |
| `slb_sec-600000-lower` | PASS | 1 | actualDates=20240603; uniqueDateCount=1; min=20240603; max=20240603; candidateRowLimit=5000; candidateLimitReached=false; slbBusinessKeyValidRows=1; slbBusinessKeyUnavailableRows=0; slbDistinctBusinessKeyCount=1; slbDuplicateBusinessKeyRows=0; slbSameDateMultipleKeyGroupCount=0; slbBusinessKeyDigestSha256=e399dd6f7d5eb7bff03cf0e786244dbefaadef46e691b34c6e7f0565086d957d |
| `slb_sec-600000-lower-edge` | PASS | 13 | actualDates=20240603,20240604,20240605,20240606,20240607,20240611,20240612,20240613,20240614,20240617,20240618,20240619,20240620; uniqueDateCount=13; min=20240603; max=20240620; candidateRowLimit=5000; candidateLimitReached=false; slbBusinessKeyValidRows=13; slbBusinessKeyUnavailableRows=0; slbDistinctBusinessKeyCount=13; slbDuplicateBusinessKeyRows=0; slbSameDateMultipleKeyGroupCount=0; slbBusinessKeyDigestSha256=99302cd3c7b4456ff79c520d08b663ef3e57e1bf241f0193eea08fb48e86f671 |
| `slb_sec-600000-20240710` | PASS | 1 | actualDates=20240710; uniqueDateCount=1; min=20240710; max=20240710; candidateRowLimit=5000; candidateLimitReached=false; slbBusinessKeyValidRows=1; slbBusinessKeyUnavailableRows=0; slbDistinctBusinessKeyCount=1; slbDuplicateBusinessKeyRows=0; slbSameDateMultipleKeyGroupCount=0; slbBusinessKeyDigestSha256=995c9c13606fda0d28e7abc2e866731c899088510caeb2370de167e364e6c430 |
| `slb_sec-600000-20240711` | PASS | 1 | actualDates=20240711; uniqueDateCount=1; min=20240711; max=20240711; candidateRowLimit=5000; candidateLimitReached=false; slbBusinessKeyValidRows=1; slbBusinessKeyUnavailableRows=0; slbDistinctBusinessKeyCount=1; slbDuplicateBusinessKeyRows=0; slbSameDateMultipleKeyGroupCount=0; slbBusinessKeyDigestSha256=b853d24867661e7c9d7b610238d0be66130eaf14a6b1abe0f90f25ee7e78b746 |
| `slb_sec_detail-000001-lower` | PASS | 1 | actualDates=20240611; uniqueDateCount=1; min=20240611; max=20240611; candidateRowLimit=5000; candidateLimitReached=false; slbBusinessKeyValidRows=1; slbBusinessKeyUnavailableRows=0; slbDistinctBusinessKeyCount=1; slbDuplicateBusinessKeyRows=0; slbSameDateMultipleKeyGroupCount=0; slbBusinessKeyDigestSha256=d2b622505bc73db53ed599a647297b096cb4c474998157bb05d9069fe397a3c2; slbDistinctTenorCount=1; slbDistinctFeeRateCount=1; slbDistinctTenorFeeRatePairCount=1; slbSameStockDateMultipleTenorGroupCount=0; slbSameStockDateTenorMultipleFeeRateGroupCount=0 |
| `slb_sec_detail-000001-lower-edge` | PASS | 2 | actualDates=20240611,20240620; uniqueDateCount=2; min=20240611; max=20240620; candidateRowLimit=5000; candidateLimitReached=false; slbBusinessKeyValidRows=2; slbBusinessKeyUnavailableRows=0; slbDistinctBusinessKeyCount=2; slbDuplicateBusinessKeyRows=0; slbSameDateMultipleKeyGroupCount=0; slbBusinessKeyDigestSha256=bcdc9c22165a0c345eed7c78809ea05dd60b7b854dff1fa9f94f2cb858f91a5d; slbDistinctTenorCount=1; slbDistinctFeeRateCount=2; slbDistinctTenorFeeRatePairCount=2; slbSameStockDateMultipleTenorGroupCount=0; slbSameStockDateTenorMultipleFeeRateGroupCount=0 |
| `slb_sec_detail-000001-20240710` | PASS | 1 | actualDates=20240710; uniqueDateCount=1; min=20240710; max=20240710; candidateRowLimit=5000; candidateLimitReached=false; slbBusinessKeyValidRows=1; slbBusinessKeyUnavailableRows=0; slbDistinctBusinessKeyCount=1; slbDuplicateBusinessKeyRows=0; slbSameDateMultipleKeyGroupCount=0; slbBusinessKeyDigestSha256=0e798df89eb7c33505421a00c538cc6f4c9b6216226cd27ffa26e5ecef1a2bf3; slbDistinctTenorCount=1; slbDistinctFeeRateCount=1; slbDistinctTenorFeeRatePairCount=1; slbSameStockDateMultipleTenorGroupCount=0; slbSameStockDateTenorMultipleFeeRateGroupCount=0 |
| `slb_sec_detail-000001-20240711` | EVIDENCE_MISSING | 0 | actualDates=; uniqueDateCount=0; min=none; max=none; candidateRowLimit=5000; candidateLimitReached=false; slbBusinessKeyValidRows=0; slbBusinessKeyUnavailableRows=0; slbDistinctBusinessKeyCount=0; slbDuplicateBusinessKeyRows=0; slbSameDateMultipleKeyGroupCount=0; slbBusinessKeyDigestSha256=4f53cda18c2baa0c0354bb5f9a3ecbe5ed12ab4d8e11ba873c2f11161202b945; slbDistinctTenorCount=0; slbDistinctFeeRateCount=0; slbDistinctTenorFeeRatePairCount=0; slbSameStockDateMultipleTenorGroupCount=0; slbSameStockDateTenorMultipleFeeRateGroupCount=0 |
| `slb_sec_detail-600000-lower` | PASS | 1 | actualDates=20240605; uniqueDateCount=1; min=20240605; max=20240605; candidateRowLimit=5000; candidateLimitReached=false; slbBusinessKeyValidRows=1; slbBusinessKeyUnavailableRows=0; slbDistinctBusinessKeyCount=1; slbDuplicateBusinessKeyRows=0; slbSameDateMultipleKeyGroupCount=0; slbBusinessKeyDigestSha256=0d9ecb995aea47d43970c2b1bb6ab5b82f5a1262f4d65846a6735cb3699c3c5c; slbDistinctTenorCount=1; slbDistinctFeeRateCount=1; slbDistinctTenorFeeRatePairCount=1; slbSameStockDateMultipleTenorGroupCount=0; slbSameStockDateTenorMultipleFeeRateGroupCount=0 |
| `slb_sec_detail-600000-lower-edge` | PASS | 3 | actualDates=20240605,20240613,20240620; uniqueDateCount=3; min=20240605; max=20240620; candidateRowLimit=5000; candidateLimitReached=false; slbBusinessKeyValidRows=3; slbBusinessKeyUnavailableRows=0; slbDistinctBusinessKeyCount=3; slbDuplicateBusinessKeyRows=0; slbSameDateMultipleKeyGroupCount=0; slbBusinessKeyDigestSha256=a7bbea76aa6ba9a54861b15a8d756eb49a5b26a41462c3208b781249d7e8e04d; slbDistinctTenorCount=1; slbDistinctFeeRateCount=3; slbDistinctTenorFeeRatePairCount=3; slbSameStockDateMultipleTenorGroupCount=0; slbSameStockDateTenorMultipleFeeRateGroupCount=0 |
| `slb_sec_detail-600000-20240710` | PASS | 1 | actualDates=20240710; uniqueDateCount=1; min=20240710; max=20240710; candidateRowLimit=5000; candidateLimitReached=false; slbBusinessKeyValidRows=1; slbBusinessKeyUnavailableRows=0; slbDistinctBusinessKeyCount=1; slbDuplicateBusinessKeyRows=0; slbSameDateMultipleKeyGroupCount=0; slbBusinessKeyDigestSha256=25be8d29390bf5f7eb724059adbf74c187ba181be5fc7c37e25e796986b01214; slbDistinctTenorCount=1; slbDistinctFeeRateCount=1; slbDistinctTenorFeeRatePairCount=1; slbSameStockDateMultipleTenorGroupCount=0; slbSameStockDateTenorMultipleFeeRateGroupCount=0 |
| `slb_sec_detail-600000-20240711` | EVIDENCE_MISSING | 0 | actualDates=; uniqueDateCount=0; min=none; max=none; candidateRowLimit=5000; candidateLimitReached=false; slbBusinessKeyValidRows=0; slbBusinessKeyUnavailableRows=0; slbDistinctBusinessKeyCount=0; slbDuplicateBusinessKeyRows=0; slbSameDateMultipleKeyGroupCount=0; slbBusinessKeyDigestSha256=4f53cda18c2baa0c0354bb5f9a3ecbe5ed12ab4d8e11ba873c2f11161202b945; slbDistinctTenorCount=0; slbDistinctFeeRateCount=0; slbDistinctTenorFeeRatePairCount=0; slbSameStockDateMultipleTenorGroupCount=0; slbSameStockDateTenorMultipleFeeRateGroupCount=0 |

### ISSUE-024 本轮验证与待决状态

- 两轮39case/39请求，33非空PASS、6空EVIDENCE_MISSING；两个exit0/cleanup PASS，输入/原对象/源码/包均逐项相同。唯一JSON为22轮/687case/751请求；旧20轮/648case及其他37接口/顶层输入哈希保持，生产4 AVAILABLE不变。
- `verify-observations.py`确认所有39项原键有效计数等于原始行数、不可用/重复均0、每片<5000、非空边界和整段窄化摘要一致；明细期限/费率及同股同日组数、空对照、权限和四公开原件哈希均成立。`verify-identity.py`确认执行前后两包/源码及当前Probe/测试一致。
- `verify-handoff.mjs`确认33项唯一参数精确SOURCE绑定，6新空+9旧空/失败RANGE共15个负例全部拒绝；私有候选只用于离线复查，实际TASK尚未登记或执行。[ISSUE-026条件输入](../issues/problems/ISSUE-026-range-task-final-acceptance.md#issue-024-已交付输入)可由表格重建。
- 当前证据预期更新前Node78项1失败，因原断言仍要求SOURCE_RUN_FAILED当前缺口；同步三接口真实引用/聚合及禁止SOURCE直接开放后，Node78/78、无失败/跳过、exit0。日志`/private/tmp/issue024-control/node-current-red.log`、`node-final.log`。独立clone保持真实执行时的证据测试版本，最终主树同步没有回填SOURCE身份。
- 190相关Maven、隔离1089后端/包+468前端通过；40行报告与唯一JSON相同，99本地链接/锚点和`git diff --check`通过。首次私有40行检查正则未包含既有6行“不适用”，修正检查器后40行全部通过；未修改源数据迎合检查。
- 取证工具独立复审发现的极端数值问题已RED复现并修复，最终完整证据复审另行登记。三项新文档已Git纳管；没有自动提交、发布、修改数据库或提前开放生产。
- 已向用户提交[方案A/B](../issues/proposals/ISSUE-024-historical-support.md)，尚未收到决定。原issue关闭条件要求历史支持依据不足时交用户决策，因此保留IN_PROGRESS，不自行放宽起止/持续保留要求。ISSUE-025与ISSUE-026仍NOT_STARTED，T13/T14及母issue保持原状态。

### ISSUE-024 最终独立复审

最终只读复审无Critical/Important/Minor发现，确认材料可供用户作具体A/B决定、当前IN_PROGRESS正确。复审独立执行当前Node24证据78/78、39项观察/身份校验、33精确绑定及15负例检查；重建40行报告和ISSUE-026的33行参数表，全部与唯一JSON相等。旧20轮/其他37接口保持，原始安全对象及预登记输入逐项相同。

复审核对build-final日志及对应XML，1089项后端/包、468前端通过；后续真实Probe额外生成1份测试结果，不能将后来的1090全XML和构建1089混算。当前299个data-plane/scripts/docs-contracts文件与固定clone相同，两包/源码身份保持。原三接口历史支持关闭要求尚未修改，需用户明确采用决定后才可进行限定修订及最终关闭；未提前完成ISSUE-024或母issue。

### ISSUE-024 方案A确认与最终验收

- 2026-09-14（Asia/Shanghai）用户对上轮具体问题明确回复“同意方案 A（推荐）”，采用[方案A全部四项与限定差异](../issues/proposals/ISSUE-024-historical-support.md#决策记录)。T13/T14及专属设计限定修订为历史查询能力与代表窗口验收；精确历史起止/持续保留保证仍未知，不再等待其补齐。本决定不创造上游保证、不新增日期限制、不改变5000拆分/单日满额失败或原业务键。
- 决定后Node独立预期先RED78项1失败（decisionRef仍null），更新三接口决定引用并解除HISTORICAL_RETENTION_CONTRACT_DECISION_PENDING后GREEN78/78、无失败/跳过，exit0；不提前开放的拒绝检查保持。日志`/private/tmp/issue024-close-20260914/node-red.log`、`node-green.log`。
- 决定后`verify-handoff.mjs`确认33项精确参数及SOURCE绑定，6新空+9旧空/失败共15负例仍拒绝，任务引用新增明确用户决定。所有22轮/687case/751请求与决定前快照逐对象相同，其他37接口与完整性规则、SOURCE聚合、任务状态、准入版本均保持；只有三接口decisionRef和当前待决说明改变。
- `verify-observations.py`与`verify-identity.py`再次核对39项原始安全对象、非空边界/摘要/期限费率、私有权限/公开原件哈希/40行报告及固定源码/双包通过。未新增账户请求或TASK/SQL。前次190相关Maven、隔离1089后端/包与468前端的证据保持有效；本轮仅修改决定文档及Node预期，没有重跑账户或宣称新Maven构建。
- [ISSUE-026已交付输入](../issues/problems/ISSUE-026-range-task-final-acceptance.md#issue-024-已交付输入)正式接收历史采用规则与33项候选，6空EVIDENCE_MISSING保留。生产三接口仍NEEDS_VERIFICATION/v1，4 AVAILABLE不变，实际候选包/RANGE TASK/SQL及适用六门禁继续由ISSUE-026验收。
- 决定增量独立复审无Critical/Important/Minor发现；复审重建33行交付表和决定引用，重新执行Node78/78并确认全部22轮和其他37接口未改。174个本地链接/锚点及diff检查通过，原私有候选已按决定引用重新生成。按已批准口径逐条复核专属设计及问题文档的三项关闭条件成立，先记录`IN_PROGRESS → COMPLETED`；本issue完成。下一项ISSUE-025保持NOT_STARTED，未创建占位交接；T13/T14及母issue未关闭。

## ISSUE-025 十一接口提取规则与来源

用户2026-09-14要求“完成issue25”，[专属设计](../task-designs/ISSUE-025-design.md)及[计划](../superpowers/plans/2026-09-14-issue-025.md)已建立。基线22轮/687case/751请求；原索引备份`/private/tmp/issue025-control/baseline.json`。十一完整性仍UNKNOWN，生产准入/版本保持，新增SOURCE不产生任务或SQL。公开复核见[§14](ISSUE-018-T14-official-evidence.md#14-issue-025-十一接口提取合同复核2026-09-14)。

### ISSUE-025 第一轮固定来源计划

- runId `issue025-source-20260913T162759Z`，23case/23请求；至少2000ms，30分钟/5000请求硬上限、35分钟外层超时，失败停轮、无自动重试。
- 清单`/private/tmp/issue025-source-20260913T162759Z/cases.json`，文件0600/目录0700，SHA-256 `76e705284783c46acec9ab3cb49c681ad4c84408d3aa6820edc9921de718137f`。此时仅预登记，待隔离构建与身份核对后执行。固定历史窗只用于有限调查，不定义支持边界；空结果保留。

| caseId后缀 | API / dateAxis | 精确params | 来源与观察目的 |
| --- | --- | --- | --- |
| `adj_factor-000001-whole` | adj_factor / TRADE_DATE | `{"ts_code":"000001.SZ","start_date":"20251229","end_date":"20260105"}` | 官网28股票/日期参数与历史能力；固定跨年窗，不按每日恰一行构造完整性 |
| `adj_factor-600000-whole` | adj_factor / TRADE_DATE | `{"ts_code":"600000.SH","start_date":"20251229","end_date":"20260105"}` | 官网28股票/日期参数与历史能力；固定跨年窗，不按每日恰一行构造完整性 |
| `income-000001-whole` | income / ANNOUNCEMENT_DATE | `{"ts_code":"000001.SZ","start_date":"20240101","end_date":"20241231"}` | 官网33公告轴；固定2024完整年度调查两股票公告/报告期和有效端点 |
| `income-600000-whole` | income / ANNOUNCEMENT_DATE | `{"ts_code":"600000.SH","start_date":"20240101","end_date":"20241231"}` | 官网33公告轴；固定2024完整年度调查两股票公告/报告期和有效端点 |
| `balancesheet-000001-whole` | balancesheet / ANNOUNCEMENT_DATE | `{"ts_code":"000001.SZ","start_date":"20240101","end_date":"20241231"}` | 官网36公告轴；固定2024完整年度调查公告/报告期和有效端点 |
| `balancesheet-600000-whole` | balancesheet / ANNOUNCEMENT_DATE | `{"ts_code":"600000.SH","start_date":"20240101","end_date":"20241231"}` | 官网36公告轴；固定2024完整年度调查公告/报告期和有效端点 |
| `cashflow-000001-whole` | cashflow / ANNOUNCEMENT_DATE | `{"ts_code":"000001.SZ","start_date":"20240101","end_date":"20241231"}` | 官网44公告轴及独立f_ann_date；固定2024年度同一行对照 |
| `cashflow-600000-whole` | cashflow / ANNOUNCEMENT_DATE | `{"ts_code":"600000.SH","start_date":"20240101","end_date":"20241231"}` | 官网44公告轴及独立f_ann_date；固定2024年度同一行对照 |
| `fina_audit-000001-whole` | fina_audit / ANNOUNCEMENT_DATE | `{"ts_code":"000001.SZ","start_date":"20240101","end_date":"20241231"}` | 官网80公告起止和历史年度样例；2024年度是固定调查窗，不预填非空 |
| `fina_audit-600000-whole` | fina_audit / ANNOUNCEMENT_DATE | `{"ts_code":"600000.SH","start_date":"20240101","end_date":"20241231"}` | 官网80公告起止和历史年度样例；2024年度是固定调查窗，不预填非空 |
| `express-000001-whole` | express / ANNOUNCEMENT_DATE | `{"ts_code":"000001.SZ","start_date":"20180101","end_date":"20180701"}` | 官网46明确同样的600000.SH历史请求窗；000001.SZ为原合同独立第二样本 |
| `express-600000-whole` | express / ANNOUNCEMENT_DATE | `{"ts_code":"600000.SH","start_date":"20180101","end_date":"20180701"}` | 官网46明确同样的600000.SH历史请求窗；000001.SZ为原合同独立第二样本 |
| `top10_holders-000001-whole` | top10_holders / REPORT_PERIOD | `{"ts_code":"000001.SZ","start_date":"20170101","end_date":"20171231"}` | 官网61同样的600000.SH报告期请求窗；000001.SZ独立验证 |
| `top10_holders-600000-whole` | top10_holders / REPORT_PERIOD | `{"ts_code":"600000.SH","start_date":"20170101","end_date":"20171231"}` | 官网61同样的600000.SH报告期请求窗；000001.SZ独立验证 |
| `top10_floatholders-000001-whole` | top10_floatholders / REPORT_PERIOD | `{"ts_code":"000001.SZ","start_date":"20170101","end_date":"20171231"}` | 官网62同样的600000.SH报告期请求窗；000001.SZ独立验证 |
| `top10_floatholders-600000-whole` | top10_floatholders / REPORT_PERIOD | `{"ts_code":"600000.SH","start_date":"20170101","end_date":"20171231"}` | 官网62同样的600000.SH报告期请求窗；000001.SZ独立验证 |
| `stk_managers-000001-whole` | stk_managers / ANNOUNCEMENT_DATE | `{"ts_code":"000001.SZ","start_date":"20180101","end_date":"20190630"}` | 官网193展示000001.SZ的2018到20190604公告；600000.SH为原合同独立样本 |
| `stk_managers-600000-whole` | stk_managers / ANNOUNCEMENT_DATE | `{"ts_code":"600000.SH","start_date":"20180101","end_date":"20190630"}` | 官网193展示000001.SZ的2018到20190604公告；600000.SH为原合同独立样本 |
| `suspend_d-000001-whole` | suspend_d / TRADE_DATE | `{"ts_code":"000001.SZ","start_date":"20000101","end_date":"20251231"}` | 官网214提供停复牌起止参数；基准股票固定有限历史调查，不声明API历史起点/连续全集 |
| `suspend_d-600000-whole` | suspend_d / TRADE_DATE | `{"ts_code":"600000.SH","start_date":"20000101","end_date":"20251231"}` | 官网214提供停复牌起止参数；基准股票固定有限历史调查，不声明API历史起点/连续全集 |
| `repurchase-all-whole` | repurchase / ANNOUNCEMENT_DATE | `{"start_date":"20260801","end_date":"20260831"}` | 原representative SOURCE同窗852行/585股票；本轮核对非股票原方式、日期和端点，不把默认2000当上限 |
| `suspend_d-000029-whole` | suspend_d / TRADE_DATE | `{"ts_code":"000029.SZ","start_date":"20200309","end_date":"20200313"}` | 官网214明确该股20200312停牌；固定该周观察连续日期/S/R，补充样本不替代基准股票要求 |
| `suspend_d-600310-whole` | suspend_d / TRADE_DATE | `{"ts_code":"600310.SH","start_date":"20200309","end_date":"20200313"}` | 官网214明确该股20200312停牌；固定该周观察连续日期/S/R，补充样本不替代基准股票要求 |

### ISSUE-025 隔离构建与输入身份

- 独立clone `/private/tmp/issue025-work-20260913T162352Z`，完整复制853个Git管理文件并逐文件核对，初始快照SHA-256 `5cbe3053a32842026e97d51dd333459d35e2181ef7c3d2b4bdd3464c3fc15758`。独立复审后补管理层离任日期及姓名脱敏测试，重新复制该测试后相关回归194/194通过；生产/Probe实现未变。
- 首次测试编译发现Map泛型不匹配并修正；随后RED60项4个缺计数失败，实现后Probe60/60。联合回归首次因沙箱本地端口权限产生21个环境错误，隔离构建升级后全部通过。独立复审无实现缺陷，指出离任日期测试缺口已补。
- `mvn -o -f data-plane/pom.xml -Pacceptance verify` exit0，当前XML合计1093项后端/包，失败0/错误0/跳过0；前端468通过。后续精确回归194/194和Node78/78均exit0。普通环境白名单排除账户/DB，日志`/private/tmp/issue025-control/build.log`、`regression.log`、`node-pre-source.log`。
- sourceDiffSha256: `946b1df0fce3841b1396a4f74d3a1442c82cb0914ad5a0fe6cb8f7078064b15b`。
- productionJarSha256: `d46297ad93e9ef3ccd8f9bd4e96c2f3903f7d674cae3b6bd5eabab57514c570f`。
- acceptanceJarSha256: `0ec1d61344c1a72416aed64afb26c0d1bc04b61ed2542640c023910acd82757c`。
- manifestSha256: `386f46a99b6605e203129836d7a744b96b65304307f52991dd8bba6fd1870984`。
- requestExamplesSha256: `6d4c74a1a539b59ac20fb0cbd3ba1fba0954c40ef1209b652f7dcc2192ec932f`。

每轮wrapper核对以上源码/两包及固定清单身份；新测试变更已纳入来源指纹，不沿用复审前指纹。此时SOURCE尚未执行。

### issue025-source-20260913T162759Z 实际结果

- UTC `2026-09-13T16:29:23.661Z`～`2026-09-13T16:30:13.013Z`；exit 0、cleanup PASS；23case/23请求，状态{'PASS': 22, 'EVIDENCE_MISSING': 1}。安全原对象与预登记输入逐项相符，完整追加唯一索引，旧run未改。
- 来源指纹 `946b1df0fce3841b1396a4f74d3a1442c82cb0914ad5a0fe6cb8f7078064b15b`，生产包 `d46297ad93e9ef3ccd8f9bd4e96c2f3903f7d674cae3b6bd5eabab57514c570f`，验收包 `0ec1d61344c1a72416aed64afb26c0d1bc04b61ed2542640c023910acd82757c`；manifest `386f46a99b6605e203129836d7a744b96b65304307f52991dd8bba6fd1870984`，examples `6d4c74a1a539b59ac20fb0cbd3ba1fba0954c40ef1209b652f7dcc2192ec932f`。wrapper清理检查结果见本轮私有source-run.json；SOURCE的TASK/SQL字段为null。

| caseId后缀 | 状态 | 行数 | 安全日期/计数/摘要观察 |
| --- | --- | ---: | --- |
| `adj_factor-000001-whole` | PASS | 4 | actualDates=20251229,20251230,20251231,20260105; uniqueDateCount=4; min=20251229; max=20260105; candidateRowLimit=UNKNOWN; candidateLimitReached=false |
| `adj_factor-600000-whole` | PASS | 4 | actualDates=20251229,20251230,20251231,20260105; uniqueDateCount=4; min=20251229; max=20260105; candidateRowLimit=UNKNOWN; candidateLimitReached=false |
| `income-000001-whole` | PASS | 4 | actualDates=20240315,20240420,20240816,20241019; uniqueDateCount=4; min=20240315; max=20241019; candidateRowLimit=UNKNOWN; candidateLimitReached=false; dateComparisonValidRows=4; dateComparisonUnavailableRows=0; annDateDifferentFromEndDateRows=4; annInRangeEndOutsideRows=1 |
| `income-600000-whole` | PASS | 4 | actualDates=20240430,20240820,20241031; uniqueDateCount=3; min=20240430; max=20241031; candidateRowLimit=UNKNOWN; candidateLimitReached=false; dateComparisonValidRows=4; dateComparisonUnavailableRows=0; annDateDifferentFromEndDateRows=4; annInRangeEndOutsideRows=1 |
| `balancesheet-000001-whole` | PASS | 6 | actualDates=20240315,20240420,20240816,20241019; uniqueDateCount=4; min=20240315; max=20241019; candidateRowLimit=UNKNOWN; candidateLimitReached=false; dateComparisonValidRows=6; dateComparisonUnavailableRows=0; annDateDifferentFromEndDateRows=6; annInRangeEndOutsideRows=2 |
| `balancesheet-600000-whole` | PASS | 7 | actualDates=20240430,20240820,20241031; uniqueDateCount=3; min=20240430; max=20241031; candidateRowLimit=UNKNOWN; candidateLimitReached=false; dateComparisonValidRows=7; dateComparisonUnavailableRows=0; annDateDifferentFromEndDateRows=7; annInRangeEndOutsideRows=2 |
| `cashflow-000001-whole` | PASS | 6 | actualDates=20240315,20240420,20240816,20241019; uniqueDateCount=4; min=20240315; max=20241019; candidateRowLimit=UNKNOWN; candidateLimitReached=false; dateComparisonValidRows=6; dateComparisonUnavailableRows=0; annDateDifferentFromEndDateRows=6; annInRangeEndOutsideRows=2; cashflowActualAnnouncementDateComparisonValidRows=6; cashflowActualAnnouncementDateComparisonUnavailableRows=0; cashflowActualAnnouncementDifferentRows=0; cashflowActualAnnouncementAnnInRangeCompanionOutsideRows=0 |
| `cashflow-600000-whole` | PASS | 5 | actualDates=20240430,20240820,20241031; uniqueDateCount=3; min=20240430; max=20241031; candidateRowLimit=UNKNOWN; candidateLimitReached=false; dateComparisonValidRows=5; dateComparisonUnavailableRows=0; annDateDifferentFromEndDateRows=5; annInRangeEndOutsideRows=2; cashflowActualAnnouncementDateComparisonValidRows=5; cashflowActualAnnouncementDateComparisonUnavailableRows=0; cashflowActualAnnouncementDifferentRows=0; cashflowActualAnnouncementAnnInRangeCompanionOutsideRows=0 |
| `fina_audit-000001-whole` | PASS | 1 | actualDates=20240315; uniqueDateCount=1; min=20240315; max=20240315; candidateRowLimit=UNKNOWN; candidateLimitReached=false; dateComparisonValidRows=1; dateComparisonUnavailableRows=0; annDateDifferentFromEndDateRows=1; annInRangeEndOutsideRows=1 |
| `fina_audit-600000-whole` | PASS | 1 | actualDates=20240430; uniqueDateCount=1; min=20240430; max=20240430; candidateRowLimit=UNKNOWN; candidateLimitReached=false; dateComparisonValidRows=1; dateComparisonUnavailableRows=0; annDateDifferentFromEndDateRows=1; annInRangeEndOutsideRows=1 |
| `express-000001-whole` | EVIDENCE_MISSING | 0 | actualDates=; uniqueDateCount=0; min=none; max=none; candidateRowLimit=UNKNOWN; candidateLimitReached=false; dateComparisonValidRows=0; dateComparisonUnavailableRows=0; annDateDifferentFromEndDateRows=0; annInRangeEndOutsideRows=0 |
| `express-600000-whole` | PASS | 1 | actualDates=20180106; uniqueDateCount=1; min=20180106; max=20180106; candidateRowLimit=UNKNOWN; candidateLimitReached=false; dateComparisonValidRows=1; dateComparisonUnavailableRows=0; annDateDifferentFromEndDateRows=1; annInRangeEndOutsideRows=1 |
| `top10_holders-000001-whole` | PASS | 40 | actualDates=20170331,20170630,20170930,20171231; uniqueDateCount=4; min=20170331; max=20171231; candidateRowLimit=UNKNOWN; candidateLimitReached=false; dateComparisonValidRows=40; dateComparisonUnavailableRows=0; endInRangeAnnOutsideRows=10 |
| `top10_holders-600000-whole` | PASS | 60 | actualDates=20170331,20170630,20170831,20170904,20170930,20171231; uniqueDateCount=6; min=20170331; max=20171231; candidateRowLimit=UNKNOWN; candidateLimitReached=false; dateComparisonValidRows=60; dateComparisonUnavailableRows=0; endInRangeAnnOutsideRows=10 |
| `top10_floatholders-000001-whole` | PASS | 54 | actualDates=20170331,20170630,20170930,20171231; uniqueDateCount=4; min=20170331; max=20171231; candidateRowLimit=UNKNOWN; candidateLimitReached=false; dateComparisonValidRows=54; dateComparisonUnavailableRows=0; endInRangeAnnOutsideRows=10 |
| `top10_floatholders-600000-whole` | PASS | 46 | actualDates=20170331,20170630,20170930,20171231; uniqueDateCount=4; min=20170331; max=20171231; candidateRowLimit=UNKNOWN; candidateLimitReached=false; dateComparisonValidRows=46; dateComparisonUnavailableRows=0; endInRangeAnnOutsideRows=10 |
| `stk_managers-000001-whole` | PASS | 5 | actualDates=20180816,20190307; uniqueDateCount=2; min=20180816; max=20190307; candidateRowLimit=UNKNOWN; candidateLimitReached=false; managerBeginDateComparisonValidRows=5; managerBeginDateComparisonUnavailableRows=0; managerAnnDateDifferentFromBeginDateRows=5; managerAnnInRangeBeginOutsideRows=0; managerEndDateComparisonValidRows=2; managerEndDateComparisonUnavailableRows=3; managerAnnDateDifferentFromEndDateRows=2; managerAnnInRangeEndOutsideRows=2 |
| `stk_managers-600000-whole` | PASS | 8 | actualDates=20180428,20180830,20190326; uniqueDateCount=3; min=20180428; max=20190326; candidateRowLimit=UNKNOWN; candidateLimitReached=false; managerBeginDateComparisonValidRows=8; managerBeginDateComparisonUnavailableRows=0; managerAnnDateDifferentFromBeginDateRows=8; managerAnnInRangeBeginOutsideRows=0; managerEndDateComparisonValidRows=3; managerEndDateComparisonUnavailableRows=5; managerAnnDateDifferentFromEndDateRows=3; managerAnnInRangeEndOutsideRows=3 |
| `suspend_d-000001-whole` | PASS | 220 | actualDates=见唯一JSON; uniqueDateCount=220; min=20000622; max=20140716; candidateRowLimit=UNKNOWN; candidateLimitReached=false; suspensionRows=211; resumptionRows=9; suspensionTypeUnavailableRows=0; intradayTimingPresentRows=0 |
| `suspend_d-600000-whole` | PASS | 147 | actualDates=见唯一JSON; uniqueDateCount=147; min=20000508; max=20160311; candidateRowLimit=UNKNOWN; candidateLimitReached=false; suspensionRows=142; resumptionRows=5; suspensionTypeUnavailableRows=0; intradayTimingPresentRows=0 |
| `repurchase-all-whole` | PASS | 852 | actualDates=见唯一JSON; uniqueDateCount=26; min=20260801; max=20260831; candidateRowLimit=UNKNOWN; candidateLimitReached=false; validStockCodeRows=852; unavailableStockCodeRows=0; distinctStockCodeCount=585 |
| `suspend_d-000029-whole` | PASS | 5 | actualDates=20200309,20200310,20200311,20200312,20200313; uniqueDateCount=5; min=20200309; max=20200313; candidateRowLimit=UNKNOWN; candidateLimitReached=false; suspensionRows=5; resumptionRows=0; suspensionTypeUnavailableRows=0; intradayTimingPresentRows=0 |
| `suspend_d-600310-whole` | PASS | 5 | actualDates=20200309,20200310,20200311,20200312,20200313; uniqueDateCount=5; min=20200309; max=20200313; candidateRowLimit=UNKNOWN; candidateLimitReached=false; suspensionRows=5; resumptionRows=0; suspensionTypeUnavailableRows=0; intradayTimingPresentRows=0 |

### ISSUE-025 第二轮固定边界计划

- runId `issue025-boundaries-20260913T163148Z`，106case/106预期请求，仍至少2000ms、30分钟/5000次、失败停轮无重试。私有`/private/tmp/issue025-boundaries-20260913T163148Z/cases.json`，0600/0700，SHA-256 `df59942558712a49c2df5000d01d6a70bafed7a425621cd5acf6574a305128cd`。此时未执行。
- 仅依据首轮22个非空SOURCE实际日期制定；相同params去重，单点同时承担整段/上下端身份。两个基准停牌股票额外连续五日来自首轮实际日期集合。express 000001.SZ旧窗为空保留，另找公开事件依据后再登记，未混入本轮。

| caseId后缀 | API / dateAxis | 精确params | 依据与目的 |
| --- | --- | --- | --- |
| `adj_factor-000001-bounded` | adj_factor / TRADE_DATE | `{"ts_code":"000001.SZ","start_date":"20251229","end_date":"20260105"}` | 首轮实际合法日期收紧整段；单日同时承担上下端，不重复相同请求；来源 issue025-source-20260913T162759Z-adj_factor-000001-whole |
| `adj_factor-000001-lower` | adj_factor / TRADE_DATE | `{"ts_code":"000001.SZ","start_date":"20251229","end_date":"20251229"}` | 独立下端单日；来源 issue025-source-20260913T162759Z-adj_factor-000001-whole |
| `adj_factor-000001-upper` | adj_factor / TRADE_DATE | `{"ts_code":"000001.SZ","start_date":"20260105","end_date":"20260105"}` | 独立上端单日；来源 issue025-source-20260913T162759Z-adj_factor-000001-whole |
| `adj_factor-000001-before` | adj_factor / TRADE_DATE | `{"ts_code":"000001.SZ","start_date":"20251228","end_date":"20251228"}` | 端点外相邻自然日对照；可能有数据，不预设为空；来源 issue025-source-20260913T162759Z-adj_factor-000001-whole |
| `adj_factor-000001-after` | adj_factor / TRADE_DATE | `{"ts_code":"000001.SZ","start_date":"20260106","end_date":"20260106"}` | 端点外相邻自然日对照；可能有数据，不预设为空；来源 issue025-source-20260913T162759Z-adj_factor-000001-whole |
| `adj_factor-600000-bounded` | adj_factor / TRADE_DATE | `{"ts_code":"600000.SH","start_date":"20251229","end_date":"20260105"}` | 首轮实际合法日期收紧整段；单日同时承担上下端，不重复相同请求；来源 issue025-source-20260913T162759Z-adj_factor-600000-whole |
| `adj_factor-600000-lower` | adj_factor / TRADE_DATE | `{"ts_code":"600000.SH","start_date":"20251229","end_date":"20251229"}` | 独立下端单日；来源 issue025-source-20260913T162759Z-adj_factor-600000-whole |
| `adj_factor-600000-upper` | adj_factor / TRADE_DATE | `{"ts_code":"600000.SH","start_date":"20260105","end_date":"20260105"}` | 独立上端单日；来源 issue025-source-20260913T162759Z-adj_factor-600000-whole |
| `adj_factor-600000-before` | adj_factor / TRADE_DATE | `{"ts_code":"600000.SH","start_date":"20251228","end_date":"20251228"}` | 端点外相邻自然日对照；可能有数据，不预设为空；来源 issue025-source-20260913T162759Z-adj_factor-600000-whole |
| `adj_factor-600000-after` | adj_factor / TRADE_DATE | `{"ts_code":"600000.SH","start_date":"20260106","end_date":"20260106"}` | 端点外相邻自然日对照；可能有数据，不预设为空；来源 issue025-source-20260913T162759Z-adj_factor-600000-whole |
| `income-000001-bounded` | income / ANNOUNCEMENT_DATE | `{"ts_code":"000001.SZ","start_date":"20240315","end_date":"20241019"}` | 首轮实际合法日期收紧整段；单日同时承担上下端，不重复相同请求；来源 issue025-source-20260913T162759Z-income-000001-whole |
| `income-000001-lower` | income / ANNOUNCEMENT_DATE | `{"ts_code":"000001.SZ","start_date":"20240315","end_date":"20240315"}` | 独立下端单日；来源 issue025-source-20260913T162759Z-income-000001-whole |
| `income-000001-upper` | income / ANNOUNCEMENT_DATE | `{"ts_code":"000001.SZ","start_date":"20241019","end_date":"20241019"}` | 独立上端单日；来源 issue025-source-20260913T162759Z-income-000001-whole |
| `income-000001-before` | income / ANNOUNCEMENT_DATE | `{"ts_code":"000001.SZ","start_date":"20240314","end_date":"20240314"}` | 端点外相邻自然日对照；可能有数据，不预设为空；来源 issue025-source-20260913T162759Z-income-000001-whole |
| `income-000001-after` | income / ANNOUNCEMENT_DATE | `{"ts_code":"000001.SZ","start_date":"20241020","end_date":"20241020"}` | 端点外相邻自然日对照；可能有数据，不预设为空；来源 issue025-source-20260913T162759Z-income-000001-whole |
| `income-600000-bounded` | income / ANNOUNCEMENT_DATE | `{"ts_code":"600000.SH","start_date":"20240430","end_date":"20241031"}` | 首轮实际合法日期收紧整段；单日同时承担上下端，不重复相同请求；来源 issue025-source-20260913T162759Z-income-600000-whole |
| `income-600000-lower` | income / ANNOUNCEMENT_DATE | `{"ts_code":"600000.SH","start_date":"20240430","end_date":"20240430"}` | 独立下端单日；来源 issue025-source-20260913T162759Z-income-600000-whole |
| `income-600000-upper` | income / ANNOUNCEMENT_DATE | `{"ts_code":"600000.SH","start_date":"20241031","end_date":"20241031"}` | 独立上端单日；来源 issue025-source-20260913T162759Z-income-600000-whole |
| `income-600000-before` | income / ANNOUNCEMENT_DATE | `{"ts_code":"600000.SH","start_date":"20240429","end_date":"20240429"}` | 端点外相邻自然日对照；可能有数据，不预设为空；来源 issue025-source-20260913T162759Z-income-600000-whole |
| `income-600000-after` | income / ANNOUNCEMENT_DATE | `{"ts_code":"600000.SH","start_date":"20241101","end_date":"20241101"}` | 端点外相邻自然日对照；可能有数据，不预设为空；来源 issue025-source-20260913T162759Z-income-600000-whole |
| `balancesheet-000001-bounded` | balancesheet / ANNOUNCEMENT_DATE | `{"ts_code":"000001.SZ","start_date":"20240315","end_date":"20241019"}` | 首轮实际合法日期收紧整段；单日同时承担上下端，不重复相同请求；来源 issue025-source-20260913T162759Z-balancesheet-000001-whole |
| `balancesheet-000001-lower` | balancesheet / ANNOUNCEMENT_DATE | `{"ts_code":"000001.SZ","start_date":"20240315","end_date":"20240315"}` | 独立下端单日；来源 issue025-source-20260913T162759Z-balancesheet-000001-whole |
| `balancesheet-000001-upper` | balancesheet / ANNOUNCEMENT_DATE | `{"ts_code":"000001.SZ","start_date":"20241019","end_date":"20241019"}` | 独立上端单日；来源 issue025-source-20260913T162759Z-balancesheet-000001-whole |
| `balancesheet-000001-before` | balancesheet / ANNOUNCEMENT_DATE | `{"ts_code":"000001.SZ","start_date":"20240314","end_date":"20240314"}` | 端点外相邻自然日对照；可能有数据，不预设为空；来源 issue025-source-20260913T162759Z-balancesheet-000001-whole |
| `balancesheet-000001-after` | balancesheet / ANNOUNCEMENT_DATE | `{"ts_code":"000001.SZ","start_date":"20241020","end_date":"20241020"}` | 端点外相邻自然日对照；可能有数据，不预设为空；来源 issue025-source-20260913T162759Z-balancesheet-000001-whole |
| `balancesheet-600000-bounded` | balancesheet / ANNOUNCEMENT_DATE | `{"ts_code":"600000.SH","start_date":"20240430","end_date":"20241031"}` | 首轮实际合法日期收紧整段；单日同时承担上下端，不重复相同请求；来源 issue025-source-20260913T162759Z-balancesheet-600000-whole |
| `balancesheet-600000-lower` | balancesheet / ANNOUNCEMENT_DATE | `{"ts_code":"600000.SH","start_date":"20240430","end_date":"20240430"}` | 独立下端单日；来源 issue025-source-20260913T162759Z-balancesheet-600000-whole |
| `balancesheet-600000-upper` | balancesheet / ANNOUNCEMENT_DATE | `{"ts_code":"600000.SH","start_date":"20241031","end_date":"20241031"}` | 独立上端单日；来源 issue025-source-20260913T162759Z-balancesheet-600000-whole |
| `balancesheet-600000-before` | balancesheet / ANNOUNCEMENT_DATE | `{"ts_code":"600000.SH","start_date":"20240429","end_date":"20240429"}` | 端点外相邻自然日对照；可能有数据，不预设为空；来源 issue025-source-20260913T162759Z-balancesheet-600000-whole |
| `balancesheet-600000-after` | balancesheet / ANNOUNCEMENT_DATE | `{"ts_code":"600000.SH","start_date":"20241101","end_date":"20241101"}` | 端点外相邻自然日对照；可能有数据，不预设为空；来源 issue025-source-20260913T162759Z-balancesheet-600000-whole |
| `cashflow-000001-bounded` | cashflow / ANNOUNCEMENT_DATE | `{"ts_code":"000001.SZ","start_date":"20240315","end_date":"20241019"}` | 首轮实际合法日期收紧整段；单日同时承担上下端，不重复相同请求；来源 issue025-source-20260913T162759Z-cashflow-000001-whole |
| `cashflow-000001-lower` | cashflow / ANNOUNCEMENT_DATE | `{"ts_code":"000001.SZ","start_date":"20240315","end_date":"20240315"}` | 独立下端单日；来源 issue025-source-20260913T162759Z-cashflow-000001-whole |
| `cashflow-000001-upper` | cashflow / ANNOUNCEMENT_DATE | `{"ts_code":"000001.SZ","start_date":"20241019","end_date":"20241019"}` | 独立上端单日；来源 issue025-source-20260913T162759Z-cashflow-000001-whole |
| `cashflow-000001-before` | cashflow / ANNOUNCEMENT_DATE | `{"ts_code":"000001.SZ","start_date":"20240314","end_date":"20240314"}` | 端点外相邻自然日对照；可能有数据，不预设为空；来源 issue025-source-20260913T162759Z-cashflow-000001-whole |
| `cashflow-000001-after` | cashflow / ANNOUNCEMENT_DATE | `{"ts_code":"000001.SZ","start_date":"20241020","end_date":"20241020"}` | 端点外相邻自然日对照；可能有数据，不预设为空；来源 issue025-source-20260913T162759Z-cashflow-000001-whole |
| `cashflow-600000-bounded` | cashflow / ANNOUNCEMENT_DATE | `{"ts_code":"600000.SH","start_date":"20240430","end_date":"20241031"}` | 首轮实际合法日期收紧整段；单日同时承担上下端，不重复相同请求；来源 issue025-source-20260913T162759Z-cashflow-600000-whole |
| `cashflow-600000-lower` | cashflow / ANNOUNCEMENT_DATE | `{"ts_code":"600000.SH","start_date":"20240430","end_date":"20240430"}` | 独立下端单日；来源 issue025-source-20260913T162759Z-cashflow-600000-whole |
| `cashflow-600000-upper` | cashflow / ANNOUNCEMENT_DATE | `{"ts_code":"600000.SH","start_date":"20241031","end_date":"20241031"}` | 独立上端单日；来源 issue025-source-20260913T162759Z-cashflow-600000-whole |
| `cashflow-600000-before` | cashflow / ANNOUNCEMENT_DATE | `{"ts_code":"600000.SH","start_date":"20240429","end_date":"20240429"}` | 端点外相邻自然日对照；可能有数据，不预设为空；来源 issue025-source-20260913T162759Z-cashflow-600000-whole |
| `cashflow-600000-after` | cashflow / ANNOUNCEMENT_DATE | `{"ts_code":"600000.SH","start_date":"20241101","end_date":"20241101"}` | 端点外相邻自然日对照；可能有数据，不预设为空；来源 issue025-source-20260913T162759Z-cashflow-600000-whole |
| `fina_audit-000001-bounded` | fina_audit / ANNOUNCEMENT_DATE | `{"ts_code":"000001.SZ","start_date":"20240315","end_date":"20240315"}` | 首轮实际合法日期收紧整段；单日同时承担上下端，不重复相同请求；来源 issue025-source-20260913T162759Z-fina_audit-000001-whole |
| `fina_audit-000001-before` | fina_audit / ANNOUNCEMENT_DATE | `{"ts_code":"000001.SZ","start_date":"20240314","end_date":"20240314"}` | 端点外相邻自然日对照；可能有数据，不预设为空；来源 issue025-source-20260913T162759Z-fina_audit-000001-whole |
| `fina_audit-000001-after` | fina_audit / ANNOUNCEMENT_DATE | `{"ts_code":"000001.SZ","start_date":"20240316","end_date":"20240316"}` | 端点外相邻自然日对照；可能有数据，不预设为空；来源 issue025-source-20260913T162759Z-fina_audit-000001-whole |
| `fina_audit-600000-bounded` | fina_audit / ANNOUNCEMENT_DATE | `{"ts_code":"600000.SH","start_date":"20240430","end_date":"20240430"}` | 首轮实际合法日期收紧整段；单日同时承担上下端，不重复相同请求；来源 issue025-source-20260913T162759Z-fina_audit-600000-whole |
| `fina_audit-600000-before` | fina_audit / ANNOUNCEMENT_DATE | `{"ts_code":"600000.SH","start_date":"20240429","end_date":"20240429"}` | 端点外相邻自然日对照；可能有数据，不预设为空；来源 issue025-source-20260913T162759Z-fina_audit-600000-whole |
| `fina_audit-600000-after` | fina_audit / ANNOUNCEMENT_DATE | `{"ts_code":"600000.SH","start_date":"20240501","end_date":"20240501"}` | 端点外相邻自然日对照；可能有数据，不预设为空；来源 issue025-source-20260913T162759Z-fina_audit-600000-whole |
| `express-600000-bounded` | express / ANNOUNCEMENT_DATE | `{"ts_code":"600000.SH","start_date":"20180106","end_date":"20180106"}` | 首轮实际合法日期收紧整段；单日同时承担上下端，不重复相同请求；来源 issue025-source-20260913T162759Z-express-600000-whole |
| `express-600000-before` | express / ANNOUNCEMENT_DATE | `{"ts_code":"600000.SH","start_date":"20180105","end_date":"20180105"}` | 端点外相邻自然日对照；可能有数据，不预设为空；来源 issue025-source-20260913T162759Z-express-600000-whole |
| `express-600000-after` | express / ANNOUNCEMENT_DATE | `{"ts_code":"600000.SH","start_date":"20180107","end_date":"20180107"}` | 端点外相邻自然日对照；可能有数据，不预设为空；来源 issue025-source-20260913T162759Z-express-600000-whole |
| `top10_holders-000001-bounded` | top10_holders / REPORT_PERIOD | `{"ts_code":"000001.SZ","start_date":"20170331","end_date":"20171231"}` | 首轮实际合法日期收紧整段；单日同时承担上下端，不重复相同请求；来源 issue025-source-20260913T162759Z-top10_holders-000001-whole |
| `top10_holders-000001-lower` | top10_holders / REPORT_PERIOD | `{"ts_code":"000001.SZ","start_date":"20170331","end_date":"20170331"}` | 独立下端单日；来源 issue025-source-20260913T162759Z-top10_holders-000001-whole |
| `top10_holders-000001-upper` | top10_holders / REPORT_PERIOD | `{"ts_code":"000001.SZ","start_date":"20171231","end_date":"20171231"}` | 独立上端单日；来源 issue025-source-20260913T162759Z-top10_holders-000001-whole |
| `top10_holders-000001-before` | top10_holders / REPORT_PERIOD | `{"ts_code":"000001.SZ","start_date":"20170330","end_date":"20170330"}` | 端点外相邻自然日对照；可能有数据，不预设为空；来源 issue025-source-20260913T162759Z-top10_holders-000001-whole |
| `top10_holders-000001-after` | top10_holders / REPORT_PERIOD | `{"ts_code":"000001.SZ","start_date":"20180101","end_date":"20180101"}` | 端点外相邻自然日对照；可能有数据，不预设为空；来源 issue025-source-20260913T162759Z-top10_holders-000001-whole |
| `top10_holders-600000-bounded` | top10_holders / REPORT_PERIOD | `{"ts_code":"600000.SH","start_date":"20170331","end_date":"20171231"}` | 首轮实际合法日期收紧整段；单日同时承担上下端，不重复相同请求；来源 issue025-source-20260913T162759Z-top10_holders-600000-whole |
| `top10_holders-600000-lower` | top10_holders / REPORT_PERIOD | `{"ts_code":"600000.SH","start_date":"20170331","end_date":"20170331"}` | 独立下端单日；来源 issue025-source-20260913T162759Z-top10_holders-600000-whole |
| `top10_holders-600000-upper` | top10_holders / REPORT_PERIOD | `{"ts_code":"600000.SH","start_date":"20171231","end_date":"20171231"}` | 独立上端单日；来源 issue025-source-20260913T162759Z-top10_holders-600000-whole |
| `top10_holders-600000-before` | top10_holders / REPORT_PERIOD | `{"ts_code":"600000.SH","start_date":"20170330","end_date":"20170330"}` | 端点外相邻自然日对照；可能有数据，不预设为空；来源 issue025-source-20260913T162759Z-top10_holders-600000-whole |
| `top10_holders-600000-after` | top10_holders / REPORT_PERIOD | `{"ts_code":"600000.SH","start_date":"20180101","end_date":"20180101"}` | 端点外相邻自然日对照；可能有数据，不预设为空；来源 issue025-source-20260913T162759Z-top10_holders-600000-whole |
| `top10_floatholders-000001-bounded` | top10_floatholders / REPORT_PERIOD | `{"ts_code":"000001.SZ","start_date":"20170331","end_date":"20171231"}` | 首轮实际合法日期收紧整段；单日同时承担上下端，不重复相同请求；来源 issue025-source-20260913T162759Z-top10_floatholders-000001-whole |
| `top10_floatholders-000001-lower` | top10_floatholders / REPORT_PERIOD | `{"ts_code":"000001.SZ","start_date":"20170331","end_date":"20170331"}` | 独立下端单日；来源 issue025-source-20260913T162759Z-top10_floatholders-000001-whole |
| `top10_floatholders-000001-upper` | top10_floatholders / REPORT_PERIOD | `{"ts_code":"000001.SZ","start_date":"20171231","end_date":"20171231"}` | 独立上端单日；来源 issue025-source-20260913T162759Z-top10_floatholders-000001-whole |
| `top10_floatholders-000001-before` | top10_floatholders / REPORT_PERIOD | `{"ts_code":"000001.SZ","start_date":"20170330","end_date":"20170330"}` | 端点外相邻自然日对照；可能有数据，不预设为空；来源 issue025-source-20260913T162759Z-top10_floatholders-000001-whole |
| `top10_floatholders-000001-after` | top10_floatholders / REPORT_PERIOD | `{"ts_code":"000001.SZ","start_date":"20180101","end_date":"20180101"}` | 端点外相邻自然日对照；可能有数据，不预设为空；来源 issue025-source-20260913T162759Z-top10_floatholders-000001-whole |
| `top10_floatholders-600000-bounded` | top10_floatholders / REPORT_PERIOD | `{"ts_code":"600000.SH","start_date":"20170331","end_date":"20171231"}` | 首轮实际合法日期收紧整段；单日同时承担上下端，不重复相同请求；来源 issue025-source-20260913T162759Z-top10_floatholders-600000-whole |
| `top10_floatholders-600000-lower` | top10_floatholders / REPORT_PERIOD | `{"ts_code":"600000.SH","start_date":"20170331","end_date":"20170331"}` | 独立下端单日；来源 issue025-source-20260913T162759Z-top10_floatholders-600000-whole |
| `top10_floatholders-600000-upper` | top10_floatholders / REPORT_PERIOD | `{"ts_code":"600000.SH","start_date":"20171231","end_date":"20171231"}` | 独立上端单日；来源 issue025-source-20260913T162759Z-top10_floatholders-600000-whole |
| `top10_floatholders-600000-before` | top10_floatholders / REPORT_PERIOD | `{"ts_code":"600000.SH","start_date":"20170330","end_date":"20170330"}` | 端点外相邻自然日对照；可能有数据，不预设为空；来源 issue025-source-20260913T162759Z-top10_floatholders-600000-whole |
| `top10_floatholders-600000-after` | top10_floatholders / REPORT_PERIOD | `{"ts_code":"600000.SH","start_date":"20180101","end_date":"20180101"}` | 端点外相邻自然日对照；可能有数据，不预设为空；来源 issue025-source-20260913T162759Z-top10_floatholders-600000-whole |
| `stk_managers-000001-bounded` | stk_managers / ANNOUNCEMENT_DATE | `{"ts_code":"000001.SZ","start_date":"20180816","end_date":"20190307"}` | 首轮实际合法日期收紧整段；单日同时承担上下端，不重复相同请求；来源 issue025-source-20260913T162759Z-stk_managers-000001-whole |
| `stk_managers-000001-lower` | stk_managers / ANNOUNCEMENT_DATE | `{"ts_code":"000001.SZ","start_date":"20180816","end_date":"20180816"}` | 独立下端单日；来源 issue025-source-20260913T162759Z-stk_managers-000001-whole |
| `stk_managers-000001-upper` | stk_managers / ANNOUNCEMENT_DATE | `{"ts_code":"000001.SZ","start_date":"20190307","end_date":"20190307"}` | 独立上端单日；来源 issue025-source-20260913T162759Z-stk_managers-000001-whole |
| `stk_managers-000001-before` | stk_managers / ANNOUNCEMENT_DATE | `{"ts_code":"000001.SZ","start_date":"20180815","end_date":"20180815"}` | 端点外相邻自然日对照；可能有数据，不预设为空；来源 issue025-source-20260913T162759Z-stk_managers-000001-whole |
| `stk_managers-000001-after` | stk_managers / ANNOUNCEMENT_DATE | `{"ts_code":"000001.SZ","start_date":"20190308","end_date":"20190308"}` | 端点外相邻自然日对照；可能有数据，不预设为空；来源 issue025-source-20260913T162759Z-stk_managers-000001-whole |
| `stk_managers-600000-bounded` | stk_managers / ANNOUNCEMENT_DATE | `{"ts_code":"600000.SH","start_date":"20180428","end_date":"20190326"}` | 首轮实际合法日期收紧整段；单日同时承担上下端，不重复相同请求；来源 issue025-source-20260913T162759Z-stk_managers-600000-whole |
| `stk_managers-600000-lower` | stk_managers / ANNOUNCEMENT_DATE | `{"ts_code":"600000.SH","start_date":"20180428","end_date":"20180428"}` | 独立下端单日；来源 issue025-source-20260913T162759Z-stk_managers-600000-whole |
| `stk_managers-600000-upper` | stk_managers / ANNOUNCEMENT_DATE | `{"ts_code":"600000.SH","start_date":"20190326","end_date":"20190326"}` | 独立上端单日；来源 issue025-source-20260913T162759Z-stk_managers-600000-whole |
| `stk_managers-600000-before` | stk_managers / ANNOUNCEMENT_DATE | `{"ts_code":"600000.SH","start_date":"20180427","end_date":"20180427"}` | 端点外相邻自然日对照；可能有数据，不预设为空；来源 issue025-source-20260913T162759Z-stk_managers-600000-whole |
| `stk_managers-600000-after` | stk_managers / ANNOUNCEMENT_DATE | `{"ts_code":"600000.SH","start_date":"20190327","end_date":"20190327"}` | 端点外相邻自然日对照；可能有数据，不预设为空；来源 issue025-source-20260913T162759Z-stk_managers-600000-whole |
| `suspend_d-000001-bounded` | suspend_d / TRADE_DATE | `{"ts_code":"000001.SZ","start_date":"20000622","end_date":"20140716"}` | 首轮实际合法日期收紧整段；单日同时承担上下端，不重复相同请求；来源 issue025-source-20260913T162759Z-suspend_d-000001-whole |
| `suspend_d-000001-lower` | suspend_d / TRADE_DATE | `{"ts_code":"000001.SZ","start_date":"20000622","end_date":"20000622"}` | 独立下端单日；来源 issue025-source-20260913T162759Z-suspend_d-000001-whole |
| `suspend_d-000001-upper` | suspend_d / TRADE_DATE | `{"ts_code":"000001.SZ","start_date":"20140716","end_date":"20140716"}` | 独立上端单日；来源 issue025-source-20260913T162759Z-suspend_d-000001-whole |
| `suspend_d-000001-before` | suspend_d / TRADE_DATE | `{"ts_code":"000001.SZ","start_date":"20000621","end_date":"20000621"}` | 端点外相邻自然日对照；可能有数据，不预设为空；来源 issue025-source-20260913T162759Z-suspend_d-000001-whole |
| `suspend_d-000001-after` | suspend_d / TRADE_DATE | `{"ts_code":"000001.SZ","start_date":"20140717","end_date":"20140717"}` | 端点外相邻自然日对照；可能有数据，不预设为空；来源 issue025-source-20260913T162759Z-suspend_d-000001-whole |
| `suspend_d-600000-bounded` | suspend_d / TRADE_DATE | `{"ts_code":"600000.SH","start_date":"20000508","end_date":"20160311"}` | 首轮实际合法日期收紧整段；单日同时承担上下端，不重复相同请求；来源 issue025-source-20260913T162759Z-suspend_d-600000-whole |
| `suspend_d-600000-lower` | suspend_d / TRADE_DATE | `{"ts_code":"600000.SH","start_date":"20000508","end_date":"20000508"}` | 独立下端单日；来源 issue025-source-20260913T162759Z-suspend_d-600000-whole |
| `suspend_d-600000-upper` | suspend_d / TRADE_DATE | `{"ts_code":"600000.SH","start_date":"20160311","end_date":"20160311"}` | 独立上端单日；来源 issue025-source-20260913T162759Z-suspend_d-600000-whole |
| `suspend_d-600000-before` | suspend_d / TRADE_DATE | `{"ts_code":"600000.SH","start_date":"20000507","end_date":"20000507"}` | 端点外相邻自然日对照；可能有数据，不预设为空；来源 issue025-source-20260913T162759Z-suspend_d-600000-whole |
| `suspend_d-600000-after` | suspend_d / TRADE_DATE | `{"ts_code":"600000.SH","start_date":"20160312","end_date":"20160312"}` | 端点外相邻自然日对照；可能有数据，不预设为空；来源 issue025-source-20260913T162759Z-suspend_d-600000-whole |
| `repurchase-all-bounded` | repurchase / ANNOUNCEMENT_DATE | `{"start_date":"20260801","end_date":"20260831"}` | 首轮实际合法日期收紧整段；单日同时承担上下端，不重复相同请求；来源 issue025-source-20260913T162759Z-repurchase-all-whole |
| `repurchase-all-lower` | repurchase / ANNOUNCEMENT_DATE | `{"start_date":"20260801","end_date":"20260801"}` | 独立下端单日；来源 issue025-source-20260913T162759Z-repurchase-all-whole |
| `repurchase-all-upper` | repurchase / ANNOUNCEMENT_DATE | `{"start_date":"20260831","end_date":"20260831"}` | 独立上端单日；来源 issue025-source-20260913T162759Z-repurchase-all-whole |
| `repurchase-all-before` | repurchase / ANNOUNCEMENT_DATE | `{"start_date":"20260731","end_date":"20260731"}` | 端点外相邻自然日对照；可能有数据，不预设为空；来源 issue025-source-20260913T162759Z-repurchase-all-whole |
| `repurchase-all-after` | repurchase / ANNOUNCEMENT_DATE | `{"start_date":"20260901","end_date":"20260901"}` | 端点外相邻自然日对照；可能有数据，不预设为空；来源 issue025-source-20260913T162759Z-repurchase-all-whole |
| `suspend_d-000029-bounded` | suspend_d / TRADE_DATE | `{"ts_code":"000029.SZ","start_date":"20200309","end_date":"20200313"}` | 首轮实际合法日期收紧整段；单日同时承担上下端，不重复相同请求；来源 issue025-source-20260913T162759Z-suspend_d-000029-whole |
| `suspend_d-000029-lower` | suspend_d / TRADE_DATE | `{"ts_code":"000029.SZ","start_date":"20200309","end_date":"20200309"}` | 独立下端单日；来源 issue025-source-20260913T162759Z-suspend_d-000029-whole |
| `suspend_d-000029-upper` | suspend_d / TRADE_DATE | `{"ts_code":"000029.SZ","start_date":"20200313","end_date":"20200313"}` | 独立上端单日；来源 issue025-source-20260913T162759Z-suspend_d-000029-whole |
| `suspend_d-000029-before` | suspend_d / TRADE_DATE | `{"ts_code":"000029.SZ","start_date":"20200308","end_date":"20200308"}` | 端点外相邻自然日对照；可能有数据，不预设为空；来源 issue025-source-20260913T162759Z-suspend_d-000029-whole |
| `suspend_d-000029-after` | suspend_d / TRADE_DATE | `{"ts_code":"000029.SZ","start_date":"20200314","end_date":"20200314"}` | 端点外相邻自然日对照；可能有数据，不预设为空；来源 issue025-source-20260913T162759Z-suspend_d-000029-whole |
| `suspend_d-600310-bounded` | suspend_d / TRADE_DATE | `{"ts_code":"600310.SH","start_date":"20200309","end_date":"20200313"}` | 首轮实际合法日期收紧整段；单日同时承担上下端，不重复相同请求；来源 issue025-source-20260913T162759Z-suspend_d-600310-whole |
| `suspend_d-600310-lower` | suspend_d / TRADE_DATE | `{"ts_code":"600310.SH","start_date":"20200309","end_date":"20200309"}` | 独立下端单日；来源 issue025-source-20260913T162759Z-suspend_d-600310-whole |
| `suspend_d-600310-upper` | suspend_d / TRADE_DATE | `{"ts_code":"600310.SH","start_date":"20200313","end_date":"20200313"}` | 独立上端单日；来源 issue025-source-20260913T162759Z-suspend_d-600310-whole |
| `suspend_d-600310-before` | suspend_d / TRADE_DATE | `{"ts_code":"600310.SH","start_date":"20200308","end_date":"20200308"}` | 端点外相邻自然日对照；可能有数据，不预设为空；来源 issue025-source-20260913T162759Z-suspend_d-600310-whole |
| `suspend_d-600310-after` | suspend_d / TRADE_DATE | `{"ts_code":"600310.SH","start_date":"20200314","end_date":"20200314"}` | 端点外相邻自然日对照；可能有数据，不预设为空；来源 issue025-source-20260913T162759Z-suspend_d-600310-whole |
| `suspend_d-000001-consecutive` | suspend_d / TRADE_DATE | `{"ts_code":"000001.SZ","start_date":"20100705","end_date":"20100709"}` | 首轮已实际观察连续五个自然日停复牌记录；不借用通用交易日历或声称全历史完整；来源 issue025-source-20260913T162759Z-suspend_d-000001-whole |
| `suspend_d-600000-consecutive` | suspend_d / TRADE_DATE | `{"ts_code":"600000.SH","start_date":"20150608","end_date":"20150612"}` | 首轮已实际观察连续五个自然日停复牌记录；不借用通用交易日历或声称全历史完整；来源 issue025-source-20260913T162759Z-suspend_d-600000-whole |

### ISSUE-025 第三轮固定快报计划

- runId `issue025-express-20260913T163418Z`，6case/6预期请求；私有`/private/tmp/issue025-express-20260913T163418Z/cases.json`，0600/0700，SHA-256 `942e3a6ef1ee1c8f18754b9ae7d5696f696d55a423f9e927b719931dcaed6500`。仅预登记，待第二轮退出和清理确认后串行执行；至少2000ms、30分钟/5000次上限，失败停轮无重试。
- 全部仅express/000001.SZ/ANNOUNCEMENT_DATE，依据[已取得的公开快报公告](ISSUE-018-T14-official-evidence.md#issue-025-平安银行业绩快报补充选样)，原2018空样本保持；新样本结果未知。

| caseId后缀 | 精确params | 目的 |
| --- | --- | --- |
| `whole` | `{"ts_code":"000001.SZ","start_date":"20220113","end_date":"20220115"}` | 公告整段/单日/两端及邻界对照，输出ann_date与end_date独立比较 |
| `day` | `{"ts_code":"000001.SZ","start_date":"20220114","end_date":"20220114"}` | 公告整段/单日/两端及邻界对照，输出ann_date与end_date独立比较 |
| `lower-window` | `{"ts_code":"000001.SZ","start_date":"20220114","end_date":"20220115"}` | 公告整段/单日/两端及邻界对照，输出ann_date与end_date独立比较 |
| `upper-window` | `{"ts_code":"000001.SZ","start_date":"20220113","end_date":"20220114"}` | 公告整段/单日/两端及邻界对照，输出ann_date与end_date独立比较 |
| `before` | `{"ts_code":"000001.SZ","start_date":"20220113","end_date":"20220113"}` | 公告整段/单日/两端及邻界对照，输出ann_date与end_date独立比较 |
| `after` | `{"ts_code":"000001.SZ","start_date":"20220115","end_date":"20220115"}` | 公告整段/单日/两端及邻界对照，输出ann_date与end_date独立比较 |

### issue025-boundaries-20260913T163148Z 实际结果

- UTC `2026-09-13T16:32:02.295Z`～`2026-09-13T16:35:40.834Z`；exit 0、cleanup PASS；106case/106请求，状态{'PASS': 66, 'EVIDENCE_MISSING': 40}。安全原对象与预登记输入逐项相符，完整追加唯一索引，旧run未改。
- 来源指纹 `946b1df0fce3841b1396a4f74d3a1442c82cb0914ad5a0fe6cb8f7078064b15b`，生产包 `d46297ad93e9ef3ccd8f9bd4e96c2f3903f7d674cae3b6bd5eabab57514c570f`，验收包 `0ec1d61344c1a72416aed64afb26c0d1bc04b61ed2542640c023910acd82757c`；manifest `386f46a99b6605e203129836d7a744b96b65304307f52991dd8bba6fd1870984`，examples `6d4c74a1a539b59ac20fb0cbd3ba1fba0954c40ef1209b652f7dcc2192ec932f`。wrapper清理检查结果见本轮私有source-run.json；SOURCE的TASK/SQL字段为null。

| caseId后缀 | 状态 | 行数 | 安全日期/计数/摘要观察 |
| --- | --- | ---: | --- |
| `adj_factor-000001-bounded` | PASS | 4 | actualDates=20251229,20251230,20251231,20260105; uniqueDateCount=4; min=20251229; max=20260105; candidateRowLimit=UNKNOWN; candidateLimitReached=false |
| `adj_factor-000001-lower` | PASS | 1 | actualDates=20251229; uniqueDateCount=1; min=20251229; max=20251229; candidateRowLimit=UNKNOWN; candidateLimitReached=false |
| `adj_factor-000001-upper` | PASS | 1 | actualDates=20260105; uniqueDateCount=1; min=20260105; max=20260105; candidateRowLimit=UNKNOWN; candidateLimitReached=false |
| `adj_factor-000001-before` | EVIDENCE_MISSING | 0 | actualDates=; uniqueDateCount=0; min=none; max=none; candidateRowLimit=UNKNOWN; candidateLimitReached=false |
| `adj_factor-000001-after` | PASS | 1 | actualDates=20260106; uniqueDateCount=1; min=20260106; max=20260106; candidateRowLimit=UNKNOWN; candidateLimitReached=false |
| `adj_factor-600000-bounded` | PASS | 4 | actualDates=20251229,20251230,20251231,20260105; uniqueDateCount=4; min=20251229; max=20260105; candidateRowLimit=UNKNOWN; candidateLimitReached=false |
| `adj_factor-600000-lower` | PASS | 1 | actualDates=20251229; uniqueDateCount=1; min=20251229; max=20251229; candidateRowLimit=UNKNOWN; candidateLimitReached=false |
| `adj_factor-600000-upper` | PASS | 1 | actualDates=20260105; uniqueDateCount=1; min=20260105; max=20260105; candidateRowLimit=UNKNOWN; candidateLimitReached=false |
| `adj_factor-600000-before` | EVIDENCE_MISSING | 0 | actualDates=; uniqueDateCount=0; min=none; max=none; candidateRowLimit=UNKNOWN; candidateLimitReached=false |
| `adj_factor-600000-after` | PASS | 1 | actualDates=20260106; uniqueDateCount=1; min=20260106; max=20260106; candidateRowLimit=UNKNOWN; candidateLimitReached=false |
| `income-000001-bounded` | PASS | 4 | actualDates=20240315,20240420,20240816,20241019; uniqueDateCount=4; min=20240315; max=20241019; candidateRowLimit=UNKNOWN; candidateLimitReached=false; dateComparisonValidRows=4; dateComparisonUnavailableRows=0; annDateDifferentFromEndDateRows=4; annInRangeEndOutsideRows=1 |
| `income-000001-lower` | PASS | 1 | actualDates=20240315; uniqueDateCount=1; min=20240315; max=20240315; candidateRowLimit=UNKNOWN; candidateLimitReached=false; dateComparisonValidRows=1; dateComparisonUnavailableRows=0; annDateDifferentFromEndDateRows=1; annInRangeEndOutsideRows=1 |
| `income-000001-upper` | PASS | 1 | actualDates=20241019; uniqueDateCount=1; min=20241019; max=20241019; candidateRowLimit=UNKNOWN; candidateLimitReached=false; dateComparisonValidRows=1; dateComparisonUnavailableRows=0; annDateDifferentFromEndDateRows=1; annInRangeEndOutsideRows=1 |
| `income-000001-before` | EVIDENCE_MISSING | 0 | actualDates=; uniqueDateCount=0; min=none; max=none; candidateRowLimit=UNKNOWN; candidateLimitReached=false; dateComparisonValidRows=0; dateComparisonUnavailableRows=0; annDateDifferentFromEndDateRows=0; annInRangeEndOutsideRows=0 |
| `income-000001-after` | EVIDENCE_MISSING | 0 | actualDates=; uniqueDateCount=0; min=none; max=none; candidateRowLimit=UNKNOWN; candidateLimitReached=false; dateComparisonValidRows=0; dateComparisonUnavailableRows=0; annDateDifferentFromEndDateRows=0; annInRangeEndOutsideRows=0 |
| `income-600000-bounded` | PASS | 4 | actualDates=20240430,20240820,20241031; uniqueDateCount=3; min=20240430; max=20241031; candidateRowLimit=UNKNOWN; candidateLimitReached=false; dateComparisonValidRows=4; dateComparisonUnavailableRows=0; annDateDifferentFromEndDateRows=4; annInRangeEndOutsideRows=2 |
| `income-600000-lower` | PASS | 2 | actualDates=20240430; uniqueDateCount=1; min=20240430; max=20240430; candidateRowLimit=UNKNOWN; candidateLimitReached=false; dateComparisonValidRows=2; dateComparisonUnavailableRows=0; annDateDifferentFromEndDateRows=2; annInRangeEndOutsideRows=2 |
| `income-600000-upper` | PASS | 1 | actualDates=20241031; uniqueDateCount=1; min=20241031; max=20241031; candidateRowLimit=UNKNOWN; candidateLimitReached=false; dateComparisonValidRows=1; dateComparisonUnavailableRows=0; annDateDifferentFromEndDateRows=1; annInRangeEndOutsideRows=1 |
| `income-600000-before` | EVIDENCE_MISSING | 0 | actualDates=; uniqueDateCount=0; min=none; max=none; candidateRowLimit=UNKNOWN; candidateLimitReached=false; dateComparisonValidRows=0; dateComparisonUnavailableRows=0; annDateDifferentFromEndDateRows=0; annInRangeEndOutsideRows=0 |
| `income-600000-after` | EVIDENCE_MISSING | 0 | actualDates=; uniqueDateCount=0; min=none; max=none; candidateRowLimit=UNKNOWN; candidateLimitReached=false; dateComparisonValidRows=0; dateComparisonUnavailableRows=0; annDateDifferentFromEndDateRows=0; annInRangeEndOutsideRows=0 |
| `balancesheet-000001-bounded` | PASS | 6 | actualDates=20240315,20240420,20240816,20241019; uniqueDateCount=4; min=20240315; max=20241019; candidateRowLimit=UNKNOWN; candidateLimitReached=false; dateComparisonValidRows=6; dateComparisonUnavailableRows=0; annDateDifferentFromEndDateRows=6; annInRangeEndOutsideRows=2 |
| `balancesheet-000001-lower` | PASS | 2 | actualDates=20240315; uniqueDateCount=1; min=20240315; max=20240315; candidateRowLimit=UNKNOWN; candidateLimitReached=false; dateComparisonValidRows=2; dateComparisonUnavailableRows=0; annDateDifferentFromEndDateRows=2; annInRangeEndOutsideRows=2 |
| `balancesheet-000001-upper` | PASS | 1 | actualDates=20241019; uniqueDateCount=1; min=20241019; max=20241019; candidateRowLimit=UNKNOWN; candidateLimitReached=false; dateComparisonValidRows=1; dateComparisonUnavailableRows=0; annDateDifferentFromEndDateRows=1; annInRangeEndOutsideRows=1 |
| `balancesheet-000001-before` | EVIDENCE_MISSING | 0 | actualDates=; uniqueDateCount=0; min=none; max=none; candidateRowLimit=UNKNOWN; candidateLimitReached=false; dateComparisonValidRows=0; dateComparisonUnavailableRows=0; annDateDifferentFromEndDateRows=0; annInRangeEndOutsideRows=0 |
| `balancesheet-000001-after` | EVIDENCE_MISSING | 0 | actualDates=; uniqueDateCount=0; min=none; max=none; candidateRowLimit=UNKNOWN; candidateLimitReached=false; dateComparisonValidRows=0; dateComparisonUnavailableRows=0; annDateDifferentFromEndDateRows=0; annInRangeEndOutsideRows=0 |
| `balancesheet-600000-bounded` | PASS | 7 | actualDates=20240430,20240820,20241031; uniqueDateCount=3; min=20240430; max=20241031; candidateRowLimit=UNKNOWN; candidateLimitReached=false; dateComparisonValidRows=7; dateComparisonUnavailableRows=0; annDateDifferentFromEndDateRows=7; annInRangeEndOutsideRows=4 |
| `balancesheet-600000-lower` | PASS | 4 | actualDates=20240430; uniqueDateCount=1; min=20240430; max=20240430; candidateRowLimit=UNKNOWN; candidateLimitReached=false; dateComparisonValidRows=4; dateComparisonUnavailableRows=0; annDateDifferentFromEndDateRows=4; annInRangeEndOutsideRows=4 |
| `balancesheet-600000-upper` | PASS | 2 | actualDates=20241031; uniqueDateCount=1; min=20241031; max=20241031; candidateRowLimit=UNKNOWN; candidateLimitReached=false; dateComparisonValidRows=2; dateComparisonUnavailableRows=0; annDateDifferentFromEndDateRows=2; annInRangeEndOutsideRows=2 |
| `balancesheet-600000-before` | EVIDENCE_MISSING | 0 | actualDates=; uniqueDateCount=0; min=none; max=none; candidateRowLimit=UNKNOWN; candidateLimitReached=false; dateComparisonValidRows=0; dateComparisonUnavailableRows=0; annDateDifferentFromEndDateRows=0; annInRangeEndOutsideRows=0 |
| `balancesheet-600000-after` | EVIDENCE_MISSING | 0 | actualDates=; uniqueDateCount=0; min=none; max=none; candidateRowLimit=UNKNOWN; candidateLimitReached=false; dateComparisonValidRows=0; dateComparisonUnavailableRows=0; annDateDifferentFromEndDateRows=0; annInRangeEndOutsideRows=0 |
| `cashflow-000001-bounded` | PASS | 6 | actualDates=20240315,20240420,20240816,20241019; uniqueDateCount=4; min=20240315; max=20241019; candidateRowLimit=UNKNOWN; candidateLimitReached=false; dateComparisonValidRows=6; dateComparisonUnavailableRows=0; annDateDifferentFromEndDateRows=6; annInRangeEndOutsideRows=2; cashflowActualAnnouncementDateComparisonValidRows=6; cashflowActualAnnouncementDateComparisonUnavailableRows=0; cashflowActualAnnouncementDifferentRows=0; cashflowActualAnnouncementAnnInRangeCompanionOutsideRows=0 |
| `cashflow-000001-lower` | PASS | 2 | actualDates=20240315; uniqueDateCount=1; min=20240315; max=20240315; candidateRowLimit=UNKNOWN; candidateLimitReached=false; dateComparisonValidRows=2; dateComparisonUnavailableRows=0; annDateDifferentFromEndDateRows=2; annInRangeEndOutsideRows=2; cashflowActualAnnouncementDateComparisonValidRows=2; cashflowActualAnnouncementDateComparisonUnavailableRows=0; cashflowActualAnnouncementDifferentRows=0; cashflowActualAnnouncementAnnInRangeCompanionOutsideRows=0 |
| `cashflow-000001-upper` | PASS | 1 | actualDates=20241019; uniqueDateCount=1; min=20241019; max=20241019; candidateRowLimit=UNKNOWN; candidateLimitReached=false; dateComparisonValidRows=1; dateComparisonUnavailableRows=0; annDateDifferentFromEndDateRows=1; annInRangeEndOutsideRows=1; cashflowActualAnnouncementDateComparisonValidRows=1; cashflowActualAnnouncementDateComparisonUnavailableRows=0; cashflowActualAnnouncementDifferentRows=0; cashflowActualAnnouncementAnnInRangeCompanionOutsideRows=0 |
| `cashflow-000001-before` | EVIDENCE_MISSING | 0 | actualDates=; uniqueDateCount=0; min=none; max=none; candidateRowLimit=UNKNOWN; candidateLimitReached=false; dateComparisonValidRows=0; dateComparisonUnavailableRows=0; annDateDifferentFromEndDateRows=0; annInRangeEndOutsideRows=0; cashflowActualAnnouncementDateComparisonValidRows=0; cashflowActualAnnouncementDateComparisonUnavailableRows=0; cashflowActualAnnouncementDifferentRows=0; cashflowActualAnnouncementAnnInRangeCompanionOutsideRows=0 |
| `cashflow-000001-after` | EVIDENCE_MISSING | 0 | actualDates=; uniqueDateCount=0; min=none; max=none; candidateRowLimit=UNKNOWN; candidateLimitReached=false; dateComparisonValidRows=0; dateComparisonUnavailableRows=0; annDateDifferentFromEndDateRows=0; annInRangeEndOutsideRows=0; cashflowActualAnnouncementDateComparisonValidRows=0; cashflowActualAnnouncementDateComparisonUnavailableRows=0; cashflowActualAnnouncementDifferentRows=0; cashflowActualAnnouncementAnnInRangeCompanionOutsideRows=0 |
| `cashflow-600000-bounded` | PASS | 5 | actualDates=20240430,20240820,20241031; uniqueDateCount=3; min=20240430; max=20241031; candidateRowLimit=UNKNOWN; candidateLimitReached=false; dateComparisonValidRows=5; dateComparisonUnavailableRows=0; annDateDifferentFromEndDateRows=5; annInRangeEndOutsideRows=3; cashflowActualAnnouncementDateComparisonValidRows=5; cashflowActualAnnouncementDateComparisonUnavailableRows=0; cashflowActualAnnouncementDifferentRows=0; cashflowActualAnnouncementAnnInRangeCompanionOutsideRows=0 |
| `cashflow-600000-lower` | PASS | 3 | actualDates=20240430; uniqueDateCount=1; min=20240430; max=20240430; candidateRowLimit=UNKNOWN; candidateLimitReached=false; dateComparisonValidRows=3; dateComparisonUnavailableRows=0; annDateDifferentFromEndDateRows=3; annInRangeEndOutsideRows=3; cashflowActualAnnouncementDateComparisonValidRows=3; cashflowActualAnnouncementDateComparisonUnavailableRows=0; cashflowActualAnnouncementDifferentRows=0; cashflowActualAnnouncementAnnInRangeCompanionOutsideRows=0 |
| `cashflow-600000-upper` | PASS | 1 | actualDates=20241031; uniqueDateCount=1; min=20241031; max=20241031; candidateRowLimit=UNKNOWN; candidateLimitReached=false; dateComparisonValidRows=1; dateComparisonUnavailableRows=0; annDateDifferentFromEndDateRows=1; annInRangeEndOutsideRows=1; cashflowActualAnnouncementDateComparisonValidRows=1; cashflowActualAnnouncementDateComparisonUnavailableRows=0; cashflowActualAnnouncementDifferentRows=0; cashflowActualAnnouncementAnnInRangeCompanionOutsideRows=0 |
| `cashflow-600000-before` | EVIDENCE_MISSING | 0 | actualDates=; uniqueDateCount=0; min=none; max=none; candidateRowLimit=UNKNOWN; candidateLimitReached=false; dateComparisonValidRows=0; dateComparisonUnavailableRows=0; annDateDifferentFromEndDateRows=0; annInRangeEndOutsideRows=0; cashflowActualAnnouncementDateComparisonValidRows=0; cashflowActualAnnouncementDateComparisonUnavailableRows=0; cashflowActualAnnouncementDifferentRows=0; cashflowActualAnnouncementAnnInRangeCompanionOutsideRows=0 |
| `cashflow-600000-after` | EVIDENCE_MISSING | 0 | actualDates=; uniqueDateCount=0; min=none; max=none; candidateRowLimit=UNKNOWN; candidateLimitReached=false; dateComparisonValidRows=0; dateComparisonUnavailableRows=0; annDateDifferentFromEndDateRows=0; annInRangeEndOutsideRows=0; cashflowActualAnnouncementDateComparisonValidRows=0; cashflowActualAnnouncementDateComparisonUnavailableRows=0; cashflowActualAnnouncementDifferentRows=0; cashflowActualAnnouncementAnnInRangeCompanionOutsideRows=0 |
| `fina_audit-000001-bounded` | PASS | 1 | actualDates=20240315; uniqueDateCount=1; min=20240315; max=20240315; candidateRowLimit=UNKNOWN; candidateLimitReached=false; dateComparisonValidRows=1; dateComparisonUnavailableRows=0; annDateDifferentFromEndDateRows=1; annInRangeEndOutsideRows=1 |
| `fina_audit-000001-before` | EVIDENCE_MISSING | 0 | actualDates=; uniqueDateCount=0; min=none; max=none; candidateRowLimit=UNKNOWN; candidateLimitReached=false; dateComparisonValidRows=0; dateComparisonUnavailableRows=0; annDateDifferentFromEndDateRows=0; annInRangeEndOutsideRows=0 |
| `fina_audit-000001-after` | EVIDENCE_MISSING | 0 | actualDates=; uniqueDateCount=0; min=none; max=none; candidateRowLimit=UNKNOWN; candidateLimitReached=false; dateComparisonValidRows=0; dateComparisonUnavailableRows=0; annDateDifferentFromEndDateRows=0; annInRangeEndOutsideRows=0 |
| `fina_audit-600000-bounded` | PASS | 1 | actualDates=20240430; uniqueDateCount=1; min=20240430; max=20240430; candidateRowLimit=UNKNOWN; candidateLimitReached=false; dateComparisonValidRows=1; dateComparisonUnavailableRows=0; annDateDifferentFromEndDateRows=1; annInRangeEndOutsideRows=1 |
| `fina_audit-600000-before` | EVIDENCE_MISSING | 0 | actualDates=; uniqueDateCount=0; min=none; max=none; candidateRowLimit=UNKNOWN; candidateLimitReached=false; dateComparisonValidRows=0; dateComparisonUnavailableRows=0; annDateDifferentFromEndDateRows=0; annInRangeEndOutsideRows=0 |
| `fina_audit-600000-after` | EVIDENCE_MISSING | 0 | actualDates=; uniqueDateCount=0; min=none; max=none; candidateRowLimit=UNKNOWN; candidateLimitReached=false; dateComparisonValidRows=0; dateComparisonUnavailableRows=0; annDateDifferentFromEndDateRows=0; annInRangeEndOutsideRows=0 |
| `express-600000-bounded` | PASS | 1 | actualDates=20180106; uniqueDateCount=1; min=20180106; max=20180106; candidateRowLimit=UNKNOWN; candidateLimitReached=false; dateComparisonValidRows=1; dateComparisonUnavailableRows=0; annDateDifferentFromEndDateRows=1; annInRangeEndOutsideRows=1 |
| `express-600000-before` | EVIDENCE_MISSING | 0 | actualDates=; uniqueDateCount=0; min=none; max=none; candidateRowLimit=UNKNOWN; candidateLimitReached=false; dateComparisonValidRows=0; dateComparisonUnavailableRows=0; annDateDifferentFromEndDateRows=0; annInRangeEndOutsideRows=0 |
| `express-600000-after` | EVIDENCE_MISSING | 0 | actualDates=; uniqueDateCount=0; min=none; max=none; candidateRowLimit=UNKNOWN; candidateLimitReached=false; dateComparisonValidRows=0; dateComparisonUnavailableRows=0; annDateDifferentFromEndDateRows=0; annInRangeEndOutsideRows=0 |
| `top10_holders-000001-bounded` | PASS | 40 | actualDates=20170331,20170630,20170930,20171231; uniqueDateCount=4; min=20170331; max=20171231; candidateRowLimit=UNKNOWN; candidateLimitReached=false; dateComparisonValidRows=40; dateComparisonUnavailableRows=0; endInRangeAnnOutsideRows=10 |
| `top10_holders-000001-lower` | PASS | 10 | actualDates=20170331; uniqueDateCount=1; min=20170331; max=20170331; candidateRowLimit=UNKNOWN; candidateLimitReached=false; dateComparisonValidRows=10; dateComparisonUnavailableRows=0; endInRangeAnnOutsideRows=10 |
| `top10_holders-000001-upper` | PASS | 10 | actualDates=20171231; uniqueDateCount=1; min=20171231; max=20171231; candidateRowLimit=UNKNOWN; candidateLimitReached=false; dateComparisonValidRows=10; dateComparisonUnavailableRows=0; endInRangeAnnOutsideRows=10 |
| `top10_holders-000001-before` | EVIDENCE_MISSING | 0 | actualDates=; uniqueDateCount=0; min=none; max=none; candidateRowLimit=UNKNOWN; candidateLimitReached=false; dateComparisonValidRows=0; dateComparisonUnavailableRows=0; endInRangeAnnOutsideRows=0 |
| `top10_holders-000001-after` | EVIDENCE_MISSING | 0 | actualDates=; uniqueDateCount=0; min=none; max=none; candidateRowLimit=UNKNOWN; candidateLimitReached=false; dateComparisonValidRows=0; dateComparisonUnavailableRows=0; endInRangeAnnOutsideRows=0 |
| `top10_holders-600000-bounded` | PASS | 60 | actualDates=20170331,20170630,20170831,20170904,20170930,20171231; uniqueDateCount=6; min=20170331; max=20171231; candidateRowLimit=UNKNOWN; candidateLimitReached=false; dateComparisonValidRows=60; dateComparisonUnavailableRows=0; endInRangeAnnOutsideRows=10 |
| `top10_holders-600000-lower` | PASS | 10 | actualDates=20170331; uniqueDateCount=1; min=20170331; max=20170331; candidateRowLimit=UNKNOWN; candidateLimitReached=false; dateComparisonValidRows=10; dateComparisonUnavailableRows=0; endInRangeAnnOutsideRows=10 |
| `top10_holders-600000-upper` | PASS | 10 | actualDates=20171231; uniqueDateCount=1; min=20171231; max=20171231; candidateRowLimit=UNKNOWN; candidateLimitReached=false; dateComparisonValidRows=10; dateComparisonUnavailableRows=0; endInRangeAnnOutsideRows=10 |
| `top10_holders-600000-before` | EVIDENCE_MISSING | 0 | actualDates=; uniqueDateCount=0; min=none; max=none; candidateRowLimit=UNKNOWN; candidateLimitReached=false; dateComparisonValidRows=0; dateComparisonUnavailableRows=0; endInRangeAnnOutsideRows=0 |
| `top10_holders-600000-after` | EVIDENCE_MISSING | 0 | actualDates=; uniqueDateCount=0; min=none; max=none; candidateRowLimit=UNKNOWN; candidateLimitReached=false; dateComparisonValidRows=0; dateComparisonUnavailableRows=0; endInRangeAnnOutsideRows=0 |
| `top10_floatholders-000001-bounded` | PASS | 54 | actualDates=20170331,20170630,20170930,20171231; uniqueDateCount=4; min=20170331; max=20171231; candidateRowLimit=UNKNOWN; candidateLimitReached=false; dateComparisonValidRows=54; dateComparisonUnavailableRows=0; endInRangeAnnOutsideRows=10 |
| `top10_floatholders-000001-lower` | PASS | 15 | actualDates=20170331; uniqueDateCount=1; min=20170331; max=20170331; candidateRowLimit=UNKNOWN; candidateLimitReached=false; dateComparisonValidRows=15; dateComparisonUnavailableRows=0; endInRangeAnnOutsideRows=15 |
| `top10_floatholders-000001-upper` | PASS | 10 | actualDates=20171231; uniqueDateCount=1; min=20171231; max=20171231; candidateRowLimit=UNKNOWN; candidateLimitReached=false; dateComparisonValidRows=10; dateComparisonUnavailableRows=0; endInRangeAnnOutsideRows=10 |
| `top10_floatholders-000001-before` | EVIDENCE_MISSING | 0 | actualDates=; uniqueDateCount=0; min=none; max=none; candidateRowLimit=UNKNOWN; candidateLimitReached=false; dateComparisonValidRows=0; dateComparisonUnavailableRows=0; endInRangeAnnOutsideRows=0 |
| `top10_floatholders-000001-after` | EVIDENCE_MISSING | 0 | actualDates=; uniqueDateCount=0; min=none; max=none; candidateRowLimit=UNKNOWN; candidateLimitReached=false; dateComparisonValidRows=0; dateComparisonUnavailableRows=0; endInRangeAnnOutsideRows=0 |
| `top10_floatholders-600000-bounded` | PASS | 46 | actualDates=20170331,20170630,20170930,20171231; uniqueDateCount=4; min=20170331; max=20171231; candidateRowLimit=UNKNOWN; candidateLimitReached=false; dateComparisonValidRows=46; dateComparisonUnavailableRows=0; endInRangeAnnOutsideRows=10 |
| `top10_floatholders-600000-lower` | PASS | 12 | actualDates=20170331; uniqueDateCount=1; min=20170331; max=20170331; candidateRowLimit=UNKNOWN; candidateLimitReached=false; dateComparisonValidRows=12; dateComparisonUnavailableRows=0; endInRangeAnnOutsideRows=12 |
| `top10_floatholders-600000-upper` | PASS | 10 | actualDates=20171231; uniqueDateCount=1; min=20171231; max=20171231; candidateRowLimit=UNKNOWN; candidateLimitReached=false; dateComparisonValidRows=10; dateComparisonUnavailableRows=0; endInRangeAnnOutsideRows=10 |
| `top10_floatholders-600000-before` | EVIDENCE_MISSING | 0 | actualDates=; uniqueDateCount=0; min=none; max=none; candidateRowLimit=UNKNOWN; candidateLimitReached=false; dateComparisonValidRows=0; dateComparisonUnavailableRows=0; endInRangeAnnOutsideRows=0 |
| `top10_floatholders-600000-after` | EVIDENCE_MISSING | 0 | actualDates=; uniqueDateCount=0; min=none; max=none; candidateRowLimit=UNKNOWN; candidateLimitReached=false; dateComparisonValidRows=0; dateComparisonUnavailableRows=0; endInRangeAnnOutsideRows=0 |
| `stk_managers-000001-bounded` | PASS | 5 | actualDates=20180816,20190307; uniqueDateCount=2; min=20180816; max=20190307; candidateRowLimit=UNKNOWN; candidateLimitReached=false; managerBeginDateComparisonValidRows=5; managerBeginDateComparisonUnavailableRows=0; managerAnnDateDifferentFromBeginDateRows=5; managerAnnInRangeBeginOutsideRows=3; managerEndDateComparisonValidRows=2; managerEndDateComparisonUnavailableRows=3; managerAnnDateDifferentFromEndDateRows=2; managerAnnInRangeEndOutsideRows=2 |
| `stk_managers-000001-lower` | PASS | 3 | actualDates=20180816; uniqueDateCount=1; min=20180816; max=20180816; candidateRowLimit=UNKNOWN; candidateLimitReached=false; managerBeginDateComparisonValidRows=3; managerBeginDateComparisonUnavailableRows=0; managerAnnDateDifferentFromBeginDateRows=3; managerAnnInRangeBeginOutsideRows=3; managerEndDateComparisonValidRows=0; managerEndDateComparisonUnavailableRows=3; managerAnnDateDifferentFromEndDateRows=0; managerAnnInRangeEndOutsideRows=0 |
| `stk_managers-000001-upper` | PASS | 2 | actualDates=20190307; uniqueDateCount=1; min=20190307; max=20190307; candidateRowLimit=UNKNOWN; candidateLimitReached=false; managerBeginDateComparisonValidRows=2; managerBeginDateComparisonUnavailableRows=0; managerAnnDateDifferentFromBeginDateRows=2; managerAnnInRangeBeginOutsideRows=2; managerEndDateComparisonValidRows=2; managerEndDateComparisonUnavailableRows=0; managerAnnDateDifferentFromEndDateRows=2; managerAnnInRangeEndOutsideRows=2 |
| `stk_managers-000001-before` | EVIDENCE_MISSING | 0 | actualDates=; uniqueDateCount=0; min=none; max=none; candidateRowLimit=UNKNOWN; candidateLimitReached=false; managerBeginDateComparisonValidRows=0; managerBeginDateComparisonUnavailableRows=0; managerAnnDateDifferentFromBeginDateRows=0; managerAnnInRangeBeginOutsideRows=0; managerEndDateComparisonValidRows=0; managerEndDateComparisonUnavailableRows=0; managerAnnDateDifferentFromEndDateRows=0; managerAnnInRangeEndOutsideRows=0 |
| `stk_managers-000001-after` | EVIDENCE_MISSING | 0 | actualDates=; uniqueDateCount=0; min=none; max=none; candidateRowLimit=UNKNOWN; candidateLimitReached=false; managerBeginDateComparisonValidRows=0; managerBeginDateComparisonUnavailableRows=0; managerAnnDateDifferentFromBeginDateRows=0; managerAnnInRangeBeginOutsideRows=0; managerEndDateComparisonValidRows=0; managerEndDateComparisonUnavailableRows=0; managerAnnDateDifferentFromEndDateRows=0; managerAnnInRangeEndOutsideRows=0 |
| `stk_managers-600000-bounded` | PASS | 8 | actualDates=20180428,20180830,20190326; uniqueDateCount=3; min=20180428; max=20190326; candidateRowLimit=UNKNOWN; candidateLimitReached=false; managerBeginDateComparisonValidRows=8; managerBeginDateComparisonUnavailableRows=0; managerAnnDateDifferentFromBeginDateRows=8; managerAnnInRangeBeginOutsideRows=1; managerEndDateComparisonValidRows=3; managerEndDateComparisonUnavailableRows=5; managerAnnDateDifferentFromEndDateRows=3; managerAnnInRangeEndOutsideRows=3 |
| `stk_managers-600000-lower` | PASS | 1 | actualDates=20180428; uniqueDateCount=1; min=20180428; max=20180428; candidateRowLimit=UNKNOWN; candidateLimitReached=false; managerBeginDateComparisonValidRows=1; managerBeginDateComparisonUnavailableRows=0; managerAnnDateDifferentFromBeginDateRows=1; managerAnnInRangeBeginOutsideRows=1; managerEndDateComparisonValidRows=1; managerEndDateComparisonUnavailableRows=0; managerAnnDateDifferentFromEndDateRows=1; managerAnnInRangeEndOutsideRows=1 |
| `stk_managers-600000-upper` | PASS | 5 | actualDates=20190326; uniqueDateCount=1; min=20190326; max=20190326; candidateRowLimit=UNKNOWN; candidateLimitReached=false; managerBeginDateComparisonValidRows=5; managerBeginDateComparisonUnavailableRows=0; managerAnnDateDifferentFromBeginDateRows=5; managerAnnInRangeBeginOutsideRows=5; managerEndDateComparisonValidRows=0; managerEndDateComparisonUnavailableRows=5; managerAnnDateDifferentFromEndDateRows=0; managerAnnInRangeEndOutsideRows=0 |
| `stk_managers-600000-before` | EVIDENCE_MISSING | 0 | actualDates=; uniqueDateCount=0; min=none; max=none; candidateRowLimit=UNKNOWN; candidateLimitReached=false; managerBeginDateComparisonValidRows=0; managerBeginDateComparisonUnavailableRows=0; managerAnnDateDifferentFromBeginDateRows=0; managerAnnInRangeBeginOutsideRows=0; managerEndDateComparisonValidRows=0; managerEndDateComparisonUnavailableRows=0; managerAnnDateDifferentFromEndDateRows=0; managerAnnInRangeEndOutsideRows=0 |
| `stk_managers-600000-after` | EVIDENCE_MISSING | 0 | actualDates=; uniqueDateCount=0; min=none; max=none; candidateRowLimit=UNKNOWN; candidateLimitReached=false; managerBeginDateComparisonValidRows=0; managerBeginDateComparisonUnavailableRows=0; managerAnnDateDifferentFromBeginDateRows=0; managerAnnInRangeBeginOutsideRows=0; managerEndDateComparisonValidRows=0; managerEndDateComparisonUnavailableRows=0; managerAnnDateDifferentFromEndDateRows=0; managerAnnInRangeEndOutsideRows=0 |
| `suspend_d-000001-bounded` | PASS | 220 | actualDates=见唯一JSON; uniqueDateCount=220; min=20000622; max=20140716; candidateRowLimit=UNKNOWN; candidateLimitReached=false; suspensionRows=211; resumptionRows=9; suspensionTypeUnavailableRows=0; intradayTimingPresentRows=0 |
| `suspend_d-000001-lower` | PASS | 1 | actualDates=20000622; uniqueDateCount=1; min=20000622; max=20000622; candidateRowLimit=UNKNOWN; candidateLimitReached=false; suspensionRows=1; resumptionRows=0; suspensionTypeUnavailableRows=0; intradayTimingPresentRows=0 |
| `suspend_d-000001-upper` | PASS | 1 | actualDates=20140716; uniqueDateCount=1; min=20140716; max=20140716; candidateRowLimit=UNKNOWN; candidateLimitReached=false; suspensionRows=0; resumptionRows=1; suspensionTypeUnavailableRows=0; intradayTimingPresentRows=0 |
| `suspend_d-000001-before` | EVIDENCE_MISSING | 0 | actualDates=; uniqueDateCount=0; min=none; max=none; candidateRowLimit=UNKNOWN; candidateLimitReached=false; suspensionRows=0; resumptionRows=0; suspensionTypeUnavailableRows=0; intradayTimingPresentRows=0 |
| `suspend_d-000001-after` | EVIDENCE_MISSING | 0 | actualDates=; uniqueDateCount=0; min=none; max=none; candidateRowLimit=UNKNOWN; candidateLimitReached=false; suspensionRows=0; resumptionRows=0; suspensionTypeUnavailableRows=0; intradayTimingPresentRows=0 |
| `suspend_d-600000-bounded` | PASS | 147 | actualDates=见唯一JSON; uniqueDateCount=147; min=20000508; max=20160311; candidateRowLimit=UNKNOWN; candidateLimitReached=false; suspensionRows=142; resumptionRows=5; suspensionTypeUnavailableRows=0; intradayTimingPresentRows=0 |
| `suspend_d-600000-lower` | PASS | 1 | actualDates=20000508; uniqueDateCount=1; min=20000508; max=20000508; candidateRowLimit=UNKNOWN; candidateLimitReached=false; suspensionRows=1; resumptionRows=0; suspensionTypeUnavailableRows=0; intradayTimingPresentRows=0 |
| `suspend_d-600000-upper` | PASS | 1 | actualDates=20160311; uniqueDateCount=1; min=20160311; max=20160311; candidateRowLimit=UNKNOWN; candidateLimitReached=false; suspensionRows=0; resumptionRows=1; suspensionTypeUnavailableRows=0; intradayTimingPresentRows=0 |
| `suspend_d-600000-before` | EVIDENCE_MISSING | 0 | actualDates=; uniqueDateCount=0; min=none; max=none; candidateRowLimit=UNKNOWN; candidateLimitReached=false; suspensionRows=0; resumptionRows=0; suspensionTypeUnavailableRows=0; intradayTimingPresentRows=0 |
| `suspend_d-600000-after` | EVIDENCE_MISSING | 0 | actualDates=; uniqueDateCount=0; min=none; max=none; candidateRowLimit=UNKNOWN; candidateLimitReached=false; suspensionRows=0; resumptionRows=0; suspensionTypeUnavailableRows=0; intradayTimingPresentRows=0 |
| `repurchase-all-bounded` | PASS | 852 | actualDates=见唯一JSON; uniqueDateCount=26; min=20260801; max=20260831; candidateRowLimit=UNKNOWN; candidateLimitReached=false; validStockCodeRows=852; unavailableStockCodeRows=0; distinctStockCodeCount=585 |
| `repurchase-all-lower` | PASS | 44 | actualDates=20260801; uniqueDateCount=1; min=20260801; max=20260801; candidateRowLimit=UNKNOWN; candidateLimitReached=false; validStockCodeRows=44; unavailableStockCodeRows=0; distinctStockCodeCount=41 |
| `repurchase-all-upper` | PASS | 10 | actualDates=20260831; uniqueDateCount=1; min=20260831; max=20260831; candidateRowLimit=UNKNOWN; candidateLimitReached=false; validStockCodeRows=10; unavailableStockCodeRows=0; distinctStockCodeCount=10 |
| `repurchase-all-before` | PASS | 35 | actualDates=20260731; uniqueDateCount=1; min=20260731; max=20260731; candidateRowLimit=UNKNOWN; candidateLimitReached=false; validStockCodeRows=35; unavailableStockCodeRows=0; distinctStockCodeCount=34 |
| `repurchase-all-after` | PASS | 70 | actualDates=20260901; uniqueDateCount=1; min=20260901; max=20260901; candidateRowLimit=UNKNOWN; candidateLimitReached=false; validStockCodeRows=70; unavailableStockCodeRows=0; distinctStockCodeCount=65 |
| `suspend_d-000029-bounded` | PASS | 5 | actualDates=20200309,20200310,20200311,20200312,20200313; uniqueDateCount=5; min=20200309; max=20200313; candidateRowLimit=UNKNOWN; candidateLimitReached=false; suspensionRows=5; resumptionRows=0; suspensionTypeUnavailableRows=0; intradayTimingPresentRows=0 |
| `suspend_d-000029-lower` | PASS | 1 | actualDates=20200309; uniqueDateCount=1; min=20200309; max=20200309; candidateRowLimit=UNKNOWN; candidateLimitReached=false; suspensionRows=1; resumptionRows=0; suspensionTypeUnavailableRows=0; intradayTimingPresentRows=0 |
| `suspend_d-000029-upper` | PASS | 1 | actualDates=20200313; uniqueDateCount=1; min=20200313; max=20200313; candidateRowLimit=UNKNOWN; candidateLimitReached=false; suspensionRows=1; resumptionRows=0; suspensionTypeUnavailableRows=0; intradayTimingPresentRows=0 |
| `suspend_d-000029-before` | EVIDENCE_MISSING | 0 | actualDates=; uniqueDateCount=0; min=none; max=none; candidateRowLimit=UNKNOWN; candidateLimitReached=false; suspensionRows=0; resumptionRows=0; suspensionTypeUnavailableRows=0; intradayTimingPresentRows=0 |
| `suspend_d-000029-after` | EVIDENCE_MISSING | 0 | actualDates=; uniqueDateCount=0; min=none; max=none; candidateRowLimit=UNKNOWN; candidateLimitReached=false; suspensionRows=0; resumptionRows=0; suspensionTypeUnavailableRows=0; intradayTimingPresentRows=0 |
| `suspend_d-600310-bounded` | PASS | 5 | actualDates=20200309,20200310,20200311,20200312,20200313; uniqueDateCount=5; min=20200309; max=20200313; candidateRowLimit=UNKNOWN; candidateLimitReached=false; suspensionRows=5; resumptionRows=0; suspensionTypeUnavailableRows=0; intradayTimingPresentRows=0 |
| `suspend_d-600310-lower` | PASS | 1 | actualDates=20200309; uniqueDateCount=1; min=20200309; max=20200309; candidateRowLimit=UNKNOWN; candidateLimitReached=false; suspensionRows=1; resumptionRows=0; suspensionTypeUnavailableRows=0; intradayTimingPresentRows=0 |
| `suspend_d-600310-upper` | PASS | 1 | actualDates=20200313; uniqueDateCount=1; min=20200313; max=20200313; candidateRowLimit=UNKNOWN; candidateLimitReached=false; suspensionRows=1; resumptionRows=0; suspensionTypeUnavailableRows=0; intradayTimingPresentRows=0 |
| `suspend_d-600310-before` | EVIDENCE_MISSING | 0 | actualDates=; uniqueDateCount=0; min=none; max=none; candidateRowLimit=UNKNOWN; candidateLimitReached=false; suspensionRows=0; resumptionRows=0; suspensionTypeUnavailableRows=0; intradayTimingPresentRows=0 |
| `suspend_d-600310-after` | EVIDENCE_MISSING | 0 | actualDates=; uniqueDateCount=0; min=none; max=none; candidateRowLimit=UNKNOWN; candidateLimitReached=false; suspensionRows=0; resumptionRows=0; suspensionTypeUnavailableRows=0; intradayTimingPresentRows=0 |
| `suspend_d-000001-consecutive` | PASS | 5 | actualDates=20100705,20100706,20100707,20100708,20100709; uniqueDateCount=5; min=20100705; max=20100709; candidateRowLimit=UNKNOWN; candidateLimitReached=false; suspensionRows=5; resumptionRows=0; suspensionTypeUnavailableRows=0; intradayTimingPresentRows=0 |
| `suspend_d-600000-consecutive` | PASS | 5 | actualDates=20150608,20150609,20150610,20150611,20150612; uniqueDateCount=5; min=20150608; max=20150612; candidateRowLimit=UNKNOWN; candidateLimitReached=false; suspensionRows=5; resumptionRows=0; suspensionTypeUnavailableRows=0; intradayTimingPresentRows=0 |

### issue025-express-20260913T163418Z 实际结果

- UTC `2026-09-13T16:36:00.042Z`～`2026-09-13T16:36:13.361Z`；exit 0、cleanup PASS；6case/6请求，状态{'PASS': 4, 'EVIDENCE_MISSING': 2}。安全原对象与预登记输入逐项相符，完整追加唯一索引，旧run未改。
- 来源指纹 `946b1df0fce3841b1396a4f74d3a1442c82cb0914ad5a0fe6cb8f7078064b15b`，生产包 `d46297ad93e9ef3ccd8f9bd4e96c2f3903f7d674cae3b6bd5eabab57514c570f`，验收包 `0ec1d61344c1a72416aed64afb26c0d1bc04b61ed2542640c023910acd82757c`；manifest `386f46a99b6605e203129836d7a744b96b65304307f52991dd8bba6fd1870984`，examples `6d4c74a1a539b59ac20fb0cbd3ba1fba0954c40ef1209b652f7dcc2192ec932f`。wrapper清理检查结果见本轮私有source-run.json；SOURCE的TASK/SQL字段为null。

| caseId后缀 | 状态 | 行数 | 安全日期/计数/摘要观察 |
| --- | --- | ---: | --- |
| `whole` | PASS | 1 | actualDates=20220114; uniqueDateCount=1; min=20220114; max=20220114; candidateRowLimit=UNKNOWN; candidateLimitReached=false; dateComparisonValidRows=1; dateComparisonUnavailableRows=0; annDateDifferentFromEndDateRows=1; annInRangeEndOutsideRows=1 |
| `day` | PASS | 1 | actualDates=20220114; uniqueDateCount=1; min=20220114; max=20220114; candidateRowLimit=UNKNOWN; candidateLimitReached=false; dateComparisonValidRows=1; dateComparisonUnavailableRows=0; annDateDifferentFromEndDateRows=1; annInRangeEndOutsideRows=1 |
| `lower-window` | PASS | 1 | actualDates=20220114; uniqueDateCount=1; min=20220114; max=20220114; candidateRowLimit=UNKNOWN; candidateLimitReached=false; dateComparisonValidRows=1; dateComparisonUnavailableRows=0; annDateDifferentFromEndDateRows=1; annInRangeEndOutsideRows=1 |
| `upper-window` | PASS | 1 | actualDates=20220114; uniqueDateCount=1; min=20220114; max=20220114; candidateRowLimit=UNKNOWN; candidateLimitReached=false; dateComparisonValidRows=1; dateComparisonUnavailableRows=0; annDateDifferentFromEndDateRows=1; annInRangeEndOutsideRows=1 |
| `before` | EVIDENCE_MISSING | 0 | actualDates=; uniqueDateCount=0; min=none; max=none; candidateRowLimit=UNKNOWN; candidateLimitReached=false; dateComparisonValidRows=0; dateComparisonUnavailableRows=0; annDateDifferentFromEndDateRows=0; annInRangeEndOutsideRows=0 |
| `after` | EVIDENCE_MISSING | 0 | actualDates=; uniqueDateCount=0; min=none; max=none; candidateRowLimit=UNKNOWN; candidateLimitReached=false; dateComparisonValidRows=0; dateComparisonUnavailableRows=0; annDateDifferentFromEndDateRows=0; annInRangeEndOutsideRows=0 |

### ISSUE-025 当前交付核对（2026-09-14）

- 三轮原对象/固定私有清单逐项相符，135项SOURCE各1请求、1成功叶子；92非空、43空，无FAILED/NOT_RUN，三个exit0/cleanup PASS。SOURCE任务/SQL字段均null，安全日期/事件计数分母逐项核对，空与非空原状态保留。
- 十一接口两基准股票/原非股票整段与非空首末单日均匹配；suspend两基准股票连续五日再次实测各5行，历史S/R为211/9与142/5。cashflow两股票6/5行ann/f_ann有效且全部相同，不能声称已有两列不同样本。top10_holders浦发60行含20170831/20170904非季末日期，不以“四季×10”推导上限。
- 87组唯一非空参数已精确绑定SOURCE和另取TASK caseId，结构校验通过；现有消费者对87组UNKNOWN任务逐一拒绝。43项新空未放入条件输入；交付清单仅是唯一索引的可重建投影，未形成新验收索引或执行TASK。
- 新当前证据预期先RED（78项中1项失败，原引用仍指旧SOURCE），更新十一当前引用后Node78/78、exit0。前述Maven194、隔离1093后端/包及468前端结果保持；本次索引/文档与Node测试修改未改变隔离SOURCE源码/两包身份，未重复账户调用。`/private/tmp/issue025-control/verification-summary.json`记录25轮/822case/886请求、旧22轮及其他29接口逐对象保持、40行报告与索引一致。
- 十一completeness仍UNKNOWN、decisionRef仍null、生产4 AVAILABLE/30 NEEDS_VERIFICATION/6 SINGLE_ONLY和版本不变。ISSUE-025第一项关闭条件仍缺上游完整提取规则，具体[A/B采用方案](../issues/proposals/ISSUE-025-extraction-contracts.md)待用户决定，当前IN_PROGRESS，不提前关闭T14或母issue。

### ISSUE-025 最终独立复审与待决收尾（2026-09-14）

独立复审逐对象确认原22轮/其他29接口保持、三新run原对象相等、135项/92非空/43空、22组首轮非空来源的收紧整段/首末单日及邻日参数准确。直接从ISSUE-026 Markdown重建87条清单，逐项匹配真实非空SOURCE；validateEvidence/validateCasePlan通过，selectTaskCases因UNKNOWN全部拒绝。两基准停牌连续五日均来自既有实际日期且各返回5行S。

复审发现一处报告越界：fina_audit具体报告期未保存，不能从年度窗推定20231231。已将JSON当前说明及报告两处改为“同一行报告期在2024年度窗外”，没有补造具体日期或改变运行对象。复审确认闭环，无剩余阻断发现。修正后Node78/78、exit0；25个run与私有原对象/旧基线完全一致。源码/两包身份稳定、15个本轮文件Git管理、所有本地Markdown链接目标与diff空白检查通过；无提交/发布。

用户已收到[具体A/B采用选择](../issues/proposals/ISSUE-025-extraction-contracts.md)，尚未回复。本轮已授权的取证、测试、输入整理和复审完成；第一项关闭条件仍依赖完整性规则或明确的产品承诺修订。ISSUE-025保持IN_PROGRESS，ISSUE-026仍NOT_STARTED，母issue不关闭。

### ISSUE-025 方案A确认与最终验收（2026-09-14）

用户明确回复“可以接受不完整”，采用[方案A及逐接口合同](../issues/proposals/ISSUE-025-extraction-contracts.md#决策记录)。十一项以RESPONSE_ONLY单次原生响应采集替代原完整提取要求；逐接口参数/日期轴、返回行处理、空/失败、持久化快照、页面说明、版本和旧证据处理明确。T13/T14、总体设计和母issue关闭条件均加同一限定引用，其他接口要求与全部历史事实保持。生产实现、能力/页面与真实TASK/SQL仍归ISSUE-026。

- 本轮只修改合同/索引决定及Node独立预期，不改生产、Java投影、SOURCE源码/两包、YAML或业务键，不新增真实请求或数据库操作。此前60项Probe、194项相关Maven、隔离1093项后端/包及468项前端结果沿用，不声称重新执行。
- 独立预期先运行78项、77通过/1失败，失败为decisionRef仍null；更新正式决定后执行 `env -i PATH=/usr/bin:/bin:/usr/sbin:/sbin LANG=C LC_ALL=C data-plane/tensor-app/target/frontend/node/node --test control-plane/e2e/tushare-range-evidence.test.js`，78/78、0失败/跳过、exit0。带用户决定引用与非空SOURCE仍不能绕过UNKNOWN。
- 与决定前基线逐对象核对：只有十一项decisionRef和unresolved中的待决码改变；completeness仍UNKNOWN、rowLimit null、evidenceRefs空，v1/NEEDS_VERIFICATION及所有来源/任务状态不改。全部25轮822case886请求、其他29接口、顶层哈希相等。
- 从ISSUE-026 Markdown独立重建87组唯一参数，全部匹配准确非空SOURCE及清洁run，validateEvidence/validateCasePlan通过；追加本决定引用后仍87/87拒绝。43个新空保持EVIDENCE_MISSING且不在候选中。核对摘要为 `/private/tmp/issue025-decision/verification-summary.json`，它不是新验收索引。
- 独立复审发现提案管理层任期字段应为输出begin_date/end_date，已按实际字段修正。复核总体设计/母issue限定范围及该修正后无剩余阻断。Markdown本地目标与暂存/未暂存空白检查通过。

逐条复核[专属设计](../task-designs/ISSUE-025-design.md)三项Acceptance：已批准的精确响应采集合同成立；两基准股票/回购原方式的代表整段、非空两端与日期/事件来源已取得；清洁运行、必要回归、独立审查与87组正式来源交付均成立。按修订后的真实条件记录ISSUE-025 `IN_PROGRESS → COMPLETED`，不是证明上游完整。生产4 AVAILABLE/30 NEEDS_VERIFICATION/6 SINGLE_ONLY和T13/T14/母issue状态保持；无提交、合并或发布。

### ISSUE-026 后继设计与交接准备（2026-09-14）

在ISSUE-025已记录COMPLETED后完成[专属设计](../task-designs/ISSUE-026-design.md)。独立设计复审指出并已修正：fina_mainbz已承诺的全年/六年真实拆分必须补四新SOURCE和四TASK，不能只用受控测试替代；正式HTTP schema/examples/OpenAPI须同步；同参数多个清洁SOURCE须按指定完整runId/caseId绑定，不得first-match错用旧来源。最终限定复核无剩余material gap。

设计明确268组既有来源、四项新拆分SOURCE和278计划TASK（另含两次披露重下、四项回归），全部为未来实施安排。按看板顺序先链接完成设计，再写[next-task交接](../task-handoffs/ISSUE-026-handoff.md)，之后记录ISSUE-026 NOT_STARTED → READY；未启动实施，未修改生产、未执行新SOURCE/TASK/SQL、未更新验收索引schema或准入。

## ISSUE-029 主营业务拆分SOURCE

### 四项固定来源预登记

- 用户要求完成ISSUE-029，采用[专属设计](../task-designs/ISSUE-029-design.md)和[ISSUE-020决定](../issues/proposals/ISSUE-020-fina-mainbz-default-type.md#决策记录)。runId `issue026-mainbz-split-source-20260914T013736Z`；本节登记时尚未执行。
- 私有清单 `/private/tmp/issue026-mainbz-split-source-20260914T013736Z/cases.json`，文件0600/目录0700，SHA-256 `305121ee329ae4ba8c5836bab04cbf01cd9602cedca80a9e573f8e168d6541ea`。四项均省略type，REPORT_PERIOD/end_date。
- 原始响应<100为成功叶子；>=100多日用DateRangePlanner.split生成相邻闭区间，深度优先先左后右；单日满额失败。父不计成功行及安全投影，请求数包含父。失败即停、无自动重试、SOURCE无业务数据库。
- 每请求至少2000ms，全轮30分钟/5000请求硬上限。四树最坏共10224请求，超过硬上限；不是保证能执行的请求计划，实际预算以Context限制为准。完整隔离源码与两包将在构建/审查通过后、真实执行前补记并冻结。

| 完整caseId后缀 | ts_code | start_date | end_date | 闭区间日数 | 最坏二叉树请求数 |
| --- | --- | --- | --- | ---: | ---: |
| `000001-annual` | 000001.SZ | 20250101 | 20251231 | 365 | 729 |
| `600000-annual` | 600000.SH | 20250101 | 20251231 | 365 | 729 |
| `000001-wide` | 000001.SZ | 20200101 | 20251231 | 2192 | 4383 |
| `600000-wide` | 600000.SH | 20200101 | 20251231 | 2192 | 4383 |

### ISSUE-029 隔离构建与执行前身份

- 完整副本 `/private/tmp/issue029-work-20260914T020500Z`，879个Git管理文件（含全部未提交内容）复制后逐文件相同；snapshot SHA-256 `647b11c1cdfda9949e9a20b072246acfdad3c899cdd787b40cb4cb8d0511f79b`。构建后逐文件再次核对通过。
- Java21.0.11/Node24.15.0，普通构建环境移除账户和业务DB变量。`mvn -o -f data-plane/pom.xml -Pacceptance verify` exit0/BUILD SUCCESS；66个后端/包测试类合计1132项、前端34文件524项及Vite构建通过；失败/错误/跳过0。专项Probe67/67、相关Tushare154/154；隔离Node98/98。日志与逐类计数在 `/private/tmp/issue029-control/` 的build.log、acceptance-summary.json、isolated-node.log和专项日志。
- Task1最初审查的全历史schema限制、新计划完整来源引用与参数键顺序问题均已修复，限定复审PASS；Task2独立规格/质量PASS。整体执行前审查待完成，不将本节视为真实来源通过。
- sourceDiff SHA-256 `f81c6de542e22d3e28d8505cf4997765b370cb2878a833126a49275c3754eadb`；生产包 `f58bc3073e7ea5b6ed19cb9c4561f26e2a4ac08e9607de29c723f475882eaca8`；验收包 `82444713eb4336ac12de10b8da8a4cddbbee9b2ef26979aaff7635c61efb10d1`。
- manifest `386f46a99b6605e203129836d7a744b96b65304307f52991dd8bba6fd1870984`；request examples `6d4c74a1a539b59ac20fb0cbd3ba1fba0954c40ef1209b652f7dcc2192ec932f`。真实wrapper将核对预登记清单、完整snapshot、源码差异、两包与这两份输入在执行前后保持，目录0700/输入与输出0600。
- 正式索引仅schemaVersion 1→2，原25轮822case及全部interface/inputHashes逐对象保持；生产注册/版本保持。此时四项真实SOURCE仍未执行，没有新TASK/SQL。

- 执行前整体独立审查已完成，规格/质量PASS，无阻断项；见[审查记录](../../.superpowers/sdd/2026-09-14-issue-029/final-review.md)。独立重算879文件、两包和固定输入全部一致，允许按已授权固定计划执行，尚不代表SOURCE结果通过。

### issue026-mainbz-split-source-20260914T013736Z 实际结果

- UTC `2026-09-14T02:09:53.597Z`～`2026-09-14T02:11:21.644Z`；exit 0、cleanup PASS；4case/42请求，状态{'PASS': 4}。安全原对象与预登记输入逐项相符，完整追加唯一索引，旧run未改。
- 来源指纹 `f81c6de542e22d3e28d8505cf4997765b370cb2878a833126a49275c3754eadb`，生产包 `f58bc3073e7ea5b6ed19cb9c4561f26e2a4ac08e9607de29c723f475882eaca8`，验收包 `82444713eb4336ac12de10b8da8a4cddbbee9b2ef26979aaff7635c61efb10d1`；manifest `386f46a99b6605e203129836d7a744b96b65304307f52991dd8bba6fd1870984`，examples `6d4c74a1a539b59ac20fb0cbd3ba1fba0954c40ef1209b652f7dcc2192ec932f`。wrapper清理检查结果见本轮私有source-run.json；SOURCE的TASK/SQL字段为null。

| caseId后缀 | 状态 | 行数 | 安全日期/计数/摘要观察 |
| --- | --- | ---: | --- |
| `000001-annual` | PASS | 74 | actualDates=20250630,20251231; uniqueDateCount=2; min=20250630; max=20251231; candidateRowLimit=100; candidateLimitReached=false; splitRule=ROW_LIMIT=100; splitParentCount=0; unresolvedFullLeafCount=0; mainbzTypePRows=52; mainbzTypeDRows=14; mainbzTypeIRows=8; mainbzTypeUnavailableRows=0; mainbzReportDates=20250630,20251231; mainbzReportDateUnavailableRows=0; mainbzBusinessKeyValidRows=74; mainbzBusinessKeyUnavailableRows=0; mainbzDistinctBusinessKeyCount=74; mainbzCrossTypeBusinessKeyCount=0 |
| `600000-annual` | PASS | 110 | actualDates=20250630,20251231; uniqueDateCount=2; min=20250630; max=20251231; candidateRowLimit=100; candidateLimitReached=true; splitRule=ROW_LIMIT=100; splitParentCount=1; unresolvedFullLeafCount=0; mainbzTypePRows=74; mainbzTypeDRows=20; mainbzTypeIRows=16; mainbzTypeUnavailableRows=0; mainbzReportDates=20250630,20251231; mainbzReportDateUnavailableRows=0; mainbzBusinessKeyValidRows=110; mainbzBusinessKeyUnavailableRows=0; mainbzDistinctBusinessKeyCount=110; mainbzCrossTypeBusinessKeyCount=0 |
| `000001-wide` | PASS | 458 | actualDates=20200630,20201231,20210630,20211231,20220630,20221231,20230630,20231231,20240630,20241231,20250630,20251231; uniqueDateCount=12; min=20200630; max=20251231; candidateRowLimit=100; candidateLimitReached=true; splitRule=ROW_LIMIT=100; splitParentCount=7; unresolvedFullLeafCount=0; mainbzTypePRows=326; mainbzTypeDRows=84; mainbzTypeIRows=48; mainbzTypeUnavailableRows=0; mainbzReportDates=20200630,20201231,20210630,20211231,20220630,20221231,20230630,20231231,20240630,20241231,20250630,20251231; mainbzReportDateUnavailableRows=0; mainbzBusinessKeyValidRows=458; mainbzBusinessKeyUnavailableRows=0; mainbzDistinctBusinessKeyCount=458; mainbzCrossTypeBusinessKeyCount=0 |
| `600000-wide` | PASS | 657 | actualDates=20200630,20201231,20210630,20211231,20220630,20221231,20230630,20231231,20240630,20241231,20250630,20251231; uniqueDateCount=12; min=20200630; max=20251231; candidateRowLimit=100; candidateLimitReached=true; splitRule=ROW_LIMIT=100; splitParentCount=11; unresolvedFullLeafCount=0; mainbzTypePRows=443; mainbzTypeDRows=117; mainbzTypeIRows=97; mainbzTypeUnavailableRows=0; mainbzReportDates=20200630,20201231,20210630,20211231,20220630,20221231,20230630,20231231,20240630,20241231,20250630,20251231; mainbzReportDateUnavailableRows=0; mainbzBusinessKeyValidRows=657; mainbzBusinessKeyUnavailableRows=0; mainbzDistinctBusinessKeyCount=657; mainbzCrossTypeBusinessKeyCount=0 |


## ISSUE-030 候选构建与离线交付（2026-09-15）

- 按[专属设计](../task-designs/ISSUE-030-design.md)完成30新候选：11 RESPONSE_ONLY、18 ROW_LIMIT、1完整日历，均首次v2；原四v2保持。BJ只规划SSE日历且保留BJ证券，直接BSE仍本地拒绝。本地34 AVAILABLE/6 SINGLE_ONLY是后继TASK入口；正式索引仍4 AVAILABLE/30 NEEDS_VERIFICATION/6 SINGLE_ONLY。
- 新隔离副本`/private/tmp/issue030-work-20260914T025010Z`，893个Git管理文件（含前序未提交内容）逐文件一致；snapshot `c05f7a306c73cea3e526120b165672878f3aba2bab4052bee96b1dffd68c10db`。最终422个相关源码文件及两包/输入再次核对稳定；后续文档不改变此构建身份。
- 源码diff SHA-256 `5a6aeecda5269cdd2259602f2ca0a550faaa8218413644d3a892fb20b19372d6`。
- 生产JAR SHA-256 `de8130e205f20493a01c566d74390a14483bb1296bcf955df5c2e4f389ff3ac1`。
- 验收JAR SHA-256 `518add550be578917ef064ca626982f4293f2d493acb1f919358af0f34c5ce2a`。
- manifest SHA-256 `386f46a99b6605e203129836d7a744b96b65304307f52991dd8bba6fd1870984`。
- request examples SHA-256 `6d4c74a1a539b59ac20fb0cbd3ba1fba0954c40ef1209b652f7dcc2192ec932f`。
- Java21.0.11/Node24.15.0白名单环境；完整acceptance exit0/BUILD SUCCESS，66类1133后端/包测试、34文件524前端测试、Vite构建通过；Node104、受控UI66、实际验收包metadata40通过，0失败/错误/跳过。metadata上游/任务提交0，测试进程、容器、临时秘密清理；最终源码与输入稳定。独立组件、限定复审及整体源码/身份审查通过。
- 272个离线SOURCE绑定精确对应28/12/38/35/35/33/87/4，mainbz原12与新4按完整身份分别保留；未来case命名保持交付约定。私有计划SHA `9c2cb2a9daab04c13bf9aeed5ac69a0d403453611c4a3b40b85d8685837c7b3d`，绑定SHA `a4ce0c747643c91745726b87b4ea5ae682152ac2e542a6783bcd0402acbbba02`；目录0700/文件0600。
- 未新增真实SOURCE/TASK/SQL或JSON run/case。26轮826case928请求逐对象保持，canonical runs SHA（Python `json.dumps(runs, sort_keys=True, separators=(',', ':'), ensure_ascii=False)` UTF-8）`7da2f75bbf00648df9157261704e9c99913fb1d9f3acd9775a6f948d3f5ef213`。候选索引SHA `a281a8460c4fdf57cf5a5d7ebdf13324f6579b15d61867d937f28d2e566c6490`。此前失败/中断浏览器尝试和真实历史均未删除或重标；完整结果、输入摘要和审查见[ISSUE-030验收](ISSUE-030-range-candidate-policies.md)。278 TASK/十轮SQL仍由ISSUE-031执行，最终六门禁/母任务关闭归ISSUE-032。

## ISSUE-031 limits 真实TASK预登记（20260914T174909Z）

- runId `issue026-range-limits-20260914T174909Z`；固定28 TASK、API `daily, forecast, dividend`，完整清单 `/private/tmp/issue026-range-limits-20260914T174909Z/inputs/cases.json`。原参数/日期轴/来源身份不变；本段登记时尚未启动真实任务。
- SOURCE既有观察合计36请求，仅用于预算；实际TASK请求数待实测，潜在日期二分仍受5000请求/30分钟硬限约束（最坏树可能超过预算，不保证全部完成）。每请求间隔≥2000ms、workers=1、retries=0，trace/screenshot/video关闭。fixture另2提交/3查询，目标records查询56次。
- 新空schema计划 `tensor_m14_t05_26b635bc26c14282`，MySQL8.4.6；创建后核对0表、单库最小权限与UTF-8，应用启动后核对8迁移/52业务与任务表。全部成功数据库保留；只管理本轮JVM，固定HTTPS。
- 私有输入父目录0700/文件0600，索引仅按本轮快照消费；hash：`cases.json` `f8282446274eaa0b09c5a3924d686a49f4f8a9d1a65970150b115bcee01a4e9b`；`evidence-index.json` `a281a8460c4fdf57cf5a5d7ebdf13324f6579b15d61867d937f28d2e566c6490`；`source-bindings.json` `646aa854a44f99422790114eddbd0e733d9d16f8385ee155f01525510724cdfd`。
- 源码/两包沿用ISSUE-030冻结893文件snapshot `c05f7a306c73cea3e526120b165672878f3aba2bab4052bee96b1dffd68c10db`，422相关源码与工作树相同；生产 `de8130e205f20493a01c566d74390a14483bb1296bcf955df5c2e4f389ff3ac1` / 验收 `518add550be578917ef064ca626982f4293f2d493acb1f919358af0f34c5ce2a`；其余身份见本轮wrapper记录和ISSUE-030构建登记。
- SOURCE→TASK逐项绑定 `/private/tmp/issue026-range-limits-20260914T174909Z/inputs/source-bindings.json`；每项均核对页面→202/Location→详情/全部批次→两次records→SQL原键/归属/实际source、insert、update/日志与清理。失败或输入变化停止，无自动重试或换日期；结果随后追加。

- 首轮准备实测（启动前）：新schema已创建，MySQL版本8.4.6、初始0表、utf8mb4和7项单库权限核对通过；只读`--list` exit0，准确daily/forecast/dividend三个API、28 TASK。未发生真实来源请求。私有准备证据`/private/tmp/issue026-range-limits-20260914T174909Z/database/preflight.json`与`list.log`。

### ISSUE-031 limits 实际结果（issue026-range-limits-20260914T174909Z）

- 浏览器exit0，cleanup PASS；28计划项，状态{"PASS":28,"FAILED":0,"EVIDENCE_MISSING":0,"NOT_RUN":0}。实际来源36请求、records 56次；fixture另2提交/3查询。source 48行、insert 18次、update 30次，最终SQL键数按API单独记录。
- 完整本轮安全输出`/private/tmp/issue026-range-limits-20260914T174909Z/artifacts/run/safe-results.json` SHA `ed6db795e54d6c7ed1fa4e2b54d91cc10b5ee83c68b2be57118ca8fc6f31fa3f`；wrapper身份`/private/tmp/issue026-range-limits-20260914T174909Z/artifacts/task-run-identity.json` SHA `9fcac7b650bbefd1877638c19dd82a26afb75cca96d21ac9a0998fbf499e9c90`。冻结源码/893 snapshot/两包/输入/私有权限前后保持，日志安全、自有进程回收、网络与任务日志关联通过情况以原产物为准；SQL安全汇总`/private/tmp/issue026-range-limits-20260914T174909Z/sql-supplement.json`。新库成功数据保留。
- 旧26轮逐对象保持，唯一索引追加后27轮/854case/964请求。逐项来源绑定与持久策略在harness核对；本轮经独立运行审查后开放接口：daily、forecast、dividend。

| TASK caseId | 状态 | 请求 | source | insert | update | SQL前→后键数 | 成功叶/总叶 |
| --- | --- | ---: | ---: | ---: | ---: | --- | --- |
| `issue026-task-issue019-source-20260913T122126Z-daily-000001-whole` | PASS | 1 | 6 | 6 | 0 | 0→6 | 1/1 |
| `issue026-task-issue019-source-20260913T122126Z-daily-000001-lower` | PASS | 1 | 1 | 0 | 1 | 6→6 | 1/1 |
| `issue026-task-issue019-source-20260913T122126Z-daily-000001-upper` | PASS | 1 | 1 | 0 | 1 | 6→6 | 1/1 |
| `issue026-task-issue019-source-20260913T122126Z-daily-000001-overlap` | PASS | 1 | 4 | 0 | 4 | 6→6 | 1/1 |
| `issue026-task-issue019-source-20260913T122126Z-daily-600000-whole` | PASS | 1 | 6 | 6 | 0 | 6→12 | 1/1 |
| `issue026-task-issue019-source-20260913T122126Z-daily-600000-lower` | PASS | 1 | 1 | 0 | 1 | 12→12 | 1/1 |
| `issue026-task-issue019-source-20260913T122126Z-daily-600000-upper` | PASS | 1 | 1 | 0 | 1 | 12→12 | 1/1 |
| `issue026-task-issue019-source-20260913T122126Z-daily-600000-overlap` | PASS | 1 | 4 | 0 | 4 | 12→12 | 1/1 |
| `issue026-task-issue019-source-20260913T122126Z-forecast-000001-whole` | PASS | 1 | 1 | 1 | 0 | 0→1 | 1/1 |
| `issue026-task-issue019-source-20260913T122126Z-forecast-000001-event-at-lower` | PASS | 1 | 1 | 0 | 1 | 1→1 | 1/1 |
| `issue026-task-issue019-source-20260913T122126Z-forecast-000001-event-at-upper` | PASS | 1 | 1 | 0 | 1 | 1→1 | 1/1 |
| `issue026-task-issue019-source-20260913T122126Z-forecast-000001-event` | PASS | 1 | 1 | 0 | 1 | 1→1 | 1/1 |
| `issue026-task-issue019-source-20260913T122126Z-forecast-600000-whole` | PASS | 1 | 1 | 1 | 0 | 1→2 | 1/1 |
| `issue026-task-issue019-source-20260913T122126Z-forecast-600000-event-at-lower` | PASS | 1 | 1 | 0 | 1 | 2→2 | 1/1 |
| `issue026-task-issue019-source-20260913T122126Z-forecast-600000-event-at-upper` | PASS | 1 | 1 | 0 | 1 | 2→2 | 1/1 |
| `issue026-task-issue019-source-20260913T122126Z-forecast-600000-event` | PASS | 1 | 1 | 0 | 1 | 2→2 | 1/1 |
| `issue026-task-issue019-source-20260913T122126Z-forecast-000005-whole` | PASS | 1 | 1 | 1 | 0 | 2→3 | 1/1 |
| `issue026-task-issue019-source-20260913T122126Z-forecast-000005-event-at-lower` | PASS | 1 | 1 | 0 | 1 | 3→3 | 1/1 |
| `issue026-task-issue019-source-20260913T122126Z-forecast-000005-event-at-upper` | PASS | 1 | 1 | 0 | 1 | 3→3 | 1/1 |
| `issue026-task-issue019-source-20260913T122126Z-forecast-000005-event` | PASS | 1 | 1 | 0 | 1 | 3→3 | 1/1 |
| `issue026-task-issue019-source-20260913T122126Z-dividend-000001-whole` | PASS | 3 | 2 | 2 | 0 | 0→2 | 3/3 |
| `issue026-task-issue019-source-20260913T122126Z-dividend-000001-event` | PASS | 1 | 2 | 0 | 2 | 2→2 | 1/1 |
| `issue026-task-issue019-source-20260913T122126Z-dividend-000001-event-at-lower` | PASS | 2 | 2 | 0 | 2 | 2→2 | 2/2 |
| `issue026-task-issue019-source-20260913T122126Z-dividend-000001-event-at-upper` | PASS | 2 | 2 | 0 | 2 | 2→2 | 2/2 |
| `issue026-task-issue019-dividend-source-20260913T122801Z-whole` | PASS | 3 | 1 | 1 | 0 | 2→3 | 3/3 |
| `issue026-task-issue019-dividend-source-20260913T122801Z-event` | PASS | 1 | 1 | 0 | 1 | 3→3 | 1/1 |
| `issue026-task-issue019-dividend-source-20260913T122801Z-event-at-lower` | PASS | 2 | 1 | 0 | 1 | 3→3 | 2/2 |
| `issue026-task-issue019-dividend-source-20260913T122801Z-event-at-upper` | PASS | 2 | 1 | 0 | 1 | 3→3 | 2/2 |

## ISSUE-031 mainbz 真实TASK预登记（20260914T175905Z）

- runId `issue026-range-mainbz-20260914T175905Z`；固定16 TASK、API `fina_mainbz`，完整清单 `/private/tmp/issue026-range-mainbz-20260914T175905Z/inputs/cases.json`。原参数/日期轴/来源身份不变；本段登记时尚未启动真实任务。
- SOURCE既有观察合计54请求，仅用于预算；实际TASK请求数待实测，潜在日期二分仍受5000请求/30分钟硬限约束（最坏树可能超过预算，不保证全部完成）。每请求间隔≥2000ms、workers=1、retries=0，trace/screenshot/video关闭。fixture另2提交/3查询，目标records查询32次。
- 新空schema计划 `tensor_m14_t05_960d2a98883a5454`，MySQL8.4.6；创建后核对0表、单库最小权限与UTF-8，应用启动后核对8迁移/52业务与任务表。全部成功数据库保留；只管理本轮JVM，固定HTTPS。
- 私有输入父目录0700/文件0600，索引仅按本轮快照消费；hash：`cases.json` `b7b54537302f0d7ff313c9c347d86c00f6748f0194529ecb870b9e934da7d922`；`evidence-index.json` `fa22f370fc0300cec869ebaa964c6f2549df389be8061c95812cc7efc399636b`；`source-bindings.json` `89132c9113e0490ea75a369b849b7b9fd75e81ded80a5f06846e7a565e7dd127`。
- 源码/两包沿用ISSUE-030冻结893文件snapshot `c05f7a306c73cea3e526120b165672878f3aba2bab4052bee96b1dffd68c10db`，422相关源码与工作树相同；生产 `de8130e205f20493a01c566d74390a14483bb1296bcf955df5c2e4f389ff3ac1` / 验收 `518add550be578917ef064ca626982f4293f2d493acb1f919358af0f34c5ce2a`；其余身份见本轮wrapper记录和ISSUE-030构建登记。
- SOURCE→TASK逐项绑定 `/private/tmp/issue026-range-mainbz-20260914T175905Z/inputs/source-bindings.json`；每项均核对页面→202/Location→详情/全部批次→两次records→SQL原键/归属/实际source、insert、update/日志与清理。失败或输入变化停止，无自动重试或换日期；结果随后追加。

### ISSUE-031 mainbz 实际结果（issue026-range-mainbz-20260914T175905Z）

- 浏览器exit0，cleanup PASS；16计划项，状态{"PASS":16,"FAILED":0,"EVIDENCE_MISSING":0,"NOT_RUN":0}。实际来源54请求、records 32次；fixture另2提交/3查询。source 1907行、insert 1115次、update 792次，最终SQL键数按API单独记录。
- 完整本轮安全输出`/private/tmp/issue026-range-mainbz-20260914T175905Z/artifacts/run/safe-results.json` SHA `a8d39bb679cf136c7d024d24024dfffa04446bc776a742fd9879afb3c00a8d0d`；wrapper身份`/private/tmp/issue026-range-mainbz-20260914T175905Z/artifacts/task-run-identity.json` SHA `f3cc746419f5c2ed1d3943487f48412105906120afe4127183ed516c7e328418`。冻结源码/893 snapshot/两包/输入/私有权限前后保持，日志安全、自有进程回收、网络与任务日志关联通过情况以原产物为准；SQL安全汇总`/private/tmp/issue026-range-mainbz-20260914T175905Z/sql-supplement.json`。新库成功数据保留。
- 旧27轮逐对象保持，唯一索引追加后28轮/870case/1018请求。逐项来源绑定与持久策略在harness核对；本轮经独立运行审查后开放接口：fina_mainbz。

| TASK caseId | 状态 | 请求 | source | insert | update | SQL前→后键数 | 成功叶/总叶 |
| --- | --- | ---: | ---: | ---: | ---: | --- | --- |
| `issue026-task-issue020-boundary-source-20260913T131859Z-000001-whole` | PASS | 1 | 37 | 37 | 0 | 0→37 | 1/1 |
| `issue026-task-issue020-source-20260913T131245Z-000001-whole` | PASS | 1 | 74 | 37 | 37 | 37→74 | 1/1 |
| `issue026-task-issue026-mainbz-split-source-20260914T013736Z-000001-annual` | PASS | 1 | 74 | 0 | 74 | 74→74 | 1/1 |
| `issue026-task-issue026-mainbz-split-source-20260914T013736Z-000001-wide` | PASS | 15 | 458 | 384 | 74 | 74→458 | 8/8 |
| `issue026-task-issue020-source-20260913T131245Z-000001-half` | PASS | 1 | 37 | 0 | 37 | 458→458 | 1/1 |
| `issue026-task-issue020-source-20260913T131245Z-000001-annual` | PASS | 1 | 37 | 0 | 37 | 458→458 | 1/1 |
| `issue026-task-issue020-source-20260913T131245Z-000001-event-at-upper` | PASS | 1 | 37 | 0 | 37 | 458→458 | 1/1 |
| `issue026-task-issue020-source-20260913T131245Z-000001-event-at-lower` | PASS | 1 | 37 | 0 | 37 | 458→458 | 1/1 |
| `issue026-task-issue020-source-20260913T131245Z-000001-both-boundaries` | PASS | 1 | 74 | 0 | 74 | 458→458 | 1/1 |
| `issue026-task-issue020-boundary-source-20260913T131859Z-600000-whole` | PASS | 1 | 55 | 55 | 0 | 458→513 | 1/1 |
| `issue026-task-issue026-mainbz-split-source-20260914T013736Z-600000-annual` | PASS | 3 | 110 | 55 | 55 | 513→568 | 2/2 |
| `issue026-task-issue026-mainbz-split-source-20260914T013736Z-600000-wide` | PASS | 23 | 657 | 547 | 110 | 568→1115 | 12/12 |
| `issue026-task-issue020-source-20260913T131245Z-600000-half` | PASS | 1 | 55 | 0 | 55 | 1115→1115 | 1/1 |
| `issue026-task-issue020-source-20260913T131245Z-600000-annual` | PASS | 1 | 55 | 0 | 55 | 1115→1115 | 1/1 |
| `issue026-task-issue020-source-20260913T131245Z-600000-event-at-upper` | PASS | 1 | 55 | 0 | 55 | 1115→1115 | 1/1 |
| `issue026-task-issue020-source-20260913T131245Z-600000-event-at-lower` | PASS | 1 | 55 | 0 | 55 | 1115→1115 | 1/1 |

## ISSUE-031 calendar 真实TASK预登记（20260914T180604Z）

- runId `issue026-range-calendar-20260914T180604Z`；固定38 TASK、API `trade_cal, margin, top_list`，完整清单 `/private/tmp/issue026-range-calendar-20260914T180604Z/inputs/cases.json`。原参数/日期轴/来源身份不变；本段登记时尚未启动真实任务。
- SOURCE既有观察合计62请求，仅用于预算；实际TASK请求数待实测，潜在日期二分仍受5000请求/30分钟硬限约束（最坏树可能超过预算，不保证全部完成）。每请求间隔≥2000ms、workers=1、retries=0，trace/screenshot/video关闭。fixture另2提交/3查询，目标records查询76次。
- 新空schema计划 `tensor_m14_t05_39ebad9a951facfb`，MySQL8.4.6；创建后核对0表、单库最小权限与UTF-8，应用启动后核对8迁移/52业务与任务表。全部成功数据库保留；只管理本轮JVM，固定HTTPS。
- 私有输入父目录0700/文件0600，索引仅按本轮快照消费；hash：`cases.json` `8aabb249bc6ddfa4ec309de4922a0ac19b12093b0b5fa685910c0a696698481f`；`evidence-index.json` `f23f69bec074f3d235e1a61af8e5644bb9273da2d626a5c407b462e90ef913ca`；`source-bindings.json` `7725a8f5c0e8166979a2bf59a58e4754eab455639e6bfd8cb1edc4a25ba4ffc2`。
- 源码/两包沿用ISSUE-030冻结893文件snapshot `c05f7a306c73cea3e526120b165672878f3aba2bab4052bee96b1dffd68c10db`，422相关源码与工作树相同；生产 `de8130e205f20493a01c566d74390a14483bb1296bcf955df5c2e4f389ff3ac1` / 验收 `518add550be578917ef064ca626982f4293f2d493acb1f919358af0f34c5ce2a`；其余身份见本轮wrapper记录和ISSUE-030构建登记。
- SOURCE→TASK逐项绑定 `/private/tmp/issue026-range-calendar-20260914T180604Z/inputs/source-bindings.json`；每项均核对页面→202/Location→详情/全部批次→两次records→SQL原键/归属/实际source、insert、update/日志与清理。失败或输入变化停止，无自动重试或换日期；结果随后追加。

- limits/mainbz两轮SQL及独立审查后均已核对8080无自有JVM并删除本轮临时数据库凭据文件；成功schema和旧容器保留。各轮`secret-cleanup.json`记录实际清理，未删除任何业务数据。

### ISSUE-031 calendar 实际结果（issue026-range-calendar-20260914T180604Z）

- 浏览器exit0，cleanup PASS；38计划项，状态{"PASS":38,"FAILED":0,"EVIDENCE_MISSING":0,"NOT_RUN":0}。实际来源62请求、records 76次；fixture另2提交/3查询。source 138行、insert 103次、update 35次，最终SQL键数按API单独记录。
- 完整本轮安全输出`/private/tmp/issue026-range-calendar-20260914T180604Z/artifacts/run/safe-results.json` SHA `0d297286024012b8b1a108375038330f9d7630899af66bd4816aa138e53b9828`；wrapper身份`/private/tmp/issue026-range-calendar-20260914T180604Z/artifacts/task-run-identity.json` SHA `08019d69d7c80408ecfb87147bc20ee50a8b6f59265732f9d95ac712e76454e7`。冻结源码/893 snapshot/两包/输入/私有权限前后保持，日志安全、自有进程回收、网络与任务日志关联通过情况以原产物为准；SQL安全汇总`/private/tmp/issue026-range-calendar-20260914T180604Z/sql-supplement.json`。新库成功数据保留。
- 旧28轮逐对象保持，唯一索引追加后29轮/908case/1080请求。逐项来源绑定与持久策略在harness核对；本轮经独立运行审查后开放接口：margin、top_list。

| TASK caseId | 状态 | 请求 | source | insert | update | SQL前→后键数 | 成功叶/总叶 |
| --- | --- | ---: | ---: | ---: | ---: | --- | --- |
| `issue026-task-issue021-source-20260913T135006Z-trade_cal-sse-whole` | PASS | 1 | 12 | 12 | 0 | 0→12 | 1/1 |
| `issue026-task-issue021-source-20260913T135006Z-trade_cal-sse-lower` | PASS | 1 | 1 | 0 | 1 | 12→12 | 1/1 |
| `issue026-task-issue021-source-20260913T135006Z-trade_cal-sse-upper` | PASS | 1 | 1 | 0 | 1 | 12→12 | 1/1 |
| `issue026-task-issue021-source-20260913T135006Z-trade_cal-sse-closed` | PASS | 1 | 7 | 0 | 7 | 12→12 | 1/1 |
| `issue026-task-issue021-source-20260913T135006Z-trade_cal-sse-cross-year` | PASS | 1 | 8 | 8 | 0 | 12→20 | 1/1 |
| `issue026-task-issue021-source-20260913T135006Z-trade_cal-sse-current` | PASS | 1 | 8 | 8 | 0 | 20→28 | 1/1 |
| `issue026-task-issue021-bj-source-20260913T135704Z-calendar-sse` | PASS | 1 | 12 | 12 | 0 | 28→40 | 1/1 |
| `issue026-task-issue021-source-20260913T135006Z-trade_cal-szse-whole` | PASS | 1 | 12 | 12 | 0 | 40→52 | 1/1 |
| `issue026-task-issue021-source-20260913T135006Z-trade_cal-szse-lower` | PASS | 1 | 1 | 0 | 1 | 52→52 | 1/1 |
| `issue026-task-issue021-source-20260913T135006Z-trade_cal-szse-upper` | PASS | 1 | 1 | 0 | 1 | 52→52 | 1/1 |
| `issue026-task-issue021-source-20260913T135006Z-trade_cal-szse-closed` | PASS | 1 | 7 | 0 | 7 | 52→52 | 1/1 |
| `issue026-task-issue021-source-20260913T135006Z-trade_cal-szse-cross-year` | PASS | 1 | 8 | 8 | 0 | 52→60 | 1/1 |
| `issue026-task-issue021-source-20260913T135006Z-trade_cal-szse-current` | PASS | 1 | 8 | 8 | 0 | 60→68 | 1/1 |
| `issue026-task-issue021-bj-source-20260913T135704Z-calendar-szse` | PASS | 1 | 12 | 12 | 0 | 68→80 | 1/1 |
| `issue026-task-issue021-source-20260913T135006Z-margin-sse-whole` | PASS | 1 | 6 | 6 | 0 | 0→6 | 1/1 |
| `issue026-task-issue021-source-20260913T135006Z-margin-sse-lower` | PASS | 1 | 1 | 0 | 1 | 6→6 | 1/1 |
| `issue026-task-issue021-source-20260913T135006Z-margin-sse-upper` | PASS | 1 | 1 | 0 | 1 | 6→6 | 1/1 |
| `issue026-task-issue021-source-20260913T135006Z-margin-szse-whole` | PASS | 1 | 6 | 6 | 0 | 6→12 | 1/1 |
| `issue026-task-issue021-source-20260913T135006Z-margin-szse-lower` | PASS | 1 | 1 | 0 | 1 | 12→12 | 1/1 |
| `issue026-task-issue021-source-20260913T135006Z-margin-szse-upper` | PASS | 1 | 1 | 0 | 1 | 12→12 | 1/1 |
| `issue026-task-issue021-source-20260913T135006Z-margin-bse-whole` | PASS | 1 | 6 | 6 | 0 | 12→18 | 1/1 |
| `issue026-task-issue021-source-20260913T135006Z-margin-bse-lower` | PASS | 1 | 1 | 0 | 1 | 18→18 | 1/1 |
| `issue026-task-issue021-source-20260913T135006Z-margin-bse-upper` | PASS | 1 | 1 | 0 | 1 | 18→18 | 1/1 |
| `issue026-task-issue021-source-20260913T135006Z-top_list-000007-whole` | PASS | 4 | 3 | 3 | 0 | 0→3 | 3/3 |
| `issue026-task-issue021-source-20260913T135006Z-top_list-000007-event` | PASS | 2 | 1 | 0 | 1 | 3→3 | 1/1 |
| `issue026-task-issue021-source-20260913T135006Z-top_list-000007-event-at-lower` | PASS | 3 | 2 | 0 | 2 | 3→3 | 2/2 |
| `issue026-task-issue021-source-20260913T135006Z-top_list-000007-event-at-upper` | PASS | 3 | 2 | 0 | 2 | 3→3 | 2/2 |
| `issue026-task-issue021-source-20260913T135006Z-top_list-000007-closed` | PASS | 1 | 0 | 0 | 0 | 3→3 | 0/0 |
| `issue026-task-issue021-source-20260913T135006Z-top_list-600318-whole` | PASS | 4 | 1 | 1 | 0 | 3→4 | 3/3 |
| `issue026-task-issue021-source-20260913T135006Z-top_list-600318-event` | PASS | 2 | 1 | 0 | 1 | 4→4 | 1/1 |
| `issue026-task-issue021-source-20260913T135006Z-top_list-600318-event-at-lower` | PASS | 3 | 1 | 0 | 1 | 4→4 | 2/2 |
| `issue026-task-issue021-source-20260913T135006Z-top_list-600318-event-at-upper` | PASS | 3 | 1 | 0 | 1 | 4→4 | 2/2 |
| `issue026-task-issue021-source-20260913T135006Z-top_list-600318-closed` | PASS | 1 | 0 | 0 | 0 | 4→4 | 0/0 |
| `issue026-task-issue021-bj-source-20260913T135704Z-920008-whole` | PASS | 4 | 1 | 1 | 0 | 4→5 | 3/3 |
| `issue026-task-issue021-bj-source-20260913T135704Z-920008-event` | PASS | 2 | 1 | 0 | 1 | 5→5 | 1/1 |
| `issue026-task-issue021-bj-source-20260913T135704Z-920008-event-at-lower` | PASS | 3 | 1 | 0 | 1 | 5→5 | 2/2 |
| `issue026-task-issue021-bj-source-20260913T135704Z-920008-event-at-upper` | PASS | 3 | 1 | 0 | 1 | 5→5 | 2/2 |
| `issue026-task-issue021-bj-source-20260913T135704Z-920008-closed` | PASS | 1 | 0 | 0 | 0 | 5→5 | 0/0 |

## ISSUE-031 dates 真实TASK预登记（20260914T181447Z）

- runId `issue026-range-dates-20260914T181447Z`；固定35 TASK、API `fina_indicator, stk_holdernumber, trade_cal, weekly, monthly, new_share`，完整清单 `/private/tmp/issue026-range-dates-20260914T181447Z/inputs/cases.json`。原参数/日期轴/来源身份不变；本段登记时尚未启动真实任务。
- SOURCE既有观察合计35请求，仅用于预算；实际TASK请求数待实测，潜在日期二分仍受5000请求/30分钟硬限约束（最坏树可能超过预算，不保证全部完成）。每请求间隔≥2000ms、workers=1、retries=0，trace/screenshot/video关闭。fixture另2提交/3查询，目标records查询70次。
- 新空schema计划 `tensor_m14_t05_e866d8c7fdca2d11`，MySQL8.4.6；创建后核对0表、单库最小权限与UTF-8，应用启动后核对8迁移/52业务与任务表。全部成功数据库保留；只管理本轮JVM，固定HTTPS。
- 私有输入父目录0700/文件0600，索引仅按本轮快照消费；hash：`cases.json` `9f58d1b4cd78cb151b88bc52ec62a9a4b907cb078d9bd3ffe9c4d2c0062bef54`；`evidence-index.json` `38bd429712df9aa26ea203fc2cf36744f112b00324a641bfd9a04559f50639d0`；`source-bindings.json` `7c023fe63cb276c13140e0b64dbd08d56ad9317f92f5b8998182915c61eff097`。
- 源码/两包沿用ISSUE-030冻结893文件snapshot `c05f7a306c73cea3e526120b165672878f3aba2bab4052bee96b1dffd68c10db`，422相关源码与工作树相同；生产 `de8130e205f20493a01c566d74390a14483bb1296bcf955df5c2e4f389ff3ac1` / 验收 `518add550be578917ef064ca626982f4293f2d493acb1f919358af0f34c5ce2a`；其余身份见本轮wrapper记录和ISSUE-030构建登记。
- SOURCE→TASK逐项绑定 `/private/tmp/issue026-range-dates-20260914T181447Z/inputs/source-bindings.json`；每项均核对页面→202/Location→详情/全部批次→两次records→SQL原键/归属/实际source、insert、update/日志与清理。失败或输入变化停止，无自动重试或换日期；结果随后追加。

### ISSUE-031 dates 实际结果（issue026-range-dates-20260914T181447Z）

- 浏览器exit1，cleanup PASS；35计划项，状态{"PASS":0,"FAILED":1,"EVIDENCE_MISSING":0,"NOT_RUN":34}。实际来源1请求、records 2次；fixture另2提交/3查询。source 0行、insert 0次、update 0次，最终SQL键数按API单独记录。
- 完整本轮安全输出`/private/tmp/issue026-range-dates-20260914T181447Z/artifacts/run/safe-results.json` SHA `7c2038c663e7565c2f764b1666be296defb15113e916114690ab41e51c82ed33`；wrapper身份`/private/tmp/issue026-range-dates-20260914T181447Z/artifacts/task-run-identity.json` SHA `938d202a3cd886356b6ef06a1af0864f74d2de03f43c7ebd0417d46116b8ce6c`。冻结源码/893 snapshot/两包/输入/私有权限前后保持，日志安全、自有进程回收、网络与任务日志关联通过情况以原产物为准；SQL安全汇总`/private/tmp/issue026-range-dates-20260914T181447Z/sql-supplement.json`。新库成功数据保留；临时client.cnf/environment.json已删除，实际清理见本轮secret-cleanup.json。
- 旧29轮逐对象保持，唯一索引追加后30轮/943case/1081请求。逐项来源绑定与持久策略在harness核对；本轮经独立运行审查后开放接口：无（尚有跨轮任务或证据缺口）。

| TASK caseId | 状态 | 请求 | source | insert | update | SQL前→后键数 | 成功叶/总叶 |
| --- | --- | ---: | ---: | ---: | ---: | --- | --- |
| `issue026-issue022-indicator-000001-whole` | FAILED | 1 | 0 | 0 | 0 | 0→0 | 0/1 |
| `issue026-issue022-indicator-000001-lower` | NOT_RUN | — | — | — | — | —→— | —/— |
| `issue026-issue022-indicator-000001-upper` | NOT_RUN | — | — | — | — | —→— | —/— |
| `issue026-issue022-indicator-600000-whole` | NOT_RUN | — | — | — | — | —→— | —/— |
| `issue026-issue022-indicator-600000-lower` | NOT_RUN | — | — | — | — | —→— | —/— |
| `issue026-issue022-indicator-600000-upper` | NOT_RUN | — | — | — | — | —→— | —/— |
| `issue026-issue022-holder-000001-whole` | NOT_RUN | — | — | — | — | —→— | —/— |
| `issue026-issue022-holder-000001-event` | NOT_RUN | — | — | — | — | —→— | —/— |
| `issue026-issue022-holder-000001-lower-edge` | NOT_RUN | — | — | — | — | —→— | —/— |
| `issue026-issue022-holder-000001-upper-edge` | NOT_RUN | — | — | — | — | —→— | —/— |
| `issue026-issue022-holder-600000-whole` | NOT_RUN | — | — | — | — | —→— | —/— |
| `issue026-issue022-holder-600000-event` | NOT_RUN | — | — | — | — | —→— | —/— |
| `issue026-issue022-holder-600000-lower-edge` | NOT_RUN | — | — | — | — | —→— | —/— |
| `issue026-issue022-holder-600000-upper-edge` | NOT_RUN | — | — | — | — | —→— | —/— |
| `issue026-issue022-cal-week-SSE` | NOT_RUN | — | — | — | — | —→— | —/— |
| `issue026-issue022-cal-month-SSE` | NOT_RUN | — | — | — | — | —→— | —/— |
| `issue026-issue022-cal-week-SZSE` | NOT_RUN | — | — | — | — | —→— | —/— |
| `issue026-issue022-cal-month-SZSE` | NOT_RUN | — | — | — | — | —→— | —/— |
| `issue026-issue022-weekly-000001-whole` | NOT_RUN | — | — | — | — | —→— | —/— |
| `issue026-issue022-weekly-000001-lower` | NOT_RUN | — | — | — | — | —→— | —/— |
| `issue026-issue022-weekly-000001-upper` | NOT_RUN | — | — | — | — | —→— | —/— |
| `issue026-issue022-weekly-000001-holiday-last` | NOT_RUN | — | — | — | — | —→— | —/— |
| `issue026-issue022-weekly-600000-whole` | NOT_RUN | — | — | — | — | —→— | —/— |
| `issue026-issue022-weekly-600000-lower` | NOT_RUN | — | — | — | — | —→— | —/— |
| `issue026-issue022-weekly-600000-upper` | NOT_RUN | — | — | — | — | —→— | —/— |
| `issue026-issue022-weekly-600000-holiday-last` | NOT_RUN | — | — | — | — | —→— | —/— |
| `issue026-issue022-monthly-000001-whole` | NOT_RUN | — | — | — | — | —→— | —/— |
| `issue026-issue022-monthly-000001-lower` | NOT_RUN | — | — | — | — | —→— | —/— |
| `issue026-issue022-monthly-000001-upper` | NOT_RUN | — | — | — | — | —→— | —/— |
| `issue026-issue022-monthly-600000-whole` | NOT_RUN | — | — | — | — | —→— | —/— |
| `issue026-issue022-monthly-600000-lower` | NOT_RUN | — | — | — | — | —→— | —/— |
| `issue026-issue022-monthly-600000-upper` | NOT_RUN | — | — | — | — | —→— | —/— |
| `issue026-issue022-ipo-whole` | NOT_RUN | — | — | — | — | —→— | —/— |
| `issue026-issue022-ipo-lower` | NOT_RUN | — | — | — | — | —→— | —/— |
| `issue026-issue022-ipo-upper` | NOT_RUN | — | — | — | — | —→— | —/— |

### ISSUE-031失败撤回与新构建（2026-09-15）

四轮实际合计82 PASS / 1 FAILED，195计划项尚未运行；dates失败后没有重试，失败v2任务快照/SQL零写入及原包保留。当前fina_indicator撤回NEEDS_VERIFICATION / v3，正式10/24/6；根因尚未确证。原26轮与初始baseline逐对象保持，当前30轮943case1081请求。临时凭据全部删除，四轮schema保留。

新隔离包身份及定向155、Node104、完整acceptance后端/两包1134和前端524通过见[撤回后验证](ISSUE-031-range-live-task-verification.md#撤回后离线验证与新身份)。本记录不把旧v2实测归于新v3包，ISSUE-031保持BLOCKED，尚无最终收尾或发布结果。

撤回新包metadata普通浏览器40/40 PASS、exit0；安全汇总零TASK/零上游，JVM/容器/临时秘密清理通过，前后验收包身份不变。安全原件及SHA见上述ISSUE-031验收。

## ISSUE-031 dates 真实TASK预登记（20260914T185851Z）

- runId `issue031-range-dates-20260914T185851Z`；固定29 TASK、API `stk_holdernumber, trade_cal, weekly, monthly, new_share`，完整清单 `/private/tmp/issue031-range-dates-20260914T185851Z/inputs/cases.json`。原参数/日期轴/来源身份不变；本段登记时尚未启动真实任务。
- SOURCE既有观察合计29请求，仅用于预算；实际TASK请求数待实测，潜在日期二分仍受5000请求/30分钟硬限约束（最坏树可能超过预算，不保证全部完成）。每请求间隔≥2000ms、workers=1、retries=0，trace/screenshot/video关闭。fixture另2提交/3查询，目标records查询58次。
- 新空schema计划 `tensor_m14_t05_ed76a36414a553d2`，MySQL8.4.6；创建后核对0表、单库最小权限与UTF-8，应用启动后核对8迁移/52业务与任务表。全部成功数据库保留；只管理本轮JVM，固定HTTPS。
- 私有输入父目录0700/文件0600，索引仅按本轮快照消费；hash：`cases.json` `9467f95c51bb18e5112f79f1d13da5ce95c51764fd2d624c38d6becca03a59eb`；`evidence-index.json` `0ce5418cb1bd1df6862a704db43b0e00041733dd9206e53a2fdb2d1b68554e3d`；`source-bindings.json` `9318be567fce0418d7e265634ee15140a524409c56e16e15a9d8bf95ed7c2c58`。
- 源码/两包沿用ISSUE-031撤回构建冻结897文件snapshot `632a8308f20b33908542f2cb65e821a0a3037a3abba78194ae95dbda54e064cf`，422相关源码与工作树相同；生产 `ff30109702e240d34782618f62ab9efed4bdd977e8a691f322d05f6806838bb5` / 验收 `6522cbbd6d02decf91136bc47766de59ec55e369b84fd3e9d00390bd47e5d7f1`；其余身份见本轮wrapper记录和ISSUE-031撤回构建登记。
- SOURCE→TASK逐项绑定 `/private/tmp/issue031-range-dates-20260914T185851Z/inputs/source-bindings.json`；每项均核对页面→202/Location→详情/全部批次→两次records→SQL原键/归属/实际source、insert、update/日志与清理。失败或输入变化停止，无自动重试或换日期；结果随后追加。

### ISSUE-031 dates 实际结果（issue031-range-dates-20260914T185851Z）

- 浏览器exit0，cleanup PASS；29计划项，状态{"PASS":29,"FAILED":0,"EVIDENCE_MISSING":0,"NOT_RUN":0}。实际来源29请求、records 58次；fixture另2提交/3查询。source 205行、insert 186次、update 19次，最终SQL键数按API单独记录。
- 完整本轮安全输出`/private/tmp/issue031-range-dates-20260914T185851Z/artifacts/run/safe-results.json` SHA `bfcef88e148ddc2a9b14e993985f27eb9667095038beb89ceeeee0b18bcdaa46`；wrapper身份`/private/tmp/issue031-range-dates-20260914T185851Z/artifacts/task-run-identity.json` SHA `dc8036f008c778f7badca4d25ef6ad407f4858e14fb6e2cef04e1bd90fb56a39`。冻结源码/897 snapshot/两包/输入/私有权限前后保持，日志安全、自有进程回收、网络与任务日志关联通过情况以原产物为准；SQL安全汇总`/private/tmp/issue031-range-dates-20260914T185851Z/sql-supplement.json`。新库成功数据保留；临时client.cnf/environment.json已删除，实际清理见本轮secret-cleanup.json。
- 旧30轮逐对象保持，唯一索引追加后31轮/972case/1110请求。逐项来源绑定与持久策略在harness核对；本轮经独立运行审查后开放接口：stk_holdernumber、trade_cal、weekly、monthly、new_share。

| TASK caseId | 状态 | 请求 | source | insert | update | SQL前→后键数 | 成功叶/总叶 |
| --- | --- | ---: | ---: | ---: | ---: | --- | --- |
| `issue031-resume-issue026-issue022-holder-000001-whole` | PASS | 1 | 1 | 1 | 0 | 0→1 | 1/1 |
| `issue031-resume-issue026-issue022-holder-000001-event` | PASS | 1 | 1 | 0 | 1 | 1→1 | 1/1 |
| `issue031-resume-issue026-issue022-holder-000001-lower-edge` | PASS | 1 | 1 | 0 | 1 | 1→1 | 1/1 |
| `issue031-resume-issue026-issue022-holder-000001-upper-edge` | PASS | 1 | 1 | 0 | 1 | 1→1 | 1/1 |
| `issue031-resume-issue026-issue022-holder-600000-whole` | PASS | 1 | 1 | 1 | 0 | 1→2 | 1/1 |
| `issue031-resume-issue026-issue022-holder-600000-event` | PASS | 1 | 1 | 0 | 1 | 2→2 | 1/1 |
| `issue031-resume-issue026-issue022-holder-600000-lower-edge` | PASS | 1 | 1 | 0 | 1 | 2→2 | 1/1 |
| `issue031-resume-issue026-issue022-holder-600000-upper-edge` | PASS | 1 | 1 | 0 | 1 | 2→2 | 1/1 |
| `issue031-resume-issue026-issue022-cal-week-SSE` | PASS | 1 | 21 | 21 | 0 | 0→21 | 1/1 |
| `issue031-resume-issue026-issue022-cal-month-SSE` | PASS | 1 | 61 | 61 | 0 | 21→82 | 1/1 |
| `issue031-resume-issue026-issue022-cal-week-SZSE` | PASS | 1 | 21 | 21 | 0 | 82→103 | 1/1 |
| `issue031-resume-issue026-issue022-cal-month-SZSE` | PASS | 1 | 61 | 61 | 0 | 103→164 | 1/1 |
| `issue031-resume-issue026-issue022-weekly-000001-whole` | PASS | 1 | 3 | 3 | 0 | 0→3 | 1/1 |
| `issue031-resume-issue026-issue022-weekly-000001-lower` | PASS | 1 | 1 | 0 | 1 | 3→3 | 1/1 |
| `issue031-resume-issue026-issue022-weekly-000001-upper` | PASS | 1 | 1 | 0 | 1 | 3→3 | 1/1 |
| `issue031-resume-issue026-issue022-weekly-000001-holiday-last` | PASS | 1 | 1 | 0 | 1 | 3→3 | 1/1 |
| `issue031-resume-issue026-issue022-weekly-600000-whole` | PASS | 1 | 3 | 3 | 0 | 3→6 | 1/1 |
| `issue031-resume-issue026-issue022-weekly-600000-lower` | PASS | 1 | 1 | 0 | 1 | 6→6 | 1/1 |
| `issue031-resume-issue026-issue022-weekly-600000-upper` | PASS | 1 | 1 | 0 | 1 | 6→6 | 1/1 |
| `issue031-resume-issue026-issue022-weekly-600000-holiday-last` | PASS | 1 | 1 | 0 | 1 | 6→6 | 1/1 |
| `issue031-resume-issue026-issue022-monthly-000001-whole` | PASS | 1 | 2 | 2 | 0 | 0→2 | 1/1 |
| `issue031-resume-issue026-issue022-monthly-000001-lower` | PASS | 1 | 1 | 0 | 1 | 2→2 | 1/1 |
| `issue031-resume-issue026-issue022-monthly-000001-upper` | PASS | 1 | 1 | 0 | 1 | 2→2 | 1/1 |
| `issue031-resume-issue026-issue022-monthly-600000-whole` | PASS | 1 | 2 | 2 | 0 | 2→4 | 1/1 |
| `issue031-resume-issue026-issue022-monthly-600000-lower` | PASS | 1 | 1 | 0 | 1 | 4→4 | 1/1 |
| `issue031-resume-issue026-issue022-monthly-600000-upper` | PASS | 1 | 1 | 0 | 1 | 4→4 | 1/1 |
| `issue031-resume-issue026-issue022-ipo-whole` | PASS | 1 | 10 | 10 | 0 | 0→10 | 1/1 |
| `issue031-resume-issue026-issue022-ipo-lower` | PASS | 1 | 1 | 0 | 1 | 10→10 | 1/1 |
| `issue031-resume-issue026-issue022-ipo-upper` | PASS | 1 | 2 | 0 | 2 | 10→10 | 1/1 |

## ISSUE-031 events 真实TASK预登记（20260914T191040Z）

- runId `issue031-range-events-20260914T191040Z`；固定37 TASK、API `block_trade, disclosure_date, stk_holdertrade, pledge_detail`，完整清单 `/private/tmp/issue031-range-events-20260914T191040Z/inputs/cases.json`。原参数/日期轴/来源身份不变；本段登记时尚未启动真实任务。
- SOURCE既有观察合计129请求，仅用于预算；实际TASK请求数待实测，潜在日期二分仍受5000请求/30分钟硬限约束（最坏树可能超过预算，不保证全部完成）。每请求间隔≥2000ms、workers=1、retries=0，trace/screenshot/video关闭。fixture另2提交/3查询，目标records查询74次。
- 新空schema计划 `tensor_m14_t05_45615ec0a5c947d1`，MySQL8.4.6；创建后核对0表、单库最小权限与UTF-8，应用启动后核对8迁移/52业务与任务表。全部成功数据库保留；只管理本轮JVM，固定HTTPS。
- 私有输入父目录0700/文件0600，索引仅按本轮快照消费；hash：`cases.json` `ad95f8d7701f5e1a5c19be2ac3ac7e8fa9f9f19a63f5f08df7c11397fcff4e55`；`evidence-index.json` `ca122ea073edd475b07cc1010a8f56a2eb23e14084cfdc5d590d590767b6f9db`；`source-bindings.json` `4c33705e82fac4bae645f10dc579abbd252dc979888f52cec2542c133c90f0b4`。
- 源码/两包沿用ISSUE-031撤回构建冻结897文件snapshot `632a8308f20b33908542f2cb65e821a0a3037a3abba78194ae95dbda54e064cf`，422相关源码与工作树相同；生产 `ff30109702e240d34782618f62ab9efed4bdd977e8a691f322d05f6806838bb5` / 验收 `6522cbbd6d02decf91136bc47766de59ec55e369b84fd3e9d00390bd47e5d7f1`；其余身份见本轮wrapper记录和ISSUE-031撤回构建登记。
- SOURCE→TASK逐项绑定 `/private/tmp/issue031-range-events-20260914T191040Z/inputs/source-bindings.json`；每项均核对页面→202/Location→详情/全部批次→两次records→SQL原键/归属/实际source、insert、update/日志与清理。失败或输入变化停止，无自动重试或换日期；结果随后追加。

### ISSUE-031 events 实际结果（issue031-range-events-20260914T191040Z）

- 浏览器exit0，cleanup PASS；37计划项，状态{"PASS":37,"FAILED":0,"EVIDENCE_MISSING":0,"NOT_RUN":0}。实际来源129请求、records 74次；fixture另2提交/3查询。source 98行、insert 40次、update 58次，最终SQL键数按API单独记录。
- 完整本轮安全输出`/private/tmp/issue031-range-events-20260914T191040Z/artifacts/run/safe-results.json` SHA `f924471179792c3c2a50f5168b5ebd3b86a674b05e9845253bc3eabfad9a45e3`；wrapper身份`/private/tmp/issue031-range-events-20260914T191040Z/artifacts/task-run-identity.json` SHA `fd87712427f11803cb78ce0a87efb129150ae6142c7c6277d4c12e22d2258ccf`。冻结源码/897 snapshot/两包/输入/私有权限前后保持，日志安全、自有进程回收、网络与任务日志关联通过情况以原产物为准；SQL安全汇总`/private/tmp/issue031-range-events-20260914T191040Z/sql-supplement.json`。新库成功数据保留；临时client.cnf/environment.json已删除，实际清理见本轮secret-cleanup.json。
- 旧31轮逐对象保持，唯一索引追加后32轮/1009case/1239请求。逐项来源绑定与持久策略在harness核对；本轮经独立运行审查后开放接口：block_trade、disclosure_date、stk_holdertrade、pledge_detail。

| TASK caseId | 状态 | 请求 | source | insert | update | SQL前→后键数 | 成功叶/总叶 |
| --- | --- | ---: | ---: | ---: | ---: | --- | --- |
| `issue026-issue023-source-20260913T144308Z-block-official` | PASS | 1 | 14 | 14 | 0 | 0→14 | 1/1 |
| `issue026-issue023-boundary-source-20260913T145716Z-block-official-event` | PASS | 1 | 5 | 0 | 5 | 14→14 | 1/1 |
| `issue026-issue023-source-20260913T144308Z-block-000001-whole` | PASS | 1 | 2 | 2 | 0 | 14→16 | 1/1 |
| `issue026-issue023-source-20260913T144308Z-block-000001-event` | PASS | 1 | 2 | 0 | 2 | 16→16 | 1/1 |
| `issue026-issue023-source-20260913T144308Z-block-000001-lower-edge` | PASS | 1 | 2 | 0 | 2 | 16→16 | 1/1 |
| `issue026-issue023-source-20260913T144308Z-block-000001-upper-edge` | PASS | 1 | 2 | 0 | 2 | 16→16 | 1/1 |
| `issue026-issue023-source-20260913T144308Z-block-600000-whole` | PASS | 1 | 2 | 2 | 0 | 16→18 | 1/1 |
| `issue026-issue023-source-20260913T144308Z-block-600000-event` | PASS | 1 | 2 | 0 | 2 | 18→18 | 1/1 |
| `issue026-issue023-source-20260913T144308Z-block-600000-lower-edge` | PASS | 1 | 2 | 0 | 2 | 18→18 | 1/1 |
| `issue026-issue023-source-20260913T144308Z-block-600000-upper-edge` | PASS | 1 | 2 | 0 | 2 | 18→18 | 1/1 |
| `issue026-issue023-source-20260913T144308Z-disclosure-official` | PASS | 3 | 1 | 1 | 0 | 0→1 | 3/3 |
| `issue026-issue023-events-source-20260913T145005Z-disclosure-000001-revision-window` | PASS | 27 | 1 | 1 | 0 | 1→2 | 27/27 |
| `issue026-issue023-boundary-source-20260913T145716Z-disclosure-000001-event` | PASS | 1 | 1 | 0 | 1 | 2→2 | 1/1 |
| `issue026-disclosure-000001-update-recheck` | PASS | 1 | 1 | 0 | 1 | 2→2 | 1/1 |
| `issue026-issue023-boundary-source-20260913T145716Z-disclosure-000001-lower-edge` | PASS | 2 | 1 | 0 | 1 | 2→2 | 2/2 |
| `issue026-issue023-boundary-source-20260913T145716Z-disclosure-000001-upper-edge` | PASS | 2 | 1 | 0 | 1 | 2→2 | 2/2 |
| `issue026-issue023-events-source-20260913T145005Z-disclosure-600000-revision-window` | PASS | 61 | 1 | 1 | 0 | 2→3 | 61/61 |
| `issue026-issue023-boundary-source-20260913T145716Z-disclosure-600000-event` | PASS | 1 | 1 | 0 | 1 | 3→3 | 1/1 |
| `issue026-disclosure-600000-update-recheck` | PASS | 1 | 1 | 0 | 1 | 3→3 | 1/1 |
| `issue026-issue023-boundary-source-20260913T145716Z-disclosure-600000-lower-edge` | PASS | 2 | 1 | 0 | 1 | 3→3 | 2/2 |
| `issue026-issue023-boundary-source-20260913T145716Z-disclosure-600000-upper-edge` | PASS | 2 | 1 | 0 | 1 | 3→3 | 2/2 |
| `issue026-issue023-source-20260913T144308Z-holder-official` | PASS | 1 | 2 | 2 | 0 | 0→2 | 1/1 |
| `issue026-issue023-events-source-20260913T145005Z-holder-000001-whole` | PASS | 1 | 6 | 6 | 0 | 2→8 | 1/1 |
| `issue026-issue023-events-source-20260913T145005Z-holder-000001-event` | PASS | 1 | 6 | 0 | 6 | 8→8 | 1/1 |
| `issue026-issue023-events-source-20260913T145005Z-holder-000001-lower-edge` | PASS | 1 | 6 | 0 | 6 | 8→8 | 1/1 |
| `issue026-issue023-events-source-20260913T145005Z-holder-000001-upper-edge` | PASS | 1 | 6 | 0 | 6 | 8→8 | 1/1 |
| `issue026-issue023-events-source-20260913T145005Z-holder-600000-whole` | PASS | 1 | 1 | 1 | 0 | 8→9 | 1/1 |
| `issue026-issue023-events-source-20260913T145005Z-holder-600000-event` | PASS | 1 | 1 | 0 | 1 | 9→9 | 1/1 |
| `issue026-issue023-events-source-20260913T145005Z-holder-600000-lower-edge` | PASS | 1 | 1 | 0 | 1 | 9→9 | 1/1 |
| `issue026-issue023-events-source-20260913T145005Z-holder-600000-upper-edge` | PASS | 1 | 1 | 0 | 1 | 9→9 | 1/1 |
| `issue026-issue023-source-20260913T144308Z-pledge-official` | PASS | 1 | 8 | 8 | 0 | 0→8 | 1/1 |
| `issue026-issue023-boundary-source-20260913T145716Z-pledge-000014-lower` | PASS | 1 | 2 | 0 | 2 | 8→8 | 1/1 |
| `issue026-issue023-boundary-source-20260913T145716Z-pledge-000014-upper` | PASS | 1 | 4 | 0 | 4 | 8→8 | 1/1 |
| `issue026-issue023-boundary-source-20260913T145716Z-pledge-600000-whole` | PASS | 1 | 2 | 2 | 0 | 8→10 | 1/1 |
| `issue026-issue023-boundary-source-20260913T145716Z-pledge-600000-event` | PASS | 1 | 2 | 0 | 2 | 10→10 | 1/1 |
| `issue026-issue023-boundary-source-20260913T145716Z-pledge-600000-lower-edge` | PASS | 1 | 2 | 0 | 2 | 10→10 | 1/1 |
| `issue026-issue023-boundary-source-20260913T145716Z-pledge-600000-upper-edge` | PASS | 1 | 2 | 0 | 2 | 10→10 | 1/1 |

## ISSUE-031 history 真实TASK预登记（20260914T192313Z）

- runId `issue031-range-history-20260914T192313Z`；固定33 TASK、API `slb_len, slb_sec, slb_sec_detail`，完整清单 `/private/tmp/issue031-range-history-20260914T192313Z/inputs/cases.json`。原参数/日期轴/来源身份不变；本段登记时尚未启动真实任务。
- SOURCE既有观察合计33请求，仅用于预算；实际TASK请求数待实测，潜在日期二分仍受5000请求/30分钟硬限约束（最坏树可能超过预算，不保证全部完成）。每请求间隔≥2000ms、workers=1、retries=0，trace/screenshot/video关闭。fixture另2提交/3查询，目标records查询66次。
- 新空schema计划 `tensor_m14_t05_e08fdccc0312a3fa`，MySQL8.4.6；创建后核对0表、单库最小权限与UTF-8，应用启动后核对8迁移/52业务与任务表。全部成功数据库保留；只管理本轮JVM，固定HTTPS。
- 私有输入父目录0700/文件0600，索引仅按本轮快照消费；hash：`cases.json` `7b255fad17d3f39820370cd9fdd3437dae5d0de77d79ed0174f14ba8505bf0c8`；`evidence-index.json` `0cf3cc2b1c16b441a01bf11a9993f4fe23e97ddf1b2a315dca2f0688e8c1b5f5`；`source-bindings.json` `7338361fa951b4fb255d60f9e3f7344730efa98605a1f66dec6b9d8c5977e871`。
- 源码/两包沿用ISSUE-031撤回构建冻结897文件snapshot `632a8308f20b33908542f2cb65e821a0a3037a3abba78194ae95dbda54e064cf`，422相关源码与工作树相同；生产 `ff30109702e240d34782618f62ab9efed4bdd977e8a691f322d05f6806838bb5` / 验收 `6522cbbd6d02decf91136bc47766de59ec55e369b84fd3e9d00390bd47e5d7f1`；其余身份见本轮wrapper记录和ISSUE-031撤回构建登记。
- SOURCE→TASK逐项绑定 `/private/tmp/issue031-range-history-20260914T192313Z/inputs/source-bindings.json`；每项均核对页面→202/Location→详情/全部批次→两次records→SQL原键/归属/实际source、insert、update/日志与清理。失败或输入变化停止，无自动重试或换日期；结果随后追加。

### ISSUE-031 history 实际结果（issue031-range-history-20260914T192313Z）

- 浏览器exit0，cleanup PASS；33计划项，状态{"PASS":33,"FAILED":0,"EVIDENCE_MISSING":0,"NOT_RUN":0}。实际来源33请求、records 66次；fixture另2提交/3查询。source 563行、insert 511次、update 52次，最终SQL键数按API单独记录。
- 完整本轮安全输出`/private/tmp/issue031-range-history-20260914T192313Z/artifacts/run/safe-results.json` SHA `49a0f6ab0409719d0f147674673de3b7998afa4ed0d2264eb54e96083e846e5f`；wrapper身份`/private/tmp/issue031-range-history-20260914T192313Z/artifacts/task-run-identity.json` SHA `b508bddbe69f4e1214d4cafd3bacf1b6095a59956222059b61eb3c9e82811ff7`。冻结源码/897 snapshot/两包/输入/私有权限前后保持，日志安全、自有进程回收、网络与任务日志关联通过情况以原产物为准；SQL安全汇总`/private/tmp/issue031-range-history-20260914T192313Z/sql-supplement.json`。新库成功数据保留；临时client.cnf/environment.json已删除，实际清理见本轮secret-cleanup.json。
- 旧32轮逐对象保持，唯一索引追加后33轮/1042case/1272请求。逐项来源绑定与持久策略在harness核对；本轮经独立运行审查后开放接口：slb_len、slb_sec、slb_sec_detail。

| TASK caseId | 状态 | 请求 | source | insert | update | SQL前→后键数 | 成功叶/总叶 |
| --- | --- | ---: | ---: | ---: | ---: | --- | --- |
| `issue026-issue024-source-20260913T153146Z-len-whole` | PASS | 1 | 13 | 13 | 0 | 0→13 | 1/1 |
| `issue026-issue024-source-20260913T153146Z-len-lower` | PASS | 1 | 1 | 0 | 1 | 13→13 | 1/1 |
| `issue026-issue024-source-20260913T153146Z-len-upper` | PASS | 1 | 1 | 0 | 1 | 13→13 | 1/1 |
| `issue026-issue024-source-20260913T153146Z-slb_len-all-suspension` | PASS | 1 | 9 | 9 | 0 | 13→22 | 1/1 |
| `issue026-issue024-source-20260913T153146Z-slb_len-all-settlement` | PASS | 1 | 1 | 1 | 0 | 22→23 | 1/1 |
| `issue026-issue024-source-20260913T153146Z-slb_sec-000001-whole` | PASS | 1 | 13 | 13 | 0 | 0→13 | 1/1 |
| `issue026-issue024-source-20260913T153146Z-slb_sec-000001-official-day` | PASS | 1 | 1 | 0 | 1 | 13→13 | 1/1 |
| `issue026-issue024-source-20260913T153146Z-slb_sec-000001-suspension` | PASS | 1 | 9 | 9 | 0 | 13→22 | 1/1 |
| `issue026-issue024-boundary-source-20260913T153639Z-slb_sec-000001-lower` | PASS | 1 | 1 | 0 | 1 | 22→22 | 1/1 |
| `issue026-issue024-boundary-source-20260913T153639Z-slb_sec-000001-lower-edge` | PASS | 1 | 13 | 0 | 13 | 22→22 | 1/1 |
| `issue026-issue024-boundary-source-20260913T153639Z-slb_sec-000001-20240710` | PASS | 1 | 1 | 0 | 1 | 22→22 | 1/1 |
| `issue026-issue024-boundary-source-20260913T153639Z-slb_sec-000001-20240711` | PASS | 1 | 1 | 0 | 1 | 22→22 | 1/1 |
| `issue026-issue024-source-20260913T153146Z-slb_sec-600000-whole` | PASS | 1 | 13 | 13 | 0 | 22→35 | 1/1 |
| `issue026-issue024-source-20260913T153146Z-slb_sec-600000-official-day` | PASS | 1 | 1 | 0 | 1 | 35→35 | 1/1 |
| `issue026-issue024-source-20260913T153146Z-slb_sec-600000-suspension` | PASS | 1 | 9 | 9 | 0 | 35→44 | 1/1 |
| `issue026-issue024-boundary-source-20260913T153639Z-slb_sec-600000-lower` | PASS | 1 | 1 | 0 | 1 | 44→44 | 1/1 |
| `issue026-issue024-boundary-source-20260913T153639Z-slb_sec-600000-lower-edge` | PASS | 1 | 13 | 0 | 13 | 44→44 | 1/1 |
| `issue026-issue024-boundary-source-20260913T153639Z-slb_sec-600000-20240710` | PASS | 1 | 1 | 0 | 1 | 44→44 | 1/1 |
| `issue026-issue024-boundary-source-20260913T153639Z-slb_sec-600000-20240711` | PASS | 1 | 1 | 0 | 1 | 44→44 | 1/1 |
| `issue026-issue024-source-20260913T153146Z-slb_sec_detail-000001-whole` | PASS | 1 | 2 | 2 | 0 | 0→2 | 1/1 |
| `issue026-issue024-source-20260913T153146Z-detail-000001-long` | PASS | 1 | 242 | 240 | 2 | 2→242 | 1/1 |
| `issue026-issue024-source-20260913T153146Z-slb_sec_detail-000001-official-day` | PASS | 1 | 1 | 0 | 1 | 242→242 | 1/1 |
| `issue026-issue024-source-20260913T153146Z-slb_sec_detail-000001-suspension` | PASS | 1 | 7 | 7 | 0 | 242→249 | 1/1 |
| `issue026-issue024-boundary-source-20260913T153639Z-slb_sec_detail-000001-lower` | PASS | 1 | 1 | 0 | 1 | 249→249 | 1/1 |
| `issue026-issue024-boundary-source-20260913T153639Z-slb_sec_detail-000001-lower-edge` | PASS | 1 | 2 | 0 | 2 | 249→249 | 1/1 |
| `issue026-issue024-boundary-source-20260913T153639Z-slb_sec_detail-000001-20240710` | PASS | 1 | 1 | 0 | 1 | 249→249 | 1/1 |
| `issue026-issue024-source-20260913T153146Z-slb_sec_detail-600000-whole` | PASS | 1 | 3 | 3 | 0 | 249→252 | 1/1 |
| `issue026-issue024-source-20260913T153146Z-detail-600000-long` | PASS | 1 | 190 | 187 | 3 | 252→439 | 1/1 |
| `issue026-issue024-source-20260913T153146Z-slb_sec_detail-600000-official-day` | PASS | 1 | 1 | 0 | 1 | 439→439 | 1/1 |
| `issue026-issue024-source-20260913T153146Z-slb_sec_detail-600000-suspension` | PASS | 1 | 5 | 5 | 0 | 439→444 | 1/1 |
| `issue026-issue024-boundary-source-20260913T153639Z-slb_sec_detail-600000-lower` | PASS | 1 | 1 | 0 | 1 | 444→444 | 1/1 |
| `issue026-issue024-boundary-source-20260913T153639Z-slb_sec_detail-600000-lower-edge` | PASS | 1 | 3 | 0 | 3 | 444→444 | 1/1 |
| `issue026-issue024-boundary-source-20260913T153639Z-slb_sec_detail-600000-20240710` | PASS | 1 | 1 | 0 | 1 | 444→444 | 1/1 |

历史窗口专项只读SQL补充：`/private/tmp/issue031-range-history-20260914T192313Z/history-sql.json` SHA `e08afd89421634726547a96e07ef32deda0f355e14ba39002d9b5863306b5a44`。33项最终窗口日期集合/原键基数/同日多键组均匹配准确SOURCE；len23、sec44（两股各22）、detail444（249/195）最终键保留。明细两长窗242/190键、196/150日期、41/33同日多键组、9/7期限、54/39费率；早期窗口及另一股票持续保留。独立审查PASS；这些观察不构成上游历史起止或持续保留保证。

## ISSUE-031 response-trade 真实TASK预登记（20260914T193118Z）

- runId `issue031-range-response-trade-20260914T193118Z`；固定24 TASK、API `adj_factor, suspend_d`，完整清单 `/private/tmp/issue031-range-response-trade-20260914T193118Z/inputs/cases.json`。原参数/日期轴/来源身份不变；本段登记时尚未启动真实任务。
- SOURCE既有观察合计24请求，仅用于预算；实际TASK请求数待实测，潜在日期二分仍受5000请求/30分钟硬限约束（最坏树可能超过预算，不保证全部完成）。每请求间隔≥2000ms、workers=1、retries=0，trace/screenshot/video关闭。fixture另2提交/3查询，目标records查询48次。
- 新空schema计划 `tensor_m14_t05_74bc9d13757ffc1e`，MySQL8.4.6；创建后核对0表、单库最小权限与UTF-8，应用启动后核对8迁移/52业务与任务表。全部成功数据库保留；只管理本轮JVM，固定HTTPS。
- 私有输入父目录0700/文件0600，索引仅按本轮快照消费；hash：`cases.json` `18dca2aa4fac03351a2b4cfe10f66018ffb508f9df47bd12b73086db01921bff`；`evidence-index.json` `3a2fbec19ae4f30ac3cebef8ab75b87fb68bcf03889533c732234fa4ad895ef2`；`source-bindings.json` `041157e66a9f569c0f7e62537cfdfe7a23e4fd405b7a0d85a5d6bca75b971ce1`。
- 源码/两包沿用ISSUE-031撤回构建冻结897文件snapshot `632a8308f20b33908542f2cb65e821a0a3037a3abba78194ae95dbda54e064cf`，422相关源码与工作树相同；生产 `ff30109702e240d34782618f62ab9efed4bdd977e8a691f322d05f6806838bb5` / 验收 `6522cbbd6d02decf91136bc47766de59ec55e369b84fd3e9d00390bd47e5d7f1`；其余身份见本轮wrapper记录和ISSUE-031撤回构建登记。
- SOURCE→TASK逐项绑定 `/private/tmp/issue031-range-response-trade-20260914T193118Z/inputs/source-bindings.json`；每项均核对页面→202/Location→详情/全部批次→两次records→SQL原键/归属/实际source、insert、update/日志与清理。失败或输入变化停止，无自动重试或换日期；结果随后追加。

### ISSUE-031 response-trade 实际结果（issue031-range-response-trade-20260914T193118Z）

- 浏览器exit1，cleanup PASS；24计划项，状态{"PASS":0,"FAILED":0,"EVIDENCE_MISSING":1,"NOT_RUN":23}。实际来源1请求、records 1次；fixture另2提交/3查询。source 0行、insert 0次、update 0次，最终SQL键数按API单独记录。
- 完整本轮安全输出`/private/tmp/issue031-range-response-trade-20260914T193118Z/artifacts/run/safe-results.json` SHA `544fdf650def75c8a25b90b71c1f5c63f4ea566ef395aec731c159430d0370a9`；wrapper身份`/private/tmp/issue031-range-response-trade-20260914T193118Z/artifacts/task-run-identity.json` SHA `6a5f10bf86afd3c3d44343830690ad8c5f682e989224a868a0c98d5c31b975ea`。冻结源码/897 snapshot/两包/输入/私有权限前后保持，日志安全、自有进程回收、网络与任务日志关联通过情况以原产物为准；SQL安全汇总`/private/tmp/issue031-range-response-trade-20260914T193118Z/sql-supplement.json`。新库成功数据保留；临时client.cnf/environment.json已删除，实际清理见本轮secret-cleanup.json。
- 旧33轮逐对象保持，唯一索引追加后34轮/1066case/1273请求。逐项来源绑定与持久策略在harness核对；本轮经独立运行审查后开放接口：无（尚有跨轮任务或证据缺口）。

| TASK caseId | 状态 | 请求 | source | insert | update | SQL前→后键数 | 成功叶/总叶 |
| --- | --- | ---: | ---: | ---: | ---: | --- | --- |
| `issue026-issue025-source-20260913T162759Z-adj_factor-000001-whole` | EVIDENCE_MISSING | 1 | — | — | — | —→— | —/— |
| `issue026-issue025-boundaries-20260913T163148Z-adj_factor-000001-lower` | NOT_RUN | — | — | — | — | —→— | —/— |
| `issue026-issue025-boundaries-20260913T163148Z-adj_factor-000001-upper` | NOT_RUN | — | — | — | — | —→— | —/— |
| `issue026-issue025-boundaries-20260913T163148Z-adj_factor-000001-after` | NOT_RUN | — | — | — | — | —→— | —/— |
| `issue026-issue025-source-20260913T162759Z-adj_factor-600000-whole` | NOT_RUN | — | — | — | — | —→— | —/— |
| `issue026-issue025-boundaries-20260913T163148Z-adj_factor-600000-lower` | NOT_RUN | — | — | — | — | —→— | —/— |
| `issue026-issue025-boundaries-20260913T163148Z-adj_factor-600000-upper` | NOT_RUN | — | — | — | — | —→— | —/— |
| `issue026-issue025-boundaries-20260913T163148Z-adj_factor-600000-after` | NOT_RUN | — | — | — | — | —→— | —/— |
| `issue026-issue025-source-20260913T162759Z-suspend_d-000001-whole` | NOT_RUN | — | — | — | — | —→— | —/— |
| `issue026-issue025-boundaries-20260913T163148Z-suspend_d-000001-bounded` | NOT_RUN | — | — | — | — | —→— | —/— |
| `issue026-issue025-boundaries-20260913T163148Z-suspend_d-000001-lower` | NOT_RUN | — | — | — | — | —→— | —/— |
| `issue026-issue025-boundaries-20260913T163148Z-suspend_d-000001-upper` | NOT_RUN | — | — | — | — | —→— | —/— |
| `issue026-issue025-boundaries-20260913T163148Z-suspend_d-000001-consecutive` | NOT_RUN | — | — | — | — | —→— | —/— |
| `issue026-issue025-source-20260913T162759Z-suspend_d-600000-whole` | NOT_RUN | — | — | — | — | —→— | —/— |
| `issue026-issue025-boundaries-20260913T163148Z-suspend_d-600000-bounded` | NOT_RUN | — | — | — | — | —→— | —/— |
| `issue026-issue025-boundaries-20260913T163148Z-suspend_d-600000-lower` | NOT_RUN | — | — | — | — | —→— | —/— |
| `issue026-issue025-boundaries-20260913T163148Z-suspend_d-600000-upper` | NOT_RUN | — | — | — | — | —→— | —/— |
| `issue026-issue025-boundaries-20260913T163148Z-suspend_d-600000-consecutive` | NOT_RUN | — | — | — | — | —→— | —/— |
| `issue026-issue025-source-20260913T162759Z-suspend_d-000029-whole` | NOT_RUN | — | — | — | — | —→— | —/— |
| `issue026-issue025-boundaries-20260913T163148Z-suspend_d-000029-lower` | NOT_RUN | — | — | — | — | —→— | —/— |
| `issue026-issue025-boundaries-20260913T163148Z-suspend_d-000029-upper` | NOT_RUN | — | — | — | — | —→— | —/— |
| `issue026-issue025-source-20260913T162759Z-suspend_d-600310-whole` | NOT_RUN | — | — | — | — | —→— | —/— |
| `issue026-issue025-boundaries-20260913T163148Z-suspend_d-600310-lower` | NOT_RUN | — | — | — | — | —→— | —/— |
| `issue026-issue025-boundaries-20260913T163148Z-suspend_d-600310-upper` | NOT_RUN | — | — | — | — | —→— | —/— |

本轮中断说明：上表null字段汇总为0仅表示未完成验收计数，不能解释为零来源/零写入。实际adj_factor TASK `74417c73-0f69-4bb7-82f0-10c8238881e3` 与批次SUCCEEDED，1请求/1尝试/source4/insert4/update0；只读SQL原键0→4，日期20251229、20251230、20251231、20260105，suspend_d仍0记录。harness在成功状态文案处使用过期“已成功”断言，早于RESPONSE_ONLY检查及AFTER records/SQL；保留1 EVIDENCE_MISSING/23 NOT_RUN，exit1，不计清洁合格TASK。独立审查确认工具缺陷、原33轮/策略与清理保持；没有数据源失败，不撤回候选或递增策略。本轮sql-supplement.json为中断后只读观察，不能补造缺少的浏览器闭环。修复/重新冻结和明确24项新ID补验见ISSUE-031设计修订。

## ISSUE-031验收脚本文案修复构建与续验身份

最小harness修复已独立审查：通用SUCCEEDED状态按RESPONSE_ONLY非空/空使用既有正确文案，严格RANGE/SINGLE仍为“已成功”；保留提示/计数/SQL。实际submitDownload路径新回归先出现“返回记录已采集 !== 已成功”RED，再四场景GREEN。阶段证据同步后Node106/106通过（0失败/跳过）；日志`/private/tmp/issue031-resume-control/response-fix-full-green.log`。

隔离副本 `/private/tmp/issue031-harnessfix-20260914T194130Z`，898文件snapshot `c67cff6a43c75b35344ab78806e67bae0dfcd4d0fefb3a97f26af044f643ee21`，422相关源码与当前工作字节一致。完整离线 `mvn -o -f data-plane/pom.xml -Pacceptance verify` exit0，后端/两包1134、前端524通过，0失败/错误/跳过；受控download-tasks浏览器15/15 PASS、exit0，自有Vite已停止，未给测试提供真实上游令牌。日志/汇总位于 `/private/tmp/issue031-harnessfix-control/build/`。

- sourceDiffSha256：`9e1f478b96a08497338274972d77b5f73d89d4670e9a92066ddefa6e4fb96cc4`。
- productionJarSha256：`c3369f07a44a3da401984f6ce6206715c2616216b0e44fe552ae6d1ca9f5e442`。
- acceptanceJarSha256：`99ae75e9370a6b4fdf45391d2092524c382461106b9893622b65260eb6c1dda5`。
- manifestSha256：`386f46a99b6605e203129836d7a744b96b65304307f52991dd8bba6fd1870984`。
- requestExamplesSha256：`6d4c74a1a539b59ac20fb0cbd3ba1fba0954c40ef1209b652f7dcc2192ec932f`。

剩余91项按trade24/announcement42/holders21/regression4串行；24项明确新ID，原33项历史轮与其他旧记录不变。53条总替代映射（dates29+trade24）含原状态/任务ID约束，逐case构建表分别绑定旧82、续办99、本次91，禁止把旧TASK算成新包结果。纯选择91绑定通过；实际任务仍待执行。
- `/private/tmp/issue031-harnessfix-control/ten-round-plan.json` SHA `e319702b4cdc9d890221db48def5efc252be536e6c1810465b8613dc9c213dbe`。
- `/private/tmp/issue031-harnessfix-control/replacement-mapping.json` SHA `e1aea36a98761efd30c81946e240482186441c936b21d1abd6bf263b068ae1e9`。
- `/private/tmp/issue031-harnessfix-control/replacement-statuses.json` SHA `05b14537582f71f196d547bedf7344f613194b6c6fe54e95f1a3b1f0b1517833`。
- `/private/tmp/issue031-harnessfix-control/case-build-bindings.json` SHA `6f58ece6668ecf97d30f212681410228d7a6dfe86dc01ce1487a28f08f51abe4`。

## ISSUE-031 response-trade 真实TASK预登记（20260914T194412Z）

- runId `issue031-statusfix-range-response-trade-20260914T194412Z`；固定24 TASK、API `adj_factor, suspend_d`，完整清单 `/private/tmp/issue031-statusfix-range-response-trade-20260914T194412Z/inputs/cases.json`。原参数/日期轴/来源身份不变；本段登记时尚未启动真实任务。
- SOURCE既有观察合计24请求，仅用于预算；实际TASK请求数待实测，潜在日期二分仍受5000请求/30分钟硬限约束（最坏树可能超过预算，不保证全部完成）。每请求间隔≥2000ms、workers=1、retries=0，trace/screenshot/video关闭。fixture另2提交/3查询，目标records查询48次。
- 新空schema计划 `tensor_m14_t05_831cc2e3a03be024`，MySQL8.4.6；创建后核对0表、单库最小权限与UTF-8，应用启动后核对8迁移/52业务与任务表。全部成功数据库保留；只管理本轮JVM，固定HTTPS。
- 私有输入父目录0700/文件0600，索引仅按本轮快照消费；hash：`cases.json` `12226d0e7f0e2355aa50d8633891904107660f2b9a2efc861439a4a2148dba28`；`evidence-index.json` `336c2a0cb3845451b2b63e17e1d1b948ed139a7ea3e02e23b673b312af77d1a8`；`source-bindings.json` `73251b5d92c4ff7deff076fa2f031b86dc151c85bbb4d6ab19621e5d35f654ce`。
- 源码/两包沿用ISSUE-031验收脚本文案修复构建冻结898文件snapshot `c67cff6a43c75b35344ab78806e67bae0dfcd4d0fefb3a97f26af044f643ee21`，422相关源码与工作树相同；生产 `c3369f07a44a3da401984f6ce6206715c2616216b0e44fe552ae6d1ca9f5e442` / 验收 `99ae75e9370a6b4fdf45391d2092524c382461106b9893622b65260eb6c1dda5`；其余身份见本轮wrapper记录和ISSUE-031验收脚本文案修复构建登记。
- SOURCE→TASK逐项绑定 `/private/tmp/issue031-statusfix-range-response-trade-20260914T194412Z/inputs/source-bindings.json`；每项均核对页面→202/Location→详情/全部批次→两次records→SQL原键/归属/实际source、insert、update/日志与清理。失败或输入变化停止，无自动重试或换日期；结果随后追加。

### ISSUE-031 response-trade 实际结果（issue031-statusfix-range-response-trade-20260914T194412Z）

- 浏览器exit0，cleanup PASS；24计划项，状态{"PASS":24,"FAILED":0,"EVIDENCE_MISSING":0,"NOT_RUN":0}。实际来源24请求、records 48次；fixture另2提交/3查询。source 776行、insert 387次、update 389次，最终SQL键数按API单独记录。
- 完整本轮安全输出`/private/tmp/issue031-statusfix-range-response-trade-20260914T194412Z/artifacts/run/safe-results.json` SHA `02b41ac83b1d2d99d3dda92a7e02666f0846008c184bc0aae7b9536b2618714c`；wrapper身份`/private/tmp/issue031-statusfix-range-response-trade-20260914T194412Z/artifacts/task-run-identity.json` SHA `9711d9409755e6c26f9395a1a120649d92e663f3ad82302ce877d678d9dfb513`。冻结源码/898 snapshot/两包/输入/私有权限前后保持，日志安全、自有进程回收、网络与任务日志关联通过情况以原产物为准；SQL安全汇总`/private/tmp/issue031-statusfix-range-response-trade-20260914T194412Z/sql-supplement.json`。新库成功数据保留；临时client.cnf/environment.json已删除，实际清理见本轮secret-cleanup.json。
- 旧34轮逐对象保持，唯一索引追加后35轮/1090case/1297请求。逐项来源绑定与持久策略在harness核对；本轮经独立运行审查后开放接口：adj_factor、suspend_d。

| TASK caseId | 状态 | 请求 | source | insert | update | SQL前→后键数 | 成功叶/总叶 |
| --- | --- | ---: | ---: | ---: | ---: | --- | --- |
| `issue031-statusfix-issue026-issue025-source-20260913T162759Z-adj_factor-000001-whole` | PASS | 1 | 4 | 4 | 0 | 0→4 | 1/1 |
| `issue031-statusfix-issue026-issue025-boundaries-20260913T163148Z-adj_factor-000001-lower` | PASS | 1 | 1 | 0 | 1 | 4→4 | 1/1 |
| `issue031-statusfix-issue026-issue025-boundaries-20260913T163148Z-adj_factor-000001-upper` | PASS | 1 | 1 | 0 | 1 | 4→4 | 1/1 |
| `issue031-statusfix-issue026-issue025-boundaries-20260913T163148Z-adj_factor-000001-after` | PASS | 1 | 1 | 1 | 0 | 4→5 | 1/1 |
| `issue031-statusfix-issue026-issue025-source-20260913T162759Z-adj_factor-600000-whole` | PASS | 1 | 4 | 4 | 0 | 5→9 | 1/1 |
| `issue031-statusfix-issue026-issue025-boundaries-20260913T163148Z-adj_factor-600000-lower` | PASS | 1 | 1 | 0 | 1 | 9→9 | 1/1 |
| `issue031-statusfix-issue026-issue025-boundaries-20260913T163148Z-adj_factor-600000-upper` | PASS | 1 | 1 | 0 | 1 | 9→9 | 1/1 |
| `issue031-statusfix-issue026-issue025-boundaries-20260913T163148Z-adj_factor-600000-after` | PASS | 1 | 1 | 1 | 0 | 9→10 | 1/1 |
| `issue031-statusfix-issue026-issue025-source-20260913T162759Z-suspend_d-000001-whole` | PASS | 1 | 220 | 220 | 0 | 0→220 | 1/1 |
| `issue031-statusfix-issue026-issue025-boundaries-20260913T163148Z-suspend_d-000001-bounded` | PASS | 1 | 220 | 0 | 220 | 220→220 | 1/1 |
| `issue031-statusfix-issue026-issue025-boundaries-20260913T163148Z-suspend_d-000001-lower` | PASS | 1 | 1 | 0 | 1 | 220→220 | 1/1 |
| `issue031-statusfix-issue026-issue025-boundaries-20260913T163148Z-suspend_d-000001-upper` | PASS | 1 | 1 | 0 | 1 | 220→220 | 1/1 |
| `issue031-statusfix-issue026-issue025-boundaries-20260913T163148Z-suspend_d-000001-consecutive` | PASS | 1 | 5 | 0 | 5 | 220→220 | 1/1 |
| `issue031-statusfix-issue026-issue025-source-20260913T162759Z-suspend_d-600000-whole` | PASS | 1 | 147 | 147 | 0 | 220→367 | 1/1 |
| `issue031-statusfix-issue026-issue025-boundaries-20260913T163148Z-suspend_d-600000-bounded` | PASS | 1 | 147 | 0 | 147 | 367→367 | 1/1 |
| `issue031-statusfix-issue026-issue025-boundaries-20260913T163148Z-suspend_d-600000-lower` | PASS | 1 | 1 | 0 | 1 | 367→367 | 1/1 |
| `issue031-statusfix-issue026-issue025-boundaries-20260913T163148Z-suspend_d-600000-upper` | PASS | 1 | 1 | 0 | 1 | 367→367 | 1/1 |
| `issue031-statusfix-issue026-issue025-boundaries-20260913T163148Z-suspend_d-600000-consecutive` | PASS | 1 | 5 | 0 | 5 | 367→367 | 1/1 |
| `issue031-statusfix-issue026-issue025-source-20260913T162759Z-suspend_d-000029-whole` | PASS | 1 | 5 | 5 | 0 | 367→372 | 1/1 |
| `issue031-statusfix-issue026-issue025-boundaries-20260913T163148Z-suspend_d-000029-lower` | PASS | 1 | 1 | 0 | 1 | 372→372 | 1/1 |
| `issue031-statusfix-issue026-issue025-boundaries-20260913T163148Z-suspend_d-000029-upper` | PASS | 1 | 1 | 0 | 1 | 372→372 | 1/1 |
| `issue031-statusfix-issue026-issue025-source-20260913T162759Z-suspend_d-600310-whole` | PASS | 1 | 5 | 5 | 0 | 372→377 | 1/1 |
| `issue031-statusfix-issue026-issue025-boundaries-20260913T163148Z-suspend_d-600310-lower` | PASS | 1 | 1 | 0 | 1 | 377→377 | 1/1 |
| `issue031-statusfix-issue026-issue025-boundaries-20260913T163148Z-suspend_d-600310-upper` | PASS | 1 | 1 | 0 | 1 | 377→377 | 1/1 |

交易日期窗口专项SQL：`/private/tmp/issue031-statusfix-range-response-trade-20260914T194412Z/trade-window-sql.json` SHA `ea67c3c2641082cc67b4b9bbcf9fbd8a65c952cecc170abab4b89031dce62b09`。24项各自日期集合、逐股票原键数/摘要和最终窗口并集一致；adj_factor10键（两股各5），suspend_d377键（000001=220、600000=147、000029=5、600310=5），较早窗口保留。独立审查PASS。新24项完整通过后按显式映射移除中断轮的1 EVIDENCE_MISSING/23 NOT_RUN当前引用；原24个run/case对象完整保留，未将后端成功追认为旧轮验收PASS。RESPONSE_ONLY仍不保证上游完整性。

## ISSUE-031 response-announcement 真实TASK预登记（20260914T195234Z）

- runId `issue031-statusfix-range-response-announcement-20260914T195234Z`；固定42 TASK、API `income, balancesheet, cashflow, fina_audit, express, stk_managers`，完整清单 `/private/tmp/issue031-statusfix-range-response-announcement-20260914T195234Z/inputs/cases.json`。原参数/日期轴/来源身份不变；本段登记时尚未启动真实任务。
- SOURCE既有观察合计42请求，仅用于预算；实际TASK请求数待实测，潜在日期二分仍受5000请求/30分钟硬限约束（最坏树可能超过预算，不保证全部完成）。每请求间隔≥2000ms、workers=1、retries=0，trace/screenshot/video关闭。fixture另2提交/3查询，目标records查询84次。
- 新空schema计划 `tensor_m14_t05_e555dda5dd7209fe`，MySQL8.4.6；创建后核对0表、单库最小权限与UTF-8，应用启动后核对8迁移/52业务与任务表。全部成功数据库保留；只管理本轮JVM，固定HTTPS。
- 私有输入父目录0700/文件0600，索引仅按本轮快照消费；hash：`cases.json` `0cedee1dfd4c967c8ca2601ec016f97e06553133c62ea0e07f66f8994e9e7185`；`evidence-index.json` `b227aee36ab409a7ac731d7605450862c78fc2c9a1d8ce39e1ba11a52726756a`；`source-bindings.json` `f6a1b5570cf58dc5b26625953e39aecc239532b03fa6da5f62c2f7d45f8746c0`。
- 源码/两包沿用ISSUE-031验收脚本文案修复构建冻结898文件snapshot `c67cff6a43c75b35344ab78806e67bae0dfcd4d0fefb3a97f26af044f643ee21`，422相关源码与工作树相同；生产 `c3369f07a44a3da401984f6ce6206715c2616216b0e44fe552ae6d1ca9f5e442` / 验收 `99ae75e9370a6b4fdf45391d2092524c382461106b9893622b65260eb6c1dda5`；其余身份见本轮wrapper记录和ISSUE-031验收脚本文案修复构建登记。
- SOURCE→TASK逐项绑定 `/private/tmp/issue031-statusfix-range-response-announcement-20260914T195234Z/inputs/source-bindings.json`；每项均核对页面→202/Location→详情/全部批次→两次records→SQL原键/归属/实际source、insert、update/日志与清理。失败或输入变化停止，无自动重试或换日期；结果随后追加。

### ISSUE-031 response-announcement 实际结果（issue031-statusfix-range-response-announcement-20260914T195234Z）

- 浏览器exit1，cleanup PASS；42计划项，状态{"PASS":8,"FAILED":1,"EVIDENCE_MISSING":0,"NOT_RUN":33}。实际来源9请求、records 17次；fixture另2提交/3查询。source 21行、insert 8次、update 13次，最终SQL键数按API单独记录。
- 完整本轮安全输出`/private/tmp/issue031-statusfix-range-response-announcement-20260914T195234Z/artifacts/run/safe-results.json` SHA `10dde355950e4476b812fc67b81967a2a41ba4e15a0d6432ad91b4c47ec80667`；wrapper身份`/private/tmp/issue031-statusfix-range-response-announcement-20260914T195234Z/artifacts/task-run-identity.json` SHA `641980e0c067ef5b1f4fd89964789d868bf5c446c132c757b66eee508e4c100a`。冻结源码/898 snapshot/两包/输入/私有权限前后保持，日志安全、自有进程回收、网络与任务日志关联通过情况以原产物为准；SQL安全汇总`/private/tmp/issue031-statusfix-range-response-announcement-20260914T195234Z/sql-supplement.json`。新库成功数据保留；临时client.cnf/environment.json已删除，实际清理见本轮secret-cleanup.json。
- 旧35轮逐对象保持，唯一索引追加后36轮/1132case/1306请求。逐项来源绑定与持久策略在harness核对；本轮经独立运行审查后开放接口：无（尚有跨轮任务或证据缺口）。

| TASK caseId | 状态 | 请求 | source | insert | update | SQL前→后键数 | 成功叶/总叶 |
| --- | --- | ---: | ---: | ---: | ---: | --- | --- |
| `issue026-issue025-source-20260913T162759Z-income-000001-whole` | PASS | 1 | 4 | 4 | 0 | 0→4 | 1/1 |
| `issue026-issue025-boundaries-20260913T163148Z-income-000001-bounded` | PASS | 1 | 4 | 0 | 4 | 4→4 | 1/1 |
| `issue026-issue025-boundaries-20260913T163148Z-income-000001-lower` | PASS | 1 | 1 | 0 | 1 | 4→4 | 1/1 |
| `issue026-issue025-boundaries-20260913T163148Z-income-000001-upper` | PASS | 1 | 1 | 0 | 1 | 4→4 | 1/1 |
| `issue026-issue025-source-20260913T162759Z-income-600000-whole` | PASS | 1 | 4 | 4 | 0 | 4→8 | 1/1 |
| `issue026-issue025-boundaries-20260913T163148Z-income-600000-bounded` | PASS | 1 | 4 | 0 | 4 | 8→8 | 1/1 |
| `issue026-issue025-boundaries-20260913T163148Z-income-600000-lower` | PASS | 1 | 2 | 0 | 2 | 8→8 | 1/1 |
| `issue026-issue025-boundaries-20260913T163148Z-income-600000-upper` | PASS | 1 | 1 | 0 | 1 | 8→8 | 1/1 |
| `issue026-issue025-source-20260913T162759Z-balancesheet-000001-whole` | FAILED | 1 | 0 | 0 | 0 | 0→0 | 0/1 |
| `issue026-issue025-boundaries-20260913T163148Z-balancesheet-000001-bounded` | NOT_RUN | — | — | — | — | —→— | —/— |
| `issue026-issue025-boundaries-20260913T163148Z-balancesheet-000001-lower` | NOT_RUN | — | — | — | — | —→— | —/— |
| `issue026-issue025-boundaries-20260913T163148Z-balancesheet-000001-upper` | NOT_RUN | — | — | — | — | —→— | —/— |
| `issue026-issue025-source-20260913T162759Z-balancesheet-600000-whole` | NOT_RUN | — | — | — | — | —→— | —/— |
| `issue026-issue025-boundaries-20260913T163148Z-balancesheet-600000-bounded` | NOT_RUN | — | — | — | — | —→— | —/— |
| `issue026-issue025-boundaries-20260913T163148Z-balancesheet-600000-lower` | NOT_RUN | — | — | — | — | —→— | —/— |
| `issue026-issue025-boundaries-20260913T163148Z-balancesheet-600000-upper` | NOT_RUN | — | — | — | — | —→— | —/— |
| `issue026-issue025-source-20260913T162759Z-cashflow-000001-whole` | NOT_RUN | — | — | — | — | —→— | —/— |
| `issue026-issue025-boundaries-20260913T163148Z-cashflow-000001-bounded` | NOT_RUN | — | — | — | — | —→— | —/— |
| `issue026-issue025-boundaries-20260913T163148Z-cashflow-000001-lower` | NOT_RUN | — | — | — | — | —→— | —/— |
| `issue026-issue025-boundaries-20260913T163148Z-cashflow-000001-upper` | NOT_RUN | — | — | — | — | —→— | —/— |
| `issue026-issue025-source-20260913T162759Z-cashflow-600000-whole` | NOT_RUN | — | — | — | — | —→— | —/— |
| `issue026-issue025-boundaries-20260913T163148Z-cashflow-600000-bounded` | NOT_RUN | — | — | — | — | —→— | —/— |
| `issue026-issue025-boundaries-20260913T163148Z-cashflow-600000-lower` | NOT_RUN | — | — | — | — | —→— | —/— |
| `issue026-issue025-boundaries-20260913T163148Z-cashflow-600000-upper` | NOT_RUN | — | — | — | — | —→— | —/— |
| `issue026-issue025-source-20260913T162759Z-fina_audit-000001-whole` | NOT_RUN | — | — | — | — | —→— | —/— |
| `issue026-issue025-boundaries-20260913T163148Z-fina_audit-000001-bounded` | NOT_RUN | — | — | — | — | —→— | —/— |
| `issue026-issue025-source-20260913T162759Z-fina_audit-600000-whole` | NOT_RUN | — | — | — | — | —→— | —/— |
| `issue026-issue025-boundaries-20260913T163148Z-fina_audit-600000-bounded` | NOT_RUN | — | — | — | — | —→— | —/— |
| `issue026-issue025-source-20260913T162759Z-express-600000-whole` | NOT_RUN | — | — | — | — | —→— | —/— |
| `issue026-issue025-boundaries-20260913T163148Z-express-600000-bounded` | NOT_RUN | — | — | — | — | —→— | —/— |
| `issue026-issue025-express-20260913T163418Z-whole` | NOT_RUN | — | — | — | — | —→— | —/— |
| `issue026-issue025-express-20260913T163418Z-day` | NOT_RUN | — | — | — | — | —→— | —/— |
| `issue026-issue025-express-20260913T163418Z-lower-window` | NOT_RUN | — | — | — | — | —→— | —/— |
| `issue026-issue025-express-20260913T163418Z-upper-window` | NOT_RUN | — | — | — | — | —→— | —/— |
| `issue026-issue025-source-20260913T162759Z-stk_managers-000001-whole` | NOT_RUN | — | — | — | — | —→— | —/— |
| `issue026-issue025-boundaries-20260913T163148Z-stk_managers-000001-bounded` | NOT_RUN | — | — | — | — | —→— | —/— |
| `issue026-issue025-boundaries-20260913T163148Z-stk_managers-000001-lower` | NOT_RUN | — | — | — | — | —→— | —/— |
| `issue026-issue025-boundaries-20260913T163148Z-stk_managers-000001-upper` | NOT_RUN | — | — | — | — | —→— | —/— |
| `issue026-issue025-source-20260913T162759Z-stk_managers-600000-whole` | NOT_RUN | — | — | — | — | —→— | —/— |
| `issue026-issue025-boundaries-20260913T163148Z-stk_managers-600000-bounded` | NOT_RUN | — | — | — | — | —→— | —/— |
| `issue026-issue025-boundaries-20260913T163148Z-stk_managers-600000-lower` | NOT_RUN | — | — | — | — | —→— | —/— |
| `issue026-issue025-boundaries-20260913T163148Z-stk_managers-600000-upper` | NOT_RUN | — | — | — | — | —→— | —/— |

## ISSUE-031 资产负债表单次只读诊断（2026-09-15）

`issue031-balancesheet-diagnostic-20260914T200411Z`：按[具体计划](../task-designs/ISSUE-031-design.md#2026-09-15资产负债表失败保全与有界定位)完成一次原参数调用，1请求/exit0，adapter实际失败KEY_CONFLICT；6行逐字段转换异常0，两对零基行2/3及4/5仅total_share/update_flag不同。诊断包装器将ADAPTER_FAILED与执行错误区分，异常/超时/来源失败返回非零，不自动重试；未访问DB、提交TASK或持久化原始财务数据。

失败轮冻结验收包`99ae75e9370a6b4fdf45391d2092524c382461106b9893622b65260eb6c1dda5`及54个提取依赖字节一致，诊断源码/两class/包装器4项哈希在一次性execution-start中固定；私有目录`/private/tmp/issue031-balancesheet-diagnostic/`。safe-result SHA `473cc864541a24ecdcb53fc703fc28516995b54a026b34218bab3655e2d197bf`、execution-start SHA `2e0aa4672fc8772b4db05500a66c39705c5dd7072eead3cf0fd9121bf06b65c7`。独立实际产物审查PASS；原错误响应未保留，不能推断与本次逐值相同。官方doc36只说明“更新标识”，版本保留规则待决定，balancesheet保持v3撤回。

本次独立诊断不追加SOURCE/TASK验收case，不开放接口，不覆盖原成功来源或失败任务。唯一索引仍36轮/1132case/1306请求，另诊断1次单列。fina_indicator继续递延ISSUE-033，全程未调用。

## ISSUE-031 response-announcement-rest 真实TASK预登记（20260914T202312Z）

- runId `issue031-keyconflict-range-response-announcement-rest-20260914T202312Z`；固定34 TASK、API `income, cashflow, fina_audit, express, stk_managers`，完整清单 `/private/tmp/issue031-keyconflict-range-response-announcement-rest-20260914T202312Z/inputs/cases.json`。原参数/日期轴/来源身份不变；本段登记时尚未启动真实任务。
- SOURCE既有观察合计34请求，仅用于预算；实际TASK请求数待实测，潜在日期二分仍受5000请求/30分钟硬限约束（最坏树可能超过预算，不保证全部完成）。每请求间隔≥2000ms、workers=1、retries=0，trace/screenshot/video关闭。fixture另2提交/3查询，目标records查询68次。
- 新空schema计划 `tensor_m14_t05_a3a779445b7cc7a5`，MySQL8.4.6；创建后核对0表、单库最小权限与UTF-8，应用启动后核对8迁移/52业务与任务表。全部成功数据库保留；只管理本轮JVM，固定HTTPS。
- 私有输入父目录0700/文件0600，索引仅按本轮快照消费；hash：`cases.json` `5b8ec03889431d6feb30a2550277a3c1d234b19a7b167e6424a1ca3f23114940`；`evidence-index.json` `923217282533023e5030a7b7f6692532b9030f3d3babb210852487cd7918a593`；`source-bindings.json` `d1d9a54c42384498c699e96d8abfd39726bd0609e135619126a0944c07a94f57`。
- 源码/两包使用ISSUE-031冲突保全构建；准确snapshot/两包/源码身份见本轮registration.buildIdentity及wrapper记录，运行前逐文件核验。
- SOURCE→TASK逐项绑定 `/private/tmp/issue031-keyconflict-range-response-announcement-rest-20260914T202312Z/inputs/source-bindings.json`；每项均核对页面→202/Location→详情/全部批次→两次records→SQL原键/归属/实际source、insert、update/日志与清理。失败或输入变化停止，无自动重试或换日期；结果随后追加。

## ISSUE-031 冲突保全后构建与独立续验准备（2026-09-15）

资产负债表仍撤回且保留8项未决；其余59项按更新设计独立恢复，财务指标6项仍递延033。公告日期34项新ID/完整SOURCE绑定，holders21与regression4原ID未使用。计划仍272项，205旧接受项按原包归属，held8仍保留原失败轮包；替代映射87条（旧53加公告34），income的原8观察PASS只在指定exit1轮中按显式映射替代，原FAILED不适用。纯validateEvidence/validateCasePlan/selectTaskCases共59项通过，未发出任务请求。

新冻结副本`/private/tmp/issue031-keyconflict-20260914T201740Z`，898文件snapshot `c0d0c13144763d9866281566b362aa4f393df554f797140a2e1ffb619aa7d28f`。完整acceptance构建exit0：后端/两包1135、前端524项通过；Node107、受控任务页面15与新包metadata40项通过，失败/错误/跳过0。metadata零TASK/同步下载/records/上游，JVM/sentinel/日志检查true，外层自有容器与临时凭据已清理。安全原件`/var/folders/s5/h3vynqy544lc7vwtz0zjy39m0000gn/T/tensor-m14-t04-bcWZro/metadata-evidence.json` SHA `ad7742c1ed0a089f147877217891c16d33927fc215db9276126e5076ec999bff`；各日志与摘要位于`/private/tmp/issue031-keyconflict-control/build/`。

- sourceDiffSha256: `88e5fa50483860f7b20168fee0beef11c1b3aff920e24de7cc6ba03441852c27`。
- productionJarSha256: `b975e8a68810fb792b7e02fb2ac7dfc6bdd74583adf0e47ccb4e1757083fc4f5`。
- acceptanceJarSha256: `65a809227e4c3d485799af51a9826d37943ecc623c9deaf6aed3167aed19654e`。
- manifestSha256: `386f46a99b6605e203129836d7a744b96b65304307f52991dd8bba6fd1870984`。
- requestExamplesSha256: `6d4c74a1a539b59ac20fb0cbd3ba1fba0954c40ef1209b652f7dcc2192ec932f`。
- `/private/tmp/issue031-keyconflict-control/ten-round-plan.json` SHA `c58ec57b6395b744a071ea594f948b0a20b36e09875edbf8672af2063c5617ef`。
- `/private/tmp/issue031-keyconflict-control/replacement-mapping.json` SHA `5189b9fa888918bbb5d448ce60810fcaaecdb6b1dbfd196dbd71bdc06b26db94`。
- `/private/tmp/issue031-keyconflict-control/replacement-statuses.json` SHA `34e87bafbd4003ffb6728efb86957efd7d88678cc01f0c32cf798f2fe3788e99`。
- `/private/tmp/issue031-keyconflict-control/case-build-bindings.json` SHA `faf1727914e00c140b79db4e253b2c16ec5ab426eee5146a74ed04a6e118dcee`。

运行包装器从新config读取snapshot；运行前与registration、运行后collector均交叉核对同一snapshot和五项构建身份。旧包、旧run及原参数/日期轴/业务键保持；本准备不表示59项真实验收已经通过。

### ISSUE-031 response-announcement-rest 实际结果（issue031-keyconflict-range-response-announcement-rest-20260914T202312Z）

- 浏览器exit1，cleanup PASS；34计划项，状态{"PASS":8,"FAILED":1,"EVIDENCE_MISSING":0,"NOT_RUN":25}。实际来源9请求、records 17次；fixture另2提交/3查询。source 21行、insert 8次、update 13次，最终SQL键数按API单独记录。
- 完整本轮安全输出`/private/tmp/issue031-keyconflict-range-response-announcement-rest-20260914T202312Z/artifacts/run/safe-results.json` SHA `92271d5a02e8c4209e3f6ff94787cc32746cc1e8f20375a455ac943f2f4372e2`；wrapper身份`/private/tmp/issue031-keyconflict-range-response-announcement-rest-20260914T202312Z/artifacts/task-run-identity.json` SHA `9b1a715ac1d60915ba45037887a50bcf8b0aa7dd2f02dc900a611401757d7440`。冻结源码/898 snapshot/两包/输入/私有权限前后保持，日志安全、自有进程回收、网络与任务日志关联通过情况以原产物为准；SQL安全汇总`/private/tmp/issue031-keyconflict-range-response-announcement-rest-20260914T202312Z/sql-supplement.json`。新库成功数据保留；临时client.cnf/environment.json已删除，实际清理见本轮secret-cleanup.json。
- 旧36轮逐对象保持，唯一索引追加后37轮/1166case/1315请求。逐项来源绑定与持久策略在harness核对；本轮经独立运行审查后开放接口：无（尚有跨轮任务或证据缺口）。

| TASK caseId | 状态 | 请求 | source | insert | update | SQL前→后键数 | 成功叶/总叶 |
| --- | --- | ---: | ---: | ---: | ---: | --- | --- |
| `issue031-keyconflict-issue026-issue025-source-20260913T162759Z-income-000001-whole` | PASS | 1 | 4 | 4 | 0 | 0→4 | 1/1 |
| `issue031-keyconflict-issue026-issue025-boundaries-20260913T163148Z-income-000001-bounded` | PASS | 1 | 4 | 0 | 4 | 4→4 | 1/1 |
| `issue031-keyconflict-issue026-issue025-boundaries-20260913T163148Z-income-000001-lower` | PASS | 1 | 1 | 0 | 1 | 4→4 | 1/1 |
| `issue031-keyconflict-issue026-issue025-boundaries-20260913T163148Z-income-000001-upper` | PASS | 1 | 1 | 0 | 1 | 4→4 | 1/1 |
| `issue031-keyconflict-issue026-issue025-source-20260913T162759Z-income-600000-whole` | PASS | 1 | 4 | 4 | 0 | 4→8 | 1/1 |
| `issue031-keyconflict-issue026-issue025-boundaries-20260913T163148Z-income-600000-bounded` | PASS | 1 | 4 | 0 | 4 | 8→8 | 1/1 |
| `issue031-keyconflict-issue026-issue025-boundaries-20260913T163148Z-income-600000-lower` | PASS | 1 | 2 | 0 | 2 | 8→8 | 1/1 |
| `issue031-keyconflict-issue026-issue025-boundaries-20260913T163148Z-income-600000-upper` | PASS | 1 | 1 | 0 | 1 | 8→8 | 1/1 |
| `issue031-keyconflict-issue026-issue025-source-20260913T162759Z-cashflow-000001-whole` | FAILED | 1 | 0 | 0 | 0 | —→— | 0/1 |
| `issue031-keyconflict-issue026-issue025-boundaries-20260913T163148Z-cashflow-000001-bounded` | NOT_RUN | — | — | — | — | —→— | —/— |
| `issue031-keyconflict-issue026-issue025-boundaries-20260913T163148Z-cashflow-000001-lower` | NOT_RUN | — | — | — | — | —→— | —/— |
| `issue031-keyconflict-issue026-issue025-boundaries-20260913T163148Z-cashflow-000001-upper` | NOT_RUN | — | — | — | — | —→— | —/— |
| `issue031-keyconflict-issue026-issue025-source-20260913T162759Z-cashflow-600000-whole` | NOT_RUN | — | — | — | — | —→— | —/— |
| `issue031-keyconflict-issue026-issue025-boundaries-20260913T163148Z-cashflow-600000-bounded` | NOT_RUN | — | — | — | — | —→— | —/— |
| `issue031-keyconflict-issue026-issue025-boundaries-20260913T163148Z-cashflow-600000-lower` | NOT_RUN | — | — | — | — | —→— | —/— |
| `issue031-keyconflict-issue026-issue025-boundaries-20260913T163148Z-cashflow-600000-upper` | NOT_RUN | — | — | — | — | —→— | —/— |
| `issue031-keyconflict-issue026-issue025-source-20260913T162759Z-fina_audit-000001-whole` | NOT_RUN | — | — | — | — | —→— | —/— |
| `issue031-keyconflict-issue026-issue025-boundaries-20260913T163148Z-fina_audit-000001-bounded` | NOT_RUN | — | — | — | — | —→— | —/— |
| `issue031-keyconflict-issue026-issue025-source-20260913T162759Z-fina_audit-600000-whole` | NOT_RUN | — | — | — | — | —→— | —/— |
| `issue031-keyconflict-issue026-issue025-boundaries-20260913T163148Z-fina_audit-600000-bounded` | NOT_RUN | — | — | — | — | —→— | —/— |
| `issue031-keyconflict-issue026-issue025-source-20260913T162759Z-express-600000-whole` | NOT_RUN | — | — | — | — | —→— | —/— |
| `issue031-keyconflict-issue026-issue025-boundaries-20260913T163148Z-express-600000-bounded` | NOT_RUN | — | — | — | — | —→— | —/— |
| `issue031-keyconflict-issue026-issue025-express-20260913T163418Z-whole` | NOT_RUN | — | — | — | — | —→— | —/— |
| `issue031-keyconflict-issue026-issue025-express-20260913T163418Z-day` | NOT_RUN | — | — | — | — | —→— | —/— |
| `issue031-keyconflict-issue026-issue025-express-20260913T163418Z-lower-window` | NOT_RUN | — | — | — | — | —→— | —/— |
| `issue031-keyconflict-issue026-issue025-express-20260913T163418Z-upper-window` | NOT_RUN | — | — | — | — | —→— | —/— |
| `issue031-keyconflict-issue026-issue025-source-20260913T162759Z-stk_managers-000001-whole` | NOT_RUN | — | — | — | — | —→— | —/— |
| `issue031-keyconflict-issue026-issue025-boundaries-20260913T163148Z-stk_managers-000001-bounded` | NOT_RUN | — | — | — | — | —→— | —/— |
| `issue031-keyconflict-issue026-issue025-boundaries-20260913T163148Z-stk_managers-000001-lower` | NOT_RUN | — | — | — | — | —→— | —/— |
| `issue031-keyconflict-issue026-issue025-boundaries-20260913T163148Z-stk_managers-000001-upper` | NOT_RUN | — | — | — | — | —→— | —/— |
| `issue031-keyconflict-issue026-issue025-source-20260913T162759Z-stk_managers-600000-whole` | NOT_RUN | — | — | — | — | —→— | —/— |
| `issue031-keyconflict-issue026-issue025-boundaries-20260913T163148Z-stk_managers-600000-bounded` | NOT_RUN | — | — | — | — | —→— | —/— |
| `issue031-keyconflict-issue026-issue025-boundaries-20260913T163148Z-stk_managers-600000-lower` | NOT_RUN | — | — | — | — | —→— | —/— |
| `issue031-keyconflict-issue026-issue025-boundaries-20260913T163148Z-stk_managers-600000-upper` | NOT_RUN | — | — | — | — | —→— | —/— |

## ISSUE-031 income 真实TASK预登记（20260914T204158Z）

- runId `issue031-perapi-range-income-20260914T204158Z`；固定8 TASK、API `income`，完整清单 `/private/tmp/issue031-perapi-range-income-20260914T204158Z/inputs/cases.json`。原参数/日期轴/来源身份不变；本段登记时尚未启动真实任务。
- SOURCE既有观察合计8请求，仅用于预算；实际TASK请求数待实测，潜在日期二分仍受5000请求/30分钟硬限约束（最坏树可能超过预算，不保证全部完成）。每请求间隔≥2000ms、workers=1、retries=0，trace/screenshot/video关闭。fixture另2提交/3查询，目标records查询16次。
- 新空schema计划 `tensor_m14_t05_14f56bb775a18dbf`，MySQL8.4.6；创建后核对0表、单库最小权限与UTF-8，应用启动后核对8迁移/52业务与任务表。全部成功数据库保留；只管理本轮JVM，固定HTTPS。
- 私有输入父目录0700/文件0600，索引仅按本轮快照消费；hash：`cases.json` `2c3b2fb79ea5c3a9a64e44a87609d18b2289c5cef0d794c523f2e394144227e6`；`evidence-index.json` `a6ca477127bca2ec4d708c696fbc971357c773945d486ccaf0502a1c6c909278`；`source-bindings.json` `48d742a6072290d5bcb6f1ac7d003fdf241deb9bd73a75ef05e904d2d5f1f9f7`。
- 源码/两包使用ISSUE-031冲突保全构建；准确snapshot/两包/源码身份见本轮registration.buildIdentity及wrapper记录，运行前逐文件核验。
- SOURCE→TASK逐项绑定 `/private/tmp/issue031-perapi-range-income-20260914T204158Z/inputs/source-bindings.json`；每项均核对页面→202/Location→详情/全部批次→两次records→SQL原键/归属/实际source、insert、update/日志与清理。失败或输入变化停止，无自动重试或换日期；结果随后追加。

### cashflow失败保全与逐接口续验准备

`issue031-keyconflict-range-response-announcement-rest-20260914T202312Z`已按失败归档，未开放接口。cashflow TASK `4675518e-eba7-4d86-bc62-e6d613f54843` / batch `4398c8d3-1d5d-4444-9395-21c515c82acf`，原参数000001.SZ/20240101/20241231，FAILED/ADAPTER_TYPE_INVALID、1请求/1尝试，失败叶1/成功叶0/空叶0。新harness原始case直接保留FAILED，SQL-before/after字段均null；safe.sql保留BEFORE0、AFTER缺失，只有BEFORE records。后续只读SQL确认表0，单列不补造AFTER验收。实际上游行数未知，也不能借balancesheet诊断判定本次根因。

安全输出SHA `92271d5a02e8c4209e3f6ff94787cc32746cc1e8f20375a455ac943f2f4372e2`，SQL SHA `aa357d54b20b58f827cf9e9af70bb354b7e4c75038f75dc4d0c002073693e509`，income SQL SHA `3844ce14d76d2260ed0ec5c9e707d2bc7f5550d4e39d12fc4043e6f763b71a19`，均位于本轮同名私有目录；后者确认8个原键、两股历史保留及21source/8insert/13update。独立审查、进程/秘密清理通过，37个run共1166case/1315请求，另balancesheet诊断1次单列。income同8个计划位置在两个exit1轮各观察PASS，尚不计清洁接受。

cashflow撤回v3/NEEDS_VERIFICATION，SINGLE保持；现候选31 AVAILABLE/3 NEEDS_VERIFICATION/6 UNSUPPORTED，正式仍24/10/6。原参数拒绝测试先RED（实际AVAILABLE），随后157个定向Java回归通过；现金流量表f_ann_date日期轴的原规则改用已有受控策略验证，生产撤回另验，规则未放宽。Node108项通过，所有旧run和SQL缺失事实保持。日志`/private/tmp/issue031-control/cashflow-withdrawal-red.log`、`cashflow-withdrawal-green2.log`、`evidence-current37.log`。

剩余51项按[逐接口续验计划](../task-designs/ISSUE-031-design.md#现金流量表失败后的逐接口续验)独立运行，balancesheet8/cashflow8暂挂且仍在272目标内，财务指标033仍未调用。新副本`/private/tmp/issue031-perapi-20260914T203858Z`，898文件snapshot `49f9c70bc960af962fbf0d2504c678c06a0e98a76eaec275eb45eb9c18537723`；完整构建exit0，后端/两包1136、前端524、Node108通过。新包定向metadata仅3项（两旧撤回加cashflow），3/3通过、零上游/任务/records、JVM/sentinel/日志安全和自有容器/秘密清理通过；原件 `/var/folders/s5/h3vynqy544lc7vwtz0zjy39m0000gn/T/tensor-m14-t04-6GXj4G/metadata-evidence.json` SHA `a4d97938cf973be3650b8e5ee1a2904f039df8a488ddd37c0e2de17a875b3d5c`。上一包40metadata/15受控页面属于历史适用检查，未冒称新包跑了40项。

- sourceDiffSha256: `962c28560ae2bc6811c0fbc936a33d35930bcd9c567a3730f15560da47d5f87a`。
- productionJarSha256: `586020fa2d294b7a9d7d46d22199239fd0fbb646b26a54b2554bdd4615ced533`。
- acceptanceJarSha256: `a77d99aca450fe0345869a89392a9889480753f23c41c814bf81ef6f70c732d3`。
- manifestSha256: `386f46a99b6605e203129836d7a744b96b65304307f52991dd8bba6fd1870984`。
- requestExamplesSha256: `6d4c74a1a539b59ac20fb0cbd3ba1fba0954c40ef1209b652f7dcc2192ec932f`。
- `/private/tmp/issue031-perapi-control/ten-round-plan.json` SHA `b6c0e943f191d8ed272f68cd38a519013a117342ebcc3395b0b278e8b40aaaf9`。
- `/private/tmp/issue031-perapi-control/replacement-mapping.json` SHA `338b0c5fe86fa0f6fccb66d56cad5c7e387373df7264075e931d98907f804aac`。
- `/private/tmp/issue031-perapi-control/replacement-statuses.json` SHA `ef132fa8670141fbe4dc49647bafb2f1b0bcf19ad00cdb83972305d6651ff3ce`。
- `/private/tmp/issue031-perapi-control/case-build-bindings.json` SHA `e351fe41832a36893e5873dff283172a73c4f8dbaabf71359b20299f23229a06`。

纯选择51绑定通过，26个公告新ID、25个未用旧ID、held16及旧205身份分别核对；113条替代映射直接指向最终case，旧PASS例外只限两个固定失败run的income观察，原FAILED不适用。此处仅准备事实，尚未执行逐接口真实任务。

### ISSUE-031 income 实际结果（issue031-perapi-range-income-20260914T204158Z）

- 浏览器exit0，cleanup PASS；8计划项，状态{"PASS":8,"FAILED":0,"EVIDENCE_MISSING":0,"NOT_RUN":0}。实际来源8请求、records 16次；fixture另2提交/3查询。source 21行、insert 8次、update 13次，最终SQL键数按API单独记录。
- 完整本轮安全输出`/private/tmp/issue031-perapi-range-income-20260914T204158Z/artifacts/run/safe-results.json` SHA `30e0d3d058eb10a22924bc7b30b7a730e0e8e261a8cd453b4ae802c95c3e1e1e`；wrapper身份`/private/tmp/issue031-perapi-range-income-20260914T204158Z/artifacts/task-run-identity.json` SHA `60435fd4b00fbfefe29b044423baf88999e200aa49bdf8bcd078e5251c508127`。冻结源码/898 snapshot/两包/输入/私有权限前后保持，日志安全、自有进程回收、网络与任务日志关联通过情况以原产物为准；SQL安全汇总`/private/tmp/issue031-perapi-range-income-20260914T204158Z/sql-supplement.json`。新库成功数据保留；临时client.cnf/environment.json已删除，实际清理见本轮secret-cleanup.json。
- 旧37轮逐对象保持，唯一索引追加后38轮/1174case/1323请求。逐项来源绑定与持久策略在harness核对；本轮经独立运行审查后开放接口：income。

| TASK caseId | 状态 | 请求 | source | insert | update | SQL前→后键数 | 成功叶/总叶 |
| --- | --- | ---: | ---: | ---: | ---: | --- | --- |
| `issue031-perapi-issue031-keyconflict-issue026-issue025-source-20260913T162759Z-income-000001-whole` | PASS | 1 | 4 | 4 | 0 | 0→4 | 1/1 |
| `issue031-perapi-issue031-keyconflict-issue026-issue025-boundaries-20260913T163148Z-income-000001-bounded` | PASS | 1 | 4 | 0 | 4 | 4→4 | 1/1 |
| `issue031-perapi-issue031-keyconflict-issue026-issue025-boundaries-20260913T163148Z-income-000001-lower` | PASS | 1 | 1 | 0 | 1 | 4→4 | 1/1 |
| `issue031-perapi-issue031-keyconflict-issue026-issue025-boundaries-20260913T163148Z-income-000001-upper` | PASS | 1 | 1 | 0 | 1 | 4→4 | 1/1 |
| `issue031-perapi-issue031-keyconflict-issue026-issue025-source-20260913T162759Z-income-600000-whole` | PASS | 1 | 4 | 4 | 0 | 4→8 | 1/1 |
| `issue031-perapi-issue031-keyconflict-issue026-issue025-boundaries-20260913T163148Z-income-600000-bounded` | PASS | 1 | 4 | 0 | 4 | 8→8 | 1/1 |
| `issue031-perapi-issue031-keyconflict-issue026-issue025-boundaries-20260913T163148Z-income-600000-lower` | PASS | 1 | 2 | 0 | 2 | 8→8 | 1/1 |
| `issue031-perapi-issue031-keyconflict-issue026-issue025-boundaries-20260913T163148Z-income-600000-upper` | PASS | 1 | 1 | 0 | 1 | 8→8 | 1/1 |

## ISSUE-031 fina_audit 真实TASK预登记（20260914T205049Z）

- runId `issue031-perapi-range-fina_audit-20260914T205049Z`；固定4 TASK、API `fina_audit`，完整清单 `/private/tmp/issue031-perapi-range-fina_audit-20260914T205049Z/inputs/cases.json`。原参数/日期轴/来源身份不变；本段登记时尚未启动真实任务。
- SOURCE既有观察合计4请求，仅用于预算；实际TASK请求数待实测，潜在日期二分仍受5000请求/30分钟硬限约束（最坏树可能超过预算，不保证全部完成）。每请求间隔≥2000ms、workers=1、retries=0，trace/screenshot/video关闭。fixture另2提交/3查询，目标records查询8次。
- 新空schema计划 `tensor_m14_t05_e65dd1eea20cc684`，MySQL8.4.6；创建后核对0表、单库最小权限与UTF-8，应用启动后核对8迁移/52业务与任务表。全部成功数据库保留；只管理本轮JVM，固定HTTPS。
- 私有输入父目录0700/文件0600，索引仅按本轮快照消费；hash：`cases.json` `0f8f2d356c7d5275504bea98270ab72b98fe63cbf4aa1006cc9bce801061f185`；`evidence-index.json` `2550b0d61d83df75edd14081d213453ee15b6392eeb5db5f57c7d2b1bbea4d57`；`source-bindings.json` `2aeaf20e7ca666086ca9704ccf654a2660f4bd4ca79b4b605f40c897551bfec4`。
- 源码/两包使用ISSUE-031冲突保全构建；准确snapshot/两包/源码身份见本轮registration.buildIdentity及wrapper记录，运行前逐文件核验。
- SOURCE→TASK逐项绑定 `/private/tmp/issue031-perapi-range-fina_audit-20260914T205049Z/inputs/source-bindings.json`；每项均核对页面→202/Location→详情/全部批次→两次records→SQL原键/归属/实际source、insert、update/日志与清理。失败或输入变化停止，无自动重试或换日期；结果随后追加。

### ISSUE-031 fina_audit 实际结果（issue031-perapi-range-fina_audit-20260914T205049Z）

- 浏览器exit0，cleanup PASS；4计划项，状态{"PASS":4,"FAILED":0,"EVIDENCE_MISSING":0,"NOT_RUN":0}。实际来源4请求、records 8次；fixture另2提交/3查询。source 4行、insert 2次、update 2次，最终SQL键数按API单独记录。
- 完整本轮安全输出`/private/tmp/issue031-perapi-range-fina_audit-20260914T205049Z/artifacts/run/safe-results.json` SHA `0bb6a9be26c1c140fcb04b5e9b57107536ab6018aedda3bdddc9f3c49433278c`；wrapper身份`/private/tmp/issue031-perapi-range-fina_audit-20260914T205049Z/artifacts/task-run-identity.json` SHA `e78f7d2c2f4b5e0e91ad99611060e7f2f37e21a8f4ccfa13d78aec910c80fbb6`。冻结源码/898 snapshot/两包/输入/私有权限前后保持，日志安全、自有进程回收、网络与任务日志关联通过情况以原产物为准；SQL安全汇总`/private/tmp/issue031-perapi-range-fina_audit-20260914T205049Z/sql-supplement.json`。新库成功数据保留；临时client.cnf/environment.json已删除，实际清理见本轮secret-cleanup.json。
- 旧38轮逐对象保持，唯一索引追加后39轮/1178case/1327请求。逐项来源绑定与持久策略在harness核对；本轮经独立运行审查后开放接口：fina_audit。

| TASK caseId | 状态 | 请求 | source | insert | update | SQL前→后键数 | 成功叶/总叶 |
| --- | --- | ---: | ---: | ---: | ---: | --- | --- |
| `issue031-perapi-issue031-keyconflict-issue026-issue025-source-20260913T162759Z-fina_audit-000001-whole` | PASS | 1 | 1 | 1 | 0 | 0→1 | 1/1 |
| `issue031-perapi-issue031-keyconflict-issue026-issue025-boundaries-20260913T163148Z-fina_audit-000001-bounded` | PASS | 1 | 1 | 0 | 1 | 1→1 | 1/1 |
| `issue031-perapi-issue031-keyconflict-issue026-issue025-source-20260913T162759Z-fina_audit-600000-whole` | PASS | 1 | 1 | 1 | 0 | 1→2 | 1/1 |
| `issue031-perapi-issue031-keyconflict-issue026-issue025-boundaries-20260913T163148Z-fina_audit-600000-bounded` | PASS | 1 | 1 | 0 | 1 | 2→2 | 1/1 |

## ISSUE-031 express 真实TASK预登记（20260914T205520Z）

- runId `issue031-perapi-range-express-20260914T205520Z`；固定6 TASK、API `express`，完整清单 `/private/tmp/issue031-perapi-range-express-20260914T205520Z/inputs/cases.json`。原参数/日期轴/来源身份不变；本段登记时尚未启动真实任务。
- SOURCE既有观察合计6请求，仅用于预算；实际TASK请求数待实测，潜在日期二分仍受5000请求/30分钟硬限约束（最坏树可能超过预算，不保证全部完成）。每请求间隔≥2000ms、workers=1、retries=0，trace/screenshot/video关闭。fixture另2提交/3查询，目标records查询12次。
- 新空schema计划 `tensor_m14_t05_f67700690c9e7b51`，MySQL8.4.6；创建后核对0表、单库最小权限与UTF-8，应用启动后核对8迁移/52业务与任务表。全部成功数据库保留；只管理本轮JVM，固定HTTPS。
- 私有输入父目录0700/文件0600，索引仅按本轮快照消费；hash：`cases.json` `3a5c3f8b320b9806076c0cf854d34308cb46573d052aa799cca634b144831e8d`；`evidence-index.json` `9f37012aa70217a3f5cca738ac9ab9218fc1166e6672e8ddd532a6c86afb7817`；`source-bindings.json` `5401b542d54960d21e13990c06d258f5aea4132a65405c7c9dc406d638ca57ba`。
- 源码/两包使用ISSUE-031冲突保全构建；准确snapshot/两包/源码身份见本轮registration.buildIdentity及wrapper记录，运行前逐文件核验。
- SOURCE→TASK逐项绑定 `/private/tmp/issue031-perapi-range-express-20260914T205520Z/inputs/source-bindings.json`；每项均核对页面→202/Location→详情/全部批次→两次records→SQL原键/归属/实际source、insert、update/日志与清理。失败或输入变化停止，无自动重试或换日期；结果随后追加。

### ISSUE-031 express 实际结果（issue031-perapi-range-express-20260914T205520Z）

- 浏览器exit0，cleanup PASS；6计划项，状态{"PASS":6,"FAILED":0,"EVIDENCE_MISSING":0,"NOT_RUN":0}。实际来源6请求、records 12次；fixture另2提交/3查询。source 6行、insert 2次、update 4次，最终SQL键数按API单独记录。
- 完整本轮安全输出`/private/tmp/issue031-perapi-range-express-20260914T205520Z/artifacts/run/safe-results.json` SHA `4425671e736739e9f9498a439276bcbe594024c46f28065b23caa439a42f41d2`；wrapper身份`/private/tmp/issue031-perapi-range-express-20260914T205520Z/artifacts/task-run-identity.json` SHA `59a5b4e377a586b707259c0d2342c2e99b679bf50fc0986847d0bcc61d7f87ab`。冻结源码/898 snapshot/两包/输入/私有权限前后保持，日志安全、自有进程回收、网络与任务日志关联通过情况以原产物为准；SQL安全汇总`/private/tmp/issue031-perapi-range-express-20260914T205520Z/sql-supplement.json`。新库成功数据保留；临时client.cnf/environment.json已删除，实际清理见本轮secret-cleanup.json。
- 旧39轮逐对象保持，唯一索引追加后40轮/1184case/1333请求。逐项来源绑定与持久策略在harness核对；本轮经独立运行审查后开放接口：express。

| TASK caseId | 状态 | 请求 | source | insert | update | SQL前→后键数 | 成功叶/总叶 |
| --- | --- | ---: | ---: | ---: | ---: | --- | --- |
| `issue031-perapi-issue031-keyconflict-issue026-issue025-source-20260913T162759Z-express-600000-whole` | PASS | 1 | 1 | 1 | 0 | 0→1 | 1/1 |
| `issue031-perapi-issue031-keyconflict-issue026-issue025-boundaries-20260913T163148Z-express-600000-bounded` | PASS | 1 | 1 | 0 | 1 | 1→1 | 1/1 |
| `issue031-perapi-issue031-keyconflict-issue026-issue025-express-20260913T163418Z-whole` | PASS | 1 | 1 | 1 | 0 | 1→2 | 1/1 |
| `issue031-perapi-issue031-keyconflict-issue026-issue025-express-20260913T163418Z-day` | PASS | 1 | 1 | 0 | 1 | 2→2 | 1/1 |
| `issue031-perapi-issue031-keyconflict-issue026-issue025-express-20260913T163418Z-lower-window` | PASS | 1 | 1 | 0 | 1 | 2→2 | 1/1 |
| `issue031-perapi-issue031-keyconflict-issue026-issue025-express-20260913T163418Z-upper-window` | PASS | 1 | 1 | 0 | 1 | 2→2 | 1/1 |

## ISSUE-031 stk_managers 真实TASK预登记（20260914T210252Z）

- runId `issue031-perapi-range-stk_managers-20260914T210252Z`；固定8 TASK、API `stk_managers`，完整清单 `/private/tmp/issue031-perapi-range-stk_managers-20260914T210252Z/inputs/cases.json`。原参数/日期轴/来源身份不变；本段登记时尚未启动真实任务。
- SOURCE既有观察合计8请求，仅用于预算；实际TASK请求数待实测，潜在日期二分仍受5000请求/30分钟硬限约束（最坏树可能超过预算，不保证全部完成）。每请求间隔≥2000ms、workers=1、retries=0，trace/screenshot/video关闭。fixture另2提交/3查询，目标records查询16次。
- 新空schema计划 `tensor_m14_t05_872c5dd80b68cf35`，MySQL8.4.6；创建后核对0表、单库最小权限与UTF-8，应用启动后核对8迁移/52业务与任务表。全部成功数据库保留；只管理本轮JVM，固定HTTPS。
- 私有输入父目录0700/文件0600，索引仅按本轮快照消费；hash：`cases.json` `2c6a32f970f1137f5dfe1ed4b320e0af1692a4e2189a83a84efcef7ba2680456`；`evidence-index.json` `8c1c99e4fe66de56bd0e1734eab7d6e7c364f44807ae9f4c775df94385dbdbbe`；`source-bindings.json` `09f2d3283b806bb5a9386d0ede6e0faf9d62a7c93ad0270cfa8ce70c3e7fc03a`。
- 源码/两包使用ISSUE-031冲突保全构建；准确snapshot/两包/源码身份见本轮registration.buildIdentity及wrapper记录，运行前逐文件核验。
- SOURCE→TASK逐项绑定 `/private/tmp/issue031-perapi-range-stk_managers-20260914T210252Z/inputs/source-bindings.json`；每项均核对页面→202/Location→详情/全部批次→两次records→SQL原键/归属/实际source、insert、update/日志与清理。失败或输入变化停止，无自动重试或换日期；结果随后追加。

## ISSUE-031现金流量表一次诊断预登记

- run `issue031-cashflow-diagnostic-20260914T210414Z`，当前PREPARED_NOT_RUN。原cashflow/000001.SZ/20240101～20241231，仅一次来源调用，150秒、零重试；其他真实验收全部结束并独立审查后才执行。无TASK/SQL，不计入272通过数或唯一TASK索引。
- 原失败包SHA `65a809227e4c3d485799af51a9826d37943ecc623c9deaf6aed3167aed19654e`；诊断目录 `/private/tmp/issue031-cashflow-diagnostic`，registration SHA `e014e06eacf7730c06d8a587c9fc54e84a7c8c90a2db822b0386d62274d70d36`。原client/definition/adapter及54依赖、Java/源码/类/包装器/白名单输入执行前核验；一次启动标记防止重跑。
- 本地编译及7项输出白名单检查通过，来源请求0；只输出安全分支元数据，不持久化原响应、财务值、业务键值或原异常。不会调用fina_indicator或再次诊断balancesheet；设计依据见[现金流量表定位](../task-designs/ISSUE-031-design.md#现金流量表的一次有界根因定位)。

### ISSUE-031 stk_managers 实际结果（issue031-perapi-range-stk_managers-20260914T210252Z）

- 浏览器exit0，cleanup PASS；8计划项，状态{"PASS":8,"FAILED":0,"EVIDENCE_MISSING":0,"NOT_RUN":0}。实际来源8请求、records 16次；fixture另2提交/3查询。source 37行、insert 13次、update 24次，最终SQL键数按API单独记录。
- 完整本轮安全输出`/private/tmp/issue031-perapi-range-stk_managers-20260914T210252Z/artifacts/run/safe-results.json` SHA `7714f8ca9f691f5a7a3534831d2e53c081049ee76438dde1f4bf538619f32c61`；wrapper身份`/private/tmp/issue031-perapi-range-stk_managers-20260914T210252Z/artifacts/task-run-identity.json` SHA `415558adee861eb482e3b0673207d2357d4cff9edf4140ad9dccd97242519374`。冻结源码/898 snapshot/两包/输入/私有权限前后保持，日志安全、自有进程回收、网络与任务日志关联通过情况以原产物为准；SQL安全汇总`/private/tmp/issue031-perapi-range-stk_managers-20260914T210252Z/sql-supplement.json`。新库成功数据保留；临时client.cnf/environment.json已删除，实际清理见本轮secret-cleanup.json。
- 旧40轮逐对象保持，唯一索引追加后41轮/1192case/1341请求。逐项来源绑定与持久策略在harness核对；本轮经独立运行审查后开放接口：stk_managers。

| TASK caseId | 状态 | 请求 | source | insert | update | SQL前→后键数 | 成功叶/总叶 |
| --- | --- | ---: | ---: | ---: | ---: | --- | --- |
| `issue031-perapi-issue031-keyconflict-issue026-issue025-source-20260913T162759Z-stk_managers-000001-whole` | PASS | 1 | 5 | 5 | 0 | 0→5 | 1/1 |
| `issue031-perapi-issue031-keyconflict-issue026-issue025-boundaries-20260913T163148Z-stk_managers-000001-bounded` | PASS | 1 | 5 | 0 | 5 | 5→5 | 1/1 |
| `issue031-perapi-issue031-keyconflict-issue026-issue025-boundaries-20260913T163148Z-stk_managers-000001-lower` | PASS | 1 | 3 | 0 | 3 | 5→5 | 1/1 |
| `issue031-perapi-issue031-keyconflict-issue026-issue025-boundaries-20260913T163148Z-stk_managers-000001-upper` | PASS | 1 | 2 | 0 | 2 | 5→5 | 1/1 |
| `issue031-perapi-issue031-keyconflict-issue026-issue025-source-20260913T162759Z-stk_managers-600000-whole` | PASS | 1 | 8 | 8 | 0 | 5→13 | 1/1 |
| `issue031-perapi-issue031-keyconflict-issue026-issue025-boundaries-20260913T163148Z-stk_managers-600000-bounded` | PASS | 1 | 8 | 0 | 8 | 13→13 | 1/1 |
| `issue031-perapi-issue031-keyconflict-issue026-issue025-boundaries-20260913T163148Z-stk_managers-600000-lower` | PASS | 1 | 1 | 0 | 1 | 13→13 | 1/1 |
| `issue031-perapi-issue031-keyconflict-issue026-issue025-boundaries-20260913T163148Z-stk_managers-600000-upper` | PASS | 1 | 5 | 0 | 5 | 13→13 | 1/1 |

## ISSUE-031 repurchase 真实TASK预登记（20260914T211042Z）

- runId `issue031-perapi-range-repurchase-20260914T211042Z`；固定5 TASK、API `repurchase`，完整清单 `/private/tmp/issue031-perapi-range-repurchase-20260914T211042Z/inputs/cases.json`。原参数/日期轴/来源身份不变；本段登记时尚未启动真实任务。
- SOURCE既有观察合计5请求，仅用于预算；实际TASK请求数待实测，潜在日期二分仍受5000请求/30分钟硬限约束（最坏树可能超过预算，不保证全部完成）。每请求间隔≥2000ms、workers=1、retries=0，trace/screenshot/video关闭。fixture另2提交/3查询，目标records查询10次。
- 新空schema计划 `tensor_m14_t05_fd49169929ec1885`，MySQL8.4.6；创建后核对0表、单库最小权限与UTF-8，应用启动后核对8迁移/52业务与任务表。全部成功数据库保留；只管理本轮JVM，固定HTTPS。
- 私有输入父目录0700/文件0600，索引仅按本轮快照消费；hash：`cases.json` `ef13e28d929eef3dbe0a776e0af01a8ae2a2d2381783879b301eb3a7d997f972`；`evidence-index.json` `0c115885fa9014eb339dcd579c350f7fde20cb62a71e26712528eb6117e4ed7c`；`source-bindings.json` `5e4236b03fb10d6494d04a507631b2f87c6f0ce85702d5a0fdf73b2a525c8e35`。
- 源码/两包使用ISSUE-031冲突保全构建；准确snapshot/两包/源码身份见本轮registration.buildIdentity及wrapper记录，运行前逐文件核验。
- SOURCE→TASK逐项绑定 `/private/tmp/issue031-perapi-range-repurchase-20260914T211042Z/inputs/source-bindings.json`；每项均核对页面→202/Location→详情/全部批次→两次records→SQL原键/归属/实际source、insert、update/日志与清理。失败或输入变化停止，无自动重试或换日期；结果随后追加。

### ISSUE-031 repurchase 实际结果（issue031-perapi-range-repurchase-20260914T211042Z）

- 浏览器exit1，cleanup PASS；5计划项，状态{"PASS":0,"FAILED":1,"EVIDENCE_MISSING":0,"NOT_RUN":4}。实际来源1请求、records 1次；fixture另2提交/3查询。source 0行、insert 0次、update 0次，最终SQL键数按API单独记录。
- 完整本轮安全输出`/private/tmp/issue031-perapi-range-repurchase-20260914T211042Z/artifacts/run/safe-results.json` SHA `7c491eeccd94ecbdd6398cc786acff6d5ef856a62f9766f00db3ed1fbfdc9d88`；wrapper身份`/private/tmp/issue031-perapi-range-repurchase-20260914T211042Z/artifacts/task-run-identity.json` SHA `f0c28e42d71e8952994bee4bfae545fda14c7ad4165131f17a166a28c5f06750`。冻结源码/898 snapshot/两包/输入/私有权限前后保持，日志安全、自有进程回收、网络与任务日志关联通过情况以原产物为准；SQL安全汇总`/private/tmp/issue031-perapi-range-repurchase-20260914T211042Z/sql-supplement.json`。新库成功数据保留；临时client.cnf/environment.json已删除，实际清理见本轮secret-cleanup.json。
- 旧41轮逐对象保持，唯一索引追加后42轮/1197case/1342请求。逐项来源绑定与持久策略在harness核对；本轮经独立运行审查后开放接口：无（尚有跨轮任务或证据缺口）。

| TASK caseId | 状态 | 请求 | source | insert | update | SQL前→后键数 | 成功叶/总叶 |
| --- | --- | ---: | ---: | ---: | ---: | --- | --- |
| `issue026-issue025-source-20260913T162759Z-repurchase-all-whole` | FAILED | 1 | 0 | 0 | 0 | —→— | 0/1 |
| `issue026-issue025-boundaries-20260913T163148Z-repurchase-all-lower` | NOT_RUN | — | — | — | — | —→— | —/— |
| `issue026-issue025-boundaries-20260913T163148Z-repurchase-all-upper` | NOT_RUN | — | — | — | — | —→— | —/— |
| `issue026-issue025-boundaries-20260913T163148Z-repurchase-all-before` | NOT_RUN | — | — | — | — | —→— | —/— |
| `issue026-issue025-boundaries-20260913T163148Z-repurchase-all-after` | NOT_RUN | — | — | — | — | —→— | —/— |

### 回购失败保全与续验范围

`issue031-perapi-range-repurchase-20260914T211042Z`：原DATES参数20260801～20260831、无ts_code。task `938ab8cc-0b39-476d-bb77-abbfd683285e` / batch `31115b76-6521-4185-8f16-264121f4f557` 实际FAILED / ADAPTER_TYPE_INVALID，1请求/1尝试；1次BEFORE records，AFTER缺失，规范case的SQL前后均null。上文source/insert/update0仅为成功计数，不证明来源为空。补充只读SQL确认回购表0，未冒充完整TASK闭环。其余4项NOT_RUN，exit1/cleanup PASS；独立审查通过，失败schema保留、临时凭据已清理，旧41轮和其他接口不变。

仅repurchase当前准入撤回v3，SINGLE保持；根因尚未确定，无诊断或自动重试。当前231/272清洁接受，3 FAILED/38 NOT_RUN；其中balancesheet8、cashflow8、repurchase5暂挂，剩余holders16和regression4按新冻结包继续，21项暂挂没有另行递延。财务指标另6项仍归ISSUE-033。当前正式28 AVAILABLE/6 NEEDS_VERIFICATION/6 SINGLE_ONLY；候选30 AVAILABLE/4 NEEDS_VERIFICATION/6 UNSUPPORTED。

### 现金流诊断准备修订2（仍未执行）

公开doc44明确更新标志1为最新，仅据此增加同一次响应的安全聚合版本计数，不选行/不写入、不增加请求。登记现SHA `8af2822b2b0b4971a4367be71e7df03f32364cc34e183a4c2b912d4c8f772b49`；旧e014版本保存在诊断目录`revision1-prepared-not-run/`。21项纯检查通过、零真实请求；源码/类/runner哈希和实际输出口径见registration与[设计修订](../task-designs/ISSUE-031-design.md#现金流量表的一次有界根因定位)，执行前再次复核。

### 回购撤回后的新冻结构建

隔离副本`/private/tmp/issue031-repurchase-20260914T211953Z`，898文件snapshot `258b7df2a635a9cd3f53f7aaf6abb9257a55ab2551549269551bf09602da3b9b`；sourceDiff `453a552545163b0b18ccfeab043241ab86bb3bee6f4f8f651ddfcb6e07f1cc7c`，生产JAR `4d4f1e3985c10aca803a0ca4d8d1a535e2637909b0f68192c977d193c40735be`，验收JAR `5ecb993e13d6c1340013eb48ca87ea4a91782906dd06bd51f2108b7b51c1bfd5`。完整离线acceptance构建1137后端/打包、524前端，失败/错误/跳过0；定向158和当前阶段Node110均PASS。旧包和旧结果保持。

新包metadata核对4个撤回接口4/4 PASS，TASK提交/同步下载/records/上游调用均0，内部JVM/sentinel/日志检查及外层临时容器/凭据清理完成。原件`/var/folders/s5/h3vynqy544lc7vwtz0zjy39m0000gn/T/tensor-m14-t04-wI3ns8/metadata-evidence.json` SHA `afd24914aa306d58e3bbc633d73c96188042b6eefb4968966d3491139b7df8c8`；这是本包4接口检查，不将旧metadata40/受控页面15冒充新包结果。构建报告与准确身份见`/private/tmp/issue031-repurchase-control/build/`。

272逐case构建绑定中只更新未用的holders8+8/regression4共20项为新包，252个既有绑定保持各自历史包；20项纯选择与完整SOURCE绑定通过、尚未新增真实请求。两个运行包装器已指新副本，冻结前后源码/包/输入门禁保持。实际运行须逐轮预登记、列表/新空schema检查和独立复核后再执行。

## ISSUE-031 top10_holders 真实TASK预登记（20260914T212506Z）

- runId `issue031-repurchase-range-top10_holders-20260914T212506Z`；固定8 TASK、API `top10_holders`，完整清单 `/private/tmp/issue031-repurchase-range-top10_holders-20260914T212506Z/inputs/cases.json`。原参数/日期轴/来源身份不变；本段登记时尚未启动真实任务。
- SOURCE既有观察合计8请求，仅用于预算；实际TASK请求数待实测，潜在日期二分仍受5000请求/30分钟硬限约束（最坏树可能超过预算，不保证全部完成）。每请求间隔≥2000ms、workers=1、retries=0，trace/screenshot/video关闭。fixture另2提交/3查询，目标records查询16次。
- 新空schema计划 `tensor_m14_t05_00aa3f7984eb2044`，MySQL8.4.6；创建后核对0表、单库最小权限与UTF-8，应用启动后核对8迁移/52业务与任务表。全部成功数据库保留；只管理本轮JVM，固定HTTPS。
- 私有输入父目录0700/文件0600，索引仅按本轮快照消费；hash：`cases.json` `651a5a3a1e209260ee2a00b66ccfad56e8196034811c144d6ea4e0b4fbdc2801`；`evidence-index.json` `0bfb7c102c72fb600d77d607f56c8b612bf07631853e8bb6a0b9f31673502c7f`；`source-bindings.json` `70ee02eafb7a5fced137b62c36234f703b3175931fefb5c5293b5338f2286406`。
- 源码/两包使用ISSUE-031冲突保全构建；准确snapshot/两包/源码身份见本轮registration.buildIdentity及wrapper记录，运行前逐文件核验。
- SOURCE→TASK逐项绑定 `/private/tmp/issue031-repurchase-range-top10_holders-20260914T212506Z/inputs/source-bindings.json`；每项均核对页面→202/Location→详情/全部批次→两次records→SQL原键/归属/实际source、insert、update/日志与清理。失败或输入变化停止，无自动重试或换日期；结果随后追加。

## ISSUE-031回购一次诊断预登记（尚未执行）

`issue031-repurchase-diagnostic-20260914T212829Z`，私有目录`/private/tmp/issue031-repurchase-diagnostic`；registration SHA `57b870e3af74e97a5928a349c59b91d18da7341d03642c291f23f8ffe662e463`。原repurchase/20260801～20260831/无stock，原失败a77d99ac…验收包，最多1请求150秒、无重试/DB/TASK；其他TASK结束且独立复核后执行。编译+7项输出校验PASS/0请求；仅元数据白名单，不持久化原响应或字段值。详见[有界诊断设计](../task-designs/ISSUE-031-design.md#回购的一次有界根因定位)。

### ISSUE-031 top10_holders 实际结果（issue031-repurchase-range-top10_holders-20260914T212506Z）

- 浏览器exit0，cleanup PASS；8计划项，状态{"PASS":8,"FAILED":0,"EVIDENCE_MISSING":0,"NOT_RUN":0}。实际来源8请求、records 16次；fixture另2提交/3查询。source 240行、insert 100次、update 140次，最终SQL键数按API单独记录。
- 完整本轮安全输出`/private/tmp/issue031-repurchase-range-top10_holders-20260914T212506Z/artifacts/run/safe-results.json` SHA `961e550ac22dc1c11ff967d62b5dc44cd0523c72f26837153d534872b71832ab`；wrapper身份`/private/tmp/issue031-repurchase-range-top10_holders-20260914T212506Z/artifacts/task-run-identity.json` SHA `3b5236b3f073dfe0c3c74707fab3b8164186823a49ed7fa217b2926ce8d53fc7`。冻结源码/898 snapshot/两包/输入/私有权限前后保持，日志安全、自有进程回收、网络与任务日志关联通过情况以原产物为准；SQL安全汇总`/private/tmp/issue031-repurchase-range-top10_holders-20260914T212506Z/sql-supplement.json`。新库成功数据保留；临时client.cnf/environment.json已删除，实际清理见本轮secret-cleanup.json。
- 旧42轮逐对象保持，唯一索引追加后43轮/1205case/1350请求。逐项来源绑定与持久策略在harness核对；本轮经独立运行审查后开放接口：top10_holders。

| TASK caseId | 状态 | 请求 | source | insert | update | SQL前→后键数 | 成功叶/总叶 |
| --- | --- | ---: | ---: | ---: | ---: | --- | --- |
| `issue026-issue025-source-20260913T162759Z-top10_holders-000001-whole` | PASS | 1 | 40 | 40 | 0 | 0→40 | 1/1 |
| `issue026-issue025-boundaries-20260913T163148Z-top10_holders-000001-bounded` | PASS | 1 | 40 | 0 | 40 | 40→40 | 1/1 |
| `issue026-issue025-boundaries-20260913T163148Z-top10_holders-000001-lower` | PASS | 1 | 10 | 0 | 10 | 40→40 | 1/1 |
| `issue026-issue025-boundaries-20260913T163148Z-top10_holders-000001-upper` | PASS | 1 | 10 | 0 | 10 | 40→40 | 1/1 |
| `issue026-issue025-source-20260913T162759Z-top10_holders-600000-whole` | PASS | 1 | 60 | 60 | 0 | 40→100 | 1/1 |
| `issue026-issue025-boundaries-20260913T163148Z-top10_holders-600000-bounded` | PASS | 1 | 60 | 0 | 60 | 100→100 | 1/1 |
| `issue026-issue025-boundaries-20260913T163148Z-top10_holders-600000-lower` | PASS | 1 | 10 | 0 | 10 | 100→100 | 1/1 |
| `issue026-issue025-boundaries-20260913T163148Z-top10_holders-600000-upper` | PASS | 1 | 10 | 0 | 10 | 100→100 | 1/1 |

## ISSUE-031 top10_floatholders 真实TASK预登记（20260914T213321Z）

- runId `issue031-repurchase-range-top10_floatholders-20260914T213321Z`；固定8 TASK、API `top10_floatholders`，完整清单 `/private/tmp/issue031-repurchase-range-top10_floatholders-20260914T213321Z/inputs/cases.json`。原参数/日期轴/来源身份不变；本段登记时尚未启动真实任务。
- SOURCE既有观察合计8请求，仅用于预算；实际TASK请求数待实测，潜在日期二分仍受5000请求/30分钟硬限约束（最坏树可能超过预算，不保证全部完成）。每请求间隔≥2000ms、workers=1、retries=0，trace/screenshot/video关闭。fixture另2提交/3查询，目标records查询16次。
- 新空schema计划 `tensor_m14_t05_62b07759774bc742`，MySQL8.4.6；创建后核对0表、单库最小权限与UTF-8，应用启动后核对8迁移/52业务与任务表。全部成功数据库保留；只管理本轮JVM，固定HTTPS。
- 私有输入父目录0700/文件0600，索引仅按本轮快照消费；hash：`cases.json` `0c7f969099645bcc016923d64ff304e5ecd70dfb9e3ca8ae3551902b0a6255ae`；`evidence-index.json` `0306407c1beba6f6e7bde39c49fdb533a362c252f001d3b3309a48a886caaba9`；`source-bindings.json` `27d7642bc99c7b414594af57688ef3cc0c3c7fac26c0a0aab87cfeaad2f9b96e`。
- 源码/两包使用ISSUE-031冲突保全构建；准确snapshot/两包/源码身份见本轮registration.buildIdentity及wrapper记录，运行前逐文件核验。
- SOURCE→TASK逐项绑定 `/private/tmp/issue031-repurchase-range-top10_floatholders-20260914T213321Z/inputs/source-bindings.json`；每项均核对页面→202/Location→详情/全部批次→两次records→SQL原键/归属/实际source、insert、update/日志与清理。失败或输入变化停止，无自动重试或换日期；结果随后追加。

### ISSUE-031 top10_floatholders 实际结果（issue031-repurchase-range-top10_floatholders-20260914T213321Z）

- 浏览器exit0，cleanup PASS；8计划项，状态{"PASS":8,"FAILED":0,"EVIDENCE_MISSING":0,"NOT_RUN":0}。实际来源8请求、records 16次；fixture另2提交/3查询。source 247行、insert 100次、update 147次，最终SQL键数按API单独记录。
- 完整本轮安全输出`/private/tmp/issue031-repurchase-range-top10_floatholders-20260914T213321Z/artifacts/run/safe-results.json` SHA `1e597360f2d5d1bf1c35a8e085504b71e116df6af369e183b4a16f0efb2c35c2`；wrapper身份`/private/tmp/issue031-repurchase-range-top10_floatholders-20260914T213321Z/artifacts/task-run-identity.json` SHA `045080c9f64d017c8ee69f458e9926babe739120f248daa8beea1699cf26b33a`。冻结源码/898 snapshot/两包/输入/私有权限前后保持，日志安全、自有进程回收、网络与任务日志关联通过情况以原产物为准；SQL安全汇总`/private/tmp/issue031-repurchase-range-top10_floatholders-20260914T213321Z/sql-supplement.json`。新库成功数据保留；临时client.cnf/environment.json已删除，实际清理见本轮secret-cleanup.json。
- 旧43轮逐对象保持，唯一索引追加后44轮/1213case/1358请求。逐项来源绑定与持久策略在harness核对；本轮经独立运行审查后开放接口：top10_floatholders。

| TASK caseId | 状态 | 请求 | source | insert | update | SQL前→后键数 | 成功叶/总叶 |
| --- | --- | ---: | ---: | ---: | ---: | --- | --- |
| `issue026-issue025-source-20260913T162759Z-top10_floatholders-000001-whole` | PASS | 1 | 54 | 54 | 0 | 0→54 | 1/1 |
| `issue026-issue025-boundaries-20260913T163148Z-top10_floatholders-000001-bounded` | PASS | 1 | 54 | 0 | 54 | 54→54 | 1/1 |
| `issue026-issue025-boundaries-20260913T163148Z-top10_floatholders-000001-lower` | PASS | 1 | 15 | 0 | 15 | 54→54 | 1/1 |
| `issue026-issue025-boundaries-20260913T163148Z-top10_floatholders-000001-upper` | PASS | 1 | 10 | 0 | 10 | 54→54 | 1/1 |
| `issue026-issue025-source-20260913T162759Z-top10_floatholders-600000-whole` | PASS | 1 | 46 | 46 | 0 | 54→100 | 1/1 |
| `issue026-issue025-boundaries-20260913T163148Z-top10_floatholders-600000-bounded` | PASS | 1 | 46 | 0 | 46 | 100→100 | 1/1 |
| `issue026-issue025-boundaries-20260913T163148Z-top10_floatholders-600000-lower` | PASS | 1 | 12 | 0 | 12 | 100→100 | 1/1 |
| `issue026-issue025-boundaries-20260913T163148Z-top10_floatholders-600000-upper` | PASS | 1 | 10 | 0 | 10 | 100→100 | 1/1 |

## ISSUE-031 regression 真实TASK预登记（20260914T213809Z）

- runId `issue031-repurchase-range-regression-20260914T213809Z`；固定4 TASK、API `daily_basic, moneyflow, stk_limit, margin_detail`，完整清单 `/private/tmp/issue031-repurchase-range-regression-20260914T213809Z/inputs/cases.json`。原参数/日期轴/来源身份不变；本段登记时尚未启动真实任务。
- SOURCE既有观察合计4请求，仅用于预算；实际TASK请求数待实测，潜在日期二分仍受5000请求/30分钟硬限约束（最坏树可能超过预算，不保证全部完成）。每请求间隔≥2000ms、workers=1、retries=0，trace/screenshot/video关闭。fixture另2提交/3查询，目标records查询8次。
- 新空schema计划 `tensor_m14_t05_bd8a47b0e5d27d9d`，MySQL8.4.6；创建后核对0表、单库最小权限与UTF-8，应用启动后核对8迁移/52业务与任务表。全部成功数据库保留；只管理本轮JVM，固定HTTPS。
- 私有输入父目录0700/文件0600，索引仅按本轮快照消费；hash：`cases.json` `0f9375625f4690b0bf9a6a141a0f011d5187a7901feeb12f22b0e7c60f3bccc9`；`evidence-index.json` `c7d0067c818fd25a4205120c1623e07d609f41836e276c61721fe8476bbb3f2f`；`source-bindings.json` `432c6c47bba61474ac41936f30f7822fd543fff0feed1da7899bc6f3095cfb26`。
- 源码/两包使用ISSUE-031冲突保全构建；准确snapshot/两包/源码身份见本轮registration.buildIdentity及wrapper记录，运行前逐文件核验。
- SOURCE→TASK逐项绑定 `/private/tmp/issue031-repurchase-range-regression-20260914T213809Z/inputs/source-bindings.json`；每项均核对页面→202/Location→详情/全部批次→两次records→SQL原键/归属/实际source、insert、update/日志与清理。失败或输入变化停止，无自动重试或换日期；结果随后追加。

### ISSUE-031 regression 实际结果（issue031-repurchase-range-regression-20260914T213809Z）

- 浏览器exit0，cleanup PASS；4计划项，状态{"PASS":4,"FAILED":0,"EVIDENCE_MISSING":0,"NOT_RUN":0}。实际来源4请求、records 8次；fixture另2提交/3查询。source 22行、insert 22次、update 0次，最终SQL键数按API单独记录。
- 完整本轮安全输出`/private/tmp/issue031-repurchase-range-regression-20260914T213809Z/artifacts/run/safe-results.json` SHA `34c8469556cae3788b549a2b7bd592dc1c655c7ab507a2cb7fd4419233b70aff`；wrapper身份`/private/tmp/issue031-repurchase-range-regression-20260914T213809Z/artifacts/task-run-identity.json` SHA `1939bfbf34c616b1053f393dc3bbab47da68312c15eb986b08dedab566916484`。冻结源码/898 snapshot/两包/输入/私有权限前后保持，日志安全、自有进程回收、网络与任务日志关联通过情况以原产物为准；SQL安全汇总`/private/tmp/issue031-repurchase-range-regression-20260914T213809Z/sql-supplement.json`。新库成功数据保留；临时client.cnf/environment.json已删除，实际清理见本轮secret-cleanup.json。
- 旧44轮逐对象保持，唯一索引追加后45轮/1217case/1362请求。逐项来源绑定与持久策略在harness核对；本轮经独立运行审查，daily_basic、moneyflow、stk_limit、margin_detail回归通过，维持原AVAILABLE。

| TASK caseId | 状态 | 请求 | source | insert | update | SQL前→后键数 | 成功叶/总叶 |
| --- | --- | ---: | ---: | ---: | ---: | --- | --- |
| `issue026-regression-issue018-t14-priority-source-20260913T092938Z-daily_basic-cross-year` | PASS | 1 | 4 | 4 | 0 | 0→4 | 1/1 |
| `issue026-regression-issue018-t14-priority-source-20260913T092938Z-moneyflow-range` | PASS | 1 | 6 | 6 | 0 | 0→6 | 1/1 |
| `issue026-regression-issue018-t14-priority-source-20260913T092938Z-stk_limit-range` | PASS | 1 | 6 | 6 | 0 | 0→6 | 1/1 |
| `issue026-regression-issue018-t14-priority-source-20260913T092938Z-margin_detail-range` | PASS | 1 | 6 | 6 | 0 | 0→6 | 1/1 |

## 三接口冲突诊断与剩余阻塞

2026-09-15，其他可独立验收的任务已结束；当前目标272 = 251清洁PASS + 3 FAILED + 18 NOT_RUN。唯一索引45轮/1217case/1362来源请求，另三个有界诊断各1请求单独登记，不加入TASK/SOURCE验收。正式30 AVAILABLE/4 NEEDS_VERIFICATION/6 SINGLE_ONLY；构建能力30 AVAILABLE/4 NEEDS_VERIFICATION/6 UNSUPPORTED。fina_indicator另6项递延ISSUE-033，本次未调用。

| 当前受阻接口 | FAILED / NOT_RUN | 已观察的原因 | 尚缺的规则 |
| --- | ---: | --- | --- |
| balancesheet | 1 / 7 | 原键下total_share/update_flag不同，转换失败0 | doc36只称更新标识，版本保留优先级未定 |
| cashflow | 1 / 7 | 原键下两组内容冲突，转换失败0 | 是否采用唯一最新版本；无最新/多最新如何处理 |
| repurchase | 1 / 4 | 原键下15组内容冲突，转换失败0 | 如何识别不同回购记录或版本并保留数据 |

### 现金流量表诊断实际结果

`issue031-cashflow-diagnostic-20260914T210414Z`已按修订2登记执行并独立审查PASS。私有目录`/private/tmp/issue031-cashflow-diagnostic`，登记SHA `8af2822b2b0b4971a4367be71e7df03f32364cc34e183a4c2b912d4c8f772b49`；原失败验收JAR `65a809227e4c3d485799af51a9826d37943ecc623c9deaf6aed3167aed19654e`，原000001.SZ/20240101～20241231参数，1请求返回6行。原adapter结果ADAPTER_FAILED/ADAPTER_TYPE_INVALID/KEY_CONFLICT，字段转换失败0。6行完整转换为4个原键组，其中2个冲突组；每组恰有1个update_flag字符串为1的不同完整行，无最新组0、多最新组0。零基行1/2差异为end_type/prov_depr_assets/oth_loss_asset/update_flag，4/5为prov_depr_assets/oth_loss_asset/update_flag。原键保持ts_code,end_date,report_type,ann_date。

官方[doc44](https://tushare.pro/document/2?doc_id=44)写明update_flag为“更新标志(1最新）”；保存的official-doc44.html SHA `c72936927f2038160b357766e350aba40241404f02aebe6e12edac9283e17b9c`。这支持提出cashflow专属方案：冲突组只有一个不同的最新完整行时采用该版本，无最新或多个最新仍失败。该方案尚未决定或实现，不能推广到资产负债表和回购。safe-result.json SHA `6867e1802f09d1ddeb3ce64a482e0c19f7b3253deb8ee49bfcd27b7171e6e491`；execution-start.json SHA `9888e2fb812f2ae77e664d0b5e407253822c56593778913d0a9550bc00513d62`。修订1留在revision1-prepared-not-run/，从未执行。

### 回购诊断实际结果

`issue031-repurchase-diagnostic-20260914T212829Z`已执行并独立审查PASS。私有目录`/private/tmp/issue031-repurchase-diagnostic`，登记SHA `57b870e3af74e97a5928a349c59b91d18da7341d03642c291f23f8ffe662e463`；原失败验收JAR `a77d99aca450fe0345869a89392a9889480753f23c41c814bf81ef6f70c732d3`，原DATES参数20260801～20260831、无ts_code，1请求返回852行。原adapter结果ADAPTER_FAILED/ADAPTER_TYPE_INVALID/KEY_CONFLICT，转换失败0；按不同firstRowIndex得到15个冲突组，相对各组首行共有17次不同内容比较。差异字段仅end_date/vol/amount/high_limit/low_limit，原键保持ts_code,ann_date,proc。没有最新版本统计，也未单独计数完全重复行，不能用852减17推算唯一键数。

safe-result.json SHA `e4b9c1395f037ec258411b173a0ee8e67a53405998a7363c63a020cb058d73e8`；execution-start.json SHA `9e8fd714f432e4d3ad2528002fe4abff40dd352911d98362f1be5b107233b049`。

两次新诊断均在最后regression真实轮结束后串行执行。独立复核原包/Java/源码/类/包装器/54依赖及白名单输入身份通过；无DB/TASK、无原响应或字段值持久化、无重试。原失败响应未保留，结论仅描述原包/原参数下此次诊断观察，不声称重现了完全相同的原始payload。cashflow和repurchase原FAILED及规范SQL前后null保持，补充表0没有补成AFTER验收。

此前balancesheet诊断`issue031-balancesheet-diagnostic-20260914T200411Z`保持唯一一次请求：6行、KEY_CONFLICT、转换失败0、零基行2/3和4/5仅total_share/update_flag不同；没有测得最新标记或唯一键总数。safe SHA `473cc864541a24ecdcb53fc703fc28516995b54a026b34218bab3655e2d197bf`。三个接口保持v3撤回，原参数/字段/精度/键/SINGLE均未改变。只更新cashflow和repurchase当前未决原因为版本/身份规则未解决，全部45轮原case对象保持。

三个接口的21项仍在ISSUE-031目标中，尚未获准另行递延；ISSUE-031不能完成，ISSUE-032不启动。恢复条件是明确各接口可执行的冲突保留/身份规则并据此修订设计，或用户明确批准另行递延和目标变更；诊断请求不重复执行。

## 独立续验的专项SQL汇总

income8/fina_audit4/express6/stk_managers8四个独立成功轮按各自原业务键最终保留8/2/2/13键；income和express分别两股4+4及1+1，managers为5+8。managers原八字段元组与fingerprint计数匹配，没有独立重算fingerprint编码。对应各轮announcement-sql.json已与通用SQL一并审查；SHA依次为`4ec8ecc4b0e6ccdf2d821142f3f00d3fff3d5155255f45d732108e7f48aaeb74`、`01c290c52c75d5af6cae5fd80957a0b5890525f3745facb944db8f9ac6046b74`、`986928555dce15f97a9906b8d304353a115bfbf43f0742594b1b30290e904874`、`3c69261d144bc3a1d2abb75b0e3883310b8168e202896c5e036103e88f644393`。

top10_holders8与top10_floatholders8均保留原四字段键ts_code,end_date,holder_name,ann_date，最终各100键，股票分布分别40+60、54+46；后股票写入没有覆盖前股票。holders的600000额外保留20170831/20170904报告期各10键；floatholders两股各报告期键数分别15/15/14/10和12/12/12/10。两个接口各8项的“同股东同报告期不同公告日”组数都为0；部分报告期超过10行不能证明观察到了同一股东的多公告版本。各轮holders-sql.json SHA分别`5e6ba250e85474885f9c4d2d7d92da882219ef73a39f134f678a4c5245e9f916`和`338238115ac35f114cbbcbf59acf2612a12038960d73e561cfa0b89a8b0e8b9e`；最终键摘要分别`4b457f387aa7db4254ff5683e4eea3a63791291aca3cc48e001c6fab4af5dde3`和`e98f2805ed7a3ad1434cd5cf0af409bf37ba38964c0e06c64e0e213a97f9007f`。

regression4依次为daily_basic/moneyflow/stk_limit/margin_detail，最终键数4/6/6/6，来源及插入各22、更新0。SQL键摘要由准确SOURCE日期、股票和原字段顺序独立重算一致；保持原v2、6000/5800阈值、TRADE_DATE及NATIVE_RANGE/可拆合同。sql-supplement.json SHA `ec1f55a08737f5a8fbd76f2df798e536ed83fbe6911c214da252f73be46faf09`。上述三轮在5ecb993e…验收包上完成并清理秘密，数据库保留；没有把前四轮a77d99ac…包结果归到新包。

## 本次续办最终离线核对

真实调用全部结束后，使用当前唯一索引执行以下命令，exit0，111/111 PASS、失败/取消/跳过0；日志`/private/tmp/issue031-repurchase-control/final-stage-final.log`。阶段断言此前先出现109/110（旧阶段预期失败），按真实新结果更新并增加三轮绑定/历史保全检查后通过111项；没有修改生产运行代码或重置索引。

```sh
/private/tmp/issue031-repurchase-20260914T211953Z/data-plane/tensor-app/target/frontend/node/node --test control-plane/e2e/tushare-range-evidence.test.js
/private/tmp/issue031-repurchase-20260914T211953Z/data-plane/tensor-app/target/frontend/node/node /private/tmp/issue031-repurchase-control/final-audit.mjs
git diff --check
git diff --cached --check
```

最终audit为PASS：272精确计划/参数/日期轴/逐case构建匹配，251/3/18和正式30/4/6吻合；原26轮哈希`95b2e7e0a4743f982d1818540ea8075f694050b9e0ac51a13617f63529381313`及全部42轮基线对象保持，新三轮原始/规范输出、冻结身份、输入、清理和凭据文件移除均核对。报告`/private/tmp/issue031-repurchase-control/final-audit.json`，当前索引SHA `1a83add3b0288d2823c1f2bf1d862bc279ac0b8c59cd0ab912c1a2a1c2321558`。

`post-live-source-audit.json`同目录：原898文件冻结快照及两JAR保持；422相关源码/合同中，当前工作树相对真实运行包只有tushare-range-evidence.test.js阶段断言变化，生产与合同源码一致。登记文档另有更新，不能宣称最终工作树全部字节等同旧快照。适用完整acceptance构建为此前同一冻结包的1137后端/打包（66 suites）与524前端，定向158通过；新包metadata只做4个撤回接口，4/4通过且零上游/TASK/records，清理通过。旧包metadata40/受控页面15不计作本包检查，本次文档/证据测试变更不重复完整构建或真实请求。

ISSUE-031以251/272及三接口21项原键冲突暂停，按[暂停交接](../task-handoffs/ISSUE-031-handoff.md)记录IN_PROGRESS → BLOCKED。ISSUE-032与ISSUE-033保持NOT_STARTED，母任务未关闭；财务指标已按用户要求单独登记并加入Git。此次续办变更未commit/push，先前要求的一次远程提交已由f563bd9完成。

最终增量独立审查未发现阻断问题；审查指出的看板旧278范围/首次执行动作及回归四接口“开放”措辞均已修正。当前文档、索引未决原因、阶段测试与BLOCKED状态一致，未扩大诊断、递延或实现范围。

## 2026-09-15四接口排除与ISSUE-031收尾

用户明确本次不支持balancesheet/cashflow/repurchase/fina_indicator的RANGE批量下载，并要求不开始032；[范围决定](../issues/proposals/ISSUE-026-range-scope.md)限定替代原34/278全量要求。当前30个RANGE接口、251项固定TASK全部有既有合格证据，原27项排除并保留4 FAILED/23 NOT_RUN；没有将其改为PASS或删除原run。

本次仅修改范围文档、四接口的正式EXCLUDED处置/依据引用及现有证据测试的阶段预期。JSON中原45轮/1217case/1362来源请求逐对象保持，原inputHashes与其余36个接口逐对象保持；四接口的SOURCE/TASK状态、日期/参数、cases、unresolved、原提取规则和v3均保留。RESPONSE_ONLY的decisionRef继续指向已补充本次排除决定的ISSUE-025决策节，另在四接口evidenceRefs加入本次精确范围引用；财务指标decisionRef指向本决定，其原规则依据仍保留。

本次离线验证：

- `data-plane/tensor-app/target/frontend/node/node --test control-plane/e2e/tushare-range-evidence.test.js`：111/111通过，失败/取消/跳过0、exit0。日志`/private/tmp/issue031-scope-6edtmtx5/evidence-tests.log`。
- 同一Node执行`/private/tmp/issue031-scope-6edtmtx5/scope-audit.mjs`：PASS，exit0。机械核对原272项的参数/日期轴/包绑定，排除三个接口21项后精确剩251项、30 API，均PASS且原所属run清洁；另核对财务指标6项，合计27项排除及原状态。45轮逐对象、最后三轮原始安全结果/清单/身份/清理及当前30 AVAILABLE/4 EXCLUDED/6 SINGLE_ONLY通过。报告`scope-audit.json`，唯一索引SHA `6ef89b6692fdef4791b0ead64c8646ecde7646115e9aae2e907eac0850399cb3`。
- 422个运行/合同相关文件对比原冻结副本，仅证据测试变化；生产与合同源码保持，未构建新包或重写旧任务包归属。本次真实SOURCE/TASK/SQL新增均0。

| ISSUE-031修订后Acceptance | 结果与依据 |
| --- | --- |
| 1 固定纳入任务、SQL及历史保全 | 251/251 PASS，各自原SOURCE/参数/包、SQL及清洁身份已归档；本次scope-audit复核，原27排除项历史保持 |
| 2 mainbz、disclosure与日期/历史/BJ场景 | 原mainbz16项（含四新TASK）共19父/35成功叶、父零写入，四新TASK中三项实际SPLIT；两次disclosure重下及日期/历史/BJ的原SQL与独立运行审查保留，见前文对应轮次与专项SQL汇总 |
| 3 纳入的响应采集与逐接口准入 | 八个纳入的RESPONSE_ONLY及其余22个严格规则接口有实际TASK/SQL/页面和清洁证据；四排除接口保持失败与v3拒绝，不声称修复 |
| 4 相关回归、审查与准确汇总 | 111项证据回归和本次离线审计通过；此前各真实轮的独立审查保留，当前范围/能力/手册一致 |

据此仅完成修订范围内的ISSUE-031，记录IN_PROGRESS → COMPLETED。四接口问题继续保留，本次不处理；ISSUE-033保持NOT_STARTED。遵循用户“不开始32”，ISSUE-032保持NOT_STARTED、无后继交接、无六门禁执行；ISSUE-026/T13/T14/母issue均未关闭，不提交、推送或发布。

## 2026-09-15 ISSUE-032最终门禁与母任务收尾

本次没有新增账户SOURCE/TASK、账户SQL写入或真实run/case，唯一45轮/1217case/1362请求保持原字节。固定251项通过/27排除及各自原包再审通过；六门禁1401/524/1134/1137/15/138、111证据测试及独立终审通过，清理完成。首轮G1的陈旧能力断言、后续浏览器失败和各自诊断/修正/复验均按最终报告保留；最终冻结源码从G1重新按序验证。完整命令、时间、两包、历史保全与母合同逐条表见[最终报告](ISSUE-032-range-final-closure.md)。按032→T14→T13→017/018→026顺序收尾，033仍未开始；完整发布脚本未运行，未提交或发布。
